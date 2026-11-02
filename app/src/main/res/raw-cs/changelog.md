# Seznam změn

## 1.2.0

### Nové
- **Propojení účtu Discord** (karta *Stav*): vaše skutečné jméno a profilový obrázek v živém náhledu a na kartě
  *Stav* – ukládá se jen veřejný profil
- **26 dalších jazyků**: francouzština, španělština, portugalština (Brazílie), italština, nizozemština, dánština,
  švédština, norština, finština, polština, čeština, maďarština, rumunština, řečtina, turečtina, srbština, arabština,
  hindština, indonéština, vietnamština, thajština, čínština, japonština a korejština (překlady pomocí AI – opravy vítány)
- **Výběr jazyka** v *Nastavení aplikace* (v Androidu 13+ také v nastavení aplikace v Androidu)

### Vylepšeno
- Menší spotřeba baterie: „Pozastaveno“ zmizí včas i během spánku telefonu; žádné probouzení, když je stav vypnutý
  nebo chybí Discord; opakované připojování se postupně zpomaluje
- Méně dat a paměti: obaly se znovu kontrolují méně často, neznámá alba se 12 h znovu nevyhledávají,
  obrázky se uvolní, když je aplikace na pozadí
- Opravy: neplatné hodnoty v importovaných nastaveních se opraví, přehrávání vrátí stav po zastavení ze správce úloh

## 1.1.0

### Nové
- **Nastavení aplikace** v nabídce se třemi tečkami: motiv *Podle systému* / *Světlý* / *Tmavý*, **AMOLED černá**
  pro tmavý režim a barvy **Material You** (Android 12+)
- **O aplikaci**: obrázek autora z GitHubu a vystředěné odkazy
- **Střídání textů**: `%artist% || %title%` zobrazuje střídavě interpreta a název (*Interval střídání* na kartě
  *Zobrazení*, 5–60 s)
- **Nová ikona aplikace** ve stylu Powerampu: bublina s pruhy ekvalizéru (také jako tematická ikona v Androidu 13+);
  logo v aplikaci, úvodní obrazovka i obrázky v Discordu jsou sladěné

### Plynulejší
- Optimalizované buildy (R8 + baseline profile): obrazovky a přepínání karet už se nezasekávají
- Nastavení, stav Powerampu a obrázky se načítají na pozadí
- Živý náhled aktualizuje pozici přehrávání jen jednou za sekundu; vzdálené karty se otevírají hned; protokol se posouvá plynule

## 1.0.0

### Nové
- První vydání: **Discord Rich Presence pro Poweramp** – funguje se stabilními i beta buildy Powerampu
- **„Poslouchá Poweramp“** s ukazatelem průběhu (nebo „Hraje“ s uplynulým časem)
- **Obaly bez streamovací služby**: obal, který zobrazuje Poweramp, se nahraje na x0.at, catbox.moe nebo Imgur,
  plus volitelné vyhledávání online (Spotify, Deezer, Apple Music / iTunes, Bandcamp, Discogs,
  MusicBrainz + Cover Art Archive)
- Textové šablony se zástupnými symboly (`%title%`, `%artist%`, `%album%`, `%bar%` …) a živý náhled toho, co
  zobrazí Discord
- Chování při pozastavení (zobrazit „Pozastaveno“ nebo skrýt, odstranit po pauze) a filtry pro streamy a složky
- Kontrola aktualizací (vydání na GitHubu)
- Angličtina, němčina, ruština a ukrajinština
