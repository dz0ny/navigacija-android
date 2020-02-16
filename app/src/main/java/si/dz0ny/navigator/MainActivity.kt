package si.dz0ny.navigator

import android.Manifest
import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.preference.PreferenceManager
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import timber.log.Timber


class MainActivity : AppCompatActivity() {

    lateinit var menu: Menu
    var alertDialog: AlertDialog? = null

    // Storage Permissions
    private val REQUEST_EXTERNAL_STORAGE = 1
    private val PERMISSIONS_STORAGE = arrayOf<String>(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    /**
     * Checks if the app has permission to write to device storage
     *
     * If the app does not has permission then the user will be prompted to grant permissions
     *
     * @param activity
     */
    fun verifyStoragePermissions(activity: Activity?) {
        // Check if we have write permission
        val permission =
            ActivityCompat.checkSelfPermission(
                activity!!,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                activity,
                PERMISSIONS_STORAGE,
                REQUEST_EXTERNAL_STORAGE
            )
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false)
    }

    override fun onStart() {
        super.onStart()
        startService(Intent(this, ForegroundService::class.java))
        Timber.w("onStart")

    }

    override fun onDestroy() {
        super.onDestroy()
        // stop the service
        val isRunAsAService = PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean(SettingsActivity.PREF_KEY_RUN_AS_A_SERVICE, false)
        Timber.w("onDestroy {isService=$isRunAsAService}")
        if (!isRunAsAService) {
            stopService(Intent(this, ForegroundService::class.java))
        }
    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        this.menu = menu
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_item_prefs -> {
                showPreferences()
                true
            }
            R.id.menu_item_kill -> {
                stopService(Intent(this, ForegroundService::class.java))
                item.setVisible(false)
                menu.findItem(R.id.menu_item_start)?.setVisible(true)
                true
            }
            R.id.menu_item_start -> {
                startService(Intent(this, ForegroundService::class.java))
                item.setVisible(false)
                menu.findItem(R.id.menu_item_kill)?.setVisible(true)
                true
            }
            R.id.menu_item_apps -> {
                chooseApps(this)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    }

    private fun chooseApps(mainActivity: MainActivity) {
        val enabled = NotificationManagerCompat.getEnabledListenerPackages(this)
            .contains(BuildConfig.APPLICATION_ID)
        Timber.d("Notification Listener Enabled $enabled")

        if (alertDialog == null || !(alertDialog!!.isShowing)) {
            if (enabled) {

                // lookup installed apps
                val installedApps =
                    packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                        .filter { applicationInfo ->
                            applicationInfo.packageName.startsWith("com.google.android.apps.") || applicationInfo.packageName == "com.google.android.calendar"
                        }

                val names: Array<String> = installedApps.map { applicationInfo ->
                    packageManager.getApplicationLabel(applicationInfo).toString()
                }.toTypedArray()

                val prefsAllowedPackages: MutableSet<String>? =
                    MainApplication.sharedPrefs.getStringSet(
                        MainApplication.PREFS_KEY_ALLOWED_PACKAGES,
                        mutableSetOf()
                    )
                val checkedItems = BooleanArray(installedApps.size)
                for (i in names.indices) {
                    if (prefsAllowedPackages != null) {
                        checkedItems[i] =
                            prefsAllowedPackages.contains(installedApps[i].packageName)
                    }
                }

                val modifiedList: ArrayList<String> = arrayListOf()
                if (prefsAllowedPackages != null) {
                    modifiedList.addAll(prefsAllowedPackages)
                }

                // show Apps
                val builder: AlertDialog.Builder = AlertDialog.Builder(this)
                    .setTitle("Choose app")
                    .setPositiveButton(
                        android.R.string.ok
                    ) { _, _ ->
                        // commit
                        MainApplication.sharedPrefs.edit().putStringSet(
                            MainApplication.PREFS_KEY_ALLOWED_PACKAGES,
                            modifiedList.toSet()
                        ).apply();
                    }
                    .setNegativeButton(
                        android.R.string.cancel
                    ) { _, _ ->
                        // close without commit
                    }
                    .setMultiChoiceItems(
                        names,
                        checkedItems
                    ) { _, position, checked ->
                        if (checked) {
                            modifiedList.add(installedApps[position].packageName)
                        } else {
                            modifiedList.remove(installedApps[position].packageName)
                        }
                    }
                    .setOnDismissListener { alertDialog = null }
                    .setOnCancelListener { alertDialog = null }
                alertDialog = builder.create()
                alertDialog!!.show()
            } else {
                val builder: AlertDialog.Builder = AlertDialog.Builder(this)
                    .setTitle("Choose App")
                    .setMessage("Looks like you must first grant this app access to notifications. Do you want to continue?")
                    .setNegativeButton(android.R.string.no, null)
                    .setPositiveButton(
                        android.R.string.yes
                    ) { _: DialogInterface?, _: Int ->
                        if (!enabled) {
                            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        }
                    }
                    .setOnDismissListener { alertDialog = null }
                    .setOnCancelListener { alertDialog = null }
                alertDialog = builder.create()
                alertDialog!!.show()

            }
        }
    }


    fun showPreferences() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }
}
