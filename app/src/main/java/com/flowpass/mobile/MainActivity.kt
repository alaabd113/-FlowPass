package com.flowpass.mobile

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.webkit.*
import android.view.View
import android.widget.*
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup

class MainActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var status: TextView
    private lateinit var detail: TextView
    private var enabled = true

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(14, 19, 32))
            setPadding(20, 18, 20, 12)
        }
        val title = TextView(this).apply {
            text = "FLOWPASS  /  MOBILE"
            textSize = 21f; setTextColor(Color.rgb(226,190,72))
            typeface = Typeface.DEFAULT_BOLD
        }
        status = TextView(this).apply {
            text = "الحالة: جارٍ تجهيز المتصفح…"
            textSize = 15f; setTextColor(Color.WHITE); setPadding(0,12,0,4)
        }
        detail = TextView(this).apply {
            text = "يعمل السكربت على flow.google.com فقط."
            textSize = 12f; setTextColor(Color.LTGRAY); setPadding(0,0,0,12)
        }
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val launch = Button(this).apply {
            text = "فتح Google Flow"
            setOnClickListener { web.loadUrl("https://flow.google.com") }
        }
        val toggle = Button(this).apply {
            text = "إيقاف السكربت"
            setOnClickListener {
                enabled = !enabled
                text = if (enabled) "إيقاف السكربت" else "تشغيل السكربت"
                web.evaluateJavascript("window.__flowPassEnabled=${enabled};", null)
                status.text = if (enabled) "الحالة: السكربت مفعّل" else "الحالة: السكربت متوقف"
            }
        }
        controls.addView(launch, LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(toggle, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(title); root.addView(status); root.addView(detail); root.addView(controls)

        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    status.text = "الحالة: تحميل الصفحة…"
                    detail.text = url ?: ""
                }
                override fun onPageFinished(view: WebView?, url: String?) {
                    status.text = "الحالة: الصفحة جاهزة"
                    if (url?.startsWith("https://flow.google.com") == true) {
                        injectFlowPass()
                    }
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                    detail.text = "Console: ${msg.message()}"
                    return true
                }
            }
        }
        root.addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        web.loadUrl("https://flow.google.com")
    }

    private fun injectFlowPass() {
        try {
            val script = assets.open("flowpass.user.js").bufferedReader().use { it.readText() }
            val safe = script.replace("</script>", "<\\/script>")
            web.evaluateJavascript("""
                (function(){
                  if (!window.__flowPassInjected) {
                    window.__flowPassEnabled = ${enabled};
                    window.__flowPassInjected = true;
                    $safe
                  }
                  setTimeout(function(){
                    var d=window.__flowLocalDiagnostic;
                    if(d){ document.title='FlowPass • '+d.state; }
                  },1500);
                })();
            """.trimIndent(), null)
            status.text = "الحالة: تم حقن FlowPass"
            detail.text = "إذا ظهر applied في سجل Console فهذا يعني أن الاستجابة عُدّلت."
        } catch (e: Exception) {
            status.text = "تعذر تحميل السكربت"
            detail.text = e.message ?: "خطأ غير معروف"
        }
    }

    override fun onBackPressed() {
        if (::web.isInitialized && web.canGoBack()) web.goBack() else super.onBackPressed()
    }
}
