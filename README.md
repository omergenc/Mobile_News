# Haber Derleyici (Mobile_News)

Türkiye odaklı ekonomi, siyaset ve spor (ağırlık Galatasaray'da) haberlerini bağımsız
medyanın RSS akışlarından ve sosyal medyadan toplayan, aynı olayı anlatanları birleştiren
ve isteğe bağlı olarak Claude Haiku ile özetleyen kişisel bir Android uygulaması.

İlk sürümde **sunucu yok**: her şey telefonda çalışır. APK, GitHub Actions'da derlenir ve
Google Play yerine elle kurulur (sideload).

## Ne yapar?

- **Kaynaklar** (`app/src/main/assets/sources.json`): Medyascope, T24, bianet, BBC Türkçe,
  DW Türkçe, Euronews Türkçe, Diken, Kısa Dalga, BirGün, Halk TV, Cumhuriyet, Bloomberg HT,
  ekonomim, Galatasaray için Fotomaç / A Spor, NTV Spor; Bluesky araması; isteğe bağlı
  YouTube kanalları ve Telegram kanalları.
- **Kategoriler**: Ekonomi, Siyaset, Galatasaray, Spor (+ "Tümü"). Türkçe anahtar kelime
  kurallarıyla; Claude özeti açıksa model de kategori önerir.
- **Birleştirme**: Başlık benzerliğiyle aynı olayı anlatan haberler tek kartta toplanır;
  kartta tüm kaynak linkleri durur.
- **Teyit**: Yalnızca tek bir yayın organından gelen haberler **"Teyit edilmedi"** diye
  işaretlenir. Bluesky aramasından gelen rastgele hesaplar tek kaynak sayılır, birbirini
  "teyit" edemez.
- **Özet (opsiyonel)**: Ayarlara Anthropic API anahtarı girilirse `claude-haiku-5-5` ile
  2–3 cümlelik tarafsız Türkçe özet çıkarılır. Anahtar yoksa başlık + kaynak linkleri
  gösterilir.
- **Arka plan + bildirim**: WorkManager 15/30/60 dakikada bir tarar (Android'in alt sınırı
  15 dk). En az 3 farklı kaynağın (Galatasaray'da 2) haber yaptığı yeni gelişmeler için
  bildirim gönderir.

## APK'yı indirme ve kurma

1. GitHub'da **Releases** sayfasından en son `HaberDerleyici-x.y.z.apk` dosyasını indirin.
   (Her push'ta derlenen ara sürümler: **Actions** → ilgili çalıştırma → *Artifacts*.
   Artifact zip olarak iner, içinden APK'yı çıkarın.)
2. Telefonda dosyayı açın. Android "bilinmeyen kaynaklardan yükleme" izni isterse, APK'yı
   açtığınız uygulama (Dosyalar / Chrome) için izin verin.
3. İlk açılışta bildirim iznini onaylayın.

> İmza notu: Depoda keystore secret'ı tanımlı değilse APK, CI makinesinin geçici debug
> anahtarıyla imzalanır. Bu durumda her yeni sürüm farklı anahtarla imzalanacağından
> güncellemeden önce eski sürümü kaldırmanız gerekir. Kalıcı çözüm için aşağıdaki
> "Release imzası" adımını yapın.

## API anahtarları

Uygulamada sağ üstteki **Ayarlar** (⚙) ekranından girilir; cihazda
`EncryptedSharedPreferences` ile (Android Keystore'daki AES-256 anahtarıyla) şifreli
saklanır. Hiçbir anahtar repoya veya APK'ya girmez.

| Ayar | Gerekli mi? | Nereden alınır |
|---|---|---|
| Anthropic API anahtarı | Hayır (özet için) | https://console.anthropic.com → API Keys |
| YouTube Data API anahtarı | Hayır (YouTube kaynakları için) | Google Cloud Console → "YouTube Data API v3" etkinleştir → Credentials → API key (anahtarı Android uygulamasıyla kısıtlamanız önerilir) |
| Telegram RSS köprüsü | Hayır (Telegram kaynakları için) | Kendi kurduğunuz bir RSSHub örneği, ör. `https://rsshub.sizin-alan-adiniz.com` |

Maliyet notu: Özetler yalnızca son 24 saatteki ve yeni kaynak eklenmiş haberler için,
tarama başına en fazla 12 istekle çıkarılır.

## Kaynak ekleme / çıkarma

`app/src/main/assets/sources.json` dosyasını düzenleyin ve push'layın (yeni APK derlenir).
Uygulama içinden kaynakları açıp kapatabilirsiniz; bu tercih cihazda saklanır.

```json
{ "id": "benzersiz_id", "type": "rss", "name": "Görünen ad", "url": "https://.../rss",
  "categoryHint": "EKONOMI", "galatasaray": false, "enabled": true }
```

| `type` | Gerekli alanlar | Not |
|---|---|---|
| `rss` | `url` | RSS 2.0, Atom ve RDF desteklenir |
| `bluesky` | `query` **veya** `actor` | `query`: Türkçe arama; `actor`: bir hesabın gönderileri (handle) |
| `youtube` | `channelId` (`UC…`) | Ayarlarda YouTube anahtarı gerekir; kanalın yüklemeleri listesi okunur (istek başına 1 kota) |
| `telegram` | `channel` | Herkese açık kanal adı; Ayarlarda RSS köprüsü gerekir. Uygulama Telegram sayfalarını kazımaz. |
| `x`, `instagram` | — | Yer tutucu; sonraki sürümlerde eklenecek |

Opsiyonel alanlar: `categoryHint` (`EKONOMI` / `SIYASET` / `SPOR`), `galatasaray: true`
(kaynağın tüm haberleri Galatasaray), `enabled`, `note` (ayarlar ekranında görünür).

Notlar:
- T24'ün RSS adresi geliştirme ağından erişilemediği için doğrulanamadı; telefonda hata
  verirse Ayarlar'dan kapatın.
- VOA Türkçe'nin RSS adresi doğrulanamadı; kaynak listede kapalı duruyor. Doğru adresi
  `url` alanına yazıp `enabled: true` yapın.
- Fotomaç / A Spor yalnızca Galatasaray spor haberleri için listede.

### Yeni bir platform eklemek (X, Instagram, ileride backend)

`source/NewsSource.kt` içindeki `NewsSource` arayüzünü uygulayan bir sınıf yazın ve
`SourceRegistry.create` içine `type` adıyla kaydedin. X ve Instagram için iskelet sınıflar
(`XSource`, `InstagramSource`) hazır.

## Derleme (GitHub Actions)

`.github/workflows/android.yml`:

- `Master`/`main` dalına her push'ta ve PR'larda release APK'yı derler, **Artifacts**
  olarak yükler.
- `v` ile başlayan bir tag push'landığında APK'yı **GitHub Releases**'e ekler:

  ```bash
  git tag v0.1.0
  ```

  ```bash
  git push origin v0.1.0
  ```

### Release imzası (önerilir)

Bir kere keystore oluşturun (JDK'nın `keytool` aracıyla, herhangi bir makinede) ve
**repoya koymayın**:

```bash
keytool -genkeypair -v -keystore haber.jks -alias haber -keyalg RSA -keysize 4096 -validity 10000
```

Ardından GitHub'da *Settings → Secrets and variables → Actions* altına ekleyin:

| Secret | Değer |
|---|---|
| `KEYSTORE_BASE64` | `base64 -w0 haber.jks` çıktısı |
| `SIGNING_STORE_PASSWORD` | keystore şifresi |
| `SIGNING_KEY_ALIAS` | `haber` |
| `SIGNING_KEY_PASSWORD` | anahtar şifresi |

Keystore dosyasını ve şifreleri güvenli bir yerde yedekleyin; kaybolursa aynı uygulamaya
güncelleme imzalanamaz.

## Yerelde derleme (opsiyonel)

Depoda `gradle-wrapper.jar` yok; CI, Gradle 8.11.1'i `gradle/actions/setup-gradle` ile
kurar. Yerelde Android Studio ile projeyi açabilir ya da JDK 17 + Android SDK + Gradle
8.11.1 kuruluyken şunu çalıştırabilirsiniz:

```bash
gradle assembleDebug
```

Wrapper isterseniz bir kez `gradle wrapper --gradle-version 8.11.1` çalıştırıp oluşan
dosyaları commit'leyebilirsiniz.

## Proje yapısı

```
app/src/main/
  assets/sources.json          kaynak listesi
  java/com/omergenc/mobilenews/
    source/                    eklentili kaynaklar (RSS, Bluesky, YouTube, Telegram, X/IG iskeleti)
    processing/                kategori kuralları ve olay birleştirme
    summary/ClaudeSummarizer   Claude Haiku özetleri (Anthropic Java SDK)
    data/                      şifreli ayarlar, yerel JSON deposu, tarama akışı
    work/                      WorkManager ve bildirimler
    ui/                        Jetpack Compose ekranları
```

## Bilinen sınırlar

- Kategori ve birleştirme kural tabanlıdır; zaman zaman yanlış gruplama olabilir.
- Arka plan taramasının sıklığını Android pil optimizasyonu geciktirebilir.
- X API ücretli olduğu, Instagram'da yalnızca Business Discovery kaldığı için bu sürümde
  yoklar.
