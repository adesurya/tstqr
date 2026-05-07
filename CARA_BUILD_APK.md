# TemanQRIS Listener - Build APK via GitHub Actions

Panduan lengkap untuk build APK tanpa install software apapun di laptop Anda. Total waktu: ~10 menit.

## Apa yang Anda Butuhkan

1. **Akun GitHub** (gratis) - daftar di https://github.com/signup kalau belum punya
2. **Browser** (Chrome/Firefox/Safari)
3. **HP Android** untuk install APK nanti
4. Tidak perlu install Android Studio, Java, atau apapun

---

## Step 1: Buat Repository Baru di GitHub

1. Buka https://github.com/new
2. Isi field:
   - **Repository name**: `temanqris-listener` (atau nama lain terserah)
   - **Description**: bebas
   - Pilih **Private** (supaya code Anda tidak public)
   - JANGAN centang "Add a README file"
3. Klik tombol hijau **Create repository**

GitHub akan kasih tampilan kosong dengan instruksi. Abaikan dulu, lanjut ke Step 2.

---

## Step 2: Upload Project Files

Cara termudah tanpa install Git:

### Cara A: Upload via Browser (Paling Mudah)

1. Di halaman repo yang baru dibuat, klik link **"uploading an existing file"**
   - Atau buka URL: `https://github.com/USERNAME/temanqris-listener/upload/main`
2. **Drag & drop SEMUA folder dan file** dari folder project ini ke browser
   - PENTING: pastikan struktur folder ikut ter-upload (folder `app/`, `.github/`, dll)
   - Browser modern support drag-drop folder secara recursive
3. Scroll ke bawah, di field **"Commit changes"** ketik: `Initial commit`
4. Klik tombol hijau **Commit changes**

### Cara B: Pakai GitHub Desktop (Lebih Mudah Update Selanjutnya)

1. Download GitHub Desktop di https://desktop.github.com (gratis)
2. Install dan login dengan akun GitHub Anda
3. Klik **File → Clone Repository**, pilih repo yang baru dibuat
4. Copy semua file dari folder project ini ke folder repo yang ter-clone
5. Di GitHub Desktop, isi commit message `Initial commit` lalu klik **Commit to main**
6. Klik **Push origin** untuk upload ke GitHub

---

## Step 3: Tunggu APK Ter-Build Otomatis

Begitu Anda commit, GitHub Actions akan langsung mulai build. Cara cek progress-nya:

1. Buka repo Anda di GitHub
2. Klik tab **Actions** (di bagian atas, sebelahan dengan "Code", "Issues", dll)
3. Anda akan lihat ada workflow run baru bernama **"Build Android APK"** dengan icon kuning (artinya sedang berjalan)
4. Klik workflow run tersebut
5. Klik job **build** untuk lihat detail
6. Tunggu sekitar **3-5 menit** sampai semua step jadi centang hijau

Kalau ada step yang error (icon merah), klik step tersebut untuk lihat error message. Beritahu saya error-nya, akan saya bantu fix.

---

## Step 4: Download APK

Setelah build selesai (semua step hijau):

1. Di halaman workflow run yang sukses, scroll ke paling bawah
2. Cari section **Artifacts**
3. Klik nama artifact **TemanQrisListener-debug**
4. Browser akan otomatis download file ZIP
5. **Extract ZIP** tersebut, di dalamnya ada file `.apk`

---

## Step 5: Install APK ke HP Android

1. Transfer file `.apk` ke HP Anda (via Telegram/WhatsApp/USB cable/Google Drive)
2. Di HP, buka file `.apk`
3. Pertama kali biasanya muncul warning **"Install unknown apps"** - klik **Settings**
4. Aktifkan toggle **"Allow from this source"**
5. Kembali, lalu klik **Install**
6. Selesai - app **TemanQRIS Listener** akan muncul di app drawer

---

## Step 6: Test App

1. Buka app TemanQRIS Listener
2. Untuk testing dengan mock server lokal:
   - Pastikan laptop dan HP terhubung WiFi yang sama
   - Cari IP laptop Anda (Mac: `ifconfig | grep inet`, Windows: `ipconfig`)
   - Isi **Server URL**: `http://IP_LAPTOP:3000` (contoh: `http://192.168.1.5:3000`)
   - Isi **Device Secret**: `demo_secret_replace_in_prod`
   - Klik **Simpan Konfigurasi**
3. Klik **Grant Notification Permission**
   - Di Settings yang terbuka, cari **TemanQRIS Listener**
   - Aktifkan toggle-nya
   - Confirm di popup yang muncul
4. Kembali ke app, klik **Aktifkan Listener**
5. App siap menangkap notifikasi dari Gojek/DANA/OVO

---

## Update App Selanjutnya

Setiap kali ada perubahan code:

**Cara A (browser)**: Edit file di GitHub web, klik commit. APK baru otomatis ter-build.

**Cara B (GitHub Desktop)**: Edit file di laptop, commit & push via GitHub Desktop.

APK terbaru selalu ada di tab **Actions → workflow run terbaru → Artifacts**.

---

## Troubleshooting

### Build error "Cannot find package"
Pastikan struktur folder ter-upload lengkap, terutama folder `.github/workflows/`

### Build error "compileSdk not specified"
File `app/build.gradle` mungkin tidak ter-upload. Cek di GitHub apakah file ada.

### Build sukses tapi APK tidak bisa di-install
Hapus dulu app lain dengan package name sama, atau di Settings → Apps cari "TemanQRIS" dan uninstall.

### App crash saat dibuka
Buka **Logcat** dengan tools seperti **Logcat Reader** dari Play Store untuk lihat error. Atau kirim screenshot crash ke saya.

### Notifikasi tidak ke-capture
- Pastikan permission Notification Access sudah di-grant
- Pastikan listener "Aktifkan" (tombol hijau)
- Cek di tab Log apakah ada notifikasi yang ter-skip
- Format notifikasi mungkin berbeda dari yang saya assume - perlu adjust regex di parser

---

## Catatan Penting

**APK ini adalah DEBUG build** - artinya:
- Bisa di-install tanpa signing key resmi
- Performance sedikit lebih lambat dari release build
- Tidak bisa di-upload ke Play Store langsung
- Bagus untuk testing internal dan POC

Untuk **release ke Play Store** nanti, kita perlu:
1. Generate signing keystore (1 kali setup)
2. Upload keystore sebagai GitHub Secret
3. Tambah workflow untuk build release APK

Saya bisa bantu setup ini kalau Anda sudah siap launch.
