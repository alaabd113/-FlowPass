package com.flowpass.mobile

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.app.DownloadManager
import android.content.Intent
import android.content.ContentValues
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.*
import org.json.JSONObject
import kotlin.math.cos
import kotlin.math.sin
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

class MainActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var homePanel: ScrollView
    private lateinit var addressInput: EditText
    private lateinit var drawer: LinearLayout
    private lateinit var shade: View
    private lateinit var engineSwitch: Switch
    private lateinit var desktopSwitch: Switch
    private lateinit var displayModeLabel: TextView
    private lateinit var progressBar: ProgressBar

    // Upgraded Studio UI Telemetry & State Views
    private lateinit var statusPillText: TextView
    private lateinit var statusDotView: View
    private lateinit var mastheadModeBadge: TextView
    private lateinit var heroEngineStateText: TextView
    private lateinit var heroPatchCountText: TextView
    private lateinit var heroViewportText: TextView
    private lateinit var studioModeDesktopCard: LinearLayout
    private lateinit var studioModeMobileCard: LinearLayout
    private lateinit var drawerEngineStatusBadge: TextView
    private lateinit var drawerDisplayStatusBadge: TextView
    private lateinit var drawerDiagnosticDetailText: TextView
    private lateinit var savedEmailValueText: TextView
    private lateinit var dockFlowItem: LinearLayout
    private lateinit var dockBackItem: LinearLayout
    private lateinit var dockStudioItem: LinearLayout
    private lateinit var dockForwardItem: LinearLayout
    private lateinit var dockControlItem: LinearLayout
    private lateinit var dockFlowLabel: TextView
    private lateinit var dockStudioLabel: TextView

    private val cascadeViews = mutableListOf<View>()
    private val drawerCascadeViews = mutableListOf<View>()

    private val prefs by lazy { getSharedPreferences("flowpass", MODE_PRIVATE) }
    private var engineEnabled = true
    private var desktopMode = true
    private var injectedUrl: String? = null
    private var diagnosticState: String = "armed"
    private var diagnosticAppliedCount: Int = 0
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var welcomeDialog: Dialog? = null
    private var emailVaultDialog: Dialog? = null
    private val blobStreams = ConcurrentHashMap<String, FileOutputStream>()
    private val blobFiles = ConcurrentHashMap<String, File>()
    private val blobNames = ConcurrentHashMap<String, Pair<String, String>>()

    private val audioManager by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }
    private var audioFocusRequest: AudioFocusRequest? = null
    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change == AudioManager.AUDIOFOCUS_LOSS) {
            // Do not force playback after another app has taken permanent audio focus.
        }
    }

    // Obsidian & 24K Champagne Gold Executive Studio Palette
    private val bgDeep = Color.parseColor("#050811")
    private val bgSurface = Color.parseColor("#0A101D")
    private val bgElevated = Color.parseColor("#10192D")
    private val bgCardTop = Color.parseColor("#16233D")
    private val bgCardBottom = Color.parseColor("#0B1222")
    private val glassBorder = Color.parseColor("#243656")
    private val gold = Color.parseColor("#E5C158")
    private val goldBright = Color.parseColor("#FDE68A")
    private val goldDeep = Color.parseColor("#A67C1E")
    private val cyanAccent = Color.parseColor("#38BDF8")
    private val emeraldAccent = Color.parseColor("#10B981")
    private val amberAccent = Color.parseColor("#F59E0B")
    private val textPrimary = Color.parseColor("#F8FAFC")
    private val textSecondary = Color.parseColor("#94A3B8")
    private val textMuted = Color.parseColor("#64748B")

    enum class Icon3D {
        GOOGLE, YOUTUBE, FACEBOOK, X_LOGO,
        STAR_FAV, CLOCK_HIST, DOWNLOAD_TRAY, GEAR_SETTINGS,
        MENU_BARS, SEARCH_ORB, GO_ARROW,
        NAV_HOME, NAV_BACK, NAV_FORWARD, NAV_TABS,
        TELEGRAM_3D, STUDIO_SPARK, SHIELD_VAULT, PULSE_RADAR, DESKTOP_DISPLAY
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        engineEnabled = prefs.getBoolean("engine", true)
        desktopMode = prefs.getBoolean("desktop_mode", true)
        diagnosticState = if (engineEnabled) "armed" else "disabled"

        CookieManager.getInstance().setAcceptCookie(true)
        buildUi()
        syncAllUiStates()

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
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                injectedUrl = null
                progressBar.visibility = View.VISIBLE
                progressBar.progress = 18
                if (url?.startsWith(FLOW) == true) injectEngine()
                if (isGoogleAccountsUrl(url)) attachEmailHelper()
                updateNavigationState()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progressBar.visibility = View.GONE
                if (desktopMode) enforceDesktopViewport()
                if (isGoogleAccountsUrl(url)) attachEmailHelper()
                installBlobDownloadHook()
                if (url?.startsWith(FLOW) == true) {
                    CookieManager.getInstance().flush()
                    readDiagnostic()
                }
                updateNavigationState()
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                if (uri.scheme == "intent") return true
                if (uri.scheme != "https" && uri.scheme != "http") return true
                val host = uri.host.orEmpty().lowercase()
                val internal = host == "flow.google.com" || host.endsWith(".flow.google.com") ||
                    host == "accounts.google.com" || host == "myaccount.google.com"
                if (internal) return false
                return try {
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                    true
                } catch (_: Exception) { true }
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

        web.addJavascriptInterface(BlobDownloadBridge(), "FlowPassBlobDownloads")

        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (!::progressBar.isInitialized) return
                if (newProgress in 1..99) {
                    if (progressBar.visibility != View.VISIBLE) progressBar.visibility = View.VISIBLE
                    ObjectAnimator.ofInt(progressBar, "progress", progressBar.progress, newProgress).apply {
                        duration = 180
                        interpolator = DecelerateInterpolator()
                        start()
                    }
                } else {
                    progressBar.visibility = View.GONE
                }
            }

            override fun onShowFileChooser(
                view: WebView?,
                callback: ValueCallback<Array<Uri>>?,
                params: FileChooserParams?
            ): Boolean {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = callback
                return try {
                    val picker = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                        putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
                        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    }
                    startActivityForResult(
                        Intent.createChooser(picker, "اختيار صورة أو فيديو"),
                        FILE_CHOOSER_REQUEST
                    )
                    true
                } catch (error: Exception) {
                    filePathCallback?.onReceiveValue(null)
                    filePathCallback = null
                    android.util.Log.e("FlowPass", "File picker unavailable", error)
                    false
                }
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message?
            ): Boolean {
                val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                val popup = WebView(this@MainActivity).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            popupView: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
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
                    readDiagnostic()
                }
                return true
            }
        }

        // Route every WebView download through Android's system Download Manager.
        // Files are saved in the public Downloads folder and Android shows progress/completion.
        web.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            val downloadUrl = url ?: return@setDownloadListener
            try {
                val parsedDownloadUri = Uri.parse(downloadUrl)
                if (parsedDownloadUri.scheme.equals("blob", ignoreCase = true)) {
                    val blobFileName = URLUtil.guessFileName(downloadUrl, contentDisposition, mimeType)
                        .ifBlank { "flowpass_download" }
                    val js = "if (window.__flowPassSaveBlobUrl) { window.__flowPassSaveBlobUrl(" +
                        JSONObject.quote(downloadUrl) + "," + JSONObject.quote(blobFileName) + "," +
                        JSONObject.quote(mimeType ?: "application/octet-stream") + "); } else { console.error('[FlowPass] blob bridge not ready'); }"
                    web.evaluateJavascript(js, null)
                    Toast.makeText(this, "جارٍ تجهيز الملف للحفظ…", Toast.LENGTH_SHORT).show()
                    return@setDownloadListener
                }
                if (parsedDownloadUri.scheme != "https" && parsedDownloadUri.scheme != "http") {
                    Toast.makeText(this, "نوع رابط التنزيل غير مدعوم", Toast.LENGTH_LONG).show()
                    return@setDownloadListener
                }
                val request = DownloadManager.Request(parsedDownloadUri)
                val cookies = CookieManager.getInstance().getCookie(downloadUrl)
                if (!cookies.isNullOrBlank()) request.addRequestHeader("Cookie", cookies)
                if (!userAgent.isNullOrBlank()) request.addRequestHeader("User-Agent", userAgent)
                web.url?.let { request.addRequestHeader("Referer", it) }

                val fileName = URLUtil.guessFileName(downloadUrl, contentDisposition, mimeType)
                    .replace(Regex("[/:*?\"<>|]"), "_").replace(92.toChar(), '_')
                    .ifBlank { "flowpass_download" }

                request.setTitle(fileName)
                request.setDescription("جارٍ تنزيل الملف عبر FlowPass")
                request.setMimeType(mimeType ?: "application/octet-stream")
                request.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                request.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    fileName
                )
                request.setAllowedOverMetered(true)
                request.setAllowedOverRoaming(false)

                val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
                manager.enqueue(request)
                Toast.makeText(this, "بدأ التنزيل: $fileName", Toast.LENGTH_LONG).show()
            } catch (error: Exception) {
                android.util.Log.e("FlowPass", "Download failed to enqueue", error)
                Toast.makeText(this, "تعذّر بدء التنزيل: ${error.localizedMessage ?: "خطأ غير معروف"}", Toast.LENGTH_LONG).show()
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


    // Google Flow creates downloadable media as blob: URLs. DownloadManager cannot
    // consume those URLs, so JavaScript streams the Blob to this native bridge in chunks.
    private fun installBlobDownloadHook() {
        if (!::web.isInitialized) return
        val script = """
            (function() {
              if (window.__flowPassBlobHookInstalled) return;
              window.__flowPassBlobHookInstalled = true;
              const bridge = window.FlowPassBlobDownloads;
              if (!bridge) return;
              async function saveBlob(blob, filename) {
                const id = 'fp-' + Date.now() + '-' + Math.random().toString(36).slice(2);
                try {
                  bridge.beginBlobDownload(id, filename || 'flowpass_download', blob.type || 'application/octet-stream');
                  const buffer = await blob.arrayBuffer();
                  const bytes = new Uint8Array(buffer);
                  const chunkSize = 196608;
                  for (let offset = 0; offset < bytes.length; offset += chunkSize) {
                    const part = bytes.subarray(offset, Math.min(offset + chunkSize, bytes.length));
                    let binary = '';
                    for (let i = 0; i < part.length; i++) binary += String.fromCharCode(part[i]);
                    bridge.appendBlobDownload(id, btoa(binary));
                  }
                  bridge.finishBlobDownload(id);
                } catch (e) {
                  try { bridge.abortBlobDownload(id); } catch (_) {}
                  console.error('[FlowPass] Blob download failed', e);
                }
              }
              window.__flowPassSaveBlobUrl = async function(url, filename, mimeType) {
                try {
                  const response = await fetch(url);
                  if (!response.ok) throw new Error('HTTP ' + response.status);
                  const blob = await response.blob();
                  await saveBlob(blob, filename || 'flowpass_download');
                } catch (e) {
                  console.error('[FlowPass] Native blob download handoff failed', e);
                }
              };
              document.addEventListener('click', function(event) {
                const anchor = event.target && event.target.closest ? event.target.closest('a') : null;
                if (!anchor) return;
                const href = anchor.href || '';
                if (!href.startsWith('blob:')) return;
                event.preventDefault();
                event.stopImmediatePropagation();
                fetch(href).then(response => response.blob())
                  .then(blob => saveBlob(blob, anchor.download || 'flowpass_download'))
                  .catch(error => console.error('[FlowPass] Blob fetch failed', error));
              }, true);
            })();
        """.trimIndent()
        web.evaluateJavascript(script, null)
    }

    private inner class BlobDownloadBridge {
        @JavascriptInterface
        fun beginBlobDownload(id: String, filename: String, mimeType: String) {
            try {
                val safeId = id.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(100)
                if (safeId.isBlank()) return
                val safeName = filename.substringAfterLast('/')
                    .filterNot { it.code in intArrayOf(47, 58, 42, 63, 34, 60, 62, 124, 92) }.take(180)
                    .ifBlank { "flowpass_download" }
                val temp = File(cacheDir, "blob-$safeId.tmp")
                blobFiles[safeId] = temp
                blobNames[safeId] = safeName to mimeType.ifBlank { "application/octet-stream" }
                blobStreams[safeId] = FileOutputStream(temp)
            } catch (error: Exception) {
                android.util.Log.e("FlowPass", "Could not start blob download", error)
            }
        }

        @JavascriptInterface
        fun appendBlobDownload(id: String, base64Chunk: String) {
            try {
                val safeId = id.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(100)
                val stream = blobStreams[safeId] ?: return
                stream.write(Base64.decode(base64Chunk, Base64.DEFAULT))
            } catch (error: Exception) {
                android.util.Log.e("FlowPass", "Could not write blob chunk", error)
            }
        }

        @JavascriptInterface
        fun finishBlobDownload(id: String) {
            val safeId = id.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(100)
            try {
                blobStreams.remove(safeId)?.use { it.flush() }
                val temp = blobFiles.remove(safeId) ?: return
                val (name, mime) = blobNames.remove(safeId) ?: ("flowpass_download" to "application/octet-stream")
                val resolver = contentResolver
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, name)
                        put(MediaStore.Downloads.MIME_TYPE, mime)
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: throw IllegalStateException("تعذّر إنشاء ملف التنزيل")
                    resolver.openOutputStream(uri)?.use { output -> temp.inputStream().use { it.copyTo(output) } }
                        ?: throw IllegalStateException("تعذّرت كتابة الملف")
                    val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                    resolver.update(uri, done, null, null)
                } else {
                    val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    if (!directory.exists()) directory.mkdirs()
                    File(directory, name).outputStream().use { output -> temp.inputStream().use { it.copyTo(output) } }
                }
                temp.delete()
                runOnUiThread { Toast.makeText(this@MainActivity, "تم حفظ الملف في مجلد التنزيلات: $name", Toast.LENGTH_LONG).show() }
            } catch (error: Exception) {
                android.util.Log.e("FlowPass", "Blob download save failed", error)
                blobFiles.remove(safeId)?.delete()
                blobNames.remove(safeId)
                runOnUiThread { Toast.makeText(this@MainActivity, "تعذّر حفظ الملف: ${error.localizedMessage}", Toast.LENGTH_LONG).show() }
            }
        }

        @JavascriptInterface
        fun abortBlobDownload(id: String) {
            val safeId = id.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(100)
            try { blobStreams.remove(safeId)?.close() } catch (_: Exception) {}
            blobFiles.remove(safeId)?.delete()
            blobNames.remove(safeId)
        }
    }

    private fun applyBrowserMode() {
        val nativeUa = WebSettings.getDefaultUserAgent(this)
        val chromeVersion = Regex("Chrome/([0-9]+)").find(nativeUa)?.groupValues?.get(1) ?: "120"
        web.settings.apply {
            if (desktopMode) {
                userAgentString =
                    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chromeVersion.0.0.0 Safari/537.36"
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
        syncAllUiStates()
    }

    private fun setDesktopModeState(enabled: Boolean, reloadWeb: Boolean = true) {
        desktopMode = enabled
        prefs.edit().putBoolean("desktop_mode", enabled).apply()
        if (::desktopSwitch.isInitialized && desktopSwitch.isChecked != enabled) {
            desktopSwitch.isChecked = enabled
        }
        applyBrowserMode()
        if (reloadWeb && ::web.isInitialized) {
            web.reload()
        }
    }

    private fun setEngineState(enabled: Boolean) {
        engineEnabled = enabled
        prefs.edit().putBoolean("engine", enabled).apply()
        if (::engineSwitch.isInitialized && engineSwitch.isChecked != enabled) {
            engineSwitch.isChecked = enabled
        }
        diagnosticState = if (enabled) {
            if (diagnosticAppliedCount > 0) "applied" else "armed"
        } else {
            "disabled"
        }
        if (::web.isInitialized) {
            web.evaluateJavascript("window.__flowPassEnabled = $enabled;", null)
            if (enabled) injectEngine()
        }
        syncAllUiStates()
    }

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
        cascadeViews.clear()
        drawerCascadeViews.clear()

        val root = FrameLayout(this).apply { setBackgroundColor(bgDeep) }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgDeep)
        }

        // 1. EXECUTIVE STUDIO MASTHEAD WITH 3D MENU, PULSING EMBLEM & LIVE TELEMETRY PILL
        val masthead = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(10))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#0C1324"), bgDeep)
            )
        }

        val menuBtn = FrameLayout(this).apply {
            background = glassCardDrawable(bgCardTop, bgCardBottom, Color.argb(135, 229, 193, 88), 14)
            elevation = dp(6).toFloat()
            contentDescription = "فتح غرفة التحكم"
            attachSpringTouch()
            setOnClickListener { showDrawer() }
            addView(Icon3DView(Icon3D.MENU_BARS), FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
        }
        masthead.addView(menuBtn, LinearLayout.LayoutParams(dp(44), dp(44)))

        val logoContainer = FrameLayout(this).apply {
            isClickable = true
            attachSpringTouch(0.92f)
            setOnClickListener { toggleStudioHub() }
            addView(PulsingHaloView(gold), FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER))
            addView(ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.ic_flowpass_foreground)
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = "FLOWPASS Studio Logo"
            }, FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER))
        }
        masthead.addView(logoContainer, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginStart = dp(8) })

        val identity = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), 0, dp(6), 0)
            isClickable = true
            setOnClickListener { toggleStudioHub() }
        }
        identity.addView(label("FLOWPASS", 17.5f, goldBright, true).apply {
            letterSpacing = 0.03f
            isSingleLine = true
        })
        identity.addView(label("STUDIO PRO 1.5", 8f, textSecondary, true).apply {
            letterSpacing = 0.11f
            isSingleLine = true
        })
        masthead.addView(identity, LinearLayout.LayoutParams(0, -2, 1f))

        // Live Telemetry & Engine Status Pill in Masthead
        val statusPill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = glassCardDrawable(
                Color.parseColor("#15223B"),
                Color.parseColor("#0B1324"),
                Color.argb(120, 229, 193, 88),
                18
            )
            setPadding(dp(10), dp(6), dp(10), dp(6))
            elevation = dp(4).toFloat()
            isClickable = true
            isFocusable = true
            attachSpringTouch(0.93f)
            setOnClickListener { toggleStudioHub() }
        }
        statusDotView = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(emeraldAccent)
            }
        }
        statusPill.addView(statusDotView, LinearLayout.LayoutParams(dp(7), dp(7)).apply { marginEnd = dp(6) })
        statusPillText = label("ACTIVE", 9f, goldBright, true).apply {
            letterSpacing = 0.04f
            isSingleLine = true
        }
        statusPill.addView(statusPillText)
        masthead.addView(statusPill, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(6) })

        // Quick Viewport Mode Switcher Badge in Masthead
        mastheadModeBadge = label("980P", 9f, cyanAccent, true).apply {
            isSingleLine = true
            gravity = Gravity.CENTER
            background = glassCardDrawable(
                Color.parseColor("#12243D"),
                Color.parseColor("#0A1426"),
                Color.argb(115, 56, 189, 248),
                12
            )
            setPadding(dp(10), dp(7), dp(10), dp(7))
            elevation = dp(4).toFloat()
            isClickable = true
            isFocusable = true
            attachSpringTouch(0.91f)
            setOnClickListener {
                setDesktopModeState(!desktopMode, reloadWeb = true)
                val modeMsg = if (desktopMode) {
                    "تم تفعيل وضع الاستوديو (كمبيوتر 980p)"
                } else {
                    "تم تفعيل وضع الهاتف لتسجيل الدخول السريع"
                }
                Toast.makeText(this@MainActivity, modeMsg, Toast.LENGTH_SHORT).show()
            }
        }
        masthead.addView(mastheadModeBadge)
        body.addView(masthead)

        // 2. HIDDEN OMNIBOX (Preserved for internal compatibility in Flow-only mode)
        val addressRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(4), dp(18), dp(14))
            setBackgroundColor(bgDeep)
            visibility = View.GONE
        }
        addressInput = EditText(this).apply {
            hint = "ابحث أو أدخل عنوان الموقع"
            setHintTextColor(textSecondary)
            setTextColor(textPrimary)
            textSize = 13.5f
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_GO
            background = null
            setOnEditorActionListener { _, _, _ ->
                navigateFromAddress()
                true
            }
        }
        addressRow.addView(addressInput, LinearLayout.LayoutParams(0, dp(44), 1f))
        body.addView(addressRow)

        // Luminous Gold Progress Bar & Hairline Specular Divider
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            visibility = View.GONE
            progressTintList = ColorStateList.valueOf(goldBright)
            progressBackgroundTintList = ColorStateList.valueOf(glassBorder)
        }
        body.addView(progressBar, LinearLayout.LayoutParams(-1, dp(2)))
        body.addView(View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor("#0D1526"), Color.argb(120, 229, 193, 88), Color.parseColor("#0D1526"))
            )
        }, LinearLayout.LayoutParams(-1, dp(1)))

        // 3. EXECUTIVE STUDIO HUB (Accessible via "الاستوديو" Dock or Top Status Pill)
        homePanel = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(bgDeep)
            overScrollMode = View.OVER_SCROLL_NEVER
            visibility = View.GONE // Opens directly into Google Flow at launch; toggleable anytime
        }
        val homeContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(28))
            setBackgroundColor(bgDeep)
        }

        val headerBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        headerBlock.addView(label("FLOWPASS STUDIO  •  COMMAND CENTER", 9.5f, cyanAccent, true).apply {
            letterSpacing = 0.16f
        })
        headerBlock.addView(label("مركز قيادة الاستوديو", 26f, textPrimary, true).apply {
            setPadding(0, dp(4), 0, dp(2))
        })
        headerBlock.addView(label("تحكّم فوري بمحرك FlowPass، أبعاد سطح المكتب، وخزنة الدخول الذكي", 12f, textSecondary, false).apply {
            setPadding(0, 0, 0, dp(16))
        })
        homeContent.addView(headerBlock)
        cascadeViews.add(headerBlock)

        // 3A. ANIMATED AURORA HERO CARD WITH LIVE TELEMETRY METRICS
        val heroWrapper = AnimatedHeroContainer(this).apply {
            setPadding(dp(20), dp(20), dp(20), dp(20))
            elevation = dp(10).toFloat()
        }
        val heroInner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val heroTopBadgeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        heroTopBadgeRow.addView(label("FLOWPASS PRO ENGINE  •  v1.5", 9.5f, goldBright, true).apply {
            letterSpacing = 0.12f
        }, LinearLayout.LayoutParams(0, -2, 1f))
        heroTopBadgeRow.addView(label("flow.google.com", 9.5f, cyanAccent, true).apply {
            background = editorialShape(Color.argb(90, 10, 20, 40), Color.argb(100, 56, 189, 248), 10)
            setPadding(dp(9), dp(4), dp(9), dp(4))
        })
        heroInner.addView(heroTopBadgeRow)

        heroInner.addView(label("بيئة Google Flow الاحترافية بلا قيود", 21f, Color.WHITE, true).apply {
            setPadding(0, dp(10), 0, dp(4))
        })
        heroInner.addView(label(
            "حقن تلقائي فوري لطلبات batchexecute مع محاكاة شاشة سطح مكتب كاملة 980px وحفظ آمن للجلسة.",
            12f,
            Color.rgb(219, 234, 254),
            false
        ).apply {
            setLineSpacing(dp(2).toFloat(), 1f)
        })

        // 3 Live Telemetry Mini-Cards inside Hero
        val metricsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(16), 0, dp(4))
        }
        fun createMetricBox(title: String, initialValue: String, valueColor: Int, isLast: Boolean = false): Pair<LinearLayout, TextView> {
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = editorialShape(Color.argb(145, 8, 14, 28), Color.argb(90, 229, 193, 88), 13)
                setPadding(dp(10), dp(9), dp(10), dp(9))
            }
            val valView = label(initialValue, 12.5f, valueColor, true)
            val capView = label(title, 9.5f, textSecondary, false).apply {
                setPadding(0, dp(2), 0, 0)
            }
            box.addView(valView)
            box.addView(capView)
            metricsRow.addView(box, LinearLayout.LayoutParams(0, -2, 1f).apply {
                if (!isLast) marginEnd = dp(8)
            })
            return Pair(box, valView)
        }
        heroEngineStateText = createMetricBox("حالة المحرك", "نشط ومسلّح", emeraldAccent).second
        heroPatchCountText = createMetricBox("التفعيلات", "0 عملية", goldBright).second
        heroViewportText = createMetricBox("نمط العرض", "كمبيوتر 980p", cyanAccent, true).second
        heroInner.addView(metricsRow)

        val heroActionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(14), 0, 0)
        }
        val returnToFlowBtn = ShimmerGoldButton(this, "العودة إلى مساحة عمل Flow  〈", 13f, 14).apply {
            setPadding(dp(18), dp(11), dp(18), dp(11))
            elevation = dp(6).toFloat()
            attachSpringTouch()
            setOnClickListener { openFlowHome() }
        }
        heroActionsRow.addView(returnToFlowBtn, LinearLayout.LayoutParams(0, dp(46), 1f).apply {
            marginEnd = dp(8)
        })
        val quickReloadHeroBtn = label("تحديث ↻", 12f, goldBright, true).apply {
            gravity = Gravity.CENTER
            background = glassCardDrawable(bgCardTop, bgCardBottom, Color.argb(130, 229, 193, 88), 14)
            setPadding(dp(14), dp(11), dp(14), dp(11))
            isClickable = true
            isFocusable = true
            attachSpringTouch()
            setOnClickListener {
                openFlowHome()
                web.reload()
            }
        }
        heroActionsRow.addView(quickReloadHeroBtn, LinearLayout.LayoutParams(-2, dp(46)))
        heroInner.addView(heroActionsRow)

        heroWrapper.addView(heroInner, FrameLayout.LayoutParams(-1, -2))
        homeContent.addView(heroWrapper, LinearLayout.LayoutParams(-1, -2))
        cascadeViews.add(heroWrapper)

        // 3B. SMART SIGN-IN & VIEWPORT MODE SWITCHER
        val switcherHeading = label("التبديل الذكي للعرض وتسجيل الدخول", 16.5f, textPrimary, true).apply {
            setPadding(0, dp(22), 0, dp(10))
        }
        homeContent.addView(switcherHeading)
        cascadeViews.add(switcherHeading)

        val modeCardsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        studioModeDesktopCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glassCardDrawable(bgCardTop, bgCardBottom, gold, 18)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            elevation = dp(5).toFloat()
            isClickable = true
            isFocusable = true
            attachSpringTouch(0.94f)
            setOnClickListener {
                setDesktopModeState(true, reloadWeb = true)
                openFlowHome()
            }
            val topRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            topRow.addView(Icon3DView(Icon3D.DESKTOP_DISPLAY), LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(8) })
            topRow.addView(label("وضع الاستوديو", 13.5f, goldBright, true))
            addView(topRow)
            addView(label("واجهة سطح المكتب الكاملة (980px) لتشغيل جميع أدوات التوليد.", 10.5f, textSecondary, false).apply {
                setPadding(0, dp(8), 0, 0)
                setLineSpacing(dp(2).toFloat(), 1f)
            })
        }
        studioModeMobileCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glassCardDrawable(bgCardTop, bgCardBottom, glassBorder, 18)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            elevation = dp(5).toFloat()
            isClickable = true
            isFocusable = true
            attachSpringTouch(0.94f)
            setOnClickListener {
                setDesktopModeState(false, reloadWeb = true)
                openFlowHome()
            }
            val topRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            topRow.addView(Icon3DView(Icon3D.SHIELD_VAULT), LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(8) })
            topRow.addView(label("وضع الدخول الآمن", 13.5f, cyanAccent, true))
            addView(topRow)
            addView(label("واجهة الهاتف لتسجيل الدخول بحساب Google بسلاسة دون حظر.", 10.5f, textSecondary, false).apply {
                setPadding(0, dp(8), 0, 0)
                setLineSpacing(dp(2).toFloat(), 1f)
            })
        }
        modeCardsRow.addView(studioModeDesktopCard, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(10) })
        modeCardsRow.addView(studioModeMobileCard, LinearLayout.LayoutParams(0, -2, 1f))
        homeContent.addView(modeCardsRow)
        cascadeViews.add(modeCardsRow)

        // 3C. 3D STUDIO BENTO GRID (خزنة البريد، رادار المحرك، تثبيت الجلسة، غرفة التحكم)
        val toolsHeading = label("أدوات الاستوديو الاحترافية", 16.5f, textPrimary, true).apply {
            setPadding(0, dp(22), 0, dp(10))
        }
        homeContent.addView(toolsHeading)
        cascadeViews.add(toolsHeading)

        val toolsRow1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val toolsRow2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, 0)
        }
        fun toolTile3D(
            parent: LinearLayout,
            iconType: Icon3D,
            title: String,
            subtitle: String,
            borderTint: Int,
            isLastInRow: Boolean = false,
            action: () -> Unit
        ) {
            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = glassCardDrawable(bgCardTop, bgCardBottom, borderTint, 18)
                setPadding(dp(13), dp(13), dp(13), dp(13))
                elevation = dp(5).toFloat()
                isClickable = true
                isFocusable = true
                attachSpringTouch(0.93f)
                setOnClickListener { action() }

                addView(
                    Icon3DView(iconType),
                    LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(10) }
                )

                val textCol = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                }
                textCol.addView(label(title, 13f, textPrimary, true))
                textCol.addView(label(subtitle, 10f, textSecondary, false).apply {
                    setPadding(0, dp(2), 0, 0)
                })
                addView(textCol, LinearLayout.LayoutParams(0, -2, 1f))
            }
            parent.addView(tile, LinearLayout.LayoutParams(0, dp(82), 1f).apply {
                if (!isLastInRow) marginEnd = dp(10)
            })
            cascadeViews.add(tile)
        }
        toolTile3D(toolsRow1, Icon3D.STAR_FAV, "خزنة البريد", "التعبئة التلقائية", Color.argb(105, 229, 193, 88), false) {
            showEmailVaultDialog()
        }
        toolTile3D(toolsRow1, Icon3D.PULSE_RADAR, "رادار المحرك", "فحص وتنشيط الحقن", Color.argb(105, 56, 189, 248), true) {
            if (!engineEnabled) setEngineState(true)
            injectedUrl = null
            injectEngine()
            readDiagnostic()
            Toast.makeText(this, "تم فحص وتنشيط محرك FlowPass بنجاح", Toast.LENGTH_SHORT).show()
        }
        toolTile3D(toolsRow2, Icon3D.DOWNLOAD_TRAY, "تثبيت الجلسة", "حفظ الكوكيز فوراً", Color.argb(105, 16, 185, 129), false) {
            CookieManager.getInstance().flush()
            Toast.makeText(this, "تم تثبيت وحفظ بيانات الجلسة وملفات الارتباط", Toast.LENGTH_SHORT).show()
        }
        toolTile3D(toolsRow2, Icon3D.GEAR_SETTINGS, "غرفة التحكم", "الإعدادات المتقدمة", Color.argb(105, 168, 85, 247), true) {
            showDrawer()
        }
        homeContent.addView(toolsRow1)
        homeContent.addView(toolsRow2)

        val spaceNoteCard = label(
            "ملاحظة هندسية: يعمل محرك FlowPass محلياً داخل صفحة flow.google.com دون تخزين كلمات مرور أو الوصول لرسائل البريد، مع حفظ الكوكيز تلقائياً عند الخروج.",
            11f,
            textSecondary,
            false
        ).apply {
            background = glassCardDrawable(bgCardTop, bgCardBottom, glassBorder, 16)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setLineSpacing(dp(2).toFloat(), 1f)
            elevation = dp(3).toFloat()
        }
        homeContent.addView(spaceNoteCard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
        cascadeViews.add(spaceNoteCard)

        homePanel.addView(homeContent)
        body.addView(homePanel, LinearLayout.LayoutParams(-1, 0, 1f))

        // 4. WEBVIEW WORKSPACE
        web = WebView(this)
        web.visibility = View.VISIBLE
        web.addJavascriptInterface(EmailBridge(), "FlowPassAccount")
        body.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))

        // 5. 5-ACTION 3D BOTTOM STUDIO DOCK (Flow، رجوع، الاستوديو، تقدم، التحكم)
        body.addView(View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor("#0D1526"), Color.argb(110, 229, 193, 88), Color.parseColor("#0D1526"))
            )
        }, LinearLayout.LayoutParams(-1, dp(1)))

        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(6), dp(8), dp(8))
            setBackgroundColor(bgSurface)
            elevation = dp(16).toFloat()
        }
        fun dockAction3D(
            iconType: Icon3D,
            caption: String,
            isHighlighted: Boolean = false,
            action: () -> Unit
        ): Pair<LinearLayout, TextView> {
            lateinit var captionView: TextView
            val container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                background = glassCardDrawable(
                    if (isHighlighted) Color.parseColor("#17243D") else Color.TRANSPARENT,
                    if (isHighlighted) Color.parseColor("#0D1526") else Color.TRANSPARENT,
                    if (isHighlighted) Color.argb(95, 229, 193, 88) else null,
                    14
                )
                attachSpringTouch(0.88f)
                setOnClickListener { action() }
                addView(
                    Icon3DView(iconType),
                    LinearLayout.LayoutParams(dp(24), dp(24)).apply { gravity = Gravity.CENTER_HORIZONTAL }
                )
                captionView = TextView(this@MainActivity).apply {
                    text = caption
                    textSize = 9.5f
                    gravity = Gravity.CENTER
                    setTextColor(if (isHighlighted) goldBright else textSecondary)
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding(0, dp(3), 0, 0)
                }
                addView(captionView)
            }
            return Pair(container, captionView)
        }

        val flowPair = dockAction3D(Icon3D.NAV_HOME, "Flow", true) { openFlowHome() }
        dockFlowItem = flowPair.first
        dockFlowLabel = flowPair.second

        dockBackItem = dockAction3D(Icon3D.NAV_BACK, "رجوع") {
            if (homePanel.visibility == View.VISIBLE) {
                openFlowHome()
            } else if (web.canGoBack()) {
                web.goBack()
            }
        }.first

        val studioPair = dockAction3D(Icon3D.STUDIO_SPARK, "الاستوديو") { toggleStudioHub() }
        dockStudioItem = studioPair.first
        dockStudioLabel = studioPair.second

        dockForwardItem = dockAction3D(Icon3D.NAV_FORWARD, "تقدم") {
            if (web.canGoForward()) web.goForward()
        }.first

        dockControlItem = dockAction3D(Icon3D.NAV_TABS, "التحكم") { showDrawer() }.first

        footer.addView(dockFlowItem, LinearLayout.LayoutParams(0, dp(54), 1f).apply { marginEnd = dp(3) })
        footer.addView(dockBackItem, LinearLayout.LayoutParams(0, dp(54), 1f).apply { marginEnd = dp(3) })
        footer.addView(dockStudioItem, LinearLayout.LayoutParams(0, dp(54), 1.15f).apply { marginEnd = dp(3) })
        footer.addView(dockForwardItem, LinearLayout.LayoutParams(0, dp(54), 1f).apply { marginEnd = dp(3) })
        footer.addView(dockControlItem, LinearLayout.LayoutParams(0, dp(54), 1f))
        body.addView(footer)

        root.addView(body, FrameLayout.LayoutParams(-1, -1))

        // 6. ANIMATED SHADE & SCROLLABLE LUXURY SIDE CONTROL DRAWER
        shade = View(this).apply {
            setBackgroundColor(0xBF02050C.toInt())
            visibility = View.GONE
            alpha = 0f
            setOnClickListener { hideDrawer() }
        }
        root.addView(shade, FrameLayout.LayoutParams(-1, -1))

        drawer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#0E172A"), Color.parseColor("#070B16"))
            )
            elevation = dp(28).toFloat()

            // Drawer ScrollView so content never clips on compact screens
            val drawerScroll = ScrollView(this@MainActivity).apply {
                isFillViewport = true
                overScrollMode = View.OVER_SCROLL_NEVER
            }
            val drawerContent = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20), dp(24), dp(20), dp(18))
            }

            // Top Row with Close Button & Emblem
            val drawerTopRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val drawerLogoWrap = FrameLayout(this@MainActivity).apply {
                addView(PulsingHaloView(gold), FrameLayout.LayoutParams(dp(58), dp(58), Gravity.CENTER))
                addView(ImageView(this@MainActivity).apply {
                    setImageResource(R.drawable.ic_flowpass_foreground)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    contentDescription = "FLOWPASS premium logo"
                }, FrameLayout.LayoutParams(dp(50), dp(50), Gravity.CENTER))
            }
            drawerTopRow.addView(drawerLogoWrap, LinearLayout.LayoutParams(dp(58), dp(58)))

            val drawerTitleCol = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(10), 0, dp(8), 0)
                addView(label("CONTROL ROOM", 9f, cyanAccent, true).apply { letterSpacing = 0.16f })
                addView(label("غرفة التحكم", 22f, textPrimary, true))
                addView(label("إعدادات الاستوديو والمحرك الذكي", 11f, textSecondary, false))
            }
            drawerTopRow.addView(drawerTitleCol, LinearLayout.LayoutParams(0, -2, 1f))

            val closeDrawerBtn = label("✕", 14f, goldBright, true).apply {
                gravity = Gravity.CENTER
                background = glassCardDrawable(bgCardTop, bgCardBottom, Color.argb(110, 229, 193, 88), 12)
                isClickable = true
                isFocusable = true
                attachSpringTouch(0.88f)
                setOnClickListener { hideDrawer() }
            }
            drawerTopRow.addView(closeDrawerBtn, LinearLayout.LayoutParams(dp(38), dp(38)))
            drawerContent.addView(drawerTopRow)
            drawerCascadeViews.add(drawerTopRow)

            drawerContent.addView(View(this@MainActivity).apply {
                background = GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(Color.TRANSPARENT, glassBorder, Color.TRANSPARENT)
                )
            }, LinearLayout.LayoutParams(-1, dp(1)).apply {
                topMargin = dp(14)
                bottomMargin = dp(14)
            })

            // CARD 1: DISPLAY & VIEWPORT CARD
            val displayCard = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                background = glassCardDrawable(bgCardTop, bgCardBottom, Color.argb(115, 56, 189, 248), 18)
                setPadding(dp(15), dp(14), dp(15), dp(14))
            }
            val displayHeaderRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            displayHeaderRow.addView(
                Icon3DView(Icon3D.DESKTOP_DISPLAY),
                LinearLayout.LayoutParams(dp(34), dp(34)).apply { marginEnd = dp(10) }
            )
            val displayTitleCol = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(label("DISPLAY VIEWPORT", 8.5f, cyanAccent, true).apply { letterSpacing = 0.12f })
                displayModeLabel = label(
                    if (desktopMode) "سطح المكتب قيد التشغيل" else "عرض الهاتف قيد التشغيل",
                    14f,
                    textPrimary,
                    true
                )
                addView(displayModeLabel)
            }
            displayHeaderRow.addView(displayTitleCol, LinearLayout.LayoutParams(0, -2, 1f))
            drawerDisplayStatusBadge = label("980P PRO", 9.5f, cyanAccent, true).apply {
                background = editorialShape(Color.parseColor("#0B192E"), Color.argb(110, 56, 189, 248), 10)
                setPadding(dp(8), dp(4), dp(8), dp(4))
            }
            displayHeaderRow.addView(drawerDisplayStatusBadge)
            displayCard.addView(displayHeaderRow)

            desktopSwitch = Switch(this@MainActivity).apply {
                text = "تصفح بنسخة الكمبيوتر الكاملة"
                textSize = 12.5f
                setTextColor(textPrimary)
                thumbTintList = ColorStateList.valueOf(goldBright)
                trackTintList = ColorStateList.valueOf(Color.argb(120, 229, 193, 88))
                isChecked = desktopMode
                setPadding(0, dp(10), 0, dp(6))
                setOnCheckedChangeListener { _, checked ->
                    if (desktopMode != checked) {
                        setDesktopModeState(checked, reloadWeb = true)
                    }
                }
            }
            displayCard.addView(desktopSwitch)

            displayCard.addView(label(
                "لتسجيل الدخول بالبريد، أوقف هذا الخيار مؤقتاً أو اضغط زر التبديل السريع بالأسفل، ثم أعد تفعيله بعد الدخول.",
                10.5f,
                textSecondary,
                false
            ).apply {
                setLineSpacing(dp(2).toFloat(), 1f)
                setPadding(dp(11), dp(9), dp(11), dp(9))
                background = editorialShape(Color.parseColor("#0D1628"), glassBorder, 12)
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })

            drawerContent.addView(displayCard)
            drawerCascadeViews.add(displayCard)

            // CARD 2: FLOWPASS ENGINE CARD
            val engineCard = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                background = glassCardDrawable(bgCardTop, bgCardBottom, Color.argb(120, 229, 193, 88), 18)
                setPadding(dp(15), dp(14), dp(15), dp(14))
            }
            val engineHeaderRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            engineHeaderRow.addView(
                Icon3DView(Icon3D.PULSE_RADAR),
                LinearLayout.LayoutParams(dp(34), dp(34)).apply { marginEnd = dp(10) }
            )
            val engineTitleCol = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(label("FLOWPASS ENGINE", 8.5f, goldBright, true).apply { letterSpacing = 0.12f })
                addView(label("محرك الحقن الذكي", 14f, textPrimary, true))
            }
            engineHeaderRow.addView(engineTitleCol, LinearLayout.LayoutParams(0, -2, 1f))
            drawerEngineStatusBadge = label("ARMED", 9.5f, emeraldAccent, true).apply {
                background = editorialShape(Color.parseColor("#09221E"), Color.argb(120, 16, 185, 129), 10)
                setPadding(dp(8), dp(4), dp(8), dp(4))
            }
            engineHeaderRow.addView(drawerEngineStatusBadge)
            engineCard.addView(engineHeaderRow)

            engineSwitch = Switch(this@MainActivity).apply {
                text = "تفعيل محرك FlowPass تلقائياً"
                textSize = 12.5f
                setTextColor(textPrimary)
                thumbTintList = ColorStateList.valueOf(goldBright)
                trackTintList = ColorStateList.valueOf(Color.argb(120, 229, 193, 88))
                isChecked = engineEnabled
                setPadding(0, dp(10), 0, dp(6))
                setOnCheckedChangeListener { _, checked ->
                    if (engineEnabled != checked) {
                        setEngineState(checked)
                    }
                }
            }
            engineCard.addView(engineSwitch)

            drawerDiagnosticDetailText = label(
                "حالة التشخيص: مسلّح وجاهز  •  التفعيلات: 0",
                10.5f,
                goldBright,
                false
            ).apply {
                setPadding(dp(11), dp(8), dp(11), dp(8))
                background = editorialShape(Color.parseColor("#0D1628"), glassBorder, 11)
            }
            engineCard.addView(drawerDiagnosticDetailText, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })

            drawerContent.addView(engineCard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
            drawerCascadeViews.add(engineCard)

            // CARD 3: SMART EMAIL VAULT & PRIVACY CARD
            val privacyCard = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                background = glassCardDrawable(bgCardTop, bgCardBottom, glassBorder, 18)
                setPadding(dp(15), dp(14), dp(15), dp(14))
            }
            val privacyHeaderRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            privacyHeaderRow.addView(
                Icon3DView(Icon3D.SHIELD_VAULT),
                LinearLayout.LayoutParams(dp(34), dp(34)).apply { marginEnd = dp(10) }
            )
            val privacyTitleCol = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(label("PRIVACY & AUTOFILL VAULT", 8.5f, cyanAccent, true).apply { letterSpacing = 0.12f })
                addView(label("البريد الذكي والخصوصية", 14f, textPrimary, true))
            }
            privacyHeaderRow.addView(privacyTitleCol, LinearLayout.LayoutParams(0, -2, 1f))
            val manageEmailBtn = label("إدارة البريد", 10f, goldBright, true).apply {
                background = glassCardDrawable(
                    Color.parseColor("#1E293B"),
                    Color.parseColor("#0F172A"),
                    Color.argb(120, 229, 193, 88),
                    10
                )
                setPadding(dp(10), dp(6), dp(10), dp(6))
                isClickable = true
                isFocusable = true
                attachSpringTouch(0.92f)
                setOnClickListener { showEmailVaultDialog() }
            }
            privacyHeaderRow.addView(manageEmailBtn)
            privacyCard.addView(privacyHeaderRow)

            savedEmailValueText = label("البريد المحفوظ: غير محدد بعد", 11f, goldBright, true).apply {
                setPadding(0, dp(10), 0, dp(4))
            }
            privacyCard.addView(savedEmailValueText)
            privacyCard.addView(label(
                "يُحفظ البريد الذي تدخله على هذا الجهاز فقط لتسهيل تعبئته في صفحة Google. لا تُحفظ كلمة المرور ولا تتم قراءة رسائل Gmail.",
                10.5f,
                textSecondary,
                false
            ).apply {
                setLineSpacing(dp(2).toFloat(), 1f)
            })

            drawerContent.addView(privacyCard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
            drawerCascadeViews.add(privacyCard)

            // Action Buttons inside Drawer
            val reloadBtn = ShimmerGoldButton(this@MainActivity, "إعادة تحميل الصفحة     ↻", 13f, 14).apply {
                attachSpringTouch()
                setOnClickListener {
                    hideDrawer()
                    openFlowHome()
                    web.reload()
                }
            }
            drawerContent.addView(reloadBtn, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(16) })
            drawerCascadeViews.add(reloadBtn)

            // Creator & Telegram Luxury Signature Card
            val signatureCard = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = glassCardDrawable(
                    Color.parseColor("#131F38"),
                    Color.parseColor("#091020"),
                    Color.argb(110, 229, 193, 88),
                    16
                )
                setPadding(dp(14), dp(12), dp(14), dp(12))
                isClickable = true
                isFocusable = true
                attachSpringTouch(0.95f)
                setOnClickListener {
                    try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TELEGRAM_URL))) }
                    catch (error: Exception) { android.util.Log.w("FlowPass", "Could not open Telegram", error) }
                }

                addView(
                    Icon3DView(Icon3D.TELEGRAM_3D),
                    LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(12) }
                )

                val sigCol = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(label("FLOWPASS  /  STUDIO", 9f, cyanAccent, true).apply { letterSpacing = 0.12f })
                    addView(label("Designed & developed by Alaa Abdulaziz", 10.5f, textPrimary, true).apply {
                        setPadding(0, dp(2), 0, dp(2))
                    })
                    addView(label("تابع قناة تيليجرام الرسمية  ↗", 10.5f, goldBright, true))
                }
                addView(sigCol, LinearLayout.LayoutParams(0, -2, 1f))
            }
            drawerContent.addView(signatureCard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
            drawerCascadeViews.add(signatureCard)

            drawerScroll.addView(drawerContent)
            addView(drawerScroll, LinearLayout.LayoutParams(-1, -1))
        }

        val drawerWidth = (resources.displayMetrics.widthPixels * 0.86f).toInt().coerceAtMost(dp(340))
        root.addView(drawer, FrameLayout.LayoutParams(drawerWidth, -1, Gravity.START))
        drawer.visibility = View.GONE
        setContentView(root)
    }

    private fun syncAllUiStates() {
        if (!::statusPillText.isInitialized) return

        // 1. Update Masthead Status Pill & Dot
        val dotColor = when {
            !engineEnabled -> amberAccent
            diagnosticState == "applied" || diagnosticAppliedCount > 0 -> emeraldAccent
            diagnosticState.contains("mismatch") -> Color.parseColor("#F43F5E")
            else -> cyanAccent
        }
        statusDotView.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(dotColor)
        }
        statusPillText.text = when {
            !engineEnabled -> "OFF"
            diagnosticAppliedCount > 0 -> "PATCHED • $diagnosticAppliedCount"
            else -> "ACTIVE"
        }
        statusPillText.setTextColor(if (engineEnabled) goldBright else textSecondary)

        // 2. Update Masthead Viewport Badge
        mastheadModeBadge.text = if (desktopMode) "980P" else "MOB"
        mastheadModeBadge.setTextColor(if (desktopMode) cyanAccent else goldBright)

        // 3. Update Studio Hub Telemetry Metrics & Mode Cards
        heroEngineStateText.text = when {
            !engineEnabled -> "متوقف مؤقتاً"
            diagnosticAppliedCount > 0 -> "مفعّل بنجاح"
            else -> "نشط ومسلّح"
        }
        heroEngineStateText.setTextColor(if (engineEnabled) emeraldAccent else amberAccent)
        heroPatchCountText.text = "$diagnosticAppliedCount عملية"
        heroViewportText.text = if (desktopMode) "كمبيوتر 980p" else "وضع الهاتف"

        studioModeDesktopCard.background = glassCardDrawable(
            bgCardTop,
            bgCardBottom,
            if (desktopMode) goldBright else glassBorder,
            18
        )
        studioModeMobileCard.background = glassCardDrawable(
            bgCardTop,
            bgCardBottom,
            if (!desktopMode) cyanAccent else glassBorder,
            18
        )

        // 4. Update Drawer Cards
        displayModeLabel.text = if (desktopMode) "سطح المكتب قيد التشغيل" else "عرض الهاتف قيد التشغيل"
        drawerDisplayStatusBadge.text = if (desktopMode) "980P PRO" else "LOGIN MODE"
        drawerDisplayStatusBadge.setTextColor(if (desktopMode) cyanAccent else goldBright)

        drawerEngineStatusBadge.text = when {
            !engineEnabled -> "OFF"
            diagnosticAppliedCount > 0 -> "APPLIED ($diagnosticAppliedCount)"
            else -> "ARMED"
        }
        drawerEngineStatusBadge.setTextColor(if (engineEnabled) emeraldAccent else amberAccent)

        val stateArabic = when {
            !engineEnabled -> "متوقف"
            diagnosticState == "applied" -> "تم تطبيق التعديلات بنجاح"
            diagnosticState.contains("mismatch") -> "تغير هيكل الاستجابة"
            else -> "مسلّح وجاهز للعمل"
        }
        drawerDiagnosticDetailText.text = "حالة التشخيص: $stateArabic  •  التفعيلات: $diagnosticAppliedCount"

        val savedEmail = prefs.getString("google_email", "")?.trim().orEmpty()
        savedEmailValueText.text = if (savedEmail.isNotEmpty()) {
            "البريد المحفوظ: $savedEmail"
        } else {
            "البريد المحفوظ: لم يتم حفظ بريد بعد"
        }

        updateNavigationState()
    }

    private fun updateNavigationState() {
        if (!::dockBackItem.isInitialized || !::web.isInitialized) return
        val isStudioVisible = homePanel.visibility == View.VISIBLE
        dockFlowItem.background = glassCardDrawable(
            if (!isStudioVisible) Color.parseColor("#17243D") else Color.TRANSPARENT,
            if (!isStudioVisible) Color.parseColor("#0D1526") else Color.TRANSPARENT,
            if (!isStudioVisible) Color.argb(110, 229, 193, 88) else null,
            14
        )
        dockFlowLabel.setTextColor(if (!isStudioVisible) goldBright else textSecondary)

        dockStudioItem.background = glassCardDrawable(
            if (isStudioVisible) Color.parseColor("#17243D") else Color.TRANSPARENT,
            if (isStudioVisible) Color.parseColor("#0D1526") else Color.TRANSPARENT,
            if (isStudioVisible) Color.argb(110, 56, 189, 248) else null,
            14
        )
        dockStudioLabel.setTextColor(if (isStudioVisible) cyanAccent else textSecondary)

        val canBack = isStudioVisible || web.canGoBack()
        val canFwd = !isStudioVisible && web.canGoForward()
        dockBackItem.alpha = if (canBack) 1.0f else 0.42f
        dockForwardItem.alpha = if (canFwd) 1.0f else 0.42f
    }

    private fun toggleStudioHub() {
        hideDrawer()
        if (homePanel.visibility == View.VISIBLE) {
            openFlowHome()
        } else {
            readDiagnostic()
            syncAllUiStates()
            web.visibility = View.GONE
            homePanel.visibility = View.VISIBLE
            playHomeCascadeAnimation()
            updateNavigationState()
        }
    }

    // CUSTOM 3D VOLUMETRIC ICON ENGINE (Renders true 3D squircles, specular highlights, and brand/studio vectors)
    private inner class Icon3DView(private val type: Icon3D) : View(this@MainActivity) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val rect = RectF()
        private val innerRect = RectF()
        private val path = Path()

        private fun draw3DSquircleBase(
            canvas: Canvas,
            w: Float,
            h: Float,
            topColor: Int,
            bottomColor: Int,
            lipColor: Int,
            glowColor: Int,
            cornerFactor: Float = 0.28f
        ) {
            val pad = w * 0.06f
            val r = w * cornerFactor

            // 1. Ambient Drop Glow underneath
            paint.style = Paint.Style.FILL
            paint.shader = RadialGradient(
                w * 0.5f, h * 0.62f, w * 0.48f,
                intArrayOf(glowColor, Color.TRANSPARENT),
                null,
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(w * 0.5f, h * 0.62f, w * 0.48f, paint)

            // 2. 3D Extruded Bottom Bevel Lip
            paint.shader = null
            paint.color = lipColor
            rect.set(pad, pad + h * 0.05f, w - pad, h - pad + h * 0.02f)
            canvas.drawRoundRect(rect, r, r, paint)

            // 3. Main 3D Volumetric Body
            rect.set(pad, pad, w - pad, h - pad * 1.35f)
            paint.shader = LinearGradient(
                0f, rect.top, 0f, rect.bottom,
                intArrayOf(topColor, bottomColor),
                null,
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(rect, r, r, paint)

            // 4. Top Glossy Specular Reflection Arc
            innerRect.set(rect.left + w * 0.04f, rect.top + h * 0.02f, rect.right - w * 0.04f, rect.top + rect.height() * 0.48f)
            paint.shader = LinearGradient(
                0f, innerRect.top, 0f, innerRect.bottom,
                intArrayOf(Color.argb(95, 255, 255, 255), Color.TRANSPARENT),
                null,
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(innerRect, r * 0.85f, r * 0.85f, paint)

            // 5. Crisp 1px Specular Rim Highlight
            strokePaint.shader = LinearGradient(
                0f, rect.top, 0f, rect.bottom,
                intArrayOf(Color.argb(140, 255, 255, 255), Color.argb(30, 255, 255, 255)),
                null,
                Shader.TileMode.CLAMP
            )
            strokePaint.strokeWidth = dp(1).toFloat()
            canvas.drawRoundRect(rect, r, r, strokePaint)
            strokePaint.shader = null
            paint.shader = null
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return
            path.reset()

            when (type) {
                Icon3D.GOOGLE -> {
                    draw3DSquircleBase(
                        canvas, w, h,
                        Color.parseColor("#FFFFFF"),
                        Color.parseColor("#E2E8F0"),
                        Color.parseColor("#94A3B8"),
                        Color.argb(75, 66, 133, 244)
                    )
                    val cx = w * 0.5f
                    val cy = h * 0.47f
                    val radius = w * 0.21f
                    strokePaint.strokeWidth = w * 0.095f
                    strokePaint.strokeCap = Paint.Cap.BUTT
                    rect.set(cx - radius, cy - radius, cx + radius, cy + radius)
                    strokePaint.color = Color.parseColor("#EA4335")
                    canvas.drawArc(rect, 215f, 100f, false, strokePaint)
                    strokePaint.color = Color.parseColor("#FBBC05")
                    canvas.drawArc(rect, 145f, 70f, false, strokePaint)
                    strokePaint.color = Color.parseColor("#34A853")
                    canvas.drawArc(rect, 45f, 100f, false, strokePaint)
                    strokePaint.color = Color.parseColor("#4285F4")
                    canvas.drawArc(rect, -12f, 57f, false, strokePaint)
                    paint.style = Paint.Style.FILL
                    paint.color = Color.parseColor("#4285F4")
                    canvas.drawRect(cx - w * 0.01f, cy - w * 0.042f, cx + radius + w * 0.04f, cy + w * 0.042f, paint)
                    strokePaint.strokeCap = Paint.Cap.ROUND
                }

                Icon3D.YOUTUBE -> {
                    draw3DSquircleBase(
                        canvas, w, h,
                        Color.parseColor("#FF2A4B"),
                        Color.parseColor("#C8001D"),
                        Color.parseColor("#780010"),
                        Color.argb(95, 255, 26, 64)
                    )
                    val cx = w * 0.5f
                    val cy = h * 0.47f
                    path.moveTo(cx - w * 0.08f, cy - h * 0.13f)
                    path.lineTo(cx + w * 0.14f, cy)
                    path.lineTo(cx - w * 0.08f, cy + h * 0.13f)
                    path.close()
                    paint.color = Color.WHITE
                    canvas.drawPath(path, paint)
                }

                Icon3D.FACEBOOK -> {
                    draw3DSquircleBase(
                        canvas, w, h,
                        Color.parseColor("#2B88FF"),
                        Color.parseColor("#0A52C6"),
                        Color.parseColor("#063382"),
                        Color.argb(95, 24, 119, 242)
                    )
                    strokePaint.color = Color.WHITE
                    strokePaint.strokeWidth = w * 0.105f
                    val fx = w * 0.53f
                    path.moveTo(fx + w * 0.09f, h * 0.25f)
                    path.quadTo(fx, h * 0.24f, fx, h * 0.35f)
                    path.lineTo(fx, h * 0.76f)
                    canvas.drawPath(path, strokePaint)
                    canvas.drawLine(fx - w * 0.11f, h * 0.47f, fx + w * 0.10f, h * 0.47f, strokePaint)
                }

                Icon3D.X_LOGO -> {
                    draw3DSquircleBase(
                        canvas, w, h,
                        Color.parseColor("#2B3445"),
                        Color.parseColor("#0B0F19"),
                        Color.parseColor("#04060A"),
                        Color.argb(85, 148, 163, 184)
                    )
                    strokePaint.color = Color.WHITE
                    strokePaint.strokeWidth = w * 0.075f
                    canvas.drawLine(w * 0.31f, h * 0.28f, w * 0.69f, h * 0.66f, strokePaint)
                    strokePaint.strokeWidth = w * 0.05f
                    canvas.drawLine(w * 0.69f, h * 0.28f, w * 0.31f, h * 0.66f, strokePaint)
                }

                Icon3D.STAR_FAV -> {
                    draw3DSquircleBase(
                        canvas, w, h,
                        Color.parseColor("#2E2514"),
                        Color.parseColor("#16120B"),
                        Color.parseColor("#0B0905"),
                        Color.argb(80, 229, 193, 88)
                    )
                    val cx = w * 0.5f
                    val cy = h * 0.47f
                    val outerR = w * 0.24f
                    val innerR = w * 0.11f
                    for (i in 0 until 10) {
                        val angle = Math.toRadians((i * 36 - 90).toDouble())
                        val rad = if (i % 2 == 0) outerR else innerR
                        val px = cx + (rad * cos(angle)).toFloat()
                        val py = cy + (rad * sin(angle)).toFloat()
                        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                    }
                    path.close()
                    paint.shader = LinearGradient(
                        0f, cy - outerR, 0f, cy + outerR,
                        intArrayOf(Color.parseColor("#FFF3B0"), Color.parseColor("#E5C158"), Color.parseColor("#B38728")),
                        null,
                        Shader.TileMode.CLAMP
                    )
                    canvas.drawPath(path, paint)
                    paint.shader = null
                }

                Icon3D.CLOCK_HIST, Icon3D.PULSE_RADAR -> {
                    draw3DSquircleBase(
                        canvas, w, h,
                        Color.parseColor("#122B42"),
                        Color.parseColor("#0A1828"),
                        Color.parseColor("#050D17"),
                        Color.argb(85, 56, 189, 248)
                    )
                    val cx = w * 0.5f
                    val cy = h * 0.47f
                    strokePaint.color = Color.argb(110, 56, 189, 248)
                    strokePaint.strokeWidth = w * 0.04f
                    canvas.drawCircle(cx, cy, w * 0.23f, strokePaint)
                    strokePaint.color = Color.parseColor("#7DD3FC")
                    strokePaint.strokeWidth = w * 0.06f
                    path.moveTo(w * 0.24f, cy)
                    path.lineTo(w * 0.39f, cy)
                    path.lineTo(w * 0.46f, cy - h * 0.16f)
                    path.lineTo(w * 0.55f, cy + h * 0.16f)
                    path.lineTo(w * 0.62f, cy)
                    path.lineTo(w * 0.76f, cy)
                    canvas.drawPath(path, strokePaint)
                }

                Icon3D.DOWNLOAD_TRAY -> {
                    draw3DSquircleBase(
                        canvas, w, h,
                        Color.parseColor("#0F2E26"),
                        Color.parseColor("#081C17"),
                        Color.parseColor("#04100D"),
                        Color.argb(80, 16, 185, 129)
                    )
                    val cx = w * 0.5f
                    val cy = h * 0.47f
                    strokePaint.color = Color.parseColor("#34D399")
                    strokePaint.strokeWidth = w * 0.065f
                    canvas.drawLine(cx, cy - h * 0.16f, cx, cy + h * 0.08f, strokePaint)
                    path.moveTo(cx - w * 0.12f, cy - h * 0.02f)
                    path.lineTo(cx, cy + h * 0.10f)
                    path.lineTo(cx + w * 0.12f, cy - h * 0.02f)
                    canvas.drawPath(path, strokePaint)
                    canvas.drawLine(cx - w * 0.16f, cy + h * 0.18f, cx + w * 0.16f, cy + h * 0.18f, strokePaint)
                }

                Icon3D.GEAR_SETTINGS -> {
                    draw3DSquircleBase(
                        canvas, w, h,
                        Color.parseColor("#2A1B3D"),
                        Color.parseColor("#160E24"),
                        Color.parseColor("#0B0714"),
                        Color.argb(80, 192, 132, 252)
                    )
                    val cx = w * 0.5f
                    val cy = h * 0.47f
                    strokePaint.color = Color.parseColor("#E9D5FF")
                    strokePaint.strokeWidth = w * 0.065f
                    for (i in 0 until 8) {
                        val a = Math.toRadians((i * 45).toDouble())
                        canvas.drawLine(
                            cx + (w * 0.13f * cos(a)).toFloat(),
                            cy + (w * 0.13f * sin(a)).toFloat(),
                            cx + (w * 0.22f * cos(a)).toFloat(),
                            cy + (w * 0.22f * sin(a)).toFloat(),
                            strokePaint
                        )
                    }
                    paint.color = Color.parseColor("#C084FC")
                    canvas.drawCircle(cx, cy, w * 0.15f, paint)
                    paint.color = Color.parseColor("#160E24")
                    canvas.drawCircle(cx, cy, w * 0.065f, paint)
                }

                Icon3D.SHIELD_VAULT -> {
                    draw3DSquircleBase(
                        canvas, w, h,
                        Color.parseColor("#1B2A4A"),
                        Color.parseColor("#0D1528"),
                        Color.parseColor("#060B16"),
                        Color.argb(85, 229, 193, 88)
                    )
                    val cx = w * 0.5f
                    val cy = h * 0.47f
                    path.moveTo(cx, cy - h * 0.22f)
                    path.lineTo(cx + w * 0.20f, cy - h * 0.14f)
                    path.lineTo(cx + w * 0.18f, cy + h * 0.06f)
                    path.quadTo(cx, cy + h * 0.24f, cx, cy + h * 0.24f)
                    path.quadTo(cx, cy + h * 0.24f, cx - w * 0.18f, cy + h * 0.06f)
                    path.lineTo(cx - w * 0.20f, cy - h * 0.14f)
                    path.close()
                    strokePaint.color = goldBright
                    strokePaint.strokeWidth = w * 0.06f
                    canvas.drawPath(path, strokePaint)
                    paint.color = cyanAccent
                    canvas.drawCircle(cx, cy, w * 0.055f, paint)
                }

                Icon3D.DESKTOP_DISPLAY -> {
                    draw3DSquircleBase(
                        canvas, w, h,
                        Color.parseColor("#142942"),
                        Color.parseColor("#0A1626"),
                        Color.parseColor("#050C16"),
                        Color.argb(85, 56, 189, 248)
                    )
                    strokePaint.color = Color.parseColor("#7DD3FC")
                    strokePaint.strokeWidth = w * 0.058f
                    rect.set(w * 0.24f, h * 0.26f, w * 0.76f, h * 0.58f)
                    canvas.drawRoundRect(rect, dp(3).toFloat(), dp(3).toFloat(), strokePaint)
                    canvas.drawLine(w * 0.50f, h * 0.58f, w * 0.50f, h * 0.70f, strokePaint)
                    canvas.drawLine(w * 0.36f, h * 0.70f, w * 0.64f, h * 0.70f, strokePaint)
                }

                Icon3D.STUDIO_SPARK -> {
                    val cx = w * 0.5f
                    val cy = h * 0.5f
                    val rOuter = w * 0.38f
                    val rInner = w * 0.13f
                    for (i in 0 until 8) {
                        val angle = Math.toRadians((i * 45).toDouble())
                        val rad = if (i % 2 == 0) rOuter else rInner
                        val px = cx + (rad * cos(angle)).toFloat()
                        val py = cy + (rad * sin(angle)).toFloat()
                        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                    }
                    path.close()
                    paint.shader = LinearGradient(
                        0f, 0f, w, h,
                        intArrayOf(goldBright, cyanAccent),
                        null,
                        Shader.TileMode.CLAMP
                    )
                    canvas.drawPath(path, paint)
                    paint.shader = null
                }

                Icon3D.MENU_BARS -> {
                    strokePaint.shader = LinearGradient(0f, 0f, w, h, goldBright, gold, Shader.TileMode.CLAMP)
                    strokePaint.strokeWidth = dp(2).toFloat()
                    canvas.drawLine(w * 0.18f, h * 0.28f, w * 0.82f, h * 0.28f, strokePaint)
                    canvas.drawLine(w * 0.28f, h * 0.50f, w * 0.82f, h * 0.50f, strokePaint)
                    canvas.drawLine(w * 0.18f, h * 0.72f, w * 0.66f, h * 0.72f, strokePaint)
                    strokePaint.shader = null
                }

                Icon3D.SEARCH_ORB -> {
                    paint.shader = RadialGradient(
                        w * 0.4f, h * 0.35f, w * 0.48f,
                        intArrayOf(Color.parseColor("#38BDF8"), Color.parseColor("#0369A1")),
                        null,
                        Shader.TileMode.CLAMP
                    )
                    canvas.drawCircle(w * 0.5f, h * 0.5f, w * 0.42f, paint)
                    paint.shader = null
                    strokePaint.color = Color.WHITE
                    strokePaint.strokeWidth = dp(2).toFloat()
                    canvas.drawCircle(w * 0.46f, h * 0.46f, w * 0.16f, strokePaint)
                    canvas.drawLine(w * 0.58f, h * 0.58f, w * 0.72f, h * 0.72f, strokePaint)
                }

                Icon3D.GO_ARROW -> {
                    paint.shader = LinearGradient(
                        0f, 0f, w, h,
                        intArrayOf(goldBright, gold, goldDeep),
                        null,
                        Shader.TileMode.CLAMP
                    )
                    canvas.drawCircle(w * 0.5f, h * 0.5f, w * 0.44f, paint)
                    paint.shader = null
                    strokePaint.color = bgDeep
                    strokePaint.strokeWidth = dp(2).toFloat()
                    canvas.drawLine(w * 0.68f, h * 0.5f, w * 0.32f, h * 0.5f, strokePaint)
                    path.moveTo(w * 0.48f, h * 0.33f)
                    path.lineTo(w * 0.31f, h * 0.50f)
                    path.lineTo(w * 0.48f, h * 0.67f)
                    canvas.drawPath(path, strokePaint)
                }

                Icon3D.NAV_HOME -> {
                    strokePaint.shader = LinearGradient(0f, 0f, w, h, goldBright, gold, Shader.TileMode.CLAMP)
                    strokePaint.strokeWidth = dp(2).toFloat()
                    path.moveTo(w * 0.16f, h * 0.48f)
                    path.lineTo(w * 0.50f, h * 0.18f)
                    path.lineTo(w * 0.84f, h * 0.48f)
                    path.moveTo(w * 0.26f, h * 0.44f)
                    path.lineTo(w * 0.26f, h * 0.82f)
                    path.lineTo(w * 0.74f, h * 0.82f)
                    path.lineTo(w * 0.74f, h * 0.44f)
                    canvas.drawPath(path, strokePaint)
                    strokePaint.shader = null
                }

                Icon3D.NAV_BACK -> {
                    strokePaint.color = textPrimary
                    strokePaint.strokeWidth = dp(2).toFloat()
                    path.moveTo(w * 0.38f, h * 0.24f)
                    path.lineTo(w * 0.64f, h * 0.50f)
                    path.lineTo(w * 0.38f, h * 0.76f)
                    canvas.drawPath(path, strokePaint)
                }

                Icon3D.NAV_FORWARD -> {
                    strokePaint.color = textPrimary
                    strokePaint.strokeWidth = dp(2).toFloat()
                    path.moveTo(w * 0.62f, h * 0.24f)
                    path.lineTo(w * 0.36f, h * 0.50f)
                    path.lineTo(w * 0.62f, h * 0.76f)
                    canvas.drawPath(path, strokePaint)
                }

                Icon3D.NAV_TABS -> {
                    strokePaint.color = textPrimary
                    strokePaint.strokeWidth = dp(2).toFloat()
                    rect.set(w * 0.22f, h * 0.24f, w * 0.74f, h * 0.76f)
                    canvas.drawRoundRect(rect, dp(4).toFloat(), dp(4).toFloat(), strokePaint)
                }

                Icon3D.TELEGRAM_3D -> {
                    draw3DSquircleBase(
                        canvas, w, h,
                        Color.parseColor("#38BDF8"),
                        Color.parseColor("#0284C7"),
                        Color.parseColor("#0369A1"),
                        Color.argb(90, 56, 189, 248)
                    )
                    path.moveTo(w * 0.72f, h * 0.28f)
                    path.lineTo(w * 0.24f, h * 0.47f)
                    path.lineTo(w * 0.43f, h * 0.55f)
                    path.lineTo(w * 0.58f, h * 0.70f)
                    path.close()
                    paint.color = Color.WHITE
                    canvas.drawPath(path, paint)
                }
            }
        }
    }

    private fun playHomeCascadeAnimation() {
        cascadeViews.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = dp(24).toFloat()
            view.scaleX = 0.96f
            view.scaleY = 0.96f
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay((index * 45).toLong())
                .setDuration(360)
                .setInterpolator(DecelerateInterpolator(1.6f))
                .start()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun View.attachSpringTouch(pressedScale: Float = 0.93f) {
        setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate()
                        .scaleX(pressedScale)
                        .scaleY(pressedScale)
                        .setDuration(110)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(250)
                        .setInterpolator(OvershootInterpolator(2.2f))
                        .start()
                }
            }
            false
        }
    }

    private fun showHome() = openFlowHome()

    private fun openFlowHome() {
        hideDrawer()
        homePanel.visibility = View.GONE
        if (web.visibility != View.VISIBLE) {
            web.alpha = 0f
            web.visibility = View.VISIBLE
            web.animate().alpha(1f).setDuration(220).start()
        }
        val currentUrl = web.url
        if (currentUrl == null || (!currentUrl.startsWith(FLOW) && !isGoogleAccountsUrl(currentUrl))) {
            web.loadUrl(FLOW_URL)
        }
        updateNavigationState()
    }

    private fun openPage(url: String) {
        val parsed = Uri.parse(url)
        val host = parsed.host.orEmpty().lowercase()
        val allowed = host == "flow.google.com" || host.endsWith(".flow.google.com") ||
            host == "accounts.google.com" || host == "myaccount.google.com"
        if (!allowed) {
            openFlowHome()
            return
        }
        homePanel.visibility = View.GONE
        if (web.visibility != View.VISIBLE) {
            web.alpha = 0f
            web.visibility = View.VISIBLE
            web.animate().alpha(1f).setDuration(250).start()
        }
        addressInput.setText(url)
        web.loadUrl(url)
        updateNavigationState()
    }

    private fun navigateFromAddress() {
        val value = addressInput.text?.toString()?.trim().orEmpty()
        if (value.isBlank()) return
        openPage(value)
        try {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(addressInput.windowToken, 0)
        } catch (_: Exception) {}
    }

    private fun editorialShape(fill: Int, stroke: Int?, radius: Int) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radius).toFloat()
        if (stroke != null) setStroke(dp(1), stroke)
    }

    private fun glassCardDrawable(topFill: Int, bottomFill: Int, strokeColor: Int?, radius: Int): RippleDrawable {
        val content = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(topFill, bottomFill)
        ).apply {
            cornerRadius = dp(radius).toFloat()
            if (strokeColor != null) setStroke(dp(1), strokeColor)
        }
        val mask = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dp(radius).toFloat()
        }
        return RippleDrawable(ColorStateList.valueOf(Color.argb(48, 229, 193, 88)), content, mask)
    }

    private fun showEmailVaultDialog() {
        if (isFinishing || emailVaultDialog?.isShowing == true) return
        val dialog = Dialog(this).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
        }
        emailVaultDialog = dialog

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(24), dp(22), dp(20))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#172542"), Color.parseColor("#090F1E"))
            ).apply {
                cornerRadius = dp(22).toFloat()
                setStroke(dp(1), Color.argb(145, 229, 193, 88))
            }
            elevation = dp(20).toFloat()
        }

        card.addView(label("SMART EMAIL VAULT", 9.5f, cyanAccent, true).apply { letterSpacing = 0.14f })
        card.addView(label("خزنة البريد الذكي للتعبئة التلقائية", 18.5f, textPrimary, true).apply {
            setPadding(0, dp(4), 0, dp(6))
        })
        card.addView(label(
            "أدخل بريد Google الخاص بك ليتم تعبئته تلقائياً عند فتح صفحة تسجيل الدخول accounts.google.com.",
            11.5f,
            textSecondary,
            false
        ).apply {
            setLineSpacing(dp(2).toFloat(), 1f)
            setPadding(0, 0, 0, dp(14))
        })

        val emailInput = EditText(this).apply {
            setText(prefs.getString("google_email", "") ?: "")
            hint = "example@gmail.com"
            setHintTextColor(textMuted)
            setTextColor(textPrimary)
            textSize = 14f
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = editorialShape(Color.parseColor("#0B1324"), Color.argb(120, 229, 193, 88), 13)
        }
        card.addView(emailInput, LinearLayout.LayoutParams(-1, -2))

        val actionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(16), 0, 0)
        }

        val saveBtn = ShimmerGoldButton(this, "حفظ البريد", 13f, 13).apply {
            attachSpringTouch()
            setOnClickListener {
                val candidate = emailInput.text?.toString()?.trim().orEmpty()
                if (candidate.isEmpty()) {
                    prefs.edit().remove("google_email").apply()
                    syncAllUiStates()
                    Toast.makeText(this@MainActivity, "تم مسح البريد المحفوظ", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                } else if (candidate.matches(Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"))) {
                    prefs.edit().putString("google_email", candidate).apply()
                    syncAllUiStates()
                    Toast.makeText(this@MainActivity, "تم حفظ البريد للتعبئة التلقائية", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                } else {
                    Toast.makeText(this@MainActivity, "يرجى إدخال بريد إلكتروني صحيح", Toast.LENGTH_SHORT).show()
                }
            }
        }
        actionsRow.addView(saveBtn, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(8) })

        val clearBtn = label("مسح", 12.5f, textSecondary, true).apply {
            gravity = Gravity.CENTER
            background = glassCardDrawable(bgCardTop, bgCardBottom, glassBorder, 13)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            isClickable = true
            isFocusable = true
            attachSpringTouch()
            setOnClickListener {
                prefs.edit().remove("google_email").apply()
                syncAllUiStates()
                Toast.makeText(this@MainActivity, "تم حذف البريد المحفوظ من الجهاز", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }
        actionsRow.addView(clearBtn, LinearLayout.LayoutParams(-2, dp(46)))
        card.addView(actionsRow)

        dialog.setContentView(card)
        dialog.setCancelable(true)
        dialog.setOnDismissListener { emailVaultDialog = null }
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.90f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun showWelcomeDialog() {
        if (isFinishing || welcomeDialog?.isShowing == true) return

        val dialog = Dialog(this).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
        }
        welcomeDialog = dialog

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(22), dp(26), dp(22), dp(22))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#182644"), Color.parseColor("#080D1A"))
            ).apply {
                cornerRadius = dp(24).toFloat()
                setStroke(dp(1), Color.argb(155, 229, 193, 88))
            }
            elevation = dp(22).toFloat()
        }

        val logoPulseBox = FrameLayout(this).apply {
            addView(PulsingHaloView(gold), FrameLayout.LayoutParams(dp(92), dp(92), Gravity.CENTER))
            addView(ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.ic_flowpass_foreground)
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = "FLOWPASS premium logo"
            }, FrameLayout.LayoutParams(dp(76), dp(76), Gravity.CENTER))
        }
        content.addView(logoPulseBox, LinearLayout.LayoutParams(dp(92), dp(92)).apply {
            gravity = Gravity.CENTER
        })

        content.addView(label("FLOWPASS STUDIO", 23f, goldBright, true).apply {
            gravity = Gravity.CENTER
            letterSpacing = 0.05f
        })
        content.addView(label(
            "تم تطوير التطبيق بواسطة علاء عبد العزيز",
            14.5f,
            Color.WHITE,
            true
        ).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(4))
        })
        content.addView(label(
            "يسعدنا انضمامك إلى قناتنا على تيليجرام لمتابعة التحديثات. اضغط للاشتراك ثم ارجع للتطبيق وسيكمل العمل تلقائياً.",
            12.5f,
            textSecondary,
            false
        ).apply {
            gravity = Gravity.CENTER
            setLineSpacing(dp(2).toFloat(), 1f)
            setPadding(0, dp(2), 0, dp(12))
        })
        content.addView(label("ملاحظة تسجيل الدخول المهمة", 12.5f, goldBright, true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, dp(6))
        })
        content.addView(label(
            "وضع سطح المكتب (980P) مفعّل تلقائياً. عند تسجيل الدخول بالبريد الإلكتروني، يمكنك التبديل بضغطة واحدة من شريط الاستوديو العلوي أو القائمة الجانبية إلى وضع الهاتف، ثم العودة لوضع الكمبيوتر.",
            11.5f,
            Color.rgb(236, 220, 176),
            false
        ).apply {
            gravity = Gravity.CENTER
            setLineSpacing(dp(2).toFloat(), 1f)
            setPadding(dp(14), dp(11), dp(14), dp(11))
            background = editorialShape(Color.parseColor("#121D34"), Color.argb(95, 229, 193, 88), 15)
        })

        val subscribeBtn = ShimmerGoldButton(this, "اشترك بقناة تيليجرام  ↗", 14f, 15).apply {
            attachSpringTouch()
            setOnClickListener {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TELEGRAM_URL)))
                } catch (error: Exception) {
                    android.util.Log.w("FlowPass", "Could not open Telegram channel", error)
                } finally {
                    dialog.dismiss()
                }
            }
        }
        content.addView(subscribeBtn, LinearLayout.LayoutParams(-1, dp(50)).apply {
            topMargin = dp(16)
        })

        val continueBtn = label("الدخول إلى مساحة العمل", 12.5f, textSecondary, true).apply {
            gravity = Gravity.CENTER
            background = glassCardDrawable(bgCardTop, bgCardBottom, glassBorder, 14)
            setPadding(0, dp(11), 0, dp(11))
            isClickable = true
            isFocusable = true
            attachSpringTouch(0.95f)
            setOnClickListener { dialog.dismiss() }
        }
        content.addView(continueBtn, LinearLayout.LayoutParams(-1, dp(44)).apply {
            topMargin = dp(8)
        })

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
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        content.scaleX = 0.84f
        content.scaleY = 0.84f
        content.alpha = 0f
        content.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(400)
            .setInterpolator(OvershootInterpolator(1.2f))
            .start()
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
            val email = value?.trim()?.take(254) ?: return
            if (!email.matches(Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"))) return
            runOnUiThread {
                if (!isGoogleAccountsUrl(web.url)) return@runOnUiThread
                prefs.edit().putString("google_email", email).apply()
                syncAllUiStates()
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
        if (!::web.isInitialized) return
        web.evaluateJavascript(
            "JSON.stringify(window.__flowLocalDiagnostic || {state:'waiting', applied:0})"
        ) { result ->
            android.util.Log.d("FlowPass", "Diagnostic: $result")
            try {
                val raw = result?.trim() ?: return@evaluateJavascript
                val unwrapped = if (raw.startsWith("\"") && raw.endsWith("\"")) {
                    org.json.JSONTokener(raw).nextValue().toString()
                } else {
                    raw
                }
                if (unwrapped.startsWith("{")) {
                    val obj = JSONObject(unwrapped)
                    diagnosticState = obj.optString("state", if (engineEnabled) "armed" else "disabled")
                    diagnosticAppliedCount = obj.optInt("applied", diagnosticAppliedCount)
                    syncAllUiStates()
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun showDrawer() {
        if (drawer.visibility == View.VISIBLE) return
        syncAllUiStates()
        shade.visibility = View.VISIBLE
        drawer.visibility = View.VISIBLE
        drawer.bringToFront()

        shade.alpha = 0f
        shade.animate().alpha(1f).setDuration(240).start()

        val offset = if (drawer.layoutDirection == View.LAYOUT_DIRECTION_RTL) dp(320).toFloat() else -dp(320).toFloat()
        drawer.translationX = offset
        drawer.animate()
            .translationX(0f)
            .setDuration(280)
            .setInterpolator(DecelerateInterpolator(1.7f))
            .start()

        drawerCascadeViews.forEachIndexed { idx, v ->
            v.alpha = 0f
            v.translationY = dp(18).toFloat()
            v.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay((55 + idx * 40).toLong())
                .setDuration(290)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    private fun hideDrawer() {
        if (!::drawer.isInitialized || drawer.visibility != View.VISIBLE) return
        shade.animate().alpha(0f).setDuration(200).withEndAction {
            shade.visibility = View.GONE
        }.start()
        val offset = if (drawer.layoutDirection == View.LAYOUT_DIRECTION_RTL) dp(320).toFloat() else -dp(320).toFloat()
        drawer.animate()
            .translationX(offset)
            .setDuration(220)
            .withEndAction {
                drawer.visibility = View.GONE
                drawer.translationX = 0f
            }
            .start()
    }

    private fun label(text: String, size: Float, color: Int, bold: Boolean) =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
        }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private inner class PulsingHaloView(private val haloColor: Int) : View(this@MainActivity) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private var phase = 0f
        private var animator: ValueAnimator? = null

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 2200
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener {
                    phase = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }

        override fun onDetachedFromWindow() {
            animator?.cancel()
            animator = null
            super.onDetachedFromWindow()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val maxR = (width.coerceAtMost(height) / 2f) * (0.82f + 0.18f * phase)
            if (maxR <= 0f) return
            paint.shader = RadialGradient(
                cx,
                cy,
                maxR,
                intArrayOf(
                    Color.argb((95 * (1f - 0.35f * phase)).toInt(), Color.red(haloColor), Color.green(haloColor), Color.blue(haloColor)),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0.2f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, maxR, paint)
        }
    }

    private inner class AnimatedHeroContainer(context: Activity) : FrameLayout(context) {
        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val orbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(1).toFloat()
            color = Color.argb(145, 229, 193, 88)
        }
        private val rectF = RectF()
        private val clipPath = Path()
        private var animProgress = 0f
        private var orbAnimator: ValueAnimator? = null

        init {
            setWillNotDraw(false)
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            orbAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 4200
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener {
                    animProgress = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }

        override fun onDetachedFromWindow() {
            orbAnimator?.cancel()
            orbAnimator = null
            super.onDetachedFromWindow()
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) {
                super.onDraw(canvas)
                return
            }
            val r = dp(22).toFloat()
            rectF.set(0f, 0f, w, h)
            clipPath.reset()
            clipPath.addRoundRect(rectF, r, r, Path.Direction.CW)

            val save = canvas.save()
            canvas.clipPath(clipPath)

            bgPaint.shader = LinearGradient(
                0f, 0f, w, h,
                intArrayOf(Color.parseColor("#163158"), Color.parseColor("#091224")),
                null,
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(rectF, bgPaint)

            val orb1X = w * (0.20f + 0.25f * animProgress)
            val orb1Y = h * (0.25f + 0.15f * (1f - animProgress))
            orbPaint.shader = RadialGradient(
                orb1X, orb1Y, w * 0.55f,
                intArrayOf(Color.argb(78, 56, 189, 248), Color.TRANSPARENT),
                null,
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(orb1X, orb1Y, w * 0.55f, orbPaint)

            val orb2X = w * (0.82f - 0.22f * animProgress)
            val orb2Y = h * (0.75f - 0.20f * animProgress)
            orbPaint.shader = RadialGradient(
                orb2X, orb2Y, w * 0.48f,
                intArrayOf(Color.argb(68, 229, 193, 88), Color.TRANSPARENT),
                null,
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(orb2X, orb2Y, w * 0.48f, orbPaint)

            canvas.restoreToCount(save)
            rectF.inset(strokePaint.strokeWidth / 2f, strokePaint.strokeWidth / 2f)
            canvas.drawRoundRect(rectF, r, r, strokePaint)
            super.onDraw(canvas)
        }
    }

    private inner class ShimmerGoldButton(
        context: Activity,
        labelText: String,
        textSizeSp: Float,
        private val radiusDp: Int
    ) : TextView(context) {
        private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val shimmerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rectF = RectF()
        private var sweepFraction = -0.4f
        private var shimmerAnimator: ValueAnimator? = null

        init {
            text = labelText
            textSize = textSizeSp
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(8, 13, 25))
            isClickable = true
            isFocusable = true
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            shimmerAnimator = ValueAnimator.ofFloat(-0.5f, 1.5f).apply {
                duration = 2600
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
                interpolator = LinearInterpolator()
                addUpdateListener {
                    sweepFraction = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }

        override fun onDetachedFromWindow() {
            shimmerAnimator?.cancel()
            shimmerAnimator = null
            super.onDetachedFromWindow()
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            if (w > 0f && h > 0f) {
                val r = dp(radiusDp).toFloat()
                rectF.set(0f, 0f, w, h)
                basePaint.shader = LinearGradient(
                    0f, 0f, w, h,
                    intArrayOf(goldBright, gold, goldDeep),
                    floatArrayOf(0f, 0.55f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawRoundRect(rectF, r, r, basePaint)

                val centerX = w * sweepFraction
                val bandWidth = w * 0.35f
                shimmerPaint.shader = LinearGradient(
                    centerX - bandWidth, 0f, centerX + bandWidth, h,
                    intArrayOf(Color.TRANSPARENT, Color.argb(125, 255, 255, 255), Color.TRANSPARENT),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawRoundRect(rectF, r, r, shimmerPaint)
            }
            super.onDraw(canvas)
        }
    }

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

    override fun onResume() {
        super.onResume()
        volumeControlStream = AudioManager.STREAM_MUSIC
        if (::web.isInitialized) {
            web.onResume()
            web.resumeTimers()
        }
        requestMediaAudioFocus()
        syncAllUiStates()
    }

    private fun requestMediaAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                            .build()
                    )
                    .setOnAudioFocusChangeListener(audioFocusListener)
                    .setWillPauseWhenDucked(false)
                    .build()
                audioFocusRequest = request
                audioManager.requestAudioFocus(request)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    audioFocusListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
                )
            }
        } catch (error: Exception) {
            android.util.Log.w("FlowPass", "Audio focus request failed", error)
        }
    }

    private fun abandonMediaAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
                audioFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(audioFocusListener)
            }
        } catch (_: Exception) { }
    }

    override fun onPause() {
        if (::web.isInitialized) {
            CookieManager.getInstance().flush()
            web.onPause()
        }
        abandonMediaAudioFocus()
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
        } else if (::homePanel.isInitialized && homePanel.visibility == View.VISIBLE) {
            openFlowHome()
        } else if (::web.isInitialized && web.visibility == View.VISIBLE && web.canGoBack()) {
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
