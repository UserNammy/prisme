# Prism — passation IA

Ce document est le point d'entrée pour toute IA ou développeur qui reprend le
projet. Le lire avant de modifier le code.

## But du projet

**Prism** est un lecteur IPTV personnel pour Android TV et Android mobile.
Il ne contient aucun flux, aucune playlist, aucun compte et aucun backend
obligatoire. L’utilisateur renseigne uniquement ses propres sources légitimes
(M3U/M3U8 ou Xtream).

Objectif produit : une application très légère, rapide, lisible à la TV,
agréable sur téléphone et visuellement fidèle à la direction artistique Prism.

## Limites à respecter

- Ne pas ajouter de flux, identifiants ou contenu tiers au dépôt.
- Ne pas contourner les limites de connexion d’un fournisseur, un portail lié à
  une adresse MAC, ou une restriction d’abonnement.
- L’app pourra à terme coordonner une seule lecture entre appareils *Prism*
  appairés, mais elle ne peut pas modifier la limite imposée par un fournisseur.
- Préférer le local-first : les données et identifiants restent sur les appareils
  de l’utilisateur. La configuration TV par QR utilise déjà une petite page LAN
  éphémère, sans cloud.
- Les effets visuels ne doivent jamais dégrader la lecture vidéo. Pas d’upscale
  logiciel lourd ni de filtre GPU permanent ; le décodage matériel et le scaling
  matériel sont le défaut.

## État au dernier commit

Dernier jalon : passe de nommage (Prism, `fr.prisme.player`, français par défaut) et d’optimisation des performances. Testé jusqu’ici en émulateur uniquement.

Fonctionnel dans la base existante :

- sources M3U/M3U8 (adresse web ou fichier envoyé depuis le téléphone, gardé dans
  le stockage privé de l’appli : `LocalPlaylist`) et Xtream ;
- cache Room local, rafraîchissement manuel et planifié ;
- lecture Media3 / ExoPlayer (HLS, DASH, TS), pistes audio et sous-titres ;
- EPG XMLTV lorsque la source le fournit ;
- films, séries, reprise de lecture, favoris, profils et code PIN ;
- page de configuration TV accessible par QR depuis un téléphone sur le même
  Wi-Fi ;
- accueil TV navigable à la télécommande ;
- premier accueil tactile compact sur téléphone : recherche, Direct / Films /
  Séries, liste de contenus et ouverture des réglages ;
- le lecteur vidéo reste en paysage pendant la lecture ;
- interface française pour les parcours importants sur un appareil configuré en
  français.

## Direction artistique Prism

Source de référence locale historique : `Prism — planche DA.html` (fournie par
l’utilisateur lors de la conception). La DA à reproduire :

- fond noir `#000000`, dominant ;
- nuage bleu discret en haut/gauche, nuage rouge discret en bas/droite ;
- tuiles et panneaux : voile blanc à 8 % (`#14FFFFFF`) ;
- retraits, séparateurs et focus de fond : voile blanc à 16 % (`#29FFFFFF`) ;
- bleu d’accent : `#4D86FF`, réservé au focus et à la progression ;
- texte principal `#F4F6F7`, texte secondaire `#B4BBD0` ;
- logo : triangle « play » blanc facetté + mot `prism` en minuscules ;
- éviter les petits traits verticaux latéraux de sélection ;
- les nuages peuvent dériver très lentement, sans animation gourmande et en
  respectant la réduction des animations quand elle est demandée.

Les constantes sont dans :
`app/src/main/java/fr/prisme/player/ui/theme/Theme.kt`

Le fond Canvas animé est dans :
`app/src/main/java/fr/prisme/player/ui/theme/PrismBackground.kt`

## Architecture actuelle

```text
app/src/main/java/fr/prisme/player/
  MainActivity.kt           Routes Compose, bascule TV / mobile
  player/                   Media3, PlayerActivity et overlays
  data/
    m3u/                    Parseur M3U
    xtream/                 Client Xtream
    epg/                    XMLTV
    db/                     Room
    settings/               DataStore + configuration QR LAN
    repo/                   Rafraîchissement du catalogue
  ui/
    channels/               Accueil TV
    mobile/                 Accueil compact tactile
    settings/               Source, lecteur, profils, actualisation
    guide/, detail/, ...    Écrans TV existants
    theme/                  Palette et fond Prism
```

Le package Kotlin, le `namespace` Gradle et l’`applicationId` sont tous
`fr.prisme.player` (migration depuis `nl.vanvrouwerff.iptv` faite). Le nom affiché
est **Prism** partout ; « prisme » ne subsiste que dans l’identifiant technique.

Décision du 25 novembre 2026 : on garde **Prism** (sans E) comme nom affiché, car
le logo, la bannière TV et la planche DA sont dessinés autour de ce nom. Ne pas
« corriger » vers Prisme. Aucun appareil n’a encore Prism installé à cette date,
mais dès la première installation il ne faudra jamais renommer :

- l’`applicationId` `fr.prisme.player` : Android y verrait une autre appli, les
  appareils installés ne recevraient plus les mises à jour et il faudrait tout
  reconfigurer ;
- le dépôt GitHub `UserNammy/prisme` : la mise à jour intégrée y cherche les
  versions (`UPDATE_REPO`).

Le français est la langue par défaut (`values/strings.xml`), il n’y a plus de
ressources néerlandaises. Les titres de rangées internes (`ChannelsUiState.CONTINUE_WATCHING`,
etc.) sont des clés héritées en néerlandais : ne jamais les afficher directement,
passer par `ui/RailTitles.kt` (`displayRailTitle`).

## Décisions déjà prises

- La largeur inférieure à `600dp` utilise `MobileHomeScreen` ; les grands écrans
  gardent `ChannelsScreen`, optimisé télécommande.
- `MainActivity` n’est pas verrouillée en paysage afin que l’accueil fonctionne
  sur téléphone. `PlayerActivity`, elle, reste en paysage.
- L’accueil mobile lit le même cache Room que la TV. Il ne refait pas un import
  réseau et ne crée pas de seconde base de données.
- Le premier écran mobile transmet la liste filtrée au lecteur pour permettre le
  zapping entre chaînes visibles.
- Les réglages ont été rendus moins rigides : largeur maximale `720dp` et marge
  latérale réduite sur téléphone.
- Tous les textes sont dans `values/strings.xml` (français). Pas de texte en dur.
- Défauts pensés pour un foyer français : filtre de catégories Xtream `FR, FRA`,
  langue audio préférée `fr`, bandes-annonces de l’accueil désactivées (lecteur
  YouTube trop lourd pour un petit boîtier, activables dans Réglages).
- **Mises à jour** : `data/update/AppUpdater.kt` lit la dernière release GitHub
  (`BuildConfig.UPDATE_REPO`), compare `prism-<versionCode>.apk` à
  `BuildConfig.VERSION_CODE` et installe via `PackageInstaller`. Vérification au
  démarrage au plus toutes les 6 h (jamais en debug), fenêtre `ui/update/UpdatePrompt.kt`.
  Procédure de publication : `docs/RELEASE.md`.
- **Sauvegarde** : `data/backup/BackupManager.kt` exporte les réglages DataStore et
  les tables utilisateur (profils, favoris, listes, reprises, rappels), pas le
  catalogue. Transport par la page téléphone (`PhoneSetupServer` : `/<jeton>/backup`
  et `POST /<jeton>/restore`), car les boîtiers TV n’ont souvent pas de sélecteur de
  fichiers. Toute nouvelle table utilisateur doit être ajoutée à `USER_TABLES`.
- Option « Reprendre la dernière chaîne » (désactivée par défaut) : `MainActivity`
  ouvre la dernière chaîne en direct du profil actif au démarrage à froid.

## Règles de performance

La cible est un boîtier TV modeste : l’accueil ne doit pas coûter de CPU/GPU au repos.

- Une valeur animée en continu (Ken Burns, pulsation, zoom de focus) se lit dans la
  phase de dessin : `Modifier.deferredScale { v }` / `deferredAlpha { v }`
  (`ui/theme/Focus.kt`), jamais `Modifier.scale(v)` qui recompose tout l’écran à
  chaque image.
- Le fond `PrismBackground` met en cache ses dégradés et ne redessine que lorsque la
  dérive bouge d’environ 1 px (≈ 3 fois par seconde) ; il reste fixe si les
  animations système sont désactivées.
- Ne jamais charger tout le catalogue dans un écran : filtrer, chercher et limiter en
  SQL (`ChannelDao.observeChannelsByType`, `searchChannels`).
- Un `Flow` passé à `collectAsState` doit être dans un `remember`, sinon la requête
  Room redémarre à chaque recomposition.
- `app/compose-stability.conf` déclare les collections Kotlin stables : l’état UI doit
  donc toujours être reconstruit (copy), jamais muté sur place.

## Ce qu’il reste à faire — ordre recommandé

1. Tester sur une vraie TV Android et un téléphone Android avec une source de
   test appartenant à l’utilisateur.
2. Corriger les défauts de navigation, tailles et focus observés sur ces appareils.
3. Finir l’interface tactile : fiches films/séries, guide, profils et réglages
   spécifiquement mobiles ; ne pas simplement comprimer les écrans TV.
4. La francisation est faite (français par défaut) ; garder tout nouveau texte
   dans `values/strings.xml`, jamais en dur.
5. Refaire progressivement les écrans TV restants avec les composants Prism
   (accueil, guide, fiches, réglages, lecteur) sans réintroduire les barres
   latérales de focus.
6. Extraire à terme les données/lecture dans un module `core` partagé avant de
   multiplier les surfaces.
7. Brancher l’appairage TV ↔ téléphone sur le transport LAN : le cœur de bail
   unique expirant est maintenant dans `data/pairing/SinglePlaybackLease.kt` et
   couvert par test. Il manque l’authentification QR, le stockage chiffré des
   pairs et les endpoints LAN qui l’appelleront. Ne jamais présenter ce système
   comme un contournement de limite fournisseur.
8. Ajouter des profils de qualité prudents : par défaut matériel ; désentrelacement
   ou traitement léger uniquement si le matériel tient sans images perdues.

## Vérification / compilation

GitHub Actions (`.github/workflows/android.yml`) lance les tests unitaires et
compile l’APK release à chaque push ; l’APK est téléchargeable dans l’onglet
Actions. Un tag `v*` publie en plus une release (signée, voir `docs/RELEASE.md`).
Ne pas affirmer qu’un changement compile sans une CI verte ou une compilation
locale réussie :

Le checkout principal est placé dans OneDrive : la synchronisation peut
verrouiller `app/build` et provoquer une erreur Gradle du type « Unable to delete
directory ». Dans ce cas, faire la vérification dans une copie ou un worktree
hors d’un dossier synchronisé plutôt que de supprimer des fichiers source.

```powershell
.\gradlew.bat test --no-daemon --console=plain
.\gradlew.bat assembleDebug --no-daemon --console=plain
```

Pré-requis : JDK 17 ou 21, SDK Android configuré via `ANDROID_HOME` ou
`local.properties` (`sdk.dir=...`). Le fichier `local.properties` ne doit jamais
être commité ; il peut contenir le chemin du SDK et, éventuellement, le jeton
TMDB local.

## Travail à plusieurs IA

Avant de modifier :

1. Lire ce fichier, `README.md` et `docs/ROADMAP.md`.
2. Vérifier `git status --short` et ne pas écraser les modifications d’une autre
   IA ou de l’utilisateur.
3. Prendre un périmètre de fichiers distinct quand plusieurs IA travaillent en
   parallèle. Exemples : une IA sur `ui/mobile`, une sur `values/strings.xml`, une sur
   les tests ou la documentation.
4. Ne pas faire de `git reset --hard`, de checkout destructif, ni de migration
   massive de package sans accord explicite.
5. Finir chaque bloc par `git diff --check`, puis un commit petit et descriptif.
6. Rapporter clairement ce qui a été vérifié et ce qui n’a pas pu l’être.

## Liens utiles

- Dépôt public : <https://github.com/UserNammy/prisme>
- Roadmap : `docs/ROADMAP.md`
- Prototype navigateur : `prototype/index.html`
- Licence de la base importée : `LICENSE` et `NOTICE`
