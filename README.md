# Haze Mobile

A **join-only** Android client for [Haze](../haze) — the anonymous, end-to-end
encrypted P2P chat that runs over Tor onion services. This app lets an Android
user join a chat **hosted from the Haze desktop app**, speaking the exact same
wire protocol (see [`PROTOCOL.md`](PROTOCOL.md)).

The visual design mirrors Haze's web/desktop UI: same dark palette, the `HAZE`
wordmark, and the green *end-to-end encrypted* badge.

> **Scope:** this is a client. It joins existing sessions; it does **not** host
> onion services (that stays on desktop for now). See the roadmap in the main
> repo for the hosting milestone.

## Architecture
```
ConnectScreen / ChatScreen (Jetpack Compose)
        │
   ChatViewModel  ── StateFlow<ChatUiState>
        │
   ChatClient  ──►  Socks5 ──► Tor (Orbot :9050) ──► <onion>.onion:5222
        │
   SessionCrypto (X25519 + HKDF-SHA256 + ChaCha20-Poly1305, BouncyCastle)
   Framing (4-byte length prefix + JSON, 1 MiB cap)
```
Crypto and framing are byte-for-byte compatible with the Python host.

## Requirements
- **Android 8.0+ (API 26)**
- **[Orbot](https://play.google.com/store/apps/details?id=org.torproject.android)**
  installed and running (provides the Tor SOCKS proxy on `127.0.0.1:9050`).
- A running Haze desktop host and its `…​.onion` address.

## Build
Open in **Android Studio** (Koala / 2024.1+), let it sync, and Run — or from
the CLI:

```bash
# One-time: generate the Gradle wrapper jar if it isn't present
gradle wrapper --gradle-version 8.9

./gradlew assembleDebug
# APK → app/build/outputs/apk/debug/app-debug.apk
```
> The Gradle **wrapper jar** (`gradle/wrapper/gradle-wrapper.jar`) is a binary and
> is not committed here; Android Studio adds it automatically, or run the
> `gradle wrapper` command above once.

## Usage
1. Start Orbot and wait until it reports **Connected**.
2. Open Haze, enter the host's `…​.onion` address, a nickname, and the session
   password (if the host set one).
3. Tap **Join Chat**.

## Security notes
- End-to-end encryption is identical to desktop Haze (X25519 → ChaCha20-Poly1305);
  Tor only carries already-encrypted frames.
- Chat text is rendered with Compose `Text`, which never interprets HTML/markup —
  so the desktop's rich-text IP-leak vector does **not** exist here.
- The session key lives only in memory and is zeroed on disconnect.

## Status / roadmap
- [x] Tor SOCKS connect + X25519 handshake
- [x] Text chat: send / receive, userlist, join / leave, typing
- [x] panic / kicked handling
- [ ] File transfer (receive/send)
- [ ] Edit / delete messages
- [ ] Embedded Tor (kmp-tor) so Orbot isn't required
- [ ] Voice messages
- [ ] Hosting from the phone
