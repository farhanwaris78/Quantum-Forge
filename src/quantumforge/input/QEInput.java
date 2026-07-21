/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.input;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import quantumforge.atoms.model.Atom;
import quantumforge.atoms.model.Cell;
import quantumforge.com.math.Matrix3D;
import quantumforge.input.card.QEAtomicPositions;
import quantumforge.input.card.QEAtomicSpecies;
import quantumforge.input.card.QECard;
import quantumforge.input.card.QECellParameters;
import quantumforge.input.card.QEKPoints;
import quantumforge.input.correcter.QEInputCorrecter;
import quantumforge.input.namelist.QENamelist;
import quantumforge.input.namelist.QEValue;

public abstract class QEInput {

    public static final String NAMELIST_CONTROL = "CONTROL";
    public static final String NAMELIST_SYSTEM = "SYSTEM";
    public static final String NAMELIST_ELECTRONS = "ELECTRONS";
    public static final String NAMELIST_IONS = "IONS";
    public static final String NAMELIST_CELL = "CELL";
    public static final String NAMELIST_DOS = "DOS";
    public static final String NAMELIST_PROJWFC = "PROJWFC";
    public static final String NAMELIST_BANDS = "BANDS";

    /**
     * pw.x EXTENSION namelist keys mined from the QE 7.2-7.6 grammar:
     * &FCP (fixed-cell-potential constant-mu), &RISM (ESM-RISM electrolyte),
     * &WANNIER, &WANNIER_AC and &PRESS_AI (vcd md.x companion). They are
     * deliberately NOT part of {@link #listNamelistKeys()}: the render/write
     * path keeps its eight-slot shape, and a subclass registers one of these
     * only when it OWNS that deck. The mined-schema audit adapter reaches them
     * either from a registered model namelist or from the rendered deck text.
     */
    public static final String NAMELIST_FCP = "FCP";
    public static final String NAMELIST_RISM = "RISM";
    public static final String NAMELIST_WANNIER = "WANNIER";
    public static final String NAMELIST_WANNIER_AC = "WANNIER_AC";
    public static final String NAMELIST_PRESS_AI = "PRESS_AI";

    /**
     * The five extension namelist keys of {@link #NAMELIST_FCP} and siblings,
     * in mined-schema order. Roadmap R1 accessor.
     */
    public static String[] listExtraNamelistKeys() {
        return new String[] {
                NAMELIST_FCP,
                NAMELIST_RISM,
                NAMELIST_WANNIER,
                NAMELIST_WANNIER_AC,
                NAMELIST_PRESS_AI
        };
    }

    /**
     * The eight primary keys followed by the five extension keys (13 total).
     * Read-side accessor only - nothing in this class starts WRITING these
     * decks implicitly because of this list.
     */
    public static String[] listAllNamelistKeys() {
        String[] primary = listNamelistKeys();
        String[] extra = listExtraNamelistKeys();
        String[] all = new String[primary.length + extra.length];
        System.arraycopy(primary, 0, all, 0, primary.length);
        System.arraycopy(extra, 0, all, primary.length, extra.length);
        return all;
    }

    public static String[] listNamelistKeys() {
        return new String[] {
                NAMELIST_CONTROL,
                NAMELIST_SYSTEM,
                NAMELIST_ELECTRONS,
                NAMELIST_IONS,
                NAMELIST_CELL,
                NAMELIST_DOS,
                NAMELIST_PROJWFC,
                NAMELIST_BANDS
        };
    }

    public static String[] listCardKeys() {
        return new String[] {
                QEKPoints.CARD_NAME,
                QECellParameters.CARD_NAME,
                QEAtomicSpecies.CARD_NAME,
                QEAtomicPositions.CARD_NAME
        };
    }

    private static final Map<Class<? extends QECard>, String> CARD_KEY_MAP;
    static {
        CARD_KEY_MAP = new HashMap<Class<? extends QECard>, String>();
        CARD_KEY_MAP.put(QEKPoints.class, QEKPoints.CARD_NAME);
        CARD_KEY_MAP.put(QECellParameters.class, QECellParameters.CARD_NAME);
        CARD_KEY_MAP.put(QEAtomicSpecies.class, QEAtomicSpecies.CARD_NAME);
        CARD_KEY_MAP.put(QEAtomicPositions.class, QEAtomicPositions.CARD_NAME);
    }

    protected Map<String, QENamelist> namelists;

    protected Map<String, QECard> cards;

    private QEInputReader reader;

    private QEInputCorrecter inputCorrecter;

    private CellBuilder cellBuilder;

    protected QEInput() {
        try {
            this.createNamelists(null);
            this.createCards(null);
        } catch (IOException e) {
            e.printStackTrace();
        }

        QEInputCorrecter inputCorrecter = this.getInputCorrector();
        if (inputCorrecter != null) {
            inputCorrecter.correctInput();
        }

        this.reader = null;
        this.cellBuilder = null;
    }

    protected QEInput(String fileName) throws IOException {
        this(fileName == null || fileName.isEmpty() ? null : new File(fileName));
    }

    protected QEInput(File file) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("file is null.");
        }

        this.reader = new QEInputReader(file);
        this.createNamelists(this.reader);
        this.createCards(this.reader);

        QEInputCorrecter inputCorrecter = this.getInputCorrector();
        if (inputCorrecter != null) {
            inputCorrecter.correctInput();
        }

        this.cellBuilder = null;
    }

    private void createNamelists(QEInputReader reader) throws IOException {
        this.namelists = new HashMap<String, QENamelist>();
        this.setupNamelists(reader);
    }

    protected abstract void setupNamelists(QEInputReader reader) throws IOException;

    protected void setupNamelist(String name, QEInputReader reader) throws IOException {
        if (name == null) {
            return;
        }

        QENamelist namelist = null;
        if (this.namelists.containsKey(name)) {
            namelist = this.namelists.get(name);
        } else {
            namelist = new QENamelist(name);
            this.namelists.put(name, namelist);
        }

        if (reader != null) {
            reader.readNamelist(namelist);
        }
    }

    private void createCards(QEInputReader reader) throws IOException {
        this.cards = new HashMap<String, QECard>();
        this.setupCards(reader);
    }

    protected abstract void setupCards(QEInputReader reader) throws IOException;

    protected void setupCard(QECard card, QEInputReader reader) throws IOException {
        if (card == null) {
            return;
        }

        QECard card_ = null;
        String name = card.getName();
        if (this.cards.containsKey(name)) {
            card_ = this.cards.get(name);
        } else {
            card_ = card;
            this.cards.put(name, card_);
        }

        if (reader != null) {
            reader.readCard(card_);
        }
    }

    public void updateInputData(QEInputReader reader) throws IOException {
        String[] keyNamelists = listNamelistKeys();
        for (String keyNamelist : keyNamelists) {
            QENamelist namelist = this.namelists.get(keyNamelist);
            if (namelist != null) {
                namelist.clear();
            }
        }

        String[] keyCards = listCardKeys();
        for (String keyCard : keyCards) {
            QECard card = this.cards.get(keyCard);
            if (card != null) {
                card.clear();
            }
        }

        this.setupNamelists(reader);
        this.setupCards(reader);

        QEInputCorrecter inputCorrecter = this.getInputCorrector();
        if (inputCorrecter != null) {
            inputCorrecter.correctInput();
        }
    }

    public void updateInputData(String inputData) throws IOException {
        QEInputReader reader = new QEInputReader();
        reader.appendInputData(inputData);
        this.updateInputData(reader);
    }

    public abstract void reload();

    public abstract QEInput copy();

    public QENamelist getNamelist(String key) {
        if (!this.namelists.containsKey(key)) {
            return null;
        }

        return this.namelists.get(key);
    }

    public QECard getCard(String key) {
        if (!this.cards.containsKey(key)) {
            return null;
        }

        return this.cards.get(key);
    }

    @SuppressWarnings("unchecked")
    public <T extends QECard> T getCard(Class<T> cardClass) {
        String key = CARD_KEY_MAP.get(cardClass);

        if (!this.cards.containsKey(key)) {
            return null;
        }

        QECard card = this.cards.get(key);
        if (card != null && card.getClass() == cardClass) {
            return (T) card;
        }

        return null;
    }

    protected QEInputCorrecter getInputCorrector() {
        if (this.inputCorrecter == null) {
            this.inputCorrecter = this.createInputCorrector();
        }

        return this.inputCorrecter;
    }

    protected abstract QEInputCorrecter createInputCorrector();

    public QEInputReader getReader() {
        return this.reader;
    }

    protected CellBuilder getCellBuilder() {
        if (this.cellBuilder == null) {
            this.cellBuilder = new CellBuilder(this);
        }

        return this.cellBuilder;
    }

    public double[][] getLattice() {
        return this.getCellBuilder().buildLattice();
    }

    public double[][] getAngstromMatrix() {
        return this.getCellBuilder().buildAngstromMatrix();
    }

    public double[][] getAngstromInverse() {
        double[][] matrix = this.getCellBuilder().buildAngstromMatrix();
        if (matrix != null) {
            matrix = Matrix3D.inverse(matrix);
        }

        return matrix;
    }

    public Atom getAtom(int i) {
        return this.getCellBuilder().buildAtom(i);
    }

    public List<Atom> getAtoms() {
        return this.getCellBuilder().buildAtoms();
    }

    public static Atom pickOutAtom(Cell cell, int i) {
        return CellBinder.pickOutAtom(cell, i);
    }

    @Override
    public String toString() {
        return this.toString(true);
    }

    public String toString(boolean withAtomicPositions) {
        String str = "";

        String[] keyNamelists = listNamelistKeys();
        for (String keyNamelist : keyNamelists) {
            QENamelist namelist = this.namelists.get(keyNamelist);
            if (namelist != null) {
                str = str + namelist.toString() + System.lineSeparator();
            }
        }

        String[] keyCards = listCardKeys();
        for (String keyCard : keyCards) {
            QECard card = this.cards.get(keyCard);
            if (card != null) {

                boolean toShow = false;
                if (card instanceof QECellParameters) {
                    QENamelist nmlSystem = this.namelists.get(NAMELIST_SYSTEM);
                    if (nmlSystem != null) {
                        QEValue value = nmlSystem.getValue("ibrav");
                        toShow = value != null && value.getIntegerValue() == 0;
                    }

                } else if (card instanceof QEAtomicPositions) {
                    if (withAtomicPositions) {
                        toShow = true;
                    }

                } else {
                    toShow = true;
                }

                if (toShow) {
                    str = str + card.toString() + System.lineSeparator();
                }
            }
        }

        return str;
    }
}
