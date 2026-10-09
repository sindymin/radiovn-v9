package vn.radio.app

import android.content.Context
import android.os.Environment
import android.widget.Toast
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.cert.X509Certificate
import java.text.Normalizer
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()
fun Context.toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

fun norm(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
        .replace('đ', 'd').replace('Đ', 'D').lowercase()

fun Station.isHls() = hls || url.contains(".m3u8")

private val lenientFactory: SSLSocketFactory by lazy {
    val tm = object : X509TrustManager {
        override fun checkClientTrusted(c: Array<X509Certificate>?, a: String?) {}
        override fun checkServerTrusted(c: Array<X509Certificate>?, a: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }
    val ctx = SSLContext.getInstance("TLS")
    ctx.init(null, arrayOf<TrustManager>(tm), null)
    ctx.socketFactory
}

/** Mở kết nối HTTP(S). Nếu máy cũ không tin chứng chỉ thì thử lại không kiểm tra chứng chỉ. */
fun openStream(url: String, timeout: Int = 15000): HttpURLConnection =
    try { connect(url, timeout, false) } catch (e: SSLException) { connect(url, timeout, true) }

private fun connect(url: String, timeout: Int, lenient: Boolean): HttpURLConnection {
    var u = url
    for (i in 0..5) {
        val c = URL(u).openConnection() as HttpURLConnection
        if (lenient && c is HttpsURLConnection) {
            c.sslSocketFactory = lenientFactory
            c.hostnameVerifier = HostnameVerifier { _, _ -> true }
        }
        c.connectTimeout = timeout
        c.readTimeout = timeout
        c.setRequestProperty("User-Agent", "RadioVN/1.0")
        c.instanceFollowRedirects = false
        val code = c.responseCode
        if (code in 300..399) {
            val l = c.getHeaderField("Location") ?: break
            u = URL(URL(u), l).toString()
            c.disconnect()
            continue
        }
        return c
    }
    throw IOException("Too many redirects")
}

fun recDirs(c: Context): List<File> = listOfNotNull(
    File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "RadioVN"),
    c.getExternalFilesDir(null)?.let { File(it, "Recordings") }
)

fun recDirForWrite(c: Context): File {
    val d = recDirs(c)
    val p = d.first()
    if ((p.exists() || p.mkdirs()) && p.canWrite()) return p
    val a = d.last()
    a.mkdirs()
    return a
}

/** Nút "← Trang chính" dùng cho các màn hình phụ. */
fun android.app.Activity.homeButton(): android.widget.Button = android.widget.Button(this).apply {
    text = "← Trang chính"
    setOnClickListener {
        startActivity(
            android.content.Intent(this@homeButton, MainActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finish()
    }
}
