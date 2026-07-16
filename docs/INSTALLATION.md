# QuantumForge installation, launch, update, and removal

This guide applies to QuantumForge 2.0.0. It deliberately separates the **QuantumForge desktop application** from computational engines. QuantumForge does not bundle Quantum ESPRESSO or licensed VASP/CASTEP binaries.

## 1. Supported platform policy

| Platform | Release form | Baseline/support statement |
|---|---|---|
| Ubuntu | portable `.tar.gz`, self-contained app image, `.deb` | Linux x64 builds use an Ubuntu 20.04 userspace baseline so they can run on 20.04, 22.04, 24.04, and later compatible glibc systems. Ubuntu 20.04 itself is past standard support; use Ubuntu Pro/ESM or upgrade. |
| Other Debian derivatives | portable archive / `.deb` | Best effort; test `quantumforge --doctor`. |
| Arch Linux | `.pkg.tar.zst` and portable archive | Rolling current x86-64; update the system before installation. |
| Windows | portable `.zip`, self-contained `.msi`/`.exe` | Supported security baseline is 64-bit Windows 10 22H2 or Windows 11. “All Windows versions” is neither technically nor safely possible: Microsoft-unsupported Windows 7/8/old Windows 10 are not supported. |
| macOS | portable `.tar.gz`, `.app`/`.dmg` | Intel x64 and Apple Silicon arm64 are separate artifacts. Use an artifact matching `uname -m`. Current maintained macOS releases are the target. |
| Remote Linux GUI | Linux portable/native installation over SSH X11 | MobaXterm, Linux X11, or XQuartz can display the GUI; performance is lower than a local desktop. |

The native package contains a private Java/JavaFX runtime. The smaller portable archive requires a system **64-bit Java 17 or newer** and includes platform-specific JavaFX libraries.

## 2. Choose an installation method

### Recommended for normal users: native package

- Ubuntu: `QuantumForge-2.0.0-linux-x64.deb`
- Arch: `quantumforge-2.0.0-1-x86_64.pkg.tar.zst`
- Windows: `QuantumForge-2.0.0.msi`
- macOS: `QuantumForge-2.0.0.dmg`

Advantages: private runtime, desktop integration, familiar OS uninstall. Use the OS package manager/installer to update.

### Recommended for clusters, MobaXterm, and no-root accounts: portable user install

Portable installation is unprivileged and versioned. It installs under:

- Linux/macOS application: `${XDG_DATA_HOME:-~/.local/share}/quantumforge`
- Linux/macOS command: `~/.local/bin/quantumforge`
- Windows application/command: `%LOCALAPPDATA%\QuantumForge`

Activation is atomic: a complete release is staged first and then selected. Existing `~/.quantumforge` research data is never overwritten by the installer.

## 3. Verify every download first

A release provides `SHA256SUMS`. Download the artifact and checksum from the **same GitHub release page**. Do not pipe a remote installer into a shell.

### Linux

```bash
VERSION=2.0.0
ASSET="QuantumForge-${VERSION}-linux-x64.tar.gz"
curl --proto '=https' --tlsv1.2 -fLO \
  "https://github.com/farhanwaris78/Quantum-Forge/releases/download/v${VERSION}/${ASSET}"
curl --proto '=https' --tlsv1.2 -fLO \
  "https://github.com/farhanwaris78/Quantum-Forge/releases/download/v${VERSION}/SHA256SUMS"
grep "  ${ASSET}$" SHA256SUMS | sha256sum --check --strict
```

Expected output: `...: OK`. Stop if verification fails.

### macOS

```bash
VERSION=2.0.0
ARCH=$(uname -m); [ "$ARCH" = arm64 ] || ARCH=x64
ASSET="QuantumForge-${VERSION}-macos-${ARCH}.tar.gz"
curl -fLO "https://github.com/farhanwaris78/Quantum-Forge/releases/download/v${VERSION}/${ASSET}"
curl -fLO "https://github.com/farhanwaris78/Quantum-Forge/releases/download/v${VERSION}/SHA256SUMS"
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

Checksums detect corruption/tampering relative to the release manifest. See [RELEASE_AND_SECURITY.md](RELEASE_AND_SECURITY.md) for signatures, attestation, SBOM, and the present code-signing limitation.

## 4. Ubuntu 20.04 and later

### 4.1 Portable user installation (best for SSH/MobaXterm)

Install prerequisites:

```bash
sudo apt update
sudo apt install openjdk-17-jre curl python3 xauth libgtk-3-0 libxtst6 libxrender1 libxi6
java -version
```

On newer Ubuntu versions, package names can change (for example time64 library transitions); installing `openjdk-17-jre` normally resolves its native dependencies.

After checksum verification:

```bash
tar -tzf "$ASSET" | grep -E '(^/|(^|/)\.\.(/|$))' && { echo "unsafe archive"; exit 1; } || true
tar -xzf "$ASSET"
cd "QuantumForge-${VERSION}-linux-x64"
./install.sh
```

If `~/.local/bin` is not already on `PATH`:

```bash
echo 'export PATH="$HOME/.local/bin:$PATH"' >> ~/.profile
export PATH="$HOME/.local/bin:$PATH"
```

Then:

```bash
quantumforge --version
quantumforge --doctor
quantumforge
```

### 4.2 Debian package

After verifying both package and checksum:

```bash
sudo apt install ./QuantumForge-2.0.0-linux-x64.deb
quantumforge --doctor
```

Update with a newly verified package:

```bash
sudo apt install ./QuantumForge-NEW-linux-x64.deb
```

Remove the app while retaining `~/.quantumforge`:

```bash
sudo apt remove quantumforge
# Optional removal of package-level configuration:
sudo apt purge quantumforge
```

`apt purge` does not normally remove files in your home directory. Delete `~/.quantumforge` only after making a backup and deciding that projects/settings/pseudopotentials are no longer needed.

## 5. Arch Linux

Update the rolling system first:

```bash
sudo pacman -Syu
```

For the portable archive install runtime prerequisites:

```bash
sudo pacman -S jre17-openjdk gtk3 libxtst alsa-lib xorg-xauth
archlinux-java status
```

Then use the same extraction and `./install.sh` procedure as Ubuntu.

For the pacman package:

```bash
sha256sum -c <(grep 'quantumforge-2.0.0-1-x86_64.pkg.tar.zst$' SHA256SUMS)
sudo pacman -U quantumforge-2.0.0-1-x86_64.pkg.tar.zst
quantumforge --doctor
```

Update by downloading/verifying the next package and using `pacman -U`. Remove while preserving home data:

```bash
sudo pacman -Rns quantumforge
```

## 6. Windows

### 6.1 Portable ZIP

Install a 64-bit JRE/JDK 17+ (for example, an OpenJDK distribution), then confirm in a **new** Command Prompt:

```cmd
java -version
```

After PowerShell checksum verification:

```powershell
Expand-Archive ".\QuantumForge-2.0.0-windows-x64.zip" -DestinationPath .
cd ".\QuantumForge-2.0.0-windows-x64"
Set-ExecutionPolicy -Scope Process Bypass
.\Install-QuantumForge.ps1
```

The installer adds only `%LOCALAPPDATA%\QuantumForge\bin` to the current user's `PATH`. Open a new Command Prompt:

```cmd
quantumforge --version
quantumforge --doctor
quantumforge
```

### 6.2 MSI/EXE

Use the self-contained native installer if it has a valid project code signature. If a release is unsigned, Windows SmartScreen can warn; compare SHA-256 with `SHA256SUMS` and read the release security notes before deciding. Never disable antivirus globally.

Update by running the newer verified installer. Remove through **Settings → Apps → Installed apps → QuantumForge**. This preserves `%USERPROFILE%\.quantumforge` unless you delete it explicitly.

### 6.3 Windows + WSLg

Windows 10 build 19044+ and Windows 11 can run Linux GUI applications through WSL2/WSLg. Install the Linux bundle inside WSL and enter `quantumforge`; do not install a Linux bundle on the Windows filesystem for HPC workloads. Microsoft documents `wsl --install` and `wsl --update` at <https://learn.microsoft.com/windows/wsl/tutorials/gui-apps>.

## 7. macOS

### 7.1 Portable archive

```bash
brew install --cask temurin@17   # one possible OpenJDK distribution
java -version
# verify, extract, then:
cd QuantumForge-2.0.0-macos-$( [ "$(uname -m)" = arm64 ] && echo arm64 || echo x64 )
./install.sh
quantumforge --doctor
```

### 7.2 DMG/app

Use the artifact matching Intel or Apple Silicon. Production macOS releases should be Developer-ID signed, notarized, and stapled. If this project has not configured those credentials, Gatekeeper will report an unsigned/unnotarized application. SHA-256 is necessary but is not a replacement for notarization.

Drag the app into `/Applications`, or use the generated `.pkg` if supplied. Update by replacing it with the newer verified/notarized release. Uninstall the app bundle:

```bash
rm -rf /Applications/QuantumForge.app
```

Do not remove `~/.quantumforge` unless you intend to erase user data.

## 8. Safe portable update and uninstall

### Update

```bash
quantumforge --update
```

The updater:

1. queries the latest stable GitHub release over HTTPS;
2. selects the exact OS/CPU archive;
3. downloads that archive and `SHA256SUMS` into a private temporary directory;
4. verifies SHA-256;
5. rejects absolute/parent-traversal archive paths;
6. stages the complete version and atomically switches `current`;
7. leaves `~/.quantumforge` untouched.

For unattended use: `quantumforge --update --yes`. Native/pacman/apt installations should use their package manager rather than the portable updater.

### Uninstall

```bash
quantumforge --uninstall
```

Default removal preserves `~/.quantumforge`. To remove user data too:

```bash
quantumforge --uninstall --purge
```

`--purge` deliberately asks for interactive confirmation even when other noninteractive flags are present. Back up projects first.

## 9. MobaXterm and SSH X11 forwarding

On the **remote Ubuntu/Arch host**, install QuantumForge and X11 authentication:

```bash
# Ubuntu
sudo apt install xauth x11-apps openjdk-17-jre libgtk-3-0
# Arch
sudo pacman -S xorg-xauth xorg-xclock jre17-openjdk gtk3
```

Administrator-side SSH server configuration must allow forwarding (`X11Forwarding yes` in `sshd_config` followed by a safe daemon reload). In MobaXterm:

1. start its built-in X server;
2. edit the SSH session and enable **X11-Forwarding**;
3. reconnect—the setting is negotiated only at login;
4. check the remote variable and a tiny X client:

```bash
echo "$DISPLAY"       # normally localhost:10.0 or similar
xclock                 # close it after the test
quantumforge --doctor
quantumforge
```

Do **not** manually export `DISPLAY` to your Windows IP when using SSH forwarding; let SSH set the authenticated loopback display. The launcher automatically requests JavaFX software rendering when both `SSH_CONNECTION` and `DISPLAY` are set. To force it manually:

```bash
QUANTUMFORGE_JAVA_OPTS='-Dprism.order=sw' quantumforge
```

MobaXterm documentation states that its X server starts by default and SSH forwarding sets `DISPLAY`; an independent HPC guide is <https://www.hpc2n.umu.se/documentation/access-and-accounts/ssh-x11-forwarding>.

For long-distance links, X11 can be slow, especially JavaFX WebView and 3D scenes. Prefer a remote desktop/VNC solution permitted by the cluster, or run calculations remotely and inspect results locally.

## 10. First launch and QE path

1. Run `quantumforge --doctor`.
2. Start `quantumforge`.
3. Open **Menu → Path**.
4. Select the **directory containing `pw.x`, `ph.x`, `dos.x`, `bands.x`, etc.**—not a single executable.
5. If using MPI, select the directory containing `mpirun`/`mpiexec` and check the MPI template.
6. Configure pseudopotentials and test a tiny reference SCF before production.

Settings/projects/logs live under `~/.quantumforge`. Version upgrades now merge defaults into the settings file rather than replacing user paths.

## 11. Troubleshooting

```bash
quantumforge --doctor
QUANTUMFORGE_JAVA_OPTS='-Dprism.verbose=true -Dprism.order=sw' quantumforge
```

- **`java: command not found`**: install Java 17+ or set `QUANTUMFORGE_JAVA_HOME`.
- **JavaFX modules missing**: the archive is incomplete or for the wrong platform; re-download and verify.
- **`Unable to open DISPLAY`**: fix X11/Wayland/SSH forwarding; do not run the GUI on a headless batch node.
- **GTK/GL errors**: install native GTK/X11 libraries and try software rendering.
- **QE command not found**: select the QE `bin` directory in Menu → Path and rerun doctor.
- **MPI fails but serial works**: verify that QE was built against the same MPI family being invoked; do not mix Open MPI and MPICH launchers/libraries.
- **GUI starts but advanced menu does nothing**: consult the capability matrix; many advanced classes are currently unwired prototypes.
- **Logs**: inspect `~/.quantumforge/_logOut.txt` and `_logErr.txt`. Launch with `QUANTUMFORGE_JAVA_OPTS='-Ddebug=true'` to keep console output visible where appropriate.
