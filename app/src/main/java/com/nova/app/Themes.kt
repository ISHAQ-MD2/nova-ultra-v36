package com.nova.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Wallpaper themes.
 *  - Built-in: 4 hacker backgrounds (v30)
 *  - Custom: aap khud ki koi bhi picture add karo (APK ke andar save hoti hai, jitni chaho)
 * fx: 0 petals, 1 leaves, 2 sparks. cut = baal hilane wala alag cutout hai.
 */
class Theme(val id: String, val name: String, val accent: Int, val bg: Int, val fx: Int, val cut: Boolean = false, val amp: Float = 0f, val custom: Boolean = false) {
    // baal hilne ke zone (tasveer ki height/width ka hissa)
    val hv0 = 0.27f; val hv1 = 0.40f; val hv2 = 0.58f
    val hd0 = 0.50f; val hd1 = 0.80f
}

object Themes {
    private fun c(s: String) = Color.parseColor(s)

    private val builtin: List<Theme> = listOf(
        Theme("h1", "Matrix Terminal", c("#00FF9C"), c("#020805"), 2),
        Theme("h2", "Cyber Circuit", c("#28BEFF"), c("#02060F"), 2),
        Theme("h3", "Red Alert", c("#FF2840"), c("#0B0204"), 2),
        Theme("h4", "Hex Grid Purple", c("#BE5AFF"), c("#05020C"), 2)
    )

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("nova", Context.MODE_PRIVATE)
    private fun dir(ctx: Context): File = File(ctx.filesDir, "custom").apply { mkdirs() }

    fun customThemes(ctx: Context): List<Theme> = try {
        val a = JSONArray(prefs(ctx).getString("custom_walls", "[]"))
        (0 until a.length()).map { val o = a.getJSONObject(it); Theme(o.getString("id"), o.getString("name"), o.getInt("accent"), o.getInt("bg"), o.optInt("fx", 0), o.optBoolean("cut", false), 0f, true) }
    } catch (_: Exception) { emptyList() }

    fun all(ctx: Context): List<Theme> = builtin + customThemes(ctx)
    fun byId(ctx: Context, id: String): Theme = all(ctx).firstOrNull { it.id == id } ?: builtin[0]
    fun current(ctx: Context): Theme = byId(ctx, prefs(ctx).getString("theme", "h1") ?: "h1")
    fun set(ctx: Context, id: String) { prefs(ctx).edit().putString("theme", id).apply() }

    private fun res(ctx: Context, name: String): Bitmap? = try {
        val rid = ctx.resources.getIdentifier(name, "drawable", ctx.packageName)
        if (rid == 0) null else BitmapFactory.decodeResource(ctx.resources, rid)
    } catch (_: Throwable) { null }

    fun wallpaper(ctx: Context, t: Theme): Bitmap? =
        if (t.custom) try { BitmapFactory.decodeFile(File(dir(ctx), t.id + ".jpg").path) } catch (_: Throwable) { null } else res(ctx, "wp_" + t.id)
    fun cutout(ctx: Context, t: Theme): Bitmap? =
        if (!t.cut) null else if (t.custom) try { BitmapFactory.decodeFile(File(dir(ctx), t.id + "_cut.png").path) } catch (_: Throwable) { null } else res(ctx, "cut_" + t.id)

    /** Picker ke liye chhota gol thumbnail */
    fun thumb(ctx: Context, t: Theme): Bitmap? {
        if (!t.custom) return res(ctx, "thumb_" + t.id)
        return try {
            val o = BitmapFactory.Options().apply { inSampleSize = 4 }
            val b = BitmapFactory.decodeFile(File(dir(ctx), t.id + ".jpg").path, o) ?: return null
            val s = minOf(b.width, b.height); val l = (b.width - s) / 2
            Bitmap.createBitmap(b, l, 0, s, s)
        } catch (_: Throwable) { null }
    }

    /** Gallery ki picture ko wallpaper/character ban kar add karo. Background thread se bulao. Fail par null. */
    fun addCustom(ctx: Context, uri: Uri): Theme? {
        return try {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
            var sample = 1
            while (maxOf(o.outWidth, o.outHeight) / sample > 2000) sample *= 2
            val bo = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bo) } ?: return null
            val id = "c" + System.currentTimeMillis()
            val f = File(dir(ctx), "$id.jpg")
            FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            try { Backup.sub("wallpapers")?.let { f.copyTo(File(it, "$id.jpg"), true) } } catch (_: Exception) {}     // NOVA-FILES mein bhi
            // rang: kinaron ka andhera + sabse chamakdar rang
            val w = bmp.width; val h = bmp.height
            var r = 0L; var g = 0L; var b = 0L; var n = 0
            for (x in 0 until w step maxOf(1, w / 24)) for (y in intArrayOf(0, h - 1)) { val p = bmp.getPixel(x, y); r += Color.red(p); g += Color.green(p); b += Color.blue(p); n++ }
            val bg = Color.rgb((r / n * 0.5).toInt(), (g / n * 0.5).toInt(), (b / n * 0.5).toInt())
            var best = 0f; var acc = c("#FF8FC7")
            val hsv = FloatArray(3)
            for (x in 0 until w step maxOf(1, w / 30)) for (y in 0 until h step maxOf(1, h / 40)) {
                Color.colorToHSV(bmp.getPixel(x, y), hsv); val sc = hsv[1] * hsv[2]
                if (sc > best) { best = sc; acc = Color.HSVToColor(floatArrayOf(hsv[0], minOf(1f, hsv[1] + 0.15f), 1f)) }
            }
            val hasCut = false
            val list = JSONArray(prefs(ctx).getString("custom_walls", "[]"))
            val name = "My picture " + (list.length() + 1)
            list.put(JSONObject().put("id", id).put("name", name).put("accent", acc).put("bg", bg).put("fx", 0).put("cut", hasCut))
            prefs(ctx).edit().putString("custom_walls", list.toString()).apply()
            Theme(id, name, acc, bg, 0, hasCut, 0f, true)
        } catch (_: Throwable) { null }
    }

    fun deleteCustom(ctx: Context, id: String) {
        try {
            val a = JSONArray(prefs(ctx).getString("custom_walls", "[]")); val out = JSONArray()
            for (i in 0 until a.length()) if (a.getJSONObject(i).getString("id") != id) out.put(a.getJSONObject(i))
            prefs(ctx).edit().putString("custom_walls", out.toString()).apply()
            File(dir(ctx), "$id.jpg").delete(); File(dir(ctx), id + "_cut.png").delete()
            if (prefs(ctx).getString("theme", "") == id) set(ctx, "h1")
        } catch (_: Exception) {}
    }
}
