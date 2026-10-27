# SouthJack: JDK 21 builds, security scans, and GitHub releases

Copy this package into the root of your SouthJack fork, preserving every path. It is an overlay for upstream commit `ebb7228aeb76cf276a44da6954f723b961196feb`, not a complete source tree.

## Included changes

| Path | Purpose |
| --- | --- |
| `.github/workflows/android-ci.yml` | Scans, then builds a debug APK on pushes and pull requests |
| `.github/workflows/android-release.yml` | Scans, builds, signs on an isolated runner, and publishes tagged releases |
| `.github/workflows/security.yml` | Reusable and daily vulnerability, secret, workflow, policy, and lint checks |
| `.github/dependabot.yml` | Weekly Gradle and GitHub Actions update proposals |
| `build.gradle.kts`, `app/build.gradle.kts` | AGP/Kotlin upgrades, patched transitive dependencies, and Java/Kotlin 21 targets |
| `gradle/wrapper/*` | Gradle 9.7.1 wrapper JAR and checksum-pinned distribution |
| `app/src/main/...` | Privacy hardening and focused policy fixes |
| `scripts/ci/*` | Versioning, signing, security inventory, scanner installation, and regression tests |
| `SECURITY-AUDIT.md` | Findings, remediations, verification, and scan limits |

The resulting toolchain is Temurin JDK 21, Gradle 9.7.1, Android Gradle Plugin 9.3.1, Kotlin Gradle Plugin 2.4.20, Android SDK 34 extension 12, and Build Tools 36.0.0. The app still targets Android 14/API 34 for compatibility with the Galaxy A16 use case.

## Automated checks

The reusable `Security` workflow runs before debug and release builds and also runs daily at 05:17 UTC. It:

- downloads OSV-Scanner, Gitleaks, and zizmor from fixed release URLs and verifies their SHA-256 hashes;
- scans full git history for leaked secrets, with findings redacted;
- scans GitHub Actions for medium-or-higher workflow security findings;
- runs six source-level privacy and permission regression tests;
- resolves seven app/build classpaths into a CycloneDX 1.5 dependency inventory;
- scans all 121 currently resolved components with OSV; and
- runs Android lint for debug and release and retains the reports for 30 days.

Action references are commit-pinned. Dependabot proposes weekly Gradle and Actions updates. Scanner version updates are deliberate edits to `scripts/ci/security-tools.json`: change both the release URL and reviewed SHA-256 value.

The upstream project has no unit-test sources. Under AGP 9, no unit-test tasks are created for this project, so the workflows run the focused Python security tests plus Android lint and complete APK assembly instead of pretending a `NO-SOURCE` task is test coverage.

## 1. Add the overlay and enable Actions

Extract this package into the repository root and commit every included file. Ensure `.github` is copied even if your file manager hides it. Then open **Actions** in the fork and enable workflows if GitHub shows the fork notice.

Pushes and pull requests first pass the security workflow, then build `app-debug.apk`. Download it from the run's `southjack-debug-...` artifact. The debug application ID has a `.debug` suffix, so it can coexist with the release app; only one app can hold Android's call-redirection role at a time.

The debug certificate is disposable. Installing a debug APK from a later clean runner may require uninstalling the old debug app. Use releases for an updatable installation.

## 2. Generate one persistent release key

On a trusted computer with JDK 21, store the key outside the repository and back it up:

```bash
mkdir -p "$HOME/.local/share/southjack"
keytool -genkeypair \
  -keystore "$HOME/.local/share/southjack/southjack-release.p12" \
  -storetype PKCS12 \
  -alias southjack \
  -keyalg RSA \
  -keysize 3072 \
  -validity 10000
```

Use the same password for the PKCS12 keystore and private key. Losing this key prevents future APKs from updating the installed app. Never commit it or generate a new key per release.

## 3. Configure release secrets

Open **Settings → Secrets and variables → Actions** and add:

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64-encoded `.p12` contents |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | `southjack` |
| `ANDROID_KEY_PASSWORD` | Private-key password; normally the same PKCS12 password |

GitHub CLI can upload the key without printing it:

```bash
base64 < "$HOME/.local/share/southjack/southjack-release.p12" | gh secret set ANDROID_KEYSTORE_BASE64
gh secret set ANDROID_KEYSTORE_PASSWORD
gh secret set ANDROID_KEY_ALIAS
gh secret set ANDROID_KEY_PASSWORD
```

The build job never receives these secrets. It uploads an unsigned APK with one-day retention; a fresh runner then receives the artifact and secrets, signs and verifies the APK, records checksums/certificate/build metadata, and publishes the release.

## 4. Publish a release

After the default-branch CI succeeds, tag the exact commit you want to release:

```bash
git tag v0.2.0
git push origin v0.2.0
```

The workflow publishes `southjack-0.2.0.apk`, `SHA256SUMS.txt`, `signing-certificate.txt`, and `build-info.json`. Do not manually create the GitHub Release first because the workflow refuses to overwrite an existing release.

Tags must be `vMAJOR.MINOR.PATCH`, with each component from 0 to 99, no leading zeroes or suffixes, and at least `v0.0.2`. Android's version code is `MAJOR × 10000 + MINOR × 100 + PATCH`; for example, `v0.2.1` becomes version name `0.2.1` and version code `201`. Never move or reuse a published tag.

On the A16, download the APK from **Releases**, permit installation from the browser/file manager, install it, and grant the call-redirection role. If an existing APK uses the same application ID but a different key, uninstall it once before installing your first release; that removes its settings. Releases signed with the preserved key then update in place.

## Troubleshooting

- **No run:** enable Actions and confirm the tag points to a commit containing all overlay files.
- **Security job fails:** download `security-reports-...`; OSV, Gitleaks, zizmor, inventory, privacy-test, and Android-lint output identify the gate.
- **Scanner checksum fails:** do not bypass it. Review the upstream scanner release, then update the pinned URL and checksum together.
- **SDK package fails:** the workflows require `platforms;android-34-ext12` and `build-tools;36.0.0`.
- **Signing fails:** verify all four secret names, alias, and passwords. Base64 is encoding, not encryption.
- **Publishing returns 403:** repository or organization policy must allow the release job's `contents: write` permission.
- **Release already exists:** publish a higher version; the workflow intentionally will not replace it.
- **APK will not update:** confirm the application ID, signing certificate, and increasing version code.
- **Cell2Jack/Google Voice fails:** CI validates software construction, not physical handset Bluetooth audio, screen-off behavior, hangup, or Google Voice behavior. Test those on the Galaxy A16 before relying on it.

Remove the two Android workflows and the scheduled trigger from `security.yml` to stop automation. Existing releases and installed apps are unaffected.

## Verification performed on this package

On 2026-09-19, the overlay was applied to the inspected upstream commit and verified with:

- a clean JDK 21 debug and unsigned-release build using Gradle 9.7.1/AGP 9.3.1/Kotlin 2.4.20;
- Java/Kotlin class-file major version 65 (Java 21);
- debug and release Android lint: 0 errors and two intentional API-level warnings each;
- a 121-component resolved dependency inventory and OSV result of zero known vulnerabilities;
- Gitleaks source-snapshot and zizmor medium-or-higher scans with zero findings (CI scans full git history);
- six privacy/permission tests, workflow YAML/actionlint/Bash checks, and 14 release-version cases; and
- disposable-key zip alignment, APK signing, and signature verification.

The remaining lint warnings note that API 34 is not the newest platform. That target is intentionally retained for the current Galaxy A16 deployment and should only be raised after testing newer Android behavior. See `SECURITY-AUDIT.md` for findings and scan limitations.
