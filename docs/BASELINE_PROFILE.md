# Baseline profile (à faire sur un PC avec émulateur)

Un *baseline profile* indique à Android quelles parties du code précompiler à
l'installation : lancement, accueil et ouverture d'une chaîne deviennent plus
fluides, surtout sur un boîtier TV lent.

Déjà en place : `androidx.profileinstaller` est inclus, donc les profils fournis
par Compose et Media3 sont appliqués même sur une installation par APK. Le
profil propre à Prism reste à générer.

## Ce qu'il faut

- Un émulateur **Android TV** (API 30 à 34, image *Google APIs*, pas *Play*),
  ou une vraie TV en ADB ;
- une source de test configurée dans l'appli (sinon le parcours s'arrête à
  l'assistant).

## Étapes (à faire ensemble, avec Claude)

1. Ajouter un module `:baselineprofile` (plugin `androidx.baselineprofile`
   1.2.x, `benchmark-macro-junit4`, `uiautomator`) ciblant `:app`.
2. Écrire le parcours : lancement → accueil → descendre dans 2 rangées →
   ouvrir une chaîne → retour.
3. Lancer :

   ```powershell
   .\gradlew.bat :app:generateReleaseBaselineProfile
   ```

4. Le fichier `app/src/release/generated/baselineProfiles/baseline-prof.txt` est
   généré : le commiter. La CI l'embarque ensuite automatiquement dans chaque APK.
