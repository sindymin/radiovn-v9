package vn.radio.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.util.Calendar

object Scheduler {
    fun next(t: RecTimer): Long {
        val now = System.currentTimeMillis()
        for (d in 0..8) {
            val c = Calendar.getInstance()
            c.add(Calendar.DAY_OF_YEAR, d)
            c.set(Calendar.HOUR_OF_DAY, t.hour)
            c.set(Calendar.MINUTE, t.minute)
            c.set(Calendar.SECOND, 0)
            c.set(Calendar.MILLISECOND, 0)
            if (c.timeInMillis <= now) continue
            val bit = (t.days shr (c.get(Calendar.DAY_OF_WEEK) - 1)) and 1
            if (t.days == 0 || bit == 1) return c.timeInMillis
        }
        return now + 86400000L
    }

    private fun pi(c: Context, id: Long) = PendingIntent.getBroadcast(
        c, 0,
        Intent(c, AlarmReceiver::class.java).setData(Uri.parse("radio://timer/$id")).putExtra("id", id),
        PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun am(c: Context) = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun set(c: Context, t: RecTimer) {
        am(c).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next(t), pi(c, t.id))
    }

    fun cancel(c: Context, t: RecTimer) = am(c).cancel(pi(c, t.id))

    fun all(c: Context) = Store.timers(c).filter { it.enabled }.forEach { set(c, it) }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getLongExtra("id", 0)
        val t = Store.timers(c).find { it.id == id } ?: return
        if (!t.enabled) return
        RecorderService.start(c, t.stationName, t.url, t.durationMin)
        if (!t.silent) PlayerService.play(c, Station(t.stationId, t.stationName, t.url))
        if (t.days != 0) Scheduler.set(c, t)
        else Store.saveTimers(c, Store.timers(c).map { if (it.id == id) it.copy(enabled = false) else it })
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Scheduler.all(c)
    }
}
