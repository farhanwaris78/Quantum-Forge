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

## Recommended next batch

1. Run `mvn clean verify` on a JDK 17 machine and fix any compiler findings.
2. Drive `RunningNode` stage loop from `QECommandDag.remaining(...)`.
3. Native OS keyring backends behind `SecretStore`.
4. Strict-host-key SSH/SFTP + one SLURM adapter (91–99).
5. spglib/seekpath isolated service (71–73).
6. Real QE-generated golden outputs for Si/Fe/molecule.
