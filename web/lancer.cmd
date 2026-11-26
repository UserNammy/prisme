@echo off
rem Lance Prism Web avec son relais local (débloque les chaînes « CORS » et « en-tête »).
title Prism Web
where node >nul 2>nul
if errorlevel 1 (
  echo Node.js est introuvable. Installe-le depuis https://nodejs.org puis relance ce fichier.
  pause
  exit /b 1
)
node "%~dp0serveur.mjs"
pause
