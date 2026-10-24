"""Summarises a desktop --log file: per app, how often the position moves and how honest
LastUpdatedTime is. Usage: python desktop/analyze.py desktop/logs/spotify.jsonl"""
import json, statistics, sys
from collections import defaultdict


def pct(values, p):
    values = sorted(values)
    return values[min(len(values) - 1, int(len(values) * p))] if values else None


def summarise(path):
    by_app = defaultdict(list)
    for raw in open(path, encoding="utf-8"):
        line = json.loads(raw)["line"]
        if "status" in line:
            by_app[line["app"]].append(line)
    for app, rows in by_app.items():
        events = defaultdict(int)
        for r in rows:
            events[r["event"]] += 1
        # A position update: the position or its stamp changed while playing.
        updates, prev = [], None
        for r in rows:
            key = (r["positionMs"], r["lastUpdatedMs"])
            if r["status"] == "Playing" and prev and key != prev[0] and prev[1] == "Playing":
                updates.append(r)
            prev = (key, r["status"])
        gaps = [b["t"] - a["t"] for a, b in zip(updates, updates[1:])]
        ages = [r["t"] - r["lastUpdatedMs"] for r in updates]
        # Where each update puts the song now, by its stamp and ignoring it, against where the
        # update before it said the song would be by now. Seeks and track changes are left out.
        honest, naive = [], []
        for a, b in zip(updates, updates[1:]):
            rate = b["rate"] or 1.0
            if a["title"] != b["title"]:
                continue
            want = a["positionMs"] + (b["t"] - a["lastUpdatedMs"]) * rate
            with_stamp = b["positionMs"] + (b["t"] - b["lastUpdatedMs"]) * rate
            if abs(with_stamp - want) > 1500:
                continue
            honest.append(with_stamp - want)
            naive.append(b["positionMs"] - (a["positionMs"] + (b["t"] - a["t"]) * rate))
        print(f"== {app}  ({rows[-1].get('title')!r})")
        print(f"   lines {len(rows)}: " + ", ".join(f"{k} {v}" for k, v in sorted(events.items())))
        print(f"   rate field: {sorted({str(r['rate']) for r in rows})}")
        if gaps:
            print(f"   position updates while playing: {len(updates)}, gap median {statistics.median(gaps):.0f} ms, p10 {pct(gaps, .1)}, p90 {pct(gaps, .9)}")
            print(f"   stamp age when seen: median {statistics.median(ages):.0f} ms, p90 {pct(ages, .9)}, max {max(ages)}")
        if honest:
            ab = [abs(x) for x in honest]
            print(f"   error with stamp:    median {statistics.median(ab):.0f} ms, p90 {pct(ab, .9):.0f}, max {max(ab):.0f}  (signed median {statistics.median(honest):+.0f})")
            ab = [abs(x) for x in naive]
            print(f"   error without stamp: median {statistics.median(ab):.0f} ms, p90 {pct(ab, .9):.0f}, max {max(ab):.0f}")
        # The reports around each state change, to see what pause, resume and seek look like.
        print("   state changes:")
        prev = None
        for r in rows:
            sig = (r["status"], r["title"])
            jump = prev and r["status"] == "Playing" and prev["status"] == "Playing" and abs(
                (r["positionMs"] + (r["t"] - r["lastUpdatedMs"])) - (prev["positionMs"] + (r["t"] - prev["lastUpdatedMs"]))) > 1500
            if prev is None or sig != (prev["status"], prev["title"]) or jump:
                print(f"     t+{(r['t'] - rows[0]['t']) / 1000:7.2f}s {r['event']:9} {r['status']:8} pos {r['positionMs']:7} age {r['t'] - r['lastUpdatedMs']:6} {'SEEK' if jump else ''}")
            prev = r


for p in sys.argv[1:]:
    summarise(p)
