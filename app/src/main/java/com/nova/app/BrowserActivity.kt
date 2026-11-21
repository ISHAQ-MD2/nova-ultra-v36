package com.nova.app

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast

/**
 * NOVA BROWSER ki poori-screen window. Asli engine NovaBrowser.kt mein hai (yahan sirf dikhane wala hissa):
 * URL bar, back/forward/reload, tabs, bookmarks, history, downloads, desktop site, floating mode, profile, long-press menu, fullscreen video.
 * Activity peeche jaye to NovaBrowser khud floating/chhupa window mein bhej deta hai (gaana chalta rehta hai).
 */
class BrowserActivity : Activity() {
    lateinit var webBox: FrameLayout
    private lateinit var rootFrame: FrameLayout
    private lateinit var urlBar: EditText
    private lateinit var progress: ProgressBar
    private lateinit var tabBtn: TextView
    private lateinit var reloadBtn: TextView
    private lateinit var backBtn: TextView
    private lateinit var fwdBtn: TextView
    private var customBox: FrameLayout? = null
    private var customView: View? = null
    private var customCb: WebChromeClient.CustomViewCallback? = null
    private var pendingUrl: String? = null
    private val h = Handler(Looper.getMainLooper())

    private val bgCol = Color.parseColor("#0B1220")
    private val barCol = Color.parseColor("#141E33")
    private val accent = Color.parseColor("#00E5FF")

    private fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        NovaBrowser.init(this)
        window.statusBarColor = bgCol
        window.navigationBarColor = bgCol
        buildUi()
        takeIntent(intent)
    }

    override fun onNewIntent(i: Intent?) {
        super.onNewIntent(i)
        setIntent(i)
        takeIntent(i)
    }

    private fun takeIntent(i: Intent?) {
        val u = i?.getStringExtra("url") ?: i?.dataString
        if (!u.isNullOrBlank()) pendingUrl = u
    }

    override fun onStart() {
        super.onStart()
        NovaBrowser.bindActivity(this)
        NovaBrowser.onChange = { refreshUi() }
        refreshUi()
    }

    override fun onResume() {
        super.onResume()
        val u = pendingUrl
        pendingUrl = null
        if (u != null) NovaBrowser.routeUrl(u)
        else if (NovaBrowser.tabs.isEmpty()) NovaBrowser.newTab(NovaBrowser.home(), true)
        refreshUi()
    }

    override fun onStop() {
        if (!isChangingConfigurations) {
            hideCustom()
            NovaBrowser.onChange = null
            NovaBrowser.unbindActivity(this)
        }
        super.onStop()
    }

    override fun onDestroy() {
        NovaBrowser.unbindActivity(this)
        super.onDestroy()
    }

    @Deprecated("simple")
    override fun onBackPressed() {
        if (customView != null) { hideCustom(); return }
        val w = NovaBrowser.active?.web
        if (w != null && w.canGoBack()) w.goBack() else finish()
    }

    // ------------------------------------------------------------------ UI
    private fun glyph(t: String, size: Float = 20f, onClick: () -> Unit): TextView = TextView(this).apply {
        text = t; textSize = size; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        setOnClickListener { onClick() }
    }

    private fun buildUi() {
        rootFrame = FrameLayout(this).apply { setBackgroundColor(bgCol); fitsSystemWindows = true }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val bar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setBackgroundColor(barCol); setPadding(dp(4), dp(4), dp(4), dp(4)) }
        backBtn = glyph("←") { NovaBrowser.active?.web?.let { if (it.canGoBack()) it.goBack() } }
        fwdBtn = glyph("→") { NovaBrowser.active?.web?.let { if (it.canGoForward()) it.goForward() } }
        reloadBtn = glyph("↻") {
            val t = NovaBrowser.active
            if (t != null) { if (t.loading) t.web.stopLoading() else t.web.reload() }
        }
        urlBar = EditText(this).apply {
            setSingleLine(); setTextColor(Color.WHITE); setHintTextColor(Color.parseColor("#7F8CA3")); hint = "Search ya website likho"
            textSize = 14f; setSelectAllOnFocus(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            setPadding(dp(12), 0, dp(12), 0)
            background = GradientDrawable().apply { setColor(Color.parseColor("#1F2B45")); cornerRadius = dp(18).toFloat() }
            setOnEditorActionListener { v, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                    NovaBrowser.userGo(v.text.toString())
                    v.clearFocus()
                    try { (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(v.windowToken, 0) } catch (_: Exception) {}
                    true
                } else false
            }
        }
        tabBtn = glyph("1", 13f) { tabsDialog() }.apply {
            background = GradientDrawable().apply { setStroke(dp(2), Color.WHITE); cornerRadius = dp(6).toFloat() }
        }
        val menuBtn = glyph("⋮", 24f) { menuDialog() }
        bar.addView(backBtn, LinearLayout.LayoutParams(dp(34), dp(40)))
        bar.addView(fwdBtn, LinearLayout.LayoutParams(dp(34), dp(40)))
        bar.addView(reloadBtn, LinearLayout.LayoutParams(dp(34), dp(40)))
        bar.addView(urlBar, LinearLayout.LayoutParams(0, dp(38), 1f).apply { leftMargin = dp(4); rightMargin = dp(6) })
        bar.addView(tabBtn, LinearLayout.LayoutParams(dp(28), dp(28)).apply { rightMargin = dp(6) })
        bar.addView(menuBtn, LinearLayout.LayoutParams(dp(30), dp(40)))
        col.addView(bar, LinearLayout.LayoutParams(-1, -2))

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; isIndeterminate = false; progressTintList = ColorStateList.valueOf(accent); visibility = View.INVISIBLE
        }
        col.addView(progress, LinearLayout.LayoutParams(-1, dp(3)))

        webBox = FrameLayout(this).apply { setBackgroundColor(Color.WHITE) }
        col.addView(webBox, LinearLayout.LayoutParams(-1, 0, 1f))
        rootFrame.addView(col, FrameLayout.LayoutParams(-1, -1))
        setContentView(rootFrame)
    }

    private fun refreshUi() {
        try {
            val t = NovaBrowser.active
            if (!urlBar.hasFocus()) urlBar.setText(t?.url ?: "")
            val loading = t?.loading == true
            progress.visibility = if (loading) View.VISIBLE else View.INVISIBLE
            progress.progress = t?.progress ?: 0
            tabBtn.text = NovaBrowser.tabs.size.toString()
            reloadBtn.text = if (loading) "✕" else "↻"
            val w = t?.web
            backBtn.alpha = if (w != null && w.canGoBack()) 1f else 0.35f
            fwdBtn.alpha = if (w != null && w.canGoForward()) 1f else 0.35f
        } catch (_: Exception) {}
    }

    private fun dlg() = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    private fun copy(s: String) {
        try { (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("url", s)); toast("Copy ho gaya") } catch (_: Exception) {}
    }

    // ------------------------------------------------------------------ menu
    private fun menuDialog() {
        val t = NovaBrowser.active
        val prefs = getSharedPreferences("nova", Context.MODE_PRIVATE)
        val floatOn = prefs.getBoolean("nb_float", false)
        val items = arrayOf(
            "➕ Naya tab",
            "🗂 Tabs (" + NovaBrowser.tabs.size + ")",
            "⭐ Bookmark karo",
            "📚 Bookmarks",
            "🕘 History",
            "⬇ Downloads",
            "🖥 Desktop site: " + (if (t?.desktop == true) "ON" else "OFF"),
            "🪟 Floating window: " + (if (floatOn) "ON" else "OFF"),
            "👤 Profile: " + NovaBrowser.profileLabel(),
            "🔗 Link copy karo",
            "🏠 Isko home page banao",
            "🧹 Saara login / data saaf",
            "❓ Google login madad",
            "✖ Browser band karo"
        )
        dlg().setTitle("NOVA Browser").setItems(items) { _, i ->
            when (i) {
                0 -> NovaBrowser.newTab(NovaBrowser.home(), true)
                1 -> tabsDialog()
                2 -> { if (t != null) { NovaBrowser.addBookmark(t.title, t.url); toast("Bookmark ho gaya") } }
                3 -> listDialog("📚 Bookmarks", NovaBrowser.bookmarks(), false)
                4 -> listDialog("🕘 History", NovaBrowser.history(), true)
                5 -> downloadsDialog()
                6 -> { if (t != null) NovaBrowser.setDesktop(t, !t.desktop) }
                7 -> {
                    val msg = NovaBrowser.setFloatPref(this, !floatOn)
                    toast(msg)
                    if (!floatOn && NovaBrowser.overlayOk()) finish()   // activity band -> floating window khul jati hai
                }
                8 -> profileDialog()
                9 -> if (t != null) copy(t.url)
                10 -> if (t != null) { prefs.edit().putString("nb_home", t.url).apply(); toast("Home page set") }
                11 -> dlg().setMessage("Saare logins (Google, GitHub ...), cookies aur history hat jayenge. Pakka?")
                    .setPositiveButton("Haan saaf karo") { _, _ -> NovaBrowser.clearAllData(); toast("Saaf ho gaya") }.setNegativeButton("Nahi", null).show()
                12 -> googleHelp()
                13 -> { NovaBrowser.window("close"); finish() }
            }
        }.setNegativeButton("Close", null).show()
    }

    private fun tabsDialog() {
        val tabs = NovaBrowser.tabs.toList()
        if (tabs.isEmpty()) { NovaBrowser.newTab(NovaBrowser.home(), true); return }
        val names = tabs.mapIndexed { i, t -> (if (t === NovaBrowser.active) "● " else "○ ") + (i + 1) + ". " + t.title.ifEmpty { t.url }.take(46) }.toTypedArray()
        dlg().setTitle("Tabs").setItems(names) { _, i -> NovaBrowser.setActive(tabs[i]) }
            .setPositiveButton("➕ Naya") { _, _ -> NovaBrowser.newTab(NovaBrowser.home(), true) }
            .setNeutralButton("Ye tab band") { _, _ ->
                val a = NovaBrowser.active
                if (a != null) NovaBrowser.closeTab(a)
                if (NovaBrowser.tabs.isEmpty()) NovaBrowser.newTab(NovaBrowser.home(), true)
            }
            .setNegativeButton("Close", null).show()
    }

    private fun listDialog(title: String, items: List<Pair<String, String>>, canClear: Boolean) {
        if (items.isEmpty()) { dlg().setTitle(title).setMessage("Abhi khali hai.").setPositiveButton("OK", null).show(); return }
        val names = items.map { (it.first.ifEmpty { it.second }).take(40) + "\n" + it.second.take(60) }.toTypedArray()
        val b = dlg().setTitle(title).setItems(names) { _, i -> NovaBrowser.userGo(items[i].second) }.setNegativeButton("Close", null)
        if (canClear) b.setNeutralButton("Saaf karo") { _, _ -> NovaBrowser.clearHistory(); toast("History saaf") }
        b.show()
    }

    private fun downloadsDialog() {
        val l = NovaBrowser.downloads.toList().takeLast(15).reversed()
        val sb = StringBuilder("Folder: ").append(NovaBrowser.downloadDir().path).append("\n\n")
        if (l.isEmpty()) sb.append("Abhi koi download nahi.") else l.forEach { sb.append("• ").append(it.name).append("  —  ").append(it.status).append('\n') }
        dlg().setTitle("⬇ Downloads").setMessage(sb.toString())
            .setPositiveButton("Downloads app") { _, _ -> try { startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)) } catch (_: Exception) {} }
            .setNegativeButton("Close", null).show()
    }

    private fun profileDialog() {
        val opts = arrayOf("Mera profile (mere apne accounts)", "Nova ka profile (NOVA ke alag accounts)")
        val cur = if (NovaBrowser.profileName() == "nova") 1 else 0
        dlg().setTitle("Kis ke login se browse karna hai?")
            .setSingleChoiceItems(opts, cur) { d, which ->
                val r = NovaBrowser.setProfile(if (which == 1) "nova" else "me")
                toast(r); d.dismiss()
                NovaBrowser.newTab(NovaBrowser.home(), true)
            }.setNegativeButton("Close", null).show()
    }

    private fun googleHelp() {
        dlg().setTitle("Google login madad")
            .setMessage("GitHub aur zyada tar sites mein login seedha chalta hai (ek baar login karo, hamesha save rehta hai).\n\n" +
                "Google kabhi kabhi apps ke andar ke browser mein login rok deta hai (\"This browser or app may not be secure\"). Aisa ho to:\n" +
                "1) Pehle accounts.google.com kholo aur wahin se login try karo (YouTube se nahi).\n" +
                "2) Phir bhi na ho to YouTube/doosri sites par Google ki jagah email+password ya GitHub se login karo.\n" +
                "3) Menu > Desktop site ON karke dobara try karo.\n\n" +
                "Ye Google ki apni pabandi hai, app se poori tarah hat nahi sakti.")
            .setPositiveButton("accounts.google.com kholo") { _, _ -> NovaBrowser.userGo("https://accounts.google.com") }
            .setNegativeButton("Close", null).show()
    }

    // ------------------------------------------------------------------ long press (link / image)
    fun onLongPress(hit: WebView.HitTestResult): Boolean {
        val extra = hit.extra ?: return false
        return when (hit.type) {
            WebView.HitTestResult.SRC_ANCHOR_TYPE, WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                dlg().setTitle(extra.take(70)).setItems(arrayOf("Naye tab mein kholo", "Link copy karo", "⬇ Download karo")) { _, i ->
                    when (i) {
                        0 -> NovaBrowser.newTab(extra, true)
                        1 -> copy(extra)
                        2 -> NovaBrowser.startDownload(extra, null, null, null, NovaBrowser.active?.url ?: "", NovaBrowser.active?.web)
                    }
                }.show()
                true
            }
            WebView.HitTestResult.IMAGE_TYPE -> {
                dlg().setTitle("Image").setItems(arrayOf("⬇ Image download karo", "Image link copy karo", "Image naye tab mein")) { _, i ->
                    when (i) {
                        0 -> NovaBrowser.startDownload(extra, null, null, null, NovaBrowser.active?.url ?: "", NovaBrowser.active?.web)
                        1 -> copy(extra)
                        2 -> NovaBrowser.newTab(extra, true)
                    }
                }.show()
                true
            }
            else -> false
        }
    }

    // ------------------------------------------------------------------ fullscreen video
    fun showCustom(v: View, cb: WebChromeClient.CustomViewCallback?) {
        if (customView != null) { try { cb?.onCustomViewHidden() } catch (_: Exception) {}; return }
        customView = v; customCb = cb
        val box = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        box.addView(v, FrameLayout.LayoutParams(-1, -1))
        rootFrame.addView(box, FrameLayout.LayoutParams(-1, -1))
        customBox = box
        immersive(true)
    }

    fun hideCustom() {
        val b = customBox ?: return
        try { rootFrame.removeView(b) } catch (_: Exception) {}
        customBox = null; customView = null
        try { customCb?.onCustomViewHidden() } catch (_: Exception) {}
        customCb = null
        immersive(false)
    }

    @Suppress("DEPRECATION")
    private fun immersive(on: Boolean) {
        try {
            if (on) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                window.decorView.systemUiVisibility = 0
            }
        } catch (_: Exception) {}
    }
}
