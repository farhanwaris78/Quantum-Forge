# QuantumForge v2.0.3 release notes

## Highlights

- Consolidates all roadmap planning into one maintained file: `docs/ROADMAP.md`.
- Removes the older scattered roadmap files from the repository after merging their contents into the consolidated roadmap.
- Keeps the runtime version declarations aligned at `2.0.3` through the auto-release version bump.
- Publishes portable and native installer assets from the auto-release workflow.

## Release assets

This release is expected to publish:

- Linux x64 portable archive: `.tar.gz`
- Linux x64 native package: `.deb`
- Windows x64 portable archive: `.zip`
- Windows x64 native installer: `.msi`
- macOS x64 portable archive: `.tar.gz`
- macOS x64 native disk image: `.dmg`
- macOS x64 native installer package: `.pkg`
- `quantumforge.jar`
- `quantumforge-sbom.json`
- `SHA256SUMS`

## Integrity and signing

- `SHA256SUMS` is generated after the release assets are collected.
- Artifacts are unsigned because no Windows Authenticode, macOS Developer ID/notarization, or Linux package-signing secrets are configured for this repository.
- Users should verify checksums before installing and should expect normal OS warnings for unsigned installers.

## Documentation changes

The following roadmap files were merged into `docs/ROADMAP.md` and removed:

- `docs/FUTURE_ROADMAP.md`
- `docs/QE_INTEGRATION_ROADMAP.md`
- `ROADMAP_EXTENDED.md`
- `ROADMAP_MEGA.md`
- `ROADMAP_V3.md`

## Compatibility notes

No scientific engine behavior changes are intended in this release. The release is documentation and release-packaging focused, with the consolidated roadmap becoming the single planning reference.
