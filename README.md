# Video Döngü 🎬🔒

Seçtiğiniz klasördeki videoları **karışık sırayla, sürekli döngüde** oynatan, **çocuk kilitli** bir Android uygulaması.
Eski tabletleri çocuklar için bir "video ekranına" dönüştürmek amacıyla yapıldı: tablet şarja takılınca video başlar,
çocuk kilidi aktifken çocuk uygulamadan çıkamaz, videoyu değiştiremez, ayarları kurcalayamaz.

Proje iki ayrı uygulamadan oluşur:

| Uygulama | Klasör | Android sürümü | Paket adı | İndir |
|---|---|---|---|---|
| **Video Döngü (Android 4+)** | `app_legacy/` | Android 4.4 KitKat ve üstü | `com.example.videoloop.legacy` | [Releases](../../releases) → `VideoDongu-Android4.4+.apk` |
| **Video Döngü** | `app/` | Android 8.0 Oreo ve üstü | `com.example.videoloop` | [Releases](../../releases) → `VideoDongu-Android8+.apk` |

> **Hangisini kurmalıyım?** Cihazınız Android 8 veya daha yeniyse **Android 8+** sürümünü kurun.
> Android 4.4 – 7.x cihazlar için **Android 4+** sürümünü kurun. İki uygulama aynı cihaza birlikte de kurulabilir.

---

## ✨ Özellikler

- **Sürekli oynatma:** Seçilen klasördeki tüm videolar karışık sırayla oynatılır; liste bitince yeniden karıştırılıp baştan başlar.
- **Desteklenen biçimler:** `.mp4`, `.mkv`, `.3gp`, `.webm`, `.mov`, `.avi` (cihazın oynatabildiği kodekler).
- **Açılışta otomatik başlama:** Cihaz açıldığında uygulama kendiliğinden açılır ve oynatmaya başlar.
- **Şarj davranışı:**
  - 🔌 Şarj takılınca ekran açılır ve video kaldığı yerden devam eder.
  - 🔋 Şarj çıkarılınca 30 saniyelik geri sayım başlar; süre dolunca video durur ve ekran kapanır (cihaz yöneticisi yetkisi gerekir).
- **Ekran açık kalır**, kilit ekranının üstünde de görüntülenir.
- **Güç tuşu:** Güç tuşuna kısa basılıp ekran kapatılınca video duraklar; ekran açılınca kaldığı yerden devam eder.
- **Kumanda / kulaklık desteği:** Bluetooth/USB kumanda veya kulaklıktaki medya tuşları
  (oynat/duraklat, sonraki, önceki, durdur) **çocuk kilidi aktifken de** çalışır.
- **Duraklatma kalıcıdır:** Duraklatılan video; kilitlendiğinde, ekran açıldığında veya pencere değiştiğinde kendiliğinden başlamaz.
- **🔒 Çocuk kilidi** (ayrıntılar aşağıda).
- **Kontroller (kilit açıkken):** oynat/duraklat, önceki/sonraki video, ilerleme çubuğu, ses ve parlaklık düğmeleri, klasör değiştirme.
- **Hareketler (kilit açıkken):** ekranın sol/sağ yarısına çift dokunma = önceki/sonraki video;
  sol yarıda yukarı-aşağı kaydırma = ses, sağ yarıda = parlaklık.
- **Sistem optimizasyonu (isteğe bağlı):** İlk açılışta sorulur; kabul edilirse arka plandaki gereksiz uygulamalar kapatılarak eski cihazlarda takılma azaltılır.

---

## 🔒 Çocuk Kilidi

Uygulama **kilitli başlar**. Kilit aktifken:

- Video **tam ekrandır ve her şeyin üstünde durur**; küçülmez, üzerine hiçbir kontrol gelmez.
- Ekrana yapılan dokunmalar, kaydırmalar ve çift dokunmalar **hiçbir şey yapmaz** (sadece "Çocuk Kilidi AKTİF" yazısı görünür).
- **Geri ◁**, **Ana Ekran ○** ve **Son Uygulamalar ▢** tuşları videoyu kapatamaz.
- Bildirim paneli kapatılır.
- Kumandanın medya tuşları ve ses tuşları çalışmaya devam eder.

### Kilidi açmak
Ekrana **art arda 5 kez** hızlıca dokunun (dokunuşlar arası en fazla yarım saniye). Kilit açılınca kontroller görünür.
Birden fazla parmakla dokunma sayılmaz, böylece çocuğun avuç içiyle basması kilidi açmaz.

### Tekrar kilitlemek
Kilit açıkken sağ üstteki **"🔓 Kilitle"** düğmesine dokunun (kontroller gizliyse önce bir kez ekrana dokunup gösterin)
ya da ekrana art arda 5 kez dokunun.

### Otomatik kilit
Kilit açıkken ekrana veya kumandaya **30 saniye** dokunulmazsa çocuk kilidi kendiliğinden devreye girer
("30 sn dokunulmadı" yazısı görünür). Her dokunuş ya da tuş bu süreyi yeniden başlatır.
Klasör seçici veya başka bir pencere açıkken otomatik kilit beklemeye alınır.

### Nasıl çalışıyor?
| | Android 4+ sürümü | Android 8+ sürümü |
|---|---|---|
| Tam ekran üst pencere | ✅ Tüm uygulamaların, ana ekranın ve Son Uygulamalar'ın üstünde | ✅ Tüm uygulamaların üstünde |
| Ekran sabitleme (Screen pinning) | — (Android 4.4'te yok) | ✅ Ana Ekran, Son Uygulamalar ve bildirim panelini kapatır |
| Gereken izin | Kurulumda otomatik verilir | "Diğer uygulamaların üzerinde göster" (uygulama ilk açılışta ister) |

---

## 📲 Kurulum

1. Bu sayfanın sağındaki **Releases** bölümünden cihazınıza uygun APK'yı indirin.
2. Cihazda **bilinmeyen kaynaklardan uygulama yüklemeye** izin verin
   (Android 8+: indirme yaptığınız uygulama için *Ayarlar → Uygulamalar → Özel uygulama erişimi → Bilinmeyen uygulamaları yükle*;
   Android 4.4: *Ayarlar → Güvenlik → Bilinmeyen kaynaklar*).
3. APK'yı açıp yükleyin.

> APK'lar Android'in hata ayıklama (debug) anahtarıyla imzalıdır. Google Play'de değildir; Play Protect bir uyarı gösterebilir.

### İlk açılışta
1. **Sistem optimizasyonu** sorusuna Evet/Hayır deyin.
2. **Cihaz yöneticisi** yetkisini etkinleştirin (şarj çıkarılınca ekranı kapatabilmek için).
3. **Android 8+:** "Diğer uygulamaların üzerinde göster" iznini açın (tam ekran çocuk kilidi için).
4. **Video klasörünü seçin** (kilit bu aşamada devreye girmez, klasörü rahatça seçebilirsiniz).
5. **Android 8+:** İlk kilitlemede Android "Uygulama sabitlendi" bilgisini gösterir; **Anladım**'a dokunun.

Bu adımlardan sonra videolar oynamaya başlar ve çocuk kilidi aktif olur.

---

## 🔑 İzinler

| İzin | Neden |
|---|---|
| Cihaz yöneticisi (`BIND_DEVICE_ADMIN`, `force-lock`) | Şarj çıkarılınca ekranı kilitleyip cihazı uyutmak |
| Diğer uygulamaların üzerinde göster (`SYSTEM_ALERT_WINDOW`) | Kilitliyken videoyu her şeyin üstünde tam ekran göstermek |
| Açılışta başlama (`RECEIVE_BOOT_COMPLETED`) | Cihaz açılınca uygulamayı otomatik başlatmak |
| Ön plan servisi (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`) | Şarj takılma/çıkarılma olaylarını arka planda dinlemek ("Video Döngü çalışıyor" bildirimi) |
| Ekranı açık tutma (`WAKE_LOCK`) | Şarj takılınca ekranı uyandırmak |
| Arka plan uygulamalarını kapatma (`KILL_BACKGROUND_PROCESSES`) | İsteğe bağlı sistem optimizasyonu |
| Durum çubuğu (`EXPAND_STATUS_BAR`) | Kilitliyken bildirim panelini kapatmak (Android 11 ve öncesi) |
| Depolama okuma (sadece Android 4+ sürümü) | Video klasörünü okumak |

Uygulama **internet izni istemez**; hiçbir veri toplamaz veya göndermez.

---

## ⚠️ Bilinen sınırlamalar

- **Android 4.4:** Alttan yukarı kaydırınca alttaki 3 sistem düğmesi bir an görünebilir; Android 4.4 normal bir uygulamanın
  bu çubuğun üstüne çıkmasına izin vermez. Düğmelere basılsa bile video yerinden oynamaz.
- **Android 4.4:** Kilitliyken Ana Ekran'a basıldıysa, kilit açıldıktan sonra uygulamanın öne gelmesi Android kuralı gereği
  en fazla 5 saniye gecikebilir.
- **Android 8+:** Ekran sabitleme, kilit açılınca **bilerek kaldırılmaz** (her kilitte yeniden sabitlemek dokunuşların
  kaybolmasına yol açıyordu). Uygulamadan tamamen çıkmak için kilidi açın, sonra Android'in sabitleme kaldırma hareketini
  kullanın: hareketle gezinmede **alttan yukarı kaydırıp basılı tutun**; 3 düğmeli gezinmede **Geri ve Son Uygulamalar'a birlikte basılı tutun**.
- **Android 8+:** "Diğer uygulamaların üzerinde göster" izni verilmezse kilit, ekran sabitleme ve dokunma engeliyle çalışmaya devam eder.
- Android 12 ve üstünde uygulamalar bildirim panelini kendileri kapatamaz; bu sürümlerde paneli ekran sabitleme engeller.

---

## 📝 Sürüm geçmişi

### v1.1
- Güç tuşuyla ekran kapatılınca video artık arka planda oynamaya devam etmiyor; ekran açılınca kaldığı yerden devam ediyor.
- Kumanda medya tuşları çocuk kilidi aktifken de çalışıyor.
- Duraklatılan video, çocuk kilidi tekrar açıldığında kendiliğinden başlamıyor.
- Yeni: Kilit açıkken 30 saniye dokunulmazsa çocuk kilidi otomatik olarak devreye giriyor.
- Düzeltme: Video yüklenirken duraklatılırsa yüklenince kendiliğinden başlamıyor.
- Düzeltme: Hiçbir video açılamadığında (örn. hafıza kartı hazır değilken) uygulama çökmüyor; 10 saniye sonra yeniden deniyor.

### v1.0
- İlk sürüm: sürekli video döngüsü, çocuk kilidi (5 dokunuşla açma), şarj davranışı, açılışta başlama.

---

## 🛠️ Kaynak koddan derleme

Gereksinimler: Android Studio (Gradle ve JDK 17+ ile birlikte gelir), Android SDK 34.

```bash
git clone https://github.com/mehmet-cizmeci/videoloop.git
cd videoloop
./gradlew :app:assembleDebug :app_legacy:assembleDebug
```

APK'lar şu klasörlerde oluşur:
- `app/build/outputs/apk/debug/app-debug.apk` (Android 8+)
- `app_legacy/build/outputs/apk/debug/app_legacy-debug.apk` (Android 4.4+)

### Proje yapısı
```
videoloop/
├── app/                 Android 8+ sürümü (klasör seçimi: Storage Access Framework)
│   └── src/main/java/com/example/videoloop/
│       ├── MainActivity.kt      Oynatıcı, kontroller, çocuk kilidi
│       ├── VideoLoopService.kt  Şarj olaylarını dinleyen ön plan servisi
│       ├── BootReceiver.kt      Açılışta başlatma
│       ├── PowerReceiver.kt     Şarj takılma/çıkarılma
│       └── AdminReceiver.kt     Cihaz yöneticisi
└── app_legacy/          Android 4.4+ sürümü (dahili klasör seçici)
    └── src/main/java/com/example/videoloop/legacy/   (aynı yapı)
```

Her iki uygulamanın arayüzü kodla oluşturulur (XML yerleşim dosyası yoktur).
