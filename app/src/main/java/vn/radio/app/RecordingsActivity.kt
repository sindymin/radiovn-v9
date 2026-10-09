package vn.radio.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecordingsActivity : Activity() {
    private lateinit var lv: ListView
    private var files = listOf<File>()

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        title = "Bản ghi âm"
        lv = ListView(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(homeButton())
        root.addView(lv, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        lv.setOnItemClickListener { _, _, p, _ ->
            try {
                startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.fromFile(files[p]), "audio/*"))
            } catch (e: Exception) { toast("Không có app nào mở được file này") }
        }
        lv.setOnItemLongClickListener { _, _, p, _ ->
            AlertDialog.Builder(this).setTitle(files[p].name).setMessage("Xóa bản ghi này?")
                .setPositiveButton("Xóa") { _, _ -> files[p].delete(); refresh() }
                .setNegativeButton("Hủy", null).show()
            true
        }
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun refresh() {
        files = recDirs(this).flatMap { it.listFiles()?.toList() ?: emptyList() }
            .filter { it.isFile }.sortedByDescending { it.lastModified() }
        val fmt = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
        lv.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, files.map {
            "${it.name}\n%.1f MB · ${fmt.format(Date(it.lastModified()))}".format(it.length() / 1048576.0)
        })
    }
}
