"""
Bridge entre Kotlin (Chaquopy) y la librería nsz.

Todas las funciones públicas retornan dict serializable a través del puente
Chaquopy → JSON-friendly para que el Worker en Kotlin pueda mapearlo a su
sealed class ConversionResult.

Estilo de retorno:
  {"success": bool, ...}        para operaciones que pueden fallar
  {"valid": bool, ...}          para validaciones
"""

import os
import re
import shutil
import sys
import time
import traceback
from pathlib import Path


_REQUIRED_KEYS = ("master_key_00", "aes_kek_generation_source")
_KEY_LINE = re.compile(r"^\s*([a-zA-Z0-9_]+)\s*=\s*([0-9a-fA-F]+)\s*$")


def _switch_dir() -> Path:
    home = Path(os.path.expanduser("~"))
    d = home / ".switch"
    d.mkdir(parents=True, exist_ok=True)
    return d


def validate_keys(keys_path: str) -> dict:
    result = {
        "valid": False,
        "error": "",
        "key_count": 0,
        "has_title_keys": False,
    }
    try:
        if not keys_path or not os.path.exists(keys_path):
            result["error"] = "file_not_found"
            return result

        if os.path.getsize(keys_path) == 0:
            result["error"] = "empty_file"
            return result

        found_keys = set()
        total = 0
        with open(keys_path, "r", encoding="utf-8", errors="ignore") as fh:
            for raw in fh:
                m = _KEY_LINE.match(raw)
                if not m:
                    continue
                name = m.group(1).lower()
                found_keys.add(name)
                total += 1

        missing = [k for k in _REQUIRED_KEYS if k not in found_keys]
        if missing:
            result["error"] = "missing:" + ",".join(missing)
            result["key_count"] = total
            return result

        result["valid"] = True
        result["key_count"] = total
        result["has_title_keys"] = any(k.startswith("titlekey_") for k in found_keys)
        return result
    except Exception as exc:
        result["error"] = f"exception:{exc.__class__.__name__}:{exc}"
        return result


def setup_keys(keys_path: str) -> str:
    try:
        if not os.path.exists(keys_path):
            return "keys_file_not_found"
        target = _switch_dir() / "prod.keys"
        shutil.copyfile(keys_path, str(target))
        return ""
    except Exception as exc:
        return f"setup_failed:{exc.__class__.__name__}:{exc}"


def get_file_info(nsz_path: str) -> dict:
    info = {"size_bytes": 0, "estimated_nsp_size": 0, "name": "", "exists": False}
    try:
        if not os.path.exists(nsz_path):
            return info
        size = os.path.getsize(nsz_path)
        info["size_bytes"] = size
        info["estimated_nsp_size"] = int(size * 1.35)
        info["name"] = os.path.basename(nsz_path)
        info["exists"] = True
    except Exception:
        pass
    return info


def _resolve_output(nsz_path: str, output_dir: str) -> str:
    """Encuentra el .nsp generado en output_dir. nsz suele nombrar como el input."""
    base = os.path.splitext(os.path.basename(nsz_path))[0]
    candidate = os.path.join(output_dir, base + ".nsp")
    if os.path.exists(candidate):
        return candidate
    # Fallback: busca cualquier .nsp recién creado
    try:
        nsps = [f for f in os.listdir(output_dir) if f.lower().endswith(".nsp")]
        if nsps:
            nsps.sort(key=lambda f: os.path.getmtime(os.path.join(output_dir, f)), reverse=True)
            return os.path.join(output_dir, nsps[0])
    except OSError:
        pass
    return ""


def decompress_nsz(nsz_path: str, output_dir: str) -> dict:
    result = {
        "success": False,
        "output_path": "",
        "error": "",
        "duration_seconds": 0.0,
        "stdout": "",
        "stderr": "",
    }

    start = time.perf_counter()

    if not os.path.exists(nsz_path):
        result["error"] = "input_not_found"
        return result

    if os.path.getsize(nsz_path) == 0:
        result["error"] = "input_empty"
        return result

    os.makedirs(output_dir, exist_ok=True)

    # Captura de stdout/stderr para logs en el Worker
    from io import StringIO
    buf_out, buf_err = StringIO(), StringIO()
    old_out, old_err = sys.stdout, sys.stderr
    sys.stdout, sys.stderr = buf_out, buf_err

    try:
        try:
            from nsz import NszDecompressor
        except ImportError as ie:
            result["error"] = f"nsz_import_failed:{ie}"
            return result

        try:
            # API moderna: NszDecompressor.decompress(file, outputDir, ...)
            NszDecompressor.decompress(nsz_path, output_dir)
        except AttributeError:
            # Fallback CLI-style
            from nsz import __main__ as nsz_main
            nsz_main.main(["-D", "-o", output_dir, nsz_path])
        except FileNotFoundError:
            result["error"] = "input_not_found"
            return result
        except PermissionError as pe:
            result["error"] = f"permission_denied:{pe}"
            return result
        except Exception as exc:
            msg = str(exc).lower()
            if "key" in msg or "decrypt" in msg or "master_key" in msg:
                result["error"] = "keys_outdated_or_invalid"
            else:
                result["error"] = f"nsz_failed:{exc.__class__.__name__}:{exc}"
            result["stderr"] = buf_err.getvalue()[-4096:]
            return result

        output_path = _resolve_output(nsz_path, output_dir)
        if not output_path:
            result["error"] = "output_not_found"
            return result

        result["success"] = True
        result["output_path"] = output_path
        return result
    except Exception:
        result["error"] = "unhandled:" + traceback.format_exc().splitlines()[-1][:200]
        return result
    finally:
        sys.stdout, sys.stderr = old_out, old_err
        result["stdout"] = buf_out.getvalue()[-4096:]
        if not result["stderr"]:
            result["stderr"] = buf_err.getvalue()[-4096:]
        result["duration_seconds"] = round(time.perf_counter() - start, 3)


def nsz_version() -> str:
    try:
        import nsz
        return getattr(nsz, "__version__", "unknown")
    except Exception:
        return "unavailable"
