# Ändringslogg

## 1.2.0

### Nytt
- **Länka ditt Discord-konto** (fliken *Status*): ditt riktiga namn och din profilbild i liveförhandsvisningen och på
  fliken *Status* – endast den offentliga profilen sparas
- **26 nya språk**: franska, spanska, portugisiska (Brasilien), italienska, nederländska, danska, svenska, norska, finska,
  polska, tjeckiska, ungerska, rumänska, grekiska, turkiska, serbiska, arabiska, hindi, indonesiska, vietnamesiska,
  thailändska, kinesiska, japanska och koreanska (AI-översättningar – rättelser välkomnas)
- **Språkval** i *Appinställningar* (från Android 13 även i Androids appinställningar)

### Förbättrat
- Lägre batteriförbrukning: ”Pausad” försvinner i tid även när telefonen är i viloläge; inga väckningar medan närvaron är av eller Discord
  saknas; nya anslutningsförsök väntar allt längre
- Mindre nätverkstrafik och minnesanvändning: omslag kontrolleras om mer sällan, okända album söks inte igen på 12 h,
  bilder släpps när appen är i bakgrunden
- Rättelser: importerade inställningar korrigeras, när uppspelningen startar visas närvaron igen efter ett stopp via aktivitetshanteraren

## 1.1.0

### Nytt
- **Appinställningar** i menyn med tre punkter: temat *Följ systemet* / *Ljust* / *Mörkt*, **AMOLED-svart** för mörkt läge och
  **Material You**-färger (Android 12+)
- **Om**: utvecklarens GitHub-bild och centrerade länkar
- **Växlande texter**: `%artist% || %title%` visar artisten och titeln växelvis (*Växlingsintervall* på
  fliken *Visning*, 5–60 s)
- **Ny appikon** i samma stil som Poweramps: en pratbubbla med equalizerstaplar (även som tematisk ikon i Android 13+);
  logotypen i appen, startskärmen och Discord-bilderna matchar

### Smidigare
- Optimerade byggen (R8 + baseline-profil): skärmar och flikbyten hackar inte längre
- Inställningar, Poweramps tillstånd och bilder läses in i bakgrunden
- Liveförhandsvisningen uppdaterar bara uppspelningspositionen varje sekund; avlägsna flikar öppnas direkt; loggen rullar mjukt

## 1.0.0

### Nytt
- Första versionen: **Discord Rich Presence för Poweramp** – fungerar med både stabila versioner och betaversioner av Poweramp
- **”Lyssnar på Poweramp”** med förloppsindikator (eller ”Spelar” med förfluten tid)
- **Omslag utan streamingtjänst**: omslaget som Poweramp visar laddas upp till x0.at, catbox.moe eller Imgur,
  plus valfria sökningar online (Spotify, Deezer, Apple Music / iTunes, Bandcamp, Discogs,
  MusicBrainz + Cover Art Archive)
- Textmallar med platshållare (`%title%`, `%artist%`, `%album%`, `%bar%` …) och en liveförhandsvisning av vad
  Discord visar
- Beteende vid paus (visa ”Pausad” eller dölj, ta bort efter en paus) och filter för strömmar och mappar
- Uppdateringskontroll (GitHub-versioner)
- Engelska, tyska, ryska och ukrainska
