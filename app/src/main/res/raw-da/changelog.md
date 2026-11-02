# Ændringslog

## 1.2.0

### Nyt
- **Tilknyt din Discord-konto** (fanen *Status*): dit rigtige navn og profilbillede i live-forhåndsvisningen og under
  fanen *Status* – kun den offentlige profil gemmes
- **26 sprog mere**: fransk, spansk, portugisisk (Brasilien), italiensk, nederlandsk, dansk, svensk, norsk, finsk,
  polsk, tjekkisk, ungarsk, rumænsk, græsk, tyrkisk, serbisk, arabisk, hindi, indonesisk, vietnamesisk, thai, kinesisk,
  japansk og koreansk (AI-oversættelser – rettelser modtages gerne)
- **Sprogvælger** i *Appindstillinger* (på Android 13+ også i Androids appindstillinger)

### Forbedret
- Mindre batteriforbrug: »Sat på pause« forsvinder til tiden, mens telefonen sover; ingen opvågninger, mens visningen er slået
  fra, eller Discord mangler; nye forbindelsesforsøg sker med stigende pauser
- Mindre netværks- og hukommelsesforbrug: covers tjekkes sjældnere igen, ukendte albums søges ikke igen i 12 timer,
  billeder smides væk, når appen er i baggrunden
- Rettelser: importerede indstillinger korrigeres, afspilning bringer visningen tilbage efter et stop fra opgavestyringen

## 1.1.0

### Nyt
- **Appindstillinger** i menuen med tre prikker: temaet *Følg systemet* / *Lyst* / *Mørkt*, **AMOLED-sort** til mørk tilstand og
  **Material You**-farver (Android 12+)
- **Om**: udviklerens GitHub-billede og centrerede links
- **Skiftende tekster**: `%artist% || %title%` viser kunstneren og titlen på skift (*Skifteinterval* under fanen
  *Visning*, 5–60 s)
- **Nyt appikon** i Poweramps stil: en taleboble med equalizer-søjler (også som tematiseret ikon på Android 13+);
  logoet i appen, startskærmen og Discord-billederne passer til

### Mere flydende
- Optimerede builds (R8 + baseline profile): skærme og faneskift hakker ikke længere
- Indstillinger, Poweramps tilstand og billeder indlæses i baggrunden
- Live-forhåndsvisningen opdaterer kun afspilningspositionen hvert sekund; faner længere væk åbner med det samme; loggen ruller jævnt

## 1.0.0

### Nyt
- Første udgivelse: **Discord Rich Presence til Poweramp** – virker med både stabile builds og betabuilds af Poweramp
- **»Lytter til Poweramp«** med en statuslinje (eller »Spiller« med den forløbne tid)
- **Covers uden streamingtjeneste**: det cover, Poweramp viser, uploades til x0.at, catbox.moe eller Imgur,
  plus valgfri onlinesøgning (Spotify, Deezer, Apple Music / iTunes, Bandcamp, Discogs,
  MusicBrainz + Cover Art Archive)
- Tekstskabeloner med pladsholdere (`%title%`, `%artist%`, `%album%`, `%bar%` …) og en live-forhåndsvisning af, hvad
  Discord viser
- Adfærd ved pause (vis »Sat på pause« eller skjul, fjern efter en pause) og filtre for streams og mapper
- Søgning efter opdateringer (GitHub-udgivelser)
- Engelsk, tysk, russisk og ukrainsk
