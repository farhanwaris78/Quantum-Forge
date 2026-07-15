/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.atoms.design;

import java.lang.ref.WeakReference;

public class AtomDesignAdaptor {

    private boolean alive;

    private WeakReference<AtomDesignListener> weakListener;

    public AtomDesignAdaptor(AtomDesignListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener is null.");
        }

        this.alive = true;

        this.weakListener = new WeakReference<AtomDesignListener>(listener);
    }

    protected boolean isAlive() {
        return this.alive && (this.weakListener.get() != null);
    }

    public void detach() {
        this.alive = false;
    }

    protected AtomDesignListener getListener() {
        return this.weakListener.get();
    }

}
