# Muutosloki

## 1.2.1

### Parannettu
- **Taustapalvelu**: käynnistyy vasta, kun kappale todella soi, ei jo Powerampin avaamisesta, ja pysähtyy itsestään,
  kun mitään ei enää soi (taukoajan jälkeen)
- **Kansikuvien lataus**: satunnaiset tiedostonimet ”cover.jpg”:n sijaan – x0.at oli alkanut hylätä latauksia

## 1.2.0

### Uutta
- **Yhdistä Discord-tilisi** (*Tila*-välilehti): oikea nimesi ja profiilikuvasi esikatselussa ja
  *Tila*-välilehdellä – vain julkinen profiili tallennetaan
- **26 uutta kieltä**: ranska, espanja, portugali (Brasilia), italia, hollanti, tanska, ruotsi, norja, suomi,
  puola, tšekki, unkari, romania, kreikka, turkki, serbia, arabia, hindi, indonesia, vietnam, thai, kiina,
  japani ja korea (tekoälykäännöksiä – korjaukset tervetulleita)
- **Kielen valinta** kohdassa *Sovelluksen asetukset* (Android 13+:ssa myös Androidin sovellusasetuksissa)

### Parannettu
- Vähemmän akun kulutusta: ”Keskeytetty” katoaa ajallaan myös puhelimen ollessa lepotilassa; ei herätyksiä, kun tila on
  pois päältä tai Discord puuttuu; uudelleenyhdistämiset harvenevat
- Vähemmän verkkoliikennettä ja muistia: kansikuvat tarkistetaan uudelleen harvemmin, tuntemattomia albumeja ei haeta
  uudelleen 12 tuntiin, kuvat vapautetaan, kun sovellus on taustalla
- Korjauksia: tuodut asetukset korjataan, toisto palauttaa tilan tehtävienhallinnasta tehdyn pysäytyksen jälkeen

## 1.1.0

### Uutta
- **Sovelluksen asetukset** kolmen pisteen valikossa: teema *Järjestelmän mukaan* / *Vaalea* / *Tumma*,
  **AMOLED-musta** tummaan tilaan ja **Material You** -värit (Android 12+)
- **Tietoja**: tekijän GitHub-kuva ja keskitetyt linkit
- **Vuorottelevat tekstit**: `%artist% || %title%` näyttää vuorotellen esittäjän ja kappaleen nimen (*Vaihtoväli*
  *Näkymä*-välilehdellä, 5–60 s)
- **Uusi sovelluskuvake** Powerampin tyyliin: puhekupla ja taajuuskorjaimen palkit (myös Android 13+:n teemoitettuna
  kuvakkeena); sovelluksen sisäinen logo, aloitusnäyttö ja Discordin kuvat sopivat yhteen

### Sujuvampi
- Optimoidut koontiversiot (R8 + baseline profile): näkymät ja välilehtien vaihdot eivät enää nyi
- Asetukset, Powerampin tila ja kuvat ladataan taustalla
- Esikatselu päivittää toistokohdan vain kerran sekunnissa; kaukaiset välilehdet avautuvat heti; loki vierii sujuvasti

## 1.0.0

### Uutta
- Ensimmäinen julkaisu: **Discord Rich Presence Powerampille** – toimii Powerampin vakaan ja beetaversion kanssa
- **”Kuuntelee: Poweramp”** edistymispalkin kanssa (tai ”Pelaa” kuluneen ajan kanssa)
- **Kansikuvat ilman suoratoistopalvelua**: Powerampin näyttämä kansikuva ladataan palveluun x0.at, catbox.moe tai Imgur,
  lisäksi valinnaiset verkkohaut (Spotify, Deezer, Apple Music / iTunes, Bandcamp, Discogs,
  MusicBrainz + Cover Art Archive)
- Tekstipohjat paikkamerkeillä (`%title%`, `%artist%`, `%album%`, `%bar%` …) ja esikatselu siitä, mitä
  Discord näyttää
- Toiminta tauolla (näytä ”Keskeytetty” tai piilota, poista tauon jälkeen) ja suodattimet striimeille ja kansioille
- Päivitysten tarkistus (GitHub-julkaisut)
- Englanti, saksa, venäjä ja ukraina
