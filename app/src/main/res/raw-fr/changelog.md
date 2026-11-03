# Historique des versions

## 1.2.1

### Améliorations
- **Service en arrière-plan** : ne démarre que lorsqu’un titre est vraiment lu, plus dès l’ouverture de Poweramp, et
  s’arrête de lui-même quand plus rien ne joue (après le délai de pause)
- **Envoi des pochettes** : noms de fichiers aléatoires au lieu de « cover.jpg » – x0.at refusait les envois

## 1.2.0

### Nouveautés
- **Associez votre compte Discord** (onglet *État*) : votre vrai nom et votre photo de profil dans l’aperçu en direct et
  dans l’onglet *État* – seul le profil public est conservé
- **26 langues supplémentaires** : français, espagnol, portugais (Brésil), italien, néerlandais, danois, suédois,
  norvégien, finnois, polonais, tchèque, hongrois, roumain, grec, turc, serbe, arabe, hindi, indonésien, vietnamien,
  thaï, chinois, japonais et coréen (traductions par IA – corrections bienvenues)
- **Choix de la langue** dans les *Paramètres de l’app* (et, sous Android 13+, dans les paramètres Android de l’app)

### Améliorations
- Moins de batterie : « En pause » disparaît à temps, même quand le téléphone est en veille ; aucun réveil quand la
  présence est désactivée ou que Discord est absent ; les reconnexions s’espacent
- Moins de réseau et de mémoire : les pochettes sont revérifiées moins souvent, les albums inconnus ne sont pas
  recherchés à nouveau pendant 12 h, les images sont libérées quand l’app est en arrière-plan
- Corrections : les paramètres importés sont rectifiés, la lecture rétablit la présence après un arrêt depuis le
  gestionnaire de tâches

## 1.1.0

### Nouveautés
- **Paramètres de l’app** dans le menu à trois points : thème *Valeur par défaut du système* / *Clair* / *Sombre*, **noir AMOLED**
  pour le mode sombre et couleurs **Material You** (Android 12+)
- **À propos** : la photo GitHub de l’auteur et des liens centrés
- **Textes en rotation** : `%artist% || %title%` affiche l’artiste et le titre à tour de rôle (*Intervalle de rotation*
  dans l’onglet *Affichage*, 5–60 s)
- **Nouvelle icône** dans le style de celle de Poweramp : une bulle de dialogue avec des barres d’égaliseur (aussi en
  icône à thème sous Android 13+) ; le logo dans l’app, l’écran de démarrage et les images Discord sont assortis

### Plus fluide
- Builds optimisés (R8 + baseline profile) : les écrans et les changements d’onglet ne saccadent plus
- Les paramètres, l’état de Poweramp et les images se chargent en arrière-plan
- L’aperçu en direct ne met à jour la position de lecture qu’une fois par seconde ; les onglets éloignés s’ouvrent
  immédiatement ; le journal défile en douceur

## 1.0.0

### Nouveautés
- Première version : **Discord Rich Presence pour Poweramp** – fonctionne avec les versions stable et bêta de Poweramp
- **« Écoute Poweramp »** avec une barre de progression (ou « Joue » avec le temps écoulé)
- **Pochettes sans service de streaming** : la pochette affichée par Poweramp est mise en ligne sur x0.at, catbox.moe
  ou Imgur, avec en option des recherches en ligne (Spotify, Deezer, Apple Music / iTunes, Bandcamp, Discogs,
  MusicBrainz + Cover Art Archive)
- Modèles de texte avec variables (`%title%`, `%artist%`, `%album%`, `%bar%`…) et aperçu en direct de ce
  qu’affiche Discord
- Comportement en pause (afficher « En pause » ou masquer, retirer après une pause) et filtres pour les flux et dossiers
- Recherche de mises à jour (versions GitHub)
- Anglais, allemand, russe et ukrainien
