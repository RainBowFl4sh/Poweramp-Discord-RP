# Wijzigingen

## 1.2.1

### Verbeterd
- **Achtergronddienst**: start pas als er echt een nummer speelt, niet al bij het openen van Poweramp, en stopt
  vanzelf zodra er niets meer speelt (na de pauzelimiet)
- **Hoesuploads**: willekeurige bestandsnamen in plaats van ‘cover.jpg’ – x0.at weigerde uploads

## 1.2.0

### Nieuw
- **Koppel je Discord-account** (tabblad *Status*): je echte naam en profielfoto in het livevoorbeeld en op het
  tabblad *Status* – alleen het openbare profiel wordt bewaard
- **26 extra talen**: Frans, Spaans, Portugees (Brazilië), Italiaans, Nederlands, Deens, Zweeds, Noors, Fins,
  Pools, Tsjechisch, Hongaars, Roemeens, Grieks, Turks, Servisch, Arabisch, Hindi, Indonesisch, Vietnamees, Thai, Chinees,
  Japans en Koreaans (AI-vertalingen – correcties welkom)
- **Taalkeuze** in de *App-instellingen* (vanaf Android 13 ook in de app-instellingen van Android)

### Verbeterd
- Minder batterijverbruik: “Gepauzeerd” verdwijnt op tijd terwijl de telefoon slaapt; geen wake-ups meer zolang de
  presence uit staat of Discord ontbreekt; pogingen om opnieuw te verbinden worden steeds verder uitgesteld
- Minder netwerk- en geheugengebruik: hoezen worden minder vaak opnieuw gecontroleerd, onbekende albums worden 12 uur
  niet opnieuw opgezocht, afbeeldingen worden vrijgegeven als de app op de achtergrond staat
- Opgelost: geïmporteerde instellingen worden gecorrigeerd, afspelen brengt de presence terug na stoppen via taakbeheer

## 1.1.0

### Nieuw
- **App-instellingen** in het menu met drie puntjes: thema *Systeemstandaard volgen* / *Licht* / *Donker*,
  **AMOLED-zwart** voor de donkere modus en **Material You**-kleuren (Android 12+)
- **Over**: de GitHub-foto van de maker en gecentreerde links
- **Wisselende teksten**: `%artist% || %title%` toont afwisselend de artiest en de titel (*Wisselinterval* op het
  tabblad *Weergave*, 5–60 s)
- **Nieuw app-pictogram** in de stijl van Poweramp: een tekstballon met equalizerbalken (ook als thematisch pictogram
  vanaf Android 13); het logo in de app, het opstartscherm en de Discord-afbeeldingen passen erbij

### Soepeler
- Geoptimaliseerde builds (R8 + baselineprofiel): schermen en tabbladwissels haperen niet meer
- Instellingen, de status van Poweramp en afbeeldingen laden op de achtergrond
- Het livevoorbeeld werkt de afspeelpositie maar één keer per seconde bij; verre tabbladen openen meteen; het logboek scrolt soepel

## 1.0.0

### Nieuw
- Eerste release: **Discord Rich Presence voor Poweramp** – werkt met de stabiele en de bètaversies van Poweramp
- **“Luistert naar Poweramp”** met een voortgangsbalk (of “Speelt” met de verstreken tijd)
- **Hoezen zonder streamingdienst**: de hoes die Poweramp toont wordt geüpload naar x0.at, catbox.moe of Imgur,
  plus optioneel online zoeken (Spotify, Deezer, Apple Music / iTunes, Bandcamp, Discogs,
  MusicBrainz + Cover Art Archive)
- Tekstsjablonen met placeholders (`%title%`, `%artist%`, `%album%`, `%bar%` …) en een livevoorbeeld van wat
  Discord toont
- Gedrag bij pauze (“Gepauzeerd” tonen of verbergen, verwijderen na een pauze) en filters voor streams en mappen
- Updatecontrole (GitHub-releases)
- Engels, Duits, Russisch en Oekraïens
