package com.example.batallanavalgame

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.children

class BatallaNavalActivity : AppCompatActivity() {

    // UI Components
    private lateinit var tvEstadoJuego: TextView
    private lateinit var tvJugadorActual: TextView
    private lateinit var tvInstrucciones: TextView
    private lateinit var btnRotarBarco: Button
    private lateinit var btnSiguienteBarco: Button
    private lateinit var btnTerminarConfiguracion: Button
    private lateinit var btnCambiarJugador: Button
    private lateinit var btnGuardarPartida: Button
    private lateinit var btnNuevaPartida: Button
    private lateinit var glTablero: GridLayout

    // Game Constants
    private val TABLERO_SIZE = 10
    private val BARCOS = listOf(
        Barco(5, "Portaaviones"),  // Tamaño 5
        Barco(4, "Acorazado"),     // Tamaño 4
        Barco(3, "Crucero"),       // Tamaño 3
        Barco(3, "Submarino"),     // Tamaño 3
        Barco(2, "Destructor")     // Tamaño 2
    )

    // Game State
    private var faseActual = FaseJuego.CONFIGURACION
    private var jugadorActual = 1
    private var barcoActualIndex = 0
    private var orientacionHorizontal = true

    // Tableros de juego (uno para cada jugador)
    private val tableroJugador1 = Array(TABLERO_SIZE) { Array(TABLERO_SIZE) { EstadoCelda.VACIA } }
    private val tableroJugador2 = Array(TABLERO_SIZE) { Array(TABLERO_SIZE) { EstadoCelda.VACIA } }
    private val tableroAtaquesJugador1 = Array(TABLERO_SIZE) { Array(TABLERO_SIZE) { false } }
    private val tableroAtaquesJugador2 = Array(TABLERO_SIZE) { Array(TABLERO_SIZE) { false } }

    // Barcos colocados
    private val barcosJugador1 = mutableListOf<BarcoColocado>()
    private val barcosJugador2 = mutableListOf<BarcoColocado>()

    // Manager para guardar/cargar partidas
    private lateinit var batallaNavalManager: BatallaNavalManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_batalla_naval)

        // Inicializar UI components
        inicializarViews()

        // Inicializar manager
        batallaNavalManager = BatallaNavalManager(this)

        // Configurar listeners
        configurarBotones()

        // Inicializar tablero
        inicializarTablero()

        // Comprobar si debemos cargar partida
        if (intent.getBooleanExtra("CARGAR_PARTIDA", false)) {
            cargarPartida()
        } else {
            // Iniciar juego nuevo
            actualizarUI()
        }
    }

    private fun inicializarViews() {
        tvEstadoJuego = findViewById(R.id.tvEstadoJuego)
        tvJugadorActual = findViewById(R.id.tvJugadorActual)
        tvInstrucciones = findViewById(R.id.tvInstrucciones)
        btnRotarBarco = findViewById(R.id.btnRotarBarco)
        btnSiguienteBarco = findViewById(R.id.btnSiguienteBarco)
        btnTerminarConfiguracion = findViewById(R.id.btnTerminarConfiguracion)
        btnCambiarJugador = findViewById(R.id.btnCambiarJugador)
        btnGuardarPartida = findViewById(R.id.btnGuardarPartida)
        btnNuevaPartida = findViewById(R.id.btnNuevaPartida)
        glTablero = findViewById(R.id.glTablero)
    }

    private fun configurarBotones() {
        btnRotarBarco.setOnClickListener {
            orientacionHorizontal = !orientacionHorizontal
            Toast.makeText(
                this,
                if (orientacionHorizontal) R.string.orientacion_horizontal else R.string.orientacion_vertical,
                Toast.LENGTH_SHORT
            ).show()
        }

        btnSiguienteBarco.setOnClickListener {
            if (barcoActualIndex < BARCOS.size - 1) {
                barcoActualIndex++
                actualizarInstrucciones()
            } else {
                Toast.makeText(this, R.string.no_mas_barcos, Toast.LENGTH_SHORT).show()
                btnTerminarConfiguracion.visibility = View.VISIBLE
            }
        }

        btnTerminarConfiguracion.setOnClickListener {
            if (jugadorActual == 1) {
                // Cambiar al jugador 2 para configuración
                jugadorActual = 2
                barcoActualIndex = 0
                btnTerminarConfiguracion.visibility = View.GONE
                actualizarUI()
            } else {
                // Ambos jugadores han terminado la configuración
                iniciarFaseAtaque()
            }
        }

        btnCambiarJugador.setOnClickListener {
            cambiarJugador()
        }

        btnGuardarPartida.setOnClickListener {
            guardarPartida()
        }

        btnNuevaPartida.setOnClickListener {
            mostrarDialogoNuevaPartida()
        }
    }

    private fun inicializarTablero() {
        glTablero.removeAllViews()

        for (fila in 0 until TABLERO_SIZE) {
            for (columna in 0 until TABLERO_SIZE) {
                val celda = View(this)
                val params = GridLayout.LayoutParams()

                // Calcular tamaño de celda basado en el tamaño de pantalla
                val size = (resources.displayMetrics.widthPixels - glTablero.paddingLeft -
                        glTablero.paddingRight - (TABLERO_SIZE * 2)) / TABLERO_SIZE

                params.width = size
                params.height = size
                params.rowSpec = GridLayout.spec(fila)
                params.columnSpec = GridLayout.spec(columna)

                celda.layoutParams = params
                celda.background = ContextCompat.getDrawable(this, R.drawable.cell_empty)
                celda.tag = "$fila,$columna"

                celda.setOnClickListener {
                    val position = (it.tag as String).split(",")
                    val row = position[0].toInt()
                    val col = position[1].toInt()
                    manejarClick(row, col)
                }

                glTablero.addView(celda)
            }
        }
    }

    private fun actualizarUI() {
        // Actualizar textos según la fase actual
        when (faseActual) {
            FaseJuego.CONFIGURACION -> {
                tvEstadoJuego.text = getString(R.string.fase_configuracion)
                tvJugadorActual.text = getString(
                    if (jugadorActual == 1) R.string.jugador_1 else R.string.jugador_2
                )
                actualizarInstrucciones()

                btnRotarBarco.visibility = View.VISIBLE
                btnSiguienteBarco.visibility = View.VISIBLE
                btnCambiarJugador.visibility = View.GONE

                // Mostrar tablero vacío para el jugador actual
                actualizarVistaTablero()
            }
            FaseJuego.ATAQUE -> {
                tvEstadoJuego.text = getString(R.string.fase_ataque)
                tvJugadorActual.text = getString(
                    if (jugadorActual == 1) R.string.jugador_1 else R.string.jugador_2
                )
                tvInstrucciones.text = getString(R.string.instrucciones_ataque)

                btnRotarBarco.visibility = View.GONE
                btnSiguienteBarco.visibility = View.GONE
                btnTerminarConfiguracion.visibility = View.GONE
                btnCambiarJugador.visibility = View.VISIBLE

                // Mostrar tablero del oponente para ataques
                actualizarVistaTablero()
            }
        }
    }

    private fun actualizarInstrucciones() {
        val barcoActual = BARCOS[barcoActualIndex]
        tvInstrucciones.text = getString(
            R.string.instrucciones_colocar_barco,
            barcoActual.nombre,
            barcoActual.longitud
        )
    }

    private fun actualizarVistaTablero() {
        val tableroActual: Array<Array<EstadoCelda>>
        val tableroAtaques: Array<Array<Boolean>>

        if (faseActual == FaseJuego.CONFIGURACION) {
            // En fase de configuración, mostrar el tablero propio
            tableroActual = if (jugadorActual == 1) tableroJugador1 else tableroJugador2
            tableroAtaques = if (jugadorActual == 1) tableroAtaquesJugador1 else tableroAtaquesJugador2

            // Actualizar cada celda
            var index = 0
            for (fila in 0 until TABLERO_SIZE) {
                for (columna in 0 until TABLERO_SIZE) {
                    val celda = glTablero.getChildAt(index++)
                    when (tableroActual[fila][columna]) {
                        EstadoCelda.VACIA -> celda.background = ContextCompat.getDrawable(this, R.drawable.cell_empty)
                        EstadoCelda.BARCO -> celda.background = ContextCompat.getDrawable(this, R.drawable.cell_ship)
                        EstadoCelda.AGUA -> celda.background = ContextCompat.getDrawable(this, R.drawable.cell_water)
                        EstadoCelda.IMPACTO -> celda.background = ContextCompat.getDrawable(this, R.drawable.cell_hit)
                    }
                }
            }
        } else {
            // En fase de ataque, mostrar el tablero del oponente
            tableroActual = if (jugadorActual == 1) tableroJugador2 else tableroJugador1
            tableroAtaques = if (jugadorActual == 1) tableroAtaquesJugador1 else tableroAtaquesJugador2

            // Actualizar cada celda, ocultando barcos no atacados
            var index = 0
            for (fila in 0 until TABLERO_SIZE) {
                for (columna in 0 until TABLERO_SIZE) {
                    val celda = glTablero.getChildAt(index++)
                    if (tableroAtaques[fila][columna]) {
                        // Celda ya atacada
                        when (tableroActual[fila][columna]) {
                            EstadoCelda.BARCO -> celda.background = ContextCompat.getDrawable(this, R.drawable.cell_hit)
                            else -> celda.background = ContextCompat.getDrawable(this, R.drawable.cell_water)
                        }
                    } else {
                        // Celda no atacada
                        celda.background = ContextCompat.getDrawable(this, R.drawable.cell_empty)
                    }
                }
            }
        }
    }

    private fun manejarClick(fila: Int, columna: Int) {
        when (faseActual) {
            FaseJuego.CONFIGURACION -> manejarClickConfiguracion(fila, columna)
            FaseJuego.ATAQUE -> manejarClickAtaque(fila, columna)
        }
    }

    private fun manejarClickConfiguracion(fila: Int, columna: Int) {
        val barcoActual = BARCOS[barcoActualIndex]
        val tableroActual = if (jugadorActual == 1) tableroJugador1 else tableroJugador2
        val barcosJugador = if (jugadorActual == 1) barcosJugador1 else barcosJugador2

        // Verificar si el barco cabe en esa posición
        if (comprobarPosicionValida(fila, columna, barcoActual.longitud, orientacionHorizontal, tableroActual)) {
            // Colocar barco
            val posiciones = mutableListOf<Pair<Int, Int>>()

            for (i in 0 until barcoActual.longitud) {
                val nuevaFila = if (orientacionHorizontal) fila else fila + i
                val nuevaColumna = if (orientacionHorizontal) columna + i else columna

                tableroActual[nuevaFila][nuevaColumna] = EstadoCelda.BARCO
                posiciones.add(Pair(nuevaFila, nuevaColumna))
            }

            // Registrar barco colocado
            barcosJugador.add(BarcoColocado(barcoActual.longitud, posiciones))

            // Pasar al siguiente barco
            if (barcoActualIndex < BARCOS.size - 1) {
                barcoActualIndex++
                actualizarInstrucciones()
            } else {
                Toast.makeText(this, R.string.todos_barcos_colocados, Toast.LENGTH_SHORT).show()
                btnTerminarConfiguracion.visibility = View.VISIBLE
            }

            // Actualizar vista
            actualizarVistaTablero()
        } else {
            Toast.makeText(this, R.string.posicion_invalida, Toast.LENGTH_SHORT).show()
        }
    }

    private fun manejarClickAtaque(fila: Int, columna: Int) {
        val tableroOponente = if (jugadorActual == 1) tableroJugador2 else tableroJugador1
        val tableroAtaques = if (jugadorActual == 1) tableroAtaquesJugador1 else tableroAtaquesJugador2
        val barcosOponente = if (jugadorActual == 1) barcosJugador2 else barcosJugador1

        // Verificar si esa celda ya fue atacada
        if (tableroAtaques[fila][columna]) {
            Toast.makeText(this, R.string.ya_atacada, Toast.LENGTH_SHORT).show()
            return
        }

        // Registrar ataque
        tableroAtaques[fila][columna] = true

        // Comprobar resultado
        val impacto = tableroOponente[fila][columna] == EstadoCelda.BARCO
        if (impacto) {
            Toast.makeText(this, R.string.impacto, Toast.LENGTH_SHORT).show()

            // Comprobar si el barco ha sido hundido
            val barcoImpactado = barcosOponente.find { barco ->
                barco.posiciones.any { it.first == fila && it.second == columna }
            }

            if (barcoImpactado != null) {
                val todasPosicionesAtacadas = barcoImpactado.posiciones.all { posicion ->
                    tableroAtaques[posicion.first][posicion.second]
                }

                if (todasPosicionesAtacadas) {
                    Toast.makeText(this, R.string.barco_hundido, Toast.LENGTH_SHORT).show()

                    // Comprobar victoria
                    val todosLosBarcosHundidos = barcosOponente.all { barco ->
                        barco.posiciones.all { posicion ->
                            tableroAtaques[posicion.first][posicion.second]
                        }
                    }

                    if (todosLosBarcosHundidos) {
                        mostrarDialogoVictoria()
                        return
                    }
                }
            }
        } else {
            Toast.makeText(this, R.string.agua, Toast.LENGTH_SHORT).show()
        }

        // Actualizar vista y cambiar turno
        actualizarVistaTablero()
    }

    private fun comprobarPosicionValida(
        fila: Int,
        columna: Int,
        longitud: Int,
        horizontal: Boolean,
        tablero: Array<Array<EstadoCelda>>
    ): Boolean {
        // Comprobar límites del tablero
        if (horizontal) {
            if (columna + longitud > TABLERO_SIZE) return false
        } else {
            if (fila + longitud > TABLERO_SIZE) return false
        }

        // Comprobar que no hay otros barcos en la posición
        for (i in 0 until longitud) {
            val nuevaFila = if (horizontal) fila else fila + i
            val nuevaColumna = if (horizontal) columna + i else columna

            if (tablero[nuevaFila][nuevaColumna] != EstadoCelda.VACIA) {
                return false
            }
        }

        return true
    }

    private fun iniciarFaseAtaque() {
        faseActual = FaseJuego.ATAQUE
        jugadorActual = 1 // Empieza el jugador 1
        actualizarUI()
    }

    private fun cambiarJugador() {
        jugadorActual = if (jugadorActual == 1) 2 else 1
        actualizarUI()

        // Mostrar diálogo de cambio de jugador
        AlertDialog.Builder(this)
            .setTitle(R.string.cambio_jugador)
            .setMessage(getString(R.string.turno_jugador, jugadorActual))
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun guardarPartida() {
        val estadoJuego = EstadoJuego(
            faseActual,
            jugadorActual,
            barcoActualIndex,
            orientacionHorizontal,
            tableroJugador1,
            tableroJugador2,
            tableroAtaquesJugador1,
            tableroAtaquesJugador2,
            barcosJugador1,
            barcosJugador2
        )

        batallaNavalManager.guardarPartida(estadoJuego)
        Toast.makeText(this, R.string.partida_guardada, Toast.LENGTH_SHORT).show()
    }

    private fun cargarPartida() {
        val estadoJuego = batallaNavalManager.cargarPartida()
        if (estadoJuego != null) {
            // Restaurar estado
            faseActual = estadoJuego.faseActual
            jugadorActual = estadoJuego.jugadorActual
            barcoActualIndex = estadoJuego.barcoActualIndex
            orientacionHorizontal = estadoJuego.orientacionHorizontal

            // Copiar tableros
            for (i in 0 until TABLERO_SIZE) {
                for (j in 0 until TABLERO_SIZE) {
                    tableroJugador1[i][j] = estadoJuego.tableroJugador1[i][j]
                    tableroJugador2[i][j] = estadoJuego.tableroJugador2[i][j]
                    tableroAtaquesJugador1[i][j] = estadoJuego.tableroAtaquesJugador1[i][j]
                    tableroAtaquesJugador2[i][j] = estadoJuego.tableroAtaquesJugador2[i][j]
                }
            }

            // Restaurar barcos
            barcosJugador1.clear()
            barcosJugador1.addAll(estadoJuego.barcosJugador1)
            barcosJugador2.clear()
            barcosJugador2.addAll(estadoJuego.barcosJugador2)

            // Actualizar UI
            actualizarUI()
            Toast.makeText(this, R.string.partida_cargada, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, R.string.no_partida_guardada, Toast.LENGTH_SHORT).show()
        }
    }

    private fun mostrarDialogoNuevaPartida() {
        AlertDialog.Builder(this)
            .setTitle(R.string.nueva_partida)
            .setMessage(R.string.confirmar_nueva_partida)
            .setPositiveButton(R.string.si) { _, _ ->
                reiniciarJuego()
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    private fun mostrarDialogoVictoria() {
        AlertDialog.Builder(this)
            .setTitle(R.string.victoria)
            .setMessage(getString(R.string.mensaje_victoria, jugadorActual))
            .setPositiveButton(R.string.nueva_partida) { _, _ ->
                reiniciarJuego()
            }
            .setNegativeButton(R.string.salir) { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun reiniciarJuego() {
        // Reiniciar variables de juego
        faseActual = FaseJuego.CONFIGURACION
        jugadorActual = 1
        barcoActualIndex = 0
        orientacionHorizontal = true

        // Limpiar tableros
        for (i in 0 until TABLERO_SIZE) {
            for (j in 0 until TABLERO_SIZE) {
                tableroJugador1[i][j] = EstadoCelda.VACIA
                tableroJugador2[i][j] = EstadoCelda.VACIA
                tableroAtaquesJugador1[i][j] = false
                tableroAtaquesJugador2[i][j] = false
            }
        }

        // Limpiar barcos
        barcosJugador1.clear()
        barcosJugador2.clear()

        // Actualizar UI
        actualizarUI()
    }

    // Clases de datos y enumeraciones
    enum class FaseJuego {
        CONFIGURACION, ATAQUE
    }

    enum class EstadoCelda {
        VACIA, BARCO, AGUA, IMPACTO
    }

    data class Barco(val longitud: Int, val nombre: String)

    data class BarcoColocado(val longitud: Int, val posiciones: List<Pair<Int, Int>>)

    data class EstadoJuego(
        val faseActual: FaseJuego,
        val jugadorActual: Int,
        val barcoActualIndex: Int,
        val orientacionHorizontal: Boolean,
        val tableroJugador1: Array<Array<EstadoCelda>>,
        val tableroJugador2: Array<Array<EstadoCelda>>,
        val tableroAtaquesJugador1: Array<Array<Boolean>>,
        val tableroAtaquesJugador2: Array<Array<Boolean>>,
        val barcosJugador1: List<BarcoColocado>,
        val barcosJugador2: List<BarcoColocado>
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as EstadoJuego

            if (faseActual != other.faseActual) return false
            if (jugadorActual != other.jugadorActual) return false
            if (barcoActualIndex != other.barcoActualIndex) return false
            if (orientacionHorizontal != other.orientacionHorizontal) return false
            if (!tableroJugador1.contentDeepEquals(other.tableroJugador1)) return false
            if (!tableroJugador2.contentDeepEquals(other.tableroJugador2)) return false
            if (!tableroAtaquesJugador1.contentDeepEquals(other.tableroAtaquesJugador1)) return false
            if (!tableroAtaquesJugador2.contentDeepEquals(other.tableroAtaquesJugador2)) return false
            if (barcosJugador1 != other.barcosJugador1) return false
            if (barcosJugador2 != other.barcosJugador2) return false

            return true
        }

        override fun hashCode(): Int {
            var result = faseActual.hashCode()
            result = 31 * result + jugadorActual
            result = 31 * result + barcoActualIndex
            result = 31 * result + orientacionHorizontal.hashCode()
            result = 31 * result + tableroJugador1.contentDeepHashCode()
            result = 31 * result + tableroJugador2.contentDeepHashCode()
            result = 31 * result + tableroAtaquesJugador1.contentDeepHashCode()
            result = 31 * result + tableroAtaquesJugador2.contentDeepHashCode()
            result = 31 * result + barcosJugador1.hashCode()
            result = 31 * result + barcosJugador2.hashCode()
            return result
        }
    }
}