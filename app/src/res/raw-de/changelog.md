# Änderungen

## 1.1.0

### Neu
- **App-Einstellungen** im Drei-Punkte-Menü: Design *Wie im System* / *Hell* / *Dunkel*, **AMOLED-Schwarz** für den
  dunklen Modus und **Material-You**-Farben (ab Android 12)
- **Über**: das GitHub-Bild des Autors und zentrierte Links
- **Wechselnde Texte**: `%artist% || %title%` zeigt Interpret und Titel im Wechsel (*Wechselintervall* im Tab
  *Anzeige*, 5–60 s)
- **Neues App-Symbol** im Stil von Poweramps Symbol: eine Sprechblase mit Equalizer-Balken (auch als Designsymbol ab
  Android 13); das Logo in der App, der Startbildschirm und die Discord-Bilder passen dazu

### Flüssiger
- Optimierte Builds (R8 + Baseline-Profil): Seiten und Tab-Wechsel ruckeln nicht mehr
- Einstellungen, der Zustand von Poweramp und Bilder laden im Hintergrund
- Die Live-Vorschau aktualisiert jede Sekunde nur die Abspielposition; entfernte Tabs öffnen sofort; das Protokoll
  scrollt flüssig

## 1.0.0

### Neu
- Erste Version: **Discord Rich Presence für Poweramp** – funktioniert mit den stabilen und den Beta-Versionen
  von Poweramp
- **„Hört Poweramp“** mit Fortschrittsbalken (oder „Spielt“ mit der verstrichenen Zeit)
- **Cover ohne Streamingdienst**: Das Cover, das Poweramp anzeigt, wird zu x0.at, catbox.moe oder Imgur
  hochgeladen; dazu optional die Online-Suche (Spotify, Deezer, Apple Music / iTunes, Bandcamp, Discogs,
  MusicBrainz + Cover Art Archive)
- Textvorlagen mit Platzhaltern (`%title%`, `%artist%`, `%album%`, `%bar%` …) und eine Live-Vorschau dessen,
  was Discord zeigt
- Verhalten bei Pause („Pausiert“ zeigen oder ausblenden, nach einer Pause entfernen) und Filter für Streams
  und Ordner
- Update-Check (GitHub-Releases)
- Englisch, Deutsch, Russisch und Ukrainisch
