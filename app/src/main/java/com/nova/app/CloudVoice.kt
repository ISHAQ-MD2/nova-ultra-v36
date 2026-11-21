package com.nova.app

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Apni pasand ki AI awaaz (jaise video wali awaaz): ElevenLabs par wo awaaz clone/chuno, API key + Voice ID yahan daalo.
 * Ye reminder, offline mode aur likhe hue jawab bolne mein use hoti hai (net ho to). Net na ho to phone ki offline awaaz.
 * Gemini Live (seedhi baat-cheet) ki awaaz Google ki hoti hai - wo yahan se nahi badalti.
 */
object CloudVoice {
    private fun p(ctx: Context) = ctx.getSharedPreferences("nova", Context.MODE_PRIVATE)
    fun enabled(ctx: Context) = p(ctx).getBoolean("cv_on", false) && (p(ctx).getString("cv_key", "") ?: "").isNotBlank() && (p(ctx).getString("cv_voice", "") ?: "").isNotBlank()
    fun save(ctx: Context, key: String, voice: String, on: Boolean) { p(ctx).edit().putString("cv_key", Crypto.enc(key.trim())).putString("cv_voice", voice.trim()).putBoolean("cv_on", on).apply() }
    fun savedVoice(ctx: Context) = p(ctx).getString("cv_voice", "") ?: ""
    private fun key(ctx: Context) = Crypto.dec(p(ctx).getString("cv_key", "") ?: "")

    /** Audio file banao bina bole (WhatsApp voice message ke liye) - null = fail. */
    fun synthToFile(ctx: Context, text: String, out: File): File? {
        if (!enabled(ctx) || !Net.online(ctx) || text.isBlank()) return null
        return try {
            val voice = savedVoice(ctx)
            val con = URL("https://api.elevenlabs.io/v1/text-to-speech/$voice?output_format=mp3_44100_128").openConnection() as HttpURLConnection
            con.requestMethod = "POST"; con.connectTimeout = 8000; con.readTimeout = 25000; con.doOutput = true
            con.setRequestProperty("xi-api-key", key(ctx)); con.setRequestProperty("Content-Type", "application/json")
            con.outputStream.use { it.write(JSONObject().put("text", text.take(900)).put("model_id", "eleven_multilingual_v2").toString().toByteArray()) }
            if (con.responseCode != 200) return null
            con.inputStream.use { i -> out.outputStream().use { o -> i.copyTo(o) } }
            if (out.exists() && out.length() > 200) out else null
        } catch (_: Throwable) { null }
    }

    /** Background thread se bulao. true = cloud awaaz mein bol diya. */
    fun speak(ctx: Context, text: String, timeoutMs: Long = 40_000L): Boolean {
        if (!enabled(ctx) || !Net.online(ctx) || text.isBlank()) return false
        return try {
            val voice = savedVoice(ctx)
            val con = URL("https://api.elevenlabs.io/v1/text-to-speech/$voice?output_format=mp3_44100_128").openConnection() as HttpURLConnection
            con.requestMethod = "POST"; con.connectTimeout = 8000; con.readTimeout = 25000; con.doOutput = true
            con.setRequestProperty("xi-api-key", key(ctx)); con.setRequestProperty("Content-Type", "application/json")
            con.outputStream.use { it.write(JSONObject().put("text", text.take(900)).put("model_id", "eleven_multilingual_v2").toString().toByteArray()) }
            if (con.responseCode != 200) return false
            val f = File(ctx.cacheDir, "cv.mp3"); con.inputStream.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
            val latch = CountDownLatch(1)
            val mp = MediaPlayer()
            mp.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            mp.setDataSource(f.path); mp.setOnCompletionListener { latch.countDown() }; mp.setOnErrorListener { _, _, _ -> latch.countDown(); true }
            mp.prepare(); Vol.setSpeaking(true); mp.start()
            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
            try { mp.release() } catch (_: Exception) {}
            Vol.setSpeaking(false); true
        } catch (_: Throwable) { Vol.setSpeaking(false); false }
    }
}
