package com.nova.app

import android.app.Activity
import android.app.AlertDialog
import android.os.Handler
import android.text.InputType
import android.widget.EditText
import android.widget.TextView

/**
 * v30: SETTINGS mein naye features ke rows. Sirf is EK file mein - taake aage koi naya setting add
 * karna ho to poori zip nahi, sirf ye file (ya jis feature ki hai wo file) replace karni pare.
 */
object FeatureUi {
    private fun prefs(ctx: Activity) = ctx.getSharedPreferences("nova", android.content.Context.MODE_PRIVATE)

    private fun flag(ctx: Activity, key: String, def: Boolean) = prefs(ctx).getBoolean(key, def)
    private fun flip(ctx: Activity, key: String, def: Boolean, refresh: () -> Unit) { prefs(ctx).edit().putBoolean(key, !flag(ctx, key, def)).apply(); refresh() }

    fun items(ctx: Activity, status: TextView, h: Handler, refresh: () -> Unit): List<SItem> = listOf(
        SItem("grid", "NOVA Browser kholo", {
            NovaBrowser.profileLabel() + " | " + NovaBrowser.tabs.size + " tab | login hamesha save"
        }, null, { NovaBrowser.openUi(ctx) }),

        SItem("cloud", "Links / search NOVA browser mein", { "Chrome nahi - NOVA ka apna browser (open_url, web_search)" },
            { flag(ctx, "nb_open", true) }, { flip(ctx, "nb_open", true, refresh) }),

        SItem("music", "YouTube NOVA browser mein", { "Gaana/video YouTube app nahi, apne browser mein (background mein bhi chalta hai)" },
            { NovaBrowser.ytInBrowser(ctx) }, { flip(ctx, "nb_yt", true, refresh) }),

        SItem("bolt", "YouTube ads khud skip", { "Skip button aate hi khud dabti hai" },
            { flag(ctx, "adskip", true) }, { flip(ctx, "adskip", true, refresh) }),

        SItem("grid", "Floating browser window", {
            if (!NovaBrowser.overlayOk()) "Pehle 'Display over other apps' ON karo (neeche wali setting)" else "Kisi bhi app/game ke upar tairti window - NOVA jo kare wo dikhta hai"
        }, { flag(ctx, "nb_float", false) }, { status.text = NovaBrowser.setFloatPref(ctx, !flag(ctx, "nb_float", false)); refresh() }),

        SItem("lock", "Display over other apps", {
            if (NovaBrowser.overlayOk()) "ON ✅ (floating + background gaana chalega)" else "OFF - tap karke ON karo"
        }, null, {
            try { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:" + ctx.packageName))) } catch (_: Exception) {}
        }),

        SItem("person", "Browser profile (2 login-set)", { NovaBrowser.profileLabel() }, null, { browserProfileDialog(ctx, status, refresh) }),

        SItem("image", "Wallpaper par browser dikhao", { "NOVA browser mein jo kar rahi ho uski live tasveer wallpaper par" },
            { HackerWall.on(ctx, "browser") }, { prefs(ctx).edit().putBoolean("wpw_browser", !HackerWall.on(ctx, "browser")).apply(); refresh() }),

        SItem("folder", "Browser download folder", { NovaBrowser.downloadDir().path }, null, { browserDownloadsDialog(ctx) }),

        SItem("lock", "Browser ka saara login/data saaf", { "Google, GitHub sab logout ho jayenge" }, null, {
            AlertDialog.Builder(ctx, android.R.style.Theme_DeviceDefault_Dialog_Alert).setMessage("Browser ke saare login, cookies aur history hat jayenge. Pakka?")
                .setPositiveButton("Haan saaf karo") { _, _ -> NovaBrowser.init(ctx); NovaBrowser.clearAllData(); status.text = "Browser data saaf" }
                .setNegativeButton("Nahi", null).show()
        }),

        SItem("grid", "Wallpaper widgets", {
            val on = HackerWall.KEYS.count { HackerWall.on(ctx, it.first) }
            "$on / ${HackerWall.KEYS.size} ON - live hacker wallpaper par kya dikhe"
        }, null, { wallpaperWidgetsDialog(ctx, refresh) }),

        SItem("clock", "Wallpaper clock style", {
            arrayOf("Normal (grid)", "Center bada", "Upar-left corner", "Upar-right corner", "Clock bilkul nahi")[HackerWall.clockStyle(ctx)]
        }, null, { clockStyleDialog(ctx, refresh) }),

        SItem("mic", "WhatsApp voice mode", {
            if (voiceMode(ctx) == "tts") "AI awaaz file bhejti hai (mic nahi chhoo'ti)" else "Aap khud boltay ho, NOVA mic hold karti hai"
        }, null, { voiceModeDialog(ctx, status, refresh) }),

        SItem("bolt", "Code AI (DeepSeek / Claude)", {
            "Default: " + (if (prefs(ctx).getString("ai_default", "deepseek") == "claude") "Claude" else "DeepSeek") + " (naam bolo to wahi khulegi)"
        }, null, { aiDefaultDialog(ctx, refresh) }),

        SItem("cloud", "DeepSeek API key (asli, fast)", {
            if (DeepSeekRest.hasKey(ctx)) "Lagi hui hai - ab app khole bina seedha jawab aayega" else "Nahi lagi - abhi DeepSeek APP khol kar type karti hai (platform.deepseek.com se key free milti hai)"
        }, null, { deepSeekKeyDialog(ctx, status, refresh) }),

        SItem("cloud", "2nd Gemini API key", {
            if (Keys.hasSecond(ctx)) "Lagi hui hai - quota khatam hote hi khud switch hogi" else "Nahi lagi - quota (429) aane par fallback nahi hoga"
        }, null, { secondKeyDialog(ctx, status, refresh) }),

        SItem("download", "Link auto-download (TikTok/YouTube)", { "Copy karte hi pehchaan kar download ka notification" },
            { prefs(ctx).getBoolean("clipwatch", true) },
            { prefs(ctx).edit().putBoolean("clipwatch", !prefs(ctx).getBoolean("clipwatch", true)).apply(); ClipWatch.autoStart(ctx); refresh() })
    )

    private fun browserProfileDialog(ctx: Activity, status: TextView, refresh: () -> Unit) {
        NovaBrowser.init(ctx)
        val opts = arrayOf("Mera profile - mere apne accounts (Google, GitHub ...)", "Nova ka profile - NOVA ke alag accounts")
        AlertDialog.Builder(ctx, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Browser kis ke login se chale?")
            .setSingleChoiceItems(opts, if (NovaBrowser.profileName() == "nova") 1 else 0) { d, which ->
                status.text = NovaBrowser.setProfile(if (which == 1) "nova" else "me")
                refresh(); d.dismiss()
            }.setNegativeButton("Band karo", null).show()
    }

    private fun browserDownloadsDialog(ctx: Activity) {
        val l = NovaBrowser.downloads.toList().takeLast(15).reversed()
        val sb = StringBuilder("Folder: ").append(NovaBrowser.downloadDir().path).append("\n\n")
        if (l.isEmpty()) sb.append("Abhi koi download nahi.") else l.forEach { sb.append("• ").append(it.name).append(" — ").append(it.status).append('\n') }
        AlertDialog.Builder(ctx, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("NOVA browser downloads").setMessage(sb.toString()).setPositiveButton("OK", null).show()
    }

    private fun voiceMode(ctx: Activity) = prefs(ctx).getString("voice_mode", "real") ?: "real"

    private fun clockStyleDialog(ctx: Activity, refresh: () -> Unit) {
        val opts = arrayOf("Normal (grid mein, chhota font)", "Center - bada time (alag font)", "Upar-left corner - chhota (alag font)",
            "Upar-right corner - chhota (alag font)", "Clock bilkul nahi dikhana")
        AlertDialog.Builder(ctx, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Wallpaper par time kaise dikhe")
            .setSingleChoiceItems(opts, HackerWall.clockStyle(ctx)) { d, which ->
                prefs(ctx).edit().putInt("wp_clockstyle", which).apply()
                refresh(); d.dismiss()
            }.setNegativeButton("Band karo", null).show()
    }

    private fun wallpaperWidgetsDialog(ctx: Activity, refresh: () -> Unit) {
        val keys = HackerWall.KEYS
        val labels = keys.map { it.second }.toTypedArray()
        val checked = keys.map { HackerWall.on(ctx, it.first) }.toBooleanArray()
        AlertDialog.Builder(ctx, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Wallpaper par kya dikhe")
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                prefs(ctx).edit().putBoolean("wpw_${keys[which].first}", isChecked).apply()
            }
            .setPositiveButton("Theek hai") { _, _ -> refresh() }
            .show()
    }

    private fun voiceModeDialog(ctx: Activity, status: TextView, refresh: () -> Unit) {
        val opts = arrayOf("Real voice - aap boltay ho, NOVA WhatsApp ka mic hold karti hai", "TTS voice - NOVA apni awaaz file bana kar bhejti hai (photo/audio ki tarah)")
        val cur = if (voiceMode(ctx) == "tts") 1 else 0
        AlertDialog.Builder(ctx, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("WhatsApp voice message mode")
            .setSingleChoiceItems(opts, cur) { d, which ->
                prefs(ctx).edit().putString("voice_mode", if (which == 1) "tts" else "real").apply()
                status.text = "Voice mode: " + (if (which == 1) "TTS (file)" else "Real (aapki awaaz)")
                refresh(); d.dismiss()
            }.setNegativeButton("Band karo", null).show()
    }

    private fun aiDefaultDialog(ctx: Activity, refresh: () -> Unit) {
        val opts = arrayOf("DeepSeek (default)", "Claude")
        val cur = if (prefs(ctx).getString("ai_default", "deepseek") == "claude") 1 else 0
        AlertDialog.Builder(ctx, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Code kisse likhwana hai (naam na bolo to)")
            .setSingleChoiceItems(opts, cur) { d, which ->
                prefs(ctx).edit().putString("ai_default", if (which == 1) "claude" else "deepseek").apply()
                refresh(); d.dismiss()
            }.setNegativeButton("Band karo", null).show()
    }

    private fun deepSeekKeyDialog(ctx: Activity, status: TextView, refresh: () -> Unit) {
        val box = EditText(ctx).apply { hint = "DeepSeek API key (platform.deepseek.com)"; setSingleLine(); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        AlertDialog.Builder(ctx, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("DeepSeek asli API key")
            .setMessage("platform.deepseek.com par account banao -> API keys -> naya key banao, yahan paste karo. Lagne ke baad code_task app khole/type kiye bina seedha jawab layega (zyada fast/reliable).")
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                val k = box.text.toString().trim()
                DeepSeekRest.saveKey(ctx, k)
                status.text = if (k.isNotEmpty()) "DeepSeek API key save ho gayi ✅" else "DeepSeek API key hata di"
                refresh()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun secondKeyDialog(ctx: Activity, status: TextView, refresh: () -> Unit) {
        val box = EditText(ctx).apply { hint = "2nd Gemini API key"; setSingleLine(); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        AlertDialog.Builder(ctx, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("2nd API key (quota khatam hote hi fallback)")
            .setMessage("Jab pehli key ka quota (429) khatam ho, NOVA khud is doosri key par switch ho jayegi.")
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                val k = box.text.toString().trim()
                if (k.isNotEmpty()) { Keys.saveSecond(ctx, k); status.text = "2nd API key save ho gayi ✅" }
                refresh()
            }.setNegativeButton("Cancel", null).show()
    }
}
