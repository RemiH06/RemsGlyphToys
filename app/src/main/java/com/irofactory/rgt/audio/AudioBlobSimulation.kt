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
 * Cuarto anillo (KICK_RING), el del bombo, en tres estilos ([style]):
 *   - afuera: vive fuera de la matriz. Si la cancion tiene bombo marcado,
 *     cada golpe lo mete al borde como una banda irregular (blob, con forma
 *     nueva en cada golpe) con un destello, y se retira sin prisa hacia
 *     afuera, ya al brillo minimo, hasta perderse.
 *   - adentro: en reposo es una bolita tenue; cada golpe la lanza al borde
 *     con un destello y regresa sin prisa, ya al brillo minimo.
 *   - onda: se abre hasta el borde mientras la cancion tiene bombo, destella
 *     y engrosa con cada golpe; las otras figuras se encogen para caber.
 * Sin bombo marcado los golpes sueltos apenas lo mueven.
 *
 * Figuras ([style]): los agudos pueden ser triangulo o circulo (sin vertices;
 * con sonido aspero le salen puntas cortas), las voces diamante o diamante
 * almendra (horizontal, con puntas y siempre del mismo largo: lo que suena
 * solo la abre; con el reposo centro se lee como un ojo) y los graves
 * hexagono o pentagono.
 *
 * Reposo ([restPose], elegido en la app). Salvo en centro, que tanto se
 * forma el reposo sigue al volumen ([awake]); con musica todo vuelve a su
 * comportamiento normal:
 *   - centro: las tres figuras se juntan al centro.
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
 *   - oscuridad: todo se apaga y, con la musica, vuelve desvaneciendose.
 *   - reloj: los contornos de las tres figuras se convierten, punto por
 *     punto, en los trazos de la hora actual en 12 horas (HH:MM): el
 *     hexagono dibuja las decenas de la hora, el diamante las unidades de la
 *     hora y las decenas de los minutos (la mitad de su contorno cada una) y
 *     el triangulo las unidades de los minutos. Una letra A o P aparte (no
 *     sale de ninguna figura, como el ":") marca am/pm. Vuelven a sus
 *     figuras igual, punto por punto.
 *   - medusa, pez y burbujas: escenas propias ([RestScenes]) que no salen de
 *     las figuras: las figuras se desvanecen y la escena aparece, y al revés.
 * Plomada, derretir, piezas y burbujas usan la gravedad de [setGravity],
 * con todo e impulso de una sacudida: derretir y piezas la reciben completa;
 * la plomada y las burbujas la separan en "abajo" (lenta) y sacudida, que
 * columpia al triangulo y empuja a las burbujas de lado. Al volver la
 * musica, el agua y las piezas se reconstruyen en las figuras en REFORM_TIME:
 * cada particula viaja a un punto del contorno de su figura, y cada pieza se
 * mueve, crece y pasa de poligono al contorno vivo.
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
        // Circulo: cuantas puntas cortas le salen con sonido aspero
        const val CIRCLE_SPIKES = 8
        // Almendra: media anchura fija, horizontal
        const val ALMOND_HALF_WIDTH = 10.5f
        // Lados de la pieza solida de un circulo (en el reposo piezas)
        const val CIRCLE_PIECE_SIDES = 12
        // Energia del diamante sin linea melodica (instrumental): sus bandas tienen
        // su propio control de ganancia y sin esto crece igual con guitarra o piano
        const val MID_WITHOUT_MELODY = 0.35f

        // Anillo del bombo. false lo quita por completo
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
        // Onda: espacio que deja a las otras figuras cuando esta abierta
        const val KICK_RESERVE = KICK_THICKNESS + CLEARANCE + 0.4f
        // Blob de afuera: media anchura de la banda, cuanto se deforma y
        // radio al que llega el centro de la banda con un golpe completo
        const val KICK_BAND = 1.5f
        const val KICK_WOBBLE = 1.2f
        const val KICK_IN = 11f

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
        // Sacudida: segundos del filtro que separa la gravedad de la sacudida,
        // y cuanto columpia a la plomada cada m/s² de lado
        const val SLOW_GRAVITY = 0.5f
        const val PLUMB_SHAKE = 0.4f
        // Derretir y piezas: volumen con el que la musica ya "cambio de estado"
        // (debajo: se derriten o caen; arriba: vuelven las figuras), brillo
        // desde el que un LED de las figuras ya es agua con densidad completa,
        // y brillo del agua y las piezas
        const val SOLIDIFY = 0.85f
        const val REFORM = 0.9f
        const val MELT_FULL = 0.15f
        const val MELT_LEVEL = 0.45f
        const val PIECES_LEVEL = 0.5f
        // Reloj: puntos por digito, caja y posicion de cada uno (centradas en la
        // matriz; HH:MM en 24 horas, como ClockDigits.WIDTH/HEIGHT las define),
        // y radio y brillo de los dos puntos del separador ":"
        const val CLOCK_POINTS = 44
        val CLOCK_TOP = -ClockDigits.HEIGHT / 2f
        val CLOCK_LEFT = floatArrayOf(-9.8f, -5.2f, 2.2f, 6.8f)
        const val COLON_RADIUS = 0.55f
        const val COLON_LEVEL = 0.5f
        // Indicador am/pm ("AM" o "PM"): letras mas chicas que los digitos,
        // centradas debajo del reloj (los digitos no se mueven); espacio
        // entre las dos letras, grosor y brillo de su trazo
        const val LETTER_SCALE = 1.7f
        const val LETTER_GAP = 0.6f
        const val LETTER_SPACING = 1.9f
        val LETTER_WIDTH = 2f * LETTER_SCALE
        val LETTER_LEFT = -(2f * LETTER_WIDTH + LETTER_SPACING) / 2f
        val LETTER_M_LEFT = LETTER_LEFT + LETTER_WIDTH + LETTER_SPACING
        val LETTER_TOP = ClockDigits.HEIGHT / 2f + LETTER_GAP
        const val LETTER_THICKNESS = 0.75f
        const val LETTER_LEVEL = 0.5f
        // Brillo maximo de las escenas (medusa, pez, burbujas) antes del volumen
        const val SCENE_LEVEL = 0.6f

        // Segundos que tardan el agua o las piezas en volver a ser las figuras;
        // antes, el agua se reparte pegada al borde (radio y grosor de esa capa)
        const val REFORM_TIME = 2f
        const val SPREAD_TIME = 0.8f
        const val RIM_RADIUS = 11.6f
        const val RIM_DEPTH = 1.6f
        // Segundos en que cada figura se vuelve su pieza al empezar el silencio
        const val SOLIDIFY_TIME = 0.6f
        // Volumen con el que el reposo empieza a deshacerse y con el que ya no queda nada
        const val EYE_OPEN_FROM = 0.03f
        const val EYE_OPEN_TO = 0.3f
        // Puntos del contorno de los parpados o la rendija
        const val EYE_POINTS = 72
    }

    private enum class Form { POLYGON, CIRCLE }

    /**
     * Una figura: circulo deformado por el armonico de [sides] lobulos (su
     * poligono redondeado) mas lobulos organicos que la hacen respirar. Con
     * [form] puede ser circulo en vez de poligono.
     */
    private class Ring(
        var sides: Int,
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
        var form = Form.POLYGON
        /** Cuanto se vuelve poligono regular, de lados rectos y vertices afilados (logo, plomada). */
        var polygonBlend = 0f

        /** Que tan marcada esta la forma: mas definida entre mas suena; mas redonda si es suave. */
        private val definition: Float get() {
            if (form != Form.POLYGON) return 0f
            val playing = shapeAmp * (0.6f + 0.4f * energy) * (0.4f + 0.6f * harshness)
            return playing + (restAmp - playing) * restBlend
        }
        /** Largo de las puntas: solo aparecen con dureza, y crecen mas rapido que ella. */
        private val spikeLength get() = SPIKE * harshness * sqrt(harshness) * (if (form == Form.CIRCLE) 0.5f else 1f)
        private val spikeSides get() = if (form == Form.CIRCLE) CIRCLE_SPIKES else sides
        /** Que tan delgadas son las puntas: exponente del perfil del vertice. */
        private val spikeSharpness get() = 1f + 10f * harshness
        private val lobeReach get() = 0.5f * lobes.indices.sumOf { (lobeAmp[it] * lobeLevel[it]).toDouble() }.toFloat()
        val outer get() = (size * (1f + definition + spikeLength) + lobeReach) * squeeze
        val inner get() = ((size * (1f - definition) - lobeReach) * squeeze).coerceAtLeast(0.5f)

        fun radius(theta: Float): Float {
            val wave = cos(sides * (theta - rotation))
            var base = when (form) {
                Form.POLYGON -> 1f + definition * wave
                Form.CIRCLE -> 1f
            }
            if (polygonBlend > 0f && form == Form.POLYGON) {
                // Poligono regular en polares: la apotema entre el coseno del angulo al medio de su lado
                val segment = 2f * PI_F / sides
                var phi = (theta - rotation) % segment
                if (phi < 0f) phi += segment
                base += (cos(PI_F / sides) / cos(phi - segment / 2f) - base) * polygonBlend
            }
            // Punta: el perfil 0..1 del vertice elevado a una potencia alta queda
            // angosto, asi que solo la zona del vertice se estira
            val spikeWave = if (spikeSides == sides) wave else cos(spikeSides * (theta - rotation))
            val spike = spikeLength * ((0.5f + 0.5f * spikeWave).pow(spikeSharpness))
            var r = size * (base + spike)
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

    // Reloj: hora del dia que escribe el reposo "reloj", puesta por quien llama a step()
    private var clockHour = 0
    private var clockMinute = 0

    /** Figuras y estilo del bombo; se puede cambiar en cualquier momento. */
    var style = PulseStyle()
        set(value) {
            field = value
            high.form = if (value.high == HighShape.CIRCLE) Form.CIRCLE else Form.POLYGON
            low.sides = if (value.low == LowShape.PENTAGON) 5 else 6
        }
    private val midAlmond get() = style.mid == MidShape.ALMOND
    // Onda del bombo: que tan abierta esta (0 = bolita, 1 = en el borde)
    private var kickOpen = 0f

    // Gravedad en m/s², ejes de la matriz (y hacia abajo); la escribe el sensor desde otro hilo
    @Volatile private var gravityX = 0f
    @Volatile private var gravityY = 9.81f
    private var plumbSpin = 0f
    // Gravedad lenta ("donde es abajo") y sacudida (lo que sobra); la plomada y
    // las burbujas usan las dos por separado, el resto la gravedad completa
    private var slowGX = 0f
    private var slowGY = 9.81f
    private var shakeX = 0f
    private var shakeY = 0f

    // Derretir, piezas y oscuridad: si ya cambiaron de estado y cuanto se ven
    // (0 = figuras, 1 = agua, piezas o nada)
    private var transformed = false
    private var transformedShown = 0f
    // Reconstruccion: avance 0..1 (< 0 = no se esta reconstruyendo), de donde
    // sale cada particula, a que figura va y en que angulo; y estado de las piezas
    // Agua: de donde sale cada particula (radio y angulo), a donde llega en el
    // borde, y a que figura y angulo va despues
    private var reform = -1f
    private var reformFromRadius = FloatArray(0)
    private var reformFromAngle = FloatArray(0)
    private var reformRimRadius = FloatArray(0)
    private var reformRimAngle = FloatArray(0)
    private var reformRing = IntArray(0)
    private var reformAngle = FloatArray(0)
    private var reformPieces = emptyArray<FloatArray>()
    private val reformDuration get() = if (restPose == RestPose.MELT) SPREAD_TIME + REFORM_TIME else REFORM_TIME
    // Piezas: segundos desde que las figuras empezaron a volverse piezas (< 0 = ya lo son)
    private var solidify = -1f
    private val fluid by lazy { FlipFluidSimulation() }
    private val pieces by lazy { SolidPieces() }
    private val scenes by lazy { RestScenes() }
    private val meltRandom = Random(5)

    // 0 = reposo formado .. 1 = musica, y contorno de los parpados o la rendija (x, y intercalados)
    private var awake = 1f
    private val isEye get() = restPose == RestPose.CAT_EYE
    private val lid = FloatArray(EYE_POINTS * 2)

    /** Aceleracion del telefono en m/s², ya en ejes de la matriz (y hacia abajo). */
    fun setGravity(x: Float, y: Float) {
        gravityX = x
        gravityY = y
    }

    /**
     * [bands] son las 6 bandas y [harshness] la dureza de graves, medios y
     * agudos, ambas de SpectrumAnalysis; [loud] el volumen real 0..1.
     * [kick] y [kickPresence] mueven el anillo del bombo; [melody] (si hay voz,
     * silbido o viento) deja crecer al diamante.
     */
    fun step(dt: Float, bands: FloatArray, loud: Float, harshness: FloatArray,
             kick: Float = 0f, kickPresence: Float = 0f, melody: Float = 1f,
             hour: Int = 0, minute: Int = 0) {
        time += dt
        loudness = loud
        clockHour = hour
        clockMinute = minute
        val gx = gravityX
        val gy = gravityY
        val slowFollow = 1f - exp(-dt / SLOW_GRAVITY)
        slowGX += (gx - slowGX) * slowFollow
        slowGY += (gy - slowGY) * slowFollow
        shakeX = gx - slowGX
        shakeY = gy - slowGY

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
            val far = edge - KICK_THICKNESS * 0.6f
            kickOpen = 0f
            when (style.kick) {
                KickStyle.OUTSIDE -> {
                    for (i in kickLobes.indices) kickLobePhase[i] += kickLobeSpin[i] * dt
                    // Afuera del todo: ni la banda ni sus lobulos alcanzan un LED
                    val out = GlyphFrames.LED_RADIUS + KICK_BAND + KICK_WOBBLE * 1.5f + 0.5f
                    // Sin bombo marcado, un golpe suelto apenas se asoma por el borde
                    kickRadius = out - (out - KICK_IN) * (open + 0.35f * (1f - open)) * kickReach
                }
                KickStyle.INSIDE -> {
                    // Sin bombo marcado, un golpe suelto solo infla un poco la bolita
                    kickRadius = KICK_BALL + ((far - KICK_BALL) * open + 1.2f * (1f - open)) * kickReach
                }
                KickStyle.WAVE -> {
                    // Abierta mientras la cancion tiene bombo; el golpe solo destella
                    kickOpen = open
                    kickFlash = kick
                    kickRadius = KICK_BALL + (far - KICK_BALL) * open + 1.2f * (1f - open) * kick
                }
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
            // El triangulo del logo y la plomada: regular, de vertices afilados
            ring.polygonBlend = if (ring === high && (restPose == RestPose.LOGO || restPose == RestPose.PLUMB)) rest else 0f
        }
        stepTransformation(dt)
        if (restPose.isScene && awake < 0.999f) scenes.step(dt, restPose, slowGX, slowGY, shakeX, shakeY)
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
        mid.energy = (0.4f * bands[2] + 0.6f * bands[3]) * lerp(MID_WITHOUT_MELODY, 1f, melody)
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
                RestPose.CAT_EYE -> when (ring) {
                    low -> lerp(CAT_IRIS, target, awake)
                    high -> lerp(CAT_PUPIL, target, awake)
                    else -> target
                }
                // El reloj no cambia el tamano de las figuras: solo su contorno, al dibujarlas
                RestPose.CLOCK -> target
                RestPose.LOGO -> lerp(LOGO_SIZE[i], target, awake)
                RestPose.BOOM -> lerp(BOOM_SIZE[i], target, awake)
                RestPose.PLUMB -> lerp(PLUMB_SIZE[i], target, awake)
                // Ocultas mientras son agua, piezas o nada: reposo normal, para volver desde el centro
                RestPose.MELT, RestPose.PIECES, RestPose.VANISH,
                RestPose.JELLYFISH, RestPose.FISH, RestPose.BUBBLES -> target
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
        if (isEye || midAlmond) {
            // El diamante tiene simetria de 1/4 de vuelta: se envuelve a ±45° y,
            // al cerrarse el ojo (o siempre, si es la almendra), gira hasta
            // dejar sus vertices en horizontal
            val quarter = PI_F / 2f
            var a = mid.rotation % quarter
            if (a > quarter / 2f) a -= quarter
            if (a < -quarter / 2f) a += quarter
            val pull = if (midAlmond) 1f else 1f - awake
            mid.rotation = a * (1f - pull * (1f - exp(-dt / 0.3f)))
        }
        if (restPose == RestPose.PLUMB) {
            // Resorte hacia el suelo; con simetria de 1/3 de vuelta, cualquier vertice sirve
            val third = 2f * PI_F / 3f
            var error = (atan2(slowGY, slowGX) - high.rotation) % third
            if (error > third / 2f) error -= third
            if (error < -third / 2f) error += third
            // La sacudida de lado lo columpia, como a un pendulo
            val g = hypot(slowGX, slowGY).coerceAtLeast(1e-3f)
            val lateral = (-shakeX * slowGY + shakeY * slowGX) / g
            plumbSpin += (PLUMB_STIFFNESS * error - PLUMB_DAMPING * plumbSpin + PLUMB_SHAKE * lateral) * dt
            high.rotation += plumbSpin * dt * rest
        }
        // Temblor leve de los agudos
        high.x += 0.12f * high.energy * sin(time * 11f)
        high.y += 0.12f * high.energy * cos(time * 13f)

        // Anidado, de afuera hacia adentro: la mas chica queda dentro de la mas
        // grande con hueco visible (comprimiendose si no cabe) salvo que sus
        // tamanos se parezcan, que es cuando se cruzan. Luego, todas dentro del tope.
        // Con la onda del bombo abierta, las figuras le dejan su lugar en el borde
        val limit = minOf(shapeLimit, edge - kickOpen * KICK_RESERVE)
        for (ring in rings) ring.squeeze = 1f
        for (ring in rings) if (ring.outer > limit) ring.squeeze = limit / ring.outer
        val bySize = rings.sortedByDescending { it.size }
        for (i in bySize.indices) for (j in i + 1 until bySize.size) {
            val big = bySize[i]
            val small = bySize[j]
            var nest = smoothstep(CROSS, CROSS + CROSS_BLEND, big.size - small.size)
            // En reposo los parpados o la rendija no empujan ni los empujan: van encima
            when {
                // Almendra: su largo cruza a las demas, sin empujarlas
                midAlmond && (big === mid || small === mid) -> nest = 0f
                // Ojos: los parpados o la rendija van encima de las demas
                isEye && (big === mid || small === mid) -> nest *= awake
                // Logo y boom: sus tamanos ya estan elegidos, sin empujarse
                restPose == RestPose.LOGO || restPose == RestPose.BOOM ||
                    restPose == RestPose.PLUMB || restPose == RestPose.CLOCK -> nest *= awake
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
            val room = (limit - ring.outer).coerceAtLeast(0f)
            if (d > room && d > 1e-3f) { ring.x = ring.x / d * room; ring.y = ring.y / d * room }
        }
    }

    fun rasterize(): Array<FloatArray> {
        val grid = Array(n) { FloatArray(n) }
        val light = 0.5f + 0.5f * loudness
        val levels = FloatArray(rings.size) { (0.4f + 0.6f * rings[it].energy) * light }
        // Escenas: las figuras se desvanecen mientras la escena aparece
        val sceneShown = if (restPose.isScene) 1f - awake else 0f
        val sceneGrid = if (sceneShown > 0.001f) scenes.render(restPose) else null
        for (i in levels.indices) levels[i] *= 1f - sceneShown
        if (restPose == RestPose.PLUMB) {
            // Marco tenue, triangulo protagonista
            val rest = 1f - awake
            levels[0] *= lerp(1f, PLUMB_FRAME, rest)
            levels[1] *= lerp(1f, PLUMB_FRAME, rest)
            levels[2] *= lerp(1f, 1.6f, rest)
        }
        // Mientras son agua o piezas (o se reconstruyen), las figuras no se ven
        val fullLevels = levels.copyOf()
        val reforming = reform >= 0f
        val solidifying = solidify >= 0f
        val ringsShown = if (reforming || solidifying) 0f else 1f - transformedShown
        for (i in levels.indices) levels[i] *= ringsShown
        val fluidGrid = if (restPose == RestPose.MELT && transformedShown > 0.001f && !reforming) fluid.rasterize() else null
        val ease = smoothstep(0f, REFORM_TIME, reform)
        val particleGrid = if (reforming && restPose == RestPose.MELT) reformParticles(reform, fullLevels) else null
        // Al volverse piezas: cada figura va de su contorno a su pieza, que ya cae
        val solidifyEase = 1f - smoothstep(0f, SOLIDIFY_TIME, solidify)
        val pieceStates = if (solidifying) pieces.snapshot() else null
        // Tenue en reposo y al regresar; solo el golpe destella
        var kickLevel = if (style.kick == KickStyle.WAVE) (0.35f + 0.65f * kickFlash) * light
            else (KICK_DIM + (1f - KICK_DIM) * kickFlash) * light
        if (restPose == RestPose.BOOM) kickLevel += (BOOM_LEVEL * light - kickLevel) * (1f - awake)
        if (restPose == RestPose.VANISH) kickLevel *= ringsShown
        kickLevel *= 1f - sceneShown
        val kickThickness = KICK_THICKNESS * (1f + 0.6f * kickFlash * (if (style.kick == KickStyle.WAVE) kickOpen else 1f))
        val eyeClosed = if (isEye) 1f - awake else 0f
        // Parpados, rendija o almendra: el diamante se dibuja como contorno con su grosor real
        val midOutline = eyeClosed > 0.001f || midAlmond
        if (midOutline) buildLid(eyeClosed)
        val clock = if (restPose == RestPose.CLOCK) buildClock(1f - awake) else null

        for (row in 0 until n) {
            for (col in 0 until n) {
                val x = col + 0.5f - center
                val y = row + 0.5f - center
                if (hypot(x, y) > GlyphFrames.LED_RADIUS) continue

                // Mezcla tipo "screen": los cruces brillan mas sin saturar de golpe
                var dark = 1f
                if (clock != null) {
                    for (i in rings.indices) {
                        val d = strokeDistance(clock.points[i], clock.subIds[i], x, y)
                        dark *= 1f - (1f - d / rings[i].thickness).coerceIn(0f, 1f) * levels[i]
                    }
                    for (dot in clock.colons) dark *= 1f - colonDot(x, y, dot) * COLON_LEVEL * light * clock.rest
                    for (i in clock.letters.indices) {
                        val letterD = strokeDistance(clock.letters[i], clock.letterSubs[i], x, y)
                        dark *= 1f - (1f - letterD / LETTER_THICKNESS).coerceIn(0f, 1f) * LETTER_LEVEL * light * clock.rest
                    }
                } else if (midOutline) {
                    val distance = lidDistance(x, y)
                    dark *= 1f - rim(x, y, low) * levels[0]
                    dark *= 1f - (1f - distance / mid.thickness).coerceIn(0f, 1f) * levels[1]
                    dark *= 1f - rim(x, y, high) * levels[2]
                } else {
                    for (i in rings.indices) dark *= 1f - rim(x, y, rings[i]) * levels[i]
                }
                if (KICK_RING) {
                    val shape = if (style.kick == KickStyle.OUTSIDE) kickBlob(x, y, KICK_BAND * (1f + 0.3f * kickFlash))
                        else kickShape(hypot(x, y), kickThickness)
                    dark *= 1f - shape * kickLevel
                }
                if (reforming) {
                    if (particleGrid != null) dark *= 1f - particleGrid[row][col]
                    if (restPose == RestPose.PIECES) for (i in rings.indices) {
                        dark *= 1f - morphRim(x, y, reformPieces[i], rings[i], ease) * lerp(PIECES_LEVEL, fullLevels[i], ease)
                    }
                } else if (pieceStates != null) {
                    for (i in rings.indices) {
                        dark *= 1f - morphRim(x, y, pieceStates[i], rings[i], solidifyEase) * lerp(PIECES_LEVEL, fullLevels[i], solidifyEase)
                    }
                } else if (transformedShown > 0.001f) {
                    if (fluidGrid != null) dark *= 1f - fluidGrid[row][col] * MELT_LEVEL * transformedShown
                    if (restPose == RestPose.PIECES) dark *= 1f - pieces.brightness(x, y) * PIECES_LEVEL * transformedShown
                }
                if (sceneGrid != null) dark *= 1f - sceneGrid[row][col] * SCENE_LEVEL * light * sceneShown
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
     * Contorno de la rendija del gato: del diamante (abierto) a una almendra
     * vertical con puntas afiladas (reposo), punto por punto: x = ±W·(1 − (y/H)²).
     */
    private fun buildLid(closed: Float) {
        for (i in 0 until EYE_POINTS) {
            val theta = 2f * PI_F * i / EYE_POINTS
            val (openX, openY) = midOpenPoint(theta)
            val almondX = SLIT_WIDTH * cos(theta) * abs(cos(theta))
            val almondY = SLIT_HEIGHT * sin(theta)
            lid[2 * i] = mid.x + lerp(openX, almondX, closed)
            lid[2 * i + 1] = mid.y + lerp(openY, almondY, closed)
        }
    }

    /**
     * Punto del contorno del diamante en [theta], relativo a su centro: la
     * almendra si [midAlmond] (de largo fijo, que el tamano solo abre hasta
     * volverse tan alta como larga; los lobulos y puntas la deforman en la
     * misma proporcion que al diamante), si no el diamante normal.
     */
    private fun midOpenPoint(theta: Float): Pair<Float, Float> {
        val r = mid.radius(theta)
        val c = cos(theta)
        val s = sin(theta)
        if (!midAlmond) return r * c to r * s
        val size = (mid.size * mid.squeeze).coerceAtLeast(0.5f)
        val opening = size.coerceAtMost(ALMOND_HALF_WIDTH)
        return (ALMOND_HALF_WIDTH * c * r / size) to (opening * s * abs(s) * r / size)
    }

    /** Distancia de (x, y) a la linea de la rendija, en celdas. */
    private fun lidDistance(x: Float, y: Float): Float {
        var best = Float.MAX_VALUE
        var j = EYE_POINTS - 1
        for (i in 0 until EYE_POINTS) {
            val ax = lid[2 * j]; val ay = lid[2 * j + 1]
            val bx = lid[2 * i]; val by = lid[2 * i + 1]
            val ex = bx - ax; val ey = by - ay
            val t = (((x - ax) * ex + (y - ay) * ey) / (ex * ex + ey * ey + 1e-6f)).coerceIn(0f, 1f)
            best = minOf(best, hypot(x - ax - t * ex, y - ay - t * ey))
            j = i
        }
        return best
    }

    /** Contornos de las tres figuras mezclados hacia los trazos de la hora, los dos puntos de ":" y la letra am/pm. */
    private class ClockOverlay(
        val points: Array<FloatArray>, val subIds: Array<IntArray>, val colons: Array<FloatArray>,
        val letters: Array<FloatArray>, val letterSubs: Array<IntArray>, val rest: Float
    )

    /**
     * Arma [ClockOverlay] para el reposo "reloj": [rest] 0 = las figuras tal
     * cual, 1 = la hora. 12 horas con indicador am/pm (letra A o P, ya que no
     * hay una cuarta figura de donde sacar dos letras completas).
     */
    private fun buildClock(rest: Float): ClockOverlay {
        val h12 = clockHour % 12
        val hour12 = if (h12 == 0) 12 else h12
        val h1 = hour12 / 10; val h2 = hour12 % 10
        val m1 = clockMinute / 10; val m2 = clockMinute % 10
        val lowRing = ringToDigit(low, { th -> val r = low.radius(th); r * cos(th) to r * sin(th) },
            0f, 2f * PI_F, h1, CLOCK_LEFT[0], CLOCK_POINTS, rest, 0)
        val midA = ringToDigit(mid, ::midOpenPoint, 0f, PI_F, h2, CLOCK_LEFT[1], CLOCK_POINTS, rest, 0)
        val midB = ringToDigit(mid, ::midOpenPoint, PI_F, 2f * PI_F, m1, CLOCK_LEFT[2], CLOCK_POINTS, rest, 10)
        val highRing = ringToDigit(high, { th -> val r = high.radius(th); r * cos(th) to r * sin(th) },
            0f, 2f * PI_F, m2, CLOCK_LEFT[3], CLOCK_POINTS, rest, 0)
        val colons = arrayOf(
            floatArrayOf(0f, CLOCK_TOP + 1.3f * ClockDigits.SCALE),
            floatArrayOf(0f, CLOCK_TOP + 2.7f * ClockDigits.SCALE)
        )
        val (letter, letterSub) = ClockDigits.letterPoints(clockHour >= 12, LETTER_LEFT, LETTER_TOP, LETTER_SCALE, CLOCK_POINTS)
        val (letterM, letterMSub) = ClockDigits.letterMPoints(LETTER_M_LEFT, LETTER_TOP, LETTER_SCALE, CLOCK_POINTS)
        return ClockOverlay(
            arrayOf(lowRing.first, midA.first + midB.first, highRing.first),
            arrayOf(lowRing.second, midA.second + midB.second, highRing.second),
            colons, arrayOf(letter, letterM), arrayOf(letterSub, letterMSub), rest
        )
    }

    /**
     * Puntos del contorno propio de [ring] ([ownPoint], relativo a su centro)
     * entre [thetaFrom] y [thetaTo] parejos, mezclados hacia el digito
     * [digit] (su caja en [left], [CLOCK_TOP]) en [rest]. [subOffset] evita
     * que el id de sub-trazo del digito choque con el de otro digito del
     * mismo anillo (el diamante dibuja dos, uno en cada mitad de su angulo).
     */
    private fun ringToDigit(
        ring: Ring, ownPoint: (Float) -> Pair<Float, Float>, thetaFrom: Float, thetaTo: Float,
        digit: Int, left: Float, count: Int, rest: Float, subOffset: Int
    ): Pair<FloatArray, IntArray> {
        val (target, subIds) = ClockDigits.digitPoints(digit, left, CLOCK_TOP, count)
        val points = FloatArray(count * 2)
        for (i in 0 until count) {
            val theta = thetaFrom + (thetaTo - thetaFrom) * i / (count - 1).coerceAtLeast(1)
            val (ox, oy) = ownPoint(theta)
            points[2 * i] = lerp(ring.x + ox, target[2 * i], rest)
            points[2 * i + 1] = lerp(ring.y + oy, target[2 * i + 1], rest)
            if (subOffset != 0) subIds[i] += subOffset
        }
        return points to subIds
    }

    /** Distancia de (x, y) a la polilinea [points]; conecta dos puntos consecutivos solo si comparten [subIds]. */
    private fun strokeDistance(points: FloatArray, subIds: IntArray, x: Float, y: Float): Float {
        var best = Float.MAX_VALUE
        for (i in 1 until subIds.size) {
            if (subIds[i] != subIds[i - 1]) continue
            val ax = points[2 * (i - 1)]; val ay = points[2 * (i - 1) + 1]
            val bx = points[2 * i]; val by = points[2 * i + 1]
            val ex = bx - ax; val ey = by - ay
            val t = (((x - ax) * ex + (y - ay) * ey) / (ex * ex + ey * ey + 1e-6f)).coerceIn(0f, 1f)
            best = minOf(best, hypot(x - ax - t * ex, y - ay - t * ey))
        }
        return best
    }

    /** Brillo 0..1 de un punto redondo del separador ":" de radio [COLON_RADIUS], centrado en [dot]. */
    private fun colonDot(x: Float, y: Float, dot: FloatArray): Float =
        (1f - hypot(x - dot[0], y - dot[1]) / COLON_RADIUS).coerceIn(0f, 1f)

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
        val g = hypot(slowGX, slowGY).coerceAtLeast(1e-3f)
        val k = PLUMB_HANG * (1f - awake) / g
        return slowGX * k to slowGY * k
    }

    /**
     * Derretir, piezas y oscuridad: con el silencio las figuras se vuelven
     * agua o piezas (una vez, tal como estaban) o se apagan, y al volver la
     * musica regresan: reconstruyendose desde el agua o las piezas, o
     * desvaneciendose en oscuridad. Cambian al bajar de SOLIDIFY y regresan
     * solo al volver arriba de REFORM: con un solo umbral parpadearian en
     * cuanto el volumen ronde ese valor.
     */
    private fun stepTransformation(dt: Float) {
        val transforms = restPose == RestPose.MELT || restPose == RestPose.PIECES || restPose == RestPose.VANISH
        if (!transforms) {
            transformed = false
            transformedShown = 0f
            reform = -1f
            return
        }
        if (reform >= 0f) {
            // Una reconstruccion siempre termina; si volvio el silencio, despues se transforma otra vez
            reform += dt
            if (reform >= reformDuration) reform = -1f
            return
        }
        if (!transformed && awake < SOLIDIFY) {
            transformed = true
            if (restPose == RestPose.MELT) {
                // El agua nace justo donde estaban las figuras (completas): el cambio no se nota
                transformedShown = 0f
                fluid.pour(meltPoints(rasterize()))
                transformedShown = 1f
            } else if (restPose == RestPose.PIECES) {
                // Sin desvanecer: cada figura se transforma en su pieza
                pieces.reset(
                    floatArrayOf(low.rotation, mid.rotation, high.rotation),
                    rings.map { if (it.form == Form.CIRCLE) CIRCLE_PIECE_SIDES else it.sides }.toIntArray()
                )
                solidify = 0f
                transformedShown = 1f
            }
        } else if (transformed && awake > REFORM) {
            transformed = false
            if (restPose != RestPose.VANISH) {
                startReform()
                transformedShown = 0f
                solidify = -1f
                return
            }
        }
        val target = if (transformed) 1f else 0f
        transformedShown += (target - transformedShown) * (1f - exp(-dt / 0.25f))
        if (solidify >= 0f) {
            solidify += dt
            if (solidify >= SOLIDIFY_TIME) solidify = -1f
        }
        if (transformedShown > 0.001f && restPose != RestPose.VANISH) {
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

    /**
     * Arranca la reconstruccion. Agua: las particulas mas lejanas del centro van
     * a la figura mas grande, en proporcion a su tamano, y cada una al angulo
     * desde el que ya la ve: viajan casi en linea recta. Piezas: se guarda
     * donde quedo cada una.
     */
    private fun startReform() {
        reform = 0f
        if (restPose == RestPose.PIECES) {
            reformPieces = pieces.snapshot()
            return
        }
        // Al borde: angulos repartidos parejo, en el mismo orden en que ya estan
        // alrededor de su angulo promedio; asi el agua se extiende hacia los dos
        // lados por el borde, sin cruzar el centro
        val p = fluid.positions()
        val count = p.size / 2
        reformFromRadius = FloatArray(count)
        reformFromAngle = FloatArray(count)
        reformRimRadius = FloatArray(count)
        reformRimAngle = FloatArray(count)
        reformRing = IntArray(count)
        reformAngle = FloatArray(count)
        var sumSin = 0f
        var sumCos = 0f
        for (i in 0 until count) {
            val a = atan2(p[2 * i + 1] - center, p[2 * i] - center)
            sumSin += sin(a); sumCos += cos(a)
        }
        val mean = atan2(sumSin, sumCos)
        val relative = FloatArray(count) { i ->
            var a = atan2(p[2 * i + 1] - center, p[2 * i] - center) - mean
            while (a > PI_F) a -= 2f * PI_F
            while (a < -PI_F) a += 2f * PI_F
            a
        }
        val total = rings.sumOf { it.size.toDouble() }.toFloat()
        for ((rank, i) in (0 until count).sortedBy { relative[it] }.withIndex()) {
            reformFromRadius[i] = hypot(p[2 * i] - center, p[2 * i + 1] - center)
            reformFromAngle[i] = mean + relative[i]
            val rimAngle = mean + 2f * PI_F * ((rank + 0.5f) / count - 0.5f)
            val rimRadius = RIM_RADIUS - RIM_DEPTH * meltRandom.nextFloat()
            reformRimRadius[i] = rimRadius
            reformRimAngle[i] = rimAngle
            // Figura al azar, mas probable la mas grande; cada particula va hacia el centro desde donde quedo
            var pick = meltRandom.nextFloat() * total
            var r = 0
            while (r < rings.lastIndex && pick > rings[r].size) { pick -= rings[r].size; r++ }
            reformRing[i] = r
            reformAngle[i] = atan2(rimRadius * sin(rimAngle) - rings[r].y, rimRadius * cos(rimAngle) - rings[r].x)
        }
    }

    /**
     * Brillo de las particulas en vuelo: primero se extienden por el borde
     * (en polares, asi lo recorren en vez de cruzar el centro) y luego cada una
     * va a su punto en el contorno vivo de su figura. Cuenta por LED con el
     * brillo de su figura, asi al llegar se ve igual que la figura que la
     * reemplaza. [elapsed] en segundos desde que empezo.
     */
    private fun reformParticles(elapsed: Float, levels: FloatArray): Array<FloatArray> {
        val spread = smoothstep(0f, SPREAD_TIME, elapsed)
        val ease = smoothstep(SPREAD_TIME, SPREAD_TIME + REFORM_TIME, elapsed)
        val count = Array(n) { FloatArray(n) }
        val level = Array(n) { FloatArray(n) }
        for (i in reformRing.indices) {
            val ring = rings[reformRing[i]]
            val angle = lerp(reformFromAngle[i], reformRimAngle[i], spread)
            val distance = lerp(reformFromRadius[i], reformRimRadius[i], spread)
            val theta = reformAngle[i]
            val radius = ring.radius(theta)
            val x = lerp(center + distance * cos(angle), center + ring.x + radius * cos(theta), ease)
            val y = lerp(center + distance * sin(angle), center + ring.y + radius * sin(theta), ease)
            val col = x.toInt()
            val row = y.toInt()
            if (row !in 0 until n || col !in 0 until n) continue
            count[row][col] += 1f
            level[row][col] = maxOf(level[row][col], lerp(MELT_LEVEL, levels[reformRing[i]], ease))
        }
        for (row in 0 until n) for (col in 0 until n) {
            count[row][col] = level[row][col] * (count[row][col] / 3f).coerceAtMost(1f)
        }
        return count
    }

    /**
     * Contorno de una pieza a medio camino de su figura: centro, tamano y
     * forma van del poligono regular (donde quedo la pieza) al contorno vivo.
     * [piece]: x, y, angulo, radio y lados, como [SolidPieces.snapshot].
     */
    private fun morphRim(x: Float, y: Float, piece: FloatArray, ring: Ring, ease: Float): Float {
        val cx = lerp(piece[0], ring.x, ease)
        val cy = lerp(piece[1], ring.y, ease)
        val dx = x - cx
        val dy = y - cy
        val theta = atan2(dy, dx)
        // Poligono regular en polares: la apotema entre el coseno del angulo al medio de su lado
        val sides = piece[4]
        val segment = 2f * PI_F / sides
        var phi = (theta - piece[2]) % segment
        if (phi < 0f) phi += segment
        val polygon = piece[3] * cos(PI_F / sides) / cos(phi - segment / 2f)
        val radius = lerp(polygon, ring.radius(theta), ease)
        val thickness = lerp(0.9f, ring.thickness, ease)
        return (1f - abs(hypot(dx, dy) - radius) / thickness).coerceIn(0f, 1f)
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
