# Istorija promena

## 1.2.0

### Novo
- **Povežite Discord nalog** (kartica *Status*): vaše pravo ime i slika profila u pregledu uživo i na kartici
  *Status* – čuva se samo javni profil
- **Još 26 jezika**: francuski, španski, portugalski (Brazil), italijanski, holandski, danski, švedski, norveški, finski,
  poljski, češki, mađarski, rumunski, grčki, turski, srpski, arapski, hindi, indonežanski, vijetnamski, tajlandski, kineski,
  japanski i korejski (AI prevodi – ispravke su dobrodošle)
- **Izbor jezika** u *Podešavanjima aplikacije* (na Androidu 13+ i u Android podešavanjima za aplikaciju)

### Poboljšano
- Manja potrošnja baterije: „Pauzirano“ nestaje na vreme i dok telefon spava; nema buđenja dok je status isključen ili
  Discord nije instaliran; ponovna povezivanja se proređuju
- Manja potrošnja mreže i memorije: omoti se ređe ponovo proveravaju, nepoznati albumi se ne traže ponovo 12 h,
  slike se oslobađaju kada je aplikacija u pozadini
- Ispravke: uvezena podešavanja se ispravljaju, reprodukcija vraća status posle zaustavljanja iz menadžera zadataka

## 1.1.0

### Novo
- **Podešavanja aplikacije** u meniju sa tri tačke: tema *Prati sistem* / *Svetla* / *Tamna*, **AMOLED crna** za tamni režim i
  **Material You** boje (Android 12+)
- **O aplikaciji**: autorova GitHub slika i centrirani linkovi
- **Tekstovi koji se smenjuju**: `%artist% || %title%` naizmenično prikazuje izvođača i naslov (*Interval smenjivanja* na
  kartici *Prikaz*, 5–60 s)
- **Nova ikona aplikacije** u stilu Poweramp ikone: oblačić sa trakama ekvilajzera (i kao tematska ikona na Androidu 13+);
  logo u aplikaciji, uvodni ekran i Discord slike su usklađeni

### Glatkije
- Optimizovani buildovi (R8 + baseline profile): ekrani i prebacivanje kartica više ne seckaju
- Podešavanja, stanje Poweramp-a i slike se učitavaju u pozadini
- Pregled uživo ažurira poziciju reprodukcije samo jednom u sekundi; udaljene kartice se odmah otvaraju; evidencija se glatko pomera

## 1.0.0

### Novo
- Prvo izdanje: **Discord Rich Presence za Poweramp** – radi sa stabilnim i beta buildovima Poweramp-a
- **„Sluša Poweramp“** sa trakom napretka (ili „Igra“ sa proteklim vremenom)
- **Omoti bez striming servisa**: omot koji Poweramp prikazuje otprema se na x0.at, catbox.moe ili Imgur,
  uz opcionu pretragu na internetu (Spotify, Deezer, Apple Music / iTunes, Bandcamp, Discogs,
  MusicBrainz + Cover Art Archive)
- Šabloni teksta sa čuvarima mesta (`%title%`, `%artist%`, `%album%`, `%bar%` …) i pregled uživo onoga što
  Discord prikazuje
- Ponašanje pri pauzi (prikaži „Pauzirano“ ili sakrij, ukloni posle pauze) i filteri za strimove i foldere
- Provera ažuriranja (GitHub izdanja)
- Engleski, nemački, ruski i ukrajinski
