# Değişiklik günlüğü

## 1.2.1

### İyileştirmeler
- **Arka plan hizmeti**: Poweramp açılır açılmaz değil, yalnızca bir parça gerçekten çaldığında başlar ve artık hiçbir
  şey çalmadığında (duraklatma süresinden sonra) kendini kapatır
- **Kapak yüklemeleri**: "cover.jpg" yerine rastgele dosya adları – x0.at yüklemeleri reddetmeye başlamıştı

## 1.2.0

### Yeni
- **Discord hesabınızı bağlayın** (*Durum* sekmesi): gerçek adınız ve profil resminiz canlı önizlemede ve *Durum*
  sekmesinde – yalnızca herkese açık profil saklanır
- **26 dil daha**: Fransızca, İspanyolca, Portekizce (Brezilya), İtalyanca, Felemenkçe, Danca, İsveççe, Norveççe,
  Fince, Lehçe, Çekçe, Macarca, Rumence, Yunanca, Türkçe, Sırpça, Arapça, Hintçe, Endonezyaca, Vietnamca, Tayca,
  Çince, Japonca ve Korece (yapay zekâ çevirileri – düzeltmeler memnuniyetle karşılanır)
- *Uygulama ayarları*nda **dil seçici** (Android 13 ve üzerinde Android’in uygulama ayarlarında da)

### İyileştirmeler
- Daha az pil: “Duraklatıldı” telefon uykudayken de zamanında kaybolur; durum kapalıyken veya Discord yokken
  uyandırma yapılmaz; yeniden bağlanma denemeleri giderek seyrekleşir
- Daha az ağ ve bellek kullanımı: kapaklar daha seyrek yeniden kontrol edilir, bilinmeyen albümler 12 saat boyunca
  yeniden aranmaz, uygulama arka plandayken resimler bellekten atılır
- Düzeltmeler: içe aktarılan ayarlar düzeltilir, görev yöneticisinden durdurulduktan sonra çalmaya başlamak durumu geri getirir

## 1.1.0

### Yeni
- Üç nokta menüsünde **Uygulama ayarları**: *Sistemi takip et* / *Açık* / *Koyu* tema, koyu mod için **AMOLED siyahı**
  ve **Material You** renkleri (Android 12 ve üzeri)
- **Hakkında**: geliştiricinin GitHub resmi ve ortalanmış bağlantılar
- **Sırayla değişen metinler**: `%artist% || %title%` sanatçıyı ve başlığı sırayla gösterir (*Görünüm* sekmesinde
  *Değişim aralığı*, 5–60 sn)
- Poweramp tarzında **yeni uygulama simgesi**: ekolayzer çubuklu bir konuşma balonu (Android 13 ve üzerinde temalı
  simge olarak da); uygulama içi logo, açılış ekranı ve Discord resimleri de buna uygun

### Daha akıcı
- Optimize edilmiş derlemeler (R8 + baseline profile): ekranlar ve sekme geçişleri artık takılmıyor
- Ayarlar, Poweramp’in durumu ve resimler arka planda yüklenir
- Canlı önizleme çalma konumunu yalnızca saniyede bir günceller; uzaktaki sekmeler hemen açılır; günlük akıcı kayar

## 1.0.0

### Yeni
- İlk sürüm: **Poweramp için Discord Rich Presence** – Poweramp’in kararlı ve beta sürümleriyle çalışır
- İlerleme çubuğuyla **“Poweramp dinliyor”** (veya geçen süreyle “Oynuyor”)
- **Müzik akışı hizmeti olmadan kapaklar**: Poweramp’in gösterdiği kapak x0.at, catbox.moe veya Imgur’a yüklenir,
  ayrıca isteğe bağlı çevrimiçi aramalar (Spotify, Deezer, Apple Music / iTunes, Bandcamp, Discogs,
  MusicBrainz + Cover Art Archive)
- Yer tutuculu metin şablonları (`%title%`, `%artist%`, `%album%`, `%bar%` …) ve Discord’un ne göstereceğinin canlı
  önizlemesi
- Duraklatma davranışı (“Duraklatıldı” göster veya gizle, bir süre duraklatmadan sonra kaldır) ve yayınlar ile
  klasörler için filtreler
- Güncelleme kontrolü (GitHub sürümleri)
- İngilizce, Almanca, Rusça ve Ukraynaca
