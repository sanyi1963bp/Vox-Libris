#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Kiadás készítése egy paranccsal: teszt, fordítás, feltöltés, ellenőrzés.

Miért helyben fordít, és miért nem a GitHubon? Mert a release APK a
**hibakeresési kulccsal** van aláírva, az pedig ehhez a géphez tartozik
(`~/.android/debug.keystore`). Ha egy build-szolgáltatás fordítaná, más
kulccsal írná alá, és a tesztelőid **nem tudnának frissíteni** — előbb el
kellene távolítaniuk a régit, elveszítve az olvasási pozícióikat, a
könyvjelzőiket és a jegyzeteiket. Ezért marad a fordítás itt.

Használat:

    py -3.12 tools\\vox_release.py --dry-run    # megmutatja, mit tenne
    py -3.12 tools\\vox_release.py              # elkészíti a kiadást

A verziószámot az app/build.gradle.kts-ből olvassa, a leírást pedig a
CHANGELOG.md megfelelő szakaszából — vagy a --notes kapcsolóval megadott
fájlból.

A GitHub-kulcs (personal access token) helye, ebben a sorrendben:
  1. a GITHUB_TOKEN környezeti változó
  2. a local.properties fájl `github.token=...` sora

A local.properties nincs verziókövetve, tehát a kulcs nem kerül fel a
GitHubra. A tokennek `repo` (vagy legalább `contents: write`) jogosultság
kell; a github.com/settings/tokens oldalon lehet készíteni.
"""

import argparse
import json
import os
import re
import subprocess
import sys
import time
import urllib.error
import urllib.request

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

API = "https://api.github.com"
UPLOADS = "https://uploads.github.com"

# A projekt gyökere: ez a fájl a tools/ mappában van.
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# Ide kerülnek a kiadott APK-k, a projekt mellé.
APK_DIR = os.path.dirname(ROOT)


# ------------------------------------------------------------------ segédek

def run(args, cwd=ROOT, check=True):
    """Külső parancs futtatása, a kimenetével együtt."""
    p = subprocess.run(args, cwd=cwd, capture_output=True, text=True,
                       encoding="utf-8", errors="replace", shell=False)
    if check and p.returncode != 0:
        raise RuntimeError("%s\n%s%s" % (" ".join(args), p.stdout, p.stderr))
    return p


def fail(message):
    print("\n  HIBA: %s" % message)
    sys.exit(1)


def read_version():
    """A versionName az app/build.gradle.kts-ből."""
    path = os.path.join(ROOT, "app", "build.gradle.kts")
    with open(path, "r", encoding="utf-8") as f:
        text = f.read()
    m = re.search(r'versionName\s*=\s*"([^"]+)"', text)
    if not m:
        fail("nem találom a versionName sort itt: %s" % path)
    return m.group(1)


def read_token():
    """A GitHub-kulcs: környezeti változóból vagy a local.properties-ből."""
    for name in ("GITHUB_TOKEN", "GH_TOKEN"):
        t = os.environ.get(name)
        if t and t.strip():
            return t.strip(), name
    path = os.path.join(ROOT, "local.properties")
    if os.path.isfile(path):
        with open(path, "r", encoding="utf-8", errors="replace") as f:
            for line in f:
                if line.strip().startswith("github.token"):
                    _, _, value = line.partition("=")
                    if value.strip():
                        return value.strip(), "local.properties"
    return None, None


def repo_slug():
    """A tulajdonos/repó az origin távoli címéből."""
    url = run(["git", "remote", "get-url", "origin"]).stdout.strip()
    m = re.search(r"github\.com[:/]+([^/]+)/([^/.]+)", url)
    if not m:
        fail("az origin nem GitHub-cím: %s" % url)
    return m.group(1), m.group(2)


def notes_from_changelog(version):
    """
    A verzióhoz tartozó szakasz a CHANGELOG magyar feléből.

    A fájlban a magyar és az angol rész is tartalmaz azonos fejlécet, ezért
    az elsőt vesszük — az a magyar.
    """
    path = os.path.join(ROOT, "CHANGELOG.md")
    if not os.path.isfile(path):
        return ""
    with open(path, "r", encoding="utf-8") as f:
        lines = f.read().splitlines()
    start = None
    for i, line in enumerate(lines):
        if line.startswith("### [%s]" % version):
            start = i + 1
            break
    if start is None:
        return ""
    out = []
    for line in lines[start:]:
        if line.startswith("### ["):
            break
        out.append(line)
    return "\n".join(out).strip()


# ------------------------------------------------------------------ GitHub

def api(method, url, token, data=None, content_type="application/json",
        raw=False):
    headers = {
        "Authorization": "Bearer %s" % token,
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
        "User-Agent": "vox-release",
    }
    body = data
    if data is not None and not raw:
        body = json.dumps(data).encode("utf-8")
        headers["Content-Type"] = content_type
    elif raw:
        headers["Content-Type"] = content_type
    req = urllib.request.Request(url, data=body, headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=300) as r:
        text = r.read().decode("utf-8")
    return json.loads(text) if text else {}


def tag_exists(owner, repo, tag):
    try:
        with urllib.request.urlopen(
            "%s/repos/%s/%s/releases/tags/%s" % (API, owner, repo, tag),
            timeout=30,
        ):
            return True
    except urllib.error.HTTPError as e:
        if e.code == 404:
            return False
        raise


# ------------------------------------------------------------------ lépések

def check_git():
    """A kiadás a GitHubon lévő állapotból készül — legyen ott minden."""
    dirty = [l for l in run(["git", "status", "--porcelain"]).stdout.splitlines()
             if l.strip() and not l.startswith("??")]
    if dirty:
        print("  ! nincs elmentve minden változás:")
        for l in dirty[:8]:
            print("      %s" % l)
        return False

    run(["git", "fetch", "--quiet", "origin"], check=False)
    ahead = run(["git", "rev-list", "--count", "origin/main..HEAD"],
                check=False).stdout.strip()
    if ahead and ahead != "0":
        print("  ! %s commit még nincs feltöltve (git push kell)" % ahead)
        return False
    return True


def build(version, skip_tests=False):
    """Tesztek, majd release fordítás, végül az APK a helyére."""
    gradlew = os.path.join(ROOT, "gradlew.bat" if os.name == "nt" else "gradlew")
    tasks = ["assembleRelease"] if skip_tests else ["testDebugUnitTest", "assembleRelease"]
    print("  fordítás: %s" % " ".join(tasks))
    p = run([gradlew] + tasks + ["--console=plain"], check=False)
    if p.returncode != 0:
        tail = (p.stdout + p.stderr).splitlines()[-25:]
        print("\n".join("      %s" % l for l in tail))
        fail("a fordítás nem sikerült")

    built = os.path.join(ROOT, "app", "build", "outputs", "apk", "release",
                         "app-release.apk")
    if not os.path.isfile(built):
        fail("nincs meg a lefordított APK: %s" % built)

    target = os.path.join(APK_DIR, "vox-libris-%s.apk" % version)
    with open(built, "rb") as src, open(target, "wb") as dst:
        dst.write(src.read())
    print("  APK: %s (%.1f MB)" % (target, os.path.getsize(target) / 1048576.0))
    return target


def publish(owner, repo, token, version, notes, apk_path):
    tag = "v%s" % version
    print("  kiadás létrehozása: %s" % tag)
    rel = api("POST", "%s/repos/%s/%s/releases" % (API, owner, repo), token, {
        "tag_name": tag,
        "name": "Vox Libris %s" % version,
        "body": notes,
        "draft": False,
        "prerelease": False,
    })

    print("  APK feltöltése…")
    upload = rel["upload_url"].split("{")[0]
    name = os.path.basename(apk_path)
    with open(apk_path, "rb") as f:
        blob = f.read()
    asset = api("POST", "%s?name=%s" % (upload, name), token, data=blob,
                content_type="application/vnd.android.package-archive", raw=True)
    return rel, asset


def verify(owner, repo, version, asset_url):
    """Amit közzétettünk, azt tényleg le is lehet tölteni?"""
    ok = True
    try:
        req = urllib.request.Request(asset_url, method="GET",
                                     headers={"User-Agent": "vox-release"})
        with urllib.request.urlopen(req, timeout=120) as r:
            size = int(r.headers.get("Content-Length") or 0)
        print("  az APK letölthető (%.1f MB)" % (size / 1048576.0))
    except Exception as e:
        print("  ! az APK nem tölthető le: %s" % e)
        ok = False

    print("  várom, hogy a letöltőoldal átálljon…")
    for _ in range(20):
        try:
            with urllib.request.urlopen(
                "%s/repos/%s/%s/releases" % (API, owner, repo), timeout=30
            ) as r:
                rels = json.loads(r.read().decode("utf-8"))
            if rels and rels[0].get("tag_name") == "v%s" % version:
                print("  az API a v%s-t adja legfrissebbként — az oldal ezt fogja "
                      "mutatni" % version)
                return ok
        except Exception:
            pass
        time.sleep(3)
    print("  ! az API még nem a v%s-t adja legfrissebbként" % version)
    return False


# -------------------------------------------------------------------- futás

def main():
    ap = argparse.ArgumentParser(description="Vox Libris kiadás készítése.")
    ap.add_argument("--dry-run", action="store_true",
                    help="megmutatja, mit tenne, de nem tesz közzé semmit")
    ap.add_argument("--yes", action="store_true", help="ne kérdezzen rá")
    ap.add_argument("--notes", help="a leírás fájlja (alapból a CHANGELOG)")
    ap.add_argument("--skip-tests", action="store_true",
                    help="csak fordítás, tesztek nélkül")
    args = ap.parse_args()

    version = read_version()
    owner, repo = repo_slug()
    tag = "v%s" % version

    if args.notes:
        with open(args.notes, "r", encoding="utf-8") as f:
            notes = f.read().strip()
    else:
        notes = notes_from_changelog(version)

    print("\nVox Libris %s  ->  %s/%s" % (version, owner, repo))
    print("-" * 58)

    if tag_exists(owner, repo, tag):
        fail("a(z) %s kiadás már létezik. Emeld a versionName-et a "
             "build.gradle.kts-ben." % tag)

    token, source = (None, None) if args.dry_run else read_token()
    if not args.dry_run and not token:
        fail("nincs GitHub-kulcs.\n"
             "         Tedd a local.properties fájlba ezt a sort:\n"
             "             github.token=ghp_...\n"
             "         A kulcsot itt tudod elkészíteni (repo jogosultsággal):\n"
             "             https://github.com/settings/tokens")
    if token:
        print("  kulcs: %s" % source)

    if not notes:
        print("  ! nincs leírás ehhez a verzióhoz a CHANGELOG-ban")
    else:
        first = [l for l in notes.splitlines() if l.strip()][:1]
        print("  leírás: %d sor, kezdete: %s" % (
            len(notes.splitlines()), (first[0] if first else "")[:52]))

    clean = check_git()
    if not clean and not args.dry_run:
        fail("előbb mentsd el és told fel a változásokat")

    if args.dry_run:
        print("\n  PRÓBA — nem történik közzététel.")
        print("  Ezt tenné: fordítás, majd %s kiadás az APK-val." % tag)
        return 0

    if not args.yes:
        print()
        answer = input("  Közzétegyem a(z) %s kiadást? [i/n] " % tag).strip().lower()
        if answer not in ("i", "igen", "y", "yes"):
            print("  megszakítva")
            return 1

    print()
    apk = build(version, args.skip_tests)
    rel, asset = publish(owner, repo, token, version, notes, apk)
    print()
    ok = verify(owner, repo, version, asset["browser_download_url"])

    print("\n" + "-" * 58)
    print("  kiadás:    %s" % rel["html_url"])
    print("  APK:       %s" % asset["browser_download_url"])
    print("  tesztelői oldal: https://%s.github.io/%s/docs/" % (owner, repo))
    print("-" * 58)
    return 0 if ok else 1


if __name__ == "__main__":
    try:
        sys.exit(main())
    except KeyboardInterrupt:
        print("\n  megszakítva")
        sys.exit(1)
    except urllib.error.HTTPError as e:
        detail = ""
        try:
            detail = json.loads(e.read().decode("utf-8")).get("message", "")
        except Exception:
            pass
        fail("a GitHub visszautasította (%s) %s" % (e.code, detail))
    except Exception as e:
        fail(str(e))
