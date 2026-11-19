# Kurdo bulutta APK derleme

Bu proje GitHub Actions ile **debug APK** oluşturacak şekilde hazırlanmıştır.

## Bir kere yapılacak kurulum
1. GitHub'da yeni bir depo (repository) oluştur. Tercihen **Private** seç.
2. Bu ZIP içindeki `KurdoAndroid` klasörünün **içindeki** dosyaları deponun kök dizinine yükle. `.github/workflows/android-apk.yml` dosyasının gizli klasör olması nedeniyle yüklemede korunduğunu kontrol et.
3. GitHub deposunda **Actions** sekmesine gir, `Kurdo APK Derle` iş akışını seç, **Run workflow** seçeneğini çalıştır. Ana dala ilk yüklemede otomatik de başlayabilir.
4. Derleme başarılı olunca ilgili iş akışı çalışmasının altındaki **Artifacts** bölümünden `Kurdo-debug-APK` dosyasını indir.
5. İnen ZIP'i açıp `app-debug.apk` dosyasını Android telefonuna kur. Telefonun yükleme iznini isteyebilir.

## Sınırlar
- GitHub hesabı ve depo olmadan bu sohbetten GitHub Actions çalıştırılamaz.
- Bu paket derleme için hazırlanmıştır; gerçek GitHub derlemesi henüz çalıştırılıp doğrulanmamıştır.
- APK debug imzasıyla oluşturulur; dağıtıma hazır release imzası değildir.
- Bu, yerel sohbet/hafıza/ses prototipidir. Gerçek yerel LLM, kapsamlı Android kontrol modülleri ve çevrimdışı STT henüz yoktur.
