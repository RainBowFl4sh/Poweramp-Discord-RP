# Lista zmian

## 1.3.0

### Nowości
- **Aktualizacje w aplikacji**: *Zainstaluj* na karcie *Informacje* pobiera, sprawdza (SHA-256, podpis) i instaluje
  nową wersję – bez przeglądarki. *Instaluj aktualizacje automatycznie* robi to samo; od Androida 12 bez pytania po
  pierwszym razie
- **Własne teksty dla pauzy i radia** (karta *Wyświetlanie* → *Teksty dla*); puste pola używają tekstu odtwarzania
- **Wesprzyj aplikację**: mały, dobrowolny link PayPal na karcie *Informacje*

### Ulepszenia
- **Sprawdzanie aktualizacji** działa też, gdy limit zapytań GitHuba się wyczerpie (VPN, wspólny adres)
- **Okładki z Deezera**: wyszukiwanie swobodne wykonawca + tytuł, gdy dokładne nic nie znajduje
- **Koniec z zawieszonym „Wstrzymano”**: Discord musi potwierdzić usunięcie statusu, inaczej jest usuwany ponownie; usługa kończy się dopiero potem

## 1.2.1

### Ulepszenia
- **Usługa w tle**: uruchamia się dopiero, gdy utwór naprawdę gra, a nie już przy otwarciu Powerampa, i sama się
  wyłącza, gdy nic już nie gra (po limicie pauzy)
- **Wysyłanie okładek**: losowe nazwy plików zamiast „cover.jpg” – x0.at zaczął odrzucać wysyłki

## 1.2.0

### Nowości
- **Połączenie konta Discord** (karta *Status*): Twoja prawdziwa nazwa i zdjęcie profilowe w podglądzie na żywo i na
  karcie *Status* – zapisywany jest tylko publiczny profil
- **26 nowych języków**: francuski, hiszpański, portugalski (Brazylia), włoski, niderlandzki, duński, szwedzki,
  norweski, fiński, polski, czeski, węgierski, rumuński, grecki, turecki, serbski, arabski, hindi, indonezyjski,
  wietnamski, tajski, chiński, japoński i koreański (tłumaczenia AI – poprawki mile widziane)
- **Wybór języka** w *Ustawieniach aplikacji* (od Androida 13 także w ustawieniach aplikacji w Androidzie)

### Ulepszenia
- Mniejsze zużycie baterii: „Wstrzymano” znika punktualnie, nawet gdy telefon śpi; brak wybudzeń, gdy status jest
  wyłączony lub brakuje Discorda; kolejne próby połączenia odbywają się coraz rzadziej
- Mniejsze zużycie danych i pamięci: okładki są rzadziej sprawdzane ponownie, nieznane albumy nie są wyszukiwane ponownie przez
  12 h, obrazy są zwalniane, gdy aplikacja działa w tle
- Poprawki: zaimportowane ustawienia są korygowane, odtwarzanie przywraca status po zatrzymaniu z menedżera zadań

## 1.1.0

### Nowości
- **Ustawienia aplikacji** w menu z trzema kropkami: motyw *Zgodnie z systemem* / *Jasny* / *Ciemny*, **czerń AMOLED**
  w trybie ciemnym i kolory **Material You** (Android 12+)
- **Informacje**: zdjęcie autora z GitHuba i wyśrodkowane linki
- **Rotujące teksty**: `%artist% || %title%` pokazuje na zmianę wykonawcę i tytuł (*Interwał rotacji* na karcie
  *Wyświetlanie*, 5–60 s)
- **Nowa ikona aplikacji** w stylu ikony Powerampa: dymek z paskami korektora (także jako ikona tematyczna Androida
  13+); logo w aplikacji, ekran powitalny i obrazy w Discordzie są do niej dopasowane

### Płynniejsze działanie
- Zoptymalizowane kompilacje (R8 + profil bazowy): ekrany i przełączanie kart już się nie zacinają
- Ustawienia, stan Powerampa i obrazy wczytują się w tle
- Podgląd na żywo aktualizuje pozycję odtwarzania tylko co sekundę; odległe karty otwierają się od razu; dziennik
  przewija się płynnie

## 1.0.0

### Nowości
- Pierwsze wydanie: **Discord Rich Presence dla Powerampa** – działa ze stabilnymi i beta wersjami Powerampa
- **„Słucha Poweramp”** z paskiem postępu (lub „Gra w” z czasem, który upłynął)
- **Okładki bez serwisu streamingowego**: okładka wyświetlana przez Poweramp jest przesyłana do x0.at, catbox.moe lub
  Imgur, a do tego opcjonalne wyszukiwanie online (Spotify, Deezer, Apple Music / iTunes, Bandcamp, Discogs,
  MusicBrainz + Cover Art Archive)
- Szablony tekstów z symbolami zastępczymi (`%title%`, `%artist%`, `%album%`, `%bar%` …) i podgląd na żywo tego, co
  pokazuje Discord
- Zachowanie po wstrzymaniu (pokazywanie „Wstrzymano” lub ukrywanie, usuwanie po pauzie) oraz filtry dla strumieni i
  folderów
- Sprawdzanie aktualizacji (wydania na GitHubie)
- Angielski, niemiecki, rosyjski i ukraiński
