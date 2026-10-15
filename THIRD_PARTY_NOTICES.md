# Third-party notices

Poweramp Discord Rich Presence (author: Fl4sh) contains or builds on the following third-party work.

## antisocialcord-kt (MIT)

The AIDL interface of the Discord app's Rich Presence service (Discord Social SDK RPC) in
`app/src/main/aidl/com/discord/socialsdk/rpc/` (`IDiscordRpcService`, `IDiscordRpcConnection`,
`IDiscordRpcCallback`) is taken from antisocialcord-kt, which reverse-engineered it from Discord's Social SDK.
Discord does not document this interface for other apps; it may change with a Discord update.

Source: https://github.com/Nightdavisao/antisocialcord-kt

```
Copyright 2026 Nightdavisao

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the “Software”), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
```

## Poweramp API

The app talks to Poweramp through Poweramp's documented public API: the broadcast actions and extras
(`com.maxmpz.audioplayer.TRACK_CHANGED_EXPLICIT`, `com.maxmpz.audioplayer.STATUS_CHANGED_EXPLICIT`, the track
bundle keys, ...) and the album art provider `content://com.maxmpz.audioplayer.aa`. Only these names are used; no
code of the Poweramp API library is included.

Source: https://github.com/maxmpz/powerampapi - its license, for reference:

```
Copyright (C) 2010-2021 Maksim Petrov

Redistribution and use in source and binary forms, with or without
modification, are permitted for themes, skins, widgets, plugins, applications and other software
which communicate with Poweramp music player or Poweramp Equalizer applications on Android platform.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE FOUNDATION OR
CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```

## AIMP Discord Rich Presence

The presence logic (texts, placeholders, paused behaviour, cover resolver, cover repair, update check) and the
settings are ported from the AIMP Discord Rich Presence plugin by the same author (Fl4sh).

Source: https://github.com/RainBowFl4sh/AIMP-Discord-RP-v4-v6--Win32_64Bit--Linux

## Libraries in the APK

These libraries are compiled into the app. All of them are licensed under the
[Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0):

| Library | Source |
|---|---|
| Kotlin standard library | https://github.com/JetBrains/kotlin |
| kotlinx.coroutines | https://github.com/Kotlin/kotlinx.coroutines |
| kotlinx.serialization | https://github.com/Kotlin/kotlinx.serialization |
| OkHttp, Okio | https://github.com/square/okhttp, https://github.com/square/okio |
| AndroidX (Core, Activity, Lifecycle) and Jetpack Compose (UI, Material 3, Material Icons) | https://developer.android.com/jetpack/androidx |

## Images

`docs/assets/poweramp.png`, `play.png` and `pause.png` (the Rich Presence art assets) are original drawings made for
this project by `docs/assets/make_assets.py`, like the app's launcher icon in the style of Poweramp's round icon. No
artwork of Poweramp or Discord is included; the logo (a speech bubble with equalizer bars) is not Poweramp's logo.

## Trademarks

"Poweramp" is a trademark of its owner (Max MP), "Discord" is a trademark of Discord Inc. This project is not
affiliated with or endorsed by either of them. The names are used only to describe what the app works with.
