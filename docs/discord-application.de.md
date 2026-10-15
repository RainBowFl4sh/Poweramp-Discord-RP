# Die Discord-Anwendung einrichten

**English:** [discord-application.md](discord-application.md) · Zurück zur [README](../README.de.md)

Jede Rich Presence gehört zu einer *Discord-Anwendung*. Sie legt zwei Dinge fest:

- den **Namen**, den Discord zeigt - „Hört **Poweramp**“ in deinem Profil ist der Name der Anwendung, kein Text, den
  die App schickt (die Mitgliederliste zeigt stattdessen den Interpreten, sofern der Tab *Anzeige* der App nichts
  anderes festlegt)
- die **Bilder**, die die Presence über ihren Namen verwenden kann: das Logo und das Play-/Pause-Symbol

**Das braucht nur, wer die APK baut oder veröffentlicht, und nur einmal.** Die Application ID wird in die APK
eingebaut; Nutzer installieren die App und sind fertig. Wer einen anderen Namen oder andere Bilder möchte, kann im
Tab *Erweitert* der App eine eigene Anwendung eintragen - die Schritte sind dieselben.

Mehr braucht es von Discord nicht: keinen Bot, kein OAuth2, kein Client Secret, keinen Token. Die Application ID ist
öffentlich (Discord zeigt sie jedem, der sich die Presence ansieht) und darf daher in `gradle.properties` stehen.

## 1. Anwendung anlegen

1. Das [Discord Developer Portal](https://discord.com/developers/applications) öffnen und mit deinem Discord-Konto
   anmelden
2. **New Application** anklicken (oben rechts)
3. Name: **Poweramp** - das ist genau der Text nach „Hört“ in deinem Profil. Die Developer Terms of Service
   akzeptieren und **Create** anklicken
4. Optional unter *General Information*: `docs/assets/poweramp.png` als *App Icon* hochladen und eine Beschreibung
   eintragen. In der Presence selbst sieht man beides nicht

## 2. Application ID kopieren

Unter *General Information* bei **Application ID** auf **Copy** klicken. Das ist eine Zahl mit 17 bis 20 Ziffern,
z. B. `1234567890123456789`.

## 3. Art Assets hochladen

Die App verwendet drei Bilder über ihren Namen. Im linken Menü **Rich Presence -> Art Assets** öffnen,
**Add Image(s)** anklicken und die Dateien aus [`docs/assets/`](assets) hochladen:

| Name (genau so) | Datei | Größe | Wofür |
|---|---|---|---|
| `poweramp` | <img src="assets/poweramp.png" width="64" alt="poweramp"> `poweramp.png` | 512x512 | großes Bild, wenn kein Cover gefunden wird (und solange das Cover noch gesucht wird); die Test-Presence |
| `play` | <img src="assets/play.png" width="64" alt="play"> `play.png` | 512x512 | kleines Symbol auf dem Cover während der Wiedergabe |
| `pause` | <img src="assets/pause.png" width="64" alt="pause"> `pause.png` | 512x512 | kleines Symbol auf dem Cover bei Pause (*„Pausiert“ anzeigen*) |

- Das Portal übernimmt den Namen aus dem Dateinamen. Prüfe, dass die Namen genau `poweramp`, `play` und `pause`
  lauten (klein geschrieben), bevor du **Save Changes** anklickst
- Die Bilder müssen mindestens 512x512 Pixel groß sein; PNG mit Transparenz ist in Ordnung
- Neue oder geänderte Assets können ein paar Minuten (manchmal länger) brauchen, bis Discord sie zeigt - Discord
  speichert sie zwischen. Bis dahin zeigt die Presence eventuell ein leeres Feld statt des Logos
- *Rich Presence Invite Image* leer lassen - es wird nicht verwendet

**Zu den Bildern:** Alle drei sind eigene Zeichnungen, die für dieses Projekt im Stil des runden Poweramp-Symbols
entstanden sind: eine dunkle Scheibe mit hellblauem Rand und Schein auf transparentem Hintergrund, darauf ein weißes
Play-Dreieck, weiße Pause-Balken oder - in `poweramp.png` - das eigene Logo der App, die Sprechblase mit
Equalizer-Balken aus ihrem App-Symbol. Dieses Logo ist **nicht** das Logo von Poweramp, das eine Marke seines
Inhabers ist. Wenn du die Erlaubnis hast, das echte Poweramp-Symbol zu verwenden, kannst du stattdessen dieses unter
dem Namen `poweramp` hochladen; sonst muss nichts geändert werden. Die Bilder erzeugt
[`docs/assets/make_assets.py`](assets/make_assets.py) (Python 3, nur Standardbibliothek):
`python3 docs/assets/make_assets.py` schreibt sie neu, z. B. nach dem Ändern einer Farbe.

## 4. Optional: Social SDK freischalten

Der Rich-Presence-Dienst der Discord-App gehört zu Discords **Social SDK**. Erscheint die Presence nicht, obwohl
alles andere stimmt, schalte das Social SDK für die Anwendung frei: im linken Menü
**Discord Social SDK -> Getting Started** öffnen und das kurze Formular ausfüllen. Ob das nötig ist, ist **nicht
bestätigt** - ein Schritt zum Ausprobieren, keine Voraussetzung.

## 5. Die ID in den Build eintragen

Eine dieser Möglichkeiten:

- **`gradle.properties`** im Projektordner:

  ```
  discordApplicationId=1234567890123456789
  ```

- **Kommandozeile** (hat Vorrang vor `gradle.properties`):

  ```
  ./gradlew :app:assembleRelease -PdiscordApplicationId=1234567890123456789
  ```

- **GitHub Actions**: im Repository *Settings -> Secrets and variables -> Actions -> New repository secret*, Name
  `DISCORD_APPLICATION_ID`, Wert = die ID. Der Workflow gibt sie an Gradle weiter (siehe
  [Ein Release veröffentlichen](../README.de.md#ein-release-veröffentlichen)). Ein Wert, der nicht aus 17 bis 20
  Ziffern besteht, lässt den Build scheitern; ein Release (`v*`-Tag) ganz ohne ID ebenfalls, andere Builds warnen
  nur

Ohne ID lässt sich die App trotzdem bauen (außer als Release über GitHub Actions); der Tab *Status* meldet dann,
dass keine Application ID eingerichtet ist, und Nutzer müssen im Tab *Erweitert* eine eigene eintragen.

## 6. Prüfen

1. APK installieren, App öffnen und die Einrichtung im Tab *Status* durchgehen (siehe
   [README](../README.de.md#installation-und-einrichtung))
2. *Status -> Test-Presence senden*: Discord zeigt 15 Sekunden lang eine Test-Presence von **Poweramp**
   („Test - Poweramp Discord Rich Presence ...“) mit dem Logo
3. Einen Song in Poweramp abspielen: Das Cover erscheint (oder das Logo) mit dem Play-Symbol in der Ecke

Stimmt der Name, fehlen aber die Bilder, passen die Namen der Assets nicht oder Discord hat sie noch nicht übernommen
(siehe Schritt 3). Erscheint gar nichts, siehe [Fehlerbehebung](../README.de.md#fehlerbehebung).

## Eine eigene Anwendung in der App verwenden (Nutzer)

1. Eine Anwendung wie oben anlegen - mit einem beliebigen Namen („Musik“, „Poweramp“, ...) - und die drei Assets
   `poweramp`, `play` und `pause` hochladen
2. In der App: *Erweitert -> eigene Discord-Anwendung* einschalten und die Application ID einfügen
3. Die App verbindet sich mit der neuen ID neu; *Test-Presence senden* im Tab *Status* zeigt das Ergebnis
