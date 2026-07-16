/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.run;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * One typed stage in a Quantum ESPRESSO command DAG.
 */
public final class QECommandStage {

    public enum Kind {
        PW_SCF,
        PW_NSCF,
        PW_BANDS,
        PW_RELAX,
        PW_MD,
        DOS,
        PROJWFC,
        BANDS_X,
        UNKNOWN
    }

    private final String id;
    private final Kind kind;
    private final String label;
    private final List<String> requires;
    private final List<String> produces;
    private final String[] command;
    private final boolean optional;

    public QECommandStage(String id, Kind kind, String label,
                          List<String> requires, List<String> produces,
                          String[] command, boolean optional) {
        this.id = Objects.requireNonNull(id, "id");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.label = Objects.requireNonNull(label, "label");
        this.requires = Collections.unmodifiableList(new ArrayList<>(
                requires == null ? List.of() : requires));
        this.produces = Collections.unmodifiableList(new ArrayList<>(
                produces == null ? List.of() : produces));
        this.command = command == null ? new String[0] : command.clone();
        this.optional = optional;
    }

    public String getId() { return this.id; }
    public Kind getKind() { return this.kind; }
    public String getLabel() { return this.label; }
    public List<String> getRequires() { return this.requires; }
    public List<String> getProduces() { return this.produces; }
    public String[] getCommand() { return this.command.clone(); }
    public boolean isOptional() { return this.optional; }

    @Override
    public String toString() {
        return this.id + ":" + this.kind + " [" + String.join(" ", this.command) + "]";
    }
}
