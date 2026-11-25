# Baseline profile

Un *baseline profile* indique à Android quelles parties du code précompiler à
l'installation : lancement, accueil et ouverture d'une chaîne deviennent plus
fluides, surtout sur un boîtier TV lent.

## État

- `androidx.profileinstaller` est inclus : le profil est appliqué même sur une
  installation par APK (sans Play Store).
- Le module `:baselineprofile` enregistre le parcours (lancement → accueil →
  défilement des rangées → ouverture d'une chaîne → Films / Séries).
- Le profil généré est commité dans
  `app/src/release/generated/baselineProfiles/` (`baseline-prof.txt` et
  `startup-prof.txt`). Chaque build release l'embarque automatiquement
  (`assets/dexopt/baseline.prof` dans l'APK) : la CI n'a rien à faire.

## Quand le régénérer

Après un gros changement d'écran d'accueil, de lecteur ou de navigation, ou une
mise à jour de Compose / Media3. Pas à chaque commit.

## Comment le régénérer

Il faut un émulateur **Android TV** (API 33 ou 34, image *Google APIs* ou
*Android TV*, pas *Play*) démarré, ou une vraie TV en ADB. Aucun accès root
n'est nécessaire à partir d'Android 13.

1. Démarrer l'émulateur, puis installer l'appli (`:app:installRelease` ou le
   dernier APK) et **configurer une source de test** : sans source, le parcours
   s'arrête à l'assistant de bienvenue. La page « Configurer avec le téléphone »
   accepte aussi un POST depuis le PC (`adb forward` sur le port affiché).
   Une playlist M3U de test locale suffit : aucune chaîne n'a besoin de lire.
2. Lancer :

   ```powershell
   .\gradlew.bat :app:generateReleaseBaselineProfile --no-daemon --console=plain
   ```

   Gradle installe une variante non minifiée par-dessus l'appli (les données
   sont conservées), rejoue le parcours et copie le profil dans `app/src/release`.
3. Vérifier `git diff --stat`, puis commiter les deux fichiers `.txt`.

Le parcours est dans
`baselineprofile/src/main/java/fr/prisme/player/baselineprofile/BaselineProfileGenerator.kt`.

## Pièges

- Ne pas lancer `:baselineprofile:assemble` seul : cela déclenche le test sur
  l'appareil connecté.
- Le dépôt est dans OneDrive : si Gradle échoue avec « Unable to delete
  directory » ou `AccessDeniedException` sous `app/build`, compiler dans une copie
  hors OneDrive (voir `AI_HANDOFF.md`).
