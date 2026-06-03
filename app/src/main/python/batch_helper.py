"""Utilidades de cola y estimaciones consumidas desde Kotlin."""


def queue_status(jobs: list) -> dict:
    """jobs: lista de dicts con keys 'status', 'size_bytes'.

    Retorna conteos por estado y tamaño total pendiente.
    """
    counts = {"queued": 0, "running": 0, "success": 0, "failed": 0, "cancelled": 0}
    pending_bytes = 0
    for j in jobs or []:
        s = (j.get("status") or "queued").lower()
        if s in counts:
            counts[s] += 1
        if s in ("queued", "running"):
            try:
                pending_bytes += int(j.get("size_bytes") or 0)
            except (TypeError, ValueError):
                pass
    return {
        "counts": counts,
        "pending_bytes": pending_bytes,
        "total": sum(counts.values()),
    }


def estimate_remaining_time(speed_mbs: float, remaining_bytes: int) -> int:
    """Retorna segundos estimados, o -1 si no se puede estimar."""
    try:
        if speed_mbs is None or speed_mbs <= 0:
            return -1
        if remaining_bytes <= 0:
            return 0
        remaining_mb = float(remaining_bytes) / (1024.0 * 1024.0)
        return int(remaining_mb / float(speed_mbs))
    except Exception:
        return -1
