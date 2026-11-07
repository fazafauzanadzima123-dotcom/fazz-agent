# Clarifications — Fazz Agent (2026-11-07)

## Keputusan utama
| Topik | Keputusan |
|---|---|
| Nama produk | **Fazz Agent** (asisten menyebut dirinya "Fazz"); semua "Orbit" diganti |
| Package ID | `com.orbit.agent` → `com.fazz.agent` (APK lama harus di-uninstall) |
| Frontend | Website (Vercel) **dan** UI app Android di-redesign |
| Gaya visual | **Krem (cream) clean minimal**; app: light krem default + ikut dark mode sistem |
| Bahasa UI | Website & app: Bahasa Indonesia penuh (istilah teknis tetap English) |
| Bahasa chat dengan user | ±60% Indonesia / 40% English |
| GitHub | Public repo `fazz-agent`, license MIT, README dua bahasa (Indonesia + English), APK di GitHub Release v0.2.0 |
| Vercel | Project baru `fazz-agent`; project lama `orbit-agent` dihapus (semua serba Fazz Agent) |

## Fakta terverifikasi
- `gh` CLI terpasang tapi **belum login** → user perlu menjalankan `! gh auth login` (satu kali).
- Vercel CLI sudah login sebagai `fazafauzanadzima123-9292`.
- Gemini key aktif; `gemini-3.5-flash-lite` punya kuota gratis (dites live 2026-11-07).
- Secrets tidak masuk git: `secrets.properties`, `local.properties`, `web/.env.local` ter-ignore.

## Didelegasikan ke Claude
- Palet warna, tipografi, layout, animasi, copywriting Indonesia.
- Versi rilis: 0.2.0 (versionCode 3).
- Supabase project/function tetap (`ai-personal-agent` / `agent-chat`) — hanya teks persona di app yang berubah.

## Di luar cakupan
- Wake word, background listening, Play Store.
