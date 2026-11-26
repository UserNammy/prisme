// Prism Web — petit serveur local (Node 18+), lancé par lancer.cmd.
//
// Il sert index.html et un relais (/proxy) qui va chercher les flux à la place du
// navigateur. Le navigateur n'a donc plus de blocage CORS, et le relais peut envoyer
// les en-têtes demandés par certaines chaînes (Referer, User-Agent), comme le fait
// le lecteur de la TV.
//
// Sécurité : écoute uniquement sur 127.0.0.1, et chaque requête au relais doit porter
// un jeton aléatoire créé au démarrage (une autre page web ne peut pas s'en servir).

import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { Readable } from 'node:stream';
import { exec } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const DIR = path.dirname(fileURLToPath(import.meta.url));
const PORT = Number(process.env.PORT) || 5179;
const TOKEN = crypto.randomBytes(16).toString('hex');
const DEFAULT_UA = 'VLC/3.0.20 LibVLC/3.0.20';
const CONNECT_TIMEOUT_MS = 15000;

const cors = {
  'access-control-allow-origin': '*',
  'access-control-allow-headers': 'range',
  'access-control-expose-headers': 'content-length, content-range, content-type',
};

function proxyPath(url, q) {
  let p = '/proxy?t=' + TOKEN + '&u=' + encodeURIComponent(url);
  if (q.r) p += '&r=' + encodeURIComponent(q.r);
  if (q.a) p += '&a=' + encodeURIComponent(q.a);
  return p;
}

// Réécrit une playlist HLS : chaque adresse (segments, sous-playlists, clés) devient
// absolue puis repasse par le relais, avec les mêmes en-têtes.
function rewritePlaylist(text, base, q) {
  const wrap = (uri) => {
    try { return proxyPath(new URL(uri, base).href, q); } catch { return uri; }
  };
  return text.split(/\r?\n/).map((line) => {
    const l = line.trim();
    if (!l) return line;
    if (l.startsWith('#')) return line.replace(/URI="([^"]+)"/g, (_, u) => 'URI="' + wrap(u) + '"');
    return wrap(l);
  }).join('\n');
}

function isPlaylist(contentType, finalUrl) {
  if (/mpegurl/i.test(contentType)) return true;
  try { return /\.m3u8?$/i.test(new URL(finalUrl).pathname); } catch { return false; }
}

async function handleProxy(req, res, params) {
  if (params.get('t') !== TOKEN) { res.writeHead(403, cors).end('jeton invalide'); return; }
  const target = params.get('u') || '';
  let url;
  try { url = new URL(target); } catch { res.writeHead(400, cors).end('adresse invalide'); return; }
  if (url.protocol !== 'http:' && url.protocol !== 'https:') { res.writeHead(400, cors).end('protocole refusé'); return; }

  const q = { r: params.get('r') || '', a: params.get('a') || '' };
  const headers = { 'user-agent': q.a || DEFAULT_UA, accept: '*/*' };
  if (q.r) headers.referer = q.r;
  if (req.headers.range) headers.range = req.headers.range;

  const ctl = new AbortController();
  const timer = setTimeout(() => ctl.abort(), CONNECT_TIMEOUT_MS);
  res.on('close', () => ctl.abort());

  let up;
  try {
    up = await fetch(url, { headers, redirect: 'follow', signal: ctl.signal });
  } catch (e) {
    clearTimeout(timer);
    if (res.headersSent || res.destroyed) return;
    const timeout = e.name === 'AbortError';
    res.writeHead(timeout ? 504 : 502, cors).end(timeout ? 'délai dépassé' : 'serveur injoignable');
    return;
  }
  clearTimeout(timer);

  const type = up.headers.get('content-type') || '';
  if (up.ok && params.get('raw') !== '1' && isPlaylist(type, up.url)) {
    const text = await up.text();
    const body = text.trimStart().startsWith('#EXTM3U') ? rewritePlaylist(text, up.url, q) : text;
    res.writeHead(200, { ...cors, 'content-type': 'application/vnd.apple.mpegurl', 'cache-control': 'no-store' });
    res.end(body);
    return;
  }

  const out = { ...cors, 'cache-control': 'no-store' };
  for (const h of ['content-type', 'content-length', 'content-range', 'accept-ranges']) {
    const v = up.headers.get(h);
    if (v) out[h] = v;
  }
  // fetch décompresse déjà le corps : la longueur d'origine ne correspond plus.
  if (up.headers.get('content-encoding')) delete out['content-length'];
  res.writeHead(up.status, out);
  if (!up.body) { res.end(); return; }
  Readable.fromWeb(up.body).on('error', () => res.destroy()).pipe(res);
}

function serveIndex(res) {
  fs.readFile(path.join(DIR, 'index.html'), 'utf8', (err, html) => {
    if (err) { res.writeHead(500).end('index.html introuvable'); return; }
    const inject = '<script>window.PRISM_PROXY = ' + JSON.stringify({ base: '/proxy', token: TOKEN }) + ';</script>';
    res.writeHead(200, { 'content-type': 'text/html; charset=utf-8', 'cache-control': 'no-store' });
    res.end(html.replace('</head>', inject + '\n</head>'));
  });
}

const server = http.createServer((req, res) => {
  const u = new URL(req.url, 'http://localhost');
  if (req.method === 'OPTIONS') { res.writeHead(204, cors).end(); return; }
  if (u.pathname === '/' || u.pathname === '/index.html') { serveIndex(res); return; }
  if (u.pathname === '/proxy') {
    handleProxy(req, res, u.searchParams).catch(() => { if (!res.headersSent) res.writeHead(502, cors); res.end(); });
    return;
  }
  res.writeHead(404).end();
});

server.on('error', (e) => {
  if (e.code === 'EADDRINUSE') {
    console.log('Prism Web tourne déjà : http://localhost:' + PORT + '/');
    openBrowser();
    setTimeout(() => process.exit(0), 1500);
  } else {
    console.error(e);
    process.exit(1);
  }
});

function openBrowser() {
  if (process.env.NO_BROWSER) return;
  const url = 'http://localhost:' + PORT + '/';
  const cmd = process.platform === 'win32' ? 'start "" "' + url + '"'
    : process.platform === 'darwin' ? 'open "' + url + '"' : 'xdg-open "' + url + '"';
  exec(cmd);
}

server.listen(PORT, '127.0.0.1', () => {
  console.log('Prism Web : http://localhost:' + PORT + '/');
  console.log('Laisse cette fenêtre ouverte pendant que tu regardes. Ctrl+C pour arrêter.');
  openBrowser();
});
