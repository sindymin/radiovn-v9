package vn.radio.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var search: EditText
    private lateinit var now: TextView
    private lateinit var btnPlay: Button
    private lateinit var btnRec: Button
    private lateinit var rootView: LinearLayout
    private lateinit var audio: AudioManager
    private lateinit var volBar: SeekBar
    private var all = listOf<Station>()
    private var shown = listOf<Station>()
    private var favSet = setOf<String>()
    private var favOnly = false
    private var cur: Station? = null
    private val h = Handler(Looper.getMainLooper())

    private val adapter = object : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(p: Int) = shown[p]
        override fun getItemId(p: Int) = p.toLong()
        override fun getView(p: Int, cv: View?, parent: ViewGroup?): View {
            val v = cv ?: layoutInflater.inflate(android.R.layout.simple_list_item_2, parent, false)
            val s = shown[p]
            v.findViewById<TextView>(android.R.id.text1).text = (if (s.id in favSet) "★ " else "") + s.name
            v.findViewById<TextView>(android.R.id.text2).text =
                listOf(s.city, s.tags).filter { it.isNotBlank() }.joinToString(" · ")
            return v
        }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        volumeControlStream = AudioManager.STREAM_MUSIC
        audio = getSystemService(AUDIO_SERVICE) as AudioManager
        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        search = EditText(this).apply {
            hint = "Tìm kênh..."
            setSingleLine()
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { refresh() }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }
        val tabs = LinearLayout(this)
        fun tab(t: String, fav: Boolean) = Button(this).apply {
            text = t
            setOnClickListener { favOnly = fav; refresh() }
        }
        tabs.addView(tab("Tất cả", false), LinearLayout.LayoutParams(0, -2, 1f))
        tabs.addView(tab("★ Yêu thích", true), LinearLayout.LayoutParams(0, -2, 1f))

        val lv = ListView(this)
        lv.adapter = adapter
        lv.setOnItemClickListener { _, _, p, _ -> play(shown[p]) }
        lv.setOnItemLongClickListener { _, _, p, _ -> stationMenu(shown[p]); true }

        now = TextView(this).apply { textSize = 14f }
        btnPlay = Button(this).apply { setOnClickListener { togglePlay() } }
        btnRec = Button(this).apply { setOnClickListener { toggleRec() } }
        val bar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        bar.addView(now, LinearLayout.LayoutParams(0, -2, 1f))
        bar.addView(btnPlay)
        bar.addView(btnRec)

        volBar = SeekBar(this).apply {
            max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            progress = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    if (fromUser) audio.setStreamVolume(AudioManager.STREAM_MUSIC, p, 0)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        fun volBtn(t: String, dir: Int) = Button(this).apply {
            text = t
            minWidth = 0
            minimumWidth = 0
            setOnClickListener {
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, 0)
                volBar.progress = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            }
        }
        val volRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, dp(8), 0)
        }
        volRow.addView(volBtn("−", AudioManager.ADJUST_LOWER))
        volRow.addView(volBar, LinearLayout.LayoutParams(0, -2, 1f))
        volRow.addView(volBtn("+", AudioManager.ADJUST_RAISE))

        root.addView(search)
        root.addView(tabs)
        root.addView(lv, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(volRow)
        root.addView(bar)
        rootView = root
        setContentView(root)
        root.isFocusableInTouchMode = true
        root.requestFocus()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)

        all = Store.stations(this)
        if (all.isEmpty()) reload() else refresh()
        tick()
    }

    /** Bấm ra ngoài ô tìm kiếm thì ẩn bàn phím và bỏ focus. */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            val v = currentFocus
            if (v is EditText) {
                val r = Rect()
                v.getGlobalVisibleRect(r)
                if (!r.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                    (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                        .hideSoftInputFromWindow(v.windowToken, 0)
                    v.clearFocus()
                    rootView.requestFocus()
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onDestroy() { h.removeCallbacksAndMessages(null); super.onDestroy() }

    private fun tick() {
        val pn = PlayerService.name
        val p = when {
            pn == null -> "Chưa phát"
            PlayerService.playing -> "▶ $pn"
            else -> "Đang kết nối: $pn"
        }
        val r = RecorderService.active?.let { "\n● Đang ghi: $it" } ?: ""
        now.text = p + r
        volBar.progress = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        btnPlay.text = if (pn != null) "Dừng" else "Phát"
        btnRec.text = if (RecorderService.active != null) "Dừng ghi" else "Ghi âm"
        h.postDelayed({ tick() }, 1000)
    }

    private fun refresh() {
        favSet = Store.favs(this)
        val q = norm(search.text.toString().trim())
        shown = all.filter {
            (!favOnly || it.id in favSet) &&
                (q.isEmpty() || norm(it.name + " " + it.tags + " " + it.city).contains(q))
        }.sortedBy { if (it.id in favSet) 0 else 1 }
        adapter.notifyDataSetChanged()
    }

    private fun reload() {
        toast("Đang tải danh sách đài...")
        Thread {
            try {
                val l = Api.fetch()
                Store.saveFetched(this, l)
                runOnUiThread { all = Store.stations(this); refresh(); toast("Đã tải ${l.size} đài") }
            } catch (e: Exception) {
                runOnUiThread { toast("Không tải được danh sách: ${e.message}\nBạn có thể thêm đài thủ công từ menu.") }
            }
        }.start()
    }

    private fun play(s: Station) { cur = s; PlayerService.play(this, s) }

    private fun togglePlay() {
        if (PlayerService.name != null) PlayerService.stop(this)
        else cur?.let { play(it) } ?: toast("Chọn một đài trước")
    }

    private fun toggleRec() {
        if (RecorderService.active != null) { RecorderService.stop(this); return }
        val s = cur ?: run { toast("Chọn một đài trước"); return }
        record(s)
    }

    private fun record(s: Station) {
        if (s.isHls()) { toast("Đài này dùng HLS (.m3u8), chưa hỗ trợ ghi âm"); return }
        RecorderService.start(this, s.name, s.url, 0)
        toast("Bắt đầu ghi âm: ${s.name}")
    }

    private fun stationMenu(s: Station) {
        val fav = s.id in favSet
        AlertDialog.Builder(this).setTitle(s.name)
            .setItems(arrayOf(if (fav) "Bỏ yêu thích" else "Thêm vào yêu thích", "Ghi âm ngay (im lặng)", "Hẹn giờ ghi âm")) { _, i ->
                when (i) {
                    0 -> { Store.toggleFav(this, s.id); refresh() }
                    1 -> record(s)
                    2 -> startActivity(Intent(this, TimersActivity::class.java).putExtra("station", s.id))
                }
            }.show()
    }

    private fun addCustom() {
        val ll = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), 0)
        }
        val n = EditText(this).apply { hint = "Tên đài" }
        val u = EditText(this).apply { hint = "Link stream (http...)"; setSingleLine() }
        ll.addView(n); ll.addView(u)
        AlertDialog.Builder(this).setTitle("Thêm đài").setView(ll)
            .setPositiveButton("Lưu") { _, _ ->
                val name = n.text.toString().trim()
                val url = u.text.toString().trim()
                if (name.isNotEmpty() && url.startsWith("http")) {
                    Store.addCustom(this, Station("c" + System.currentTimeMillis(), name, url, "tự thêm"))
                    all = Store.stations(this); refresh()
                }
            }.setNegativeButton("Hủy", null).show()
    }

    private fun importMany() {
        val et = EditText(this).apply {
            hint = "Mỗi dòng một đài:\nTên đài | link stream"
            minLines = 6
            gravity = Gravity.TOP
        }
        AlertDialog.Builder(this).setTitle("Nhập nhiều đài")
            .setView(ScrollView(this).apply { addView(et) })
            .setPositiveButton("Thêm") { _, _ ->
                var n = 0
                val t = System.currentTimeMillis()
                et.text.toString().lines().forEachIndexed { i, line ->
                    val parts = line.split("|").map { it.trim() }
                    val url = parts.lastOrNull() ?: ""
                    if (parts.size >= 2 && parts[0].isNotEmpty() && url.startsWith("http")) {
                        Store.addCustom(this, Station("c$t-$i", parts[0], url, "tự thêm"))
                        n++
                    }
                }
                all = Store.stations(this); refresh()
                toast(if (n > 0) "Đã thêm $n đài" else "Không có dòng hợp lệ (cần dạng: Tên | http...)")
            }.setNegativeButton("Hủy", null).show()
    }

    private fun battery() {
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        } catch (e: Exception) {
            try { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
            catch (e2: Exception) { toast("Vào Cài đặt > Pin > Tối ưu hóa pin để tắt cho app này") }
        }
    }

    override fun onCreateOptionsMenu(m: Menu): Boolean {
        listOf("Hẹn giờ ghi âm", "Bản ghi âm", "Lịch phát sóng (web)", "Thêm đài thủ công",
            "Tải lại danh sách", "Cho phép chạy nền (ghi âm hẹn giờ)", "Nhập nhiều đài", "Lịch phát sóng tự nhập", "Thời tiết")
            .forEachIndexed { i, t -> m.add(0, i, i, t) }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            0 -> startActivity(Intent(this, TimersActivity::class.java))
            1 -> startActivity(Intent(this, RecordingsActivity::class.java))
            2 -> startActivity(Intent(this, ScheduleWebActivity::class.java))
            3 -> addCustom()
            4 -> reload()
            5 -> battery()
            6 -> importMany()
            7 -> startActivity(Intent(this, GuideActivity::class.java))
            8 -> startActivity(
                Intent(this, ScheduleWebActivity::class.java)
                    .putExtra("url", "https://radiovietnam-web-8d5r.onrender.com/tienich/thoitiet")
                    .putExtra("title", "Thời tiết")
            )
        }
        return true
    }
}
