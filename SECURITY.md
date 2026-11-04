# Security

## Reporting a vulnerability

Please report security problems privately, not in a public issue: on GitHub, open the repository's **Security** tab
and choose **Report a vulnerability**. The report is visible only to the maintainers.

Helpful details: what the problem is, how to reproduce it, the AlfredTS version and the Android version.

Reports are answered as soon as possible. Once a fix is released, the report can be published with credit to the
reporter, if they wish.

## Supported versions

Fixes go into the latest release. Updating to the newest version from the
[releases page](https://github.com/chochrunius/pocketts/releases/latest) is the way to get them.

## Checking a release

Release APKs are built by GitHub Actions from the tagged commit, with a build attestation. To check that an APK came
from this repository:

```bash
gh attestation verify AlfredTS-<version>.apk --repo chochrunius/pocketts
```

The SHA-256 of each APK is in its release notes and attached `.apk.sha256` file.
Build attestations apply to APKs produced by the GitHub release workflow; local builds do not have them.
