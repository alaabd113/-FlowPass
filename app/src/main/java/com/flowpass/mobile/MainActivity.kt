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
    private lateinit var homePanel: ScrollView
    private lateinit var addressInput: EditText
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
        // FLOWPASS Premium: deep navy surfaces, metallic gold accents and electric-blue details.
        val ink = Color.rgb(244, 240, 229)
        val paper = Color.rgb(10, 16, 30)
        val white = Color.rgb(22, 32, 52)
        val line = Color.rgb(55, 68, 91)
        val blue = Color.rgb(74, 180, 255)
        val soft = Color.rgb(166, 180, 203)

        val root = FrameLayout(this).apply { setBackgroundColor(paper) }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(paper)
        }

        val masthead = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(13), dp(18), dp(12))
            setBackgroundColor(paper)
        }
        val menu = TextView(this).apply {
            text = "☰"
            textSize = 21f
            gravity = Gravity.CENTER
            setTextColor(ink)
            background = editorialShape(panel, gold, 13)
            contentDescription = "فتح لوحة التحكم"
            setOnClickListener { showDrawer() }
        }
        masthead.addView(menu, LinearLayout.LayoutParams(dp(44), dp(42)))
        masthead.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_flowpass_foreground)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "FLOWPASS premium logo"
        }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginStart = dp(9) })
        val identity = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(9), 0, 0, 0)
        }
        identity.addView(label("flowpass", 23f, gold, true).apply {
            letterSpacing = -0.045f
        })
        identity.addView(label("BROWSER  /  STUDIO EDITION", 8f, soft, true).apply {
            letterSpacing = 0.13f
        })
        masthead.addView(identity, LinearLayout.LayoutParams(0, -2, 1f))
        masthead.addView(label("01", 10f, gold, true).apply {
            gravity = Gravity.CENTER
            background = editorialShape(Color.rgb(39, 43, 54), null, 9)
            setPadding(dp(11), dp(7), dp(11), dp(7))
        })
        body.addView(masthead)

        val addressRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(3), dp(18), dp(13))
            setBackgroundColor(paper)
        }
        val address = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = editorialShape(white, gold, 15)
            setPadding(dp(13), 0, dp(7), 0)
        }
        address.addView(label("↗", 16f, blue, true).apply {
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(dp(25), dp(46)))
        addressInput = EditText(this).apply {
            hint = "ابحث أو أدخل عنوان الموقع"
            setHintTextColor(soft)
            setTextColor(ink)
            textSize = 13f
            isSingleLine = true
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_GO
            setPadding(0, 0, 0, 0)
            background = null
            setOnEditorActionListener { _, _, _ ->
                navigateFromAddress()
                true
            }
        }
        address.addView(addressInput, LinearLayout.LayoutParams(0, dp(46), 1f))
        address.addView(TextView(this).apply {
            text = "➜"
            textSize = 19f
            gravity = Gravity.CENTER
            setTextColor(gold)
            setOnClickListener { navigateFromAddress() }
        }, LinearLayout.LayoutParams(dp(42), dp(42)))
        addressRow.addView(address, LinearLayout.LayoutParams(-1, dp(48)))
        body.addView(addressRow)

        val rule = View(this).apply { setBackgroundColor(line) }
        body.addView(rule, LinearLayout.LayoutParams(-1, dp(1)))

        // Premium home screen: native Android components, not a static mockup.
        homePanel = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(paper)
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val homeContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(24))
            setBackgroundColor(paper)
        }
        homeContent.addView(label("BROWSE  •  EXPLORE  •  MORE", 9f, blue, true).apply {
            letterSpacing = 0.18f
        })
        homeContent.addView(label("تصفّح بطريقتك", 27f, ink, true).apply {
            setPadding(0, dp(5), 0, dp(2))
        })
        homeContent.addView(label("مساحتك الخاصة للوصول السريع إلى الويب", 12f, soft, false).apply {
            setPadding(0, 0, 0, dp(18))
        })

        val shortcuts = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        fun shortcut(symbol: String, title: String, url: String) {
            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = editorialShape(panel, line, 15)
                setPadding(dp(5), dp(12), dp(5), dp(10))
                isClickable = true
                isFocusable = true
                setOnClickListener { openPage(url) }
                addView(TextView(this@MainActivity).apply {
                    text = symbol
                    textSize = 24f
                    gravity = Gravity.CENTER
                    setTextColor(if (title == "Google") Color.WHITE else gold)
                }, LinearLayout.LayoutParams(-1, dp(34)))
                addView(label(title, 10f, ink, true).apply { gravity = Gravity.CENTER })
            }
            shortcuts.addView(tile, LinearLayout.LayoutParams(0, dp(78), 1f).apply {
                marginEnd = dp(7)
            })
        }
        shortcut("G", "Google", "https://www.google.com")
        shortcut("▶", "YouTube", "https://www.youtube.com")
        shortcut("f", "Facebook", "https://www.facebook.com")
        shortcut("𝕏", "X", "https://x.com")
        homeContent.addView(shortcuts)

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            background = premiumGradient(Color.rgb(15, 48, 82), Color.rgb(8, 17, 34))
            setPadding(dp(20), dp(18), dp(20), dp(18))
            elevation = dp(3).toFloat()
        }
        hero.addView(label("FLOWPASS  /  PREMIUM BROWSER", 9f, Color.rgb(112, 205, 255), true).apply {
            letterSpacing = 0.12f
        })
        hero.addView(label("أكثر من مجرد متصفح", 21f, Color.WHITE, true).apply {
            setPadding(0, dp(8), 0, dp(4))
        })
        hero.addView(label("تجربة أنيقة، وصول أسرع، وتحكّم كامل بأدوات التصفح.", 12f, Color.rgb(207, 220, 238), false))
        hero.addView(TextView(this).apply {
            text = "ابدأ التصفح  〈"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(10, 16, 30))
            background = editorialShape(gold, null, 12)
            setPadding(dp(16), dp(11), dp(16), dp(11))
            setOnClickListener { openPage(FLOW_URL) }
        }, LinearLayout.LayoutParams(-2, dp(42)).apply { topMargin = dp(16) })
        homeContent.addView(hero, LinearLayout.LayoutParams(-1, dp(190)).apply {
            topMargin = dp(18)
        })

        homeContent.addView(label("أدوات سريعة", 17f, ink, true).apply {
            setPadding(0, dp(22), 0, dp(11))
        })
        val toolsRow1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val toolsRow2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
        }
        fun toolTile(parent: LinearLayout, symbol: String, title: String, subtitle: String, action: () -> Unit) {
            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = editorialShape(white, line, 14)
                setPadding(dp(13), dp(12), dp(9), dp(11))
                isClickable = true
                isFocusable = true
                setOnClickListener { action() }
                addView(label(symbol, 21f, gold, true))
                addView(label(title, 12f, ink, true).apply { setPadding(0, dp(6), 0, dp(2)) })
                addView(label(subtitle, 9f, soft, false))
            }
            parent.addView(tile, LinearLayout.LayoutParams(0, dp(94), 1f).apply {
                marginEnd = dp(8)
            })
        }
        toolTile(toolsRow1, "☆", "المفضلة", "مواقعك المحفوظة") {
            Toast.makeText(this, "ستتوفر إدارة المفضلة في التحديث التالي", Toast.LENGTH_SHORT).show()
        }
        toolTile(toolsRow1, "◷", "السجل", "آخر الصفحات") {
            Toast.makeText(this, "سجل التصفح محفوظ داخل WebView", Toast.LENGTH_SHORT).show()
        }
        toolTile(toolsRow2, "↓", "التنزيلات", "ملفاتك") {
            Toast.makeText(this, "افتح قائمة التنزيلات من أدوات المتصفح", Toast.LENGTH_SHORT).show()
        }
        toolTile(toolsRow2, "⚙", "الإعدادات", "الخصوصية والمظهر") { showDrawer() }
        homeContent.addView(toolsRow1)
        homeContent.addView(toolsRow2)

        homeContent.addView(label("مساحة التصفح", 17f, ink, true).apply {
            setPadding(0, dp(22), 0, dp(10))
        })
        homeContent.addView(label(
            "وضع سطح المكتب ومحرك FlowPass يعملان من القائمة الجانبية. تبقى بيانات الجلسة وملفات الارتباط داخل WebView على جهازك.",
            11f, soft, false
        ).apply {
            background = editorialShape(Color.rgb(17, 27, 45), line, 13)
            setPadding(dp(14), dp(13), dp(14), dp(13))
        })
        homePanel.addView(homeContent)
        body.addView(homePanel, LinearLayout.LayoutParams(-1, 0, 1f))

        web = WebView(this)
        web.visibility = View.GONE
        web.addJavascriptInterface(EmailBridge(), "FlowPassAccount")
        body.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))

        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(8), dp(10), dp(9))
            setBackgroundColor(white)
            elevation = dp(8).toFloat()
        }
        fun dockAction(symbol: String, caption: String, enabled: Boolean = true, action: () -> Unit): LinearLayout {
            return LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                alpha = if (enabled) 1f else 0.35f
                isClickable = enabled
                isFocusable = enabled
                setOnClickListener { if (enabled) action() }
                addView(TextView(this@MainActivity).apply {
                    text = symbol
                    textSize = 20f
                    gravity = Gravity.CENTER
                    setTextColor(if (caption == "الرئيسية") gold else ink)
                }, LinearLayout.LayoutParams(-1, dp(25)))
                addView(TextView(this@MainActivity).apply {
                    text = caption
                    textSize = 9f
                    gravity = Gravity.CENTER
                    setTextColor(if (caption == "الرئيسية") gold else soft)
                    typeface = Typeface.DEFAULT_BOLD
                })
            }
        }
        footer.addView(dockAction("⌂", "الرئيسية") { showHome() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        footer.addView(dockAction("‹", "رجوع", true) { if (web.canGoBack()) web.goBack() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        footer.addView(dockAction("›", "تقدم", true) { if (web.canGoForward()) web.goForward() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        footer.addView(dockAction("▢", "النوافذ") { showDrawer() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        body.addView(View(this).apply { setBackgroundColor(line) }, LinearLayout.LayoutParams(-1, dp(1)))
        body.addView(footer)

        root.addView(body, FrameLayout.LayoutParams(-1, -1))
        shade = View(this).apply {
            setBackgroundColor(0x770D121A)
            visibility = View.GONE
            setOnClickListener { hideDrawer() }
        }
        root.addView(shade, FrameLayout.LayoutParams(-1, -1))

        drawer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(14, 22, 38))
            setPadding(dp(22), dp(30), dp(22), dp(18))
            elevation = dp(20).toFloat()

            addView(ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.ic_flowpass_foreground)
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = "FLOWPASS premium logo"
            }, LinearLayout.LayoutParams(dp(64), dp(64)).apply { gravity = Gravity.CENTER_HORIZONTAL })
            addView(label("CONTROL ROOM", 9f, blue, true).apply { letterSpacing = 0.16f })
            addView(label("مساحة العمل", 26f, ink, true).apply {
                setPadding(0, dp(6), 0, dp(3))
            })
            addView(label("إعدادات المتصفح وتجربة FlowPass", 11f, soft, false).apply {
                setPadding(0, 0, 0, dp(18))
            })
            addView(View(this@MainActivity).apply { setBackgroundColor(line) },
                LinearLayout.LayoutParams(-1, dp(1)))

            addView(label("DISPLAY", 9f, blue, true).apply {
                letterSpacing = 0.14f
                setPadding(0, dp(22), 0, dp(7))
            })
            addView(label(
                if (desktopMode) "سطح المكتب قيد التشغيل" else "عرض الهاتف قيد التشغيل",
                15f, ink, true
            ).apply { setPadding(0, 0, 0, dp(7)) })
            desktopSwitch = Switch(this@MainActivity).apply {
                text = "تصفح بنسخة الكمبيوتر"
                setTextColor(ink)
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
                "لتسجيل الدخول بالبريد، أوقف هذا الخيار مؤقتاً. بعد اكتمال الدخول، أعد تشغيله للعودة إلى عرض الكمبيوتر.",
                11f, Color.rgb(166, 180, 203), false
            ).apply {
                setPadding(dp(12), dp(11), dp(12), dp(11))
                background = editorialShape(Color.rgb(31, 43, 63), null, 12)
            })

            addView(label("ENGINE", 9f, blue, true).apply {
                letterSpacing = 0.14f
                setPadding(0, dp(23), 0, dp(7))
            })
            engineSwitch = Switch(this@MainActivity).apply {
                text = "محرك FlowPass"
                setTextColor(ink)
                isChecked = engineEnabled
                setOnCheckedChangeListener { _, checked ->
                    engineEnabled = checked
                    prefs.edit().putBoolean("engine", checked).apply()
                    web.evaluateJavascript("window.__flowPassEnabled = $checked;", null)
                    if (checked) injectEngine()
                }
            }
            addView(engineSwitch)

            addView(label("PRIVACY", 9f, blue, true).apply {
                letterSpacing = 0.14f
                setPadding(0, dp(23), 0, dp(7))
            })
            addView(label(
                "يُحفظ البريد الذي تدخله على هذا الجهاز فقط لتسهيل تعبئته لاحقاً. لا تُحفظ كلمة المرور ولا تتم قراءة رسائل Gmail.",
                11f, soft, false
            ))

            addView(TextView(this@MainActivity).apply {
                text = "إعادة تحميل الصفحة     ↻"
                textSize = 13f
                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.rgb(10, 16, 30))
                background = editorialShape(gold, null, 12)
                setOnClickListener { hideDrawer(); web.reload() }
            }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(24) })

            addView(View(this@MainActivity).apply {
                setBackgroundColor(line)
            }, LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(18) })
            val signature = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                addView(label("FLOWPASS  /  STUDIO", 9f, blue, true))
                addView(label("Designed & developed by Alaa Abdulaziz", 9f, soft, false))
            }
            addView(signature, LinearLayout.LayoutParams(-1, 0, 1f))
        }

        root.addView(drawer, FrameLayout.LayoutParams(dp(316), -1, Gravity.START))
        drawer.visibility = View.GONE
        setContentView(root)
    }

    private fun actionBackAvailable(): Boolean = ::web.isInitialized && web.canGoBack()

    private fun showHome() {
        if (::homePanel.isInitialized) homePanel.visibility = View.VISIBLE
        if (::web.isInitialized) web.visibility = View.GONE
        if (::addressInput.isInitialized) addressInput.setText("")
        hideDrawer()
    }

    private fun openPage(url: String) {
        val target = if (url.startsWith("http://") || url.startsWith("https://")) url
            else if (url.contains(".") && !url.contains(" ")) "https://$url"
            else "https://www.google.com/search?q=" + Uri.encode(url)
        homePanel.visibility = View.GONE
        web.visibility = View.VISIBLE
        addressInput.setText(target)
        web.loadUrl(target)
    }

    private fun navigateFromAddress() {
        val value = addressInput.text?.toString()?.trim().orEmpty()
        if (value.isBlank()) return
        openPage(value)
        try {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(addressInput.windowToken, 0)
        } catch (_: Exception) {}
    }

    private fun editorialShape(fill: Int, stroke: Int?, radius: Int) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radius).toFloat()
        if (stroke != null) setStroke(dp(1), stroke)
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
        content.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_flowpass_foreground)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "FLOWPASS premium logo"
        }, LinearLayout.LayoutParams(dp(82), dp(82)).apply { gravity = Gravity.CENTER })
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
        } else if (::web.isInitialized && web.visibility == View.VISIBLE && web.canGoBack()) {
            web.goBack()
        } else if (::web.isInitialized && web.visibility == View.VISIBLE) {
            showHome()
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
