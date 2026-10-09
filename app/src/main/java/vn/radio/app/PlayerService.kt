package vn.radio.app

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.IBinder
import android.os.PowerManager

class PlayerService : Service() {
    companion object {
        @Volatile var name: String? = null
        @Volatile var playing = false
        fun play(c: Context, s: Station) {
            c.startService(Intent(c, PlayerService::class.java).putExtra("url", s.url).putExtra("name", s.name))
        }
        fun stop(c: Context) {
            c.startService(Intent(c, PlayerService::class.java).setAction("STOP"))
        }
    }

    private var mp: MediaPlayer? = null

    override fun onBind(i: Intent?): IBinder? = null

    private fun notif(text: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), 0)
        val stop = PendingIntent.getService(this, 1, Intent(this, PlayerService::class.java).setAction("STOP"), 0)
        return Notification.Builder(this)
            .setContentTitle("Radio Việt Nam").setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(open).setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Dừng", stop).build()
    }

    private fun release() {
        try { mp?.release() } catch (e: Exception) {}
        mp = null; playing = false; name = null
    }

    private fun quit() {
        release(); stopForeground(true); stopSelf()
    }

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        if (i == null) return START_NOT_STICKY
        if (i.action == "STOP") { quit(); return START_NOT_STICKY }
        val url = i.getStringExtra("url") ?: return START_NOT_STICKY
        val nm = i.getStringExtra("name") ?: "Radio"
        release()
        name = nm
        startForeground(1, notif("Đang kết nối: $nm"))
        val p = MediaPlayer()
        mp = p
        p.setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
        p.setAudioStreamType(AudioManager.STREAM_MUSIC)
        p.setOnPreparedListener {
            it.start(); playing = true
            (getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager).notify(1, notif("Đang phát: $nm"))
        }
        p.setOnErrorListener { _, _, _ -> toast("Không phát được: $nm"); quit(); true }
        try {
            p.setDataSource(url); p.prepareAsync()
        } catch (e: Exception) {
            toast("Lỗi: ${e.message}"); quit()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() { release(); super.onDestroy() }
}
