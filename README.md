# Prism

Lecteur IPTV personnel pour **Android TV** et **Android mobile**, léger et pensé
pour la télécommande. Aucun contenu intégré, aucun compte, aucun serveur : les
playlists et identifiants appartiennent à l'utilisateur et restent sur ses
appareils.

> État : utilisable sur TV (navigation télécommande complète). Le téléphone a un
> premier accueil tactile ; les fiches et réglages mobiles sont encore à affiner.

## Fonctionnalités

- **Sources** : playlists M3U/M3U8 (`tvg-*`, `group-title`) et comptes Xtream Codes
  (direct, films, séries). Cache local Room, rafraîchissement manuel ou nocturne.
- **Direct** : rangées par catégorie, zapping chaîne +/-, saisie numérique, guide
  EPG (XMLTV), rappels de programmes, catch-up quand le fournisseur le permet.
- **Films et séries** : fiches détaillées, reprise de lecture, « Continuer à
  regarder », « Ma liste », épisode suivant automatique.
- **Profils** : plusieurs profils, profil enfant, code PIN.
- **Lecteur** : Media3/ExoPlayer (HLS, DASH, TS), pistes audio et sous-titres,
  décalage des sous-titres, adaptation automatique de la fréquence d'affichage,
  statistiques de flux.
- **Configuration depuis le téléphone** : la TV affiche un QR code qui ouvre une
  petite page servie localement sur le Wi-Fi de la maison (aucun cloud).
- **TMDB (optionnel)** : affiches, résumés, bandes-annonces et « Top 10 ».

## Compiler

Prérequis : JDK 17 ou 21 et le SDK Android (API 34). Copier
`local.properties.example` en `local.properties` et renseigner `sdk.dir`.

```powershell
.\gradlew.bat test
.\gradlew.bat assembleDebug
```

APK : `app/build/outputs/apk/debug/app-debug.apk`.

Jeton TMDB optionnel, dans `local.properties` (jamais commité) :

```
TMDB_BEARER_TOKEN=eyJhbGciOi...
```

## Installer sur une TV

1. Activer les **Options pour les développeurs** (Paramètres → À propos → appuyer
   7 fois sur « Build ») puis le **débogage ADB**.
2. Noter l'adresse IP de la TV (Paramètres → Réseau).
3. Depuis le PC :

```powershell
adb connect <ip-tv>:5555
```

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Au premier lancement, l'assistant demande la source (M3U ou Xtream). Le plus simple
est de passer par le QR code et de taper les identifiants sur le téléphone.

## Structure

```
app/src/main/java/fr/prisme/player/
  PrismApp.kt        Application : singletons (base, réglages, HTTP), config Coil
  MainActivity.kt    Racine Compose, routes, bascule TV / mobile (< 600dp)
  player/            PlayerActivity (ExoPlayer) et overlays
  data/              M3U, Xtream, XMLTV, Room, DataStore, TMDB, rafraîchissement
  ui/                Écrans TV (channels, guide, detail, ...), mobile/, theme/
```

Pour reprendre le projet (humain ou IA), lire d'abord
[`docs/AI_HANDOFF.md`](docs/AI_HANDOFF.md).

## Limites connues

- Le HTTP en clair est autorisé : beaucoup de fournisseurs IPTV ne servent pas
  de HTTPS.
- Compose for TV est en `1.0.0-alpha10` ; un plantage de focus connu est
  intercepté dans `MainActivity.dispatchKeyEvent`.
- Pas de DRM ni de Chromecast.
- L'application ne contourne aucune limite de connexions fournisseur ni portail
  lié à une adresse MAC.

## Licence

Basé sur [SERFF/iptv-app](https://github.com/SERFF/iptv-app), sous licence
Apache 2.0 (voir [`LICENSE`](LICENSE) et [`NOTICE`](NOTICE)). Les modifications
de Prism sont distribuées sous la même licence. Ce produit utilise l'API TMDB
sans être approuvé ni certifié par TMDB.
