package vn.radio.app

import android.app.Activity
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.webkit.SslErrorHandler
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout

/** Mở trang lịch phát sóng trên web (do tác giả app Radio Việt Nam cung cấp). */
class ScheduleWebActivity : Activity() {
    companion object {
        const val URL = "https://app.giavangtygia.com/api/schedule"
    }

    private lateinit var web: WebView

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        title = intent.getStringExtra("title") ?: "Lịch phát sóng"
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(homeButton())
        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.webViewClient = object : WebViewClient() {
            // Android 6 đời cũ có thể không tin chứng chỉ mới: chỉ bỏ qua cho các tên miền của trang lịch và thời tiết
            override fun onReceivedSslError(v: WebView?, h: SslErrorHandler?, e: SslError?) {
                val host = Uri.parse(e?.url ?: "").host ?: ""
                if (host.endsWith("giavangtygia.com") || host.endsWith("pages.dev") || host.endsWith("onrender.com")) h?.proceed() else h?.cancel()
            }
        }
        root.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        web.loadUrl(intent.getStringExtra("url") ?: URL)
    }

    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }
}
