package com.example.pomodoroapp

import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.media.MediaPlayer
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.google.android.material.chip.ChipGroup

class MainActivity : AppCompatActivity() {

    // tag
    companion object {
        private const val TAG = "PomodoroActivity"
    }

    private enum class TimerState {
        WORK, SHORT_BREAK, LONG_BREAK
    }

    private lateinit var timerTextView: TextView
    private lateinit var playPauseButton: Button
    private lateinit var resetButton: ImageButton
    private lateinit var circularProgressBar: ProgressBar
    private lateinit var timeChipGroup: ChipGroup
    private lateinit var themeIcon: ImageView
    private lateinit var addTaskButton: com.google.android.material.button.MaterialButton
    private lateinit var tasksContainer: LinearLayout
    private val TASKS_PREF = "tasks_pref"
    private val TASKS_KEY = "tasks_key"
    private lateinit var sharedPreferences: SharedPreferences
    private val tasksList = mutableListOf<String>()

    private var countDownTimer: CountDownTimer? = null
    private var isTimerRunning = false
    private var currentState = TimerState.WORK
    private var workSessionsCompleted = 0

    private var workTimeInMillis = 25 * 1000L //* 60
    private var shortBreakTimeInMillis = 5 * 1000L //* 60
    private var longBreakTimeInMillis = 15 * 1000L //* 60
    private var timeLeftInMillis = workTimeInMillis

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLogger.i(TAG, "onCreate: Atividade Pomodoro está sendo criada.")
        applySavedTheme()
        setContentView(R.layout.activity_pomodoro_main)

        timerTextView = findViewById(R.id.timerTextView)
        playPauseButton = findViewById(R.id.playButton)
        resetButton = findViewById(R.id.resetButton)
        circularProgressBar = findViewById(R.id.circularProgressBar)
        timeChipGroup = findViewById(R.id.timeChipGroup)
        themeIcon = findViewById(R.id.ThemeIcon)
        addTaskButton = findViewById(R.id.addTaskButton)
        tasksContainer = findViewById(R.id.tasksContainer)
        sharedPreferences = getSharedPreferences(TASKS_PREF, Context.MODE_PRIVATE)
        loadTasks()

        setupChipListener()
        setupButtonListeners()
        setupThemeToggle()

        updateUIToCurrentState()
        addTaskButton.setOnClickListener {
            AppLogger.d(TAG, "Botão 'Adicionar Tarefa' clicado.")
            showAddTaskDialog()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        AppLogger.w(TAG, "onDestroy: Atividade Pomodoro destruída. Timer cancelado.")
        countDownTimer?.cancel()
        countDownTimer = null
    }

    private fun applySavedTheme() {
        val sharedPreferences = getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)
        val isNightMode = sharedPreferences.getBoolean("isNightMode", false)
        AppLogger.d(TAG, "applySavedTheme: Aplicando tema salvo. Modo Noturno = $isNightMode")
        if (isNightMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }

    private fun setupThemeToggle() {
        themeIcon.setOnClickListener {
            AppLogger.d(TAG, "Ícone de tema clicado.")
            val currentNightMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
            val isNightMode: Boolean

            if (currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
                AppLogger.i(TAG, "Mudando para o Modo Claro.")
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                isNightMode = false
            } else {
                AppLogger.i(TAG, "Mudando para o Modo Escuro.")
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                isNightMode = true
            }

            val sharedPreferences = getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)
            with(sharedPreferences.edit()) {
                putBoolean("isNightMode", isNightMode)
                apply()
            }
        }
    }

    private fun setupChipListener() {
        timeChipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener

            val checkedId = checkedIds.first()
            val chipName = resources.getResourceEntryName(checkedId)
            AppLogger.d(TAG, "Chip de tempo selecionado: $chipName")

            when (checkedId) {
                R.id.chip_25_5 -> {
                    workTimeInMillis = 25 * 1000L
                    shortBreakTimeInMillis = 5 * 1000L
                }
                R.id.chip_50_10 -> {
                    workTimeInMillis = 50 * 1000L
                    shortBreakTimeInMillis = 10 * 1000L
                    longBreakTimeInMillis = 30 * 1000L
                }
                R.id.chip_personalizado -> {
                    showCustomTimeDialog()
                }
            }
            resetTimer()
        }
    }

    private fun setupButtonListeners() {
        playPauseButton.setOnClickListener {
            if (isTimerRunning) {
                AppLogger.d(TAG, "Botão PAUSE clicado.")
                pauseTimer()
            } else {
                AppLogger.d(TAG, "Botão PLAY clicado.")
                startTimer()
            }
        }

        resetButton.setOnClickListener {
            AppLogger.w(TAG, "Botão RESET clicado. Timer reiniciado.")
            resetTimer()
        }
    }

    private fun startNextCycle() {
        val previousState = currentState
        when (currentState) {
            TimerState.WORK -> {
                workSessionsCompleted++
                currentState = if (workSessionsCompleted % 4 == 0) TimerState.LONG_BREAK else TimerState.SHORT_BREAK
            }
            TimerState.SHORT_BREAK, TimerState.LONG_BREAK -> {
                currentState = TimerState.WORK
            }
        }
        AppLogger.i(TAG, "Ciclo concluído. Transição de $previousState para $currentState.")
        resetTimer(startAutomatically = true)
    }

    private fun startTimer() {
        AppLogger.i(TAG, "startTimer: Iniciando timer para o estado $currentState com ${timeLeftInMillis / 1000}s.")
        countDownTimer = object : CountDownTimer(timeLeftInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeftInMillis = millisUntilFinished
                AppLogger.v(TAG, "onTick: ${timeLeftInMillis / 1000}s restantes no estado $currentState")
                updateTimerText()
                updateProgressBar()
            }

            override fun onFinish() {
                AppLogger.i(TAG, "onFinish: Timer finalizado.")
                playSound()
                AppLogger.i(TAG, "playSound: Tocando som de notificação.")
                startNextCycle()
            }
        }.start()

        isTimerRunning = true
        playPauseButton.text = getString(R.string.symbol_pause)
    }

    private fun pauseTimer() {
        AppLogger.d(TAG, "pauseTimer: Timer pausado com ${timeLeftInMillis / 1000}s restantes.")
        countDownTimer?.cancel()
        isTimerRunning = false
        playPauseButton.text = getString(R.string.symbol_play)
    }

    private fun resetTimer(startAutomatically: Boolean = false) {
        AppLogger.d(TAG, "resetTimer: Redefinindo timer para o estado $currentState. Início automático: $startAutomatically")
        pauseTimer()
        timeLeftInMillis = when (currentState) {
            TimerState.WORK -> workTimeInMillis
            TimerState.SHORT_BREAK -> shortBreakTimeInMillis
            TimerState.LONG_BREAK -> longBreakTimeInMillis
        }
        updateUIToCurrentState()
        if (startAutomatically) {
            startTimer()
        }
    }

    private fun updateUIToCurrentState() {
        // Log detalhado (verbose) pois é chamado com frequência
        AppLogger.v(TAG, "updateUIToCurrentState: Atualizando UI para o estado $currentState.")
        updateTimerText()
        updateProgressBar()
        // ... (resto do seu código de UI)
        val isNightMode = (resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES

        val colorRes = when (currentState) {
            TimerState.WORK -> if (isNightMode) R.color.pomodoro_running_night else R.color.pomodoro_running_light
            TimerState.SHORT_BREAK -> if (isNightMode) R.color.pomodoro_short_break_night else R.color.pomodoro_short_break_light
            TimerState.LONG_BREAK -> if (isNightMode) R.color.pomodoro_long_break_night else R.color.pomodoro_long_break_light
        }
        val color = ContextCompat.getColor(this, colorRes)
        circularProgressBar.progressTintList = ColorStateList.valueOf(color)
        playPauseButton.backgroundTintList = ColorStateList.valueOf(color)
        if (isTimerRunning) {
            playPauseButton.text = getString(R.string.symbol_pause)
        } else {
            playPauseButton.text = getString(R.string.symbol_play)
        }
    }

    private fun updateTimerText() {
        val minutes = (timeLeftInMillis / 1000) / 60
        val seconds = (timeLeftInMillis / 1000) % 60
        val timeFormatted = String.format("%02d:%02d", minutes, seconds)
        timerTextView.text = timeFormatted
    }

    private fun updateProgressBar() {
        val totalTime = when (currentState) {
            TimerState.WORK -> workTimeInMillis
            TimerState.SHORT_BREAK -> shortBreakTimeInMillis
            TimerState.LONG_BREAK -> longBreakTimeInMillis
        }
        val progress = (timeLeftInMillis.toDouble() / totalTime * 100).toInt()
        circularProgressBar.progress = progress
    }

    private fun playSound() {
        try {
            val mediaPlayer = MediaPlayer.create(applicationContext, R.raw.notificacao)
            mediaPlayer.setOnCompletionListener { mp -> mp.release() }
            mediaPlayer.start()
        } catch (e: Exception) {
            // Log de Erro (Error): Algo deu errado ao tentar tocar o som.
            AppLogger.e(TAG, "Falha ao tocar som de notificação.", e)
            e.printStackTrace()
        }
    }

    private fun saveTasks() {
        val set = tasksList.toSet()
        sharedPreferences.edit().putStringSet(TASKS_KEY, set).apply()
        AppLogger.d(TAG, "saveTasks: ${tasksList.size} tarefas salvas.")
    }

    private fun addTask(title: String) {
        AppLogger.d(TAG, "addTask: Nova tarefa adicionada: '$title'")
        tasksList.add(title)
        saveTasks()
        createTaskView(title)
    }

    private fun showAddTaskDialog() {
        AppLogger.d(TAG, "showAddTaskDialog: Exibindo diálogo para adicionar tarefa.")
        // ... (código do diálogo)
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_task, null)
        val inputTaskName: EditText = dialogView.findViewById(R.id.inputTaskName)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Adicionar") { _, _ ->
                val taskTitle = inputTaskName.text.toString().trim()
                if (taskTitle.isEmpty()) {
                    AppLogger.w(TAG, "Tentativa de adicionar tarefa com título vazio.")
                    Toast.makeText(this, "O título não pode estar vazio", Toast.LENGTH_SHORT).show()
                } else {
                    addTask(taskTitle)
                }
            }
            .setNegativeButton("Cancelar", null)
            .create()
        dialog.show()
    }

    private fun loadTasks() {
       try {
           val savedTasks = sharedPreferences.getStringSet(TASKS_KEY, emptySet()) ?: emptySet()
           tasksList.clear()
           tasksList.addAll(savedTasks)
           tasksContainer.removeAllViews()
           for (task in tasksList) {
               createTaskView(task)
           }
           AppLogger.i(TAG, "loadTasks: ${tasksList.size} tarefas carregadas da memória.")
       } catch (e: Exception) {
           AppLogger.e(TAG, "Falha crítica ao carregar tarefas do SharedPreferences!", e)
           Toast.makeText(this, "Erro ao carregar tarefas salvas.", Toast.LENGTH_LONG).show()
       }
    }

    private fun createTaskView(title: String) {
        val checkBox = CheckBox(this)
        checkBox.text = title
        checkBox.textSize = 16f
        checkBox.setPadding(0, 8, 0, 8)
        tasksContainer.addView(checkBox)
    }

    private fun showCustomTimeDialog() {
        AppLogger.d(TAG, "showCustomTimeDialog: Exibindo diálogo de tempo personalizado.")
        // ... (código do diálogo)
        val dialogView = layoutInflater.inflate(R.layout.dialog_custom_time, null)
        val workTimeInput: EditText = dialogView.findViewById(R.id.workTimeInput)
        val shortBreakInput: EditText = dialogView.findViewById(R.id.shortBreakInput)
        val longBreakInput: EditText = dialogView.findViewById(R.id.longBreakInput)
        workTimeInput.setText((workTimeInMillis / 1000).toString())
        shortBreakInput.setText((shortBreakTimeInMillis / 1000).toString())
        longBreakInput.setText((longBreakTimeInMillis / 1000).toString())
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Tempo Personalizado")
            .setView(dialogView)
            .setPositiveButton("Salvar") { _, _ ->
                val work = workTimeInput.text.toString().toLongOrNull()
                val shortBreak = shortBreakInput.text.toString().toLongOrNull()
                val longBreak = longBreakInput.text.toString().toLongOrNull()
                if (work != null && shortBreak != null && longBreak != null) {
                    AppLogger.i(TAG, "Novos tempos personalizados salvos: Trabalho=$work, Pausa Curta=$shortBreak, Pausa Longa=$longBreak")
                    workTimeInMillis = work * 1000
                    shortBreakTimeInMillis = shortBreak * 1000
                    longBreakTimeInMillis = longBreak * 1000
                    resetTimer()
                } else {
                    AppLogger.w(TAG, "Valores inválidos inseridos no diálogo de tempo personalizado.")
                    Toast.makeText(this, "Digite valores válidos", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .create()
        dialog.show()
    }
}
