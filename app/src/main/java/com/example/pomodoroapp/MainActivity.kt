package com.example.pomodoroapp

import android.content.Context
import android.content.res.ColorStateList
import android.media.MediaPlayer
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.google.android.material.chip.ChipGroup

class MainActivity : AppCompatActivity() {

    private enum class TimerState {
        WORK, SHORT_BREAK, LONG_BREAK
    }
    private lateinit var timerTextView: TextView
    private lateinit var playPauseButton: Button
    private lateinit var resetButton: ImageButton
    private lateinit var circularProgressBar: ProgressBar
    private lateinit var timeChipGroup: ChipGroup
    private lateinit var themeIcon: ImageView


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
        applySavedTheme()
        setContentView(R.layout.activity_pomodoro_main)

        timerTextView = findViewById(R.id.timerTextView)
        playPauseButton = findViewById(R.id.playButton)
        resetButton = findViewById(R.id.resetButton)
        circularProgressBar = findViewById(R.id.circularProgressBar)
        timeChipGroup = findViewById(R.id.timeChipGroup)
        themeIcon = findViewById(R.id.ThemeIcon)

        setupChipListener()
        setupButtonListeners()
        setupThemeToggle()

        updateUIToCurrentState() // Configura a UI inicial
    }

    private fun applySavedTheme() {
        val sharedPreferences = getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)
        val isNightMode = sharedPreferences.getBoolean("isNightMode", false)
        if (isNightMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }

    private fun setupThemeToggle() {
        themeIcon.setOnClickListener {
            val currentNightMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
            val newMode: Int
            val isNightMode: Boolean

            if (currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
                newMode = AppCompatDelegate.MODE_NIGHT_NO
                isNightMode = false
            } else {
                newMode = AppCompatDelegate.MODE_NIGHT_YES
                isNightMode = true
            }

            AppCompatDelegate.setDefaultNightMode(newMode)
            val sharedPreferences = getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)
            with(sharedPreferences.edit()) {
                putBoolean("isNightMode", isNightMode)
                apply()
            }
        }
    }

    private fun setupChipListener() {
        timeChipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            // Se nenhum chip estiver selecionado, não faz nada
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener

            val checkedId = checkedIds.first()
            when (checkedId) {
                R.id.chip_25_5 -> {
                    workTimeInMillis = 25 * 1000L // * 60
                    shortBreakTimeInMillis = 5 * 1000L // * 60
                }
                R.id.chip_50_10 -> {
                    workTimeInMillis = 50 * 1000L // * 60
                    shortBreakTimeInMillis = 10 * 1000L // * 60
                }
                R.id.chip_personalizado -> {
                    // ADD DIALOG P PERSONALIZAR
                    // POR ENQUANTO COLOQUEI ISSO
                    workTimeInMillis = 10 * 1000L // * 60
                    shortBreakTimeInMillis = 2 * 1000L // * 60
                }
            }
            resetTimer()
        }
    }

    private fun setupButtonListeners() {
        playPauseButton.setOnClickListener {
            if (isTimerRunning) {
                pauseTimer()
            } else {
                startTimer()
            }
        }

        resetButton.setOnClickListener {
            resetTimer()
        }
    }

    private fun startNextCycle() {
        when (currentState) {
            TimerState.WORK -> {
                workSessionsCompleted++
                currentState = if (workSessionsCompleted % 4 == 0) {
                    TimerState.LONG_BREAK
                } else {
                    TimerState.SHORT_BREAK
                }
            }
            TimerState.SHORT_BREAK, TimerState.LONG_BREAK -> {
                currentState = TimerState.WORK
            }
        }
        resetTimer(startAutomatically = true)
    }

    private fun startTimer() {
        countDownTimer = object : CountDownTimer(timeLeftInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeftInMillis = millisUntilFinished
                updateTimerText()
                updateProgressBar()
            }

            override fun onFinish() {
                playSound()
                startNextCycle()
            }
        }.start()

        isTimerRunning = true
        playPauseButton.text = getString(R.string.symbol_pause)
    }

    private fun pauseTimer() {
        countDownTimer?.cancel()
        isTimerRunning = false
        playPauseButton.text = getString(R.string.symbol_play)
    }

    private fun resetTimer(startAutomatically: Boolean = false) {
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
        updateTimerText()
        updateProgressBar()

        val isNightMode = (resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES

        val colorRes = when (currentState) {
            TimerState.WORK -> {
                if (isNightMode) R.color.pomodoro_running_night else R.color.pomodoro_running_light
            }
            TimerState.SHORT_BREAK -> {
                if (isNightMode) R.color.pomodoro_short_break_night else R.color.pomodoro_short_break_light
            }
            TimerState.LONG_BREAK -> {
                if (isNightMode) R.color.pomodoro_long_break_night else R.color.pomodoro_long_break_light
            }
        }

        val color = ContextCompat.getColor(this, colorRes)

        circularProgressBar.progressTintList = ColorStateList.valueOf(color)
        playPauseButton.backgroundTintList = ColorStateList.valueOf(color)
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
            e.printStackTrace()
        }
    }
}