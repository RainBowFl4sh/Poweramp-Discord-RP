# Changelog

## 1.2.1

### Improved
- **Background service only while a track plays**: the presence service now starts only when Poweramp really plays a
  track. Opening Poweramp (it reports its last track as paused) or a track change while paused no longer starts it,
  also with "show Paused" - before, the service could run in the background without a single song played. A running
  service still shows "Paused" and stops itself a minute after nothing is shown any more, e.g. after the paused
  timeout ("remove after a pause")
- **Cover uploads**: every upload gets a random file name (10 letters / digits, like the AIMP plugin) instead of
  `cover.jpg` / `cover.png` - x0.at had started to refuse files with that name ("403 File type not allowed.")

## 1.2.0

### New
- **Link your Discord account** (optional, *Discord account* card on the *Status* tab): the live preview and the
  *Status* tab show your real Discord name and profile picture instead of "You". The Discord app only tells
  the presence connection a placeholder user, so the app asks Discord once (OAuth2 with PKCE, scope `identify` only):
  only the public profile (ID, user name, display name, picture) is kept, the access is revoked right away and nothing
  is part of the settings export or of backups. *Refresh*, *Unlink* and plain-word errors included. Whoever owns the
  Discord application sets it up once in the Developer Portal (redirect `powerampdiscordrp://auth/callback`,
  *Public Client* on) - see the README
- **26 more languages**: French, Spanish, Portuguese (Brazil), Italian, Dutch, Danish, Swedish, Norwegian Bokmål,
  Finnish, Polish, Czech, Hungarian, Romanian, Greek, Turkish, Serbian (Cyrillic and Latin), Arabic (right to left),
  Hindi, Indonesian, Vietnamese, Thai, Chinese (Simplified and Traditional), Japanese and Korean - 30 in all. Every
  language except English and German is an AI translation; corrections are welcome via *Report a problem*
- **Language picker** in *App settings* (three-dot menu): *System default* or any of the 30 languages by its own name.
  On Android 13+ it is the system's per-app language, the same as *Settings → Apps → Poweramp Discord RP → Language*;
  on Android 8-12 the app applies it to its screens and notifications

### Improved
- **Battery**: the paused timeout ("remove after a pause") uses an inexact Android alarm, so "Paused" disappears on
  time while the phone sleeps instead of whenever it wakes up next
- **Battery and network**: without the Discord app installed, Poweramp no longer starts and stops the presence service
  with every track; while the presence is switched off, the app no longer wakes up for Poweramp's broadcasts at all;
  reconnects to Discord back off instead of retrying at a fixed pace
- **Network**: a cover uploaded earlier is checked again only every few days (the check is remembered across restarts),
  and an album no source knows is not looked up online again for 12 hours; checks of Discord's image proxy are
  bounded
- **Memory**: pictures of the screens take at most 4 MB and are dropped as soon as the app is in the background (the
  presence service keeps the process alive for hours); several screens asking for one picture share its download
- **Fixes**: settings with unknown or out-of-range values (e.g. from an import) are corrected when they are loaded, so
  the screens and the presence agree; stopped from Android's task manager while paused, one press of play brings
  the presence back; the notification keeps the last error during a retry instead of switching to "Connecting"

## 1.1.0

### New
- **App settings** in the three-dot menu of the top bar: theme *Follow system* / *Light* / *Dark*, **AMOLED black** for
  dark mode (pure black backgrounds) and **Material You** dynamic colors on Android 12+ (on by default; off uses the
  app's own Discord-like colors). The window and the system bar icons follow the chosen theme from the first frame
- **About** tab: the author's GitHub profile picture, a centered header and centered link buttons
- **Rotating texts**: `||` between alternatives lets them take turns, e.g. `%artist% || %title%` - in both lines and
  both tooltips, every 5-60 seconds (*Rotation interval* on the *Display* tab, 10 by default; a `||` chip inserts the
  separator). Every track starts with the first alternative, empty ones are skipped, and the live preview rotates too
- **New app icon** in the style of Poweramp's: a white speech bubble with equalizer bars on a dark disc with a light
  blue rim (also as a themed icon on Android 13+). The logo in the app, the splash screen (Android 12+) and the
  Discord images in `docs/assets/` match

### Smoother
- Release builds are optimized with R8 and carry a baseline profile: screens and tab switches no longer stutter (the
  release APK shrinks from about 13 MB to about 2 MB)
- Settings, Poweramp's state, covers and pictures load in the background instead of holding up the screen when the app
  opens or comes back
- The live preview only updates its playing position every second instead of redrawing the whole *Display* tab
- Jumping to a distant tab shows it at once instead of sliding through every tab in between
- The recent log on the *Advanced* tab scrolls smoothly and opens at its newest entry; pictures need far less memory

## 1.0.0

First version - the Android sibling of the AIMP Discord Rich Presence plugin (1.5.3), with its features ported to
Poweramp and the Discord app.

### New
- **Poweramp as Discord Rich Presence**: song title, artist and album with Discord's native progress bar
  ("Listening to Poweramp"), or as "Playing" with the elapsed time
- **Poweramp stable and beta**: the app uses Poweramp's public intent API (`TRACK_CHANGED_EXPLICIT` /
  `STATUS_CHANGED_EXPLICIT`, build 948 or newer), checked against build 1025 (stable) and 1032 (beta)
- **Discord application built in** ("Poweramp"): nothing to set up. Its play / pause image and logo are sent as
  picture URLs (looked up from Discord's public API), because the Discord Android app shows no asset keys; the live
  preview shows the same pictures and name
- **Official Discord app**: the presence goes through the Rich Presence service Discord added to its Android app
  for the Social SDK - the same JSON as Discord's desktop RPC. No login, no token, no self-bot. The interface is
  undocumented (reverse-engineered) and may change with a Discord update
- **Album covers** without depending on a streaming service: the cover Poweramp shows (embedded, folder image or
  downloaded by Poweramp, read from Poweramp's own album art provider) is cropped to 512x512 JPEG and uploaded to
  **x0.at** (no account, the default), catbox.moe or Imgur (Client-ID). Every upload is checked before Discord gets
  the link; if the chosen host fails, x0.at or catbox.moe stands in and the failed host is skipped for 30 minutes
  (for a minute if it did not answer at all, e.g. while the phone was offline). A picture is uploaded only once,
  however many tracks show it
- Online cover lookup without upload: Spotify, Deezer, Apple Music / iTunes, Bandcamp, Discogs, MusicBrainz + Cover
  Art Archive, with an artist check against every hit (in any script, and with spellings like "Beyoncé" or
  "Jay–Z"). One failing source does not stop the others; pasted keys and tokens are cleaned of invisible characters
- Covers are cached on the phone; uploaded covers older than 3 days are checked again and uploaded again if the
  host deleted them (kept if they cannot be checked right now, e.g. offline). Covers Discord cannot load ("?") are
  sent again, looked up again or replaced by the logo - only when Discord really cannot load them, not because the
  phone had no connection
- Poweramp logo as fallback, play / pause icon as small image
- Clickable song title (YouTube search for artist + title, or an own link - made a valid web address, left out if
  that is impossible)
- Paused behaviour: show "Paused", hide (other activities show instead) or hide after N minutes, counted from the
  moment Poweramp paused
- Hide internet radio / streams and files whose path contains given text (e.g. podcasts, audiobooks)
- Text templates with the placeholders `%artist% %title% %album% %filename% %ext% %pos% %dur% %percent% %bar%
  %status% %codec% %bitrate% %samplerate% %bits% %track% %listsize% %rating%`; every text is cut to Discord's limit
  of 128 bytes (UTF-8)
- What the status in the member list shows: the artist (default), the application name or the song title
- Settings in six tabs: *Status*, *Display*, *Cover art*, *Sources*, *Advanced*, *About*
- *Status* tab: setup checklist (Poweramp, Discord app, notifications, battery optimization, application ID),
  connection state, *Send test presence* and *Reconnect*. The app asks for the notification permission on its first
  start (Android 13+)
- An application ID Discord rejected is reported as such until you change it or Discord accepts it (*Reconnect* /
  *Send test presence*); until then Poweramp does not start the app in the background. Without an application ID
  the app is not started in the background either
- **Live preview** of the Discord card on the *Display* tab, updated while you type; when the presence is hidden it
  says why
- *Advanced* tab: own Discord application, *Timestamps in seconds* compatibility switch, recent log, export /
  import of the settings, reset to defaults
- Settings are not part of Android's backups or device-to-device transfers (they can contain your keys) - move them
  with *Export* / *Import*; the export warns that the file contains the Spotify secret, the Discogs token and the
  Imgur Client-ID
- *About* tab: version, links, **update check** against the GitHub releases (at every start, daily, weekly,
  monthly or off) and this changelog
- English, German, Russian and Ukrainian (Russian and Ukrainian translated with the help of AI)
- Runs as a small foreground service only while Poweramp plays and stops a minute after nothing is shown any more.
  The notification's *Stop* - or stopping the app in Android's list of active apps (Android 14+) - ends the
  presence until playback starts again. If Poweramp is killed while it plays (and cannot report that it stopped),
  the presence ends a while after the song would have ended
- If Android blocks the start in the background, a notification says so - at most once a day
- Discord updates are combined (at most one every 2 seconds); a lost connection is restored automatically. When the
  presence ends, Discord gets the "clear" before the app disconnects, so no outdated presence stays behind. An
  update Discord rejects is logged with Discord's reason, and the presence counts as not shown until Discord accepts
  one
