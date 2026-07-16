#!/usr/bin/env python3
"""QuantumForge spglib JSON-line sidecar (protocol v1).

Reads one JSON object from stdin and writes one JSON object to stdout.
Never executes arbitrary code. Requires: numpy, spglib.
"""
from __future__ import annotations

import json
import sys


PROTOCOL = "1"


def main() -> int:
    try:
        raw = sys.stdin.readline()
        if not raw:
            print(json.dumps({"error": "empty request"}))
            return 2
        req = json.loads(raw)
        if str(req.get("protocol", "")) != PROTOCOL:
            print(json.dumps({"error": f"unsupported protocol {req.get('protocol')}"}))
            return 2
        if req.get("op") != "get_dataset":
            print(json.dumps({"error": f"unsupported op {req.get('op')}"}))
            return 2
        try:
            import numpy as np
            import spglib
        except Exception as exc:  # pragma: no cover - environment dependent
            print(json.dumps({"error": f"spglib/numpy unavailable: {exc}"}))
            return 3

        lattice = np.array(req["lattice"], dtype=float)
        positions_cart = np.array(req["positions"], dtype=float)
        numbers = np.array(req["numbers"], dtype=int)
        tol = float(req.get("tolerance", 1.0e-5))

        # Convert Cartesian positions to fractional.
        inv = np.linalg.inv(lattice.T) if lattice.shape == (3, 3) else np.linalg.inv(lattice)
        # Our Java lattice[i] is vector i as row; spglib expects cell = (lattice, positions, numbers)
        # with lattice as row-vectors and fractional positions.
        frac = positions_cart @ np.linalg.inv(lattice)
        cell = (lattice, frac, numbers)
        dataset = spglib.get_symmetry_dataset(cell, symprec=tol)
        if dataset is None:
            print(json.dumps({"error": "spglib returned no dataset"}))
            return 4
        # spglib >=2 returns object with attributes; older returns dict.
        def get(name, default=None):
            if isinstance(dataset, dict):
                return dataset.get(name, default)
            return getattr(dataset, name, default)

        out = {
            "protocol": PROTOCOL,
            "number": int(get("number", 0)),
            "international": str(get("international", get("international_symbol", ""))),
            "hall": str(get("hall", get("hall_symbol", ""))),
            "spglib_version": getattr(spglib, "__version__", "unknown"),
            "tolerance": tol,
        }
        print(json.dumps(out))
        return 0
    except Exception as exc:  # pragma: no cover
        print(json.dumps({"error": str(exc)}))
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
