# Release, integrity, and security guide

## Artifact model

A release is built on each target operating system because JavaFX native libraries and Oracle/OpenJDK `jpackage` output are platform-specific. Oracle's jpackage guide explicitly requires building packages on the target platform: <https://docs.oracle.com/en/java/javase/17/jpackage/packaging-overview.html>.

Expected auto-release assets include:

- Linux x64 portable `.tar.gz` and native `.deb`;
- Windows x64 portable `.zip` and native `.msi`;
- macOS x64 portable `.tar.gz`, native `.dmg`, and native `.pkg`;
- `quantumforge.jar`;
- `quantumforge-sbom.json` (CycloneDX);
- `SHA256SUMS`;
- release notes describing tested scope, known scientific limitations, and code-signing status.

## What “safe” means here

The portable installer is designed to reduce installation risk:

- no `sudo` and no write outside the user's selected root/command directory;
- rejects modified bundles containing symlinks/reparse points;
- stages a whole version before activation;
- preserves old user projects/settings;
- update downloads only HTTPS GitHub release assets;
- exact OS/architecture matching;
- SHA-256 must match the release manifest;
- archive path traversal is checked before extraction;
- uninstall validates an installer receipt and refuses dangerous roots;
- purge is separate and explicitly confirmed.

This does not make arbitrary source code trustworthy. Users should inspect release provenance, signatures, dependency/SBOM information, and security advisories.

## Important signing limitation

Checksums published beside an artifact detect accidental corruption and inconsistencies, but an attacker able to replace both artifact and checksum could defeat that check. Production releases should additionally have:

1. GitHub artifact provenance/attestation tied to the repository workflow;
2. Windows Authenticode signing from a protected hardware-backed/code-signing identity;
3. macOS Developer ID signing, hardened runtime, notarization, and stapling;
4. Linux package/repository signatures (and ideally Sigstore/minisign detached signatures);
5. protected release environments requiring maintainer approval.

The repository cannot invent those private signing identities. If signing secrets/certificates are not configured, release notes must state **unsigned** prominently; do not tell users to disable SmartScreen, Gatekeeper, antivirus, or package signature checking globally.

## Maintainer release procedure

1. Resolve all P0 release blockers in `CODE_AUDIT.md`.
2. Ensure the version agrees in `pom.xml`, `Version.java`, both environment property files, and `CHANGELOG.md`.
3. Start from a clean checkout and review:

   ```bash
   git status --short
   git diff --check
   mvn clean verify
   ```

4. Review dependency changes and generated CycloneDX SBOM. Run vulnerability scanning; no ignored vulnerability without a documented threat assessment.
5. Run parser/input golden tests against the pinned QE reference corpus.
6. Run GUI smoke tests on every target OS/architecture and Ubuntu 20.04 baseline.
7. Build portable and native artifacts only in the reviewed release workflow (template: `packaging/github-workflows/release.yml`).
8. Sign/notarize where credentials are available in a protected release environment.
9. Generate `SHA256SUMS` **after** all signing/notarization (those operations change artifacts).
10. Publish release notes with:
    - exact commit and build workflow run;
    - supported OS/CPU table;
    - code-signing status;
    - dependency and JavaFX/JDK versions;
    - tested QE version(s);
    - known incomplete integrations;
    - data migration/rollback instructions.
11. Install each published artifact on a clean VM, execute `quantumforge --version` and `--doctor`, launch/close the GUI, and uninstall.
12. Mark a release production-ready only after those post-publication checks.

## Rollback

Portable releases are stored in versioned directories. Before switching versions, the installer completes a staged copy. To recover manually:

```bash
ls ~/.local/share/quantumforge/versions
ln -sfn versions/KNOWN_GOOD ~/.local/share/quantumforge/current
quantumforge --version
```

On Windows, place the known-good version string in `%LOCALAPPDATA%\QuantumForge\current.txt`. For `.deb`, pacman, MSI, or PKG installations, use the package manager's documented downgrade/rollback procedure and a previously verified artifact.

Settings migration now merges new defaults while retaining existing user values. Nevertheless, back up `~/.quantumforge` before a major-version upgrade.

## Dependency policy

The Maven build replaces repository-bundled 2016-era libraries with declared, reviewable dependencies. Of particular importance, abandoned `com.jcraft:jsch:0.1.54` is replaced by the maintained compatible fork `com.github.mwiede:jsch`. Dependencies are pinned rather than floating.

Future policy:

- automated update PRs, not direct unreviewed bumps;
- CI dependency review and vulnerability scanning;
- reproducible lock/checksum strategy for Maven plugins and dependencies;
- retain SBOMs for every historical release;
- inspect native JavaFX/JDK components as well as Java JARs;
- generate third-party license reports and fail builds for unknown licenses.

## Security issues found in the audit

- SSH submission/transfer is incomplete and should remain disabled until strict host-key verification, safe auth, and quoting are implemented.
- SSH passwords are now transient/non-serialized; proxy password persistence is disabled; Materials API keys migrate from legacy plaintext to session memory. An OS keyring is still required before secure persistence can be offered.
- Legacy Materials Project v1 endpoints remain obsolete even though API-key persistence was hardened.
- The embedded JavaFX WebView broadens attack surface; restrict untrusted navigation/downloads or make it opt-in.
- UPF XML now blocks DTD/external entities and files over 128 MiB; other file/network parsers still need systematic size/time limits and structured errors.
- Historical media/icon provenance is incomplete.
- No code-signing identities are currently represented in source control (correctly); release signing must be configured externally.

See `CODE_AUDIT.md` for remediation details.
