Install and launch
==================

Use the platform-native package or the unprivileged portable installer described
in `docs/INSTALLATION.md <../../INSTALLATION.md>`_. The portable installation
creates the same command on every supported platform:

.. code-block:: console

   quantumforge --version
   quantumforge --doctor
   quantumforge --capabilities
   quantumforge

Inside the GUI, open **Menu > Path** and select the directory that contains
``pw.x``, ``ph.x``, ``dos.x`` and the other Quantum ESPRESSO executables. Select
the MPI binary directory separately when required.

Portable updates and removal are:

.. code-block:: console

   quantumforge --update
   quantumforge --uninstall

User projects and settings under ``~/.quantumforge`` are preserved by default.
