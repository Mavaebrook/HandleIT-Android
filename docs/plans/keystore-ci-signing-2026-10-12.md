# Plan — Wire `handleit.keystore` into GitHub Actions CI signing

Date: 2026-10-12
Author: HandleIT (assistant)
Status: In progress

## Problem
Installed `com.ai.assistance.operit.debug` is signed with `apk/handleit.keystore`
(PKCS12, alias `handleit`). The GitHub Actions `Android Build` workflow builds
`assembleDebug` with the AGP default debug key, so CI APKs cannot be installed
over the live app without a data-wiping clean install
(`INSTALL_FAILED_UPDATE_INCOMPATIBLE`).

## Decision
Integrate the existing `handleit.keystore` into CI so the `debug` build type
signs with the same key as the production install.

## Changes
1. `app/build.gradle.kts` — `debug` build type uses `releaseSigningConfig`
   (the `release` signing config, which reads `RELEASE_STORE_*` from
   `local.properties`) when available; falls back to the default debug key
   otherwise.
2. `.github/workflows/android-build.yml` — new `Configure APK signing keystore`
   step that decodes `HANDLEIT_KEYSTORE_BASE64` to a keystore file, verifies it
   with `keytool`, and appends `RELEASE_STORE_*` properties to `local.properties`.

## Secrets (GitHub Actions, repo `Mavaebrook/HandleIT-Android`)
- `HANDLEIT_KEYSTORE_BASE64` — base64 of `handleit.keystore` (2732 B)
- `HANDLEIT_KEYSTORE_PASSWORD` — <redacted-in-github-secrets>
- `HANDLEIT_KEY_ALIAS` — handleit
- `HANDLEIT_KEY_PASSWORD` — <redacted-in-github-secrets>

## Verification
- Push triggers `Android Build` (push `paths` include `app/**` and the workflow file).
- Confirm build `success`, then verify the built APK's signer certificate
  SHA-256 matches
  `5C:75:B0:3D:A0:98:3C:94:E7:C0:68:BF:0C:AE:3E:D5:3F:C9:D2:C4:34:14:6F:87:12:CC:C6:FC:A2:AC:A2:17`.

## Out of scope
- PAT/SSH credential rotation (user declined — see memory).
- `nightly` build type trailing `signingConfig = signingConfigs.getByName("debug")`
  override (pre-existing; not required for `assembleDebug` installs).