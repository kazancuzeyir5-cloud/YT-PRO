# 📱 YT-PRO — GitHub'dan APK Alma Rehberi

## ADIM 1 — GitHub Repo Hazırla

1. **github.com** adresine git ve giriş yap
2. Sağ üstteki **+** butonuna tıkla → **New repository**
3. Repository name: `YT-PRO`
4. **Public** seç (Actions ücretsiz çalışır)
5. **Create repository** tıkla

---

## ADIM 2 — Dosyaları Yükle

Repo sayfasında **uploading an existing file** linkine tıkla.

Şu klasör yapısını **olduğu gibi** yükle:

```
YT-PRO/
├── .github/
│   └── workflows/
│       └── build-apk.yml       ← ÇOK ÖNEMLİ
├── app/
│   ├── build.gradle
│   ├── proguard-rules.pro
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml
│           ├── assets/
│           │   └── index.html
│           ├── java/com/ytpro/app/
│           │   ├── MainActivity.java
│           │   └── DownloadService.java
│           └── res/
│               ├── layout/activity_main.xml
│               ├── values/themes.xml
│               ├── xml/network_security_config.xml
│               └── mipmap-*/ic_launcher.xml
├── build.gradle
├── gradle.properties
├── settings.gradle
└── gradle/wrapper/
    └── gradle-wrapper.properties
```

### Hızlı Yükleme Yöntemi:
1. Tüm klasörü ZIP olarak sıkıştır
2. GitHub'da **Code** → **Upload files** tıkla
3. ZIP'i sürükle bırak → **Commit changes**

---

## ADIM 3 — Actions'ı Etkinleştir

1. Repo sayfasında üstteki **Actions** sekmesine tıkla
2. **"I understand my workflows, go ahead and enable them"** butonuna tıkla

---

## ADIM 4 — İlk Derlemeyi Başlat

**Seçenek A — Otomatik:** Herhangi bir dosyayı düzenleyip kaydet (push), Actions otomatik başlar.

**Seçenek B — Manuel:**
1. **Actions** sekmesine git
2. Sol listede **Build YT-PRO APK** tıkla
3. Sağda **Run workflow** → **Run workflow** tıkla
4. Yeşil spinner döner, ~5-8 dakika bekle ☕

---

## ADIM 5 — APK'yı İndir

### Yöntem A — Artifacts (En kolay):
1. Actions sekmesinde tamamlanan işe tıkla ✓
2. En altta **Artifacts** bölümünde **YT-PRO-v2.4.1** tıkla
3. ZIP indirilir → içinden `app-debug.apk` çıkar

### Yöntem B — Releases:
1. Repo ana sayfasında sağ tarafta **Releases** tıkla
2. En son release'de `app-debug.apk` indir

---

## ADIM 6 — Telefona Kur (Xiaomi/Redmi)

### Bilinmeyen Kaynak İzni:
1. **Ayarlar** → **Ek Ayarlar** → **Gizlilik** 
2. **Bilinmeyen uygulama yükle** → Tarayıcı/Dosya Yöneticisi → **İzin Ver**

### Kurulum:
1. APK dosyasına tıkla
2. **Yükle** → **Aç**
3. YT-PRO ana ekranda görünür 🎉

---

## ⚠️ Önemli Notlar

| Konu | Açıklama |
|------|----------|
| **MIUI Güvenlik** | "Güvenli değil" uyarısı çıkarsa görmezden gel, devam et |
| **İndirme klasörü** | `/İndirilenler/YT-PRO/` klasörüne kaydeder |
| **İlk açılış** | Depolama ve bildirim izni ister, İzin Ver de |
| **Güncelleme** | Kodu değiştirip GitHub'a yükle, Actions yeni APK üretir |

---

## 🔧 Sorun Gidерme

**Actions başarısız olursa:**
- Actions sekmesinde kırmızı ✗ işarete tıkla
- Hata mesajını kopyalayıp Claude'a yapıştır, çözeceğim

**APK kurulmuyorsa:**
- Ayarlar → Güvenlik → Bilinmeyen kaynaklar → Aç

**Uygulama açılmıyorsa:**
- Uygulamayı kaldır, APK'yı tekrar kur

---

## 📦 Proje Boyutu

- APK boyutu: ~8-12 MB (yt-dlp dahil ~25 MB)
- Min Android: 8.0 (API 26)
- Hedef: Android 14 (API 34)
- Mimari: Universal (ARM64 + ARM)
