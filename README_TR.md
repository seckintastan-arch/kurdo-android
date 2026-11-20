# Kurdo Android

Kurdo, Android telefonda çalışan kişisel yardımcı uygulamasıdır. Sohbet modeli ve hafıza telefonda tutulur; uygulama sohbet içeriğini bir sunucuya göndermez.

## Özellikler

- LiteRT-LM ile cihaz içi sohbet; model Wi-Fi üzerinden bir kez indirilir.
- Türkçe sesle yazma ve Android TTS ile sesli yanıt (cihazın çevrimdışı konuşma desteği ve yüklü seslerine bağlıdır).
- Sohbet geçmişi ve öğrenilmiş notlar cihazda saklanır.
- `öğren: ...` veya `hatırla ...` yazarak Kurdo'ya kişisel bilgi/tercih kaydedilebilir; ilgili sorularda hafızadan çağrılır.
- Kaydedilmiş hafızalar görüntülenebilir ve tek tek ya da topluca silinebilir.
- Yinelenen cümleleri azaltan yanıt temizliği ve klavye açıldığında sohbeti kaydırılabilir tutan düzen.
- Fener, arama ekranı ve mesaj taslağı komutları; arama/mesaj kullanıcı onayıyla tamamlanır.
- Android 10 ve üzeri cihazlarda Kurdo varsayılan asistan olarak seçilebilir. Arka planda sürekli uyanık kelime dinleme bu sürümde yoktur.

## Modelin sınırları

Mevcut 0.8B yerel model, çevrimdışı ve özel kullanım için tasarlanmıştır; büyük bulut modelleri kadar güçlü muhakeme veya geniş bilgi sunmaz. Hafıza özelliği model ağırlıklarını değiştirmez; kullanıcı tarafından verilen bilgileri yerel not olarak saklar ve ilgili yanıtlara ekler. Sohbetler çevrimdışı kalır.

## Kurulum ve güncelleme

GitHub Actions debug APK üretir ve sürüm APK'sını GitHub Releases'a ekler. Güncellemelerde Android'in kurulum onayı gerekir. Üzerine kurulum için APK'ların aynı imzalama sertifikasıyla imzalanması zorunludur; debug derlemeleri kalıcı bir yayın imzası sağlamaz. Dağıtım güncellemesi açılmadan önce kalıcı bir imzalama anahtarı yapılandırılmalıdır. Bu anahtar depoya commit edilmemelidir.

## Derleme

GitHub Actions, Java 17, Gradle 8.13 ve Android SDK 35 ile `:app:assembleDebug` çalıştırır. Yerel derleme için aynı SDK/JDK gerekir; komut `gradle :app:assembleDebug`.
