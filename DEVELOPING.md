# Developing PocketTS

## The parts

- `tsproto/`: the TeamSpeak connection in plain Kotlin (no Android): packets, encryption, commands, the channel and
  people model, chat, file transfer and streams. Most of the tests are here.
- `app/`: the Android app, in Jetpack Compose: screens, voice (Opus), streams (WebRTC), settings and favourites.
- `scripts/dev.sh`: every build, test and helper command.
- `docs/TESTING.md`: what to test by hand on a phone.

## Building

Everything builds in Docker, so the only things needed are Docker and a Bash shell (on Windows: Docker inside
WSL). There's no need to install Java or the Android SDK.

```bash
ACCEPT_ANDROID_SDK_LICENSE=yes scripts/dev.sh image   # the build image: Java, the Android SDK and NDK (once)
scripts/dev.sh test                                   # tests
scripts/dev.sh apk                                    # a test build in dist/
scripts/dev.sh screenshots                            # pictures of the screens (example data) in docs/screenshots/
scripts/dev.sh gradle <task>                          # any other Gradle task
```

The build image accepts the Android SDK license, which is why that variable has to be set.

Builds keep Gradle's downloads, the build output and Robolectric's Android jars (which the app's tests run on, about
200 MB) in Docker volumes: `pocketts-gradle`, `pocketts-out` and `pocketts-m2`. Later builds are then faster, and the
tests don't download those jars every time (Maven Central starts refusing repeated downloads).

`scripts/dev.sh screenshots` also makes the Google Play pictures (`docs/screenshots/play/`) and GitHub's social
preview (`docs/screenshots/social-preview.png`, the picture shown when the repository is shared or found in search).
When the social preview changes, upload it again in the repository's Settings › General › Social preview.

`dev.env` holds this computer's own settings (addresses, container names) and is never committed.

## Testing on a phone

`scripts/dev.sh devserver up` starts a small page on port 8686. Open `http://<this computer's address>:8686` on the
phone, tap the newest build and install it. Android asks once to allow installing apps from the browser.
`scripts/dev.sh devserver down` stops it.

Test builds send their log to that page (`dist/phone.log`) when `DEV_LOG_URL` is set in `dev.env`. Release builds
never do.

## Tests against a real server

`scripts/dev.sh it` connects two clients to a TeamSpeak server and checks the following:

- they see each other and each other's channel moves
- one hears exactly what the other sends
- chat messages arrive
- both leave cleanly

Put the test server's address in `dev.env` as `TS_TEST_SERVER=host:port`. If the server runs in a container, add
`TS_CONTAINER=<name>` as well, so `scripts/dev.sh ts-server up` and `down` can start and stop it.

## Testing streams

`scripts/dev.sh viewer up` starts a test viewer on the test server (`TS_CONTAINER`): WebTS and a headless Chromium,
inside the server container's own network, so nothing is opened to the outside. It joins as "Stream viewer", goes
to the channel of whoever starts a stream and watches it, taking the video as H.264 like the TeamSpeak app
(`scripts/dev.sh viewer up webts` lets WebTS choose instead). `scripts/dev.sh viewer down` stops it.

The test page shows what it sees at `/viewer`: a log line a second (picture size, frame rate, data rate, losses,
freezes, sound, route) and a picture every few seconds (also in `dist/viewer/`). To measure the delay, open the test
page's `/clock` on the phone and share the screen: the viewer reads the clock from the stream and logs the delay from
the phone's screen to its own.

## Releasing AlfredTS

AlfredTS releases belong to `chochrunius/pocketts`. The release workflow builds only
an APK, verifies its signing certificate and manifest, adds a SHA-256 checksum and
GitHub build attestation, and creates a **draft**. It never uploads to Google Play.
The inherited Play build type and helper are upstream tooling, not this release path.

### Application identity

The release application ID is `io.github.chochrunius.alfredts`; debug and shrunk builds
append `.debug` and `.shrunk`. The Kotlin namespace remains
`io.github.korppujauho.pocketts`, preserving resource imports and native JNI names.
The FileProvider authority follows the application ID. No Firebase, OAuth redirect,
app-link verification or other application-ID-bound service is configured in the app.
The upstream Play helper still targets the upstream package and must not be used for AlfredTS.

The new ID installs separately from PocketTS and older AlfredTS builds with the old
ID. Private data and the TeamSpeak identity are not imported; server groups and
permissions tied to the old identity do not carry over. Leave the old installation
intact. Runtime permissions and overlay access must be granted again. TeamSpeak URI
handlers can coexist and Android may show a chooser. See [1.6.0 notes](docs/releases/1.6.0.md).

### Signing prerequisites (once, before any tag)

Select the intended long-term production key and back up the key and passwords
outside Git. Never replace an existing release key: future updates require the same
signing identity. No key is generated as part of release preparation or the workflow.
If no production key exists, obtaining explicit approval to create one is a separate
step; do not run the inherited `scripts/dev.sh keystore` command without that approval.
Existing alias names and certificate subject names do not need to say AlfredTS.

In GitHub Settings > Environments, create `release`, restrict deployment tags to `v*`,
and configure any required reviewer. Configure these **environment secrets**:

- `POCKETTS_KEYSTORE_BASE64`: the chosen keystore, base64 encoded.
- `POCKETTS_KEYSTORE_PASSWORD`: its store password.
- `POCKETTS_KEY_PASSWORD`: only if the private key password differs from the store password.

Configure the certificate fingerprint as a **repository Actions variable** (an
environment variable also works; avoid a conflicting environment override).
The optional alias can be a repository or environment variable:

- `ALFREDTS_SIGNING_CERT_SHA256`: the trusted signing certificate's SHA-256 fingerprint,
  64 hexadecimal characters or colon-separated hexadecimal pairs. Obtain it directly
  from the selected key's certificate (`keytool -list -v -keystore <outside-repo-path> -alias <alias>`,
  interactive password prompt), cross-check it independently and preserve it for future releases.
  Do not take it from the newly built APK and blindly trust that value.
- `POCKETTS_KEY_ALIAS`: the selected alias; defaults to `pocketts` for compatibility.

Keep the existing `POCKETTS_*` infrastructure names; they do not identify the Android app.
Never print credentials, pass passwords on command lines or store a keystore in Git.
For example, after environment setup, stream a key from WSL straight to GitHub in PowerShell
(the path here is a placeholder, not a request to create a key):

```powershell
wsl -d Ubuntu-24.04 -- bash -lc 'base64 -w0 /path/outside/repository/release.keystore' | gh secret set POCKETTS_KEYSTORE_BASE64 --env release --repo chochrunius/pocketts
gh secret set POCKETTS_KEYSTORE_PASSWORD --env release --repo chochrunius/pocketts
# Only for a different key password (interactive input):
gh secret set POCKETTS_KEY_PASSWORD --env release --repo chochrunius/pocketts
# Non-secret public certificate fingerprint and alias; replace placeholders:
gh variable set ALFREDTS_SIGNING_CERT_SHA256 --body '<trusted-certificate-sha256>' --repo chochrunius/pocketts
gh variable set POCKETTS_KEY_ALIAS --body '<selected-alias>' --env release --repo chochrunius/pocketts
```

### Prepare, draft, then publish

1. Update `versionName` and increase `versionCode` in `app/build.gradle.kts`.
   Keep `CHANGELOG.md` and `docs/releases/<version>.md` accurate; the latter is the
   release body. For the first AlfredTS release these are `1.6.0` and `7`.
2. Run `bash scripts/dev.sh gradle :app:testDebugUnitTest :tsproto:test :app:assembleRelease`
   in WSL. Without a key this checks the shrunk release build but produces an **unsigned**
   APK; it is not a publishable artifact. Avoid repeating the build without a reason.
3. Review the diff, then explicitly authorize commit, push and merge of the preparation
   branch. Select only release-preparation files; leave unrelated artwork untracked.
4. Confirm signing prerequisites above and that the reviewed commit is on remote `main`.
   Only when tagging and draft creation are authorized, run in PowerShell:

   ```powershell
   git fetch --no-tags origin main
   git log -1 origin/main
   git tag -a v1.6.0 origin/main -m "AlfredTS 1.6.0"
   git -c push.followTags=false push origin refs/tags/v1.6.0:refs/tags/v1.6.0
   gh run list --workflow release.yml --repo chochrunius/pocketts --limit 3
   gh run watch <run-id> --exit-status --repo chochrunius/pocketts
   gh release view v1.6.0 --repo chochrunius/pocketts
   ```

   The tag push triggers the workflow. It verifies the version, runs tests, signs and
   checks the APK against the trusted certificate fingerprint, checks its package,
   version and non-debuggable manifest, creates checksums and an attestation, and
   creates the draft **AlfredTS 1.6.0** with `AlfredTS-1.6.0.apk` and
   `AlfredTS-1.6.0.apk.sha256`. Do not separately create a second release.
5. Download the draft assets to an empty review directory:

   ```powershell
   gh release download v1.6.0 --repo chochrunius/pocketts --dir <empty-review-directory>
   gh attestation verify <empty-review-directory>/AlfredTS-1.6.0.apk --repo chochrunius/pocketts
   Get-FileHash <empty-review-directory>/AlfredTS-1.6.0.apk -Algorithm SHA256
   Get-Content <empty-review-directory>/AlfredTS-1.6.0.apk.sha256
   ```

   Compare hashes. Run `scripts/verify-release.sh` inside the build image against the
   downloaded APK using the independently trusted certificate fingerprint. Install
   that exact signed APK on a phone, verify coexistence with the old package, Czech,
   channel preferences and scrolling, failed connection UI, connect/voice/chat/pictures/
   streams, and launch after force-stop. See [docs/TESTING.md](docs/TESTING.md).
   A test-key build cannot replace this check. Confirm an update signed with the same
   key preserves data when a subsequent release candidate is available.
6. Only after phone verification and explicit publication authorization:

   ```powershell
   gh release edit v1.6.0 --draft=false --latest --repo chochrunius/pocketts
   ```

   Published stable releases become visible to enabled in-app update checks (at most
   once daily); drafts and prereleases are not offered.

### Local signed build (fallback)

Set `POCKETTS_KEYSTORE` in ignored `dev.env` to an existing key outside the repository
(default `~/.pocketts/release.keystore` inside WSL). Export the trusted
`ALFREDTS_SIGNING_CERT_SHA256` and, if necessary, `POCKETTS_KEY_ALIAS` and
`POCKETTS_KEY_PASSWORD` securely in the shell environment. Then run:

```bash
bash scripts/dev.sh release
```

It prompts for the store password, builds and verifies the APK and writes
`dist/AlfredTS-<version>.apk` plus `.apk.sha256`. This local APK has **no GitHub build
attestation**. Prefer the tagged workflow artifact for the official release.
