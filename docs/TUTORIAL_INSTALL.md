# QuantumForge full installation tutorial

This is the complete, operator-oriented guide for installing, launching, updating,
and uninstalling **QuantumForge 2.0.0** on:

| Platform | Supported release forms |
|---|---|
| Ubuntu 20.04 → current | portable `.tar.gz`, `.deb`, self-contained app image |
| Debian derivatives | portable / `.deb` (best effort) |
| Arch Linux (rolling) | portable `.tar.gz`, `.pkg.tar.zst` |
| Windows 10 22H2 / 11 (64-bit) | portable `.zip`, `.msi` |
| macOS Intel x64 / Apple Silicon arm64 | portable `.tar.gz`, `.dmg` / `.app` |
| Remote GUI via MobaXterm / SSH X11 | Linux portable install on the remote host |

> **Important honesty rule:** QuantumForge is the **desktop GUI**. It does **not**
> ship Quantum ESPRESSO, VASP, CASTEP, thermo_pw, phonopy, BoltzTraP2, or XCrySDen.
> Install those engines separately under their own licenses.

---

## 0. The one command you will use every day

After installation, the GUI starts with:

```text
quantumforge
```

That single word works in:

- a local Ubuntu/Arch terminal
- Windows Command Prompt / PowerShell
- macOS Terminal
- a MobaXterm SSH session with X11 forwarding enabled

Useful non-GUI commands:

```text
quantumforge --version
quantumforge --doctor
quantumforge --capabilities
quantumforge --update
quantumforge --uninstall
quantumforge --help
```

| Command | Purpose |
|---|---|
| `quantumforge` | start the JavaFX GUI |
| `--version` | print QuantumForge + Java versions |
| `--doctor` | check Java, display, QE profile, optional tools |
| `--capabilities` | honest support matrix (Supported / Partial / Experimental / Unavailable) |
| `--update` | verified portable update (SHA-256) |
| `--uninstall` | remove portable install; keep `~/.quantumforge` by default |

---

## 1. Safety model (read once)

### What “safe install / update / uninstall” means

1. **Checksum first** — never install an artifact whose SHA-256 does not match `SHA256SUMS`.
2. **No `curl | bash`** — download, verify, then run a local installer.
3. **Unprivileged portable install** — default install lives under your home directory; no `sudo` required.
4. **Atomic activation** — a full version is staged, then switched with a rename/`current` pointer.
5. **Research data is separate** — projects and settings live in `~/.quantumforge` (Windows: `%USERPROFILE%\.quantumforge`) and are **not** deleted by a normal uninstall.
6. **Purge is explicit** — `quantumforge --uninstall --purge` asks for confirmation even in otherwise non-interactive modes.
7. **No fake remote success** — SSH/HPC submission is currently unavailable/fail-closed.
8. **Activation is verified, not assumed** — the installer checks that the `current` pointer and the `quantumforge` command link actually switched before reporting success; an update downloads over HTTPS only (loopback mirrors excepted, see §9.2).

### What checksums do *not* replace

If an attacker can replace **both** the archive and `SHA256SUMS`, checksums alone are insufficient.
Production releases should also have GitHub provenance attestation and OS code signatures
(Windows Authenticode, macOS notarization). See [RELEASE_AND_SECURITY.md](RELEASE_AND_SECURITY.md).

---

## 2. Download and verify (all platforms)

Replace `2.0.0` if your release tag differs.

### Linux

```bash
VERSION=2.0.0
ASSET="QuantumForge-${VERSION}-linux-x64.tar.gz"
BASE="https://github.com/farhanwaris78/Quantum-Forge/releases/download/v${VERSION}"

curl --proto '=https' --tlsv1.2 -fLO "${BASE}/${ASSET}"
curl --proto '=https' --tlsv1.2 -fLO "${BASE}/SHA256SUMS"
grep "  ${ASSET}$" SHA256SUMS | sha256sum --check --strict
```

Expected: `QuantumForge-2.0.0-linux-x64.tar.gz: OK`

### macOS

```bash
VERSION=2.0.0
ARCH=$(uname -m); [ "$ARCH" = arm64 ] || ARCH=x64
ASSET="QuantumForge-${VERSION}-macos-${ARCH}.tar.gz"
BASE="https://github.com/farhanwaris78/Quantum-Forge/releases/download/v${VERSION}"

curl -fLO "${BASE}/${ASSET}"
curl -fLO "${BASE}/SHA256SUMS"
EXPECTED=$(awk -v f="$ASSET" '$2==f {print $1}' SHA256SUMS)
printf '%s  %s\n' "$EXPECTED" "$ASSET" | shasum -a 256 -c -
```

### Windows PowerShell

```powershell
$Version = "2.0.0"
$Asset = "QuantumForge-$Version-windows-x64.zip"
$Base = "https://github.com/farhanwaris78/Quantum-Forge/releases/download/v$Version"
Invoke-WebRequest "$Base/$Asset" -OutFile $Asset
Invoke-WebRequest "$Base/SHA256SUMS" -OutFile SHA256SUMS
$Expected = ((Get-Content SHA256SUMS | Select-String "\s\*?$([regex]::Escape($Asset))$").Line -split '\s+')[0]
$Actual = (Get-FileHash $Asset -Algorithm SHA256).Hash
if ($Actual -ne $Expected) { throw "SHA-256 mismatch: do not install" }
```

**Stop if verification fails.**

---

## 3. Ubuntu 20.04 and later

Ubuntu 20.04 is the **glibc baseline** for Linux portable/native builds so the same
artifact can run on 20.04, 22.04, 24.04, and later compatible systems.

### 3.1 Portable install (recommended for clusters and MobaXterm)

```bash
sudo apt update
sudo apt install openjdk-17-jre curl python3 xauth \
  libgtk-3-0 libxtst6 libxrender1 libxi6
java -version
```

```bash
tar -tzf "$ASSET" | grep -E '(^/|(^|/)\.\.(/|$))' && { echo "unsafe archive"; exit 1; } || true
tar -xzf "$ASSET"
cd "QuantumForge-${VERSION}-linux-x64"
./install.sh
```

Add `~/.local/bin` to `PATH` if needed:

```bash
echo 'export PATH="$HOME/.local/bin:$PATH"' >> ~/.profile
export PATH="$HOME/.local/bin:$PATH"
```

```bash
quantumforge --version
quantumforge --doctor
quantumforge --capabilities
quantumforge
```

Install layout:

| Item | Path |
|---|---|
| Application versions | `~/.local/share/quantumforge/versions/<version>/` |
| Active version | `~/.local/share/quantumforge/current` → symlink |
| Command | `~/.local/bin/quantumforge` |
| Desktop entry | `~/.local/share/applications/quantumforge.desktop` |
| User data | `~/.quantumforge/` |

### 3.2 Debian package

```bash
sudo apt install ./QuantumForge-2.0.0-linux-x64.deb
quantumforge --doctor
quantumforge
```

Update:

```bash
sudo apt install ./QuantumForge-NEW-linux-x64.deb
```

Remove (keeps home data):

```bash
sudo apt remove quantumforge
# optional package config purge (still usually keeps ~/.quantumforge):
sudo apt purge quantumforge
```

### 3.3 Safe portable update / uninstall

```bash
quantumforge --update
# or non-interactive:
quantumforge --update --yes

quantumforge --uninstall
# also delete research data (asks for confirmation):
quantumforge --uninstall --purge
```

---

## 4. Arch Linux

```bash
sudo pacman -Syu
sudo pacman -S jre17-openjdk gtk3 libxtst alsa-lib xorg-xauth
```

Portable path (same as Ubuntu after extracting the Linux archive):

```bash
./install.sh
quantumforge --doctor
quantumforge
```

Pacman package:

```bash
sha256sum -c <(grep 'quantumforge-2.0.0-1-x86_64.pkg.tar.zst$' SHA256SUMS)
sudo pacman -U quantumforge-2.0.0-1-x86_64.pkg.tar.zst
quantumforge
```

Remove while keeping home data:

```bash
sudo pacman -Rns quantumforge
```

Note: on pacman installs, `quantumforge --update` / `--uninstall` intentionally
redirect you to `pacman -U` / `pacman -Rns` so package ownership stays consistent.

---

## 5. Windows (10 22H2 / 11, 64-bit)

Unsupported: Windows 7/8 and unpatched old Windows 10. Those platforms lack a safe
JavaFX/OpenJDK baseline for this project.

### 5.1 Portable ZIP

1. Install a **64-bit** JRE/JDK 17+.
2. Verify the ZIP checksum (section 2).
3. Extract and install:

```powershell
Expand-Archive ".\QuantumForge-2.0.0-windows-x64.zip" -DestinationPath .
cd ".\QuantumForge-2.0.0-windows-x64"
Set-ExecutionPolicy -Scope Process Bypass
.\Install-QuantumForge.ps1
```

Open a **new** Command Prompt:

```cmd
quantumforge --version
quantumforge --doctor
quantumforge --capabilities
quantumforge
```

Install layout:

| Item | Path |
|---|---|
| Application | `%LOCALAPPDATA%\QuantumForge\versions\<version>\` |
| Active version | `%LOCALAPPDATA%\QuantumForge\current.txt` |
| Command | `%LOCALAPPDATA%\QuantumForge\bin\quantumforge.cmd` |
| User data | `%USERPROFILE%\.quantumforge\` |

Update / uninstall:

```cmd
quantumforge --update
quantumforge --uninstall
```

### 5.2 MSI

Use the signed MSI when available. If a release is unsigned, Windows SmartScreen may
warn — verify SHA-256 and read the release notes before proceeding. Do **not**
disable antivirus globally.

Remove via **Settings → Apps → QuantumForge**. User data is preserved unless you
delete `%USERPROFILE%\.quantumforge` yourself.

### 5.3 WSL2 / WSLg

Install the **Linux** portable/deb package *inside* WSL, then run `quantumforge`.
Do not place Linux binaries on the Windows filesystem for heavy project I/O.

---

## 6. macOS (Intel and Apple Silicon)

Use the artifact matching `uname -m` (`x64` or `arm64`).

### 6.1 Portable archive

```bash
brew install --cask temurin@17   # example OpenJDK distribution
java -version
# after checksum verification:
cd QuantumForge-2.0.0-macos-$( [ "$(uname -m)" = arm64 ] && echo arm64 || echo x64 )
./install.sh
quantumforge --doctor
quantumforge
```

### 6.2 DMG / app bundle

1. Open the verified DMG.
2. Drag `QuantumForge.app` to `/Applications`.
3. Production releases should be Developer-ID signed and notarized. If not,
   Gatekeeper will block; SHA-256 alone is not a substitute for notarization.

Uninstall app only:

```bash
rm -rf /Applications/QuantumForge.app
```

Keep or delete `~/.quantumforge` deliberately.

---

## 7. MobaXterm / remote X11 (the “type a word → GUI opens” workflow)

### On the remote Ubuntu/Arch host

```bash
# Ubuntu
sudo apt install xauth x11-apps openjdk-17-jre libgtk-3-0
# Arch
sudo pacman -S xorg-xauth xorg-xclock jre17-openjdk gtk3
```

Install QuantumForge with the portable method above.

Ensure `sshd_config` has `X11Forwarding yes` (admin change + daemon reload).

### In MobaXterm

1. Start the built-in X server.
2. Edit the SSH session → enable **X11-Forwarding**.
3. Reconnect (X11 is negotiated only at login).
4. Test:

```bash
echo "$DISPLAY"     # e.g. localhost:10.0
xclock              # close after the test
quantumforge --doctor
quantumforge        # <-- the word that starts the GUI
```

Do **not** manually set `DISPLAY` to your Windows IP when using SSH forwarding.
The launcher automatically uses JavaFX software rendering when both `SSH_CONNECTION`
and `DISPLAY` are set. Force it with:

```bash
QUANTUMFORGE_JAVA_OPTS='-Dprism.order=sw' quantumforge
```

For slow WAN links, prefer VNC/remote desktop if your site allows it, or run jobs
remotely and inspect results locally.

---

## 8. First launch: point QuantumForge at Quantum ESPRESSO

1. `quantumforge --doctor`
2. `quantumforge`
3. **Menu → Path**
4. Select the directory containing `pw.x`, `ph.x`, `dos.x`, `bands.x`, … (not a single file)
5. Optionally set the MPI directory (`mpirun` / `mpiexec`)
6. Configure pseudopotentials
7. Run a tiny reference SCF before production work

Doctor now also prints a **QE executable profile** (selected bin directory, found
tools, parsed `pw.x` version when probe succeeds).

Before launch, QuantumForge runs deterministic preflight (species/atom counts,
pseudos, lattice, cutoffs, smearing, SOC/noncollinear, k-points). Invalid jobs
are blocked. Successful/failed stages write a run manifest
(`.quantumforge.run-manifest.jsonl`) with command, hashes, timestamps, and exit code.

---

## 9. Build from source (developers / release maintainers)

Requirements: 64-bit JDK 17+, Maven 3.9+, Git, platform GUI libraries.

```bash
git clone https://github.com/farhanwaris78/Quantum-Forge.git
cd Quantum-Forge
mvn clean verify
mvn javafx:run
```

Portable artifacts:

```bash
packaging/build-portable.sh linux x64
packaging/build-portable.sh macos arm64
```

```powershell
.\packaging\build-portable.ps1 -Machine x64
```

Native packages (jpackage, on the target OS):

```bash
packaging/build-native.sh deb    # or rpm, dmg, pkg, app-image
```

```powershell
.\packaging\build-native.ps1 -Type msi
```

Arch package from a verified portable archive:

```bash
export QF_ARCHIVE=/absolute/path/QuantumForge-2.0.0-linux-x64.tar.gz
export QF_SHA256=$(sha256sum "$QF_ARCHIVE" | awk '{print $1}')
export QF_VERSION=2.0.0
packaging/arch/build-package.sh
```

CI and release workflow **templates** live under `packaging/github-workflows/`.
Copy them into `.github/workflows/` on a machine/token with GitHub `workflows`
permission before publishing production releases.

### 9.1 Prove the packaging end to end (no display required)

Every claim this tutorial makes about the portable installer is executed by:

```bash
packaging/tests/smoke-portable.sh
# recommended: point at a JDK 17+ so the REAL launcher class runs too
QF_SMOKE_JDK=/path/to/jdk/bin packaging/tests/smoke-portable.sh
# full fidelity: real JavaFX module jars instead of empty placeholders
QF_SMOKE_JAVAFX_DIR=/path/to/javafx-jars QF_SMOKE_JDK=/path/to/jdk/bin \
  packaging/tests/smoke-portable.sh
```

The script fabricates a portable bundle in a throw-away directory, then runs the
**shipped** `install.sh`, `update.sh`, `uninstall.sh` and `bin/quantumforge`
against it inside a sandbox HOME: argument composition and X11-forwarding
software-rendering guard of the launcher, real-JVM `--version/--help/--doctor/
--capabilities` runs, bare-word `quantumforge` invocation through PATH,
install/reinstall/uninstall/purge safety, a loopback release-mirror update with
checksum-tampering and archive-traversal refusals, and the checksum verifier.
Nothing outside its temporary directory is touched; see
`packaging/tests/README.md` for exactly what is and is not exercised.

### 9.2 Update mirrors (testing and intranet mirrors)

Production updates are **HTTPS-only** against the GitHub releases API. Two
environment overrides exist for release testing and site mirrors:

| Variable | Effect |
|---|---|
| `QUANTUMFORGE_UPDATE_REPOSITORY` | `owner/name` (default `farhanwaris78/Quantum-Forge`) |
| `QUANTUMFORGE_UPDATE_API_BASE` | GitHub-compatible API base. `https://` hosts only; plain HTTP is accepted **only** on loopback (`127.0.0.1`, `localhost`, `[::1]`) so the smoke test and local mirrors can run — anything else is refused before any network I/O. |

---

## 10. Troubleshooting

```bash
quantumforge --doctor
QUANTUMFORGE_JAVA_OPTS='-Dprism.verbose=true -Dprism.order=sw' quantumforge
```

| Symptom | Action |
|---|---|
| `java: command not found` | Install Java 17+ or set `QUANTUMFORGE_JAVA_HOME` |
| JavaFX modules missing | Wrong/incomplete archive; re-download and verify SHA-256 |
| `Unable to open DISPLAY` | Fix X11/Wayland/SSH forwarding |
| GTK/GL errors | Install GTK/X11 libs; try software rendering |
| QE not found | Menu → Path to QE `bin`; re-run doctor |
| MPI fails, serial works | Match MPI family between QE build and launcher |
| Advanced menu does nothing | Check `quantumforge --capabilities` — many areas are experimental/unavailable |
| Crash | Inspect `~/.quantumforge/logs/quantumforge.log` and `~/.quantumforge/logs/crashes/` |

---

## 11. Research data and rollback

| Kind | Location |
|---|---|
| Projects / settings / logs | `~/.quantumforge` |
| Structured logs | `~/.quantumforge/logs/quantumforge.log` |
| Crash bundles (local only) | `~/.quantumforge/logs/crashes/` |
| Portable app versions | `~/.local/share/quantumforge/versions/` |

Rollback portable version without re-download:

```bash
ls ~/.local/share/quantumforge/versions
ln -sfn versions/KNOWN_GOOD ~/.local/share/quantumforge/current
quantumforge --version
```

On Windows, put the known-good version string into
`%LOCALAPPDATA%\QuantumForge\current.txt`.

Back up `~/.quantumforge` before major upgrades. Project property files now use
atomic writes, schema version metadata, and last-known-good `.bak` copies.

---

## 12. Related documents

- [INSTALLATION.md](INSTALLATION.md) — concise install reference
- [SCIENTIFIC_SOFTWARE_GUIDE.md](SCIENTIFIC_SOFTWARE_GUIDE.md) — QE / phonopy / etc. setup
- [RELEASE_AND_SECURITY.md](RELEASE_AND_SECURITY.md) — signing, SBOM, integrity
- [CODE_AUDIT.md](CODE_AUDIT.md) — scientific honesty audit
- [FUTURE_ROADMAP.md](FUTURE_ROADMAP.md) — prioritized implementation plan
