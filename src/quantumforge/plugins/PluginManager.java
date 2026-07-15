/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.plugins;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Plugin manager for QuantumForge.
 * 
 * Provides extensibility for integrating external tools:
 * - thermo_pw (thermodynamic properties)
 * - phonopy (phonon band structures)
 * - BoltzTraP2 (transport properties)
 * - seekpath (band path generation)
 * - NeuralMD (GNN force fields)
 * 
 * Plugins follow a simple lifecycle: init -> configure -> execute -> cleanup.
 */
public class PluginManager {

    private static PluginManager instance = null;

    public static PluginManager getInstance() {
        if (instance == null) {
            instance = new PluginManager();
        }
        return instance;
    }

    public static final String PLUGIN_THERMO_PW = "thermo_pw";
    public static final String PLUGIN_PHONOPY = "phonopy";
    public static final String PLUGIN_BOLTZTRAP2 = "boltztrap2";
    public static final String PLUGIN_SEEKPATH = "seekpath";
    public static final String PLUGIN_NEURALMD = "neuralmd";
    public static final String PLUGIN_VASP = "vasp";
    public static final String PLUGIN_CASTEP = "castep";

    private List<PluginInfo> registeredPlugins;

    private PluginManager() {
        this.registeredPlugins = new ArrayList<PluginInfo>();
        this.registerDefaultPlugins();
    }

    private void registerDefaultPlugins() {
        this.registeredPlugins.add(new PluginInfo(
            PLUGIN_THERMO_PW, "2.1.1",
            "Thermodynamic properties (Dal Corso)",
            "https://dalcorso.github.io/thermo_pw/",
            "thermo_pw.x"));

        this.registeredPlugins.add(new PluginInfo(
            PLUGIN_PHONOPY, "2.30+",
            "Phonon band structures and thermal properties",
            "https://phonopy.github.io/phonopy/",
            "phonopy"));

        this.registeredPlugins.add(new PluginInfo(
            PLUGIN_BOLTZTRAP2, "24.1+",
            "Transport properties (conductivity, Seebeck)",
            "https://www.simsa.org/boltztrap2/",
            "boltztrap.x"));

        this.registeredPlugins.add(new PluginInfo(
            PLUGIN_SEEKPATH, "2.1+",
            "Band path generation from symmetry",
            "https://seeck.github.io/seekpath/",
            "seekpath"));

        this.registeredPlugins.add(new PluginInfo(
            PLUGIN_NEURALMD, "1.0+",
            "GNN force field MD (M3GNet, CHGNet, OCP)",
            "https://github.com/materialsvirtuallab/matgl",
            "python3"));

        this.registeredPlugins.add(new PluginInfo(
            PLUGIN_VASP, "6.x",
            "Vienna Ab initio Simulation Package",
            "https://www.vasp.at/",
            "vasp_std"));

        this.registeredPlugins.add(new PluginInfo(
            PLUGIN_CASTEP, "24.x",
            "CASTEP Materials Modelling Software",
            "http://www.castep.org/",
            "castep"));
    }

    public boolean isPluginAvailable(String pluginId) {
        for (PluginInfo info : this.registeredPlugins) {
            if (info.id.equals(pluginId)) {
                return info.isAvailable();
            }
        }
        return false;
    }

    public void checkPluginAvailability(String pluginId) {
        for (PluginInfo info : this.registeredPlugins) {
            if (info.id.equals(pluginId)) {
                info.checkAvailability();
                return;
            }
        }
    }

    public String[] getAvailablePluginIds() {
        String[] ids = new String[this.registeredPlugins.size()];
        for (int i = 0; i < this.registeredPlugins.size(); i++) {
            ids[i] = this.registeredPlugins.get(i).id;
        }
        return ids;
    }

    public PluginInfo getPluginInfo(String pluginId) {
        for (PluginInfo info : this.registeredPlugins) {
            if (info.id.equals(pluginId)) {
                return info;
            }
        }
        return null;
    }

    /**
     * Plugin information container
     */
    public static class PluginInfo {
        public final String id;
        public final String version;
        public final String description;
        public final String website;
        public final String executable;
        public boolean available;
        public String execPath;

        public PluginInfo(String id, String version, String description,
                          String website, String executable) {
            this.id = id;
            this.version = version;
            this.description = description;
            this.website = website;
            this.executable = executable;
            this.available = false;
            this.execPath = null;
        }

        public boolean isAvailable() {
            return this.available;
        }

        public void checkAvailability() {
            if (this.executable == null) {
                this.available = false;
                this.execPath = null;
                return;
            }

            try {
                // Check if executable exists in PATH
                ProcessBuilder pb = new ProcessBuilder("which", this.executable);
                Process process = pb.start();
                int exitCode = process.waitFor();
                this.available = (exitCode == 0);

                if (this.available) {
                    // Read the path
                    java.io.BufferedReader reader =
                        new java.io.BufferedReader(
                            new java.io.InputStreamReader(process.getInputStream()));
                    this.execPath = reader.readLine();
                }

            } catch (Exception e) {
                this.available = false;
                this.execPath = null;
            }
        }
    }
}
