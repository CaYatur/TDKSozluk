package com.cagan.tdksozluk

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.cagan.tdksozluk.databinding.ActivitySettingsBinding
import com.cagan.tdksozluk.ui.ThemeManager

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this)
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(R.string.settings)

        val checkedId = when (ThemeManager.current(this)) {
            ThemeManager.MODE_LIGHT -> R.id.radio_light
            ThemeManager.MODE_DARK -> R.id.radio_dark
            else -> R.id.radio_system
        }
        binding.radioGroup.check(checkedId)
        binding.radioGroup.setOnCheckedChangeListener { _, id ->
            val mode = when (id) {
                R.id.radio_light -> ThemeManager.MODE_LIGHT
                R.id.radio_dark -> ThemeManager.MODE_DARK
                else -> ThemeManager.MODE_SYSTEM
            }
            ThemeManager.set(this, mode)
        }

        binding.appVersion.text = getString(
            R.string.app_version,
            packageManager.getPackageInfo(packageName, 0).versionName
        )
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> { finish(); true }
        else -> super.onOptionsItemSelected(item)
    }
}
