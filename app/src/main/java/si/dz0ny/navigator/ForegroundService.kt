package si.dz0ny.navigator

import android.annotation.TargetApi
import android.app.*
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.preference.PreferenceManager
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import no.nordicsemi.android.ble.BleManager
import timber.log.Timber
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*


/**
 *
 */
class ForegroundService : Service() {

    companion object {
        val NOTIFICATION_DISPLAY_TIMEOUT = 2 * 60 * 1000 //2 minutes
        val SERVICE_ID = 9001
        val NOTIFICATION_CHANNEL = BuildConfig.APPLICATION_ID
        val VESPA_DEVICE_ADDRESS =
            "00:00:00:00:00:00"//""24:0A:C4:13:58:EA" // <--- YOUR ESP32 MAC address here
        val formatter = SimpleDateFormat.getTimeInstance(DateFormat.SHORT)


    }

    private var startId = 0;
    lateinit var bleManager: BleManager<LEManagerCallbacks>
    var lastPost: Long = 0L
    var lastPostPicID: String = ""
    /**
     * Called by the system when the service is first created.  Do not call this method directly.
     */

    override fun onCreate() {
        super.onCreate()
        initNotificationChannel()
        Timber.w("onCreate")
        val remoteMacAddress = PreferenceManager.getDefaultSharedPreferences(this)
            .getString(SettingsActivity.PREF_KEY_REMOTE_MAC_ADDRESS, VESPA_DEVICE_ADDRESS)

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val leDevice = bluetoothManager.adapter.getRemoteDevice(remoteMacAddress?.toUpperCase())
        bleManager = LEManager(this)
        bleManager.setGattCallbacks(bleManagerCallback)
        if (bluetoothManager.adapter.state == BluetoothAdapter.STATE_ON) {
            bleManager.connect(leDevice).enqueue()
        }

        val intentFilter = IntentFilter(NotificationListener.EXTRA_ACTION)
        LocalBroadcastManager.getInstance(this).registerReceiver(localReceiver, intentFilter)

        registerReceiver(tickReceiver, IntentFilter(Intent.ACTION_TIME_TICK))
        registerReceiver(bluetoothReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
    }


    @TargetApi(Build.VERSION_CODES.O)
    private fun initNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationMgr =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notificationChannel = NotificationChannel(
                NOTIFICATION_CHANNEL,
                BuildConfig.APPLICATION_ID,
                NotificationManager.IMPORTANCE_LOW
            )
            notificationChannel.description = "Navigator"
            notificationChannel.enableLights(false)
            notificationChannel.enableVibration(false)
            notificationMgr.createNotificationChannel(notificationChannel)
        }
    }

    override fun onDestroy() {
        Timber.w("onDestroy")
        startId = 0
        bleManager.close()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(localReceiver)
        unregisterReceiver(tickReceiver)
        unregisterReceiver(bluetoothReceiver)
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancelAll()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationMgr =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationMgr.deleteNotificationChannel(NOTIFICATION_CHANNEL)
        }
        super.onDestroy()
    }

    /**
     * Create/Update the notification
     */
    private fun notify(contentText: String): Notification {
        // Launch the MainAcivity when user taps on the Notification
        val pendingIntent = PendingIntent.getActivity(
            this, 0
            , Intent(this, MainActivity::class.java)
            , PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL)
            .setSmallIcon(R.drawable.device)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(contentText)
            .setContentIntent(pendingIntent)
            .setSound(Uri.EMPTY)
            .setOnlyAlertOnce(true)
            .build()
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(
            SERVICE_ID,
            notification
        )
        return notification
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        Timber.w("onStartCommand {intent=${intent != null},flags=$flags,startId=$startId}")
        if (intent == null || this.startId != 0) {
            //service restarted
            Timber.w("onStartCommand - already running")
        } else {
            //started by intent or pending intent
            this.startId = startId
            val notification = notify("Scanning...")
            startForeground(SERVICE_ID, notification)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    val bleManagerCallback: LEManagerCallbacks = object : LEManagerCallbacks() {
        /**
         * Called when the device has been connected. This does not mean that the application may start communication.
         * A service discovery will be handled automatically after this call. Service discovery
         * may ends up with calling [.onServicesDiscovered] or
         * [.onDeviceNotSupported] if required services have not been found.
         * @param device the device that got connected
         */
        override fun onDeviceConnected(device: BluetoothDevice) {
            super.onDeviceConnected(device)
            Timber.d("onDeviceConnected ${device.name}")
            notify("Connected to ${device.name}")
        }

        /**
         * Called when the Android device started connecting to given device.
         * The [.onDeviceConnected] will be called when the device is connected,
         * or [.onError] in case of error.
         * @param device the device that got connected
         */
        override fun onDeviceConnecting(device: BluetoothDevice) {
            super.onDeviceConnecting(device)
            Timber.d("Connecting to ${if (device.name.isNullOrEmpty()) "device" else device.name}")
            notify("Connecting to ${if (device.name.isNullOrEmpty()) "device" else device.name}")
        }

        /**
         * Called when user initialized disconnection.
         * @param device the device that gets disconnecting
         */
        override fun onDeviceDisconnecting(device: BluetoothDevice) {
            super.onDeviceDisconnecting(device)
            Timber.d("Disconnecting from ${device.name}")
            notify("Disconnecting from ${device.name}")
        }

        /**
         * Called when the device has disconnected (when the callback returned
         * [BluetoothGattCallback.onConnectionStateChange] with state DISCONNECTED),
         * but ONLY if the [BleManager.shouldAutoConnect] method returned false for this device when it was connecting.
         * Otherwise the [.onLinklossOccur] method will be called instead.
         * @param device the device that got disconnected
         */
        override fun onDeviceDisconnected(device: BluetoothDevice) {
            super.onDeviceDisconnected(device)
            Timber.d("Disconnected from ${device.name}")
            notify("Disconnected from ${device.name}")
        }

        /**
         * This callback is invoked when the Ble Manager lost connection to a device that has been connected
         * with autoConnect option (see [BleManager.shouldAutoConnect].
         * Otherwise a [.onDeviceDisconnected] method will be called on such event.
         * @param device the device that got disconnected due to a link loss
         */
        override fun onLinkLossOccurred(device: BluetoothDevice) {
            super.onLinkLossOccurred(device)
            Timber.d("Lost link to ${device.name}")
            notify("Lost link to ${device.name}")
        }


        override fun onError(device: BluetoothDevice, message: String, errorCode: Int) {
            super.onError(device, message, errorCode)
            Timber.w("Error ${device.name}")
            stopSelf(startId)
        }


    }

    var tickReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            if (System.currentTimeMillis() - lastPost > NOTIFICATION_DISPLAY_TIMEOUT) {
                (bleManager as LEManager).writeTimeAndBatt(formatter.format(Date()))
            }
        }
    }

    var localReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (bleManager.isConnected && intent != null) {
                Timber.d("onReceive")
                val notificationId =
                    intent.getIntExtra(NotificationListener.EXTRA_NOTIFICATION_ID_INT, 0)
                val notificationAppName = intent.getStringExtra(NotificationListener.EXTRA_APP_NAME)
                val notificationTitle = intent.getStringExtra(NotificationListener.EXTRA_TITLE)
                val notificationBody = intent.getStringExtra(NotificationListener.EXTRA_BODY)
                val notificationIcon = intent.getStringExtra(NotificationListener.EXTRA_ICON)
                val notificationIconData =
                    intent.getByteArrayExtra(NotificationListener.EXTRA_ICON_DATA)
                val notificationTimestamp =
                    intent.getLongExtra(NotificationListener.EXTRA_TIMESTAMP_LONG, 0)
                val notificationDismissed =
                    intent.getBooleanExtra(NotificationListener.EXTRA_NOTIFICATION_DISMISSED, true)

                if (notificationAppName == "com.google.android.apps.maps") {
                    if (notificationDismissed) {
                        val success =
                            (bleManager as LEManager).writeTimeAndBatt(formatter.format(Date()))
                        lastPost = notificationTimestamp
                        lastPostPicID = ""
                        Timber.d("writeTime {success=$success}")
                    } else {
                        var sep = " - "
                        if (notificationTitle.contains("–")){
                            sep = " – "
                        }
                        val loc = notificationTitle.split(sep)[0]
                            .replace("[^\\x00-\\x7F]", "")
                            .replace(" m", "m")
                            .replace(" km", "km").trim()
                        val etaBody = notificationBody.split(" · ")
                        val time = etaBody[0].replace("[^\\x00-\\x7F]", "")
                        val dist = etaBody[1].replace("[^\\x00-\\x7F]", "")
                        val eta = etaBody[2].replace("[^\\x00-\\x7F]", "").replace("Prihod: ", "")
                        val new = "${time}${dist}${eta}${loc}${notificationIcon}"
                        if (lastPostPicID != new) {

                            val suc1 = (bleManager as LEManager).writeCmdAndMsg(1, time)
                            val suc2 = (bleManager as LEManager).writeCmdAndMsg(2, dist)
                            val suc3 = (bleManager as LEManager).writeCmdAndMsg(3, eta)
                            val suc4 = (bleManager as LEManager).writeCmdAndMsg(4, loc)
                            val suc5 =
                                (bleManager as LEManager).writeCmdAndMsg(5, notificationIcon)
                            if (suc1 && suc2 && suc3 && suc4 && suc5) {
                                lastPost = notificationTimestamp
                                Timber.d("writeLoc {time=$time,dist=$dist,loc=$loc,eta=$eta}")
                                lastPostPicID = new
                            }

                        }

                    }
                }
            }
        }
    }

    val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            if (intent?.action?.equals(BluetoothAdapter.ACTION_STATE_CHANGED) == true) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                when (state) {
                    BluetoothAdapter.STATE_ON -> {
                        // TODO: 2018/01/03 connect to remote
                        val remoteMacAddress =
                            PreferenceManager.getDefaultSharedPreferences(context)
                                .getString(
                                    SettingsActivity.PREF_KEY_REMOTE_MAC_ADDRESS,
                                    VESPA_DEVICE_ADDRESS
                                )
                        val bluetoothManager =
                            getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                        val leDevice = bluetoothManager.adapter.getRemoteDevice(remoteMacAddress)

                        bleManager = LEManager(context)
                        bleManager.setGattCallbacks(bleManagerCallback)
                        bleManager.connect(leDevice)
                    }
                    BluetoothAdapter.STATE_TURNING_OFF -> {
                        // TODO: 2018/01/03 close connections
                        bleManager.disconnect()
                        bleManager.close()
                    }
                }
            }
        }
    }

}