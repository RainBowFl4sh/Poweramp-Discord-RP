# Changelog

## 1.1.0

### New
- **App settings** in the three-dot menu: *Follow system* / *Light* / *Dark* theme, **AMOLED black** for dark mode and
  **Material You** colors (Android 12+)
- **About**: the author's GitHub picture and centered links
- **Rotating texts**: `%artist% || %title%` shows the artist and the title in turns (*Rotation interval* on the
  *Display* tab, 5–60 s)
- **New app icon** in the style of Poweramp's: a speech bubble with equalizer bars (also as an Android 13+ themed
  icon); the in-app logo, the splash screen and the Discord images match

### Smoother
- Optimized builds (R8 + baseline profile): screens and tab switches no longer stutter
- Settings, Poweramp's state and pictures load in the background
- The live preview only updates the playing position every second; distant tabs open at once; the log scrolls smoothly

## 1.0.0

### New
- First release: **Discord Rich Presence for Poweramp** – works with the stable and the beta builds of Poweramp
- **“Listening to Poweramp”** with a progress bar (or “Playing” with the elapsed time)
- **Covers without a streaming service**: the cover Poweramp shows is uploaded to x0.at, catbox.moe or Imgur,
  plus optional online lookups (Spotify, Deezer, Apple Music / iTunes, Bandcamp, Discogs,
  MusicBrainz + Cover Art Archive)
- Text templates with placeholders (`%title%`, `%artist%`, `%album%`, `%bar%` …) and a live preview of what
  Discord shows
- Paused behaviour (show “Paused” or hide, remove after a pause) and filters for streams and folders
- Update check (GitHub releases)
- English, German, Russian and Ukrainian
