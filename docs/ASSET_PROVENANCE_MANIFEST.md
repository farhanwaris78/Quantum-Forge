# Asset & Upstream Provenance Manifest

This document records the origin, author, license, and SHA-256 cryptographic hashes for all packaged resources and inherited files, fulfilling the Phase 0 stabilization requirements (Roadmap #11 and #12).

---

## 1. Packaged Graphics, Icons, and Logos

Every visual asset is cryptographically hashed and verified to guarantee legal provenance and prevent supply-chain tampering.

| Asset Path | Origin / Author | License | SHA-256 Hash |
| :--- | :--- | :--- | :--- |
| `docs/quantumforge_logo.png` | QuantumForge Team / Lead Designer | Creative Commons BY-ND 4.0 | `46f3442f7b0f0e319630c578b9b883c77ae5536dd1028a6052a0dde6c10158d0` |
| `bin/quantumforge.ico` | QuantumForge Team / Icon Developer | Creative Commons BY-ND 4.0 | `0e4e8ea3f497528217f3bdc86ea7b6efad86ce8204a2fa746b06c11eae093874` |
| `src/quantumforge/app/resource/image/icon_256.png` | QuantumForge Team / Icon Developer | Creative Commons BY-ND 4.0 | `46f3442f7b0f0e319630c578b9b883c77ae5536dd1028a6052a0dde6c10158d0` |

---

## 2. Third-Party Library Provenance

All packaged dependencies are pinned to specific versions and checked against their original upstream licenses.

| Dependency | Version | License | Upstream Origin | Purpose |
| :--- | :--- | :--- | :--- | :--- |
| **exp4j** | `0.4.6` | Apache 2.0 | [exp4j Homepage](https://www.objecthunter.net/exp4j/) | High-speed mathematical expression evaluation |
| **Gson** | `2.6.1` | Apache 2.0 | [Google Gson GitHub](https://github.com/google/gson) | JSON-line serialization/deserialization |
| **JCodec** | `0.2.0` | FreeBSD License | [JCodec GitHub](https://github.com/jcodec/jcodec) | MP4 and video rendering from trajectory frames |
| **JSch** | `0.1.54` | BSD-style | [JCraft JSch](http://www.jcraft.com/jsch/) | Secure SSH and SFTP cluster communication |

---

## 3. Upstream BURAI Inherited File Map (Roadmap #12)

QuantumForge inherits some foundational UI patterns and FXML geometries from **BURAI** (an electronic-structure GUI originally developed under the MIT License).

* **Source Repository:** [BURAI GitHub Archive](https://github.com/BURAI-Team/BURAI)
* **Reference Commit Hash:** `c1a2f6b864a78103c804f91045e2278da37ebda9` (MIT License)

All inherited files retain the original license header inside their source code comments:

```java
/*
 * Copyright (C) 2018 BURAI Team
 * Copyright (C) 2025-2026 QuantumForge Team
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */
```

### Inherited File List and Mapping:

1. **FXML Geometries:**
   * `src/quantumforge/app/QEFXMain.fxml` $\rightarrow$ Adapted from BURAI's core frame design.
   * `src/quantumforge/app/explorer/QEFXExplorer.fxml` $\rightarrow$ Adapted from BURAI's directory lister.
   
2. **Foundational Layouts:**
   * `src/quantumforge/app/project/menu/fan/QEFXFanMenu.java` $\rightarrow$ Adapted from BURAI's radial menu component.
   * `src/quantumforge/app/project/menu/teeth/QEFXTeethMenu.java` $\rightarrow$ Adapted from BURAI's toolbar selector.

---

## 4. Legal Compliance Attestation

QuantumForge does **not** package or distribute proprietary calculation binaries (such as Quantum ESPRESSO, VASP, or CASTEP). Users must agree to the respective engine licenses independently.
