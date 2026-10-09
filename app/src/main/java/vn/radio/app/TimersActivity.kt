package vn.radio.app

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TimePicker

class TimersActivity : Activity() {
    private lateinit var lv: ListView
    private var items = listOf<RecTimer>()
    private val dayNames = listOf("CN", "T2", "T3", "T4", "T5", "T6", "T7")

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        title = "Hẹn giờ ghi âm"
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(homeButton())
        root.addView(Button(this).apply { text = "+ Thêm lịch ghi âm"; setOnClickListener { addDialog(null) } })
        lv = ListView(this)
        lv.setOnItemClickListener { _, _, p, _ -> pick(items[p]) }
        root.addView(lv, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        refresh()
        intent.getStringExtra("station")?.let { addDialog(it) }
    }

    private fun refresh() {
        items = Store.timers(this)
        lv.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, items.map {
            val d = if (it.days == 0) "một lần" else dayNames.filterIndexed { i, _ -> (it.days shr i) and 1 == 1 }.joinToString(",")
            "${it.stationName}\n%02d:%02d · ${it.durationMin} phút · $d · ${if (it.silent) "im lặng" else "có tiếng"}${if (it.enabled) "" else " · TẮT"}"
                .format(it.hour, it.minute)
        })
    }

    private fun pick(t: RecTimer) {
        AlertDialog.Builder(this).setTitle(t.stationName)
            .setItems(arrayOf(if (t.enabled) "Tắt" else "Bật", "Xóa")) { _, i ->
                val l = Store.timers(this)
                if (i == 0) {
                    val n = t.copy(enabled = !t.enabled)
                    Store.saveTimers(this, l.map { if (it.id == t.id) n else it })
                    if (n.enabled) Scheduler.set(this, n) else Scheduler.cancel(this, n)
                } else {
                    Scheduler.cancel(this, t)
                    Store.saveTimers(this, l.filter { it.id != t.id })
                }
                refresh()
            }.show()
    }

    private fun addDialog(pre: String?) {
        val sts = Store.stations(this).sortedBy { norm(it.name) }
        if (sts.isEmpty()) { toast("Chưa có danh sách đài"); return }
        val ll = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), 0)
        }
        val sp = Spinner(this)
        sp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, sts.map { it.name })
        pre?.let { p -> val i = sts.indexOfFirst { it.id == p }; if (i >= 0) sp.setSelection(i) }
        val tp = TimePicker(this).apply { setIs24HourView(true) }
        val dur = EditText(this).apply {
            hint = "Thời lượng (phút)"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText("60")
        }
        val row = LinearLayout(this)
        val cbs = dayNames.map { n -> CheckBox(this).apply { text = n } }
        cbs.forEach { row.addView(it, LinearLayout.LayoutParams(0, -2, 1f)) }
        val silent = CheckBox(this).apply { text = "Ghi âm im lặng (không phát tiếng)"; isChecked = true }
        ll.addView(sp); ll.addView(tp); ll.addView(dur)
        ll.addView(android.widget.TextView(this).apply { text = "Lặp lại (không chọn = một lần):" })
        ll.addView(row); ll.addView(silent)
        AlertDialog.Builder(this).setTitle("Lịch ghi âm")
            .setView(ScrollView(this).apply { addView(ll) })
            .setPositiveButton("Lưu") { _, _ ->
                val st = sts[sp.selectedItemPosition]
                var mask = 0
                cbs.forEachIndexed { i, cb -> if (cb.isChecked) mask = mask or (1 shl i) }
                val t = RecTimer(
                    System.currentTimeMillis(), st.id, st.name, st.url, tp.hour, tp.minute,
                    dur.text.toString().toIntOrNull() ?: 60, mask, silent.isChecked
                )
                if (st.isHls()) toast("Lưu ý: đài này dùng HLS nên có thể không ghi được")
                Store.saveTimers(this, Store.timers(this) + t)
                Scheduler.set(this, t)
                refresh()
            }.setNegativeButton("Hủy", null).show()
    }
}
