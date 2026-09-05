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

# Ide megy a részeredmény futás közben; a végén töröljük.
PARTIAL_SUFFIX = ".vox.partial.json"


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

# A válasz alakját sémával kérjük, nem példával.
#
# Ez nem finomhangolás, hanem az első éles futás tanulsága. Eredetileg egy
# kitalált példa volt a promptban ("Jakub Szapiro"), és a modell azt másolta
# le a feladat elvégzése helyett: egy egész regényből egyetlen szereplő lett,
# az is a példából. A séma nem másolható — csak az alakot írja elő.
CHUNK_SCHEMA = {
    "type": "object",
    "properties": {
        "characters": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "name": {"type": "string"},
                    "description": {"type": "string"},
                },
                "required": ["name", "description"],
            },
        }
    },
    "required": ["characters"],
}

MERGE_SCHEMA = {
    "type": "object",
    "properties": {
        "characters": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "name": {"type": "string"},
                    "aliases": {"type": "array", "items": {"type": "string"}},
                    "description": {"type": "string"},
                },
                "required": ["name", "description"],
            },
        }
    },
    "required": ["characters"],
}


def ask(model, prompt, schema=None, retries=2):
    """
    Kérdés az Ollamának.

    A sémát a régebbi Ollama-változatok nem ismerik; ha visszautasítja, sima
    JSON-módban próbálkozunk tovább.
    """
    formats = [schema, "json"] if schema else ["json"]
    last = None
    for fmt in formats:
        body = json.dumps({
            "model": model,
            "prompt": prompt,
            "stream": False,
            "format": fmt,
            "options": {"temperature": 0.1},
        }).encode("utf-8")
        for attempt in range(retries + 1):
            try:
                req = urllib.request.Request(
                    OLLAMA + "/api/generate", data=body,
                    headers={"Content-Type": "application/json"},
                )
                with urllib.request.urlopen(req, timeout=600) as r:
                    answer = json.loads(r.read().decode("utf-8"))["response"]
                return json.loads(answer)
            except urllib.error.HTTPError as e:
                last = e
                break                       # ezt a formátumot nem fogadja el
            except Exception as e:          # a modell néha csonka JSON-t ad
                last = e
                if attempt < retries:
                    time.sleep(2)
    print("    ! a modell nem adott értelmes választ: %s" % last)
    return None


# A promptokban SZÁNDÉKOSAN nincs kitalált példanév. Az első éles futáson egy
# volt benne, és a modell azt másolta le a feladat elvégzése helyett — egy
# egész regényből egyetlen, a példából származó szereplő lett. A válasz alakját
# ezért séma írja elő, nem példa.

CHUNK_PROMPT = """Az alábbi regényrészletben szereplő SZEMÉLYEKET gyűjtsd ki.

Szabályok:
- Csak személyek. Helyszín, ország, hajó, intézmény, tárgy NEM kell.
- CSAK olyan nevet írj, ami szó szerint szerepel az alábbi részletben.
- A nevet ragok nélkül add meg, abban az alakban, ahogy a szövegben áll.
- A puszta beosztás nem név: névvel együtt említve igen, önmagában nem.
- A leírás egy tömör mondat arról, KICSODA az illető a részlet szerint.
- MAGYARUL írj. A leírás minden szava magyar legyen, angolul ne válaszolj.
- NE TALÁLGASS. Amit a részlet nem mond ki, azt ne írd le. Ha valamiben nem
  vagy biztos, inkább hagyd ki.
- Ha nincs benne személy, adj vissza üres listát.

A részlet:
---
%s
---"""

MERGE_PROMPT = """Ugyanannak a regénynek a szereplőiről készültek részleges
feljegyzések, a könyv egymást követő szakaszaiból. Vond össze őket EGY listába.

Szabályok:
- Aki ugyanaz a személy, az egy tétel legyen. A "name" a teljes, leggyakoribb
  alak, az "aliases" pedig a többi említett alakja ugyanannak a személynek.
- A "description" 1-3 magyar mondat: ki az illető, mi a szerepe, kikhez
  tartozik. Ez a leírás a teljes könyvet összefoglalhatja.
- Csak személyek maradjanak. Helyszín, ország, hajó, intézmény NEM.
- Amelyik névről csak bizonytalan, találgató feljegyzés van, azt hagyd ki.
- A legfontosabb szereplők kerüljenek előre.

A feljegyzések:
---
%s
---"""


def harvest(data, seen):
    """
    A modell válaszából kigyűjtjük, amit tudunk — és semmi többet nem várunk el.

    A nyelvi modell nem szerződéses fél: ugyanarra a kérésre adhat objektumok
    listáját, puszta neveket, más kulcsneveket, vagy a listát a gyökérben. Egy
    ilyen meglepetés miatt nem szabad elveszni egy órányi munkának, ezért itt
    mindent némán átugrunk, amit nem tudunk értelmezni.
    """
    if isinstance(data, list):
        items = data
    elif isinstance(data, dict):
        items = None
        for key in ("characters", "szereplok", "szereplők", "people", "persons"):
            if isinstance(data.get(key), list):
                items = data[key]
                break
        if items is None:
            return 0
    else:
        return 0

    added = 0
    for c in items:
        if not isinstance(c, dict):
            # Puszta név leírás nélkül — nincs mit kezdeni vele.
            continue
        name = c.get("name") or c.get("nev") or c.get("név") or ""
        desc = c.get("description") or c.get("leiras") or c.get("leírás") or ""
        if not isinstance(name, str) or not isinstance(desc, str):
            continue
        name, desc = name.strip(), desc.strip()
        if not name or not desc or not looks_like_name(name):
            continue
        seen.setdefault(name, []).append(desc)
        added += 1
    return added


def looks_like_name(name):
    """
    Névnek látszik-e egyáltalán?

    A modell néha egész fogalmakat ad vissza személy helyett — egy éles
    futáson például „Örökös problémák" került a szereplők közé. Egy név
    legfeljebb három szó, és minden szava nagybetűvel kezdődik.

    Ez csak a durva szemetet szűri. Egy beosztást („Kezelő") ezen az alapon
    nem lehet megkülönböztetni egy vezetéknévtől — azt az összevonó lépésre
    hagyjuk, ami az egész könyvet látja.
    """
    words = [w for w in name.replace("-", " ").split() if w]
    if not (1 <= len(words) <= 3) or len(name) > 60:
        return False
    return all(w[0].isupper() for w in words if w[0].isalpha())


def collect(model, paragraphs, chunk_size, book_path, verbose=True):
    """
    Adagonként végigolvassuk a könyvet, majd összevonjuk a jegyzeteket.

    A részeredményt minden adag után kiírjuk. Egy huszonnégy adagos könyv
    percekig fut; ha a végén bármi elromlik, ne kelljen elölről kezdeni.
    """
    parts = list(chunks(paragraphs, chunk_size))
    seen, start = load_partial(book_path, chunk_size, len(parts))
    if start:
        print("  folytatás a(z) %d. adagtól (%d név már megvan)" % (start + 1, len(seen)))

    # Adagonként kiírjuk, hány nevet hozott. Az első éles futás azért ment el
    # húsz percig a semmiért, mert a képernyőn nem látszott, hogy nulla az
    # eredmény — csak a végén derült ki.
    empty_streak = 0
    try:
        for i in range(start, len(parts)):
            data = ask(model, CHUNK_PROMPT % parts[i], CHUNK_SCHEMA)
            added = 0
            if data is not None:
                # Egyetlen adag hibája sem viheti el az egész futást.
                try:
                    added = harvest(data, seen)
                except Exception as e:
                    print("    ! ezt az adagot kihagyom: %s" % e)
            if verbose:
                print("  [%d/%d] adag (%d karakter) -> %d név, összesen %d" % (
                    i + 1, len(parts), len(parts[i]), added, len(seen)))

            # Ha sorozatban semmit nem hoz, valami elromlott — szóljunk, ne
            # húsz perc múlva derüljön ki egy majdnem üres fájlból.
            empty_streak = empty_streak + 1 if added == 0 else 0
            if empty_streak == 3:
                print("  ! három adag egymás után üres. Ha ez így marad, a modell")
                print("    nem érti a feladatot — érdemes megszakítani (Ctrl+C),")
                print("    és másik modellel próbálni: --model qwen2.5:32b")

            save_partial(book_path, chunk_size, len(parts), i + 1, seen)
    except KeyboardInterrupt:
        print("\n  megszakítva — a részeredmény elmentve, "
              "ugyanezzel a paranccsal folytatható")
        raise

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
    merged = ask(model, MERGE_PROMPT % notes[:60000], MERGE_SCHEMA)
    final = []
    if merged is not None:
        try:
            tmp = {}
            harvest(merged, tmp)
            # Az összevonásból az aliasokat is átvesszük, ha adott.
            items = merged.get("characters") if isinstance(merged, dict) else merged
            if isinstance(items, list):
                for c in items:
                    if not isinstance(c, dict):
                        continue
                    name = (c.get("name") or "").strip() if isinstance(c.get("name"), str) else ""
                    desc = (c.get("description") or "").strip() \
                        if isinstance(c.get("description"), str) else ""
                    if not name or not desc:
                        continue
                    row = {"name": name, "description": desc}
                    al = c.get("aliases")
                    if isinstance(al, list):
                        row["aliases"] = [a.strip() for a in al
                                          if isinstance(a, str) and a.strip()]
                    final.append(row)
        except Exception as e:
            print("  ! az összevonás válaszát nem értem: %s" % e)

    if final:
        return final

    # Ha az összevonás nem sikerült, a nyers gyűjtés is jobb a semminél.
    print("  (az összevonás nem sikerült, a nyers gyűjtés megy ki)")
    return [
        {"name": n, "description": d[0]}
        for n, d in sorted(seen.items(), key=lambda kv: -len(kv[1]))[:60]
    ]


# ----------------------------------------------------------------------- futás

def sidecar_path(book_path):
    base = os.path.splitext(book_path)[0]
    return base + SIDECAR_SUFFIX


def partial_path(book_path):
    return os.path.splitext(book_path)[0] + PARTIAL_SUFFIX


def save_partial(book_path, chunk_size, total, done, seen):
    """
    A részeredmény kiírása minden adag után.

    Egy nagy könyv fél óráig is futhat. Ha a végén elszáll valami — rossz
    válasz, áramszünet, Ctrl+C —, ne kelljen elölről kezdeni.
    """
    try:
        with open(partial_path(book_path), "w", encoding="utf-8") as f:
            json.dump({"chunk": chunk_size, "total": total,
                       "done": done, "seen": seen}, f, ensure_ascii=False)
    except Exception:
        pass                                # a mentés hiánya ne álljon útba


def load_partial(book_path, chunk_size, total):
    """A korábbi részeredmény, ha ugyanahhoz a felosztáshoz tartozik."""
    try:
        p = partial_path(book_path)
        if not os.path.isfile(p):
            return {}, 0
        with open(p, "r", encoding="utf-8") as f:
            d = json.load(f)
        # Más adagméret más felosztást jelent: onnan nem lehet folytatni.
        if d.get("chunk") != chunk_size or d.get("total") != total:
            return {}, 0
        seen = d.get("seen")
        done = d.get("done")
        if not isinstance(seen, dict) or not isinstance(done, int):
            return {}, 0
        return seen, max(0, min(done, total))
    except Exception:
        return {}, 0


def drop_partial(book_path):
    try:
        os.remove(partial_path(book_path))
    except Exception:
        pass


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
    characters = collect(model, paragraphs, chunk_size, book_path)
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
    drop_partial(book_path)
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
