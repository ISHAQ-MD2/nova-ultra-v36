package com.nova.app

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.io.File
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * v30 TTS VOICE MODE: NOVA apni awaaz (apna TTS ya ElevenLabs) seedha ek audio FILE mein save karti hai
 * (APK ke apne folder mein, "jaise koi apni memory se audio bhejta hai"), phir wahi file WhatsApp par
 * normal AUDIO ATTACHMENT ki tarah bheji jati hai - mic se "record" nahi karni padti, isliye kabhi galat
 * nahi hoti aur asli (sahi) awaaz hi jaati hai.
 */
object TtsFile {
    private fun dir(ctx: Context): File = File(ctx.filesDir, "nova_voice").apply { mkdirs() }

    /** Text ko audio file mein likho. Pehle apni AI awaaz (ElevenLabs, ON ho to), warna phone ka TTS. null = fail. */
    fun synth(ctx: Context, text: String, timeoutMs: Long = 25000L): File? {
        if (text.isBlank()) return null
        val out = File(dir(ctx), "voice_" + (System.currentTimeMillis() % 1000000) + ".mp3")
        if (CloudVoice.enabled(ctx) && Net.online(ctx)) {
            val f = CloudVoice.synthToFile(ctx, text, out)
            if (f != null) { try { Backup.sub("voices")?.let { f.copyTo(File(it, f.name), true) } } catch (_: Exception) {}; return f }
        }
        return synthLocal(ctx, text, out, timeoutMs)
    }

    private fun synthLocal(ctx: Context, text: String, out: File, timeoutMs: Long): File? {
        val latch = CountDownLatch(1)
        var ok = false
        var tts: TextToSpeech? = null
        tts = TextToSpeech(ctx.applicationContext) { st ->
            val e = tts
            if (st == TextToSpeech.SUCCESS && e != null) {
                try { if (e.setLanguage(Locale("hi", "IN")) < TextToSpeech.LANG_AVAILABLE) e.setLanguage(Locale.getDefault()) } catch (_: Exception) {}
                e.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onDone(id: String?) { ok = true; latch.countDown() }
                    @Deprecated("old api") override fun onError(id: String?) { latch.countDown() }
                    override fun onError(id: String?, code: Int) { latch.countDown() }
                })
                val params = android.os.Bundle()
                e.synthesizeToFile(text, params, out, "nova_voice")
            } else latch.countDown()
        }
        try { latch.await(timeoutMs, TimeUnit.MILLISECONDS) } catch (_: Exception) {}
        try { tts?.shutdown() } catch (_: Exception) {}
        return if (ok && out.exists() && out.length() > 200) out else null
    }
}
