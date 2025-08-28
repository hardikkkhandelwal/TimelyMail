package com.example.timelymail

import android.os.Bundle
import android.widget.Switch
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply saved mode before super.onCreate
        loadNightModePref()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val darkModeSwitch = findViewById<Switch>(R.id.darkModeSwitch)

        // Set current state
        val currentMode = resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK
        darkModeSwitch.isChecked = currentMode == android.content.res.Configuration.UI_MODE_NIGHT_YES

        darkModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                saveNightModePref(true)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                saveNightModePref(false)
            }
        }
    }

    private fun saveNightModePref(isDarkMode: Boolean) {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        prefs.edit().putBoolean("dark_mode", isDarkMode).apply()
    }

    private fun loadNightModePref() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean("dark_mode", false)
        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode)
                AppCompatDelegate.MODE_NIGHT_YES
            else
                AppCompatDelegate.MODE_NIGHT_NO
        )
    }
}
