# Változásnapló

## 1.2.1

### Fejlesztések
- **Háttérszolgáltatás**: csak akkor indul, ha tényleg szól egy szám, nem már a Poweramp megnyitásakor, és magától
  leáll, ha már semmi sem szól (a szüneteltetési időkorlát után)
- **Borítók feltöltése**: véletlenszerű fájlnevek a „cover.jpg” helyett – az x0.at elkezdte elutasítani a
  feltöltéseket

## 1.2.0

### Új
- **Discord-fiók összekapcsolása** (*Állapot* lap): a valódi neved és profilképed az élő előnézetben és az
  *Állapot* lapon – csak a nyilvános profilt tárolja
- **26 további nyelv**: francia, spanyol, portugál (Brazília), olasz, holland, dán, svéd, norvég, finn,
  lengyel, cseh, magyar, román, görög, török, szerb, arab, hindi, indonéz, vietnámi, thai, kínai,
  japán és koreai (MI-fordítások – a javításokat szívesen fogadjuk)
- **Nyelvválasztó** az *Alkalmazásbeállítások* menüben (Android 13+ rendszeren az Android alkalmazásbeállításaiban is)

### Fejlesztések
- Kisebb akkumulátorhasználat: a „Szüneteltetve” akkor is időben eltűnik, ha a telefon alvó módban van; nincs ébresztés,
  amíg az állapot ki van kapcsolva vagy a Discord hiányzik; az újracsatlakozási kísérletek egyre ritkábbak
- Kisebb hálózat- és memóriahasználat: a borítókat ritkábban ellenőrzi újra, az ismeretlen albumokat 12 órán át
  nem keresi újra, a képek törlődnek, ha az alkalmazás a háttérben van
- Javítások: az importált beállításokat kijavítja, a lejátszás visszahozza az állapotot a feladatkezelőből
  történt leállítás után

## 1.1.0

### Új
- **Alkalmazásbeállítások** a hárompontos menüben: *Rendszerbeállítás követése* / *Világos* / *Sötét* téma,
  **AMOLED fekete** sötét módhoz és **Material You** színek (Android 12+)
- **Névjegy**: a szerző GitHub-képe és középre igazított linkek
- **Váltakozó szövegek**: a `%artist% || %title%` felváltva mutatja az előadót és a címet (*Váltási időköz* a
  *Megjelenítés* lapon, 5–60 mp)
- **Új alkalmazásikon** a Poweramp stílusában: szövegbuborék hangszínszabályzó-sávokkal (Android 13+ rendszeren
  témázott ikonként is); az alkalmazáson belüli logó, az indítóképernyő és a Discord-képek is ehhez igazodnak

### Gördülékenyebb
- Optimalizált buildek (R8 + baseline profil): a képernyők és a lapváltások már nem akadoznak
- A beállítások, a Poweramp állapota és a képek a háttérben töltődnek be
- Az élő előnézet csak másodpercenként frissíti a lejátszási pozíciót; a távoli lapok azonnal megnyílnak; a napló
  gördülékenyen görget

## 1.0.0

### Új
- Első kiadás: **Discord Rich Presence a Poweramphoz** – a Poweramp stabil és béta buildjeivel is működik
- **„Hallgatja: Poweramp”** folyamatjelzővel (vagy „Játszik” az eltelt idővel)
- **Borítók streamingszolgáltatás nélkül**: a Poweramp által mutatott borító feltöltése az x0.at, a catbox.moe vagy
  az Imgur oldalra, valamint opcionális online keresés (Spotify, Deezer, Apple Music / iTunes, Bandcamp, Discogs,
  MusicBrainz + Cover Art Archive)
- Szövegsablonok helyőrzőkkel (`%title%`, `%artist%`, `%album%`, `%bar%` …) és élő előnézet arról, amit a
  Discord mutat
- Szüneteltetéskori viselkedés („Szüneteltetve” megjelenítése vagy elrejtés, eltávolítás szünet után) és szűrők
  streamekhez és mappákhoz
- Frissítéskeresés (GitHub-kiadások)
- Angol, német, orosz és ukrán nyelv
