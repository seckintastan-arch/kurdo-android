# Kurdo Android

Kurdo, Android telefonda çalışan kişisel yardımcı uygulamasıdır. Sohbet yanıtları LiteRT-LM üzerinden telefondaki yerel dil modeliyle üretilir; API anahtarı veya sohbet sunucusu kullanılmaz.

## Bu sürümde

- LiteRT-LM kullanan gerçek cihaz içi dil modeli sohbeti
- Qwen3.5 0.8B INT8 LiteRT modelini uygulama içinden indirme
- Ad ve bazı tercihleri cihazda yerel hafızaya kaydetme; son sohbetleri yerelde tutma
- Türkçe Android metin okuma ve destek varsa çevrimdışı Türkçe konuşma tanıma
- Feneri açıp kapatma
- Telefon arama ekranını açma
- Mesaj uygulamasını doldurup açma; gönderme işlemi kullanıcının onayına bırakılır

## İlk çalıştırma

1. APK'yi Android telefona kur ve Kurdo'yu aç.
2. İlk kullanımda yaklaşık 965 MB olan yerel model dosyasını indir. Büyük dosya nedeniyle indirmeyi Wi-Fi üzerinden başlat.
3. Model telefonda hazırlandıktan sonra sohbet çevrimdışı çalışır. Model uygulamanın özel dosya alanında tutulur.
4. Konuşma tanıma ve fener, ilgili özellik ilk kez istendiğinde Android iznini kullanır.

Model lisansı Apache 2.0'dır. İlk model indirme dışında uygulama sohbetlerini ağa göndermez. Android'in Türkçe TTS sesleri telefonda kurulu olan seslere bağlıdır; erkek ses ve Iğdır ağzı garantisi yoktur. Çevrimdışı sesle yazma, telefonda çevrimdışı Türkçe tanıma hizmeti kuruluysa çalışır.

Telefon komutları örnekleri: feneri aç, ara 05XXXXXXXXX, mesaj gönder 05XXXXXXXXX Merhaba.
Arama ve mesaj komutları Android'in sistem uygulamasını açar; arama veya mesaj kullanıcı tarafından başlatılır.

## Derleme

GitHub Actions içindeki Kurdo Android APK iş akışı Java 17, Gradle 8.13 ve Android SDK 35 ile assembleDebug çalıştırır. APK, Kurdo-debug-APK adlı iş akışı artifact'ı olarak yayımlanır.

Yerel derleme için Android SDK 35 ve JDK 17 gerekir. Komut: gradle :app:assembleDebug.
