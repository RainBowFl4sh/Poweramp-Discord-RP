# Endringslogg

## 1.2.1

### Forbedret
- **Bakgrunnstjeneste**: starter først når et spor faktisk spilles, ikke allerede når Poweramp åpnes, og stopper selv
  når ingenting spilles lenger (etter pausegrensen)
- **Opplasting av omslag**: tilfeldige filnavn i stedet for «cover.jpg» – x0.at hadde begynt å avvise opplastinger

## 1.2.0

### Nytt
- **Koble til Discord-kontoen din** (fanen *Status*): det ekte navnet og profilbildet ditt i forhåndsvisningen og i fanen
  *Status* – bare den offentlige profilen lagres
- **26 språk til**: fransk, spansk, portugisisk (Brasil), italiensk, nederlandsk, dansk, svensk, norsk, finsk,
  polsk, tsjekkisk, ungarsk, rumensk, gresk, tyrkisk, serbisk, arabisk, hindi, indonesisk, vietnamesisk, thai, kinesisk,
  japansk og koreansk (KI-oversettelser – rettelser er velkomne)
- **Språkvelger** i *Appinnstillinger* (på Android 13+ også i Androids appinnstillinger)

### Forbedret
- Mindre batteribruk: «Satt på pause» forsvinner i tide mens telefonen sover; ingen vekking mens statusen er av eller Discord
  mangler; nye tilkoblingsforsøk kommer sjeldnere og sjeldnere
- Mindre nettverks- og minnebruk: omslag sjekkes sjeldnere på nytt, ukjente album søkes ikke opp igjen på 12 t,
  bilder forkastes når appen er i bakgrunnen
- Rettelser: importerte innstillinger korrigeres, statusen kommer tilbake ved avspilling etter et stopp fra oppgavebehandleren

## 1.1.0

### Nytt
- **Appinnstillinger** i menyen med tre prikker: temaene *Følg systemet* / *Lyst* / *Mørkt*, **AMOLED-svart** for mørk modus og
  **Material You**-farger (Android 12+)
- **Om**: utviklerens GitHub-bilde og sentrerte lenker
- **Vekslende tekster**: `%artist% || %title%` viser artisten og tittelen etter tur (*Vekslingsintervall* i fanen
  *Visning*, 5–60 s)
- **Nytt appikon** i Poweramps stil: en snakkeboble med equalizer-søyler (også som tematisk ikon for Android 13+);
  logoen i appen, oppstartsskjermen og Discord-bildene er tilpasset

### Smidigere
- Optimaliserte builder (R8 + baseline-profil): skjermer og fanebytter hakker ikke lenger
- Innstillinger, Poweramps tilstand og bilder lastes inn i bakgrunnen
- Forhåndsvisningen oppdaterer avspillingsposisjonen bare hvert sekund; faner langt unna åpnes med en gang; loggen ruller jevnt

## 1.0.0

### Nytt
- Første utgivelse: **Discord Rich Presence for Poweramp** – fungerer med både stabile og beta-builder av Poweramp
- **«Lytter til Poweramp»** med fremdriftslinje (eller «Spiller» med medgått tid)
- **Omslag uten strømmetjeneste**: omslaget Poweramp viser, lastes opp til x0.at, catbox.moe eller Imgur,
  pluss valgfrie søk på nettet (Spotify, Deezer, Apple Music / iTunes, Bandcamp, Discogs,
  MusicBrainz + Cover Art Archive)
- Tekstmaler med plassholdere (`%title%`, `%artist%`, `%album%`, `%bar%` …) og forhåndsvisning av det
  Discord viser
- Oppførsel ved pause (vis «Satt på pause» eller skjul, fjern etter en pause) og filtre for strømmer og mapper
- Oppdateringssjekk (GitHub-utgivelser)
- Engelsk, tysk, russisk og ukrainsk
