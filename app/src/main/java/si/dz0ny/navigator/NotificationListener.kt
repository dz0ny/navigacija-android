package si.dz0ny.navigator

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Bundle
import android.os.Environment
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.text.SpannableString
import androidx.core.graphics.drawable.toBitmap
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import timber.log.Timber
import java.io.*
import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.MessageDigest


class NotificationListener : NotificationListenerService() {

    companion object {
        val EXTRA_ACTION = "ESP"
        val EXTRA_NOTIFICATION_DISMISSED = "EXTRA_NOTIFICATION_DISMISSED"
        val EXTRA_APP_NAME = "EXTRA_APP_NAME"
        val EXTRA_NOTIFICATION_ID_INT = "EXTRA_NOTIFICATION_ID_INT"
        val EXTRA_TITLE = "EXTRA_TITLE"
        val EXTRA_BODY = "EXTRA_BODY"
        val EXTRA_ICON = "EXTRA_ICON"
        val EXTRA_ICON_DATA = "EXTRA_ICON_DATA"
        val EXTRA_TIMESTAMP_LONG = "EXTRA_TIMESTAMP_LONG"
    }

    private fun writeToFile(name: String, content: ByteArray) {
        try {
            val file =
                File(Environment.getExternalStorageDirectory().toString() + "/navigacija-" + name +  ".jpg")
            if (file.exists()) {
                return;
            }
            val fOutputStream = FileOutputStream(file);
            fOutputStream.write(content)
            fOutputStream.flush();
            fOutputStream.close();
        } catch (e: IOException) {
            Timber.d(e)
        }
    }

    /**
     * Implement this method to learn about new notifications as they are posted by apps.
     *
     * @param sbn A data structure encapsulating the original [android.app.Notification]
     * object as well as its identifying information (tag and id) and source
     * (package name).
     */
    override fun onNotificationPosted(sbn: StatusBarNotification) {

        val notification = sbn.notification
        val ticker = notification?.tickerText
        val bundle: Bundle? = notification?.extras
        val title = when (val titleObj = bundle?.get("android.title")) {
            is String -> titleObj
            is SpannableString -> titleObj.toString()
            else -> "undefined"
        }
        val body: String? = bundle?.getCharSequence("android.subText").toString()
        Timber.d("onNotificationPosted {app=${sbn.packageName},id=${sbn.id},ticker=$ticker,title=$title,body=$body,posted=${sbn.postTime},package=${sbn.packageName}}")

        val allowedPackages: MutableSet<String> = MainApplication.sharedPrefs.getStringSet(
            MainApplication.PREFS_KEY_ALLOWED_PACKAGES,
            mutableSetOf()
        ) as MutableSet<String>

        // broadcast StatusBarNotication (exclude own notifications)
        if (sbn.id != ForegroundService.SERVICE_ID
            && allowedPackages.contains(sbn.packageName)
        ) {
            val intent = Intent(EXTRA_ACTION)
            intent.putExtra(EXTRA_NOTIFICATION_ID_INT, sbn.id)
            intent.putExtra(EXTRA_APP_NAME, sbn.packageName)
            intent.putExtra(EXTRA_TITLE, title)
            intent.putExtra(EXTRA_BODY, body)
            try {
                val icon = sbn.notification.extras.getParcelable<Icon>("android.largeIcon")
                if (icon != null) {
                    val iDraw = icon.loadDrawable(this)
                    val iID = bitMapToString(iDraw);
                    Timber.d("onNotificationPosted {icon=${iID}")
                    intent.putExtra(EXTRA_ICON, iID)

                    val stream = ByteArrayOutputStream();
                    iDraw.toBitmap(92, 92).compress(Bitmap.CompressFormat.JPEG, 90, stream)

                    writeToFile(iID, stream.toByteArray());

                    intent.putExtra(EXTRA_ICON_DATA, stream.toByteArray())
                }
            } catch (e: Exception) {
                try {
                    val icon = sbn.notification.extras.getParcelable<Bitmap>("android.largeIcon")
                    if (icon != null) {
                        val iID = bitmapToHash(icon);
                        Timber.d("onNotificationPosted {icon=${iID}")
                        intent.putExtra(EXTRA_ICON, iID)
                        val stream = ByteArrayOutputStream();
                        icon.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                        writeToFile(iID, stream.toByteArray());
                        intent.putExtra(EXTRA_ICON_DATA, stream.toByteArray())
                    }
                } catch (e: Exception) {

                }
            }

            intent.putExtra(EXTRA_NOTIFICATION_DISMISSED, false)
            intent.putExtra(EXTRA_TIMESTAMP_LONG, sbn.postTime)
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        }
    }


    fun bitMapMonochrome(bitmap: Bitmap): Bitmap {

        val bwBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.RGB_565)

        val canvas = Canvas(bwBitmap)
        val ma = ColorMatrix()
        ma.setSaturation(0f)
        val paint = Paint()
        paint.colorFilter = ColorMatrixColorFilter(ma)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        //var pixels = IntArray(bwBitmap.byteCount)

        //bwBitmap.getPixels(pixels,0,bwBitmap.width,0,0,bwBitmap.width -1,bwBitmap.height -1);
        Timber.d("PICSIze{size=${bwBitmap.byteCount}}")
        return bwBitmap
    }

    fun bitMapToBytes(
        bitmap: Bitmap
    ): ByteArray? {
        val byteBuffer: ByteBuffer = ByteBuffer.allocate(bitmap.byteCount)
        bitmap.copyPixelsToBuffer(byteBuffer)
        return byteBuffer.array()
    }

    fun bitMapToString(drawable: Drawable): String {

        // Single color bitmap will be created of 1x1 pixel
        when (drawable) {
            is BitmapDrawable -> {
                if (drawable.bitmap != null) {
                    return bitmapToHash(drawable.bitmap)
                }
            }
        }
        return "Unknown"
    }

    private fun bitmapToHash(bitmap: Bitmap?): String {
        val m = MessageDigest.getInstance("MD5")
        val baos = ByteArrayOutputStream()
        if (bitmap != null) {
            bitMapMonochrome(bitmap).compress(
                Bitmap.CompressFormat.PNG,
                100,
                baos
            )
        } //bm is the bitmap object

        val bitmapBytes: ByteArray = baos.toByteArray()
        m.update(bitmapBytes)
        return BigInteger(1, m.digest()).toString(16)
    }

    fun bitMapToBytes(drawable: Drawable): ByteArray? {

        // Single color bitmap will be created of 1x1 pixel
        when (drawable) {
            is BitmapDrawable -> {
                if (drawable.bitmap != null) {
                    return bitMapToBytes(bitMapMonochrome(drawable.bitmap))
                }
            }
        }
        return null
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)

        val notification = sbn.notification
        val ticker = notification?.tickerText
        val bundle: Bundle? = notification?.extras
        val titleObj = bundle?.get("android.title")
        val title: String
        title = when (titleObj) {
            is String -> titleObj
            is SpannableString -> titleObj.toString()
            else -> "undefined"
        }
        val body: String? = bundle?.getCharSequence("android.text").toString()

        val appInfo = applicationContext.packageManager.getApplicationInfo(
            sbn.packageName,
            PackageManager.GET_META_DATA
        )
        val appName = applicationContext.packageManager.getApplicationLabel(appInfo)
        Timber.d("onNotificationPosted {app=${appName},id=${sbn.id},ticker=$ticker,title=$title,body=$body,posted=${sbn.postTime},package=${sbn.packageName}}")

        val allowedPackages: MutableSet<String> = MainApplication.sharedPrefs.getStringSet(
            MainApplication.PREFS_KEY_ALLOWED_PACKAGES,
            mutableSetOf()
        ) as MutableSet<String>

        Timber.d(
            "onNotificationRemoved {app=${applicationContext.packageManager.getApplicationLabel(
                appInfo
            )},id=${sbn.id},ticker=$ticker,title=$title,body=$body,posted=${sbn.postTime},package=${sbn.packageName}}"
        )

        // broadcast StatusBarNotication (exclude own notifications)
        if (sbn.id != ForegroundService.SERVICE_ID
            && allowedPackages.contains(sbn.packageName)
        ) {
            val intent = Intent(EXTRA_ACTION)
            intent.putExtra(EXTRA_NOTIFICATION_ID_INT, sbn.id)
            intent.putExtra(EXTRA_APP_NAME, appName)
            intent.putExtra(EXTRA_TITLE, title)
            intent.putExtra(EXTRA_BODY, body)
            intent.putExtra(EXTRA_NOTIFICATION_DISMISSED, true)
            intent.putExtra(EXTRA_TIMESTAMP_LONG, sbn.postTime)
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        }
    }


}

