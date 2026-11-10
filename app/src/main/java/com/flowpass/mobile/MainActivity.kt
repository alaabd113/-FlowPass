package com.flowpass.mobile

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.webkit.*
import android.widget.*

class MainActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var drawer: LinearLayout
    private lateinit var shade: View
    private lateinit var engineSwitch: Switch
    private lateinit var desktopSwitch: Switch

    private val prefs by lazy { getSharedPreferences("flowpass", MODE_PRIVATE) }
    private var engineEnabled = true
    private var desktopMode = true
    private var injectedUrl: String? = null
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var welcomeDialog: Dialog? = null

    private val navy = Color.rgb(10, 16, 30)
    private val panel = Color.rgb(22, 32, 52)
    private val gold = Color.rgb(222, 187, 96)
    private val muted = Color.rgb(166, 180, 203)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        engineEnabled = prefs.getBoolean("engine", true)
        desktopMode = prefs.getBoolean("desktop_mode", true)

        CookieManager.getInstance().setAcceptCookie(true)
        buildUi()

        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadsImagesAutomatically = true
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
            mediaPlaybackRequiresUserGesture = false
            textZoom = 100
            builtInZoomControls = true
            displayZoomControls = false
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            applyBrowserMode()
        }

        web.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                injectedUrl = null
                if (url?.startsWith(FLOW) == true) injectEngine()
                if (isGoogleAccountsUrl(url)) attachEmailHelper()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (desktopMode) enforceDesktopViewport()
                if (isGoogleAccountsUrl(url)) attachEmailHelper()
                if (url?.startsWith(FLOW) == true) {
                    CookieManager.getInstance().flush()
                    readDiagnostic()
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                // Keep ordinary HTTPS navigation in the app. Never intercept or rewrite Google sign-in.
                return uri.scheme == "intent" || (uri.scheme != "https" && uri.scheme != "http")
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    android.util.Log.w("FlowPass", "Page load error: ${error?.description}")
                }
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(view: WebView?, callback: ValueCallback<Array<Uri>>?, params: FileChooserParams?): Boolean {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = callback
                return try {
                    val picker = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                        putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
                        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    }
                    startActivityForResult(Intent.createChooser(picker, "اختيار صورة أو فيديو"), FILE_CHOOSER_REQUEST)
                    true
                } catch (error: Exception) {
                    filePathCallback?.onReceiveValue(null)
                    filePathCallback = null
                    android.util.Log.e("FlowPass", "File picker unavailable", error)
                    false
                }
            }

            override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?): Boolean {
                val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                val popup = WebView(this@MainActivity).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(popupView: WebView?, request: WebResourceRequest?): Boolean {
                            val target = request?.url?.toString() ?: return false
                            if (target.startsWith("https://") || target.startsWith("http://")) web.loadUrl(target)
                            popupView?.stopLoading()
                            popupView?.destroy()
                            return true
                        }
                    }
                }
                transport.webView = popup
                resultMsg.sendToTarget()
                return true
            }

            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                val text = message.message()
                if (text.contains("[FlowPass]")) {
                    android.util.Log.d("FlowPass", text)
                }
                return true
            }
        }

        if (savedInstanceState == null) {
            if (!prefs.getBoolean("welcome_seen", false)) showWelcomeDialog()
            web.loadUrl(FLOW_URL)
        } else {
            val restored = web.restoreState(savedInstanceState)
            if (restored == null) web.loadUrl(FLOW_URL)
        }
    }

    private fun applyBrowserMode() {
        val nativeUa = WebSettings.getDefaultUserAgent(this)
        val chromeVersion = Regex("Chrome/([0-9]+)").find(nativeUa)?.groupValues?.get(1) ?: "120"
        web.settings.apply {
            if (desktopMode) {
                userAgentString = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chromeVersion.0.0.0 Safari/537.36"
                // Desktop UA alone is not enough: force a desktop layout viewport.
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(true)
                textZoom = 100
            } else {
                userAgentString = nativeUa
                useWideViewPort = false
                loadWithOverviewMode = false
                textZoom = 100
            }
        }
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        web.setInitialScale(0)
        web.requestLayout()
    }

    /**
     * Keep the device portrait while presenting a 980 CSS-pixel desktop viewport.
     * WebView wide-viewport settings alone do not override width=device-width.
     */
    private fun enforceDesktopViewport() {
        if (!desktopMode || !::web.isInitialized) return
        val script = """
            (function() {
              if (window.__flowPassDesktopViewportInstalled) return;
              window.__flowPassDesktopViewportInstalled = true;
              const desktopViewport = 'width=980, initial-scale=1.0, minimum-scale=0.25, maximum-scale=5.0, user-scalable=yes';
              const apply = () => {
                let meta = document.querySelector('meta[name="viewport"]');
                if (!meta) {
                  meta = document.createElement('meta');
                  meta.name = 'viewport';
                  (document.head || document.documentElement).appendChild(meta);
                }
                if (meta.content !== desktopViewport) meta.setAttribute('content', desktopViewport);
              };
              apply();
              const observer = new MutationObserver(apply);
              observer.observe(document.documentElement, {
                subtree: true, childList: true, attributes: true,
                attributeFilter: ['content', 'name']
              });
              window.addEventListener('pageshow', apply);
              window.addEventListener('orientationchange', apply);
            })();
        """.trimIndent()
        web.evaluateJavascript(script, null)
    }

    private fun buildUi() {
        val root = FrameLayout(this)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(navy)
        }

        val top = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        top.addView(Button(this).apply {
            text = "☰"
            textSize = 24f
            setTextColor(gold)
            background = round(panel)
            contentDescription = "القائمة الجانبية"
            setOnClickListener { showDrawer() }
        }, LinearLayout.LayoutParams(dp(48), dp(46)))

        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        brand.addView(label("FLOWPASS", 22f, gold, true))
        brand.addView(label("PREMIUM DESKTOP BROWSER", 9f, Color.rgb(115, 190, 255), true))
        top.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        top.background = premiumGradient(Color.rgb(13, 23, 43), Color.rgb(7, 12, 24))
        top.elevation = dp(5).toFloat()

        top.addView(Button(this).apply {
            text = "↻"
            textSize = 22f
            setTextColor(gold)
            background = round(panel)
            contentDescription = "تحديث الصفحة"
            setOnClickListener { web.reload() }
        }, LinearLayout.LayoutParams(dp(48), dp(46)))
        body.addView(top)

        // The old browser-status card and visible URL have intentionally been removed.
        web = WebView(this)
        web.addJavascriptInterface(EmailBridge(), "FlowPassAccount")
        body.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))

        body.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setBackgroundColor(Color.rgb(7, 11, 21))
            addView(label("FLOWPASS • DESKTOP MODE", 9f, muted, true))
        })

        root.addView(body, FrameLayout.LayoutParams(-1, -1))
        shade = View(this).apply {
            setBackgroundColor(0x99000000.toInt())
            visibility = View.GONE
            setOnClickListener { hideDrawer() }
        }
        root.addView(shade, FrameLayout.LayoutParams(-1, -1))

        drawer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(15, 23, 40))
            setPadding(dp(18), dp(28), dp(18), dp(18))
            elevation = dp(16).toFloat()

            addView(label("FLOWPASS", 25f, gold, true))
            addView(label("PREMIUM DESKTOP EXPERIENCE", 9f, Color.rgb(115, 190, 255), true))
            addView(label("لوحة التحكم", 12f, muted, true).apply {
                setPadding(0, dp(4), 0, dp(10))
            })
            addView(View(this@MainActivity).apply {
                background = premiumGradient(gold, Color.rgb(75, 112, 170))
            }, LinearLayout.LayoutParams(-1, dp(2)))

            addView(label("وضع العرض", 12f, muted, true).apply {
                setPadding(0, dp(22), 0, dp(5))
            })
            addView(label(
                if (desktopMode) "وضع سطح المكتب مفعّل" else "وضع سطح المكتب متوقف",
                13f, if (desktopMode) Color.rgb(91, 220, 155) else muted, true
            ).apply {
                setPadding(0, 0, 0, dp(5))
            })
            desktopSwitch = Switch(this@MainActivity).apply {
                text = "عرض مواقع الكمبيوتر"
                setTextColor(Color.WHITE)
                isChecked = desktopMode
                setOnCheckedChangeListener { _, checked ->
                    desktopMode = checked
                    prefs.edit().putBoolean("desktop_mode", checked).apply()
                    applyBrowserMode()
                    web.reload()
                }
            }
            addView(desktopSwitch)
            addView(label(
                "لتسجيل الدخول بالبريد: أوقف وضع سطح المكتب مؤقتاً من المفتاح أعلاه، أكمل تسجيل الدخول، ثم ارجع إلى هنا وفعّله مجدداً لتصفح المواقع بنسخة الكمبيوتر.",
                11f, Color.rgb(226, 207, 157), false
            ).apply {
                setPadding(dp(11), dp(10), dp(11), dp(10))
                background = round(Color.rgb(31, 34, 42))
            })

            addView(label("محرك FlowPass", 12f, muted, true).apply {
                setPadding(0, dp(22), 0, dp(5))
            })
            engineSwitch = Switch(this@MainActivity).apply {
                text = "تفعيل المحرك"
                setTextColor(Color.WHITE)
                isChecked = engineEnabled
                setOnCheckedChangeListener { _, checked ->
                    engineEnabled = checked
                    prefs.edit().putBoolean("engine", checked).apply()
                    web.evaluateJavascript("window.__flowPassEnabled = $checked;", null)
                    if (checked) injectEngine()
                }
            }
            addView(engineSwitch)

            addView(label("الحساب", 12f, muted, true).apply {
                setPadding(0, dp(22), 0, dp(5))
            })
            addView(label(
                "يُحفظ البريد الذي تدخله على هذا الجهاز لتسهيل تعبئته لاحقاً. لا تُحفظ كلمة المرور ولا تتم قراءة رسائل Gmail.",
                11f, muted, false
            ))

            addView(Button(this@MainActivity).apply {
                text = "تحديث الصفحة"
                setTextColor(Color.WHITE)
                background = round(panel)
                setOnClickListener { hideDrawer(); web.reload() }
            }, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(22) })

            addView(Button(this@MainActivity).apply {
                text = "إغلاق القائمة"
                setTextColor(gold)
                background = round(panel)
                setOnClickListener { hideDrawer() }
            }, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(8) })

            addView(label("FLOWPASS", 11f, gold, true).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(18), 0, dp(3))
            })
            addView(label("By Alaa Abdulaziz", 10f, muted, true).apply {
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, dp(4))
            })
        }

        root.addView(drawer, FrameLayout.LayoutParams(dp(292), -1, Gravity.START))
        drawer.visibility = View.GONE
        setContentView(root)
    }

    private fun showWelcomeDialog() {
        if (isFinishing || welcomeDialog?.isShowing == true) return

        val dialog = Dialog(this)
        welcomeDialog = dialog

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(28), dp(24), dp(24))
            background = round(Color.rgb(15, 23, 40))
        }
        content.addView(label("FLOWPASS", 25f, gold, true).apply {
            gravity = Gravity.CENTER
        })
        content.addView(label(
            "تم تطوير التطبيق بواسطة علاء عبد العزيز",
            15f, Color.WHITE, true
        ).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, dp(8))
        })
        content.addView(label(
            "يسعدنا انضمامك إلى قناتنا على تيليجرام. اضغط للاشتراك ثم ارجع للتطبيق، وسيكمل العمل تلقائياً.",
            13f, muted, false
        ).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(12))
        })
        content.addView(label("ملاحظة تسجيل الدخول المهمة", 13f, gold, true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(5), 0, dp(5))
        })
        content.addView(label(
            "وضع سطح المكتب مفعّل تلقائياً. عند تسجيل الدخول بالبريد الإلكتروني، أوقفه مؤقتاً من القائمة الجانبية، وبعد اكتمال تسجيل الدخول ارجع وفعّله من جديد.",
            12f, Color.rgb(226, 207, 157), false
        ).apply {
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = round(Color.rgb(31, 34, 42))
        })
        content.addView(Button(this).apply {
            text = "اشترك بقناة تيليجرام"
            textSize = 14f
            setTextColor(Color.rgb(10, 16, 30))
            background = round(gold)
            setOnClickListener {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TELEGRAM_URL)))
                } catch (error: Exception) {
                    android.util.Log.w("FlowPass", "Could not open Telegram channel", error)
                } finally {
                    dialog.dismiss()
                }
            }
        }, LinearLayout.LayoutParams(-1, dp(50)))

        dialog.setContentView(content)
        dialog.setCancelable(true)
        dialog.setOnDismissListener {
            prefs.edit().putBoolean("welcome_seen", true).apply()
            welcomeDialog = null
        }
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.90f).toInt(),
            -2
        )
    }

    private fun isGoogleAccountsUrl(url: String?): Boolean {
        return try {
            val uri = Uri.parse(url ?: return false)
            uri.scheme == "https" && uri.host == "accounts.google.com"
        } catch (_: Exception) {
            false
        }
    }

    private fun attachEmailHelper() {
        val script = """
            (function() {
              if (window.__flowPassEmailHelperAttached) return;
              window.__flowPassEmailHelperAttached = true;
              const attach = () => {
                const field = document.querySelector(
                  'input[type="email"], input[autocomplete="username"]'
                );
                if (!field || field.dataset.flowPassBound === '1') return;
                field.dataset.flowPassBound = '1';
                const saved = FlowPassAccount.getEmail();
                if (!field.value && saved) {
                  field.value = saved;
                  field.dispatchEvent(new Event('input', { bubbles: true }));
                  field.dispatchEvent(new Event('change', { bubbles: true }));
                }
                const save = () => {
                  const value = (field.value || '').trim();
                  if (value.includes('@')) FlowPassAccount.saveEmail(value);
                };
                field.addEventListener('change', save);
                field.addEventListener('blur', save);
              };
              attach();
              new MutationObserver(attach).observe(document.documentElement, {
                childList: true, subtree: true
              });
            })();
        """.trimIndent()
        web.evaluateJavascript(script, null)
    }

    private inner class EmailBridge {
        @JavascriptInterface
        fun getEmail(): String = prefs.getString("google_email", "") ?: ""

        @JavascriptInterface
        fun saveEmail(value: String?) {
            if (!isGoogleAccountsUrl(web.url)) return
            val email = value?.trim()?.take(254) ?: return
            if (email.matches(Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"))) {
                prefs.edit().putString("google_email", email).apply()
            }
        }
    }

    private fun injectEngine() {
        if (!engineEnabled) return
        val url = web.url ?: return
        if (!url.startsWith(FLOW) || injectedUrl == url) return
        injectedUrl = url
        try {
            val script = assets.open("flowpass.user.js").bufferedReader().use { it.readText() }
            web.evaluateJavascript(
                "(function(){window.__flowPassEnabled=$engineEnabled;" + script + "})();",
                null
            )
        } catch (error: Exception) {
            injectedUrl = null
            android.util.Log.e("FlowPass", "Engine injection failed", error)
        }
    }

    private fun readDiagnostic() {
        web.evaluateJavascript(
            "JSON.stringify(window.__flowLocalDiagnostic || {state:'waiting'})"
        ) { result ->
            android.util.Log.d("FlowPass", "Diagnostic: $result")
        }
    }

    private fun showDrawer() {
        drawer.visibility = View.VISIBLE
        shade.visibility = View.VISIBLE
        drawer.bringToFront()
    }

    private fun hideDrawer() {
        drawer.visibility = View.GONE
        shade.visibility = View.GONE
    }

    private fun label(text: String, size: Float, color: Int, bold: Boolean) =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
        }

    private fun round(color: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(14).toFloat()
        setStroke(dp(1), Color.argb(70, 222, 187, 96))
    }

    private fun premiumGradient(start: Int, end: Int) = GradientDrawable(
        GradientDrawable.Orientation.TL_BR, intArrayOf(start, end)
    ).apply {
        cornerRadius = dp(16).toFloat()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    @Deprecated("Deprecated in Android API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILE_CHOOSER_REQUEST) {
            val results = if (resultCode == RESULT_OK && data != null) {
                val clip = data.clipData
                when {
                    clip != null -> Array(clip.itemCount) { index -> clip.getItemAt(index).uri }
                    data.data != null -> arrayOf(data.data!!)
                    else -> null
                }
            } else null
            filePathCallback?.onReceiveValue(results)
            filePathCallback = null
        }
    }

    override fun onPause() {
        if (::web.isInitialized) CookieManager.getInstance().flush()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::web.isInitialized) web.saveState(outState)
    }

    @Deprecated("Deprecated in Android API")
    override fun onBackPressed() {
        if (::drawer.isInitialized && drawer.visibility == View.VISIBLE) {
            hideDrawer()
        } else if (::web.isInitialized && web.canGoBack()) {
            web.goBack()
        } else {
            super.onBackPressed()
        }
    }

    companion object {
        private const val FLOW = "https://flow.google.com"
        private const val FLOW_URL = "https://flow.google.com/"
        private const val TELEGRAM_URL = "https://t.me/EngAlaaAIStudio"
        private const val ACCOUNTS = "https://accounts.google.com"
        private const val FILE_CHOOSER_REQUEST = 4107
    }
}
