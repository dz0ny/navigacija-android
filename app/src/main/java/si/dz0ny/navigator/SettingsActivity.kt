package si.dz0ny.navigator

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import java.util.regex.Pattern

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings, SettingsFragment())
            .commit()
    }

    companion object {
        const val PREF_KEY_RUN_AS_A_SERVICE = "pref_as_bg_service"
        const val PREF_KEY_REMOTE_MAC_ADDRESS = "pref_remote_mac_address"
        const val PREF_KEY_START_AT_BOOT = "pref_start_at_boot"
        val MAC_PATTERN: Pattern = Pattern.compile("^([A-F0-9]{2}[:]?){5}[A-F0-9]{2}$")

        class SettingsFragment : PreferenceFragmentCompat() {
            override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
                setPreferencesFromResource(R.xml.preferences, rootKey)
                val sharedPref = PreferenceManager.getDefaultSharedPreferences(activity)
                sharedPref.getString(PREF_KEY_REMOTE_MAC_ADDRESS, "00:00:00:00:00:00")?.let {
                    setRemoteMACAddressPrefSummary(
                        it
                    )
                }

                // validate updates and apply is valid
                findPreference<EditTextPreference>(PREF_KEY_REMOTE_MAC_ADDRESS)?.setOnPreferenceChangeListener { preference: Preference?, value: Any? ->
                    val mac = (value as String).toUpperCase().trim()
                    if (MAC_PATTERN.matcher(mac).find()) {
                        setRemoteMACAddressPrefSummary(mac)
                        return@setOnPreferenceChangeListener true
                    } else {
                        Toast.makeText(activity, R.string.mac_format_error, Toast.LENGTH_LONG)
                            .show()
                        return@setOnPreferenceChangeListener false
                    }
                }
            }

            private fun setRemoteMACAddressPrefSummary(mac: String) {
                val pref = findPreference<EditTextPreference>(PREF_KEY_REMOTE_MAC_ADDRESS)
                pref?.summary = mac
            }

        }
    }

    override fun onStart() {
        super.onStart()
        stopService(Intent(this, ForegroundService::class.java))
    }
}