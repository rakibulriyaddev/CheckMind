#!/usr/bin/env python3
"""
Collects decisive games of strong chess.com players through the public API
(https://www.chess.com/news/view/published-data-api) and writes them as PGN
for the book builder. Resumable: rerun the same command to continue.

Usage: python fetch_chesscom.py [output.pgn] [games_per_color]

Selection:
  - standard chess, rated, rapid / blitz / daily
  - decisive: the loser was checkmated or resigned (timeouts are skipped)
  - won within MAX_PLIES half-moves (40 moves)
  - both players rated >= MIN_RATING
  - at most MAX_PER_PLAYER wins per colour from one player, for variety
Players: the chess.com leaderboards first, then all titled GMs.
Standard library only. Requests are sequential with a short pause.
"""
import gzip
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request

UA = "CheckMindBookBuilder/1.0"
OUT = sys.argv[1] if len(sys.argv) > 1 else "bulk_chesscom.pgn"
PER_COLOR = int(sys.argv[2]) if len(sys.argv) > 2 else 65535
DONE_FILE = OUT + ".done"

TIME_CLASSES = {"rapid", "blitz", "daily"}
LOSS_RESULTS = {"checkmated", "resigned"}
MIN_RATING = 2300
MIN_PLIES = 20
MAX_PLIES = 80          # won within 40 moves
MAX_PER_PLAYER = 200    # per colour
PAUSE = 0.2


def get(url):
    for attempt in range(6):
        try:
            req = urllib.request.Request(
                url, headers={"User-Agent": UA, "Accept": "application/json", "Accept-Encoding": "gzip"}
            )
            with urllib.request.urlopen(req, timeout=90) as r:
                data = r.read()
                if r.headers.get("Content-Encoding") == "gzip":
                    data = gzip.decompress(data)
                return json.loads(data)
        except urllib.error.HTTPError as e:
            if e.code == 429:
                time.sleep(8 * (attempt + 1))
                continue
            if e.code in (404, 410):
                return None
            print(f"  HTTP {e.code} for {url}", file=sys.stderr, flush=True)
            return None
        except Exception as e:  # network hiccup
            print(f"  retry {attempt + 1} for {url}: {e}", file=sys.stderr, flush=True)
            time.sleep(3)
    return None


def movetext(pgn):
    parts = pgn.split("\n\n", 1)
    text = parts[1] if len(parts) > 1 else pgn
    text = re.sub(r"\{[^}]*\}", " ", text)
    return re.sub(r"\s+", " ", text).strip()


def count_plies(text):
    toks = [t for t in text.split(" ") if t and not re.match(r"^\d+\.+$", t) and t not in ("1-0", "0-1", "1/2-1/2", "*")]
    return len(toks)


def load_state():
    seen, counts, done = set(), {"w": 0, "b": 0}, set()
    if os.path.exists(OUT):
        with open(OUT, encoding="utf-8") as f:
            for line in f:
                if line.startswith("[Site "):
                    seen.add(line.split('"')[1])
                elif line.startswith('[Result "1-0"'):
                    counts["w"] += 1
                elif line.startswith('[Result "0-1"'):
                    counts["b"] += 1
    if os.path.exists(DONE_FILE):
        with open(DONE_FILE, encoding="utf-8") as f:
            done = {l.strip().lower() for l in f if l.strip()}
    return seen, counts, done


def player_list():
    names = []
    lb = get("https://api.chess.com/pub/leaderboards") or {}
    for key in ("live_blitz", "live_rapid", "live_bullet", "daily"):
        for p in lb.get(key, []):
            names.append(p["username"])
    titled = get("https://api.chess.com/pub/titled/GM") or {}
    names.extend(titled.get("players", []))
    out, seen = [], set()
    for n in names:
        if n.lower() not in seen:
            seen.add(n.lower())
            out.append(n)
    return out


def main():
    seen, counts, done = load_state()
    print(f"resume: {counts['w']} white wins, {counts['b']} black wins, {len(done)} players done", file=sys.stderr, flush=True)
    players = player_list()
    print(f"{len(players)} players to scan", file=sys.stderr, flush=True)

    for name in players:
        if counts["w"] >= PER_COLOR and counts["b"] >= PER_COLOR:
            break
        if name.lower() in done:
            continue
        time.sleep(PAUSE)
        arch = get(f"https://api.chess.com/pub/player/{name.lower()}/games/archives")
        batch, taken = [], {"w": 0, "b": 0}
        for url in reversed((arch or {}).get("archives", [])):
            if taken["w"] >= MAX_PER_PLAYER and taken["b"] >= MAX_PER_PLAYER:
                break
            time.sleep(PAUSE)
            month = get(url)
            if not month:
                continue
            for g in month.get("games", []):
                if g.get("rules") != "chess" or not g.get("rated") or g.get("time_class") not in TIME_CLASSES:
                    continue
                pgn = g.get("pgn") or ""
                gurl = g.get("url")
                if "[SetUp" in pgn or "[FEN" in pgn or gurl in seen:
                    continue
                w, b = g["white"], g["black"]
                if min(w.get("rating", 0), b.get("rating", 0)) < MIN_RATING:
                    continue
                if w["result"] == "win" and b["result"] in LOSS_RESULTS:
                    color, result = "w", "1-0"
                elif b["result"] == "win" and w["result"] in LOSS_RESULTS:
                    color, result = "b", "0-1"
                else:
                    continue
                if counts[color] >= PER_COLOR or taken[color] >= MAX_PER_PLAYER:
                    continue
                text = movetext(pgn)
                plies = count_plies(text)
                if plies < MIN_PLIES or plies > MAX_PLIES:
                    continue
                seen.add(gurl)
                taken[color] += 1
                counts[color] += 1
                batch.append(
                    f'[Event "chess.com {g.get("time_class")}"]\n[Site "{gurl}"]\n'
                    f'[White "{w["username"]}"]\n[Black "{b["username"]}"]\n'
                    f'[WhiteElo "{w.get("rating")}"]\n[BlackElo "{b.get("rating")}"]\n[Result "{result}"]\n\n'
                    f"{text}\n\n"
                )
        if batch:
            with open(OUT, "a", encoding="utf-8", newline="\n") as f:
                f.write("".join(batch))
        with open(DONE_FILE, "a", encoding="utf-8") as f:
            f.write(name + "\n")
        print(f"{name}: +{taken['w']}w +{taken['b']}b  total white {counts['w']} black {counts['b']}", file=sys.stderr, flush=True)

    print(f"finished: {counts['w']} white wins, {counts['b']} black wins in {OUT}", file=sys.stderr, flush=True)


if __name__ == "__main__":
    main()
