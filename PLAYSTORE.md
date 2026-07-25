# Play Store Submission Guide — Haze Mobile

Concept-wise Haze is comparable to Tor-based messengers already on Google Play
(e.g. Cwtch, Briar): access is **not** open to random strangers — only someone
who has the onion address **and** the session password can join a chat. There is
no public directory or discovery. That, plus the in-app **block/mute**, satisfies
the parts of Google Play's User-Generated-Content policy that reject open,
unmoderated public apps.

What remains is the mechanical compliance below.

## 1. Build & technical (done in this repo)

- [x] `targetSdk` / `compileSdk` = **35** (Play requires a recent API for new
      apps and updates).
- [x] App Bundle build: `./build-apk.sh --bundle` → `app-release.aab`.
- [ ] **Signing:** set the keystore properties so the `.aab` is signed. In
      `gradle.properties` (kept out of git) or as `-P` flags:
      ```
      HAZE_KEYSTORE_FILE=/abs/path/haze-release.keystore
      HAZE_KEYSTORE_PASSWORD=...
      HAZE_KEY_ALIAS=...
      HAZE_KEY_PASSWORD=...
      ```
      Recommended: enroll in **Play App Signing** (Play manages the app-signing
      key; you keep the upload key).
- [ ] Bump `versionCode`/`versionName` for every upload.
- [ ] Consider enabling R8 (`isMinifyEnabled = true`) for a smaller release.

## 2. Store listing assets you must provide

- [ ] App icon (512×512), feature graphic (1024×500).
- [ ] At least 2 phone screenshots (already have some in `screenshots/`).
- [ ] Short + full description.
- [ ] **Privacy Policy URL** — host `PRIVACY.md` somewhere public (GitHub Pages,
      your site) and paste the URL. This is mandatory.

## 3. Data Safety form (Play Console → App content → Data safety)

Because the app has no backend and collects nothing, the answers are simple:

- **Does your app collect or share any user data?** → **No.**
  (No data is sent to you or any third party. Chat content is E2E-encrypted and
  goes only to peers over Tor.)
- **Is all data encrypted in transit?** → Yes (E2E + Tor).
- **Do you provide a way to request data deletion?** → Not applicable / data is
  on-device only and user-deletable in-app.

If Play's form forces you to classify the on-device Vault or media:
- Photos/voice are **processed on-device and sent only to chat peers**, not
  collected by the developer — declare "not collected."

## 4. Permissions declarations

- **Camera** and **Microphone** are user-triggered, per-feature (send photo /
  voice message). Explain this in the listing and in the permission rationale;
  `PRIVACY.md` already documents it.
- No sensitive/restricted permissions (no location, contacts, SMS,
  QUERY_ALL_PACKAGES, MANAGE_EXTERNAL_STORAGE, etc.), so no special declaration
  forms are required.

## 5. Content rating

- Complete the IARC questionnaire. This is a communication app with
  user-to-user messaging → disclose "users can interact / share content."
- Note the block/mute feature when asked about user-interaction safeguards.

## 6. Things reviewers may ask about (be ready to explain)

- **Tor usage** is legitimate and allowed (Orbot, Briar, Cwtch are on Play).
- **No open/public rooms:** access requires onion address + password; there is
  no stranger discovery. Emphasize this — it's the key UGC-policy point.
- **Encryption:** standard X25519 / ChaCha20-Poly1305; export-compliance
  self-classification may be requested (standard cryptography).

## 7. Pre-launch checklist

- [ ] Test the release `.aab` on a real device (via internal testing track).
- [ ] Verify camera + voice permission prompts appear and features work.
- [ ] Confirm the app degrades gracefully if Tor is unavailable.
- [ ] Privacy Policy URL is live and reachable.
