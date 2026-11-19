# KURDO – Android başlangıç prototipi

Bu klasör Android Studio ile açılabilecek Kotlin + Jetpack Compose başlangıç projesidir.

## Bu sürümde bulunanlar
- Koyu temalı Kurdo sohbet arayüzü
- Türkçe yazılı sohbet prototipi
- Cihazdaki SharedPreferences alanında yerel hafıza
- Basit önemli-bilgi yakalama prototipi
- Android konuşma tanıma entegrasyonu: cihaz destekliyorsa on-device tanıma seçilir
- Android TextToSpeech ile sesli yanıt

## Henüz bulunmayanlar
- GGUF yerel büyük dil modeli entegrasyonu
- Gerçek anlamsal hafıza / vektör arama
- Tamamen çevrimdışı Whisper STT paketi
- Garantili Iğdır ağzı TTS sesi

Bu nedenle bu sürüm henüz tam yapay zekâ değildir. Sohbet ekranı, yerel kayıt ve ses API iskeletidir. Gerçek çevrimdışı cevap üretimi için telefona uygun GGUF model ve llama.cpp Android motoru eklenmelidir.

## Derleme
1. Android Studio'da bu klasörü aç.
2. Gradle senkronizasyonunu tamamla.
3. Android SDK 35 ve uyumlu JDK kurulu olmalı.
4. Redmi Note 14 Pro'da USB hata ayıklamayı açıp Run ile yükle.

## Teknik notlar
Android'in SpeechRecognizer API'si bazı cihazlarda uzak servise yönlenebilir. Kod, API 31+ üzerinde on-device tanıma mevcutsa onu seçer; yoksa sistem tanıma servisine düşer. Bu nedenle bu prototipte ses girişi her koşulda internetsiz garanti edilmez.
Android TTS'de erkek Türkçe sesin bulunması cihazda kurulu ses paketlerine bağlıdır.
