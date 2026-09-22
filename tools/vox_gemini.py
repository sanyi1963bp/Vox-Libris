#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Könyvadatok készítése a Gemini segítségével, a gépen.

Miért itt és nem a telefonon: az olvasó alkalmazásnak nincs internet-
engedélye, és ez szándékos. A nehéz munka ezért itt történik — a kész
eredmény egy kis kísérőfájl, ami a könyv mellé kerül:

    A király.epub
    A király.vox.json      <- ezt készíti ez a szkript

A telefonra a kettőt együtt másolod, és az alkalmazás onnantól hálózat
nélkül is tud mindent a könyvről.

Két üzemmód, mert kétféle a feladat:

  katalogus  A könyv ELEJÉT küldi el (alapértelmezés: 40 000 karakter).
             Cím, szerző, sorozat, műfaj, fülszöveg. Olcsó, tömegesen
             futtatható — több ezer könyvhöz ez való.

  dosszie    A TELJES könyvet elküldi. A fentieken túl szereplők,
             fejezetenkénti összefoglaló és kiejtési javaslatok.
             Drága, de csak arra a néhány könyvre kell, amit elolvasol.

Példák:

    python vox_gemini.py "D:\\konyvek\\A kiraly.epub"
    python vox_gemini.py "D:\\konyvek" --mod katalogus --kesleltetes 6
    python vox_gemini.py "D:\\konyvek" --mod katalogus --korlat 20

A kulcs a local.properties-ben legyen (gemini.key=...), vagy a
GEMINI_API_KEY környezeti változóban.

A munka BÁRMIKOR megszakítható. A napló (vox_naplo.json) megjegyzi, mi
készült el, és a következő indítás ott folytatja. Hónapokig futó
feldolgozásnál ez nem kényelem, hanem feltétel.
"""

import argparse
import base64
import html
import io
import json
import os
import re
import sys
import time
import zipfile

try:
    import requests
except ImportError:
    sys.exit("Hiányzik a 'requests' csomag:  pip install requests")

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# A Windows-konzol alapértelmezése nem bírja az ékezeteket, és ezt a
# kimenetet hetekig fogjuk nézni. A fájlokat ez nem érinti, csak a kiírást.
try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except Exception:
    pass

# Szövegként feldolgozható formátumok. A PDF külön úton megy: azt maga a
# Gemini olvassa, mert abban jobb, mint bármilyen kinyerő, amit írhatnánk.
TEXT_EXT = {".epub", ".txt", ".text", ".htm", ".html", ".xhtml", ".rtf",
            ".mobi", ".prc", ".azw", ".azw3"}
PDF_EXT = {".pdf"}
MINDEN_EXT = TEXT_EXT | PDF_EXT

ALAP_MODELL = "gemini-3.8-flash"
API = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent"

# Inline küldhető PDF mérethatára. E fölött a Files API kellene; egyelőre
# inkább kihagyjuk és megmondjuk, hogy kimaradt.
PDF_MAX = 15 * 1024 * 1024

# Futás közben gyűjtött tokenfogyasztás. Enélkül vakon költenénk: több
# ezer könyvnél a token az egyetlen szám, ami a számlát meghatározza.
TOKEN = {"be": 0, "ki": 0}


# ---------------------------------------------------------------- a kulcs

def read_key():
    """A Gemini-kulcs: környezeti változóból vagy a local.properties-ből."""
    t = os.environ.get("GEMINI_API_KEY")
    if t and t.strip():
        return t.strip(), "GEMINI_API_KEY"
    path = os.path.join(ROOT, "local.properties")
    if os.path.isfile(path):
        with io.open(path, "r", encoding="utf-8", errors="replace") as f:
            for line in f:
                if line.strip().startswith("gemini.key"):
                    _, _, value = line.partition("=")
                    if value.strip():
                        return value.strip(), "local.properties"
    return None, None


# ------------------------------------------------------- szövegkinyerés

def u16(b, off):
    return (b[off] << 8) | b[off + 1]


def u32(b, off):
    return (b[off] << 24) | (b[off + 1] << 16) | (b[off + 2] << 8) | b[off + 3]


def html_to_text(raw):
    """A címkék elhagyása, bekezdésenként. Nincs külső függőség."""
    raw = re.sub(r"<(script|style)[^>]*>.*?</\1>", " ", raw, flags=re.S | re.I)
    # Lágy elválasztójel és láthatatlan karakterek. A tördelt e-könyvekben
    # tele van velük a szöveg ("Ant­hony"), és ezek nemcsak csúnyák:
    # a fejezetek első mondatát kellene visszakeresni velük, ami így nem
    # sikerülne.
    raw = raw.translate({0xAD: None, 0x200B: None, 0x200C: None,
                         0x200D: None, 0xFEFF: None})
    out = []
    for block in re.split(r"</(?:p|div|h[1-6]|li|br)>", raw, flags=re.I):
        t = re.sub(r"<[^>]+>", " ", block)
        t = html.unescape(t)
        t = re.sub(r"[ \t\r\f\v]+", " ", t).strip()
        if len(t) > 1:
            out.append(t)
    return out


def text_from_epub(path):
    """Az EPUB egy zip; a fejezetek sorrendjét az OPF gerince adja meg."""
    paras = []
    with zipfile.ZipFile(path) as z:
        names = [n for n in z.namelist() if re.search(r"\.(x?html?|htm)$", n, re.I)]
        names.sort()
        for n in names:
            try:
                raw = z.read(n).decode("utf-8", "replace")
            except Exception:
                continue
            paras.extend(html_to_text(raw))
    return paras


def palmdoc_decompress(data):
    """PalmDOC (LZ77 változat) kitömörítés — ugyanaz, ami az appban fut."""
    out = bytearray()
    i = 0
    n = len(data)
    while i < n:
        c = data[i]
        i += 1
        if c == 0:
            out.append(0)
        elif 1 <= c <= 8:
            out.extend(data[i:i + c])
            i += c
        elif c <= 0x7F:
            out.append(c)
        elif c <= 0xBF:
            if i >= n:
                break
            pair = (c << 8) | data[i]
            i += 1
            distance = (pair >> 3) & 0x7FF
            length = (pair & 0x7) + 3
            if 1 <= distance <= len(out):
                src = len(out) - distance
                for _ in range(length):
                    out.append(out[src])
                    src += 1
        else:
            out.append(0x20)
            out.append(c ^ 0x80)
    return bytes(out)


def _trailing_size(rec, end):
    num = 0
    for i in range(max(end - 4, 0), end):
        v = rec[i]
        if v & 0x80:
            num = 0
        num = (num << 7) | (v & 0x7F)
    return num


def text_from_mobi(path):
    """MOBI/PRC/AZW: PalmDB konténer, tömörítetlen vagy PalmDOC szöveggel."""
    with open(path, "rb") as f:
        data = f.read()
    if len(data) < 80 or data[60:68] not in (b"BOOKMOBI", b"TEXtREAd"):
        raise ValueError("nem MOBI-szerkezetű fájl")

    count = u16(data, 76)
    if count < 2 or 78 + count * 8 > len(data):
        raise ValueError("sérült MOBI-fejléc")
    offs = [u32(data, 78 + i * 8) for i in range(count)]

    def record(i):
        start = offs[i]
        end = offs[i + 1] if i + 1 < count else len(data)
        return data[start:end] if 0 <= start < end <= len(data) else b""

    r0 = record(0)
    if len(r0) < 16:
        raise ValueError("sérült MOBI-fejléc")
    compression = u16(r0, 0)
    text_len = u32(r0, 4)
    rec_count = u16(r0, 8)
    if u16(r0, 12) != 0:
        raise ValueError("DRM-védett fájl")

    enc, extra = "cp1252", 0
    if len(r0) >= 24 and r0[16:20] == b"MOBI":
        header_len = u32(r0, 20)
        code = u32(r0, 28)
        enc = "utf-8" if code == 65001 else ("cp1252" if code == 1252 else "utf-8")
        if header_len >= 228 and len(r0) >= 244:
            extra = u16(r0, 242)

    if compression == 17480:
        raise ValueError("HUFF/CDIC tömörítés — ezt nem olvassuk")
    if compression not in (1, 2):
        raise ValueError("ismeretlen tömörítés: %d" % compression)

    buf = bytearray()
    for i in range(1, min(rec_count, count - 1) + 1):
        rec = record(i)
        if not rec:
            continue
        end = len(rec)
        for bit in range(15, 0, -1):
            if (extra >> bit) & 1:
                end -= _trailing_size(rec, end)
                if end < 0:
                    end = 0
                    break
        if extra & 1 and end > 0:
            end -= (rec[end - 1] & 0x3) + 1
        if end <= 0:
            continue
        rec = rec[:end]
        buf.extend(palmdoc_decompress(rec) if compression == 2 else rec)

    if 0 < text_len < len(buf):
        buf = buf[:text_len]
    if not buf:
        raise ValueError("nincs szöveg a fájlban")
    return html_to_text(bytes(buf).decode(enc, "replace"))


def text_from_rtf(path):
    """Nyers, de elég: a vezérlőszavak elhagyása."""
    with io.open(path, "r", encoding="utf-8", errors="replace") as f:
        raw = f.read()
    raw = re.sub(r"\\'([0-9a-fA-F]{2})", lambda m: chr(int(m.group(1), 16)), raw)
    raw = re.sub(r"\\par[d]?\b", "\n", raw)
    raw = re.sub(r"\\[a-zA-Z]+-?\d* ?", " ", raw)
    raw = raw.replace("{", " ").replace("}", " ")
    return [p.strip() for p in raw.split("\n") if len(p.strip()) > 1]


def text_from_pdf(path, max_pages=0):
    """
    A PDF szövegrétege, ha van. A könyv-PDF-ek nagy részében van, és akkor
    ez a jó út: olcsó, és ugyanúgy kezelhető, mint bármelyik más formátum.
    Szkennelt PDF-ben nincs szövegréteg — azt a hívónak kell észrevennie.
    """
    from pypdf import PdfReader
    r = PdfReader(path)
    pages = r.pages if not max_pages else r.pages[:max_pages]
    out = []
    for p in pages:
        try:
            t = p.extract_text() or ""
        except Exception:
            continue
        t = t.translate({0xAD: None, 0x200B: None, 0xFEFF: None})
        if t.strip():
            out.append(t.strip())
    return "\n".join(out)


def pdf_first_pages(path, pages):
    """
    A PDF első néhány oldala külön fájlként, a memóriában.

    Szkennelt könyvhöz kell: ott a Gemininek oldalanként KÉPET kell néznie,
    és egy egész könyv így túllépi a befogadóképességét — ebbe futottunk
    bele. A cím, a szerző és a fülszöveg viszont az első oldalakon van.
    """
    from pypdf import PdfReader, PdfWriter
    r = PdfReader(path)
    w = PdfWriter()
    for p in r.pages[:pages]:
        w.add_page(p)
    buf = io.BytesIO()
    w.write(buf)
    return buf.getvalue()


def load_text(path):
    ext = os.path.splitext(path)[1].lower()
    if ext == ".pdf":
        return text_from_pdf(path)
    if ext == ".epub":
        paras = text_from_epub(path)
    elif ext in (".mobi", ".prc", ".azw", ".azw3"):
        paras = text_from_mobi(path)
    elif ext == ".rtf":
        paras = text_from_rtf(path)
    elif ext in (".htm", ".html", ".xhtml"):
        with io.open(path, "r", encoding="utf-8", errors="replace") as f:
            paras = html_to_text(f.read())
    else:
        with io.open(path, "r", encoding="utf-8", errors="replace") as f:
            paras = [p.strip() for p in f.read().split("\n") if p.strip()]
    return "\n".join(paras)


# ------------------------------------------------------------- a séma

def _str(desc):
    return {"type": "STRING", "description": desc}


KATALOGUS_MEZOK = {
    "title": _str("A könyv magyar címe, ahogy megjelent."),
    "original_title": _str("Az eredeti nyelvű cím, ha a könyv fordítás. Különben üres."),
    "author": _str("A szerző teljes neve."),
    "published": {"type": "INTEGER", "description": "Az eredeti megjelenés éve, ha kiderül."},
    "language": _str("A szöveg nyelvének kétbetűs kódja, pl. hu."),
    "series": _str("A sorozat neve, ha a könyv egy sorozat része. Különben üres."),
    "series_index": {"type": "INTEGER", "description": "Hányadik rész a sorozatban. Ha nem sorozat, 0."},
    "genres": {"type": "ARRAY", "items": {"type": "STRING"},
               "description": "2-4 műfaji címke, pl. regény, történelmi, krimi."},
    "tagline": _str("EGYETLEN mondat arról, miről szól a könyv. Lista alatt jelenik meg."),
    "blurb": _str("3-5 mondatos ismertető, mint a könyv hátulján. NE lője le a végét."),
    "setting": _str("Hol és mikor játszódik, egy mondatban. Több szál esetén mindegyik."),
}

KATALOGUS_SCHEMA = {
    "type": "OBJECT",
    "properties": dict(KATALOGUS_MEZOK),
    "required": ["title", "author", "tagline", "blurb"],
}

DOSSZIE_SCHEMA = {
    "type": "OBJECT",
    "properties": dict(KATALOGUS_MEZOK, **{
        "characters": {
            "type": "ARRAY",
            "description": "A fontos szereplők. Mellékalakokat ne sorolj fel.",
            "items": {
                "type": "OBJECT",
                "properties": {
                    "name": _str("A név abban az alakban, ahogy a szöveg leggyakrabban használja."),
                    "aliases": {"type": "ARRAY", "items": {"type": "STRING"},
                                "description": "Egyéb megnevezései: becenév, rang, titulus."},
                    "role": _str("fő, mellék vagy epizód"),
                    "description": _str("2-3 mondat: kicsoda, mi a szerepe a történetben."),
                    "first_chapter": {"type": "INTEGER",
                                      "description": "Hányadik fejezetben bukkan fel először."},
                },
                "required": ["name", "description"],
            },
        },
        "chapters": {
            "type": "ARRAY",
            "description": "Minden fejezethez egy bejegyzés, a könyv sorrendjében.",
            "items": {
                "type": "OBJECT",
                "properties": {
                    "number": {"type": "INTEGER", "description": "A fejezet sorszáma, 1-től."},
                    "title": _str("A fejezet címe, ha van."),
                    "first_sentence": _str(
                        "A fejezet ELSŐ mondata SZÓ SZERINT, a könyv szövegéből másolva. "
                        "Ez köti össze az összefoglalót a fájllal, ezért pontosnak kell lennie."),
                    "so_far": _str(
                        "Mi történt a könyvben EDDIG A PONTIG, beleértve ezt a fejezetet. "
                        "Ne utalj későbbi eseményekre. Aki idáig jutott, ebből értse meg, hol tart."),
                },
                "required": ["number", "first_sentence", "so_far"],
            },
        },
        "pronunciation": {
            "type": "ARRAY",
            "description": ("Nevek és szavak, amiket egy magyar gépi felolvasó rosszul mondana ki. "
                            "Csak azokat sorold fel, ahol az írás és a kiejtés tényleg eltér."),
            "items": {
                "type": "OBJECT",
                "properties": {
                    "written": _str("Ahogy a szövegben szerepel."),
                    "spoken": _str("Magyar betűkkel, ahogy ki kell mondani."),
                },
                "required": ["written", "spoken"],
            },
        },
    }),
    "required": ["title", "author", "tagline", "blurb", "characters", "chapters"],
}


KATALOGUS_PROMPT = """Egy könyv elejét kapod. Állapítsd meg az adatait.

Amit nem tudsz biztosan, azt hagyd üresen — NE TALÁLD KI. Jobb egy üres
mező, mint egy kitalált évszám vagy sorozatcím.

A fülszöveg és a rövid összefoglaló MAGYARUL legyen, akkor is, ha a könyv
más nyelvű. Ne lőjék le a történet végét.
"""

DOSSZIE_PROMPT = """Egy teljes könyvet kapsz. Készíts belőle kísérőadatokat
egy hangoskönyv-olvasó alkalmazáshoz.

Három dolog fontos:

1. A FEJEZETENKÉNTI ÖSSZEFOGLALÓ ("eddig") azt mondja el, hol tart az
   olvasó. Aki a 7. fejezetnél jár, a 7. bejegyzést olvassa — és abból
   NEM derülhet ki semmi, ami később történik. Ez a legfontosabb szabály.

2. Az "elso_mondat" mezőbe a fejezet első mondatát SZÓ SZERINT másold be a
   könyvből. Ez alapján találja meg az alkalmazás, melyik fejezetről van
   szó. Ha átfogalmazod, használhatatlan.

3. A "kiejtes" listába azok a nevek és szavak kerüljenek, amiket egy
   magyar gépi felolvasó félreolvasna — idegen nevek, régies alakok,
   rövidítések. Csak ahol tényleg eltér az írás a kiejtéstől.

Amit nem tudsz biztosan, azt hagyd üresen. Ne találj ki adatokat.
Minden szöveg MAGYARUL legyen.
"""


# -------------------------------------------------------------- a kérés

class KvotaVege(Exception):
    """
    Elfogyott a keret — és ez NEM a könyv hibája.

    Fontos, hogy külön legyen kezelve: ha a napi kvóta elfogyott, akkor a
    lista összes maradék könyve ugyanúgy elbukna, egyenként negyedórákat
    várakozva a semmire. Ilyenkor abba kell hagyni a futást, és a könyveket
    érintetlenül hagyni, hogy legközelebb sorra kerüljenek.
    """


def ask(key, model, prompt, schema, text=None, pdf_bytes=None, retries=6):
    """
    Egy kérés a Geminihez. A 429 (kvóta) nem hiba, hanem várakozás:
    ingyenes kerettel ez a normális működés, nem kivétel.
    """
    parts = [{"text": prompt}]
    if pdf_bytes is not None:
        parts.append({"inline_data": {
            "mime_type": "application/pdf",
            "data": base64.b64encode(pdf_bytes).decode("ascii"),
        }})
    if text is not None:
        parts.append({"text": "\n\n--- A KÖNYV SZÖVEGE ---\n\n" + text})

    body = {
        "contents": [{"parts": parts}],
        # A tartalomszűrő szépirodalomra nincs felkészítve: egy skandináv
        # krimi, egy háborús regény vagy egy thriller simán elakad rajta
        # (PROHIBITED_CONTENT), pedig csak fülszöveget kérünk róla. Ezek a
        # könyvek a saját polcodon állnak, és a feladat a katalogizálásuk.
        "safetySettings": [
            {"category": c, "threshold": "BLOCK_NONE"} for c in (
                "HARM_CATEGORY_HARASSMENT",
                "HARM_CATEGORY_HATE_SPEECH",
                "HARM_CATEGORY_SEXUALLY_EXPLICIT",
                "HARM_CATEGORY_DANGEROUS_CONTENT",
            )
        ],
        "generationConfig": {
            "responseMimeType": "application/json",
            "responseSchema": schema,
            "temperature": 0.2,
        },
    }
    url = API % model
    wait = 20
    kvota_volt = False
    last = ""
    for attempt in range(retries):
        try:
            r = requests.post(url, params={"key": key}, json=body, timeout=900)
        except Exception as e:
            last = str(e)
            time.sleep(wait)
            wait *= 2
            continue

        if r.status_code == 429:
            delay = wait
            try:
                info = r.json().get("error", {})
                for d in info.get("details", []):
                    if "retryDelay" in str(d):
                        m = re.search(r"(\d+)s", json.dumps(d))
                        if m:
                            delay = int(m.group(1)) + 2
            except Exception:
                pass
            kvota_volt = True
            print("      kvóta — várok %d másodpercet…" % delay)
            time.sleep(delay)
            wait = min(wait * 2, 300)
            continue

        if r.status_code >= 400:
            last = "HTTP %d: %s" % (r.status_code, r.text[:300])
            # A 400 rendszerint a kérésről szól (túl nagy, rossz séma) —
            # azt nem oldja meg az ismétlés.
            if r.status_code == 400:
                break
            time.sleep(wait)
            wait *= 2
            continue

        try:
            data = r.json()
            # Ha nincs válaszjelölt, a "KeyError: candidates" semmit nem mond.
            # Pedig az ok rendszerint kiderül: a modell elakadt, elfogyott a
            # kerete a válaszra, vagy a tartalomszűrő nem engedte át.
            if not data.get("candidates"):
                ok = (data.get("promptFeedback", {}).get("blockReason")
                      or "a modell nem adott választ")
                raise RuntimeError("elutasítva: %s" % ok)
            c0 = data["candidates"][0]
            if "content" not in c0:
                raise RuntimeError("csonka válasz (%s)" % c0.get("finishReason", "?"))
            txt = c0["content"]["parts"][0]["text"]
            u = data.get("usageMetadata", {})
            TOKEN["be"] += u.get("promptTokenCount", 0)
            TOKEN["ki"] += u.get("candidatesTokenCount", 0)
            return json.loads(txt)
        except Exception as e:
            last = "értelmezhetetlen válasz: %s" % e
            time.sleep(5)
    if kvota_volt:
        raise KvotaVege(last or "elfogyott a keret")
    raise RuntimeError(last or "nem sikerült választ kapni")


# --------------------------------------------------------------- napló

class Naplo:
    """
    Mit dolgoztunk már fel. Hónapokig futó munkánál ez tartja össze az
    egészet: bármikor megszakítható, és nem kezd újra semmit.
    """

    def __init__(self, path):
        self.path = path
        self.data = {}
        if os.path.isfile(path):
            try:
                with io.open(path, "r", encoding="utf-8") as f:
                    self.data = json.load(f)
            except Exception:
                self.data = {}

    def kesz(self, book):
        rec = self.data.get(os.path.abspath(book))
        return bool(rec) and rec.get("allapot") == "kesz"

    def ir(self, book, allapot, uzenet=""):
        self.data[os.path.abspath(book)] = {
            "allapot": allapot,
            "uzenet": uzenet,
            "ido": time.strftime("%Y-%m-%d %H:%M:%S"),
        }
        tmp = self.path + ".tmp"
        with io.open(tmp, "w", encoding="utf-8") as f:
            json.dump(self.data, f, ensure_ascii=False, indent=1)
        os.replace(tmp, self.path)


# ------------------------------------------------------------ a munka

def sidecar_for(book):
    return os.path.splitext(book)[0] + ".vox.json"


def collect(target):
    if os.path.isfile(target):
        return [target]
    out = []
    for dirpath, _, files in os.walk(target):
        for fn in sorted(files):
            if os.path.splitext(fn)[1].lower() in MINDEN_EXT:
                out.append(os.path.join(dirpath, fn))
    return out


def process(book, key, model, mode, max_chars, min_chars):
    ext = os.path.splitext(book)[1].lower()
    schema = KATALOGUS_SCHEMA if mode == "katalogus" else DOSSZIE_SCHEMA
    prompt = KATALOGUS_PROMPT if mode == "katalogus" else DOSSZIE_PROMPT

    if ext in PDF_EXT:
        # Először a szövegréteget keressük. Ha van, a PDF ugyanolyan olcsó,
        # mint bármelyik e-könyv. Ha nincs (szkennelt lapok), akkor és csak
        # akkor küldjük képként — és abból is csak az első oldalakat, mert
        # a Gemini oldalanként számol, és egy egész könyv nem fér bele.
        text = text_from_pdf(book)
        merve = len(text)
        if merve >= min_chars:
            if max_chars and len(text) > max_chars:
                text = text[:max_chars]
            result = ask(key, model, prompt, schema, text=text)
        else:
            oldal = 12 if mode == "katalogus" else 40
            data = pdf_first_pages(book, oldal)
            if len(data) > PDF_MAX:
                raise ValueError("szkennelt PDF, az első %d oldal is túl nagy (%.1f MB)"
                                 % (oldal, len(data) / 1024.0 / 1024))
            result = ask(key, model, prompt, schema, pdf_bytes=data)
            merve = os.path.getsize(book)
    else:
        text = load_text(book)
        merve = len(text)
        # Töredékre nem kérdezünk rá. Egy háromezer karakteres mintafájlból
        # a modell magabiztosan kiállít egy teljes könyvadatlapot — és ezt
        # utólag senki nem venné észre több ezer könyv között. Inkább
        # maradjon üres a hely, mint hogy kitalált adat kerüljön bele.
        if merve < min_chars:
            raise ValueError("csak %d karakter — töredék, nem könyv" % merve)
        if max_chars and len(text) > max_chars:
            text = text[:max_chars]
        result = ask(key, model, prompt, schema, text=text)

    result["_keszult"] = time.strftime("%Y-%m-%d")
    result["_mod"] = mode
    result["_modell"] = model
    out = sidecar_for(book)
    with io.open(out, "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, indent=1)
    return out, merve, result


def main():
    ap = argparse.ArgumentParser(
        description="Könyvadatok készítése Geminivel, a könyv mellé.")
    ap.add_argument("cel", help="egy könyvfájl vagy egy mappa")
    ap.add_argument("--mod", choices=["katalogus", "dosszie"], default="dosszie",
                    help="katalogus = olcsó, a könyv eleje; dosszie = teljes könyv")
    ap.add_argument("--modell", default=ALAP_MODELL)
    ap.add_argument("--kesleltetes", type=float, default=4.0,
                    help="szünet két könyv között, másodpercben")
    ap.add_argument("--korlat", type=int, default=0,
                    help="legfeljebb ennyi könyvet dolgoz fel most (0 = mind)")
    ap.add_argument("--min-karakter", type=int, default=20000,
                    help="ennél rövidebb szöveget töredéknek tekint és kihagy")
    ap.add_argument("--max-karakter", type=int, default=None,
                    help="ennyi karaktert küld (katalógusban alap: 40000)")
    ap.add_argument("--naplo", default=None, help="a napló fájl helye")
    ap.add_argument("--kitart", action="store_true",
                    help="ha elfogy a keret, megvárja a megújulását és folytatja")
    ap.add_argument("--varakozas", type=float, default=2.0,
                    help="kitartó módban ennyi órát vár, ha elfogyott a keret")
    ap.add_argument("--ujra", action="store_true",
                    help="a meglévő kísérőfájlokat is újrakészíti")
    ap.add_argument("--proba", action="store_true",
                    help="csak megmutatja, mit csinálna")
    args = ap.parse_args()

    key, forras = read_key()
    if not key and not args.proba:
        sys.exit("Nincs Gemini-kulcs. Tedd a local.properties-be:  gemini.key=...")

    max_chars = args.max_karakter
    if max_chars is None:
        max_chars = 40000 if args.mod == "katalogus" else 0

    books = collect(args.cel)
    if not books:
        sys.exit("Nem találtam feldolgozható könyvet itt: %s" % args.cel)

    naplo_path = args.naplo or os.path.join(
        args.cel if os.path.isdir(args.cel) else os.path.dirname(args.cel),
        "vox_naplo.json")
    naplo = Naplo(naplo_path)

    todo = []
    for b in books:
        if not args.ujra and (naplo.kesz(b) or os.path.isfile(sidecar_for(b))):
            continue
        todo.append(b)

    print("Vox Libris — könyvadatok Geminivel")
    print("-" * 58)
    print("  üzemmód:   %s%s" % (args.mod,
                                 ("  (max %d karakter)" % max_chars) if max_chars else "  (teljes szöveg)"))
    print("  modell:    %s" % args.modell)
    if key:
        print("  kulcs:     %s" % forras)
    print("  napló:     %s" % naplo_path)
    print("  találat:   %d könyv, ebből %d vár feldolgozásra" % (len(books), len(todo)))
    if args.korlat:
        todo = todo[:args.korlat]
        print("  most:      %d (korlát)" % len(todo))
    print()

    if args.proba:
        for b in todo[:20]:
            print("   ", os.path.basename(b))
        if len(todo) > 20:
            print("    … és még %d" % (len(todo) - 20))
        print("\n  PRÓBA — nem küldtünk semmit.")
        return

    kesz = hiba = 0
    t0 = time.time()
    # Munkasor, nem egyszerű ciklus: ha a keret elfogy, a félbehagyott könyv
    # visszakerül a sor elejére, és várakozás után onnan folytatjuk.
    sor = list(todo)
    ossz = len(sor)
    megszakitva = False
    while sor:
        b = sor.pop(0)
        n = ossz - len(sor)
        print("[%d/%d] %s" % (n, ossz, os.path.basename(b)))
        try:
            out, merve, adat = process(b, key, args.modell, args.mod,
                                       max_chars, args.min_karakter)
            naplo.ir(b, "kesz")
            kesz += 1
            # Ne csak fájlneveket lássunk: ami megjött, azt mutassuk is meg.
            # Így futás közben ellenőrizhető, hogy a munka értelmes-e, nem
            # kell hozzá utólag fájlokat nyitogatni.
            cim = adat.get("title") or "?"
            szerzo = adat.get("author") or ""
            sor = adat.get("series") or ""
            if sor:
                cim += "  [%s %s.]" % (sor, adat.get("series_index") or "?")
            print("      %s — %s" % (cim, szerzo))
            tag = (adat.get("tagline") or "").strip()
            if tag:
                print("      %s" % tag[:150])
        except KeyboardInterrupt:
            print()
            print("  Megszakítva. A napló megvan, folytatható.")
            megszakitva = True
            break
        except KvotaVege:
            # A könyvet NEM jegyezzük fel hibásnak: nem vele volt baj, a
            # keret fogyott el. Visszatesszük a sorba.
            sor.insert(0, b)
            if not args.kitart:
                print()
                print("  Elfogyott a napi keret. A futás itt abbamarad.")
                print("  Ez nem hiba: a feldolgozott könyvek megvannak, a többi")
                print("  változatlanul vár. Indítsd újra, amikor a keret megújul.")
                print("  (A --kitart kapcsolóval magától megvárná a megújulást.)")
                break
            # Kitartó mód: megvárjuk, míg a keret megújul, és folytatjuk.
            # Ettől lehet egyszer elindítani, és hetekig magára hagyni.
            print()
            print("  Elfogyott a keret. Várok %g órát, aztán folytatom." % args.varakozas)
            print("  (%d kész, %d hátra — Ctrl+C-vel bármikor megszakítható.)"
                  % (kesz, len(sor)))
            print()
            try:
                time.sleep(args.varakozas * 3600)
            except KeyboardInterrupt:
                print()
                print("  Megszakítva. A napló megvan, folytatható.")
                megszakitva = True
                break
            continue
        except Exception as e:
            naplo.ir(b, "hiba", str(e)[:300])
            hiba += 1
            print("      ! %s" % e)
        if sor and not megszakitva:
            time.sleep(args.kesleltetes)


    perc = (time.time() - t0) / 60.0
    print()
    print("-" * 58)
    print("  kész: %d    hiba: %d    idő: %.1f perc" % (kesz, hiba, perc))
    print("  token: %s bemenet, %s kimenet" % ("{:,}".format(TOKEN["be"]).replace(",", " "),
                                              "{:,}".format(TOKEN["ki"]).replace(",", " ")))
    if kesz:
        print("  könyvenként átlag: %s token" % "{:,}".format((TOKEN["be"] + TOKEN["ki"]) // kesz).replace(",", " "))
    if hiba:
        print("  A hibás könyvek a naplóban vannak, újrafuttatáskor sorra kerülnek.")


if __name__ == "__main__":
    main()
