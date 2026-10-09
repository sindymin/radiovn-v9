package vn.radio.app

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.os.IBinder
import android.os.PowerManager
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Ghi âm bằng cách tải thẳng luồng stream về file: không phát ra loa, không cần mở app. */
class RecorderService : Service() {
    companion object {
        @Volatile var active: String? = null
        fun start(c: Context, name: String, url: String, durMin: Int) {
            c.startService(
                Intent(c, RecorderService::class.java)
                    .putExtra("name", name).putExtra("url", url).putExtra("dur", durMin)
            )
        }
        fun stop(c: Context) {
            c.startService(Intent(c, RecorderService::class.java).setAction("STOP"))
        }
    }

    @Volatile private var stopFlag = false
    private var th: Thread? = null

    override fun onBind(i: Intent?): IBinder? = null

    private fun notif(text: String): Notification {
        val stop = PendingIntent.getService(this, 2, Intent(this, RecorderService::class.java).setAction("STOP"), 0)
        val open = PendingIntent.getActivity(this, 3, Intent(this, MainActivity::class.java), 0)
        return Notification.Builder(this)
            .setContentTitle("Đang ghi âm").setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(open).setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Dừng ghi", stop).build()
    }

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        if (i == null) return START_NOT_STICKY
        if (i.action == "STOP") {
            stopFlag = true
            if (th?.isAlive != true) stopSelf()
            return START_NOT_STICKY
        }
        if (th?.isAlive == true) return START_NOT_STICKY
        val url = i.getStringExtra("url") ?: return START_NOT_STICKY
        val name = i.getStringExtra("name") ?: "Radio"
        val dur = i.getIntExtra("dur", 0)
        active = name
        stopFlag = false
        startForeground(2, notif(name))
        val wl = (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "radiovn:rec")
        wl.acquire()
        th = Thread {
            try { record(url, name, dur) } catch (e: Exception) {
            } finally {
                active = null
                if (wl.isHeld) wl.release()
                stopForeground(true)
                stopSelf()
            }
        }.also { it.start() }
        return START_NOT_STICKY
    }

    private fun newFile(name: String, ct: String?): File {
        val ext = when {
            ct?.contains("aac") == true -> "aac"
            ct?.contains("ogg") == true -> "ogg"
            else -> "mp3"
        }
        val safe = name.replace(Regex("[^\\p{L}\\p{N}]+"), "_")
        val ts = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        return File(recDirForWrite(this), "${safe}_$ts.$ext")
    }

    private fun record(url: String, name: String, dur: Int) {
        val end = if (dur > 0) System.currentTimeMillis() + dur * 60000L else Long.MAX_VALUE
        var out: FileOutputStream? = null
        var file: File? = null
        var tries = 0
        val buf = ByteArray(16384)
        while (!stopFlag && System.currentTimeMillis() < end && tries < 30) {
            var got = false
            try {
                val c = openStream(url)
                if (c.responseCode == 200) {
                    val o = out ?: run {
                        val fl = newFile(name, c.contentType)
                        file = fl
                        FileOutputStream(fl).also { out = it }
                    }
                    c.inputStream.use { ins ->
                        while (!stopFlag && System.currentTimeMillis() < end) {
                            val n = ins.read(buf)
                            if (n < 0) break
                            o.write(buf, 0, n)
                            got = true
                        }
                    }
                }
                c.disconnect()
            } catch (e: Exception) {
            }
            if (got) tries = 0 else tries++
            if (!stopFlag && System.currentTimeMillis() < end) {
                try { Thread.sleep(3000) } catch (e: InterruptedException) {}
            }
        }
        try { out?.close() } catch (e: Exception) {}
        file?.let { MediaScannerConnection.scanFile(this, arrayOf(it.absolutePath), null, null) }
    }
}
