# SouthJack

**Bridge a Bluetooth landline handset to Google Voice on a modern Android phone — no root.**

SouthJack is an open-source replacement for the Cell2Jack "Hangout Helper" workflow
that broke when Google retired Hangouts and changed how Google Voice places calls.
If you use a [Cell2Jack](https://www.cell2jack.com/) (or similar Bluetooth‑HFP
adapter) to run a physical landline handset off a phone, SouthJack makes **outgoing
calls dialed on the landline keypad go out through Google Voice** again.

> ⚠️ **Emergency calls (911):** Do **not** rely on this — or on Google Voice — for
> emergency calling. Google Voice does not provide reliable 911/E911 service, and a
> Wi‑Fi‑only phone with no SIM cannot place a carrier emergency call. Keep a real
> phone line or cell phone available for emergencies. See the
> [Google Voice 911 policy](https://support.google.com/voice/answer/115061).

---

## The problem

When you dial from the landline keypad, the Cell2Jack presents to Android as a
Bluetooth **hands‑free (HFP)** device and sends a standard `ATD<number>;` command.
Android turns that into an outgoing call — but places it on the **SIM / cellular**
account. On a Wi‑Fi‑only phone with no SIM that fails with *"No mobile network."*
Google's retirement of the Hangouts integration removed the path that used to catch
this and hand it to Google Voice. Incoming calls still ring through fine; it's
**outgoing** dialing that's broken.

## How SouthJack fixes it

It doesn't touch Bluetooth or Google Voice internals. It intercepts the **outgoing
call** using public Android telephony APIs (Android 10+):

```
Landline
   │
Cell2Jack  (Bluetooth HFP headset)
   │
Android Telecom  →  outgoing call on the cellular account ("No mobile network")
   │
SouthJack  (CallRedirectionService)         ← intercepts every outgoing call
   │   • cancelCall()  → kills the dead cellular attempt
   │   • startActivity(ACTION_CALL, tel:<number>, package = Google Voice)
   ▼
Google Voice  →  places the call over Wi‑Fi (VoIP)
   │
audio routes back to the Cell2Jack (it's the active Bluetooth headset) → handset
```

When the screen is locked or off, a direct `startActivity` is blocked by Android's
background‑activity‑launch rules, so SouthJack instead posts a **full‑screen‑intent
notification** (the same mechanism incoming‑call apps use) to bring Google Voice up.

### Why not just redirect to Google Voice's calling account?

Because it doesn't have one. Recent Google Voice registers a **self‑managed**
`ConnectionService` for its VoIP calls, which never appears in
`TelecomManager.getCallCapablePhoneAccounts()` and can't be targeted by
`CallRedirectionService.redirectCall()`. The only public way in is the exported
`ACTION_CALL` / `tel:` activity that Google Voice still exposes — which is what
SouthJack uses.

## Requirements

- An Android **10+** phone (developed and tested on a **Samsung Galaxy A14 5G,
  Android 13 / One UI Core 5.1**).
- The **Google Voice** app, signed in and configured to make calls.
- A **Cell2Jack** (or compatible Bluetooth‑HFP landline adapter), paired to the phone.
- To build: **Android Studio** (bundles the SDK, JDK 17, and Gradle).

## Build & install

```bash
git clone https://github.com/aarongress1/southjack.git
cd southjack
./gradlew assembleDebug          # or open the folder in Android Studio and hit Run
# APK: app/build/outputs/apk/debug/app-debug.apk  →  adb install it, or sideload
```

## Set up on the phone

1. Open **SouthJack**. Grant the **phone**, **call**, and **notification** permissions.
2. **Section 1** — tap *Make SouthJack the Call Redirection app* and confirm.
3. **Section 2** — leave *Google Voice routing* **ON**. The screen should read **"Ready."**
4. Dial a number on the landline keypad — Google Voice places the call.

### Recommended "appliance" configuration

For a phone that lives in a drawer as a dedicated bridge:

- **Screen lock → None.** Android gates *placing an outgoing call* behind
  authentication, so a PIN/pattern will stall calls until you unlock. Removing the
  secure lock is required for hands‑off dialing while the screen is off.
  *(Security note: this phone has no SIM and only reaches your Google Voice account;
  removing the lock trades physical‑access security for hands‑free operation. Decide
  what's right for you.)*
- **Developer options → Stay awake** (screen on while charging), and keep it
  **plugged in**. Keeps the phone on the fast, always‑reliable direct‑launch path.
- **Settings → Apps → SouthJack → Battery → Unrestricted**, and keep it out of
  One UI's *Sleeping apps*, so the OS doesn't freeze the call service.

## Status & limitations

- ✅ **Outgoing** landline dialing through Google Voice — working (hands‑free).
- ✅ Two‑way audio on the handset (HFP SCO).
- ✅ Works with the screen off **once the secure lock is removed** (see above).
- ➖ **Incoming** calls already ring through Google Voice/Cell2Jack without SouthJack;
  the app doesn't touch them.
- ⚠️ Not tested across OEMs/Android versions beyond the A14 / Android 13 above.
  Reports welcome.
- ⚠️ No 911. See the warning at the top.

## Roadmap

- Reboot‑persistence / boot check for the redirection role.
- Optional in‑app auto‑answer / call UI polish.
- **Universal bridge:** target any provider that registers a callable
  `PhoneAccount` (OpenPhone, SIP, etc.) via `redirectCall()`, selectable in‑app.

## Contributing

Issues and PRs welcome — especially test reports from other phones/Android versions,
and OEM‑specific quirks (background‑launch, battery management). Please include your
device model, Android/One UI version, and the relevant `tag:SouthJack` logcat.

## Disclaimer

SouthJack is an independent project. It is **not** affiliated with, endorsed by, or
connected to Google LLC or Cell2Jack. "Google Voice" is a trademark of Google LLC;
"Cell2Jack" is a trademark of its respective owner. Provided "as is," without
warranty. You are responsible for complying with the terms of service of any app or
device you use it with.

## License

[MIT](LICENSE) © 2026 Aaron Gress
