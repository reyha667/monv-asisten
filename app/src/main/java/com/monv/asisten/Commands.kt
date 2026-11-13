package com.monv.asisten

import android.Manifest
import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.accessibilityservice.AccessibilityService
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Otak Monv: baca teks perintah -> jalankan -> balas lewat reply(). */
object Commands {

    private var torchOn = false

    const val HELP = "MENU MONV:\n" +
        "buka [app/web] • cari [kata]\n" +
        "telepon [nama/nomor] • wa [nama]\n" +
        "senter on/off • volume naik/turun\n" +
        "scroll down/up • screenshot\n" +
        "ketik [teks] • tap [teks]\n" +
        "catat [teks] • catatan\n" +
        "baterai • status • bersihkan\n" +
        "kembali • home • recent • tidur"

    private val alias = mapOf(
        "wa" to listOf("whatsapp"), "ig" to listOf("instagram"), "yt" to listOf("youtube"),
        "fb" to listOf("facebook"), "tele" to listOf("telegram"), "tt" to listOf("tiktok"),
        "setelan" to listOf("pengaturan", "settings"), "pengaturan" to listOf("pengaturan", "settings"),
        "kalkulator" to listOf("kalkulator", "calculator"), "galeri" to listOf("galeri", "gallery", "foto", "photos"),
        "chrome" to listOf("chrome"), "playstore" to listOf("play store"), "gmail" to listOf("gmail")
    )

    fun run(svc: OverlayService, raw: String, reply: (String) -> Unit) {
        val t = raw.trim()
        if (t.isEmpty()) return
        val sp = t.indexOf(' ')
        val cmd = (if (sp < 0) t else t.substring(0, sp)).lowercase(Locale.ROOT)
        val arg = if (sp < 0) "" else t.substring(sp + 1).trim()
        val c: Context = svc

        try {
            when (cmd) {
                "buka", "open" -> reply(open(c, arg))
                "cari", "search" -> {
                    if (arg.isEmpty()) return reply("Mau cari apa? contoh: cari resep nasi goreng")
                    view(c, "https://www.google.com/search?q=" + URLEncoder.encode(arg, "UTF-8"))
                    reply("🔎 Mencari \"$arg\"")
                }
                "telepon", "telpon", "tlp", "call" -> reply(call(c, arg))
                "wa", "whatsapp" -> reply(wa(c, arg))
                "senter" -> reply(torch(c, arg))
                "volume" -> reply(volume(c, arg))
                "scroll" -> acc(reply) { a ->
                    val down = !(arg.startsWith("up") || arg.startsWith("atas"))
                    a.scroll(down); reply(if (down) "⬇️ Scroll ke bawah" else "⬆️ Scroll ke atas")
                }
                "screenshot", "ss" -> acc(reply) { a ->
                    svc.hideForShot()
                    reply("📸 Screenshot...")
                    OverlayService.handler.postDelayed({ a.global(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT) }, 450)
                }
                "ketik", "type" -> acc(reply) { a ->
                    if (arg.isEmpty()) reply("Mau ketik apa? contoh: ketik halo dunia")
                    else reply(if (a.typeText(arg)) "⌨️ Terketik: $arg" else "❌ Tidak ada kolom teks yang aktif. Tap kolom teks dulu.")
                }
                "tap", "klik", "ketuk" -> acc(reply) { a ->
                    if (arg.isEmpty()) reply("Tap apa? contoh: tap login")
                    else reply(if (a.tapText(arg)) "👆 Tap \"$arg\"" else "❌ Tulisan \"$arg\" tidak ditemukan di layar")
                }
                "kembali", "back" -> acc(reply) { a -> a.global(AccessibilityService.GLOBAL_ACTION_BACK); reply("↩️ Kembali") }
                "home" -> acc(reply) { a -> a.global(AccessibilityService.GLOBAL_ACTION_HOME); reply("🏠 Home") }
                "recent", "recents" -> acc(reply) { a -> a.global(AccessibilityService.GLOBAL_ACTION_RECENTS); reply("🗂️ Recent apps") }
                "tidur", "kunci", "lock" -> acc(reply) { a -> a.global(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN); reply("😴 Layar dikunci") }
                "catat", "note" -> reply(note(c, arg))
                "catatan", "notes" -> reply(notes(c))
                "baterai", "battery" -> reply(battery(c))
                "status" -> reply(status(c))
                "bersihkan", "persihkan", "clean" -> clean(c, reply)
                "menu", "bantuan", "help" -> reply(HELP)
                "tutup", "keluar", "exit" -> { reply("👋 Sampai jumpa!"); OverlayService.handler.postDelayed({ svc.stopSelf() }, 700) }
                else -> reply("🤔 Perintah \"$cmd\" belum aku kenal. Ketik: menu")
            }
        } catch (e: Exception) {
            reply("⚠️ Gagal: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun acc(reply: (String) -> Unit, block: (MonvAccessibilityService) -> Unit) {
        val a = MonvAccessibilityService.inst
        if (a == null) reply("⚠️ Aktifkan izin Aksesibilitas Monv dulu (buka app Monv)") else block(a)
    }

    private fun start(c: Context, i: Intent) { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); c.startActivity(i) }
    private fun view(c: Context, url: String) = start(c, Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    // ---------- buka app / web ----------
    private fun open(c: Context, q0: String): String {
        val q = q0.trim().lowercase(Locale.ROOT)
        if (q.isEmpty()) return "Buka apa? contoh: buka tiktok"
        if (q.contains('.') && !q.contains(' ')) {
            view(c, if (q.startsWith("http")) q else "https://$q"); return "🌐 Membuka $q"
        }
        if (q == "kamera" || q == "camera") {
            return try { start(c, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)); "📷 Kamera" }
            catch (e: ActivityNotFoundException) { "❌ Kamera tidak ditemukan" }
        }
        val pm = c.packageManager
        val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
        for (key in (alias[q] ?: listOf(q))) {
            val hit = apps.firstOrNull { it.first.equals(key, true) }
                ?: apps.filter { it.first.contains(key, true) }.minByOrNull { it.first.length }
                ?: apps.filter { key.contains(it.first, true) && it.first.length > 2 }.maxByOrNull { it.first.length }
            if (hit != null) {
                val i = pm.getLaunchIntentForPackage(hit.second) ?: continue
                start(c, i); return "🚀 Membuka ${hit.first}"
            }
        }
        return "❌ Aplikasi \"$q0\" tidak ketemu"
    }

    // ---------- telepon / wa ----------
    private fun findNumber(c: Context, q: String): String? {
        val digits = q.filter { it.isDigit() || it == '+' }
        if (digits.length >= 5 && q.all { it.isDigit() || it in "+ -()" }) return digits
        if (c.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        c.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?", arrayOf("%$q%"), null
        )?.use { if (it.moveToFirst()) return it.getString(0) }
        return null
    }

    private fun call(c: Context, q: String): String {
        if (q.isEmpty()) return "Telepon siapa? contoh: telepon ibu"
        val num = findNumber(c, q) ?: return "❌ Kontak \"$q\" tidak ketemu (cek izin Kontak)"
        val granted = c.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        start(c, Intent(if (granted) Intent.ACTION_CALL else Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(num))))
        return "📞 Menelepon $q"
    }

    private fun wa(c: Context, arg: String): String {
        if (arg.isEmpty()) return "WA siapa? contoh: wa ibu"
        val parts = arg.split(" pesan ", limit = 2)
        val who = parts[0].trim()
        val num = findNumber(c, who) ?: return "❌ Kontak \"$who\" tidak ketemu (cek izin Kontak)"
        var n = num.filter { it.isDigit() }
        if (n.startsWith("0")) n = "62" + n.substring(1)   // asumsi Indonesia
        val text = if (parts.size > 1) "?text=" + URLEncoder.encode(parts[1], "UTF-8") else ""
        view(c, "https://wa.me/$n$text")
        return "💬 Membuka WhatsApp $who"
    }

    // ---------- hardware ----------
    private fun torch(c: Context, arg: String): String {
        val cm = c.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull {
            val ch = cm.getCameraCharacteristics(it)
            ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                ch.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
        ?: return "❌ HP ini tidak punya senter"
        val a = arg.lowercase(Locale.ROOT)
        torchOn = when {
            a in listOf("on", "nyala", "hidup", "hidupkan", "nyalakan") -> true
            a in listOf("off", "mati", "matikan") -> false
            else -> !torchOn
        }
        cm.setTorchMode(id, torchOn)
        return if (torchOn) "🔦 Senter nyala" else "🔦 Senter mati"
    }

    private fun volume(c: Context, arg: String): String {
        val am = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val a = arg.lowercase(Locale.ROOT)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        when {
            a.startsWith("naik") || a == "up" || a == "+" -> am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            a.startsWith("turun") || a == "down" || a == "-" -> am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            a == "max" || a == "penuh" -> am.setStreamVolume(AudioManager.STREAM_MUSIC, max, AudioManager.FLAG_SHOW_UI)
            a == "mute" || a == "senyap" -> am.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_SHOW_UI)
            else -> return "Contoh: volume naik / turun / max / mute"
        }
        return "🔊 Volume ${am.getStreamVolume(AudioManager.STREAM_MUSIC)}/$max"
    }

    private fun batteryInfo(c: Context): String {
        val i = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val lv = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val sc = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val st = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val ch = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
        return "${if (sc > 0) lv * 100 / sc else lv}%" + if (ch) " (mengisi)" else ""
    }

    private fun battery(c: Context) = "🔋 Baterai ${batteryInfo(c)}"

    private fun status(c: Context): String {
        val jam = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val a = if (MonvAccessibilityService.inst != null) "aktif ✅" else "mati ❌"
        return "🤖 Monv online • $jam\n🔋 ${batteryInfo(c)} • Aksesibilitas $a"
    }

    // ---------- catatan ----------
    private fun note(c: Context, t: String): String {
        if (t.isEmpty()) return "Catat apa? contoh: catat beli susu"
        val p = c.getSharedPreferences("monv", Context.MODE_PRIVATE)
        val line = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date()) + "  " + t
        p.edit().putString("notes", (p.getString("notes", "") ?: "") + line + "\n").apply()
        return "📝 Tercatat"
    }

    private fun notes(c: Context): String {
        val all = (c.getSharedPreferences("monv", Context.MODE_PRIVATE).getString("notes", "") ?: "").trim()
        return if (all.isEmpty()) "📝 Belum ada catatan" else "📝 " + all.lines().takeLast(4).joinToString("\n")
    }

    // ---------- bersihkan ----------
    private fun clean(c: Context, reply: (String) -> Unit) {
        reply("🧹 Membersihkan...")
        Thread {
            val am = c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            var n = 0
            for (a in c.packageManager.getInstalledApplications(0)) {
                if (a.packageName != c.packageName && (a.flags and ApplicationInfo.FLAG_SYSTEM) == 0) {
                    am.killBackgroundProcesses(a.packageName); n++
                }
            }
            OverlayService.handler.post { reply("🧹 Selesai: $n aplikasi dihentikan dari background") }
        }.start()
    }
}
