package vn.radio.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Spinner

/** Lịch phát sóng do người dùng tự nhập (các đài VN không có API lịch chính thức). */
class GuideActivity : Activity() {
    private lateinit var lv: ListView
    private var items = listOf<Prog>()

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        title = "Lịch phát sóng"
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(homeButton())
        root.addView(Button(this).apply { text = "+ Thêm chương trình"; setOnClickListener { add() } })
        lv = ListView(this)
        lv.setOnItemClickListener { _, _, p, _ -> pick(items[p]) }
        root.addView(lv, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        refresh()
    }

    private fun refresh() {
        items = Store.progs(this).sortedWith(compareBy<Prog>({ norm(it.stationName) }, { it.time }))
        lv.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1,
            items.map { "${it.stationName}\n${it.time} — ${it.title}" })
    }

    private fun pick(p: Prog) {
        AlertDialog.Builder(this).setTitle(p.title)
            .setItems(arrayOf("Hẹn giờ ghi âm đài này", "Xóa")) { _, i ->
                if (i == 0) {
                    val id = Store.stations(this).firstOrNull { it.name == p.stationName }?.id
                    startActivity(Intent(this, TimersActivity::class.java).putExtra("station", id))
                } else {
                    Store.saveProgs(this, Store.progs(this).filter { it.id != p.id }); refresh()
                }
            }.show()
    }

    private fun add() {
        val sts = Store.stations(this).sortedBy { norm(it.name) }
        if (sts.isEmpty()) { toast("Chưa có danh sách đài"); return }
        val ll = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), 0)
        }
        val sp = Spinner(this)
        sp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, sts.map { it.name })
        val time = EditText(this).apply { hint = "Giờ, VD: 06:00-07:00 hằng ngày"; setSingleLine() }
        val title = EditText(this).apply { hint = "Tên chương trình"; setSingleLine() }
        ll.addView(sp); ll.addView(time); ll.addView(title)
        AlertDialog.Builder(this).setTitle("Thêm chương trình").setView(ll)
            .setPositiveButton("Lưu") { _, _ ->
                val t = title.text.toString().trim()
                if (t.isNotEmpty()) {
                    val pr = Prog(System.currentTimeMillis(), sts[sp.selectedItemPosition].name, time.text.toString().trim(), t)
                    Store.saveProgs(this, Store.progs(this) + pr); refresh()
                }
            }.setNegativeButton("Hủy", null).show()
    }
}
