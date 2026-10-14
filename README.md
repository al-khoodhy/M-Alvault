# AlVault — brankas akun/password untuk Android

Aplikasi Android (Kotlin, Jetpack Compose, Material 3) yang mengikuti dokumen teknis *AlVault Android Architecture & Technical Specification*: Zero-Knowledge, local-first (offline), impor/ekspor `.aegis` yang kompatibel dengan versi web, dan sinkronisasi Supabase opsional.

## Status verifikasi (baca dulu)

| Bagian | Status |
| :--- | :--- |
| Lapisan inti (`core/`: kripto, `.aegis`, vault blob, akun, sesi/auto-lock, sinkronisasi, audit HIBP, generator, logika UI) | **Dikompilasi dan diuji di JVM: 122 tes lulus.** Termasuk impor berkas `.aegis` yang dibuat dengan WebCrypto (Node.js, 600.000 iterasi sungguhan) dan known-answer Argon2id. |
| Lapisan Android (layar Compose, Room, EncryptedSharedPreferences, BiometricPrompt, clipboard, Argon2 native) | **Belum pernah dikompilasi dan belum dijalankan di perangkat/emulator.** Lingkungan tempat kode ini ditulis tidak bisa mengakses Android SDK maupun Google Maven. Kode ditulis cermat terhadap versi pustaka di `gradle/libs.versions.toml`, tetapi kemungkinan masih ada galat kompilasi kecil (nama parameter, import) yang baru muncul saat build pertama. |

Jadi langkah pertama Anda adalah build; lihat di bawah. Bila ada galat kompilasi, kirimkan pesan galatnya agar bisa diperbaiki.

## Cara membangun APK

**A. Android Studio (paling mudah)**
1. Instal Android Studio versi terbaru (JDK 17+ sudah termasuk).
2. *File → Open* → pilih folder proyek ini → tunggu Gradle sync.
3. *Build → Build APK(s)*, atau di terminal: `./gradlew assembleDebug`.
4. APK ada di `app/build/outputs/apk/debug/app-debug.apk`. Salin ke HP, izinkan "instal dari sumber tidak dikenal", lalu instal.

**B. GitHub Actions (tanpa Android Studio)**
Unggah folder ini ke repositori GitHub milik Anda. Workflow `.github/workflows/android.yml` menjalankan semua unit test lalu membuat APK debug, yang bisa diunduh dari tab *Actions → Artifacts → alvault-debug-apk*.

**C. Hanya menjalankan tes inti (tanpa Android SDK)**
`./gradlew testDebugUnitTest` memerlukan Android SDK karena modulnya modul Android. Tes inti sendiri murni JVM.

APK debug ditandatangani dengan kunci debug; cukup untuk dipakai pribadi. Untuk rilis, buat keystore sendiri dan tambahkan `signingConfigs` (jangan mengunggah keystore ke repositori).

## Pemetaan spesifikasi → kode

| Bagian dokumen | Implementasi |
| :--- | :--- |
| 2A `.aegis` (PBKDF2-HMAC-SHA256 600.000, salt 16B, AES-256-GCM, IV 12B, tag 128 bit, JSON `{v,s,i,c}`) | `core/crypto/AegisCrypto`, `AegisFile`, `Pbkdf2`, `AesGcm`; alur di `core/backup/BackupService` |
| 2B Vault blob `Base64(IV+Ciphertext+Tag)` + regex validasi | `core/crypto/VaultBlob`, `Base64Codec` |
| 2C Argon2id 64 MB / 3 iterasi / paralelisme 1 / 64 byte → `EncryptionKey` (0..31) + `LocalAuthHash` (32..63) | `core/crypto/MasterKeyDeriver`; Argon2 native lewat `Argon2ktHasher` |
| 3 `VaultItem` | `core/model/VaultItem`, `VaultJson` (toleran terhadap field tak dikenal) |
| 4A Registrasi lokal, verifier `SHA3-512(LocalAuthHash + email + localPepper)` | `core/auth/AccountService`, `core/crypto/LocalVerifier`, `data/SecurePrefs` |
| 4B Login master password / biometrik (Keystore + `setUserAuthenticationRequired(true)`) | `AccountService.unlock`, `platform/BiometricKeyVault` |
| 5 Supabase (toggle, URL, key, Test Connection, Push/Pull) | `core/sync/SupabaseClient`, `SyncService`, layar Pengaturan |
| 6A Generator (8–42, default 16, rejection sampling CSPRNG) | `core/security/PasswordGenerator` |
| 6B Audit (cek lemah lokal + HIBP k-anonymity) | `core/security/SecurityAuditor`, `Hibp`, `PasswordStrength` |
| 6C `FLAG_SECURE`, auto-lock 60 dtk, clipboard dibersihkan 30 dtk | `MainActivity`, `core/session/*`, `platform/ClipboardCleaner` |
| 7 Stack (Compose + Material 3, Room, argon2kt, BouncyCastle, OkHttp, biometric) | `app/build.gradle.kts`, `gradle/libs.versions.toml` |

Penyimpanan brankas: satu baris Room (`vault_blob`) berisi vault blob terenkripsi, persis format kolom `encrypted_data` di Supabase. `EncryptionKey` hanya ada di RAM (`SessionManager`) dan dihapus saat terkunci.

## Keputusan dan penyimpangan dari dokumen

Dokumen tidak mengunci beberapa hal, dan satu-dua hal tidak bisa diikuti persis. Semuanya tercatat di sini supaya bisa Anda koreksi.

1. **Upsert Supabase memakai `?on_conflict=user_email`.** Dokumen menyebut `POST /rest/v1/user_vaults` dengan `Prefer: resolution=merge-duplicates`. Tanpa `on_conflict`, PostgREST memakai primary key (`id`), sehingga Push kedua gagal 409 karena `user_email` bersifat UNIQUE.
2. **`bcprov-jdk18on`, bukan `bcprov-jdk15on`.** Artefak `jdk15on` sudah tidak dirawat (versi terakhir 1.70). Dipakai hanya untuk SHA3-512.
3. **Salt Argon2id = UTF-8(`"{email}:{localSalt}"`).** Dokumen menulis `"email:{localSalt}"`; saya membacanya sebagai nilai email (huruf kecil, tanpa spasi tepi), dan `localSalt` sebagai string Base64 dari 16 byte acak. **Cek ini terhadap aplikasi web Anda.** Ini hanya memengaruhi Pull dari Supabase yang dienkripsi oleh aplikasi web; impor/ekspor `.aegis` tidak terpengaruh.
4. **`localPepper`** tidak didefinisikan di dokumen. Dipakai konstanta aplikasi `alvault.local.pepper.v1` (`LocalVerifier.PEPPER`).
5. **Simbol tambahan** pada dokumen ditulis "(28)" padahal daftar yang tertulis berisi 23 karakter. Kode memakai daftar yang tertulis.
6. **MIME dialog simpan `.aegis` = `application/octet-stream`**, bukan `application/json`. Penyedia dokumen Android menambahkan ekstensi menurut MIME, sehingga `x.aegis` bisa jadi `x.aegis.json`. Isi berkas tetap JSON; dialog impor menerima semua berkas.
7. **Sinkronisasi antarperangkat butuh `localSalt` yang sama.** Karena `localSalt` acak per perangkat, brankas di perangkat lain tidak bisa didekripsi dari cloud (kunci berbeda); aplikasi menampilkan pesan yang menjelaskan hal ini. Untuk pindah perangkat gunakan backup `.aegis`.
8. **Konflik sinkronisasi dideteksi lewat `revision`**: Push berhenti bila cloud punya revisi yang belum ditarik (konfirmasi untuk menimpa); Pull menawarkan Gabungkan/Ganti bila lokal dan cloud sama-sama berubah.

Tambahan di luar dokumen: jeda login setelah 4 kali salah password (30 dtk, berlipat, maks 15 mnt), autofill sistem dimatikan agar master password tidak ditawarkan untuk disimpan, backup/transfer perangkat Android dimatikan untuk data aplikasi, konfirmasi ketik "HAPUS" sebelum menghapus semua data, dan persetujuan "master password tidak bisa dipulihkan" saat registrasi.

`minSdk` 28 (Android 9), `targetSdk` 35.

## Daftar periksa saat pertama kali dijalankan di HP

1. Daftar dengan email + master password; pastikan masuk ke daftar kosong.
2. Tambah entri, kunci (tombol gembok), buka lagi dengan master password.
3. Aktifkan biometrik di Pengaturan, kunci, buka dengan sidik jari/wajah.
4. Salin password; cek clipboard terhapus sekitar 30 detik kemudian.
5. Coba screenshot: harus hitam/diblokir. Biarkan 60 detik di latar belakang: harus terkunci.
6. Ekspor `.aegis`, lalu impor berkas itu (mode Gabungkan). Impor juga berkas `.aegis` dari aplikasi web.
7. Audit: butuh internet; periksa hasil untuk password yang sengaja dibuat lemah.
8. (Opsional) Supabase: jalankan SQL dari layar Pengaturan di SQL Editor Supabase, isi URL + key, Test Connection, Push, lalu Pull.
