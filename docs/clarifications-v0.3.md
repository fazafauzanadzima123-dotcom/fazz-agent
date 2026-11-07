# Clarifications — Fazz Agent v0.3.0 (2026-11-07)

## Tujuan
Fazz Agent menjadi agen **otonom** yang bisa mengoperasikan aplikasi lain di HP, setara (sebisa mungkin di perangkat) dengan Claude Duet, Grok Bot, dan OpenAI dots; tampilan memakai **layout & interaksi aplikasi Claude**, identitas tetap Fazz.

## Keputusan
| Topik | Keputusan |
|---|---|
| Otonomi | Kontrol layar penuh (AccessibilityService) + integrasi langsung. Hanya sideload (melanggar kebijakan Play untuk app non-aksesibilitas). |
| Otak agen | Hybrid: struktur layar (accessibility tree) + function calling di gemini-3.5-flash-lite (gratis); screenshot (AccessibilityService.takeScreenshot) + vision bila perlu; Gemini Computer Use dipakai jika key punya kuota (dites). |
| Cakupan tugas | Pesan (WA/SMS/Telegram), telepon, email, navigasi, alarm, pengingat, musik, belanja/ojol/sosmed |
| Persetujuan | Fazz jalan sendiri untuk buka/baca/ketik/scroll; **berhenti & minta izin** sebelum aksi tak bisa dibatalkan: kirim, telepon, posting, checkout/bayar, hapus. Tombol STOP selalu terlihat saat Fazz mengontrol layar. |
| Proaktif | Briefing pagi (jam bisa diatur), ringkas & prioritaskan notifikasi (NotificationListener, opt-in), tugas di latar dengan notifikasi progres/selesai/butuh izin |
| Tampilan | Layout persis Claude: drawer riwayat chat (multi-percakapan + cari), header dengan menu/pemilih model/chat baru, greeting tengah, composer dengan "+" (foto/kamera), mic, mode suara layar penuh, jawaban tanpa gelembung + aksi salin/ulangi/bacakan. Logo, nama, warna (krem + nila), font tetap Fazz. Tidak memakai aset merek Anthropic. |
| Web search | Tavily API (secret Supabase `TAVILY_API_KEY`) |
| Wake word | Tidak di v0.3 |
| Pengujian | HP user via kabel USB + USB debugging; Claude install & baca log |
| Rilis | Otomatis: GitHub Release v0.3.0 + update Vercel setelah teruji |

## API & izin yang dibutuhkan (jawaban "butuh API apa saja")
**Layanan eksternal (key/akun):**
1. **Google Gemini API** — sudah ada (`GEMINI_API_KEY`). Function calling + vision. Computer Use opsional (kemungkinan berbayar).
2. **Tavily Search API** — BARU, user daftar di tavily.com (gratis 1.000/bulan) → secret `TAVILY_API_KEY`.
3. **Supabase** — sudah ada (Edge Function + database).

**API Android (gratis, butuh izin user di HP):**
- AccessibilityService — baca layar, ketuk, ketik, scroll, back/home, screenshot (Android 11+)
- NotificationListenerService — baca notifikasi (akses khusus)
- SEND_SMS, CALL_PHONE, READ_CONTACTS, READ/WRITE_CALENDAR, POST_NOTIFICATIONS
- Foreground Service (tipe specialUse/dataSync) — tugas agen di latar
- WorkManager — briefing pagi & pengingat
- AudioManager media keys — kontrol musik
- Intent: Maps, email, alarm/timer, share

## Batasan jujur
- Agen berjalan di HP (bukan cloud 24/7 seperti Duet/dots): HP harus menyala & terhubung internet.
- Kontrol layar bisa gagal di aplikasi yang memblokir accessibility (beberapa aplikasi bank/keamanan) atau layar tanpa label.
- Tidak menyentuh pembayaran/transfer uang secara otonom; checkout selalu butuh persetujuan.
- Google Play: fitur kontrol layar otonom tidak diizinkan → tetap distribusi APK langsung.

## Perubahan (permintaan lanjutan, 2026-11-07)
| Topik | Keputusan |
|---|---|
| Provider AI | **OpenAI utama**, Gemini cadangan otomatis (server menerjemahkan format; app tidak tahu provider) |
| Key OpenAI | User **membuat key baru** (key lama sempat tertulis di chat → dianggap bocor) dan menyimpannya sebagai secret Supabase `OPENAI_API_KEY`. Key tidak pernah masuk kode/APK/GitHub. |
| Model | Dipilih Claude setelah mengecek model yang tersedia di key user (agen: function calling + gambar, biaya wajar) |
| Sembunyikan model | Di semua layar user: chat, header, pengaturan, website, README. Pemilih model & hitungan token dihapus. Pengungkapan privasi tetap jujur ("dikirim ke penyedia AI"). |

## Didelegasikan ke Claude
Detail UI, struktur kode, kata kunci aksi tak-terbatalkan, desain overlay STOP, format briefing.
