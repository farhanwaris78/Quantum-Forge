# How to publish QuantumForge 2.0.0 (first production release)

This is the maintainer checklist for the **first public stabilization release**.
It assumes you have a 64-bit machine with JDK 17+, Maven 3.9+, Git, and (for
native packages) the platform packaging tools.

## 0. Preconditions

1. Scientific honesty: `quantumforge --capabilities` must match README claims.
2. No fabricated scientific outputs on stable menus.
3. Version strings agree in:
   - `pom.xml`
   - `src/quantumforge/ver/Version.java`
   - `CHANGELOG.md`
4. `LICENSE` / `THIRD_PARTY_NOTICES.md` reviewed.
5. You control the GitHub repository and can create releases.

## 1. Activate CI (one-time)

The GitHub App used by some agents **cannot** push workflow files. As a human maintainer:

```bash
mkdir -p .github/workflows
cp packaging/github-workflows/ci.yml .github/workflows/
cp packaging/github-workflows/release.yml .github/workflows/
git add .github/workflows
git commit -m "Activate CI and release workflows"
git push
```

Confirm Actions run on the next push.

## 2. Local verification (required)

```bash
git status --short
git diff --check
python3 scripts/static_checks.py
mvn -B -ntp clean verify
```

Optional but strongly recommended:

```bash
# Portable Linux artifact smoke
packaging/build-portable.sh linux x64
BUNDLE=target/portable/QuantumForge-2.0.0-linux-x64
"$BUNDLE/bin/quantumforge" --version
"$BUNDLE/bin/quantumforge" --doctor
"$BUNDLE/bin/quantumforge" --capabilities
HOME=/tmp/qf-smoke-home "$BUNDLE/install.sh" --yes
HOME=/tmp/qf-smoke-home /tmp/qf-smoke-home/.local/bin/quantumforge --doctor
HOME=/tmp/qf-smoke-home /tmp/qf-smoke-home/.local/bin/quantumforge --uninstall --yes
```

On a machine with a display (or MobaXterm X11):

```bash
quantumforge   # open/close GUI; open an example project if present
```

## 3. Tag the release

```bash
git checkout master   # or merge your stabilization branch first
git pull
# ensure version is 2.0.0 everywhere
git tag -a v2.0.0 -m "QuantumForge 2.0.0 stabilization release"
git push origin v2.0.0
```

Create a **draft** GitHub Release for `v2.0.0` (or publish and let the workflow upload).

## 4. Build platform artifacts

Preferred: run `packaging/github-workflows/release.yml` via `workflow_dispatch` / release publish.

Manual fallback (must be on each OS):

```bash
# Linux (ideally Ubuntu 20.04 container for glibc baseline)
packaging/build-portable.sh linux x64
packaging/build-native.sh deb

# macOS Intel / Apple Silicon (on matching hardware)
packaging/build-portable.sh macos x64    # or arm64
packaging/build-native.sh dmg

# Windows (PowerShell)
.\packaging\build-portable.ps1 -Machine x64
.\packaging\build-native.ps1 -Type msi

# Arch package from a verified Linux portable archive
export QF_VERSION=2.0.0
export QF_ARCHIVE=$PWD/target/release/QuantumForge-2.0.0-linux-x64.tar.gz
export QF_SHA256=$(sha256sum "$QF_ARCHIVE" | awk '{print $1}')
packaging/arch/build-package.sh
```

Collect into one directory, then:

```bash
cd dist
sha256sum * > SHA256SUMS
# optional: minisign / gpg detached signatures
```

## 5. Signing (production bar)

| Platform | Requirement |
|---|---|
| Windows | Authenticode sign MSI/EXE |
| macOS | Developer ID sign + notarize + staple |
| Linux | At least detached GPG/minisign + GitHub provenance attestation |
| All | Publish `SHA256SUMS` **after** signing (signing changes hashes) |

If you cannot sign yet, release notes **must** say **unsigned** and users must verify SHA-256 only.

## 6. Release notes template

Include:

- commit SHA and workflow run URL
- supported OS/CPU table
- code-signing status
- JDK / JavaFX versions
- tested Quantum ESPRESSO version(s)
- capability honesty table (copy from README)
- install one-liner pointing to `docs/TUTORIAL_INSTALL.md`
- known limitations (SSH unavailable, VASP/CASTEP incomplete, …)
- migration notes for `~/.quantumforge`

## 7. Post-publish clean-VM smoke (do not skip)

On fresh VMs for each artifact:

1. Verify SHA-256.
2. Install.
3. `quantumforge --version && quantumforge --doctor && quantumforge --capabilities`
4. Launch GUI once.
5. Uninstall; confirm `~/.quantumforge` preserved unless purge.

## 8. User install command (what you tell researchers)

```bash
# after checksum verification of the portable archive
./install.sh
export PATH="$HOME/.local/bin:$PATH"
quantumforge
```

Full guide: [`docs/TUTORIAL_INSTALL.md`](TUTORIAL_INSTALL.md).

## 9. Rollback

Portable:

```bash
ls ~/.local/share/quantumforge/versions
ln -sfn versions/2.0.0 ~/.local/share/quantumforge/current
```

Package managers: reinstall previous verified `.deb` / `.pkg.tar.zst` / MSI.

## 10. Definition of “first release done”

- [ ] `mvn verify` green on CI
- [ ] Portable artifacts for Linux/Windows/macOS published with `SHA256SUMS`
- [ ] Install/update/uninstall smoke passed on at least one Linux host
- [ ] Capability report matches documentation
- [ ] No known path returns fabricated scientific numbers as success
- [ ] Release notes list limitations honestly
