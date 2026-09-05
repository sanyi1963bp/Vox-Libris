#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Szereplőleírások készítése egy könyvhöz, helyben futó nyelvi modellel.

Miért a PC-n és nem a telefonon? Mert ahhoz, hogy egy szövegről meg lehessen
mondani, ki kicsoda, valódi szövegértés kell. A telefonos alkalmazásban
megpróbáltuk szabályokkal — két valódi regényen mérve huszonöt szereplőből
egyre adott bemutatást, és az is hibás volt.

Így viszont a nehéz munka itt történik, a saját gépeden, a saját modelleddel.
Az eredmény egy kis JSON-fájl a könyv mellett, amit az app beolvas. Az
alkalmazásnak **továbbra sincs internet-engedélye**, és nem hagy el semmi a
telefont.

Használat:

    python vox_characters.py "D:/konyvek/A kiraly.epub"
    python vox_characters.py "D:/konyvek" --all
    python vox_characters.py konyv.epub --model qwen2.5:14b

Előfeltétel: fusson az Ollama (https://ollama.com), és legyen letöltve a
modell:  ollama pull qwen2.5:14b

Kimenet:  A kiraly.vox.json  — a könyv mellé, ugyanabba a mappába.
"""

import argparse
import html
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request
import zipfile

# A Windows-konzol alapértelmezett kódlapja nem bírja a magyar ékezeteket, és
# egy hosszú futás közepén elhasalni egy kiírás miatt bosszantó volna.
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

OLLAMA = "http://localhost:11434"

# Magyar szöveghez a nagyobb, többnyelvű modellek érezhetően jobbak. Ez csak
# alapértelmezés; a --model kapcsolóval bármi megadható.
DEFAULT_MODEL = "qwen2.5:14b"

# Egy adagban ennyi karaktert küldünk a modellnek. Nagyobb adag kevesebb kérés,
# de több memória; 20 ezer karakter nagyjából 6-7 ezer token.
DEFAULT_CHUNK = 20000

SIDECAR_SUFFIX = ".vox.json"


# --------------------------------------------------------------- szövegkinyerés

def text_from_epub(path):
    """Bekezdések egy EPUB-ból. Nincs külső függőség: a fájl egy zip."""
    paragraphs = []
    with zipfile.ZipFile(path) as z:
        names = [n for n in z.namelist() if re.search(r"\.(x?html?|htm)$", n, re.I)]
        names.sort()
        for n in names:
            try:
                raw = z.read(n).decode("utf-8", "replace")
            except Exception:
                continue
            paragraphs.extend(text_from_html(raw))
    return paragraphs


def text_from_html(raw):
    """A címkék elhagyása, bekezdésenként."""
    raw = re.sub(r"<(script|style)[^>]*>.*?</\1>", " ", raw, flags=re.S | re.I)
    out = []
    for block in re.split(r"</(?:p|div|h[1-6]|li)>", raw, flags=re.I):
        t = re.sub(r"<[^>]+>", " ", block)
        t = html.unescape(t)
        t = re.sub(r"\s+", " ", t).strip()
        if len(t) > 40:
            out.append(t)
    return out


def text_from_txt(path):
    with open(path, "r", encoding="utf-8", errors="replace") as f:
        return [p.strip() for p in f.read().split("\n") if len(p.strip()) > 40]


def load_book(path):
    ext = os.path.splitext(path)[1].lower()
    if ext == ".epub":
        return text_from_epub(path)
    if ext in (".txt", ".text"):
        return text_from_txt(path)
    if ext in (".htm", ".html", ".xhtml"):
        with open(path, "r", encoding="utf-8", errors="replace") as f:
            return text_from_html(f.read())
    raise ValueError("nem támogatott formátum: %s (epub, txt, html megy)" % ext)


def chunks(paragraphs, size):
    """A bekezdésekből adagok, bekezdéshatáron vágva."""
    buf, n = [], 0
    for p in paragraphs:
        if n + len(p) > size and buf:
            yield "\n\n".join(buf)
            buf, n = [], 0
        buf.append(p)
        n += len(p)
    if buf:
        yield "\n\n".join(buf)


# ------------------------------------------------------------------- a modell

def ask(model, prompt, retries=2):
    """Kérdés az Ollamának, JSON válasszal."""
    body = json.dumps({
        "model": model,
        "prompt": prompt,
        "stream": False,
        "format": "json",
        "options": {"temperature": 0.1},
    }).encode("utf-8")
    req = urllib.request.Request(
        OLLAMA + "/api/generate", data=body,
        headers={"Content-Type": "application/json"},
    )
    last = None
    for attempt in range(retries + 1):
        try:
            with urllib.request.urlopen(req, timeout=600) as r:
                answer = json.loads(r.read().decode("utf-8"))["response"]
            return json.loads(answer)
        except Exception as e:              # a modell néha csonka JSON-t ad
            last = e
            if attempt < retries:
                time.sleep(2)
    print("    ! a modell nem adott értelmes választ: %s" % last)
    return None


CHUNK_PROMPT = """Az alábbi regényrészletben szereplő SZEMÉLYEKET gyűjtsd ki.

Szabályok:
- Csak személyek. Helyszín, ország, utca, intézmény, tárgy NEM kell.
- A nevet abban az alakban add meg, ahogy a szövegben áll, ragok nélkül.
- A leírás egy tömör magyar mondat legyen arról, KICSODA az illető:
  foglalkozása, rokoni viszonyai, szerepe a történetben.
- Ha egy szereplőről ebben a részletben semmi lényeges nem derül ki, hagyd ki.

Válaszolj pontosan ilyen JSON-nal:
{"characters":[{"name":"Jakub Szapiro","description":"Zsidó bokszoló, Kaplica bandájának verőembere."}]}

A részlet:
---
%s
---"""

MERGE_PROMPT = """Ugyanannak a regénynek a szereplőiről készültek részleges
feljegyzések, fejezetenként. Vond össze őket EGY listába.

Szabályok:
- Aki ugyanaz a személy, az egy tétel legyen. A "name" a teljes, leggyakoribb
  alak legyen ("Jakub Szapiro"), az "aliases" pedig az összes többi említett
  alak ("Szapiro", "Jakub").
- A "description" 1-3 magyar mondat: ki az illető, mi a szerepe, kikhez
  tartozik. Nyugodtan foglalja össze a teljes ívét.
- Csak személyek maradjanak. Helyszín, ország, intézmény NEM.
- A legfontosabb szereplők kerüljenek előre.

Válaszolj pontosan ilyen JSON-nal:
{"characters":[{"name":"Jakub Szapiro","aliases":["Szapiro","Jakub"],"description":"..."}]}

A feljegyzések:
---
%s
---"""


def collect(model, paragraphs, chunk_size, verbose=True):
    """Adagonként végigolvassuk a könyvet, majd összevonjuk a jegyzeteket."""
    parts = list(chunks(paragraphs, chunk_size))
    seen = {}
    for i, part in enumerate(parts, 1):
        if verbose:
            print("  [%d/%d] adag (%d karakter)…" % (i, len(parts), len(part)))
        data = ask(model, CHUNK_PROMPT % part)
        if not data:
            continue
        for c in data.get("characters") or []:
            name = (c.get("name") or "").strip()
            desc = (c.get("description") or "").strip()
            if not name or not desc:
                continue
            seen.setdefault(name, []).append(desc)

    if not seen:
        return []

    # Az összevonás egy külön kérés: a modell látja az összes részletet, és
    # ebből ír egy egységes leírást szereplőnként.
    notes = "\n".join(
        "%s: %s" % (n, " ".join(d[:6])) for n, d in sorted(
            seen.items(), key=lambda kv: -len(kv[1])
        )[:60]
    )
    if verbose:
        print("  összevonás (%d név)…" % len(seen))
    merged = ask(model, MERGE_PROMPT % notes[:60000])
    if merged and merged.get("characters"):
        return merged["characters"]

    # Ha az összevonás nem sikerült, a nyers gyűjtés is jobb a semminél.
    return [
        {"name": n, "description": d[0]}
        for n, d in sorted(seen.items(), key=lambda kv: -len(kv[1]))[:60]
    ]


# ----------------------------------------------------------------------- futás

def sidecar_path(book_path):
    base = os.path.splitext(book_path)[0]
    return base + SIDECAR_SUFFIX


def process(book_path, model, chunk_size, force=False):
    out_path = sidecar_path(book_path)
    if os.path.exists(out_path) and not force:
        print("kihagyva (már van hozzá fájl): %s" % os.path.basename(book_path))
        return True

    print("\n%s" % os.path.basename(book_path))
    try:
        paragraphs = load_book(book_path)
    except Exception as e:
        print("  ! nem sikerült beolvasni: %s" % e)
        return False
    if not paragraphs:
        print("  ! nincs benne szöveg")
        return False
    print("  %d bekezdés, %d karakter" % (
        len(paragraphs), sum(len(p) for p in paragraphs)))

    started = time.time()
    characters = collect(model, paragraphs, chunk_size)
    if not characters:
        print("  ! nem lett belőle szereplő")
        return False

    doc = {
        "format": 1,
        "book": os.path.splitext(os.path.basename(book_path))[0],
        "generated": time.strftime("%Y-%m-%d"),
        "model": model,
        # A teljes könyvet ismeri, tehát a leírás elárulhat későbbi
        # fejleményeket is. Az app ezt a mezőt jelenleg nem használja, de
        # itt marad, hogy később lehessen rá építeni.
        "spoilers": True,
        "characters": characters,
    }
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(doc, f, ensure_ascii=False, indent=1)
    print("  kész: %d szereplő, %.0f mp -> %s" % (
        len(characters), time.time() - started, os.path.basename(out_path)))
    return True


def check_ollama(model):
    try:
        with urllib.request.urlopen(OLLAMA + "/api/tags", timeout=10) as r:
            tags = json.loads(r.read().decode("utf-8"))
    except Exception:
        print("Nem érem el az Ollamát a %s címen." % OLLAMA)
        print("Indítsd el, aztán próbáld újra.")
        return False
    names = [m.get("name", "") for m in tags.get("models", [])]
    if not any(n == model or n.startswith(model + ":") for n in names):
        print("Nincs letöltve ez a modell: %s" % model)
        print("Töltsd le:  ollama pull %s" % model)
        if names:
            print("Vagy válassz a meglévők közül a --model kapcsolóval:")
            for n in names:
                print("   %s" % n)
        return False
    return True


def main():
    ap = argparse.ArgumentParser(
        description="Szereplőleírások készítése könyvekhez, helyi nyelvi modellel."
    )
    ap.add_argument("path", help="egy könyv, vagy egy mappa (--all mellett)")
    ap.add_argument("--all", action="store_true", help="a mappa összes könyve")
    ap.add_argument("--model", default=DEFAULT_MODEL, help="Ollama-modell neve")
    ap.add_argument("--chunk", type=int, default=DEFAULT_CHUNK,
                    help="adagméret karakterben (alapértelmezés: %d)" % DEFAULT_CHUNK)
    ap.add_argument("--force", action="store_true",
                    help="a meglévő kísérőfájl felülírása")
    args = ap.parse_args()

    if not check_ollama(args.model):
        return 1

    if args.all:
        if not os.path.isdir(args.path):
            print("Nem mappa: %s" % args.path)
            return 1
        books = []
        for root, _dirs, files in os.walk(args.path):
            for f in sorted(files):
                if os.path.splitext(f)[1].lower() in (".epub", ".txt", ".html", ".htm"):
                    books.append(os.path.join(root, f))
        if not books:
            print("Nem találtam könyvet ebben a mappában.")
            return 1
        print("%d könyv, modell: %s" % (len(books), args.model))
        ok = sum(1 for b in books if process(b, args.model, args.chunk, args.force))
        print("\nKész: %d/%d." % (ok, len(books)))
        return 0

    if not os.path.isfile(args.path):
        print("Nincs ilyen fájl: %s" % args.path)
        return 1
    return 0 if process(args.path, args.model, args.chunk, args.force) else 1


if __name__ == "__main__":
    sys.exit(main())
