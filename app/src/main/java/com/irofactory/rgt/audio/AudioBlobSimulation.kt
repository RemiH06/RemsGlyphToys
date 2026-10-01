package com.irofactory.rgt.audio

import com.irofactory.rgt.fluid.FlipFluidSimulation
import com.irofactory.rgt.glyph.GlyphFrames
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh
import kotlin.random.Random

/**
 * AudioBlobSimulation
 * ───────────────────────────────────────────────────────────────────────────
 * Tres contornos huecos anidados, uno por familia del sonido de
 * AudioSpectrumSource. La matriz solo distingue brillo, no color, asi que
 * cada familia se reconoce por su silueta: un blob que tiende a un poligono
 * redondeado (el armonico principal del poligono, no sus lados rectos):
 *
 *   graves  (sub, bass)        hexagono
 *   voces   (lowMid, vocal)    diamante
 *   agudos  (presence, air)    triangulo
 *
 * Capas en escala logaritmica: el radio de cada figura depende de su energia
 * relativa a la familia mas fuerte, log(r) = log(rExterior) + LOG_SPREAD *
 * log(e / eMax). La que mas suena siempre queda afuera, la que menos adentro;
 * familias parecidas quedan con radios parecidos y se solapan, y una que casi
 * no suena colapsa al centro.
 *
 * Dureza (SpectrumAnalysis.harshness, armonia y no calidad): con sonido
 * suave la figura se redondea; con sonido aspero o brusco sus vertices se
 * afilan y se estiran en puntas cada vez mas delgadas.
 *
 * Las figuras giran lento, flotan con un resorte hacia el centro (cuanto
 * flotan depende de cuanto suenan) y sus bordes respiran con lobulos
 * organicos. Los cruces suman brillo. Motor puro, sin dependencias de Android.
 *
 * Experimento, cuarto anillo (KICK_RING): un anillo para el bombo, en dos
 * estilos (KICK_FROM_OUTSIDE):
 *   - desde afuera: vive fuera de la matriz. Si la cancion tiene bombo
 *     marcado, cada golpe lo mete al borde como una banda irregular (blob, con
 *     forma nueva en cada golpe) con un destello, y se retira sin prisa hacia
 *     afuera, ya al brillo minimo, hasta desaparecer.
 *   - desde el centro: en reposo es una bolita tenue; cada golpe la lanza al
 *     borde con un destello y regresa sin prisa, ya al brillo minimo.
 * Sin bombo marcado los golpes sueltos apenas lo mueven.
 *
 * Reposo ([restPose], elegido en la app). Salvo en centro, que tanto se
 * forma el reposo sigue al volumen ([awake]); con musica todo vuelve a su
 * comportamiento normal:
 *   - centro: las tres figuras se juntan al centro.
 *   - ojo: el diamante se aplana en una almendra horizontal con puntas que
 *     casi toca los bordes (los parpados); el hexagono queda chico al centro
 *     (la iris) y el triangulo dentro (la pupila), recortados por los parpados.
 *   - gato: del tamano de la matriz. El hexagono crece hasta el tope (la iris,
 *     como si el silencio la dejara caer hasta el fondo), el diamante se vuelve
 *     una rendija vertical con puntas (la pupila) y el triangulo queda chico
 *     en medio (un brillo). La mirada va de un lado a otro: se queda un rato
 *     en cada lado y cambia rapido; la iris se mueve la mitad que la rendija,
 *     como un ojo redondo.
 *   - logo: las tres figuras regulares, una dentro de otra con las
 *     proporciones del logo de la app, girando lento cada una a su ritmo.
 *   - boom: el anillo del bombo se queda adentro, quieto en su maximo, como
 *     blob que gira lento; las tres figuras se pegan al borde, redondas, y
 *     se camuflan en el.
 *   - plomada: el triangulo crece, cuelga un poco hacia el suelo y apunta
 *     siempre hacia abajo (con un resorte que lo hace oscilar al girar el
 *     telefono); el hexagono y el diamante quedan tenues como marco.
 *   - derretir: las figuras, tal como estaban, se vuelven agua (la FLIP de
 *     fluid) que cae hacia el suelo.
 *   - piezas: las figuras como poligonos regulares solidos que caen y chocan
 *     entre si ([SolidPieces]).
 * Los tres ultimos usan la gravedad de [setGravity].
 */
class AudioBlobSimulation {

    private companion object {
        const val PI_F = 3.1415927f
        // Capas: exponente de la escala logaritmica y piso de energia (evita log 0)
        const val LOG_SPREAD = 1.5f
        const val ENERGY_FLOOR = 0.02f
        // Radio de la capa exterior: en reposo, energia de la familia mas fuerte
        // con la que ya llega al tope, y exponente de su crecimiento
        const val REST_RADIUS = 3.0f
        const val FULL_ENERGY = 0.6f
        const val GROWTH = 0.6f
        // Orilla que las figuras dejan libre dentro de la matriz: tope de 12 celdas
        const val SHAPE_MARGIN = 0.5f
        const val MIN_RADIUS = 1.0f
        // Radios mas parecidos que CROSS (celdas) se pueden cruzar; a partir de
        // CROSS + CROSS_BLEND se anidan por completo, con transicion suave.
        const val CROSS = 0.6f
        const val CROSS_BLEND = 1.6f
        // Hueco visible entre contornos anidados, ademas del grosor de ambos
        const val CLEARANCE = 0.8f
        // Largo maximo de las puntas con dureza total, relativo al tamano de la figura
        const val SPIKE = 0.45f

        // Experimento: anillo del bombo. false lo quita por completo
        const val KICK_RING = true
        const val KICK_BALL = 0.8f
        const val KICK_THICKNESS = 1.1f
        // Brillo de la bolita y del regreso, relativo al destello del golpe
        const val KICK_DIM = 0.2f
        // Tiempos (s): regreso del borde al centro y caida del destello. La
        // salida es instantanea; el regreso, mas lento pero a tiempo para el
        // siguiente golpe a 120 BPM (0.5 s)
        const val KICK_RETURN = 0.25f
        const val KICK_FLASH = 0.04f
        // Estilo: true entra desde fuera de la matriz como blob; false sale de la bolita del centro
        const val KICK_FROM_OUTSIDE = true
        // Blob de afuera: media anchura de la banda, cuanto se deforma y
        // radio al que llega el centro de la banda con un golpe completo
        const val KICK_BAND = 1.5f
        const val KICK_WOBBLE = 1.2f
        const val KICK_IN = 11f

        // Ojo humano en reposo: media anchura y media altura de la almendra
        // (casi toca el borde, 12.5, sin que la linea se salga), y radios de
        // la iris (hexagono) y la pupila (triangulo)
        const val HUMAN_WIDTH = 11.3f
        const val HUMAN_HEIGHT = 1.6f
        const val HUMAN_IRIS = 2.6f
        const val HUMAN_PUPIL = 1.2f
        // Ojo de gato en reposo: media anchura y media altura de la rendija,
        // radio de la iris (12 = el tope de las figuras) y del brillo
        const val SLIT_WIDTH = 2f
        const val SLIT_HEIGHT = 10.5f
        const val CAT_IRIS = 12f
        const val CAT_PUPIL = 1f
        // Mirada del gato: cuanto se desliza la rendija (celdas) y cada cuanto va y vuelve (s)
        const val LOOK = 3f
        const val LOOK_PERIOD = 7f
        // Logo: radio y amplitud de su poligono redondeado (la del logo de la app)
        // para hexagono, diamante y triangulo
        val LOGO_SIZE = floatArrayOf(10.9f, 7.2f, 3.6f)
        val LOGO_AMP = floatArrayOf(0.11f, 0.14f, 0.24f)
        // Boom: radios de las figuras pegadas al borde (dentro de la banda del
        // bombo, 9.5 a 12.5), amplitud de sus lobulos y brillo del blob
        val BOOM_SIZE = floatArrayOf(11.2f, 10.6f, 10f)
        val BOOM_LOBES = floatArrayOf(0.5f, 0.35f, 0.25f)
        const val BOOM_LEVEL = 0.55f
        // Plomada: radios de hexagono, diamante y triangulo, definicion del
        // triangulo, cuanto cuelga hacia el suelo, resorte de su giro y brillo
        // del marco (hexagono y diamante) respecto al normal
        val PLUMB_SIZE = floatArrayOf(11.6f, 8.8f, 6.5f)
        const val PLUMB_AMP = 0.32f
        const val PLUMB_HANG = 1.5f
        const val PLUMB_STIFFNESS = 12f
        const val PLUMB_DAMPING = 2.5f
        const val PLUMB_FRAME = 0.35f
        // Derretir y piezas: volumen con el que la musica ya "cambio de estado"
        // (debajo: se derriten o caen; arriba: vuelven las figuras), brillo
        // desde el que un LED de las figuras ya es agua con densidad completa,
        // y brillo del agua y las piezas
        const val SOLIDIFY = 0.85f
        const val REFORM = 0.95f
        const val MELT_FULL = 0.15f
        const val MELT_LEVEL = 0.45f
        const val PIECES_LEVEL = 0.5f
        // Volumen con el que el reposo empieza a deshacerse y con el que ya no queda nada
        const val EYE_OPEN_FROM = 0.03f
        const val EYE_OPEN_TO = 0.3f
        // Puntos del contorno de los parpados o la rendija
        const val EYE_POINTS = 72
    }

    /**
     * Una figura: circulo deformado por el armonico de [sides] lobulos (su
     * poligono redondeado) mas lobulos organicos que la hacen respirar.
     */
    private class Ring(
        val sides: Int,
        val shapeAmp: Float,
        orientation: Float,
        val turnRate: Float,
        val lobes: IntArray,
        val lobeAmp: FloatArray,
        val spin: FloatArray,
        val drift: Float,
        val driftSpeed: Float,
        val thickness: Float,
        val seed: Float
    ) {
        val phase = FloatArray(lobes.size) { seed + it * 1.7f }
        val lobeLevel = FloatArray(lobes.size)
        var energy = 0f
        /** 0 = sonido suave (redondeada) .. 1 = aspero o brusco (puntas). */
        var harshness = 0f
        var size = REST_RADIUS
        var rotation = orientation
        var x = 0f
        var y = 0f
        var clock = seed
        /** Compresion para caber dentro de otra figura o de la matriz (1 = libre). */
        var squeeze = 1f

        /** Definicion fija de reposo y cuanto pesa (0 = la de la musica). */
        var restAmp = 0f
        var restBlend = 0f

        /** Que tan marcada esta la forma: mas definida entre mas suena; mas redonda si es suave. */
        private val definition: Float get() {
            val playing = shapeAmp * (0.6f + 0.4f * energy) * (0.4f + 0.6f * harshness)
            return playing + (restAmp - playing) * restBlend
        }
        /** Largo de las puntas: solo aparecen con dureza, y crecen mas rapido que ella. */
        private val spikeLength get() = SPIKE * harshness * sqrt(harshness)
        /** Que tan delgadas son las puntas: exponente del perfil del vertice. */
        private val spikeSharpness get() = 1f + 10f * harshness
        private val lobeReach get() = 0.5f * lobes.indices.sumOf { (lobeAmp[it] * lobeLevel[it]).toDouble() }.toFloat()
        val outer get() = (size * (1f + definition + spikeLength) + lobeReach) * squeeze
        val inner get() = ((size * (1f - definition) - lobeReach) * squeeze).coerceAtLeast(0.5f)

        fun radius(theta: Float): Float {
            val wave = cos(sides * (theta - rotation))
            // Punta: el perfil 0..1 del vertice elevado a una potencia alta queda
            // angosto, asi que solo la zona del vertice se estira
            val spike = spikeLength * ((0.5f + 0.5f * wave).pow(spikeSharpness))
            var r = size * (1f + definition * wave + spike)
            for (i in lobes.indices) r += lobeAmp[i] * lobeLevel[i] * 0.5f * cos(lobes[i] * (theta - phase[i]))
            return (r * squeeze).coerceAtLeast(0.6f)
        }
    }

    private val n = GlyphFrames.SIZE
    private val center = n / 2f
    private val edge = GlyphFrames.LED_RADIUS - 0.2f
    /** Radio maximo del contorno de las tres figuras, puntas y lobulos incluidos. */
    private val shapeLimit = GlyphFrames.LED_RADIUS - SHAPE_MARGIN

    // Graves: hexagono, gira lento a la derecha
    private val low = Ring(sides = 6, shapeAmp = 0.07f, orientation = 0f, turnRate = 0.06f,
        lobes = intArrayOf(2, 3), lobeAmp = floatArrayOf(1.4f, 1.2f), spin = floatArrayOf(0.13f, -0.21f),
        drift = 1.2f, driftSpeed = 0.35f, thickness = 1.0f, seed = 0f)
    // Voces: diamante (vertices en los ejes), gira a la izquierda
    private val mid = Ring(sides = 4, shapeAmp = 0.14f, orientation = 0f, turnRate = -0.09f,
        lobes = intArrayOf(2, 5), lobeAmp = floatArrayOf(1.2f, 1.0f), spin = floatArrayOf(0.4f, -0.55f),
        drift = 2.0f, driftSpeed = 0.5f, thickness = 0.9f, seed = 2.1f)
    // Agudos: triangulo apuntando hacia arriba (y crece hacia abajo), gira a la derecha mas rapido
    private val high = Ring(sides = 3, shapeAmp = 0.24f, orientation = -PI_F / 2f, turnRate = 0.13f,
        lobes = intArrayOf(5, 7), lobeAmp = floatArrayOf(0.6f, 0.5f), spin = floatArrayOf(0.8f, -1.2f),
        drift = 1.5f, driftSpeed = 0.9f, thickness = 0.85f, seed = 4.3f)
    private val rings = arrayOf(low, mid, high)

    private var loudness = 0f
    private var time = 0f

    // Anillo del bombo: que tan afuera va (1 = borde), destello y ultimo golpe visto
    private var kickReach = 0f
    private var kickFlash = 0f
    private var lastKick = 0f
    private var kickRadius = KICK_BALL
    // Blob de afuera: lobulos con amplitud y fase nuevas en cada golpe, girando lento
    private val kickLobes = intArrayOf(3, 5, 7)
    private val kickLobeSpin = floatArrayOf(0.6f, -0.9f, 1.3f)
    private val kickLobeAmp = FloatArray(kickLobes.size)
    private val kickLobePhase = FloatArray(kickLobes.size)
    private val random = Random(11)

    /** Como se ve en silencio; se puede cambiar en cualquier momento. */
    var restPose = RestPose.CAT_EYE

    // Gravedad en m/s², ejes de la matriz (y hacia abajo); la escribe el sensor desde otro hilo
    @Volatile private var gravityX = 0f
    @Volatile private var gravityY = 9.81f
    private var plumbSpin = 0f

    // Derretir y piezas: si ya cambiaron de estado y cuanto se ven (0 = figuras, 1 = agua o piezas)
    private var transformed = false
    private var transformedShown = 0f
    private val fluid by lazy { FlipFluidSimulation() }
    private val pieces by lazy { SolidPieces() }
    private val meltRandom = Random(5)

    // 0 = reposo formado .. 1 = musica, y contorno de los parpados o la rendija (x, y intercalados)
    private var awake = 1f
    private val isEye get() = restPose == RestPose.HUMAN_EYE || restPose == RestPose.CAT_EYE
    private val lid = FloatArray(EYE_POINTS * 2)

    /**
     * [bands] son las 6 bandas y [harshness] la dureza de graves, medios y
     * agudos, ambas de SpectrumAnalysis; [loud] el volumen real 0..1.
     * [kick] y [kickPresence] mueven el anillo del bombo.
     */
    /** Aceleracion del telefono en m/s², ya en ejes de la matriz (y hacia abajo). */
    fun setGravity(x: Float, y: Float) {
        gravityX = x
        gravityY = y
    }

    fun step(dt: Float, bands: FloatArray, loud: Float, harshness: FloatArray,
             kick: Float = 0f, kickPresence: Float = 0f) {
        time += dt
        loudness = loud

        if (KICK_RING) {
            // Golpe nuevo: [kick] brinca de golpe y luego solo cae
            val hit = kick > lastKick + 0.1f
            lastKick = kick
            // Suavizado en S: con bombo marcado cada golpe llega al borde, sin el apenas se mueve
            val open = smoothstep(0f, 1f, kickPresence)
            kickReach *= exp(-dt / KICK_RETURN)
            kickFlash *= exp(-dt / KICK_FLASH)
            if (hit) {
                kickReach = 1f
                kickFlash = kick * (0.3f + 0.7f * open)
                for (i in kickLobes.indices) {
                    kickLobeAmp[i] = KICK_WOBBLE * (0.25f + 0.25f * random.nextFloat())
                    kickLobePhase[i] = random.nextFloat() * 2f * PI_F
                }
            }
            if (KICK_FROM_OUTSIDE) {
                for (i in kickLobes.indices) kickLobePhase[i] += kickLobeSpin[i] * dt
                // Afuera del todo: ni la banda ni sus lobulos alcanzan un LED
                val out = GlyphFrames.LED_RADIUS + KICK_BAND + KICK_WOBBLE * 1.5f + 0.5f
                // Sin bombo marcado, un golpe suelto apenas se asoma por el borde
                kickRadius = out - (out - KICK_IN) * (open + 0.35f * (1f - open)) * kickReach
            } else {
                val far = edge - KICK_THICKNESS * 0.6f
                // Sin bombo marcado, un golpe suelto solo infla un poco la bolita
                kickRadius = KICK_BALL + ((far - KICK_BALL) * open + 1.2f * (1f - open)) * kickReach
            }
        }

        if (restPose != RestPose.CENTER) {
            val target = smoothstep(EYE_OPEN_FROM, EYE_OPEN_TO, loud)
            val tau = if (target > awake) 0.3f else 0.8f
            awake += (target - awake) * (1f - exp(-dt / tau))
        } else {
            awake = 1f
        }
        val rest = 1f - awake
        for ((i, ring) in rings.withIndex()) {
            ring.restAmp = if (restPose == RestPose.PLUMB) PLUMB_AMP else LOGO_AMP[i]
            ring.restBlend = when {
                restPose == RestPose.LOGO -> rest
                restPose == RestPose.PLUMB && ring === high -> rest
                else -> 0f
            }
        }
        stepTransformation(dt)
        if (KICK_RING && restPose == RestPose.BOOM) {
            // El blob entra y se queda; sus lobulos van a una forma tranquila y siguen girando
            val settle = rest * (1f - exp(-dt / 0.6f))
            for (i in kickLobes.indices) kickLobeAmp[i] += (BOOM_LOBES[i] - kickLobeAmp[i]) * settle
            kickRadius += (KICK_IN - kickRadius) * rest
        }

        low.harshness = harshness[0]
        mid.harshness = harshness[1]
        high.harshness = harshness[2]

        low.energy = 0.6f * bands[1] + 0.4f * bands[0]
        low.lobeLevel[0] = bands[0]; low.lobeLevel[1] = bands[1]
        mid.energy = 0.4f * bands[2] + 0.6f * bands[3]
        mid.lobeLevel[0] = bands[2]; mid.lobeLevel[1] = bands[3]
        high.energy = 0.55f * bands[4] + 0.45f * bands[5]
        high.lobeLevel[0] = bands[4]; high.lobeLevel[1] = bands[5]

        // Capas logaritmicas relativas a la familia mas fuerte
        val maxEnergy = rings.maxOf { it.energy }
        val outerSize = REST_RADIUS + (shapeLimit - REST_RADIUS) * (maxEnergy / FULL_ENERGY).coerceAtMost(1f).pow(GROWTH)
        val sizeFollow = 1f - exp(-dt / 0.12f)
        val follow = 1f - exp(-dt / 0.4f)
        for (ring in rings) {
            val ratio = (ring.energy + ENERGY_FLOOR) / (maxEnergy + ENERGY_FLOOR)
            var target = (outerSize * ratio.pow(LOG_SPREAD)).coerceAtLeast(MIN_RADIUS)
            val i = rings.indexOf(ring)
            target = when (restPose) {
                RestPose.CENTER -> target
                RestPose.HUMAN_EYE -> when (ring) {
                    low -> lerp(HUMAN_IRIS, target, awake)
                    high -> lerp(HUMAN_PUPIL, target, awake)
                    else -> target
                }
                RestPose.CAT_EYE -> when (ring) {
                    low -> lerp(CAT_IRIS, target, awake)
                    high -> lerp(CAT_PUPIL, target, awake)
                    else -> target
                }
                RestPose.LOGO -> lerp(LOGO_SIZE[i], target, awake)
                RestPose.BOOM -> lerp(BOOM_SIZE[i], target, awake)
                RestPose.PLUMB -> lerp(PLUMB_SIZE[i], target, awake)
                // Ocultas mientras son agua o piezas: reposo normal, para volver desde el centro
                RestPose.MELT, RestPose.PIECES -> target
            }
            ring.size += (target - ring.size) * sizeFollow

            ring.rotation += ring.turnRate * (1f + 0.5f * ring.energy) * dt
            for (i in ring.phase.indices) ring.phase[i] += ring.spin[i] * (1f + 1.5f * ring.lobeLevel[i]) * dt

            // Flotar: deriva lenta proporcional a su energia, resorte al centro
            ring.clock += dt * ring.driftSpeed * (0.3f + ring.energy)
            val amp = ring.drift * ring.energy
            val (hangX, hangY) = hang(ring)
            val tx = amp * sin(ring.clock + ring.seed) + lookShift(ring) + hangX
            val ty = amp * sin(ring.clock * 1.3f + 2f * ring.seed) * cos(ring.clock * 0.7f) + hangY
            ring.x += (tx - ring.x) * follow
            ring.y += (ty - ring.y) * follow
        }
        if (isEye) {
            // El diamante tiene simetria de 1/4 de vuelta: se envuelve a ±45° y,
            // al cerrarse el ojo, gira hasta dejar sus vertices en horizontal
            val quarter = PI_F / 2f
            var a = mid.rotation % quarter
            if (a > quarter / 2f) a -= quarter
            if (a < -quarter / 2f) a += quarter
            mid.rotation = a * (1f - (1f - awake) * (1f - exp(-dt / 0.3f)))
        }
        if (restPose == RestPose.PLUMB) {
            // Resorte hacia el suelo; con simetria de 1/3 de vuelta, cualquier vertice sirve
            val third = 2f * PI_F / 3f
            var error = (atan2(gravityY, gravityX) - high.rotation) % third
            if (error > third / 2f) error -= third
            if (error < -third / 2f) error += third
            plumbSpin += (PLUMB_STIFFNESS * error - PLUMB_DAMPING * plumbSpin) * dt
            high.rotation += plumbSpin * dt * rest
        }
        // Temblor leve de los agudos
        high.x += 0.12f * high.energy * sin(time * 11f)
        high.y += 0.12f * high.energy * cos(time * 13f)

        // Anidado, de afuera hacia adentro: la mas chica queda dentro de la mas
        // grande con hueco visible (comprimiendose si no cabe) salvo que sus
        // tamanos se parezcan, que es cuando se cruzan. Luego, todas dentro del tope.
        for (ring in rings) ring.squeeze = 1f
        for (ring in rings) if (ring.outer > shapeLimit) ring.squeeze = shapeLimit / ring.outer
        val bySize = rings.sortedByDescending { it.size }
        for (i in bySize.indices) for (j in i + 1 until bySize.size) {
            val big = bySize[i]
            val small = bySize[j]
            var nest = smoothstep(CROSS, CROSS + CROSS_BLEND, big.size - small.size)
            // En reposo los parpados o la rendija no empujan ni los empujan: van encima
            when {
                // Ojos: los parpados o la rendija van encima de las demas
                isEye && (big === mid || small === mid) -> nest *= awake
                // Logo y boom: sus tamanos ya estan elegidos, sin empujarse
                restPose == RestPose.LOGO || restPose == RestPose.BOOM ||
                    restPose == RestPose.PLUMB -> nest *= awake
            }
            if (nest <= 0f) continue
            val gap = big.thickness + small.thickness + CLEARANCE

            val maxOuter = big.inner - gap
            if (small.outer > maxOuter) {
                val fit = (maxOuter / small.outer * small.squeeze).coerceIn(0.2f, 1f)
                small.squeeze += (fit - small.squeeze) * nest
            }

            val allowed = (big.inner - small.outer - gap).coerceAtLeast(0f)
            val dx = small.x - big.x
            val dy = small.y - big.y
            val d = hypot(dx, dy)
            if (d > allowed && d > 1e-3f) {
                val k = 1f - nest + nest * allowed / d
                small.x = big.x + dx * k
                small.y = big.y + dy * k
            }
        }
        for (ring in rings) {
            val d = hypot(ring.x, ring.y)
            val room = (shapeLimit - ring.outer).coerceAtLeast(0f)
            if (d > room && d > 1e-3f) { ring.x = ring.x / d * room; ring.y = ring.y / d * room }
        }
    }

    fun rasterize(): Array<FloatArray> {
        val grid = Array(n) { FloatArray(n) }
        val light = 0.5f + 0.5f * loudness
        val levels = FloatArray(rings.size) { (0.4f + 0.6f * rings[it].energy) * light }
        if (restPose == RestPose.PLUMB) {
            // Marco tenue, triangulo protagonista
            val rest = 1f - awake
            levels[0] *= lerp(1f, PLUMB_FRAME, rest)
            levels[1] *= lerp(1f, PLUMB_FRAME, rest)
            levels[2] *= lerp(1f, 1.6f, rest)
        }
        // Mientras son agua o piezas, las figuras no se ven
        val ringsShown = 1f - transformedShown
        for (i in levels.indices) levels[i] *= ringsShown
        val fluidGrid = if (restPose == RestPose.MELT && transformedShown > 0.001f) fluid.rasterize() else null
        // Tenue en reposo y al regresar; solo el golpe destella
        var kickLevel = (KICK_DIM + (1f - KICK_DIM) * kickFlash) * light
        if (restPose == RestPose.BOOM) kickLevel += (BOOM_LEVEL * light - kickLevel) * (1f - awake)
        val kickThickness = KICK_THICKNESS * (1f + 0.6f * kickFlash)
        val eyeClosed = if (isEye) 1f - awake else 0f
        if (eyeClosed > 0.001f) buildLid(eyeClosed)

        for (row in 0 until n) {
            for (col in 0 until n) {
                val x = col + 0.5f - center
                val y = row + 0.5f - center
                if (hypot(x, y) > GlyphFrames.LED_RADIUS) continue

                // Mezcla tipo "screen": los cruces brillan mas sin saturar de golpe
                var dark = 1f
                if (eyeClosed > 0.001f) {
                    // Parpados o rendija como linea con su grosor real. En el ojo
                    // humano la iris y la pupila solo se ven dentro de los parpados
                    val (inside, distance) = lidDistance(x, y)
                    val keep = if (restPose == RestPose.HUMAN_EYE) {
                        val clip = (0.5f + (if (inside) distance else -distance) / 0.6f).coerceIn(0f, 1f)
                        1f - eyeClosed * (1f - clip)
                    } else 1f
                    dark *= 1f - rim(x, y, low) * levels[0] * keep
                    dark *= 1f - (1f - distance / mid.thickness).coerceIn(0f, 1f) * levels[1]
                    dark *= 1f - rim(x, y, high) * levels[2] * keep
                } else {
                    for (i in rings.indices) dark *= 1f - rim(x, y, rings[i]) * levels[i]
                }
                if (KICK_RING) {
                    val shape = if (KICK_FROM_OUTSIDE) kickBlob(x, y, KICK_BAND * (1f + 0.3f * kickFlash))
                        else kickShape(hypot(x, y), kickThickness)
                    dark *= 1f - shape * kickLevel
                }
                if (transformedShown > 0.001f) {
                    if (fluidGrid != null) dark *= 1f - fluidGrid[row][col] * MELT_LEVEL * transformedShown
                    if (restPose == RestPose.PIECES) dark *= 1f - pieces.brightness(x, y) * PIECES_LEVEL * transformedShown
                }
                grid[row][col] = 1f - dark
            }
        }
        return grid
    }

    private fun rim(x: Float, y: Float, ring: Ring): Float {
        val dx = x - ring.x
        val dy = y - ring.y
        val d = hypot(dx, dy)
        return (1f - abs(d - ring.radius(atan2(dy, dx))) / ring.thickness).coerceIn(0f, 1f)
    }

    /**
     * Contorno de los parpados o la rendija: del diamante (abierto) a una
     * almendra con puntas afiladas (reposo), punto por punto. La almendra son
     * dos parabolas que se juntan en angulo: horizontal en el ojo humano,
     * y = ±H·(1 − (x/W)²); vertical en el gato, x = ±W·(1 − (y/H)²).
     */
    private fun buildLid(closed: Float) {
        for (i in 0 until EYE_POINTS) {
            val theta = 2f * PI_F * i / EYE_POINTS
            val c = cos(theta)
            val s = sin(theta)
            val r = mid.radius(theta)
            val cat = restPose == RestPose.CAT_EYE
            val almondX = if (cat) SLIT_WIDTH * c * abs(c) else HUMAN_WIDTH * c
            val almondY = if (cat) SLIT_HEIGHT * s else HUMAN_HEIGHT * s * abs(s)
            lid[2 * i] = mid.x + lerp(r * c, almondX, closed)
            lid[2 * i + 1] = mid.y + lerp(r * s, almondY, closed)
        }
    }

    /** Si (x, y) cae dentro del contorno y a que distancia de su linea, en celdas. */
    private fun lidDistance(x: Float, y: Float): Pair<Boolean, Float> {
        var inside = false
        var best = Float.MAX_VALUE
        var j = EYE_POINTS - 1
        for (i in 0 until EYE_POINTS) {
            val ax = lid[2 * j]; val ay = lid[2 * j + 1]
            val bx = lid[2 * i]; val by = lid[2 * i + 1]
            if ((ay > y) != (by > y) && x < ax + (y - ay) / (by - ay) * (bx - ax)) inside = !inside
            val ex = bx - ax; val ey = by - ay
            val t = (((x - ax) * ex + (y - ay) * ey) / (ex * ex + ey * ey + 1e-6f)).coerceIn(0f, 1f)
            best = minOf(best, hypot(x - ax - t * ex, y - ay - t * ey))
            j = i
        }
        return inside to best
    }

    /**
     * Mirada en reposo: tanh de un seno se queda en cada lado y cruza rapido.
     * La iris se mueve la mitad que la rendija y el brillo.
     */
    private fun lookShift(ring: Ring): Float {
        if (restPose != RestPose.CAT_EYE) return 0f
        val look = LOOK * tanh(3f * sin(2f * PI_F * time / LOOK_PERIOD)) * (1f - awake)
        return if (ring === low) 0.5f * look else look
    }

    /** Plomada: el triangulo cuelga un poco hacia donde apunta la gravedad. */
    private fun hang(ring: Ring): Pair<Float, Float> {
        if (restPose != RestPose.PLUMB || ring !== high) return 0f to 0f
        val g = hypot(gravityX, gravityY).coerceAtLeast(1e-3f)
        val k = PLUMB_HANG * (1f - awake) / g
        return gravityX * k to gravityY * k
    }

    /**
     * Derretir y piezas: con el silencio las figuras se vuelven agua o piezas
     * (una vez, tal como estaban) y al volver la musica regresan las figuras.
     * Cambian al bajar de SOLIDIFY y regresan solo al volver arriba de REFORM:
     * con un solo umbral parpadearian en cuanto el volumen ronde ese valor.
     */
    private fun stepTransformation(dt: Float) {
        val transforms = restPose == RestPose.MELT || restPose == RestPose.PIECES
        if (!transforms) {
            transformed = false
            transformedShown = 0f
            return
        }
        if (!transformed && awake < SOLIDIFY) {
            transformed = true
            if (restPose == RestPose.MELT) {
                // El agua nace justo donde estaban las figuras (completas): el cambio no se nota
                transformedShown = 0f
                fluid.pour(meltPoints(rasterize()))
                transformedShown = 1f
            } else {
                pieces.reset(floatArrayOf(low.rotation, mid.rotation, high.rotation))
            }
        } else if (transformed && awake > REFORM) {
            transformed = false
        }
        val target = if (transformed) 1f else 0f
        transformedShown += (target - transformedShown) * (1f - exp(-dt / 0.25f))
        if (transformedShown > 0.001f) {
            if (restPose == RestPose.MELT) {
                fluid.setGravity(gravityX, gravityY)
                fluid.step(dt)
            } else {
                pieces.step(dt, gravityX, gravityY)
            }
        }
    }

    /**
     * Particulas para el agua, al azar dentro de cada LED. Un LED encendido de
     * las figuras lleva la densidad del agua en reposo (si no, el agua recien
     * nacida es tan rala que no se ve); los mas tenues, proporcionalmente menos.
     */
    private fun meltPoints(grid: Array<FloatArray>): FloatArray {
        val points = ArrayList<Float>()
        val perLed = fluid.particlesPerLed
        val inner = GlyphFrames.LED_RADIUS - 0.4f
        for (row in 0 until n) for (col in 0 until n) {
            val count = (perLed * (grid[row][col] / MELT_FULL).coerceAtMost(1f)).toInt()
            repeat(count) {
                val x = col + meltRandom.nextFloat()
                val y = row + meltRandom.nextFloat()
                if (hypot(x - center, y - center) < inner) { points += x; points += y }
            }
        }
        return points.toFloatArray()
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    /** Contorno del anillo del bombo; chico se rellena para verse como bolita y no como aro. */
    private fun kickShape(d: Float, thickness: Float): Float {
        val rim = (1f - abs(d - kickRadius) / thickness).coerceIn(0f, 1f)
        val ball = (kickRadius + 0.6f - d).coerceIn(0f, 1f) * (1f - smoothstep(1.5f, 3f, kickRadius))
        return maxOf(rim, ball)
    }

    /** Banda irregular del estilo de afuera: solida en medio, con 0.8 celdas de orilla suave. */
    private fun kickBlob(x: Float, y: Float, halfWidth: Float): Float {
        val theta = atan2(y, x)
        var r = kickRadius
        for (i in kickLobes.indices) r += kickLobeAmp[i] * cos(kickLobes[i] * theta + kickLobePhase[i])
        return ((halfWidth - abs(hypot(x, y) - r)) / 0.8f).coerceIn(0f, 1f)
    }

    private fun smoothstep(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
