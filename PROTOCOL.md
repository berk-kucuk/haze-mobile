# Haze Wire Protocol (v1) — Interop Spec

This document freezes the on-the-wire contract that **Haze Mobile** (Kotlin client)
must match to interoperate with the existing **Haze desktop** host (Python).
It was extracted directly from the desktop source (`network/`, `crypto/e2e.py`).

Any change here must be applied in lock-step on both sides.

---

## Transport
- Host publishes a **Tor v3 onion service** with two virtual ports:
  - `:5222` → native TCP chat protocol (this document)
  - `:80`   → HTTP/WebSocket web bridge (browsers; not used by the native client)
- The client connects to `<onion>.onion:5222` **through Tor's SOCKS5 proxy**
  (Orbot on Android: `127.0.0.1:9050`). The hostname is passed to SOCKS as a
  domain (ATYP `0x03`) so Tor resolves the onion address.

## Framing
```
[ 4 bytes big-endian length ][ JSON payload (UTF-8) ]
```
- Maximum payload size: **1 MiB** (`1 * 1024 * 1024`). Larger frames are rejected.

## Cryptography
| Item | Value |
|------|-------|
| Key agreement | X25519 (ephemeral per connection) |
| Public key encoding | raw 32 bytes, Base64 (standard, no wrap) |
| KDF | HKDF-SHA256, `salt = none` (32 zero bytes), `info = "haze-protocol-v1"`, length 32 |
| AEAD | ChaCha20-Poly1305 (IETF), **12-byte** random nonce, **no AAD**, 16-byte tag appended to ciphertext |
| Nonce/ciphertext encoding | Base64 (standard, no wrap) |

## Session-password hash
```
password_hash = hex( SHA256( "haze-session-v1:" + utf8(password) ) )
```
Empty password → empty string (session is unprotected).

---

## Handshake (client → host)
1. **C→S** `hello` (unencrypted frame):
   ```json
   { "type": "hello", "public_key": "<b64>", "nick": "<str>", "password_hash": "<hex|''>" }
   ```
2. **S→C** one of:
   - `{ "type": "auth_failed" }` → wrong password; host closes the connection.
   - `{ "type": "welcome", "public_key": "<host b64>", "nonce": "<b64>", "ciphertext": "<b64>" }`
     - `ciphertext` = the 32-byte session key, ChaCha20-Poly1305-wrapped with
       `HKDF(ECDH(client_priv, host_pub))`.
3. Client derives the same wrap key via `ECDH(client_priv, host_pub)` and
   decrypts to obtain the shared **session key**.
4. **S→C** (already encrypted) the initial `userlist`.

After the handshake, **every** frame is an encrypted envelope.

## Encrypted envelope
```json
{ "type": "encrypted", "nonce": "<b64>", "ciphertext": "<b64>" }
```
`ciphertext` decrypts (with the session key) to one inner JSON payload below.

## Inner payloads
| type | fields | direction |
|------|--------|-----------|
| `chat` | `nick`, `content`, `msg_id` | both |
| `join` | `nick` | S→C |
| `leave` | `nick` | both |
| `userlist` | `users: [str]` | S→C |
| `typing` | `nick`, `state: bool` | both |
| `edit` | `nick`, `msg_id`, `content` | both |
| `delete` | `nick`, `msg_id` | both |
| `panic` | `nick` | both |
| `kicked` | — | S→C |
| `ping` | `ts: float` | C→S |
| `pong` | `ts: float` | S→C |
| `file_start` | `nick`, `file_id`, `filename`, `mime`, `total_size`, `total_chunks` | both |
| `file_chunk` | `nick`, `file_id`, `chunk_index`, `data: b64` | both |
| `file_end` | `nick`, `file_id` | both |

The host overwrites `nick` on inbound `chat`/`typing`/etc. with the
sanitized nick it assigned at join, and does **not** echo a sender's own
`chat` back to them (clients render their own messages locally).

## Nick sanitization (host-side)
Allowed chars: `[A-Za-z0-9_-]`, truncated to 20. The mobile client applies the
same rule client-side before sending so the displayed nick matches.

---

## Mobile MVP scope
The current Kotlin client implements: handshake, `chat` (send/receive),
`userlist`, `join`, `leave`, `typing`, `panic`, `kicked`. Files, voice, edit,
and delete are defined here for completeness but deferred to a later milestone.
