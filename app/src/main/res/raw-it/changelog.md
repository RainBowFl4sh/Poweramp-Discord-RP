# Registro modifiche

## 1.2.0

### Novità
- **Collega il tuo account Discord** (scheda *Stato*): il tuo vero nome e l’immagine del profilo nell’anteprima live e
  nella scheda *Stato* – viene conservato solo il profilo pubblico
- **26 nuove lingue**: francese, spagnolo, portoghese (Brasile), italiano, olandese, danese, svedese, norvegese,
  finlandese, polacco, ceco, ungherese, rumeno, greco, turco, serbo, arabo, hindi, indonesiano, vietnamita, tailandese,
  cinese, giapponese e coreano (traduzioni IA – correzioni benvenute)
- **Selezione della lingua** in *Impostazioni app* (da Android 13 in poi anche nelle impostazioni app di Android)

### Miglioramenti
- Minor consumo di batteria: “In pausa” scompare puntualmente anche mentre il telefono è in standby; nessun risveglio mentre la
  presenza è disattivata o Discord non è installato; le riconnessioni si diradano
- Meno traffico di rete e memoria: le copertine vengono ricontrollate meno spesso, gli album sconosciuti non vengono ricercati di
  nuovo per 12 h, le immagini vengono rimosse dalla memoria quando l’app è in background
- Correzioni: le impostazioni importate vengono verificate e corrette; premendo play la presenza torna dopo una chiusura dal task manager

## 1.1.0

### Novità
- **Impostazioni app** nel menu a tre puntini: tema *Predefinito di sistema* / *Chiaro* / *Scuro*, **Nero AMOLED**
  per la modalità scura e colori **Material You** (Android 12+)
- **Info**: l’immagine GitHub dell’autore e link centrati
- **Testi a rotazione**: `%artist% || %title%` mostra a turno l’artista e il titolo (*Intervallo di rotazione* nella
  scheda *Aspetto*, 5–60 s)
- **Nuova icona dell’app** nello stile di quella di Poweramp: un fumetto con barre dell’equalizzatore (anche come
  icona a tema di Android 13+); il logo nell’app, la schermata iniziale e le immagini di Discord sono coordinati

### Maggiore fluidità
- Build ottimizzate (R8 + baseline profile): schermate e cambi di scheda non scattano più
- Impostazioni, stato di Poweramp e immagini vengono caricati in background
- L’anteprima live aggiorna la posizione di riproduzione solo ogni secondo; le schede lontane si aprono subito; il log
  scorre in modo fluido

## 1.0.0

### Novità
- Prima release: **Discord Rich Presence per Poweramp** – funziona con le build stabili e beta di Poweramp
- **“Sta ascoltando Poweramp”** con una barra di avanzamento (o “Sta giocando” con il tempo trascorso)
- **Copertine senza servizio di streaming**: la copertina mostrata da Poweramp viene caricata su x0.at, catbox.moe
  o Imgur, più ricerche online opzionali (Spotify, Deezer, Apple Music / iTunes, Bandcamp, Discogs,
  MusicBrainz + Cover Art Archive)
- Modelli di testo con segnaposto (`%title%`, `%artist%`, `%album%`, `%bar%` …) e un’anteprima live di ciò che
  mostra Discord
- Comportamento in pausa (mostra “In pausa” o nascondi, rimuovi dopo una pausa) e filtri per stream e cartelle
- Controllo aggiornamenti (release su GitHub)
- Inglese, tedesco, russo e ucraino
