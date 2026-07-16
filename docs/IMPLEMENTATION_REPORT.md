# Stabilization implementation report

This report records the first roadmap implementation batch after the repository-wide audit. It distinguishes code completed here from work that still requires external-engine and cross-platform validation.

## Implemented changes, impact, and next improvement

| Change | What was implemented | Expected impact | How it can be improved further |
|---|---|---|---|
| Capability truth source | Immutable registry with Supported/Partial/Experimental/Unavailable states; GUI status menu; `quantumforge --capabilities`; doctor reports executable presence separately from integration maturity. | Prevents users from treating VASP/CASTEP/phonopy detection or a class name as a validated workflow. | Generate evidence links from CI/reference results and version each capability contract. |
| Typed operation outcomes | Generic `OperationResult<T>` with success/failed/unsupported/cancelled, diagnostic code/message/cause/value; SSH/SFTP and local scheduler expose typed methods and old booleans are deprecated. | A no-op cannot silently report `true`; UI can explain whether an operation failed or is unavailable. | Propagate typed results through every run/UI layer and add structured recovery actions. |
| QE input preflight | Checks SYSTEM, nat/ntyp/card counts, species labels/masses/pseudopotentials, finite coordinates, ibrav=0 cell determinant, cutoffs, smearing/degauss, SOC/noncollinear conflicts, and k-point completeness. `RunningNode` blocks errors before launching QE. | Avoids queue time and misleading failures for deterministic invalid input. | Generate complete rules from each supported QE version and distinguish warnings requiring convergence evidence. |
| Γ-centred grid | Fixed implementation to set the real three-element K_POINTS offset array to `0 0 0`; previous code expected six elements and never executed. | The control now actually produces an unshifted Γ-centred automatic mesh. | Add emitted-input golden tests for odd/even meshes and document when shifted meshes are preferable. |
| Atom binder bounds | Three `index >= 0 OR index < size` guards changed to AND; secondary-card type guard corrected. | Prevents out-of-range atom rename/remove/fixed-state events from indexing empty or shorter position cards. | Replace integer index coupling with stable atom IDs/mappings and property-based event-sequence tests. |
| Band-gap analyzer | Validates finite rectangular band grids; explicit degeneracy; occupation-aware partial-metal detection; VBM/CBM k indices; directness evidence; DOS threshold crossing; diagnostics/classification. | Reduces false direct/indirect and metal/gap classifications; states limits of DOS-only analysis. | Parse QE XML occupations/spin channels, handle degeneracies and tetrahedron occupations, and benchmark against real QE fixtures. |
| Band-gap output parser | Regex parser for QE Fermi and highest-occupied/lowest-unoccupied summaries plus explicit auxiliary gap summaries; D exponents, diagnostics, and “directness unknown.” | Stops silent token failures and avoids inventing directness from a scalar gap. | Version adapters and XML-first parsing; retain source line/provenance for every value. |
| PDOS integration | Validated strictly increasing nonuniform energy grid, trapezoidal cumulative integration, finite checks and defensive copies. | Integrated DOS now has the correct energy-spacing factor and is not mutated through caller arrays. | Spin/channel conventions, electron-count diagnostics, interpolation uncertainty, and projwfc metadata parser. |
| Diffusivity helper | Least-squares MSD fit over explicit window; slope/intercept, R², slope/D standard error and strict time/data validation. | Better than an endpoint slope and exposes fit quality rather than one opaque number. | Build unwrapped multi-time-origin MSD, species/direction selection, diffusive-window stability and block/bootstrap uncertainty. |
| Scientific low-level helpers | Formation energy requires all references; explicit Wannier-Mott exciton estimate; temperature-aware CHE term; checked finite-difference piezo ratio; correctly named/validated McMillan estimate. | Prevents missing references, invalid domains and mislabeling simplified models as BSE/Pourbaix/Allen-Dynes workflows. | Replace with complete engine-backed plugins and reference datasets before production UI exposure. |
| Fabricated output removal | Random work-function maps, mock hyperfine constants, zero SOC/orbital magnetization, empty Weyl/STH results, fake volumetric differences, effective mass and arbitrary phonon thermodynamics now throw an explicit capability exception. | Most important scientific-safety impact: absence is reported honestly rather than returning publishable-looking nonsense. | Implement each as a separate reviewed plugin with equations, units, convergence and benchmarks. |
| UPF parser security | UTF-8, 128 MiB limit, DTD prohibition, external general/parameter entity disabling, no external schema/DTD access. | Reduces XXE/local-file/network expansion and memory abuse from untrusted pseudopotential files. | Stream metadata instead of buffering the complete file, add UPF v1/v2 corpora/fuzzing and SHA-256 family manifests. |
| Credential persistence | SSH password field is transient and excluded from Gson; proxy passwords are session-only; legacy proxy password is deleted; Materials API key migrates from plaintext properties to session memory. | Removes immediate plaintext-at-rest credential leakage in `~/.quantumforge`. | OS keyrings (Secret Service, Keychain, Credential Manager), explicit unlock UX and migration tests. |
| Convergence mock | Fixed five-filename command loop now throws unsupported until a configured sweep exists. | Prevents five unrelated jobs being presented as a convergence study. | Typed parameter range, generated input copies, target observable, tolerance/cost curve and conservative stopping. |
| Regression/static tests | Added capability, typed result, band-gap, PDOS, diffusivity/scientific helper, QE validator, Γ offset, binder bounds, UPF XXE, SSH serialization and convergence fail-closed tests. Added dependency-free repository checks that found and fixed a public class/filename mismatch and malformed Modeler FXML. | Creates executable specifications and catches failures before GUI startup. | Must be run in authorized Maven CI; then add real QE outputs, mutation/fuzz tests, GUI/installer VMs and coverage gates. |
| Installer/docs synchronization | Installer completion text points to doctor and capability report; README, complete installation tutorial, audit, release security, roadmap progress and Sphinx command docs updated. | Installed users see the same support status as developers and release notes. | Activate cross-platform workflow templates, sign/notarize packages, and execute clean-VM install/update/uninstall tests. |

## Validation performed in this workspace

- all Java/FXML/CSS/property sources decode as UTF-8;
- shell launch/install/update/uninstall/build scripts pass `bash -n`;
- changed text passes Git whitespace checks with legacy CRLF recognized;
- XML files parse structurally;
- local Markdown links resolve;
- source scans confirm removed random/mock scientific paths and corrected suspicious index guards;
- new tests are present as executable specifications.

## Validation blocked outside this workspace

The sandbox has no JDK/Maven and its network cannot retrieve the binaries/dependencies. The connected GitHub App also lacks workflow-file permission. Therefore Maven compilation, JUnit execution, native packaging, GUI launch, clean-VM testing, Windows signing and macOS notarization are not claimed as completed here. Workflow templates remain at `packaging/github-workflows/`; an authorized maintainer must activate and run them before publishing the draft release.

## Recommended next batch

1. Activate CI and fix any compiler/test findings before new scientific code.
2. Add project schema, atomic save/backup and migration regression tests.
3. Add run manifests, structured logs and QE executable profiles.
4. Add QE 7.5 golden corpus for SCF, spin, SOC, relax, bands and DOS.
5. Replace legacy Materials Project v1 with current `mp-api`/OPTIMADE.
6. Implement strict-host-key SSH/SFTP and one scheduler adapter end to end.
7. Implement spglib/seekpath through a versioned isolated service.
