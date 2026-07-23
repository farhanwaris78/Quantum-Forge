/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import quantumforge.operation.OperationResult;
import quantumforge.remote.ContainerProfileSpec.ContainerProfile;

class ContainerProfileSpecTest {

    private static final String DIGEST = "sha256:"
            + "1a2b3c4d5e6f708192a3b4c5d6e7f8091a2b3c4d5e6f708192a3b4c5d6e7f801";

    @Test
    void pinnedDigestProfileRendersWithDeclaration() {
        OperationResult<ContainerProfile> result = ContainerProfileSpec.validate(
                "APPTAINER", "qe/qe:7.3@" + DIGEST, "/scratch/farhan,/opt/share",
                "yes");
        assertTrue(result.isSuccess(), result.toString());
        ContainerProfile profile = result.getValue().orElseThrow();
        assertEquals("apptainer", profile.getRuntime(), "typed enum normalizes");
        assertEquals("qe/qe:7.3", profile.getImageName());
        assertEquals(DIGEST, profile.getDigest());
        assertTrue(profile.isHostMpiCompatible());
        String block = profile.render();
        assertTrue(block.contains("runtime = apptainer\n"), block);
        assertTrue(block.contains("image   = qe/qe:7.3@" + DIGEST + "\n"), block);
        assertTrue(block.contains("binds = /scratch/farhan, /opt/share\n"), block);
        assertTrue(block.contains("host-MPI COMPATIBLE (declared by analyst - NOT "
                + "verified"), block + " | " + "the declaration is labeled analyst-owned, never claimed verified");
        assertTrue(block.contains("launched = NO"), block);
    }

    @Test
    void floatingTagsAndTruncatedDigestsRefuse() {
        OperationResult<ContainerProfile> floating = ContainerProfileSpec.validate(
                "apptainer", "qe/qe:7.3", "", "yes");
        assertFalse(floating.isSuccess());
        assertEquals("CONTAINER_DIGEST", floating.getCode(),
                "a floating tag is a moving target - refused with the reason named");
        OperationResult<ContainerProfile> truncated = ContainerProfileSpec.validate(
                "apptainer", "qe/qe:7.3@sha256:1a2b3c", "", "yes");
        assertFalse(truncated.isSuccess());
        assertEquals("CONTAINER_DIGEST", truncated.getCode(),
                "truncated digests are not accepted as 'close enough'");
        OperationResult<ContainerProfile> badAlgo = ContainerProfileSpec.validate(
                "apptainer", "qe/qe:7.3@sha512:" + "ab".repeat(64), "", "yes");
        assertEquals("CONTAINER_DIGEST", badAlgo.getCode(),
                "only sha256 digests are typed for this profile");
    }

    @Test
    void runtimeBindsAndMpiDeclarationRefusals() {
        assertEquals("CONTAINER_RUNTIME", ContainerProfileSpec.validate(
                "docker", "qe/qe@" + DIGEST, "", "yes").getCode(),
                "other engines are unsupported rather than renamed");
        assertEquals("CONTAINER_BIND", ContainerProfileSpec.validate(
                "apptainer", "qe/qe@" + DIGEST, "scratch/farhan", "yes").getCode(),
                "relative binds refuse");
        assertEquals("CONTAINER_BIND", ContainerProfileSpec.validate(
                "apptainer", "qe/qe@" + DIGEST, "/scratch/../etc", "yes").getCode());
        assertEquals("CONTAINER_BIND", ContainerProfileSpec.validate(
                "apptainer", "qe/qe@" + DIGEST, "/scratch/$USER", "no").getCode());
        OperationResult<ContainerProfile> silent = ContainerProfileSpec.validate(
                "apptainer", "qe/qe@" + DIGEST, "", "");
        assertFalse(silent.isSuccess());
        assertEquals("CONTAINER_MPI", silent.getCode(),
                "the profile refuses to be neutral on MPI compatibility");
        assertEquals("CONTAINER_MPI", ContainerProfileSpec.validate(
                "apptainer", "qe/qe@" + DIGEST, "", "maybe").getCode());
        OperationResult<ContainerProfile> internal = ContainerProfileSpec.validate(
                "apptainer", "qe/qe@" + DIGEST, "", "no");
        assertTrue(internal.isSuccess());
        assertFalse(internal.getValue().orElseThrow().isHostMpiCompatible());
        assertTrue(internal.getValue().orElseThrow().render()
                .contains("container-internal MPI"), "the 'no' branch renders its own line");
    }

    @Test
    void execPreviewRendersTheSharedShapeWithEditPoints() {
        ContainerProfile host = ContainerProfileSpec.validate("apptainer",
                "library/qe:7.3@sha256:0000000000000000000000000000000000000000000000000000000000000000", "/scratch,/opt/pseudo", "yes").getValue().orElseThrow();
        OperationResult<String> preview = host.execPreview(
                java.util.List.of("pw.x", "-i", "pw.in"));
        assertTrue(preview.isSuccess(), preview.toString());
        String text = preview.getValue().orElseThrow();
        assertTrue(text.contains("REVIEW line only - not launched"), text);
        assertTrue(text.contains("REQUIRED-EDIT: prefix the image reference with YOUR"
                + " pull source"), text + " | " + "the digest-pinned name never silently gains a registry scheme");
        assertTrue(text.contains("apptainer exec --bind /scratch,/opt/pseudo "
                + "library/qe:7.3@sha256:0000000000000000000000000000000000000000000000000000000000000000 pw.x -i pw.in"), text);
        assertTrue(text.contains("the MPI runner stays OUTSIDE"), text);
        assertTrue(text.contains("launched = NO"), text);

        ContainerProfile internal = ContainerProfileSpec.validate("singularity",
                "library/qe:7.3@sha256:0000000000000000000000000000000000000000000000000000000000000000", "", "no").getValue().orElseThrow();
        String inner = internal.execPreview(java.util.List.of("pw.x"))
                .getValue().orElseThrow();
        assertTrue(inner.contains("lives INSIDE"), inner);
        assertTrue(inner.contains("singularity exec library/qe:7.3@sha256:0000000000000000000000000000000000000000000000000000000000000000"
                + " <mpirun/srun + counts inside image> pw.x"), inner);
        assertTrue(inner.contains("no --bind flag rendered"), inner + " | " + "an empty bind list omits the flag and says why");
    }

    @Test
    void execPreviewRefusesCeremonialAndInjectionShapedTokens() {
        ContainerProfile profile = ContainerProfileSpec.validate("apptainer",
                "library/qe:7.3@sha256:0000000000000000000000000000000000000000000000000000000000000000", "", "yes").getValue().orElseThrow();
        assertEquals("CONTAINER_EXEC", profile.execPreview(java.util.List.of())
                .getCode(), "a container that runs nothing is ceremonial");
        assertEquals("CONTAINER_EXEC", profile.execPreview(null).getCode());
        assertEquals("CONTAINER_EXEC", profile.execPreview(
                java.util.List.of("pw.x;i")).getCode(),
                "a separator turns the review line into a micro-script - refused");
        assertEquals("CONTAINER_EXEC", profile.execPreview(
                java.util.List.of("two words")).getCode(),
                "whitespace tokens would need quoting - a one-line preview refuses");
        assertEquals("CONTAINER_EXEC", profile.execPreview(
                java.util.List.of("$(evil)")).getCode());
    }
}
