package com.flowpass.mobile

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.webkit.*
import android.widget.*

class MainActivity : Activity() {
 private lateinit var web:WebView
 private lateinit var status:TextView
 private lateinit var info:TextView
 private lateinit var drawer:LinearLayout
 private lateinit var shade:View
 private lateinit var engine:Switch
 private val prefs by lazy { getSharedPreferences("flowpass",MODE_PRIVATE) }
 private var enabled=true
 private var injected:String?=null
 private val navy=Color.rgb(10,16,30)
 private val panel=Color.rgb(22,32,52)
 private val gold=Color.rgb(222,187,96)
 private val muted=Color.rgb(166,180,203)

 @SuppressLint("SetJavaScriptEnabled")
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  enabled=prefs.getBoolean("engine",true)
  CookieManager.getInstance().setAcceptCookie(true)
  buildUi()
  CookieManager.getInstance().setAcceptThirdPartyCookies(web,true)
  web.settings.apply {
   javaScriptEnabled=true;domStorageEnabled=true;databaseEnabled=true
   loadsImagesAutomatically=true;javaScriptCanOpenWindowsAutomatically=true
   setSupportMultipleWindows(false);useWideViewPort=true;loadWithOverviewMode=false
   builtInZoomControls=true;displayZoomControls=false
   allowFileAccess=false;allowContentAccess=false
   mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
   userAgentString=DESKTOP_UA
  }
  web.addJavascriptInterface(EmailBridge(),"FlowPassAccount")
  web.webViewClient=object:WebViewClient(){
   override fun onPageStarted(v:WebView?,u:String?,b:android.graphics.Bitmap?){
    injected=null;status.text="جارٍ فتح وضع الكمبيوتر…";info.text=u ?: ""
    if(u?.startsWith(FLOW)==true) injectEngine()
   }
   override fun onPageFinished(v:WebView?,u:String?){
    if(u?.startsWith("https://accounts.google.com")==true) rememberEmail()
    if(u?.startsWith(FLOW)==true){status.text="Flow جاهز • وضع الكمبيوتر";info.text="جلسة Google محفوظة على الجهاز.";readDiagnostic()}
   }
   override fun shouldOverrideUrlLoading(v:WebView?,r:WebResourceRequest?):Boolean{
    val u=r?.url ?: return false
    return u.scheme=="intent" || (u.scheme!="https" && u.scheme!="http")
   }
  }
  web.webChromeClient=object:WebChromeClient(){
   override fun onConsoleMessage(m:ConsoleMessage):Boolean{
    val s=m.message()
    if(s.contains("[FlowPass]")){info.text=s.take(170);if(s.contains("applied"))status.text="تم تطبيق المحرك";if(s.contains("schema mismatch"))status.text="تغيّر شكل الاستجابة"}
    return true
   }
  }
  if(savedInstanceState==null)web.loadUrl(FLOW_URL) else web.restoreState(savedInstanceState)
 }

 private fun buildUi(){
  val root=FrameLayout(this)
  val body=LinearLayout(this).apply{orientation=1;setBackgroundColor(navy)}
  val top=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL;setPadding(dp(12),dp(10),dp(12),dp(10))}
  top.addView(Button(this).apply{text="☰";textSize=24f;setTextColor(gold);background=round(panel);setOnClickListener{showDrawer()}},LinearLayout.LayoutParams(dp(48),dp(46)))
  val brand=LinearLayout(this).apply{orientation=1;setPadding(dp(12),0,0,0)}
  brand.addView(label("FLOWPASS",21f,gold,true));brand.addView(label("DESKTOP FLOW • MOBILE APP",9f,muted,true))
  top.addView(brand,LinearLayout.LayoutParams(0,-2,1f))
  top.addView(Button(this).apply{text="↻";textSize=22f;setTextColor(gold);background=round(panel);setOnClickListener{web.reload()}},LinearLayout.LayoutParams(dp(48),dp(46)))
  body.addView(top)
  val card=LinearLayout(this).apply{orientation=1;background=round(panel);setPadding(dp(14),dp(10),dp(14),dp(10))}
  val row=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
  row.addView(label("حالة المتصفح",12f,Color.WHITE,true),LinearLayout.LayoutParams(0,-2,1f))
  status=label("جارٍ تجهيز وضع الكمبيوتر…",11f,gold,true);row.addView(status);card.addView(row)
  info=label("سيتم فتح Google Flow بواجهة سطح مكتب.",10f,muted,false).apply{setPadding(0,dp(5),0,0)}
  card.addView(info)
  body.addView(LinearLayout(this).apply{setPadding(dp(12),0,dp(12),dp(8));addView(card)})
  web=WebView(this);body.addView(web,LinearLayout.LayoutParams(-1,0,1f))
  body.addView(LinearLayout(this).apply{gravity=Gravity.CENTER;setPadding(dp(8),dp(6),dp(8),dp(6));setBackgroundColor(Color.rgb(7,11,21));addView(label("FLOWPASS • DESKTOP MODE",9f,muted,true))})
  root.addView(body,FrameLayout.LayoutParams(-1,-1))
  shade=View(this).apply{setBackgroundColor(0x99000000.toInt());visibility=View.GONE;setOnClickListener{hideDrawer()}}
  root.addView(shade,FrameLayout.LayoutParams(-1,-1))
  drawer=LinearLayout(this).apply{
   orientation=1;setBackgroundColor(Color.rgb(15,23,40));setPadding(dp(18),dp(28),dp(18),dp(18));elevation=dp(16).toFloat()
   addView(label("FLOWPASS",23f,gold,true));addView(label("لوحة التحكم",12f,muted,true))
   addView(label("المحرك",12f,muted,true).apply{setPadding(0,dp(20),0,dp(5))})
   engine=Switch(this@MainActivity).apply{text="تفعيل FlowPass";setTextColor(Color.WHITE);isChecked=enabled;setOnCheckedChangeListener{_,v->enabled=v;prefs.edit().putBoolean("engine",v).apply();web.evaluateJavascript("window.__flowPassEnabled=$"+"v;",null);status.text=if(v)"المحرك مفعّل" else "المحرك متوقف"}}
   addView(engine)
   addView(label("الحساب",12f,muted,true).apply{setPadding(0,dp(22),0,dp(5))})
   addView(label("يحفظ البريد الذي تدخله في صفحة Google ويحافظ على جلسة الدخول. لا يحفظ كلمة المرور.",11f,muted,false))
   addView(Button(this@MainActivity).apply{text="فتح Flow بوضع الكمبيوتر";setTextColor(gold);background=round(panel);setOnClickListener{hideDrawer();web.loadUrl(FLOW_URL)}},LinearLayout.LayoutParams(-1,dp(48)).apply{topMargin=dp(18)})
   addView(Button(this@MainActivity).apply{text="تحديث الصفحة";setTextColor(Color.WHITE);background=round(panel);setOnClickListener{hideDrawer();web.reload()}},LinearLayout.LayoutParams(-1,dp(46)).apply{topMargin=dp(8)})
  }
  root.addView(drawer,FrameLayout.LayoutParams(dp(292),-1,Gravity.START));drawer.visibility=View.GONE
  setContentView(root)
 }
 private fun rememberEmail(){
  val js="""(function(){const a=()=>{const f=document.querySelector('input[type=email],input[autocomplete=username]');if(!f)return;const s=()=>{if(f.value&&f.value.includes('@'))FlowPassAccount.saveEmail(f.value)};f.addEventListener('change',s);f.addEventListener('blur',s)};a();new MutationObserver(a).observe(document.documentElement,{childList:true,subtree:true})})();"""
  web.evaluateJavascript(js,null)
 }
 inner class EmailBridge{
  @JavascriptInterface fun saveEmail(value:String?){
   if(web.url?.startsWith("https://accounts.google.com")!=true)return
   val e=value?.trim()?.take(254)?:return
   if(e.matches(Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")))prefs.edit().putString("google_email",e).apply()
  }
 }
 private fun injectEngine(){
  if(!enabled)return
  val u=web.url?:return
  if(!u.startsWith(FLOW)||injected==u)return
  injected=u
  try{val js=assets.open("flowpass.user.js").bufferedReader().use{it.readText()};web.evaluateJavascript("(function(){window.__flowPassEnabled=true;$"+"js})();",null);status.text="تم تحميل محرك FlowPass"}
  catch(e:Exception){injected=null;status.text="تعذر تحميل المحرك";info.text=e.message?:"خطأ"}
 }
 private fun readDiagnostic(){web.evaluateJavascript("JSON.stringify(window.__flowLocalDiagnostic || {state:'waiting'})"){v->val s=v?.removeSurrounding("\"","\"")?.replace("\\\"","\"")?:"waiting";if(s.contains("applied"))status.text="تم تطبيق المحرك";info.text=s.take(180)}}
 private fun showDrawer(){drawer.visibility=View.VISIBLE;shade.visibility=View.VISIBLE;drawer.bringToFront()}
 private fun hideDrawer(){drawer.visibility=View.GONE;shade.visibility=View.GONE}
 private fun label(s:String,z:Float,c:Int,b:Boolean)=TextView(this).apply{text=s;textSize=z;setTextColor(c);if(b)typeface=Typeface.DEFAULT_BOLD;gravity=Gravity.CENTER_VERTICAL}
 private fun round(c:Int)=GradientDrawable().apply{setColor(c);cornerRadius=dp(12).toFloat()}
 private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
 override fun onPause(){if(::web.isInitialized)CookieManager.getInstance().flush();super.onPause()}
 override fun onSaveInstanceState(b:Bundle){super.onSaveInstanceState(b);if(::web.isInitialized)web.saveState(b)}
 @Deprecated("Deprecated in Android API")
 override fun onBackPressed(){if(::drawer.isInitialized&&drawer.visibility==View.VISIBLE)hideDrawer()else if(::web.isInitialized&&web.canGoBack())web.goBack()else super.onBackPressed()}
 companion object{
  private const val FLOW="https://flow.google.com"
  private const val FLOW_URL="https://flow.google.com/"
  private const val DESKTOP_UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
 }
}