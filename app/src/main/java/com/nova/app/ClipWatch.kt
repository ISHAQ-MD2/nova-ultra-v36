package com.nova.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper

/**
 * v30: "Vidmate jaisa" - TikTok/YouTube/Instagram/Facebook link copy karte hi khud pehchaanta hai aur
 * ek notification deta hai "Download karna hai?" (Termux mein yt-dlp ho to khud download karta hai).
 * Sirf app foreground mein ho tab clipboard padh sakte hain (Android 10+ ka rule) - isliye yahi se autoStart() hota hai.
 */
object ClipWatch {
    @Volatile private var started = false
    private var lastSeen = ""

    private val HOSTS = listOf(
        "tiktok.com" to "TikTok", "vt.tiktok.com" to "TikTok", "vm.tiktok.com" to "TikTok",
        "youtube.com" to "YouTube", "youtu.be" to "YouTube",
        "instagram.com" to "Instagram", "facebook.com" to "Facebook", "fb.watch" to "Facebook"
    )

    private fun site(url: String): String? {
        val host = Regex("https?://([^/]+)").find(url)?.groupValues?.get(1)?.lowercase() ?: return null
        return HOSTS.firstOrNull { host.contains(it.first) }?.second
    }

    fun autoStart(ctx: Context) {
        if (started) return
        if (!ctx.getSharedPreferences("nova", Context.MODE_PRIVATE).getBoolean("clipwatch", true)) return
        started = true
        val app = ctx.applicationContext
        Handler(Looper.getMainLooper()).post {
            try {
                val cm = app.getSystemService(ClipboardManager::class.java)
                cm.addPrimaryClipChangedListener {
                    try {
                        val t = ClipStore.readNow(app)?.trim() ?: return@addPrimaryClipChangedListener
                        if (t.isEmpty() || t == lastSeen) return@addPrimaryClipChangedListener
                        lastSeen = t
                        if (!t.startsWith("http")) return@addPrimaryClipChangedListener
                        val s = site(t) ?: return@addPrimaryClipChangedListener
                        if (!app.getSharedPreferences("nova", Context.MODE_PRIVATE).getBoolean("clipwatch", true)) return@addPrimaryClipChangedListener
                        notifyDownload(app, s, t)
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }
    }

    private fun notifyDownload(ctx: Context, site: String, url: String) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("nova_clip", "NOVA clipboard links", NotificationManager.IMPORTANCE_DEFAULT))
        val dl = PendingIntent.getBroadcast(ctx, (url.hashCode()), Intent(ctx, ClipDownloadReceiver::class.java).putExtra("url", url),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        nm.notify((url.hashCode() and 0x7fffffff) % 90000 + 5000,
            Notification.Builder(ctx, "nova_clip").setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("$site link mila")
                .setContentText("Download karoon? (yt-dlp Termux mein chahiye)")
                .addAction(0, "📥 Download karo", dl)
                .setAutoCancel(true).build())
        ClipStore.add(ctx, url, "$site link")
        WallBus.pushFeed("CLIP", "$site link mila")
    }
}

class ClipDownloadReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        val url = i.getStringExtra("url") ?: return
        Thread {
            val check = TermuxBridge.run(ctx, "command -v yt-dlp || pip show yt-dlp 2>/dev/null | head -1", false)
            val has = !check.contains("exit=1") || check.contains("yt-dlp")
            if (!has) {
                Notifier.show(ctx, "NOVA", "yt-dlp Termux mein nahi hai. Install karne ke liye: pkg install -y python && pip install yt-dlp")
                return@Thread
            }
            Notifier.show(ctx, "NOVA", "Download shuru...")
            val out = TermuxBridge.run(ctx, "mkdir -p ~/storage/downloads/NOVA-Downloads 2>/dev/null; cd ~/storage/downloads/NOVA-Downloads 2>/dev/null || cd ~; yt-dlp -o '%(title).80s.%(ext)s' '" + url.replace("'", "") + "' 2>&1 | tail -20", false)
            WallBus.download(if (out.contains("exit=0")) "done: $url" else "fail: $url")
            Notifier.show(ctx, "NOVA download", if (out.contains("exit=0")) "Download ho gaya (Download/NOVA-Downloads)" else "Download fail: ${out.take(200)}")
        }.start()
    }
}
