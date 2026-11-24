# Prisme — roadmap

## Principle

Prisme is a player only. It ships no playlist, channel, account or stream.
Everything needed to play a source is stored on the user's own devices.

## First usable release

- Android TV and Android phone installation from one codebase.
- M3U/M3U8 and Xtream setup, locally cached catalogue, EPG and favourites.
- Local QR configuration: the TV opens a short-lived LAN web page; no cloud or
  third-party server is involved.
- Hardware video decode by default, conservative buffering and no always-on
  image filters.

## Next engineering milestones

1. Extract playlist, database and playback code into a shared `core` module.
2. Split TV and phone navigation so each gets a native remote/touch UI.
3. Add authenticated LAN pairing, encrypted configuration export/import, and
   an optional single-playback lease between paired devices.
4. Add a small quality profile system: hardware scaling by default, optional
   deinterlacing and lightweight GPU effects only when the device can sustain
   them without dropped frames.

## Explicit limits

The app can coordinate one simultaneous playback between paired Prisme
devices. It cannot change a provider's stream-concurrency limit, nor bypass a
portal or subscription tied to a MAC address.
