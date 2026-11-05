# 🔏 Dijital İmza (Kod İmzalama) Rehberi

Bu rehber **mehmet-cizmeci** hesabındaki açık kaynak uygulamaların imza durumunu anlatır. Üç projede de aynıdır.

## Güncel durum

| Proje | Platform | İmza |
|---|---|---|
| [video-muzik-indirici](https://github.com/mehmet-cizmeci/video-muzik-indirici) | Windows (exe) | **İmzasız** — Windows ilk açılışta SmartScreen uyarısı gösterebilir |
| [Disci](https://github.com/mehmet-cizmeci/Disci) | Windows (exe + kurulum betikleri) | **İmzasız** — Windows ilk açılışta SmartScreen uyarısı gösterebilir |
| [videoloop](https://github.com/mehmet-cizmeci/videoloop) | Android (apk) | **İmzalı** — kalıcı Android yayın anahtarı (v1.3 ve sonrası) |

**Windows uygulamaları neden imzasız?** Windows'un tanıdığı kod imzalama sertifikaları ücretlidir (en ucuzu, açık kaynak projeler için bile yıllık ücretlidir). Bu projelerin gelir beklentisi olmadığından sertifika alınmadı. Uygulamalar imzasız da güvenle çalışır; kaynak kodun tamamı depodadır ve isteyen exe'yi kendisi derleyebilir. SmartScreen uyarısında **Ek bilgi → Yine de çalıştır** ile devam edilir.

**Android imzası ücretsizdir:** geliştiricinin kendi oluşturduğu anahtarla yapılır ve videoloop'ta kullanılmaktadır.

İmzalama altyapısı (`imzala.ps1`) depolarda hazır durur: ileride bir sertifika edinilirse (ücretli bir sertifika veya [SignPath Foundation](https://signpath.org/) gibi ücretsiz bir açık kaynak programı) derleme komutları dosyaları kendiliğinden imzalar. Sertifika yokken betik dosyaları imzasız bırakır ve derleme normal şekilde tamamlanır. Aşağıdaki Windows bölümleri bu durum için başvuru bilgisidir.

## İçindekiler

- [Neden imza?](#neden-imza)
- [Windows: Certum bulut sertifikası almak](#windows-certum-bulut-sertifikası-almak)
- [Windows: SimplySign'ı bilgisayara bağlamak](#windows-simplysignı-bilgisayara-bağlamak)
- [Windows: imzalı derleme](#windows-imzalı-derleme)
- [Android: yayın anahtarı](#android-yayın-anahtarı)
- [Kullanıcılar imzayı nasıl doğrular?](#kullanıcılar-imzayı-nasıl-doğrular)
- [Sorun giderme](#sorun-giderme)

---

## Neden imza?

- Windows'taki **"Bilinmeyen yayıncı"** yerine geliştiricinin adı görünür.
- Dosyanın yayınlandıktan sonra **değiştirilmediği** (virüs bulaşmadığı) kanıtlanır.
- SmartScreen ve antivirüs programları zamanla imzalı dosyalara **itibar** kazandırır; uyarılar azalır ve sonunda kaybolur.
- Android'de aynı anahtarla imzalanan sürümler birbirinin **üzerine güncelleme** olarak kurulabilir.

> İmza, SmartScreen uyarısını ilk günden kaldırmaz. İtibar; aynı sertifikayla imzalanmış dosyaların indirilip sorunsuz çalıştırılmasıyla birkaç hafta–birkaç ay içinde oluşur. Sertifika yenilenirken aynı kimlikle devam etmek bu itibarı korur.

---

## Windows: Certum bulut sertifikası almak

> Bu bölüm başvuru bilgisidir; şu an bu projeler için sertifika **alınmamıştır** (bkz. [Güncel durum](#güncel-durum)).

**Ürün:** Certum *Open Source Code Signing in the Cloud* (SimplySign). Bireysel açık kaynak geliştiriciler içindir; şirket gerekmez. **Ücretlidir** (Kasım 2026'da 49 €); güncel fiyat ve koşullar için Certum mağazasını kontrol edin.

1. **Satın alma:** Certum mağazasından *Open Source Code Signing* ürününün **bulut (SimplySign)** seçeneğini alın. Kart okuyuculu seçeneği almanıza gerek yok.
2. **Hesap ve başvuru formu:** Ad soyadınızı kimliğinizdeki gibi yazın (sertifikada ve Windows'un uyarı penceresinde bu ad görünecek). "Proje adresi" olarak açık kaynak depolardan birini verin, ör. `https://github.com/mehmet-cizmeci/video-muzik-indirici`.
3. **Kimlik doğrulama:** Certum'un istediği belgeleri yükleyin (genellikle kimlik/pasaport taraması ve adres teyidi). Onay e-postasını bekleyin.
4. **Etkinleştirme:** Onaydan sonra gelen e-postadaki adımlarla sertifikayı etkinleştirin. Telefona **SimplySign** uygulaması kurulur ve hesabınıza bağlanır.

Sertifika etkinleşince parmak izini (thumbprint) bir kenara not edin; aşağıda kullanılacak.

## Windows: SimplySign'ı bilgisayara bağlamak

1. **SimplySign Desktop** programını Certum'un sitesinden indirip kurun.
2. Program açıkken sistem tepsisindeki simgeye sağ tıklayın → **Connect** (Bağlan). Telefondaki SimplySign uygulamasının gösterdiği 6 haneli kodu girin.
3. Bağlantı kurulunca sertifika Windows'ta **Geçerli Kullanıcı → Kişisel** deposunda görünür. Kontrol için PowerShell:

   ```powershell
   Get-ChildItem Cert:\CurrentUser\My -CodeSigningCert | Format-List Subject, Thumbprint, NotAfter
   ```

4. (İsteğe bağlı, önerilir) Parmak izini kalıcı olarak kaydedin; betik başka bir sertifikayı yanlışlıkla seçmez:

   ```powershell
   New-Item -ItemType Directory -Force "$env:USERPROFILE\.imza" | Out-Null
   Set-Content "$env:USERPROFILE\.imza\kod-imza.txt" "BURAYA_PARMAK_IZI"
   ```

SimplySign oturumu bir süre sonra kapanır; imzalamadan önce **Connect** ile yeniden bağlanmanız gerekebilir.

## Windows: imzalı derleme

Her iki Windows projesinde de ortak betik bulunur: `tools/imzala.ps1` (Disci'de `deploy/imzala.ps1`). Betik:

- signtool'u (Windows SDK) kendisi bulur,
- sertifikayı parmak izinden ya da depodaki tek kod imzalama sertifikasından seçer,
- tüm dosyaları **tek seferde** imzalar (bulut sertifikada tek onay yeterli olur),
- SHA-256 ve **zaman damgası** ekler (sertifikanın süresi dolsa da imza geçerli kalır; Certum, DigiCert, Sectigo sunucuları sırayla denenir),
- imzayı doğrular.

Sertifika bağlı değilse dosyalar **imzasız** bırakılır ve uyarı yazılır; böylece sertifikası olmayan katkıcılar da derleme yapabilir. Yayın derlemesinde imzasız çıkmaması için "zorunlu" seçeneğini kullanın.

| Proje | Komut |
|---|---|
| video-muzik-indirici | `build.bat` — zorunlu imza: `build.bat /imzazorunlu` |
| Disci | `powershell -ExecutionPolicy Bypass -File deploy\paket-olustur.ps1` — zorunlu imza: `-ImzaZorunlu` |
| Herhangi bir dosya | `powershell -ExecutionPolicy Bypass -File tools\imzala.ps1 -Zorunlu dosya.exe` |

Elle imzalamak isterseniz eşdeğer signtool komutu:

```bat
signtool sign /fd sha256 /tr http://time.certum.pl /td sha256 /sha1 <PARMAK_IZI> dosya.exe
signtool verify /pa /v dosya.exe
```

---

## Android: yayın anahtarı

Android uygulamaları (videoloop) Certum sertifikası kullanmaz. Her uygulama, geliştiricinin oluşturduğu bir **yayın anahtarıyla** (keystore) imzalanır. Kurallar:

- **Aynı uygulamanın tüm sürümleri aynı anahtarla imzalanmalıdır.** Anahtar değişirse kullanıcılar güncelleme yapamaz, uygulamayı kaldırıp yeniden kurmak zorunda kalır.
- Anahtar **kaybolursa** o uygulamaya bir daha güncelleme çıkılamaz. Bu yüzden yedeklemek hayati önemdedir.
- Anahtar ve parolası **asla** Git deposuna konmaz.

videoloop'ta anahtar ve parolası depo dışında, şu klasörde durur:

```
%USERPROFILE%\.imza\videoloop-release.jks          ← anahtar deposu
%USERPROFILE%\.imza\videoloop-imza.properties      ← yol, takma ad ve parolalar
```

Gradle derlemesi (`app` ve `app_legacy`) bu dosyayı okur ve release APK'larını yayın anahtarıyla imzalar. Dosya yoksa (ör. başka bir geliştiricinin bilgisayarında) release derlemesi debug anahtarıyla imzalanır ve uyarı yazılır.

```bat
gradlew assembleRelease
```

> **Yedekleme:** `%USERPROFILE%\.imza` klasörünün tamamını şifreli bir USB belleğe ve/veya şifreli bir bulut klasörüne kopyalayın. Bu iki dosya olmadan videoloop'a güncelleme yayınlanamaz.

Uygulama ileride Google Play'e konursa *Play App Signing* kullanılır; bu anahtar o zaman "yükleme anahtarı" olarak kullanılabilir.

---

## Kullanıcılar imzayı nasıl doğrular?

**Windows (exe):** Dosyaya sağ tık → **Özellikler** → **Dijital İmzalar** sekmesi. İmzalayan adı ve zaman damgası görünür. "Ayrıntılar" → "Bu dijital imza geçerli" yazmalıdır.

PowerShell ile:

```powershell
Get-AuthenticodeSignature .\VideoMuzikIndirici.exe | Format-List Status, SignerCertificate, TimeStamperCertificate
```

**Android (apk):** Android SDK ile:

```bat
apksigner verify --print-certs VideoDongu-Android8+.apk
```

Çıktıdaki `Signer #1 certificate SHA-256 digest` değeri videoloop için şu olmalıdır (v1.3 ve sonrası):

```n d5b4819fd5037c74fa4b1719084db96b00cdd2ed2d67afbb50e30ac055b310e3
```


---

## Sorun giderme

| Sorun | Çözüm |
|---|---|
| "Kod imzalama sertifikası bulunamadı" | SimplySign Desktop açık ve **Connect** yapılmış mı? `Get-ChildItem Cert:\CurrentUser\My -CodeSigningCert` boş dönüyorsa bağlantı kurulmamıştır. |
| "Birden fazla kod imzalama sertifikası var" | Parmak izini `%USERPROFILE%\.imza\kod-imza.txt` dosyasına yazın veya `-Parmakizi` verin. |
| "signtool.exe bulunamadı" | Windows SDK'yı *Signing Tools for Desktop Apps* bileşeniyle kurun. |
| Zaman damgası hatası | İnternet bağlantısını kontrol edin; betik üç sunucuyu sırayla dener. Şirket ağı HTTP'yi engelliyor olabilir. |
| İmzalı exe yine SmartScreen uyarısı veriyor | Normaldir; itibar zamanla oluşur. Uyarı penceresinde artık yayıncı adınız görünür. |
| Android: "App not installed" / güncelleme kurulmuyor | Telefondaki sürüm başka bir anahtarla imzalanmış. Eski uygulamayı kaldırıp yenisini kurun (yalnızca anahtar değiştiğinde bir kez gerekir). |
