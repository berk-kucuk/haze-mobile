# Haze Mobile — Privacy Policy

_Last updated: 2026-07-26_

Haze Mobile ("the app") is an anonymous, end-to-end encrypted chat client that
communicates over the Tor network. This policy explains what the app does and
does not do with your data. **We operate no servers and collect no data.**

## Summary

- **We collect nothing.** There is no account, no sign-up, no analytics, no
  advertising, no tracking, and no backend operated by us.
- All chat content is **end-to-end encrypted** (X25519 → ChaCha20-Poly1305) and
  travels only over **Tor**. We never see it.
- Everything the app stores stays **on your device**.

## Data we collect about you

**None.** The developer does not receive, store, or process any personal data,
message content, metadata, usage statistics, crash reports, or device
identifiers.

## How the app works (data flow)

- To join a chat you enter an onion address and, if set, a session password.
  These are used only to establish the encrypted connection and are **not sent
  to us** — the app has no server to send them to.
- Messages, typing indicators, and files are exchanged **directly between
  participants** through Tor onion services, end-to-end encrypted. Only people
  who know the onion address and session password can connect.
- The session encryption key exists only in memory and is wiped on disconnect.

## On-device storage

- **Saved sessions (Vault):** If you choose to save a conversation, it is
  encrypted with a password you provide and stored locally on your device only.
  It is never uploaded anywhere. Deleting it in the app removes it.
- The app does not back up its data to the cloud (`allowBackup` is disabled).

## Permissions

The app requests the following permissions, used only for the stated feature and
only when you trigger it:

- **Internet** — to connect to the Tor network (via the Tor client) and reach
  the chat host. Required for the app to function.
- **Camera** (`CAMERA`) — only when you tap the camera button to capture a photo
  to send in a chat. Photos are sent end-to-end encrypted to chat participants
  and are not sent to us. Access is requested at runtime and can be denied.
- **Microphone** (`RECORD_AUDIO`) — only when you record a voice message to send
  in a chat. Recordings are sent end-to-end encrypted to chat participants and
  are not sent to us. Access is requested at runtime and can be denied.

Captured photos and voice recordings are not stored or transmitted anywhere
except to the chat participants you send them to, over the encrypted connection.

## Data sharing

We do not share data with third parties because we do not collect any. Your
messages and media are shared only with the chat participants you connect to, and
only end-to-end encrypted.

## Children

The app is not directed to children under 13 and collects no data from anyone.

## Security

- End-to-end encryption: X25519 key agreement, HKDF-SHA256, ChaCha20-Poly1305.
- Transport anonymity: all traffic is carried over Tor.
- On modern devices, the app sets `FLAG_SECURE` in release builds to block
  screenshots and screen recording of chat contents.

## Changes to this policy

If this policy changes, the updated version will be published at the same URL
with a new "Last updated" date.

## Contact

For questions about this policy, contact: **dev.berkkucukk@gmail.com**
