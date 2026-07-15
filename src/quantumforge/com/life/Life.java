/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.com.life;

import java.util.ArrayList;
import java.util.List;

public class Life {

    private static Life instance = null;

    public static Life getInstance() {
        if (instance == null) {
            instance = new Life();
        }

        return instance;
    }

    private boolean alive;

    private List<Dead> onDeadList;

    private Life() {
        this.alive = true;
        this.onDeadList = null;
    }

    public synchronized boolean isAlive() {
        return this.alive;
    }

    public synchronized void toBeDead() {
        if (!this.alive) {
            return;
        }

        this.alive = false;

        if (this.onDeadList != null) {
            for (Dead onDead : onDeadList) {
                if (onDead != null) {
                    onDead.onDead();
                }
            }
        }
    }

    public synchronized void addOnDead(Dead onDead) {
        if (!this.alive) {
            return;
        }

        if (onDead == null) {
            return;
        }

        if (this.onDeadList == null) {
            this.onDeadList = new ArrayList<Dead>();
        }

        this.onDeadList.add(onDead);
    }

    public synchronized void removeOnDead(Dead onDead) {
        if (!this.alive) {
            return;
        }

        if (onDead == null) {
            return;
        }

        if (this.onDeadList == null) {
            return;
        }

        int index = this.onDeadList.indexOf(onDead);
        if (index > -1) {
            this.onDeadList.remove(index);
        }
    }
}
