package com.flowpass.mobile

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.*
import android.widget.*

class MainActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var statusText: TextView
    private lateinit var detailText: TextView
    private lateinit var toggle: Switch
    private var scriptEnabled = true
    private var injectedForUrl: String? = null
    private val navy = Color.rgb(13,20,36)
    private val panel = Color.rgb(23,32,51)
    private val gold = Color.rgb(217,182,90)
    private val muted = Color.rgb(167,178,198)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CookieManager.getInstance().setAcceptCookie(true)
        buildInterface()
        configureBrowser()
        if (savedInstanceState == null) web.loadUrl(FLOW_URL) else web.restoreState(savedInstanceState)
    }

    private fun buildInterface() {
        val root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(navy) }
        val header = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; setPadding(dp(18),dp(14),dp(18),dp(12)) }
        val brand = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        brand.addView(label("FLOWPASS",22f,gold,true))
        brand.addView(label("MOBILE  •  FLOW ACCESS COMPANION",9f,muted,true))
        header.addView(brand,LinearLayout.LayoutParams(0,-2,1f))
        header.addView(Button(this).apply { text="↻"; textSize=22f; setTextColor(gold); background=rounded(panel,12); setOnClickListener { web.reload() } },LinearLayout.LayoutParams(dp(48),dp(44)))
        root.addView(header)

        val card=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; background=rounded(panel,16); setPadding(dp(16),dp(12),dp(16),dp(12)) }
        val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
        row.addView(label("حالة الاتصال",13f,Color.WHITE,true),LinearLayout.LayoutParams(0,-2,1f))
        statusText=label("جارٍ تجهيز المتصفح…",12f,gold,true)
        row.addView(statusText); card.addView(row)
        detailText=label("سيتم تشغيل Google Flow داخل التطبيق.",11f,muted,false).apply { setPadding(0,dp(7),0,0) }
        card.addView(detailText)
        val toggleRow=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; setPadding(0,dp(10),0,0) }
        toggleRow.addView(label("FlowPass Engine",13f,Color.WHITE,true),LinearLayout.LayoutParams(0,-2,1f))
        toggle=Switch(this).apply {
            text="تفعيل"; setTextColor(Color.WHITE); isChecked=true
            setOnCheckedChangeListener { _,checked ->
                scriptEnabled=checked
                if (::web.isInitialized) web.evaluateJavascript("window.__flowPassEnabled=$checked;",null)
                statusText.text=if(checked) "المحرك مفعّل" else "المحرك متوقف"
                detailText.text=if(checked) "سيتم تعديل الاستجابة المستهدفة عند تطابقها." else "التصفح مستمر، لكن التعديل متوقف."
            }
        }
        toggleRow.addView(toggle); card.addView(toggleRow)
        val cardWrap=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(14),0,dp(14),dp(12)); addView(card) }
        root.addView(cardWrap)

        web=WebView(this)
        root.addView(web,LinearLayout.LayoutParams(-1,0,1f))
        val footer=LinearLayout(this).apply { gravity=Gravity.CENTER; setPadding(dp(8),dp(7),dp(8),dp(7)); setBackgroundColor(Color.rgb(10,15,27)) }
        footer.addView(label("FLOWPASS MOBILE  •  v1.0.1",10f,muted,true)); root.addView(footer)
        setContentView(root)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureBrowser() {
        web.settings.apply {
            javaScriptEnabled=true; domStorageEnabled=true; databaseEnabled=true
            loadsImagesAutomatically=true; javaScriptCanOpenWindowsAutomatically=true
            setSupportMultipleWindows(false); builtInZoomControls=false; displayZoomControls=false
            allowFileAccess=false; allowContentAccess=false
            mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        web.webViewClient=object:WebViewClient() {
            override fun onPageStarted(view:WebView?,url:String?,favicon:android.graphics.Bitmap?) {
                injectedForUrl=null; statusText.text="جارٍ فتح الصفحة…"; detailText.text=url ?: ""
                if(url?.startsWith(FLOW_ORIGIN)==true) injectFlowPass()
            }
            override fun onPageFinished(view:WebView?,url:String?) {
                if(url?.startsWith(FLOW_ORIGIN)==true) {
                    statusText.text=if(scriptEnabled) "Google Flow جاهز • المحرك مفعّل" else "Google Flow جاهز • المحرك متوقف"
                    readDiagnostic()
                } else statusText.text="صفحة خارج Flow"
            }
            override fun shouldOverrideUrlLoading(view:WebView?,request:WebResourceRequest?):Boolean {
                val uri=request?.url ?: return false
                return uri.scheme=="intent" || (uri.scheme!="https" && uri.scheme!="http")
            }
        }
        web.webChromeClient=object:WebChromeClient() {
            override fun onConsoleMessage(message:ConsoleMessage):Boolean {
                val msg=message.message()
                if(msg.contains("[FlowPass]")) {
                    detailText.text=msg.take(180)
                    if(msg.contains("applied")) statusText.text="تم تعديل استجابة Flow"
                    if(msg.contains("schema mismatch")) statusText.text="تغيّر شكل الاستجابة — لم يتم التعديل"
                }
                return true
            }
        }
    }

    private fun injectFlowPass() {
        if(!scriptEnabled) return
        val url=web.url ?: return
        if(!url.startsWith(FLOW_ORIGIN) || injectedForUrl==url) return
        injectedForUrl=url
        try {
            val script=assets.open("flowpass.user.js").bufferedReader().use { it.readText() }
            web.evaluateJavascript("(function(){window.__flowPassEnabled=true;$script})();",null)
            statusText.text="تم تحميل محرك FlowPass"
        } catch(e:Exception) {
            injectedForUrl=null; statusText.text="تعذر تحميل المحرك"; detailText.text=e.message ?: "خطأ غير معروف"
        }
    }

    private fun readDiagnostic() {
        web.evaluateJavascript("JSON.stringify(window.__flowLocalDiagnostic || {state:'waiting'})") { result ->
            val value=result?.removeSurrounding("\"","\"")?.replace("\\\"","\"") ?: "waiting"
            if(value.contains("applied")) statusText.text="تم تعديل استجابة Flow"
            detailText.text=value.take(180)
        }
    }

    private fun label(value:String,size:Float,color:Int,bold:Boolean)=TextView(this).apply {
        text=value; textSize=size; setTextColor(color); if(bold) typeface=Typeface.DEFAULT_BOLD; gravity=Gravity.CENTER_VERTICAL
    }
    private fun rounded(color:Int,radius:Int)=GradientDrawable().apply { setColor(color); cornerRadius=dp(radius).toFloat() }
    private fun dp(value:Int)=(value*resources.displayMetrics.density).toInt()
    override fun onSaveInstanceState(outState:Bundle) { super.onSaveInstanceState(outState); if(::web.isInitialized) web.saveState(outState) }
    @Deprecated("Deprecated in Android API")
    override fun onBackPressed() { if(::web.isInitialized && web.canGoBack()) web.goBack() else super.onBackPressed() }
    companion object { private const val FLOW_ORIGIN="https://flow.google.com"; private const val FLOW_URL="https://flow.google.com/" }
}