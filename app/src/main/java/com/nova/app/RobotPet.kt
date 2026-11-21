package com.nova.app

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * Chhota stylish robot jo screen par apni marzi se ghoomta hai: kabhi upar camera par baith jata hai,
 * kabhi icons/folders ke upar, kabhi girl-robot ban jata hai. NOVA bolti hai to ye bhi mooh hilata hai.
 * Reminder ke waqt screen par aakar bolta hai "Sir, it's reminder time". Touch nahi rokta (NOT_TOUCHABLE).
 */
object RobotPet {
    private var view: RobotView? = null
    private var lp: WindowManager.LayoutParams? = null
    private var wm: WindowManager? = null
    private val ui = Handler(Looper.getMainLooper())
    private var tempShown = false

    fun enabled(ctx: Context) = ctx.getSharedPreferences("nova", Context.MODE_PRIVATE).getBoolean("robot", true)
    fun setEnabled(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences("nova", Context.MODE_PRIVATE).edit().putBoolean("robot", on).apply()
        if (on) show(ctx) else hide()
    }

    fun show(ctx: Context) {
        val app = ctx.applicationContext
        ui.post {
            if (view != null || !Settings.canDrawOverlays(app)) return@post
            val m = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val v = RobotView(app)
            val p = WindowManager.LayoutParams(v.ww, v.hh, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT)
            p.gravity = Gravity.TOP or Gravity.START
            val dm = app.resources.displayMetrics
            p.x = dm.widthPixels - v.ww; p.y = dm.heightPixels / 2
            try { m.addView(v, p); view = v; lp = p; wm = m; v.attach(m, p) } catch (_: Exception) {}
        }
    }

    fun hide() {
        ui.post {
            view?.let { it.detach(); try { wm?.removeView(it) } catch (_: Exception) {} }
            view = null; lp = null
        }
    }

    /** Robot screen par aakar bolta hai (reminder / azan). Robot band ho tab bhi thodi der ke liye aata hai. */
    fun say(ctx: Context, text: String, ms: Long = 9000L) {
        val app = ctx.applicationContext
        ui.post {
            if (view == null) { if (!Settings.canDrawOverlays(app)) return@post; tempShown = !enabled(app); show(app) }
            ui.postDelayed({ view?.say(text, ms); if (tempShown) ui.postDelayed({ if (tempShown) { tempShown = false; hide() } }, ms + 1500) }, 250)
        }
    }

    private class RobotView(val ctx: Context) : View(ctx) {
        private fun dp(x: Float) = x * ctx.resources.displayMetrics.density
        val ww = dp(230f).toInt(); val hh = dp(150f).toInt()
        private val sw = ctx.resources.displayMetrics.widthPixels
        private val sh = ctx.resources.displayMetrics.heightPixels
        private var wmr: WindowManager? = null
        private var lpr: WindowManager.LayoutParams? = null
        private val h = Handler(Looper.getMainLooper())
        private var girl = false
        private var mode = "idle"            // idle / walk / camera / perch
        private var emotion = "neutral"
        private var bubble = ""
        private var bubbleUntil = 0L
        private var tx = 0f; private var ty = 0f
        private var t0 = System.currentTimeMillis()
        private var nextPlan = 0L
        private var talkingUntil = 0L
        private val sp: (Boolean) -> Unit = { s -> if (s) talkingUntil = Long.MAX_VALUE else talkingUntil = System.currentTimeMillis() + 300 }
        private val em: (String) -> Unit = { e -> emotion = e; emoteUntil = System.currentTimeMillis() + 5000 }
        private var emoteUntil = 0L
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private var alive = true

        private val tick = object : Runnable {
            override fun run() {
                if (!alive) return
                step(); invalidate(); h.postDelayed(this, 33)
            }
        }

        fun attach(m: WindowManager, l: WindowManager.LayoutParams) {
            wmr = m; lpr = l; Vol.addSpeakListener(sp); Vol.addEmoteListener(em); h.post(tick)
            tx = l.x.toFloat(); ty = l.y.toFloat()
        }
        fun detach() { alive = false; h.removeCallbacksAndMessages(null); Vol.removeSpeakListener(sp); Vol.removeEmoteListener(em) }

        fun say(text: String, ms: Long) {
            bubble = text; bubbleUntil = System.currentTimeMillis() + ms; emotion = "happy"; emoteUntil = bubbleUntil
            // bolte waqt screen ke beech mein aa jao
            val l = lpr ?: return
            mode = "walk"; tx = (sw / 2f - ww / 2f); ty = sh * 0.38f
            nextPlan = bubbleUntil + 2000
        }

        private fun plan(now: Long) {
            val l = lpr ?: return
            val r = Random.nextInt(100)
            val top = dp(24f)
            when {
                r < 25 -> { mode = "camera"; tx = sw / 2f - ww / 2f + (if (girl) dp(0f) else 0f); ty = -dp(38f) }            // camera ke paas upar se jhaank raha
                r < 55 -> { mode = "perch"; tx = Random.nextFloat() * (sw - ww).coerceAtLeast(1); ty = (sh * (0.18f + Random.nextFloat() * 0.5f)) } // icons/folders ke upar
                else -> { mode = "walk"; tx = Random.nextFloat() * (sw - ww).coerceAtLeast(1); ty = top + Random.nextFloat() * (sh - hh - top * 2).coerceAtLeast(1f) }
            }
            if (Random.nextInt(100) < 18) girl = !girl
            if (Random.nextInt(100) < 25) { emotion = listOf("happy", "love", "surprised").random(); emoteUntil = now + 3000 }
            nextPlan = now + 5000 + Random.nextLong(7000)
        }

        private fun step() {
            val now = System.currentTimeMillis()
            val l = lpr ?: return
            if (now > nextPlan) plan(now)
            if (emoteUntil > 0 && now > emoteUntil) { emotion = "neutral"; emoteUntil = 0 }
            val dx = tx - l.x; val dy = ty - l.y
            val moving = abs(dx) > 3 || abs(dy) > 3
            if (moving) { l.x += (dx * 0.07f).toInt().let { if (it == 0) Integer.signum(dx.toInt()) else it }; l.y += (dy * 0.07f).toInt().let { if (it == 0) Integer.signum(dy.toInt()) else it } }
            try { wmr?.updateViewLayout(this, l) } catch (_: Exception) {}
        }

        override fun onDraw(c: Canvas) {
            val now = System.currentTimeMillis()
            val t = (now - t0) / 1000f
            val l = lpr
            val rightSide = (l?.x ?: 0) + ww / 2 > sw / 2 // robot window ke kis taraf
            val rw = dp(64f)
            val rcx = if (rightSide) ww - rw / 2 - dp(4f) else rw / 2 + dp(4f)
            val bob = if (mode == "camera") 0f else sin(t * 3f) * dp(2.5f)
            val base = hh - dp(6f)
            val top = base - dp(92f) + bob
            val peek = mode == "camera"
            val talking = now < talkingUntil || now < bubbleUntil && Vol.speaking
            val skin1 = if (girl) "#FF9AD5" else "#7FE7FF"
            val skin2 = if (girl) "#C2358F" else "#1B6FA8"
            val glow = if (girl) "#FF4FB8" else "#00E5FF"

            // glow
            p.shader = null; p.style = Paint.Style.FILL
            p.color = Color.parseColor(glow); p.alpha = 40
            c.drawCircle(rcx, top + dp(30f), dp(44f), p); p.alpha = 255

            // hover jet + zameen par saaya
            val jet = 0.6f + 0.4f * sin(t * 12f)
            p.shader = null; p.style = Paint.Style.FILL
            p.color = Color.parseColor(glow); p.alpha = (110 * jet).toInt(); c.drawOval(RectF(rcx - dp(15f), base - dp(1f), rcx + dp(15f), base + dp(6f)), p)
            p.alpha = (60 * jet).toInt(); c.drawOval(RectF(rcx - dp(24f), base + dp(2f), rcx + dp(24f), base + dp(9f)), p); p.alpha = 255
            // body
            val body = RectF(rcx - dp(18f), top + dp(52f), rcx + dp(18f), top + dp(86f))
            p.shader = LinearGradient(body.left, body.top, body.right, body.bottom, Color.parseColor(skin1), Color.parseColor(skin2), Shader.TileMode.CLAMP)
            c.drawRoundRect(body, dp(10f), dp(10f), p); p.shader = null
            p.color = Color.parseColor("#0A1626"); c.drawRoundRect(RectF(rcx - dp(9f), body.top + dp(8f), rcx + dp(9f), body.top + dp(20f)), dp(4f), dp(4f), p)
            p.color = Color.parseColor(glow); p.alpha = (150 + 100 * sin(t * 5f)).toInt().coerceIn(60, 255)
            c.drawCircle(rcx, body.top + dp(14f), dp(3.2f), p); p.alpha = 255
            // arms (wave when talking/happy)
            val wave = if (talking || emotion == "happy" || emotion == "love") sin(t * 9f) * dp(7f) else sin(t * 2f) * dp(2f)
            p.color = Color.parseColor(skin2); p.strokeWidth = dp(5f); p.strokeCap = Paint.Cap.ROUND; p.style = Paint.Style.STROKE
            c.drawLine(body.left, body.top + dp(10f), body.left - dp(10f), body.top + dp(24f) - wave, p)
            c.drawLine(body.right, body.top + dp(10f), body.right + dp(10f), body.top + dp(24f) + wave * 0.4f, p)
            p.style = Paint.Style.FILL
            // legs / feet
            p.color = Color.parseColor("#1B2A3F")
            c.drawRoundRect(RectF(rcx - dp(14f), body.bottom - dp(2f), rcx - dp(4f), body.bottom + dp(5f)), dp(3f), dp(3f), p)
            c.drawRoundRect(RectF(rcx + dp(4f), body.bottom - dp(2f), rcx + dp(14f), body.bottom + dp(5f)), dp(3f), dp(3f), p)

            // head
            val head = RectF(rcx - dp(28f), top + dp(6f), rcx + dp(28f), top + dp(52f))
            // antenna
            p.color = Color.parseColor(skin2); p.strokeWidth = dp(2.5f); p.style = Paint.Style.STROKE
            c.drawLine(rcx, head.top, rcx + sin(t * 2f) * dp(2f), head.top - dp(9f), p)
            p.style = Paint.Style.FILL; p.color = Color.parseColor(glow); c.drawCircle(rcx + sin(t * 2f) * dp(2f), head.top - dp(11f), dp(3.5f), p)
            p.shader = LinearGradient(head.left, head.top, head.right, head.bottom, Color.parseColor(skin1), Color.parseColor(skin2), Shader.TileMode.CLAMP)
            c.drawRoundRect(head, dp(18f), dp(18f), p); p.shader = null
            // visor
            val vz = RectF(head.left + dp(6f), head.top + dp(8f), head.right - dp(6f), head.bottom - dp(8f))
            p.color = Color.parseColor("#06101C"); c.drawRoundRect(vz, dp(12f), dp(12f), p)
            // eyes
            val blink = (now / 100) % 38 == 0L
            val ey = vz.top + dp(12f); val ex = dp(11f)
            val look = if (peek) dp(3f) else sin(t * 0.8f) * dp(2.5f)
            p.color = Color.parseColor(glow)
            for (side in intArrayOf(-1, 1)) {
                val x = rcx + side * ex + look
                when {
                    blink -> { p.strokeWidth = dp(2.5f); p.style = Paint.Style.STROKE; c.drawLine(x - dp(4f), ey, x + dp(4f), ey, p); p.style = Paint.Style.FILL }
                    emotion == "happy" || emotion == "love" -> { p.style = Paint.Style.STROKE; p.strokeWidth = dp(2.8f); c.drawArc(RectF(x - dp(5f), ey - dp(4f), x + dp(5f), ey + dp(6f)), 200f, 140f, false, p); p.style = Paint.Style.FILL }
                    emotion == "sad" -> { c.drawOval(RectF(x - dp(3.6f), ey - dp(2f), x + dp(3.6f), ey + dp(5f)), p) }
                    else -> c.drawOval(RectF(x - dp(4f), ey - dp(5f), x + dp(4f), ey + dp(5f)), p)
                }
                if (girl && !blink) { p.style = Paint.Style.STROKE; p.strokeWidth = dp(1.6f); c.drawLine(x + side * dp(4f), ey - dp(4f), x + side * dp(8f), ey - dp(7f), p); p.style = Paint.Style.FILL }
            }
            if (emotion == "love") { p.color = Color.parseColor("#FF4D88"); p.textSize = dp(11f); p.textAlign = Paint.Align.CENTER; c.drawText("♥", rcx + dp(24f), head.top - dp(2f) - (t * 8f % 8f), p) }
            // mouth
            p.color = Color.parseColor(glow); p.style = Paint.Style.STROKE; p.strokeWidth = dp(2.4f); p.strokeCap = Paint.Cap.ROUND
            val my = vz.bottom - dp(9f)
            if (talking) { p.style = Paint.Style.FILL; val o = dp(1.5f) + abs(sin(t * 14f)) * dp(5f); c.drawOval(RectF(rcx - dp(5f), my - o / 2, rcx + dp(5f), my + o / 2), p) }
            else if (emotion == "sad") c.drawArc(RectF(rcx - dp(6f), my, rcx + dp(6f), my + dp(8f)), 200f, 140f, false, p)
            else c.drawArc(RectF(rcx - dp(6f), my - dp(6f), rcx + dp(6f), my + dp(3f)), 20f, 140f, false, p)
            p.style = Paint.Style.FILL
            // girl extras: bow + blush
            if (girl) {
                p.color = Color.parseColor("#FF2E93")
                val bx = head.right - dp(8f); val by = head.top + dp(2f)
                val path = Path(); path.moveTo(bx, by); path.lineTo(bx - dp(11f), by - dp(7f)); path.lineTo(bx - dp(11f), by + dp(7f)); path.close()
                path.moveTo(bx, by); path.lineTo(bx + dp(11f), by - dp(7f)); path.lineTo(bx + dp(11f), by + dp(7f)); path.close()
                c.drawPath(path, p); c.drawCircle(bx, by, dp(3.2f), p)
                p.color = Color.parseColor("#FF9AD5"); p.alpha = 110
                c.drawCircle(rcx - dp(18f), my - dp(6f), dp(4f), p); c.drawCircle(rcx + dp(18f), my - dp(6f), dp(4f), p); p.alpha = 255
            }
            // shisha chamak (head ke upar) + antenna ki ghoomti ring
            p.style = Paint.Style.STROKE; p.strokeWidth = dp(2f); p.color = Color.WHITE; p.alpha = 70
            c.drawArc(RectF(head.left + dp(5f), head.top + dp(4f), head.right - dp(5f), head.bottom - dp(4f)), 200f, 60f, false, p); p.alpha = 255
            p.strokeWidth = dp(1.6f); p.color = Color.parseColor(glow); p.alpha = 200
            c.drawArc(RectF(rcx - dp(7f), head.top - dp(18f), rcx + dp(7f), head.top - dp(4f)), t * 240f % 360f, 100f, false, p); p.alpha = 255; p.style = Paint.Style.FILL
            // speech bubble
            if (now < bubbleUntil && bubble.isNotEmpty()) drawBubble(c, rightSide, rcx, head.top)
        }

        private fun drawBubble(c: Canvas, rightSide: Boolean, rcx: Float, headTop: Float) {
            val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = dp(12.5f); typeface = Typeface.DEFAULT_BOLD }
            val maxW = ww - dp(80f)
            val lines = ArrayList<String>(); var cur = ""
            for (w in bubble.split(" ")) { val tst = if (cur.isEmpty()) w else "$cur $w"; if (tp.measureText(tst) > maxW) { lines.add(cur); cur = w } else cur = tst }
            if (cur.isNotEmpty()) lines.add(cur)
            val shown = lines.take(4)
            val bw = (shown.maxOf { tp.measureText(it) } + dp(20f)); val bh = shown.size * dp(16f) + dp(16f)
            val left = if (rightSide) ww - dp(74f) - bw else dp(74f)
            val top = (headTop - dp(4f)).coerceAtLeast(dp(2f)).coerceAtMost(hh - bh - dp(4f))
            val r = RectF(left, top, left + bw, top + bh)
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(r.left, r.top, r.right, r.bottom, Color.parseColor("#E61A2B4D"), Color.parseColor("#E60B1730"), Shader.TileMode.CLAMP) }
            c.drawRoundRect(r, dp(14f), dp(14f), bg)
            val st = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(1.4f); color = Color.parseColor(if (girl) "#FF4FB8" else "#00E5FF") }
            c.drawRoundRect(r, dp(14f), dp(14f), st)
            shown.forEachIndexed { i, s -> c.drawText(s, r.left + dp(10f), r.top + dp(18f) + i * dp(16f), tp) }
        }
    }
}
