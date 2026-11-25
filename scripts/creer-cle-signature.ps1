# Crée la clé de signature release de Prism (à lancer UNE seule fois, sur ton PC).
#
#   powershell -ExecutionPolicy Bypass -File scripts\creer-cle-signature.ps1
#
# - La clé est créée HORS du dépôt (dans ton dossier utilisateur) : ne jamais la commiter.
# - local.properties est complété pour que les builds release locaux soient signés.
# - Le contenu base64 de la clé est copié dans le presse-papiers pour le secret GitHub
#   RELEASE_KEYSTORE_BASE64 (voir docs/RELEASE.md).
#
# Sauvegarde la clé et son mot de passe (gestionnaire de mots de passe, clé USB...) :
# sans elle, impossible de publier une mise à jour installable par-dessus l'appli existante.

$ErrorActionPreference = "Stop"

$repo = Split-Path -Parent $PSScriptRoot
$keystore = Join-Path $env:USERPROFILE "prism-release.jks"
$alias = "prism"

if (Test-Path $keystore) {
    Write-Host "La clé existe déjà : $keystore (rien n'est écrasé)." -ForegroundColor Yellow
} else {
    $keytool = "keytool"
    if ($env:JAVA_HOME) { $keytool = Join-Path $env:JAVA_HOME "bin\keytool.exe" }

    $secure = Read-Host "Mot de passe de la clé (6 caractères minimum)" -AsSecureString
    $plain = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
        [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))
    if ($plain.Length -lt 6) { throw "Mot de passe trop court." }

    & $keytool -genkeypair -v `
        -keystore $keystore -alias $alias `
        -keyalg RSA -keysize 4096 -validity 10000 `
        -storepass $plain -keypass $plain `
        -dname "CN=Prism, O=Prism, C=FR"
    if ($LASTEXITCODE -ne 0) { throw "keytool a échoué." }

    $props = Join-Path $repo "local.properties"
    $path = $keystore -replace '\\', '/'
    Add-Content -Path $props -Value @(
        "",
        "RELEASE_STORE_FILE=$path",
        "RELEASE_STORE_PASSWORD=$plain",
        "RELEASE_KEY_ALIAS=$alias",
        "RELEASE_KEY_PASSWORD=$plain"
    )
    Write-Host "Clé créée : $keystore" -ForegroundColor Green
    Write-Host "local.properties complété (fichier ignoré par git)."
}

$b64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($keystore))
Set-Clipboard -Value $b64
Write-Host ""
Write-Host "Le contenu base64 de la clé est dans le presse-papiers." -ForegroundColor Green
Write-Host "Sur GitHub : Settings > Secrets and variables > Actions > New repository secret"
Write-Host "  RELEASE_KEYSTORE_BASE64 = (coller)"
Write-Host "  RELEASE_STORE_PASSWORD  = ton mot de passe"
Write-Host "  RELEASE_KEY_PASSWORD    = ton mot de passe"
Write-Host "  RELEASE_KEY_ALIAS       = $alias"
