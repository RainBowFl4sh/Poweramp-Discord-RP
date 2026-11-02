# Istoricul modificărilor

## 1.2.0

### Nou
- **Conectați contul Discord** (fila *Stare*): numele real și fotografia de profil în previzualizarea live și în fila
  *Stare* – se păstrează doar profilul public
- **Încă 26 de limbi**: franceză, spaniolă, portugheză (Brazilia), italiană, neerlandeză, daneză, suedeză, norvegiană,
  finlandeză, poloneză, cehă, maghiară, română, greacă, turcă, sârbă, arabă, hindi, indoneziană, vietnameză, thailandeză,
  chineză, japoneză și coreeană (traduceri AI – corecturile sunt binevenite)
- **Selector de limbă** în *Setările aplicației* (iar pe Android 13+ și în setările Android ale aplicației)

### Îmbunătățit
- Consum mai mic de baterie: „Întrerupt” dispare la timp și când telefonul e în repaus; nicio trezire cât timp prezența
  e dezactivată sau Discord lipsește; reconectările se răresc treptat
- Mai puțin trafic de date și memorie: copertele sunt reverificate mai rar, albumele necunoscute nu mai sunt căutate din nou
  timp de 12 h, imaginile sunt eliberate când aplicația e în fundal
- Remedieri: setările importate sunt corectate, redarea readuce prezența după o oprire din managerul de activități

## 1.1.0

### Nou
- **Setările aplicației** în meniul cu trei puncte: tema *Conform sistemului* / *Luminoasă* / *Întunecată*, **negru AMOLED**
  pentru modul întunecat și culori **Material You** (Android 12+)
- **Despre**: fotografia de profil GitHub a autorului și linkuri centrate
- **Texte care alternează**: `%artist% || %title%` afișează pe rând artistul și titlul (*Interval de rotație* în fila
  *Afișare*, 5–60 s)
- **Pictogramă nouă a aplicației** în stilul celei din Poweramp: un balon de dialog cu bare de egalizator (și ca
  pictogramă tematică pe Android 13+); sigla din aplicație, ecranul de pornire și imaginile din Discord se potrivesc

### Mai fluid
- Build-uri optimizate (R8 + baseline profile): ecranele și comutarea filelor nu se mai blochează
- Setările, starea Poweramp și imaginile se încarcă în fundal
- Previzualizarea live actualizează poziția de redare doar o dată pe secundă; filele îndepărtate se deschid imediat;
  jurnalul se derulează fluid

## 1.0.0

### Nou
- Prima versiune: **Discord Rich Presence pentru Poweramp** – funcționează cu versiunile stabile și beta ale Poweramp
- **„Ascultă Poweramp”** cu bară de progres (sau „Se joacă” cu timpul scurs)
- **Coperte fără serviciu de streaming**: coperta afișată de Poweramp este încărcată pe x0.at, catbox.moe sau Imgur,
  plus căutări online opționale (Spotify, Deezer, Apple Music / iTunes, Bandcamp, Discogs,
  MusicBrainz + Cover Art Archive)
- Șabloane de text cu substituenți (`%title%`, `%artist%`, `%album%`, `%bar%` …) și o previzualizare live a ceea ce
  afișează Discord
- Comportament la pauză (afișare „Întrerupt” sau ascundere, eliminare după o pauză) și filtre pentru fluxuri și dosare
- Căutare de actualizări (versiuni GitHub)
- Engleză, germană, rusă și ucraineană
