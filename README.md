# QRST Signer

An Android app that moves a Nostr key (nsec) between devices by QR code, using
[QR Secret Transfer](https://github.com/sybenx/qr-secret-transfer) 1.6-draft and its
[`nostr-nsec`](https://github.com/sybenx/qr-secret-transfer/blob/main/profiles/nostr-nsec.md) profile.
That's all it does. It holds keys and transfers them, in both directions. It does not sign events (yet).

**Status: unaudited, built on a draft protocol.** Try it with throwaway keys first.

## How it works

- **Scan a QRST code** with the app, or with the phone's camera: `https://qrst.aaro.cc/#…` links open the app
  directly (Android App Links). A code that says *offer* makes this phone the Sender; *request* makes it the Receiver.
- **Receive a key**: the phone shows a code. Scan it from the device that has the key, type the five digits this
  phone shows into that device, then tap *Keep it*.
- **Send a key**: pick a key; the phone shows a code for the receiving device to scan. Or scan the receiving device's code.
  Either way you type the digits the receiver shows (`type` is the profile's minimum), confirm with your fingerprint or
  screen lock, and only then does the key leave.

The key travels sealed (NIP-44 inside a NIP-59 gift wrap, ephemeral kind 21059) over public relays that are each
tested before use (wss://syb.lol first, by default; change it in Settings). A stranger who photographs a code gets digits that won't match.

What it can't protect against: sending your key to a device that isn't yours. Only you saying no at the send prompt
stops that (QRST §15).

## Storage

Keys are encrypted with AES-GCM under a key in the Android Keystore. Where the phone has a lock screen, that Keystore
key only works for 10 seconds after a fingerprint, face or screen-lock check, so storing or sending a key always needs
one. Public keys are stored in the clear so the list needs no unlock. App data is excluded from backups and device
transfer. Screens are `FLAG_SECURE` in release builds. There is no export of the bare key.

A key that arrived by transfer is receive-only on this phone until you allow a send for that one transfer (QRST §14).

## Layout

```
app/src/main/java/cc/aaro/qrst/
  core/    the protocol, a port of the reference's src/core: SAS, NIP-44, gift wrap, pairing link, session state machine
  net/     relays (OkHttp): re-send loop, loopback relay selection, NIP-11 limits, local store (throttle, log)
  vault/   Keystore-backed key storage
  ui/      Compose screens, CameraX + ZXing scanner
site/      qrst.aaro.cc: the bounce page and .well-known/assetlinks.json
interop/   a TypeScript peer built from the reference, for InteropTest
```

## Build and test

```sh
./gradlew :app:testDebugUnitTest          # NIP-44 + SAS vectors, session flows
QRST_REFERENCE=../qr-secret-transfer ./gradlew :app:testDebugUnitTest   # plus interop against the reference over its test relay
./gradlew :app:assembleDebug
```

Releases are built by `.github/workflows/release.yml` on a `v*` tag, signed with the key in the
`QRST_KEYSTORE_B64` / `QRST_KEYSTORE_PASSWORD` secrets. Install with [Obtainium](https://github.com/ImranR98/Obtainium)
from this repository's URL.

Test mode (Settings) also carries the reference demo's `qrst-demo-text` profile, to try the app against
<https://sybenx.github.io/qr-secret-transfer/> with made-up text.

## Not yet

FROST shares (`frost-share`), reading the digits by camera, the local-network path, NIP-66 relay discovery.
