# Build APK dengan Codemagic + GitLab

Panduan super detail untuk build APK tanpa GitHub. Total waktu: ~15 menit.

## Yang Anda Butuhkan

1. **Email aktif** (untuk daftar GitLab dan Codemagic)
2. **Browser**
3. **HP Android** untuk install APK

Tidak perlu install software apapun di laptop.

---

# BAGIAN 1: Upload Code ke GitLab

GitLab adalah alternatif GitHub yang gratis tanpa lock billing.

## Step 1.1: Daftar GitLab

1. Buka https://gitlab.com/users/sign_up
2. Isi: First name, Last name, Username (terserah), Email, Password
3. Klik **Register**
4. Buka email Anda, verifikasi dengan klik link konfirmasi
5. Setelah verified, login ke GitLab

**Catatan**: GitLab kadang minta verifikasi nomor HP atau credit card (anti-spam, tidak charge). Verifikasi dengan nomor HP biasanya cukup.

## Step 1.2: Buat Repository Baru

1. Setelah login, klik tombol **+** di pojok kanan atas
2. Pilih **New project/repository**
3. Pilih **Create blank project**
4. Isi:
   - **Project name**: `temanqris-listener`
   - **Visibility Level**: **Private** (kode Anda hanya bisa Anda lihat)
   - **JANGAN centang** "Initialize repository with a README"
5. Klik **Create project**

## Step 1.3: Upload File Project

GitLab akan kasih halaman dengan instruksi upload. Cara termudah:

1. Klik tombol **Upload File** atau ikon **+** di repo Anda
2. Atau klik link **"upload existing files"** di halaman repo
3. **Drag & drop semua file dan folder** dari folder project ke browser
   - PENTING: harus include folder `app/`, `.github/`, dan semua file di root
4. Tunggu upload selesai (~30 detik)
5. Di field **Commit message**, ketik: `Initial commit`
6. Klik **Upload files**

**TIPS upload folder**: 
- Chrome/Edge: bisa drag folder langsung
- Safari: harus zip dulu folder, atau upload file-per-file
- Kalau bermasalah, pakai GitLab Desktop atau Git command line

## Step 1.4: Verifikasi Upload

Pastikan struktur folder ter-upload dengan benar. Di halaman repo, Anda harus lihat:
- Folder `app/`
- Folder `.github/` (boleh ada, tidak akan dipakai)
- File `build.gradle`
- File `settings.gradle`
- File `codemagic.yaml` ← **WAJIB ADA, ini config buat Codemagic**
- dll

Kalau ada folder yang tidak ter-upload, klik tombol **+** untuk add folder/file lagi.

---

# BAGIAN 2: Connect Codemagic ke GitLab

## Step 2.1: Daftar Codemagic

1. Buka https://codemagic.io/signup
2. Klik tombol **Sign up with GitLab**
3. Login dengan akun GitLab Anda yang baru
4. Authorize Codemagic untuk akses repository Anda
5. Anda akan diarahkan ke Codemagic dashboard

**Free tier Codemagic**: 500 menit build/bulan untuk Android. Ini sudah lebih dari cukup untuk testing intensif POC.

## Step 2.2: Add Application

1. Di Codemagic dashboard, klik **Add application**
2. Pilih **GitLab**
3. Pilih repository **temanqris-listener** dari list
4. Klik **Finish: Add application**

## Step 2.3: Setup Build

Codemagic otomatis mendeteksi file `codemagic.yaml` di repo Anda.

1. Di halaman aplikasi, klik tab **Workflows**
2. Anda akan lihat workflow **"Android Debug APK Build"** terdeteksi otomatis
3. Klik tombol **Start your first build**

**Kalau tidak terdeteksi otomatis:**
1. Klik tab **Settings**
2. Di section **Build configuration**, pilih **Use codemagic.yaml**
3. Save settings
4. Kembali ke tab Workflows, klik **Start new build**

## Step 2.4: Tunggu Build Selesai

1. Build akan mulai, status berubah ke **In progress**
2. Anda bisa lihat log realtime di tab **Build details**
3. Tunggu **5-10 menit** (build pertama lebih lama karena perlu download dependencies)
4. Status berubah ke **Success** kalau berhasil, **Failed** kalau ada error

---

# BAGIAN 3: Download APK

## Step 3.1: Lewat Email (Otomatis)

Kalau Anda set email di `codemagic.yaml`, Codemagic akan kirim APK langsung ke email Anda. Cek inbox/spam.

## Step 3.2: Lewat Codemagic Dashboard

1. Di halaman build yang Success, scroll ke bawah
2. Cari section **Artifacts**
3. Klik nama file `.apk` untuk download
4. File APK akan ter-download ke laptop/HP Anda

## Step 3.3: Install ke HP Android

1. Transfer APK ke HP (via WhatsApp / Telegram / Google Drive / USB)
2. Di HP, buka file APK
3. Pertama kali muncul warning **"Install unknown apps"** → klik **Settings**
4. Aktifkan toggle **"Allow from this source"**
5. Kembali, klik **Install**
6. App **TemanQRIS Listener** muncul di app drawer

---

# BAGIAN 4: Update Code Selanjutnya

Setiap kali Anda edit code:

## Cara A: Edit Langsung di GitLab Web

1. Buka file di GitLab web interface
2. Klik tombol **Edit** (ikon pensil)
3. Edit, klik **Commit changes**
4. Codemagic otomatis trigger build baru
5. APK baru muncul di email/dashboard

## Cara B: Pakai GitLab Desktop atau Git CLI

Kalau familiar dengan Git, clone repo lokal, edit, commit, push. Auto-build seperti biasa.

---

# Troubleshooting

## Error: "codemagic.yaml not found"
Pastikan file `codemagic.yaml` ada di root folder repo (bukan di dalam folder `app/` atau lainnya).

## Build error "Could not resolve dependency"
Network issue di Codemagic. Klik **Restart build** di dashboard.

## Build error "compileSdkVersion not specified"
File `app/build.gradle` mungkin tidak ter-upload atau corrupt. Re-upload file ini.

## APK tidak bisa di-install
Hapus app sebelumnya yang punya package name sama: Settings → Apps → cari "TemanQRIS" → Uninstall. Lalu install APK baru.

## App crash saat dibuka
Install **Logcat Reader** dari Play Store untuk lihat detail crash. Screenshot error dan kirim ke saya.

## Email tidak dikirim
Edit `codemagic.yaml`, ganti `your-email@example.com` ke email Anda yang benar. Commit, build ulang.

---

# Catatan Penting

**APK yang dihasilkan adalah DEBUG build:**
- Bisa di-install tanpa keystore (cukup untuk testing)
- Tidak bisa di-upload ke Play Store langsung
- Performance sedikit lebih lambat dari release build
- Cocok untuk POC, demo internal, dan testing

**Untuk release ke Play Store nanti:**
- Generate signing keystore (1x setup)
- Upload keystore ke Codemagic (sebagai environment variable)
- Update `codemagic.yaml` untuk build release variant
- Saya bisa bantu saat Anda siap

---

# Kapan Pakai vs Skip Codemagic

**Pakai Codemagic kalau:**
- Mau auto-build setiap push (continuous deployment)
- Punya tim, perlu sharing APK
- Tidak mau install Android Studio

**Skip Codemagic kalau:**
- Hanya build sekali atau dua kali
- Mau iterasi cepat (build lokal lebih cepat)
- Mau full control development environment

Untuk POC ini, Codemagic sangat cocok karena Anda bisa fokus ke testing logic tanpa worry tools setup.
