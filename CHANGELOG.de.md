# Änderungen

## 1.1.0

### Neu
- **App-Einstellungen** im Drei-Punkte-Menü oben rechts: Design *Wie im System* / *Hell* / *Dunkel*, **AMOLED-Schwarz**
  für den dunklen Modus (rein schwarzer Hintergrund) und dynamische **Material-You**-Farben ab Android 12 (standardmäßig
  an; aus = die eigenen, Discord-ähnlichen Farben der App). Fenster und Symbole der Systemleisten folgen dem gewählten
  Design vom ersten Bild an
- Tab **Über**: das GitHub-Profilbild des Autors, ein zentrierter Kopfbereich und zentrierte Link-Schaltflächen
- **Wechselnde Texte**: `||` zwischen Alternativen lässt sie sich abwechseln, z. B. `%artist% || %title%` - in beiden
  Zeilen und beiden Tooltips, alle 5-60 Sekunden (*Wechselintervall* im Tab *Anzeige*, standardmäßig 10; ein `||`-Chip
  fügt das Trennzeichen ein). Jeder Song beginnt mit der ersten Alternative, leere werden übersprungen, und die
  Live-Vorschau wechselt mit
- **Neues App-Symbol** im Stil von Poweramps Symbol: eine weiße Sprechblase mit Equalizer-Balken auf einer dunklen
  Scheibe mit hellblauem Rand (ab Android 13 auch als Designsymbol). Das Logo in der App, der Startbildschirm (ab
  Android 12) und die Discord-Bilder in `docs/assets/` passen dazu

### Flüssiger
- Release-Builds werden mit R8 optimiert und bringen ein Baseline-Profil mit: Seiten und Tab-Wechsel ruckeln nicht mehr
  (die Release-APK schrumpft von etwa 13 MB auf etwa 2 MB)
- Einstellungen, der Zustand von Poweramp, Cover und Bilder laden im Hintergrund, statt beim Öffnen oder Zurückkehren
  die Oberfläche aufzuhalten
- Die Live-Vorschau aktualisiert jede Sekunde nur noch die Abspielposition statt den ganzen Tab *Anzeige*
- Ein weit entfernter Tab erscheint sofort, statt durch alle Tabs dazwischen zu gleiten
- Das Protokoll im Tab *Erweitert* scrollt flüssig und öffnet beim neuesten Eintrag; Bilder brauchen deutlich weniger
  Speicher

## 1.0.0

Erste Version - das Android-Gegenstück zum AIMP-Discord-Rich-Presence-Plugin (1.5.3), dessen Funktionen auf
Poweramp und die Discord-App übertragen wurden.

### Neu
- **Poweramp als Discord Rich Presence**: Songtitel, Interpret und Album mit Discords eigenem Fortschrittsbalken
  („Hört Poweramp“) oder als „Spielt“ mit der verstrichenen Zeit
- **Poweramp Stable und Beta**: Die App nutzt die öffentliche Intent-API von Poweramp (`TRACK_CHANGED_EXPLICIT` /
  `STATUS_CHANGED_EXPLICIT`, Build 948 oder neuer), geprüft mit Build 1025 (Stable) und 1032 (Beta)
- **Discord-Application eingebaut** („Poweramp“): nichts einzurichten. Ihr Play-/Pause-Bild und Logo werden als
  Bild-URL gesendet (über Discords öffentliche API ermittelt), weil die Discord-App für Android keine Asset-Namen
  anzeigt; die Live-Vorschau zeigt dieselben Bilder und denselben Namen
- **Offizielle Discord-App**: Die Presence läuft über den Rich-Presence-Dienst, den Discord für das Social SDK in
  seine Android-App eingebaut hat - dasselbe JSON wie bei Discords Desktop-RPC. Kein Login, kein Token, kein
  Self-Bot. Die Schnittstelle ist nicht dokumentiert (per Reverse Engineering ermittelt) und kann sich mit einem
  Discord-Update ändern
- **Album-Cover**, ohne von einem Streamingdienst abzuhängen: Das Cover, das Poweramp anzeigt (eingebettet,
  Ordnerbild oder von Poweramp heruntergeladen, gelesen über Poweramps eigenen Cover-Provider), wird auf 512x512
  zugeschnitten, als JPEG zu **x0.at** (ohne Konto, der Standard), catbox.moe oder Imgur (Client-ID) hochgeladen.
  Jeder Upload wird geprüft, bevor Discord den Link bekommt; fällt das gewählte Ziel aus, springt x0.at oder
  catbox.moe ein, und der ausgefallene Dienst wird 30 Minuten übersprungen (eine Minute, wenn er gar nicht
  geantwortet hat, z. B. weil das Handy offline war). Ein Bild wird nur einmal hochgeladen, egal wie viele Titel es
  zeigen
- Online-Cover-Suche ohne Upload: Spotify, Deezer, Apple Music / iTunes, Bandcamp, Discogs, MusicBrainz + Cover Art
  Archive, mit Prüfung des Interpreten bei jedem Treffer (in jeder Schrift und mit Schreibweisen wie „Beyoncé“ oder
  „Jay–Z“). Eine ausfallende Quelle hält die anderen nicht auf; eingefügte Schlüssel und Tokens werden von
  unsichtbaren Zeichen befreit
- Cover werden auf dem Handy zwischengespeichert; hochgeladene Cover, die älter als 3 Tage sind, werden erneut
  geprüft und neu hochgeladen, wenn der Dienst sie gelöscht hat (behalten, wenn sie sich gerade nicht prüfen lassen,
  z. B. offline). Cover, die Discord nicht laden kann („?“), werden erneut gesendet, neu gesucht oder durch das Logo
  ersetzt - nur, wenn Discord sie wirklich nicht laden kann, nicht weil das Handy keine Verbindung hatte
- Poweramp-Logo, wenn kein Cover gefunden wird, Play-/Pause-Symbol als kleines Bild
- Anklickbarer Songtitel (YouTube-Suche nach Interpret + Titel oder ein eigener Link - wird zu einer gültigen
  Webadresse gemacht oder weggelassen, wenn das nicht geht)
- Bei Pause: „Pausiert“ anzeigen, ausblenden (dann erscheinen andere Aktivitäten) oder nach N Minuten ausblenden,
  gezählt ab dem Moment, in dem Poweramp pausiert hat
- Internetradio / Streams und Dateien ausblenden, deren Pfad einen bestimmten Text enthält (z. B. Podcasts, Hörbücher)
- Textvorlagen mit den Platzhaltern `%artist% %title% %album% %filename% %ext% %pos% %dur% %percent% %bar%
  %status% %codec% %bitrate% %samplerate% %bits% %track% %listsize% %rating%`; jeder Text wird auf Discords Grenze
  von 128 Bytes (UTF-8) gekürzt
- Was der Status in der Mitgliederliste zeigt: den Interpreten (Standard), den Namen der Anwendung oder den Songtitel
- Einstellungen in sechs Tabs: *Status*, *Anzeige*, *Cover*, *Quellen*, *Erweitert*, *Über*
- Tab *Status*: Einrichtungs-Checkliste (Poweramp, Discord-App, Benachrichtigungen, Akku-Optimierung, Application
  ID), Verbindungsstatus, *Test-Presence senden* und *Neu verbinden*. Die App fragt beim ersten Start nach der
  Berechtigung für Benachrichtigungen (Android 13+)
- Eine Application ID, die Discord abgelehnt hat, wird als solche gemeldet, bis du sie änderst oder Discord sie
  annimmt (*Neu verbinden* / *Test-Presence senden*); bis dahin startet Poweramp die App nicht im Hintergrund. Ohne
  Application ID wird die App ebenfalls nicht im Hintergrund gestartet
- **Live-Vorschau** der Discord-Karte im Tab *Anzeige*, die sich beim Tippen aktualisiert; ist die Presence
  ausgeblendet, sagt sie, warum
- Tab *Erweitert*: eigene Discord-Anwendung, Kompatibilitätsschalter *Zeitstempel in Sekunden*, aktuelles Log,
  Export / Import der Einstellungen, Zurücksetzen auf die Standardwerte
- Die Einstellungen sind nicht Teil der Android-Sicherung oder der Übertragung von Gerät zu Gerät (sie können deine
  Schlüssel enthalten) - übertragen werden sie mit *Exportieren* / *Importieren*; beim Export weist die App darauf
  hin, dass die Datei das Spotify-Secret, den Discogs-Token und die Imgur-Client-ID enthält
- Tab *Über*: Version, Links, **Update-Check** über die GitHub-Releases (bei jedem Start, täglich, wöchentlich,
  monatlich oder aus) und dieser Changelog
- Deutsch, Englisch, Russisch und Ukrainisch (Russisch und Ukrainisch mit Hilfe von KI übersetzt)
- Läuft als kleiner Vordergrunddienst nur, während Poweramp spielt, und beendet sich eine Minute, nachdem nichts mehr
  angezeigt wird. *Beenden* in der Benachrichtigung - oder die App in Androids Liste der aktiven Apps zu beenden
  (Android 14+) - nimmt die Presence weg, bis die Wiedergabe wieder startet. Wird Poweramp während der Wiedergabe
  zwangsweise beendet, endet die Presence eine Weile nach dem Zeitpunkt, an dem der Song zu Ende gewesen wäre
- Lässt Android den Start im Hintergrund nicht zu, meldet das eine Benachrichtigung - höchstens einmal am Tag
- Änderungen an Discord werden zusammengefasst (höchstens eine alle 2 Sekunden); eine verlorene Verbindung wird
  automatisch wiederhergestellt. Endet die Presence, bekommt Discord das „Löschen“, bevor die App die Verbindung
  trennt, sodass keine veraltete Presence stehen bleibt. Eine Änderung, die Discord ablehnt, wird mit Discords
  Begründung protokolliert, und die Presence gilt als nicht angezeigt, bis Discord wieder eine annimmt
