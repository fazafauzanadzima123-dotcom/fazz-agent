package com.fazz.agent.domain.models

/** Voice of Fazz's replies. */
enum class Tone(val id: String, val label: String, val hint: String) {
    NEUTRAL("netral", "Netral", ""),
    WARM("hangat", "Hangat", "Tone: warm, encouraging and personable."),
    DIRECT("tegas", "Tegas", "Tone: direct and matter-of-fact; say what matters, skip pleasantries."),
    ;
    companion object { fun from(id: String?) = entries.firstOrNull { it.id == id } ?: NEUTRAL }
}

enum class AnswerLength(val id: String, val label: String, val hint: String) {
    AUTO("otomatis", "Otomatis", ""),
    SHORT("singkat", "Singkat", "Length: keep answers short (a few sentences) unless the user asks for detail."),
    DETAILED("rinci", "Rinci", "Length: give thorough, well-structured answers with the reasoning and options."),
    ;
    companion object { fun from(id: String?) = entries.firstOrNull { it.id == id } ?: AUTO }
}

enum class EmojiLevel(val id: String, val label: String, val hint: String) {
    NONE("tanpa", "Tanpa emoji", "Emoji: never use emoji."),
    LIGHT("sedikit", "Sedikit", ""),
    MANY("banyak", "Banyak", "Emoji: use emoji generously to add warmth."),
    ;
    companion object { fun from(id: String?) = entries.firstOrNull { it.id == id } ?: LIGHT }
}

/** What the user told Fazz about themselves. Plain preferences; stored on the phone. */
data class Profile(
    val nickname: String = "",
    val about: String = "",
    val instructions: String = "",
    val style: ResponseStyle = ResponseStyle.NORMAL,
    val tone: Tone = Tone.NEUTRAL,
    val length: AnswerLength = AnswerLength.AUTO,
    val emoji: EmojiLevel = EmojiLevel.LIGHT,
) {
    companion object {
        const val MAX_NICKNAME = 40
        const val MAX_ABOUT = 600
        const val MAX_INSTRUCTIONS = 1200
    }
}

/**
 * A group of abilities the user can switch on or off. Anything switched off is removed from Fazz's toolbox,
 * so it cannot be used at all (not just discouraged).
 */
enum class PluginGroup(val id: String, val label: String, val description: String, val tools: Set<String>) {
    MESSAGING("pesan", "Pesan & telepon", "Kirim SMS, telepon, siapkan email, bagikan teks.", setOf("send_sms", "call_phone", "compose_email", "share_text")),
    CALENDAR("kalender", "Kalender", "Lihat jadwal dan buat acara.", setOf("read_calendar_events", "create_calendar_event")),
    CONTACTS("kontak", "Kontak", "Cari nama dan nomor di kontakmu.", setOf("search_contacts")),
    WEB("web", "Web", "Cari informasi terbaru dan buka tautan.", setOf("web_search", "open_url")),
    APPS("aplikasi", "Aplikasi & navigasi", "Buka aplikasi dan petunjuk arah.", setOf("open_app", "navigate_to")),
    SCREEN("layar", "Kontrol layar", "Mengetuk, mengetik, dan menggulir di aplikasi lain.", setOf("read_screen", "tap", "type_text", "scroll", "press_key", "swipe", "look_at_screen", "wait")),
    REMINDERS("pengingat", "Alarm & pengingat", "Alarm, timer, dan pengingat.", setOf("set_alarm", "set_timer", "set_reminder")),
    MEDIA("media", "Media", "Putar, jeda, dan lanjut lagu atau video.", setOf("media_control")),
    NOTIFICATIONS("notifikasi", "Notifikasi", "Merangkum notifikasi yang masuk.", setOf("read_notifications")),
    ARTIFACTS("artefak", "Artefak", "Membuat halaman, dokumen, dan kode yang bisa dibuka.", setOf("create_artifact")),
    GOOGLE("google", "Akun Google", "Kalender Google, Gmail, dan Google Docs (setelah akun dihubungkan).", GoogleToolMap.values.flatten().toSet()),
    MEMORY("memori", "Memori", "Menyimpan hal penting tentangmu setelah kamu setujui.", setOf("save_memory")),
    ;
    companion object { fun from(id: String) = entries.firstOrNull { it.id == id } }
}

/** Tools per Google service id (see GoogleService). */
val GoogleToolMap: Map<String, Set<String>> = mapOf(
    "kalender" to setOf("google_calendar_events", "google_calendar_create_event"),
    "gmail" to setOf("gmail_search", "gmail_read", "gmail_create_draft", "gmail_send"),
    "drive" to setOf("drive_save_document"),
)

/** Tool name of the location ability; it is controlled by the Lokasi switch rather than a plugin toggle. */
const val LOCATION_TOOL = "get_location"
