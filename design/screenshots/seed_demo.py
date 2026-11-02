"""Ajoute un jeu de seances de demonstration a une base Topo, pour les captures du README.

Les ecrans Mes seances et Progression ont besoin de plusieurs semaines de grimpe pour
montrer quelque chose ; la seance et le beta captures restent, eux, de vraies seances.
Les seances inventees n'ont pas de photo : elles ne servent qu'a la liste et aux chiffres.

Deterministe (graine fixe) et idempotent : les lignes de demonstration portent le prefixe
`demo-` et sont remplacees a chaque passage, les vraies donnees ne sont jamais touchees.

Usage, sur un emulateur avec un build debug (run-as), appli arretee :

    adb exec-out run-as com.gabrielifrim.topo cat databases/topo.db > topo.db
    python design/screenshots/seed_demo.py topo.db
    adb push topo.db /data/local/tmp/topo.db
    adb shell "run-as com.gabrielifrim.topo sh -c 'cp /data/local/tmp/topo.db databases/topo.db; rm -f databases/topo.db-wal databases/topo.db-shm'"
"""
import random
import sqlite3
import sys
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo

PARIS = ZoneInfo("Europe/Paris")

# Salles fictives autour de Bordeaux, a cote des deux vraies (Villenave, Merignac).
PLACES = ["Bègles", "Pessac", "Talence", "Bordeaux Bastide", "Gradignan", "Le Haillan"]

# Cotation typique de chaque couleur du circuit, du Jaune (rang 0) au Noir (rang 6).
GRADES_BY_RANK = [["4", "5A"], ["5B", "5C"], ["6A", "6A+"], ["6B", "6B+"], ["6C", "6C+"], ["7A"], ["7A+"]]

# Etats persistes : ordinaux de BlocStatus.
PROJECT, WORKING, FLASHED, SENT = 0, 1, 2, 3

# Dates des seances inventees : deux par semaine environ, de septembre a debut novembre.
FIRST_DAY = datetime(2026, 9, 1, tzinfo=PARIS)
DAYS = [0, 3, 7, 10, 14, 17, 31, 35, 38, 42, 45, 49, 52, 56, 60]


def outcome(rng, diff):
    """Statut et essais d'un bloc, selon son ecart au niveau du moment (rang - niveau)."""
    send = {-2: 0.97, -1: 0.92, 0: 0.7, 1: 0.35}.get(max(-2, min(diff, 2)), 0.08)
    if rng.random() < send:
        flash = {-2: 0.7, -1: 0.55, 0: 0.3, 1: 0.12}.get(max(-2, min(diff, 2)), 0.05)
        if rng.random() < flash:
            return FLASHED, 1
        return SENT, rng.randint(2, 3 + max(diff, 0) * 2)
    if diff >= 2 and rng.random() < 0.5:
        return PROJECT, rng.randint(0, 1)
    return WORKING, rng.randint(2, 6)


def seed(db_path):
    rng = random.Random(2026)
    db = sqlite3.connect(db_path)
    db.execute("PRAGMA foreign_keys = ON")
    db.execute("DELETE FROM sessions WHERE id LIKE 'demo-%'")  # cascade sur les blocs
    colors = [row[0] for row in db.execute("SELECT id FROM circuit_colors ORDER BY rank")]
    if len(colors) < len(GRADES_BY_RANK):
        sys.exit("Circuit incomplet : il faut les sept couleurs par defaut.")

    for index, day in enumerate(DAYS):
        progress = index / (len(DAYS) - 1)
        level = 1.2 + 2.3 * progress  # du Vert au Violet en deux mois
        start = FIRST_DAY + timedelta(days=day, hours=18, minutes=rng.randint(0, 50))
        session_id = f"demo-session-{index:02d}"
        db.execute(
            "INSERT INTO sessions (id, date, location_name, notes) VALUES (?, ?, ?, NULL)",
            (session_id, int(start.timestamp() * 1000), rng.choice(PLACES)),
        )
        for n in range(rng.randint(4, 8)):
            rank = max(0, min(6, round(level + rng.uniform(-1.6, 1.6))))
            status, attempts = outcome(rng, rank - round(level))
            created = start + timedelta(minutes=12 * n)
            db.execute(
                "INSERT INTO blocs (id, session_id, image_path, image_width, image_height, grade, grade_scale,"
                " color_id, status, attempts, sent_at, feeling, created_at)"
                " VALUES (?, ?, ?, 3000, 4000, ?, 0, ?, ?, ?, ?, NULL, ?)",
                (
                    f"demo-bloc-{index:02d}-{n}",
                    session_id,
                    f"demo-{index:02d}-{n}.jpg",
                    rng.choice(GRADES_BY_RANK[rank]),
                    colors[rank],
                    status,
                    attempts,
                    int(created.timestamp() * 1000) if status in (FLASHED, SENT) else None,
                    int(created.timestamp() * 1000),
                ),
            )
    db.commit()
    total, sent, flashed = db.execute(
        "SELECT COUNT(*), SUM(status IN (2, 3)), SUM(status = 2) FROM blocs"
    ).fetchone()
    print(f"{len(DAYS)} seances de demonstration ; base : {total} blocs, {sent} reussis, {flashed} flashs")


if __name__ == "__main__":
    seed(sys.argv[1])
