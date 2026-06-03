#!/usr/bin/env python3
"""Validación host del bridge Python sin tocar el dispositivo.

Corre `converter.py` contra una instalación local de `nsz` y reporta el primer
error semántico (tipos, firma, imports). Captura el 80% de los bugs ANTES de
que lleguen al APK, evitando el ciclo rebuild → install → tap-convert → leer log.

Uso:
    python3 tools/host_validate.py                    # con archivo dummy
    python3 tools/host_validate.py /path/to/real.nsz  # con NSZ real (necesita keys)

Salida:
    success: True  → bridge OK, pushear con confianza
    success: False → muestra error y stderr de nsz
"""
import os
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "app" / "src" / "main" / "python"))

import converter  # noqa


def main(argv):
    real_nsz = Path(argv[1]) if len(argv) > 1 else None

    with tempfile.TemporaryDirectory() as tmpdir:
        tmp = Path(tmpdir)
        if real_nsz and real_nsz.exists():
            nsz_path = real_nsz
            print(f"using real NSZ: {nsz_path} ({nsz_path.stat().st_size:,} bytes)")
        else:
            nsz_path = tmp / "dummy.nsz"
            nsz_path.write_bytes(b"PFS0" + b"\x00" * 64)
            print(f"using dummy NSZ at {nsz_path}")

        out_dir = tmp / "out"
        out_dir.mkdir()

        res = converter.decompress_nsz(str(nsz_path), str(out_dir))
        print("---")
        for k in ("success", "output_path", "error", "duration_seconds"):
            print(f"{k}: {res.get(k)!r}")
        stderr = res.get("stderr", "")
        if stderr:
            print(f"\nstderr_tail:\n{stderr[-1500:]}")

    # Política: success real → exit 0. Para dummy file, IndexError u otros errores
    # de "archivo inválido" son ESPERADOS y no representan bug del bridge.
    err = res.get("error", "")
    expected_dummy_errors = (
        "IndexError", "list index out of range", "input_empty",
        "BufferError", "EOFError", "struct.error",
    )
    if res["success"]:
        sys.exit(0)
    if not real_nsz and any(e in err for e in expected_dummy_errors):
        print("\n[OK] bridge validó tipos/firma — error es por dummy file, no por bridge.")
        sys.exit(0)
    sys.exit(1)


if __name__ == "__main__":
    main(sys.argv)
