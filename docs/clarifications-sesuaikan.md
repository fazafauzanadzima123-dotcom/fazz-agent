# Fazz Agent: fitur "Sesuaikan" + akun Google (klarifikasi)

Referensi: screenshot layar "Sesuaikan" aplikasi chat AI lain (hanya sebagai inspirasi tata letak; tampilan, warna, dan nama tetap milik Fazz: krem + nila).

## Keputusan pengguna
| Topik | Keputusan |
|---|---|
| Menu | Semua, kecuali **Dompet (dihapus)** |
| Kepribadian | Lengkap: nama panggilan, tentang saya/pekerjaan, instruksi khusus, gaya (sudah ada), nada, panjang jawaban, emoji |
| Plugin | Kemampuan bawaan dengan saklar hidup/mati (bukan plugin eksternal) |
| Email | Draf lewat aplikasi email (tanpa login) **dan** Gmail lewat akun Google |
| Lokasi | Izin + saklar "boleh pakai lokasiku" + alat `get_location` |
| Kata sandi | (asumsi) halaman penjelasan + tombol ke pengaturan isi otomatis Android; Fazz tidak menyimpan atau mengetik sandi |
| Letak menu | Menu geser: "Sesuaikan" di atas "Pengaturan"; **Memori** dan **Gaya jawaban** dipindah ke Sesuaikan |
| Akun | Opsional; tanpa akun tetap penuh |
| Google | Masuk dengan Google + Kalender + Gmail + Drive/Docs |
| Sinkron server | Hanya Sesuaikan: nama, instruksi, gaya, saklar plugin (bukan memori, bukan riwayat chat) |
| Kredensial Google | Saya buat lewat Chrome yang sudah login Google (project, layar izin mode pengujian, Client ID) |

## Rencana (dua tahap)
**Tahap 1: Sesuaikan (lokal, tanpa Google)**
- Layar `Sesuaikan` dengan grup kartu berikon: Kepribadian, Memori, Plugin | Akun terhubung (Google, Kalender, Kontak, Email) | Lokasi | Kata sandi.
- Kepribadian: tersimpan di HP, masuk ke instruksi sistem setiap chat (dibatasi panjang, disaring agar tidak menimpa aturan keamanan).
- Plugin: saklar per kelompok kemampuan; yang dimatikan dikeluarkan dari daftar alat dan dari instruksi.
- Lokasi: alat `get_location` (hanya jika izin + saklar aktif; lokasi tidak disimpan).
- Memori & Gaya jawaban dipindah dari Pengaturan.

**Tahap 2: Google**
- Masuk dengan Google (Credential Manager) lalu sesi Supabase Auth; tabel `user_prefs` dengan RLS per pengguna untuk sinkron Sesuaikan.
- Izin per layanan (Kalender, Gmail, Drive) diminta saat dihidupkan; token akses hanya di HP.
- Alat baru: Kalender Google (lihat/buat), Gmail (cari/ringkas, buat draf, kirim dengan persetujuan), Drive/Docs (simpan artefak).
- Kredensial: project Google Cloud, layar izin mode pengujian, Web Client ID + Android Client ID (package `com.fazz.agent` + SHA-1 kunci debug). Provider Google di Supabase.

## Batasan yang harus jelas
- Selama aplikasi Google berstatus "Pengujian", hanya akun yang didaftarkan sebagai penguji (akunmu) yang bisa masuk dan memakai Gmail/Kalender/Drive. Untuk umum perlu verifikasi Google (berminggu-minggu); bukan sesuatu yang bisa saya selesaikan sendiri.
- Kirim email, buat acara, dan simpan ke Drive tetap meminta persetujuan eksplisit.
- Tidak ada penyimpanan kata sandi, kartu, atau uang. Dompet tidak dibuat.
