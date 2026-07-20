/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.remote;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import quantumforge.operation.OperationResult;

/**
 * Roadmap #103 (draft slice): a typed Apptainer/Singularity container profile
 * draft. The reproducibility rule is STRUCTURAL, matching #103's acceptance:
 * an image reference must pin its sha256 digest - a floating tag is a moving
 * target and a "reproducible software stack" built on one is marketing. The
 * owned grammar and honesty rules:
 *
 * <ul>
 *   <li>runtime is TYPED: apptainer or singularity (CONTAINER_RUNTIME);</li>
 *   <li>image reference: {@code <name>[//<path>...][:<tag>]@sha256:<64 hex>}
 *       - the digest is REQUIRED (CONTAINER_IMAGE/CONTAINER_DIGEST); a
 *       tag-only reference refuses with the reason named, and a digest with
 *       the wrong length/alphabet refuses (no truncation acceptance);</li>
 *   <li>bind paths: csv of absolute POSIX paths with the owned literal
 *       grammar (no '..', no whitespace/quotes/expansion/separators)
 *       (CONTAINER_BIND) - a bind is an attack-surface decision, so it is
 *       literal and deliberate;</li>
 *   <li>MPI compatibility is DECLARED, never verified by this build: the
 *       analyst must type exactly 'yes' (host-MPI compatible) or 'no'
 *       (container-internal MPI); any other answer REFUSES (CONTAINER_MPI) -
 *       silence on hybrid-MPI compatibility is how broken multi-node jobs
 *       happen, so the profile refuses to be neutral;</li>
 *   <li>nothing launches from this build - the render is a reviewed profile
 *       block with an explicit not-launched footer.</li>
 * </ul>
 */
public final class ContainerProfileSpec {

    private ContainerProfileSpec() {
    }

    /** One validated profile. */
    public static final class ContainerProfile {
        private final String runtime;
        private final String imageName;    // name[:tag] part, digest stripped
        private final String digest;       // sha256:<64 hex> full form
        private final List<String> binds;
        private final boolean hostMpiCompatible;

        ContainerProfile(String runtime, String imageName, String digest,
                List<String> binds, boolean hostMpiCompatible) {
            this.runtime = runtime;
            this.imageName = imageName;
            this.digest = digest;
            this.binds = binds;
            this.hostMpiCompatible = hostMpiCompatible;
        }

        public String getRuntime() { return this.runtime; }
        public String getImageName() { return this.imageName; }
        public String getDigest() { return this.digest; }
        public List<String> getBinds() { return this.binds; }
        public boolean isHostMpiCompatible() { return this.hostMpiCompatible; }

        /** The reviewed profile block. */
        public String render() {
            StringBuilder text = new StringBuilder();
            text.append("# qf-container-profile v1 (QuantumForge, Roadmap #103 draft "
                    + "slice)\n");
            text.append("# REVIEWED, not launched - container execution is the #103 "
                    + "runtime depth.\n");
            text.append("runtime = ").append(this.runtime).append('\n');
            text.append("image   = ").append(this.imageName).append('@')
                    .append(this.digest).append('\n');
            text.append("# digest is REQUIRED: a floating tag is a moving target, and a\n");
            text.append("# reproducible stack cannot rest on a moving target.\n");
            if (this.binds.isEmpty()) {
                text.append("binds = (none declared - the default namespace holds)\n");
            } else {
                text.append("binds = ").append(String.join(", ", this.binds)).append('\n');
            }
            text.append("mpi_compatibility = ")
                    .append(this.hostMpiCompatible
                            ? "host-MPI COMPATIBLE (declared by analyst - NOT verified "
                                    + "by this build; a wrong declaration breaks "
                                    + "multi-node jobs)"
                            : "container-internal MPI (host MPI ABI stays out of the "
                                    + "picture)")
                    .append('\n');
            text.append("launched = NO - profile slice only\n");
            return text.toString();
        }
    }

    /** Validates one profile. Codes: CONTAINER_RUNTIME/IMAGE/DIGEST/BIND/MPI. */
    public static OperationResult<ContainerProfile> validate(String runtimeText,
            String imageRef, String bindsCsv, String mpiAnswer) {
        String runtime = runtimeText == null ? "" : runtimeText.trim()
                .toLowerCase(Locale.ROOT);
        if (!(runtime.equals("apptainer") || runtime.equals("singularity"))) {
            return OperationResult.failed("CONTAINER_RUNTIME",
                    "runtime is TYPED: apptainer | singularity (got '" + runtime
                            + "') - other engines are unsupported rather than renamed.",
                    null);
        }
        String ref = imageRef == null ? "" : imageRef.trim();
        int at = ref.indexOf('@');
        if (at < 0) {
            return OperationResult.failed("CONTAINER_DIGEST",
                    "the image reference carries NO digest: '" + ref
                            + "' pins a floating tag, which is a moving target - a "
                            + "reproducible stack cannot rest on it. Use "
                            + "name:tag@sha256:<64 hex>.",
                    null);
        }
        String name = ref.substring(0, at);
        String digest = ref.substring(at + 1);
        if (!name.matches("[A-Za-z0-9][A-Za-z0-9._/-]{0,127}(:[A-Za-z0-9._-]{1,64})?")) {
            return OperationResult.failed("CONTAINER_IMAGE",
                    "image name:tag '" + name + "' violates the owned reference grammar "
                            + "(registry-path letters/digits/._/- plus an optional "
                            + ":tag).",
                    null);
        }
        if (!digest.matches("sha256:[0-9a-f]{64}")) {
            return OperationResult.failed("CONTAINER_DIGEST",
                    "digest '" + digest + "' must be sha256:<64 lowercase hex> - wrong "
                            + "length or alphabet refuses; truncated digests are not "
                            + "accepted as 'close enough'.",
                    null);
        }
        List<String> binds = new ArrayList<>();
        String csv = bindsCsv == null ? "" : bindsCsv.trim();
        if (!csv.isEmpty()) {
            for (String token : csv.split(",", -1)) {
                String bind = token.trim();
                if (!bind.startsWith("/") || bind.contains("..") || bind.contains("//")
                        || bind.contains("\\") || bind.contains("\"")
                        || bind.contains("'") || bind.contains("$") || bind.contains("`")
                        || bind.contains(";") || bind.contains("|") || bind.isBlank()
                        || bind.chars().anyMatch(Character::isWhitespace)) {
                    return OperationResult.failed("CONTAINER_BIND",
                            "bind '" + bind + "' violates the owned literal grammar "
                                    + "(absolute POSIX; no '..', whitespace, quotes, "
                                    + "expansion or separators) - a bind is an "
                                    + "attack-surface decision, so it is literal and "
                                    + "deliberate.",
                            null);
                }
                binds.add(bind);
            }
        }
        String answer = mpiAnswer == null ? "" : mpiAnswer.trim().toLowerCase(Locale.ROOT);
        boolean compatible;
        if (answer.equals("yes")) {
            compatible = true;
        } else if (answer.equals("no")) {
            compatible = false;
        } else {
            return OperationResult.failed("CONTAINER_MPI",
                    "MPI compatibility must be DECLARED: type exactly 'yes' (host-MPI "
                            + "compatible) or 'no' (container-internal MPI). Refusing "
                            + "to be neutral: silence on hybrid-MPI compatibility is how "
                            + "broken multi-node jobs happen. (got '" + mpiAnswer + "')",
                    null);
        }
        return OperationResult.success("CONTAINER_OK", "Container profile validated.",
                new ContainerProfile(runtime, name, digest, List.copyOf(binds),
                        compatible));
    }
}
