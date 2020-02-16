package si.dz0ny.navigator

import android.content.Context
import android.content.SharedPreferences
import androidx.multidex.MultiDexApplication
import com.rollbar.android.Rollbar
import timber.log.Timber

class MainApplication : MultiDexApplication() {

    companion object {
        val PREFS_KEY_ALLOWED_PACKAGES = "PREFS_KEY_ALLOWED_PACKAGES"
        lateinit var sharedPrefs: SharedPreferences
    }

    override fun onCreate() {
        super.onCreate()

        sharedPrefs = getSharedPreferences(BuildConfig.APPLICATION_ID, Context.MODE_PRIVATE)

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        Rollbar.init(this);
        Rollbar.instance().error(Exception("This is a test error")); //remove this after initial testing
    }


}