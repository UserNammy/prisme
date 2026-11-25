# Publier une version de Prism

Le principe : GitHub compile et signe l'APK, le publie dans une *release*, et
l'appli installée chez l'utilisateur le propose toute seule au démarrage.

## Une seule fois : la clé de signature

Toutes les versions doivent être signées avec **la même clé**, sinon Android refuse
d'installer la mise à jour par-dessus l'appli existante.

1. Sur ton PC :

   ```powershell
   powershell -ExecutionPolicy Bypass -File scripts\creer-cle-signature.ps1
   ```

   Le script crée `%USERPROFILE%\prism-release.jks`, complète `local.properties` et
   copie la clé (en base64) dans le presse-papiers.
2. Sur GitHub, dans **Settings → Secrets and variables → Actions**, crée :
   - `RELEASE_KEYSTORE_BASE64` : le contenu du presse-papiers ;
   - `RELEASE_STORE_PASSWORD` et `RELEASE_KEY_PASSWORD` : le mot de passe choisi ;
   - `RELEASE_KEY_ALIAS` : `prism` ;
   - (optionnel) `TMDB_BEARER_TOKEN` : le jeton TMDB, pour les affiches.
3. **Sauvegarde la clé et son mot de passe** ailleurs. Si tu la perds, il faudra
   désinstaller l'appli (et perdre ses réglages) pour passer à une nouvelle clé.

## À chaque version

```powershell
git tag v0.2.0
```

```powershell
git push origin v0.2.0
```

La CI lance les tests, compile l'APK release signé (`prism-<n>.apk`) et crée la
release GitHub. L'appli vérifie les mises à jour au démarrage (au plus une fois par
6 heures) et propose l'installation ; on peut aussi forcer depuis
**Réglages → Mises à jour**.

Chaque push (sans tag) produit aussi un APK téléchargeable dans l'onglet
**Actions** du dépôt, pour tester avant de publier.

## Première installation sur une TV

L'appli doit être installée une première fois à la main (ADB ou clé USB) avec un
APK **release** signé avec la clé ci-dessus : la version debug, plus lente, n'est
pas faite pour l'usage quotidien. À la première mise à jour, Android demande
d'autoriser Prism à installer des applications : accepter une fois.
