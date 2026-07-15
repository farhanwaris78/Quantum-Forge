# QuantumForge Installation Guide (Ubuntu 20.04 Focal Fossa)

## 1. System Requirements
*   **Operating System**: Ubuntu 20.04 LTS (64-bit)
*   **Java Runtime**: OpenJDK 8 or 11 with JavaFX support.
*   **Quantum ESPRESSO**: Version 7.5 (recommended) or higher.
*   **Thermo_pw**: Version 2.1.1 (for elastic properties).

## 2. Java Environment Setup
Ubuntu 20.04 provides OpenJDK 11 by default, but QuantumForge's graphical engine is optimized for Java 8 with integrated JavaFX.

### Prerequisite Libraries (Fixes common Ubuntu crashes)
```bash
sudo apt update
sudo apt install libcanberra-gtk-module libcanberra-gtk3-module libavcodec-extra
```

### Option A: Install OpenJDK 8 (Recommended for best stability)
```bash
sudo apt update
sudo apt install openjdk-8-jdk openjfx
```

### Option B: Use OpenJDK 11 with external JavaFX
If you must use Java 11, you will need to point to the JavaFX modules:
```bash
sudo apt install openjdk-11-jdk openjfx
```

## 3. Install Quantum ESPRESSO 7.5
We recommend compiling from source or using the pre-built binaries in the `exec.LINUX` folder provided with QuantumForge.

To compile from source on Ubuntu 20.04:
```bash
sudo apt install build-essential gcc gfortran libopenmpi-dev make libfftw3-dev liblapack-dev
wget https://gitlab.com/QEF/q-e/-/archive/qe-7.5/q-e-qe-7.5.tar.gz
tar -xvf q-e-qe-7.5.tar.gz
cd q-e-qe-7.5
./configure
make pw ph dos bands projwfc neb
```

## 4. Launching QuantumForge
1.  Navigate to the QuantumForge directory.
2.  Run the launcher script:
```bash
./quantumforge.sh
```
*(If the script doesn't exist, use: `java -jar quantumforge.jar`)*

## 5. Configuring Paths
Once launched:
1.  Go to **Menu > Path of QE**.
2.  Set the path to your `pw.x` and other binaries (usually in `q-e-qe-7.5/bin`).
3.  Set the path to your **Pseudopotentials** library.

---
**Note**: This software is Proprietary and Confidential. Unauthorized distribution is prohibited.
