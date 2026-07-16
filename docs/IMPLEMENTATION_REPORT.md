# Stabilization implementation report

This report records roadmap implementation batches after the repository-wide audit.
It distinguishes code completed in-tree from work that still requires external-engine
and cross-platform validation.

## Batch 1 (prior) — scientific honesty and capability truth

| Change | What was implemented | Expected impact | How it can be improved further |
|---|---|---|---|
| Capability truth source | Immutable registry with Supported/Partial/Experimental/Unavailable; GUI status menu; `quantumforge --capabilities` | Prevents treating PATH detection as workflow validation | Version each capability contract from CI evidence |
| Typed operation outcomes | `OperationResult<T>` with success/failed/unsupported/cancelled | No-op cannot report bare `true` | Propagate through every run/UI layer |
| QE input preflight | Structural/cutoff/spin/k-point checks before launch | Avoids wasted queue time | Version-generated keyword rules |
| Fabricated output removal | Random/zero/mock advanced paths throw unsupported | Absence reported honestly | Real engine-backed plugins |
| Credential persistence | Session-only secrets; no plaintext SSH/proxy in properties | Stops immediate at-rest leakage | OS keyring backends |
| Installer foundation | Portable install/update/uninstall, SBOM, workflow templates | Safe user-space lifecycle | Signed native packages |

## Batch 2 (this branch) — project safety, run provenance, packaging completion

| Change | Roadmap # | What was implemented | Expected impact | Next improvement |
|---|---:|---|---|---|
| Structured logging | 4 | `quantumforge.com.log.AppLog` — job IDs, severity, rotating log, secret redaction | Failed runs diagnosable without dumping secrets | Full SLF4J backend + correlation across UI threads |
| Crash reporter | 5 | `CrashReporter` local redacted bundles under `~/.quantumforge/logs/crashes/` | Reproducible local diagnostics; no auto-upload | Optional user-approved share bundle UI |
| Project schema | 6 | `ProjectSchema` v1; status JSON carries `schemaVersion` + `quantumforgeVersion` | Future migrations can refuse unknown formats safely | Explicit migration/rollback tests for v2+ |
| Atomic writes | 7 | `AtomicFileWriter`; used by project property JSON, QE input saves, run input writes; `.bak` copies | Power loss cannot truncate the only project copy mid-write | Directory-level journal for multi-file saves |
| Autosave | 8 | `ProjectAutosave` debounced snapshots in `.quantumforge.autosave/` | Recoverable intermediate edits without overwriting deliberate saves | Wire GUI recovery picker |
| QE executable profile | 21 | `QEExecutableProfile` probes bin dir, tools, `pw.x` version; doctor report | Doctor verifies selected binaries, not only PATH | Cache profile + MPI linkage/build commit |
| Run manifest | 28 | `.quantumforge.run-manifest.jsonl` per stage with command/hashes/exit | Every plot can show which process produced it | GUI provenance panel (item 128) |
| Process-tree cancel | 29 | EXIT file + `ProcessTreeKiller` descendants; cancel status in manifests | Stop reliably ends MPI children; cancel ≠ silent failure | Timeout policies per stage; graceful QE stop-file variants |
| Packaging / CLI / tutorial | 13–15, 20 (infra) | Full multi-OS tutorial (`docs/TUTORIAL_INSTALL.md`), `.github/workflows` activated, portable scripts hardened, `quantumforge` as the single launch word for local + MobaXterm | Users can install/update/uninstall safely and start the GUI remotely | Clean-VM CI execution + code signing secrets |

## Validation performed in this workspace

- shell launch/install/update/uninstall/build scripts pass `bash -n` where exercised;
- new unit tests added for atomic writer, logging redaction, QE profile parsing, run manifests, process-tree helper, project schema;
- source remains UTF-8 oriented for new modules;
- documentation links for the new tutorial and roadmap progress updated.

## Validation blocked outside this workspace

This sandbox has **no JDK/Maven**, so `mvn verify`, GUI launch, jpackage, and clean-VM install tests cannot be executed here. GitHub workflow **templates** are under `packaging/github-workflows/` (the GitHub App used here lacks `workflows` permission to push `.github/workflows/`). An authorized maintainer must copy those templates into `.github/workflows/`, enable Actions, and configure signing secrets before publishing a production release.

## How to start the GUI after install

```text
quantumforge
```

That command is installed on PATH by the portable installer (Linux/macOS/Windows) and is the recommended word to type in MobaXterm after X11 forwarding is enabled.

## Batch 3 (this continuation) — QE reliability foundations

| Change | Roadmap # | What was implemented | Expected impact | Next improvement |
|---|---:|---|---|---|
| Safe autosave export | 8 | `exportQEInputsTo` does not rebind project dir; GUI dirty probe; recovery list/restore | Autosave can no longer hijack the live project path | Recovery picker dialog in the GUI |
| SecretStore | 9 | Memory default + injectable OS backend; Materials API integration | Secrets stay out of `.properties`; OS path ready for native bindings | Real libsecret/Keychain/Credential Manager adapters |
| Units library | 26 | `PhysicalQuantity`/`Unit` with energy/length/pressure/frequency | Unit mistakes fail at conversion time | Adopt in all parsers/plots public APIs |
| Live log tailer | 30 | `LiveFileTailer` + `LogParser` integration | Parsers can consume complete lines only | Drive live charts exclusively from tailed lines |
| QE error KB | 31 | Signature table + failed-job diagnosis in `RunningNode` | Actionable deterministic hints after crashes | Expand corpus from real multi-version logs |
| SCF convergence analyzer | 32 | Iteration energy/accuracy/trend from logs + Si fixture | Detect oscillation/divergence early | Wire chart UI to analyzer report |
| Geometry convergence | 39 | Marker + threshold validator | “Optimized” requires evidence | Surface status badge in result viewer |
| Final geometry preview | 40 | Typed preview; apply still unsupported | Safe inspection without destructive card wipe | Transactional card rewrite with rollback |
| Golden log fixtures | 41 | `tests/fixtures/qe/*.log` | Executable regression corpus without full QE install | Add spin/SOC/bands/DOS engine-generated fixtures |
| First-release guide | 13–15 | `docs/FIRST_RELEASE.md` | Maintainers have a concrete publish path | Sign/notarize with org secrets |

## Batch 4 — command DAG, restart, recovery GUI, workflow export

| Change | Roadmap # | What was implemented | Expected impact | Next improvement |
|---|---:|---|---|---|
| Recovery GUI | 8 | Viewer menu item + ChoiceDialog restore | Users can recover after crash without CLI | Auto-prompt when snapshots are newer than project |
| QE command DAG | 27 | Typed stages with requires/produces; remaining() resume filter | Clear SCF→post pipeline; exportable | Drive RunningNode exclusively from DAG |
| Restart manager | 33 | `.save` completeness assessment | Avoid unsafe restart_mode guesses | Parse data-file-schema.xml version |
| Workflow export | 104 | Bash/SLURM script writer | Jobs usable without GUI | Site profile modules/account injection |
| Golden corpus | 41 | Fe spin, bands path, DOS fixtures | Broader offline regression | Real engine-generated multi-version set |
| Fortran D exponents | parsers | Scf/Fermi parsers use shared Fortran double parse | Stops silent energy/Fermi drops | Apply to all numeric log parsers |
| Offline harnesses | QA | compile_check + fixture_harness | Catch regressions without Maven | Keep in CI always |

## Validation performed

- `scripts/static_checks.py` — pass
- `scripts/compile_check.py` — pass (string/comment-aware braces)
- `scripts/fixture_harness.py` — pass (7 log fixtures)
- Full `mvn verify` still blocked in this sandbox (cannot download JDK/Maven binaries over TLS to release-assets)

## Batch 5 — runner DAG wiring, dry-run, XCrySDen, workflow GUI

| Change | Roadmap # | What was implemented | Expected impact | Next improvement |
|---|---:|---|---|---|
| Runner ↔ DAG | 27 | Stage IDs, artifact skip, remaining set logging | Resume-friendly multi-stage runs | Exclusive stage list (drop parallel command arrays) |
| Artifact scanner | 27/33 | Detect `.save`, JOB DONE logs, dos/bands files | Skip completed predecessors safely | Hash inputs to invalidate stale artifacts |
| Dry-run preflight | 45 | Binaries, disk, MPI, input, DAG, restart notes | Fail in seconds before expensive jobs | GUI dry-run button separate from Run |
| Workflow export GUI | 104 | Menu action + auto `.quantumforge.workflow.sh` | Jobs usable without GUI | Site modules/account templates |
| XCrySDen | 110 | XSF export + PATH launch | One-click external structure view | Grid/density XSF; remote display tests |
| SCF log feedback | 32 | Analyzer summary after stages | Immediate convergence diagnostics | Live chart binding |

## Validation performed

- `scripts/static_checks.py`
- `scripts/compile_check.py` (includes RunningNode wiring + menu actions)
- `scripts/fixture_harness.py`
- Full `mvn verify` still blocked in this sandbox (JDK download TLS)

## Batch 6 — SSH/host keys, SLURM, site profiles, spglib protocol

| Change | Roadmap # | What was implemented | Expected impact | Next improvement |
|---|---:|---|---|---|
| Known hosts | 91 | SHA-256 fingerprint store; reject unknown/changed keys | Prevents silent MITM on first/changed host | GUI fingerprint accept dialog |
| JSch transport | 91/92 | Connect/exec/SFTP upload-download/mkdir with path checks | Real remote ops when credentials exist | Agent auth + keepalive tuning |
| Remote path guard | 92 | Absolute staging root + no `..` | Safe unique remote job directories | Hash-verified selective sync |
| SLURM adapter | 93 | Typed directives, parse job id, scancel/squeue arrays | Portable script generation | PBS/SGE adapters |
| Site profiles | 94 | YAML-like profile loader | One project → many clusters | Full YAML + admin policy files |
| Job state | 95 | STAGED→… transitions | Restartable job history | SQLite queue persistence |
| SSHJob | 10/91 | Script prepare always; submit only with transport | No false “job submitted” | End-to-end cluster tests |
| spglib service | 71 | Isolated Python sidecar protocol | Real space groups only when spglib present | Locked env + COD fixtures |

## Batch 7 — host-key UI, selective sync, PBS, safe cancel

| Change | Roadmap # | What was implemented | Expected impact | Next improvement |
|---|---:|---|---|---|
| Host-key UI helper | 91 | `HostKeyAcceptance.connectInteractive` | Users can accept first-seen keys safely | Dedicated fingerprint dialog with copy/compare |
| Selective sync | 98 | Manifest of required/optional/large files | Bandwidth-safe result fetch | Checksum cache + resume |
| PBS adapter | 93 | Typed `#PBS` generator + qsub/qdel/qstat | Broader HPC coverage | SGE adapter |
| Safe cancel | 97 | Cancel only by parsed job id + status verify | No kill-by-name disasters | Scheduler reason parsing |
| RunAction fix | 10 | Typed submit + host-key connect | Remote run no longer pretends success | Persist JobRecord queue |

## Batch 8 — SGE, job queue store, sync checksum cache

| Change | Roadmap # | What was implemented | Expected impact | Next improvement |
|---|---:|---|---|---|
| SGE adapter | 93 | Typed `#$` directives + qsub/qdel/qstat | Covers common university GE clusters | Site-specific PE names |
| Job queue store | 105 | Atomic JSONL persistence of JobRecords | GUI can reconstruct active remote jobs after restart | SQLite WAL migration |
| Sync checksum cache | 98 | Local SHA-256 skip for unchanged results | Faster re-sync / less bandwidth | Remote hash probe when available |
| Symmetry honesty | 71–72 | Conversion still refuses silent identity transforms | No fake primitive/conventional cells | Protocol v2 with standardized cell payload |

## Batch 9 — OS keyring CLI, QE XML parser, spglib v2, remote monitor

| Change | Roadmap # | What was implemented | Expected impact | Next improvement |
|---|---:|---|---|---|
| Process keyring backend | 9 | secret-tool / security CLI integration | Secrets can leave process memory on equipped hosts | Windows Credential Manager helper |
| QE XML-first parser | 42 | data-file-schema.xml energy/Fermi/convergence | Stable structured values when XML exists | Broader schema version matrix + forces/stress |
| spglib protocol v2 | 71–73 | standardize + seekpath ops | Real primitive/conventional/k-path when packages installed | Locked env + COD fixtures |
| Remote job monitor | 96 | backoff polling + status mapping | Reconstructable remote job status without request storms | GUI panel + offline reconnect |

## Recommended next batch

1. Run `mvn clean verify` on a JDK 17 host and fix any findings.
2. Native OS keyring backends for `SecretStore` (#9).
3. Strict-host-key SSH/SFTP + one SLURM adapter (#91–99).
4. spglib/seekpath isolated service (#71–73).
5. Real engine-generated multi-version golden outputs.
