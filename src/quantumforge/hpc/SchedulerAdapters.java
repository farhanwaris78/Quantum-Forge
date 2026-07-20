/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.hpc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Typed name -&gt; {@link SchedulerAdapter} registry (Roadmap #97 runtime slice).
 *
 * <p>This registry exists so that NO caller ever holds a second, textual
 * copy of "which schedulers exist" or of their job-id grammar:</p>
 * <ul>
 *   <li>{@link #forName(String)} is the ONLY name resolution path: it is
 *       case-insensitive and trimming, and it NEVER defaults to SLURM - a
 *       blank or unknown name returns {@code Optional.empty()} and the caller
 *       must refuse (a silent default scheduler would route a cancel command
 *       at the wrong cluster);</li>
 *   <li>the adapters remain the single owners of their per-scheduler job-id
 *       grammar via the {@link SchedulerAdapter} contract; callers probe
 *       grammar by asking the adapter for its command tokens and honoring the
 *       {@link IllegalArgumentException} refusal verbatim;</li>
 *   <li>the four supported names cover every scheduler this build can reason
 *       about: slurm, pbs, pjm, sge.</li>
 * </ul>
 */
public final class SchedulerAdapters {

    private static final List<SchedulerAdapter> ALL;

    static {
        List<SchedulerAdapter> adapters = new ArrayList<>();
        adapters.add(new SlurmSchedulerAdapter());
        adapters.add(new PbsSchedulerAdapter());
        adapters.add(new PjmSchedulerAdapter());
        adapters.add(new SgeSchedulerAdapter());
        ALL = Collections.unmodifiableList(adapters);
    }

    private SchedulerAdapters() {
    }

    /** Every registered adapter (slurm, pbs, pjm, sge), in registry order. */
    public static List<SchedulerAdapter> all() {
        return ALL;
    }

    /**
     * Resolve a scheduler name to its adapter. Blank and unknown names return
     * {@code Optional.empty()} - there is deliberately NO default adapter.
     */
    public static Optional<SchedulerAdapter> forName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String key = name.trim().toLowerCase(Locale.ROOT);
        for (SchedulerAdapter adapter : ALL) {
            if (adapter.name().equals(key)) {
                return Optional.of(adapter);
            }
        }
        return Optional.empty();
    }

    /** Comma-separated supported names, for refusal messages. */
    public static String supportedNames() {
        StringBuilder names = new StringBuilder();
        for (SchedulerAdapter adapter : ALL) {
            if (names.length() > 0) {
                names.append(", ");
            }
            names.append(adapter.name());
        }
        return names.toString();
    }
}
