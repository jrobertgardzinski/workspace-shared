#!/usr/bin/env python3
"""Build the Ubiquitous-Language glossary and interactive documentation.

Source of truth: the code. A *noun* term is a public type (class/record/
interface/enum) under a `domain` or `config` package; a *verb* term is either a
use-case command class in `security-system` (a class with an `execute()` method
— the class name carries the verb, `execute` is plumbing) or a domain predicate
method (`is/has/can…`). Each term's definition is its Javadoc.

Two documentation surfaces are tied to the glossary:
  - Allure documents domain/config/system (unit/property tests). Terms link to
    it by Java class name (Email -> EmailTest), no text convention.
  - Cucumber documents system/application/infrastructure/UI. Each `.feature`
    declares a per-feature legend in its description header:

        Nouns:
          USER  -> User
        Verbs:
          REGISTER, REGISTERS, REGISTERED, REGISTRATION -> Register

    mapping CAPS surface forms (trailing `*` = prefix wildcard) to Java
    identifiers. The generator resolves each identifier against the code (WARN
    on unknown/dead entries) and renders each feature with its terms hyperlinked
    to the glossary — so you can jump Cucumber <-> glossary <-> Allure.

Outputs:
  - docs/glossary/glossary.md      (versioned; the readable changelog)
  - docs/glossary/site/index.html  (generated; interactive, git-ignored)
"""

import html
import json
import re
import shutil
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parent
OUT_MD = ROOT / "docs" / "glossary" / "glossary.md"
SITE = ROOT / "docs" / "glossary" / "site"
SITE_TO_ROOT = "../../.."          # docs/glossary/site -> repo root
STATUS_EMOJI = {"passed": "✅", "failed": "❌", "broken": "⚠️",
                "skipped": "⏭️", "unknown": "❓"}

TYPE_RE = re.compile(
    r"^\s*public\s+(?:final\s+|abstract\s+|sealed\s+|non-sealed\s+)*"
    r"(class|record|interface|enum)\s+([A-Z][A-Za-z0-9_]*)"
)
# return-type then lowercase-initial name then '(' -> a method (not a constructor)
METHOD_RE = re.compile(
    r"^\s*public\s+(?:static\s+|final\s+|synchronized\s+|default\s+|abstract\s+)*"
    r"(?:<[^>]+>\s*)?"
    r"[A-Za-z_][\w<>,.\[\]\s?]*?\s+([a-z][A-Za-z0-9_]*)\s*\("
)


def spaced_caps(name: str) -> str:
    """`SessionFamily` -> `SESSION FAMILY`, `isStillActive` -> `IS STILL ACTIVE`."""
    words = re.findall(r"[A-Z]+(?=[A-Z][a-z])|[A-Z]?[a-z]+|[A-Z]+|[0-9]+", name)
    return " ".join(w.upper() for w in words)


def clean_javadoc(lines):
    text = []
    for ln in lines:
        ln = ln.strip()
        ln = re.sub(r"^/\*\*+", "", ln)
        ln = re.sub(r"\*+/\s*$", "", ln)
        ln = re.sub(r"^\*+\s?", "", ln)
        text.append(ln.strip())
    joined = " ".join(t for t in text if t)
    joined = re.sub(r"\{@\w+\s+([^}]+)\}", r"\1", joined)   # {@link Foo} -> Foo
    return re.sub(r"\s+", " ", joined).strip()


def first_sentence(doc: str) -> str:
    if not doc:
        return ""
    m = re.search(r"(.+?[.!?])(\s|$)", doc)
    return m.group(1).strip() if m else doc


# A domain method is a UL verb only if it is a predicate (is/has/can/should…);
# plumbing (equals/hashCode/toString/value/of/build…) is filtered out this way.
PREDICATE_RE = re.compile(r"^(is|has|can|should|was|were)[A-Z]")


def parse_java(path: Path):
    """Return the top-level types in one .java file, each with its public methods."""
    pkg = ""
    types = []
    current = None
    pending = None  # cleaned javadoc waiting for the next declaration
    buf = None      # javadoc line buffer while inside a block

    for raw in path.read_text(encoding="utf-8", errors="replace").splitlines():
        s = raw.strip()
        if not pkg:
            mp = re.match(r"package\s+([\w.]+)\s*;", s)
            if mp:
                pkg = mp.group(1)
                continue
        if buf is not None:
            buf.append(raw)
            if "*/" in s:
                pending = clean_javadoc(buf)
                buf = None
            continue
        if s.startswith("/**"):
            buf = [raw]
            if "*/" in s:                      # single-line /** ... */
                pending = clean_javadoc(buf)
                buf = None
            continue
        mt = TYPE_RE.match(raw)
        if mt:
            current = dict(name=mt.group(2), java_kind=mt.group(1), package=pkg,
                           path=path, doc=pending or "", methods=[])
            types.append(current)
            pending = None
            continue
        mm = METHOD_RE.match(raw)
        if mm and current is not None:
            current["methods"].append(dict(name=mm.group(1), doc=pending or ""))
            pending = None
            continue
        if s == "" or s.startswith("@") or s.startswith("//"):
            continue                            # keep pending across blanks/annotations
        pending = None                          # any other code line drops pending
    return types


def _layer(parts):
    if any(p.endswith("-system") for p in parts):
        return "system"
    if "config" in parts:
        return "config"
    return "domain"


def collect_terms():
    """Every public type (class/record/interface/enum) from the domain, config
    and *-system layers is a glossary term. Noun/verb is a role a word plays in a
    Gherkin sentence, not a property of a class, so it is NOT recorded here."""
    terms = []
    for java in ROOT.glob("**/src/main/java/**/*.java"):
        if "/target/" in str(java):
            continue
        parts = java.parts
        if not ("domain" in parts or "config" in parts
                or any(p.endswith("-system") for p in parts)):
            continue
        layer = _layer(parts)
        for ty in parse_java(java):
            terms.append(dict(name=ty["name"], owner=ty["name"], layer=layer,
                              java_kind=ty["java_kind"], package=ty["package"],
                              path=ty["path"], doc=ty["doc"]))
    # de-dup by (name, package), preferring an entry that has a definition
    seen = {}
    for t in terms:
        key = (t["name"], t["package"])
        if key not in seen or (not seen[key]["doc"] and t["doc"]):
            seen[key] = t
    return sorted(seen.values(), key=lambda t: t["name"].lower())


def build_index(terms):
    """Simple class name -> term (first wins on collision)."""
    by_id = {}
    for t in terms:
        by_id.setdefault(t["name"], t)
    return by_id


# ---- Allure leg: match terms to unit tests by class name -----------------

def _label(data, name):
    for lab in data.get("labels", []):
        if lab.get("name") == name:
            return lab.get("value")
    return None


def load_allure():
    tests, seen, mod_dirs = [], set(), {}
    for rd in ROOT.glob("**/allure-results"):
        module = str(rd.relative_to(ROOT)).replace("/target/allure-results", "") \
                                          .replace("/allure-results", "")
        mod_dirs.setdefault(module, rd)
        for f in rd.glob("*.json"):
            if not f.name.endswith("-result.json") and "result" not in f.name:
                continue
            try:
                d = json.loads(f.read_text(encoding="utf-8", errors="replace"))
            except Exception:
                continue
            if "name" not in d:
                continue
            cls = _label(d, "testClass") or (d.get("fullName", "").rsplit(".", 1)[0])
            simple = cls.rsplit(".", 1)[-1] if cls else ""
            key = (module, cls, d["name"])
            if key in seen:
                continue
            seen.add(key)
            tests.append(dict(name=d["name"], cls=cls, simple=simple, module=module,
                              status=d.get("status", "unknown")))
    return tests, mod_dirs


def generate_allure_reports(mod_dirs, needed):
    """Generate a single-file Allure report per needed module; return module -> site-relative path."""
    reports = {}
    if not shutil.which("allure"):
        return reports
    for mod in sorted(needed):
        rd = mod_dirs.get(mod)
        if not rd or not any(Path(rd).glob("*.json")):
            continue
        safe = re.sub(r"[^A-Za-z0-9]+", "-", mod).strip("-")
        outdir = SITE / "allure" / safe
        try:
            subprocess.run(["allure", "generate", str(rd), "--single-file", "--clean",
                            "-o", str(outdir)], check=True, capture_output=True, timeout=180)
            reports[mod] = f"allure/{safe}/index.html"
        except Exception:
            continue
    return reports


def attach_allure(terms, tests):
    for t in terms:
        cover = [x for x in tests if x["simple"].startswith(t["owner"])]
        if cover:
            t["allure"] = sorted(cover, key=lambda x: (x["module"], x["name"]))


# ---- per-feature legend --------------------------------------------------

LEGEND_SECTION = re.compile(r"^\s*(Nouns|Verbs)\s*:\s*$")
LEGEND_ENTRY = re.compile(r"^\s*([A-Z0-9 ,*]+?)\s*->\s*([A-Za-z_][\w.]*)\s*$")
CAPS_RUN = re.compile(r"\b[A-Z]{2,}(?:\s+[A-Z]{2,})*\b")
STEP_KW = ("Given", "When", "Then", "And", "But", "*")


def parse_legend(text):
    """Return [{kind, exact:set, prefixes:[...], identifier, raw}] from the header legend."""
    entries, section = [], None
    for ln in text.splitlines():
        ms = LEGEND_SECTION.match(ln)
        if ms:
            section = ms.group(1).lower()[:-1]   # 'nouns' -> 'noun'
            continue
        if section is None:
            continue
        me = LEGEND_ENTRY.match(ln)
        if me:
            forms = [f.strip() for f in me.group(1).split(",") if f.strip()]
            entries.append(dict(
                kind=section,
                exact={f for f in forms if not f.endswith("*")},
                prefixes=[f[:-1] for f in forms if f.endswith("*")],
                identifier=me.group(2),
                raw=me.group(1).strip(),
            ))
        elif ln.strip():
            section = None                       # a non-entry line ends the legend block
    return entries


def caps_tokens(text):
    """CAPS words and multi-word runs in the step/narrative body (legend lines skipped)."""
    tokens, section = set(), None
    for ln in text.splitlines():
        if LEGEND_SECTION.match(ln):
            section = True
            continue
        if section:
            if LEGEND_ENTRY.match(ln) or not ln.strip():
                continue
            section = None
        for run in CAPS_RUN.findall(ln):
            tokens.add(run)
            tokens.update(run.split())
    return tokens


def entry_matches(entry, token):
    return token in entry["exact"] or any(token.startswith(p) for p in entry["prefixes"])


def feature_files():
    found = []
    for feat in list(ROOT.glob("*/specs/*.feature")) + list(
        ROOT.glob("**/src/test/resources/**/*.feature")
    ):
        if "/target/" not in str(feat):
            found.append(feat)
    return sorted(set(found), key=str)


def scan_features(terms):
    """Legend-driven linking. Returns (usage, errors) and annotates terms with used_in."""
    by_id = build_index(terms)
    usage, errors = {}, []
    for feat in feature_files():
        text = feat.read_text(encoding="utf-8", errors="replace")
        entries = parse_legend(text)
        if not entries:
            continue
        tokens = caps_tokens(text)
        used = {}
        for e in entries:
            term = by_id.get(e["identifier"])
            if term is None:
                errors.append(f"{rel(feat)}: legend '{e['raw']} -> {e['identifier']}' "
                              f"names no class in the code")
                continue
            if not any(entry_matches(e, tok) for tok in tokens):
                errors.append(f"{rel(feat)}: legend form '{e['raw']}' never appears "
                              f"in the steps (dead entry?)")
                continue
            used[(term["name"], term["package"])] = term
            term.setdefault("used_in", set()).add(feat)
        if used:
            usage[feat] = list(used.values())
    return usage, errors


def rel(path: Path) -> str:
    return str(path.relative_to(ROOT))


def term_anchor(t):
    return "c-" + re.sub(r"\W+", "-", f"{t['package']}.{t['name']}")


def feat_anchor(feat):
    return "feat-" + re.sub(r"[^A-Za-z0-9]+", "-", rel(feat))


# ---- Markdown (versioned changelog) --------------------------------------

def render_markdown(terms, usage):
    lines = ["# Ubiquitous-Language glossary", "",
             "_Generated from the domain, config and *-system layers by "
             "`build_glossary.py` — do not edit by hand._", "",
             f"{len(terms)} classes · {len(usage)} feature files tagged.", ""]
    for layer in ("domain", "config", "system"):
        group = [t for t in terms if t["layer"] == layer]
        if not group:
            continue
        lines += [f"## {layer.capitalize()} ({len(group)})", ""]
        for t in group:
            defn = first_sentence(t["doc"]) or "_(no Javadoc yet)_"
            used = ""
            if t.get("used_in"):
                used = " · _used in_ " + ", ".join(sorted(f.stem for f in t["used_in"]))
            lines.append(f"- **{t['name']}** — {defn} `{rel(t['path'])}`{used}")
        lines.append("")
    lines += ["## Features → terms", ""]
    for feat, ts in sorted(usage.items(), key=lambda kv: str(kv[0])):
        tags = " ".join(f"`{t['name']}`" for t in sorted(ts, key=lambda x: x["name"]))
        lines.append(f"- `{rel(feat)}` — {tags}")
    return "\n".join(lines).rstrip() + "\n"


# ---- HTML (interactive site) ---------------------------------------------

def _worst_emoji(tests):
    for st in ("failed", "broken", "skipped", "passed"):
        if any(t["status"] == st for t in tests):
            return STATUS_EMOJI.get(st, "")
    return STATUS_EMOJI["unknown"]


def link_terms(text, matchers, base):
    """Escape a step/description line and hyperlink its CAPS terms to the glossary page."""
    esc = html.escape(text)

    def href(term):
        return f'{base}#{term_anchor(term)}'

    def link_word(w):
        for e, term in matchers:
            if entry_matches(e, w):
                return f'<a href="{href(term)}">{w}</a>'
        return w

    def repl(m):
        run = m.group(0)
        for e, term in matchers:                     # whole multi-word form, e.g. SESSION FAMILY
            if entry_matches(e, run):
                return f'<a href="{href(term)}">{run}</a>'
        return " ".join(link_word(w) for w in run.split())  # else link each CAPS word

    return CAPS_RUN.sub(repl, esc)


def render_feature(feat, by_id, base="index.html"):
    text = feat.read_text(encoding="utf-8", errors="replace")
    matchers = [(e, by_id[e["identifier"]]) for e in parse_legend(text)
                if e["identifier"] in by_id]
    out = [f"<div class=feature id={feat_anchor(feat)}>"]
    in_legend, in_desc, tbl = False, False, []

    def flush_table():
        if not tbl:
            return
        rows = "".join(
            "<tr>" + "".join(f"<{'th' if i == 0 else 'td'}>{html.escape(c)}</"
                             f"{'th' if i == 0 else 'td'}>" for c in r) + "</tr>"
            for i, r in enumerate(tbl))
        out.append(f"<table class=egt>{rows}</table>")
        tbl.clear()

    for raw in text.splitlines():
        s = raw.strip()
        if LEGEND_SECTION.match(raw):
            in_legend = True
            continue
        if in_legend:                                   # hide the legend from the view
            if LEGEND_ENTRY.match(raw) or not s:
                continue
            in_legend = False
        if s.startswith("|"):
            tbl.append([c.strip() for c in s.strip().strip("|").split("|")])
            continue
        flush_table()
        if not s:
            continue
        if s.startswith("Feature:"):
            out.append(f"<h3>{html.escape(s[8:].strip())}</h3>")
            in_desc = True
        elif s.startswith("Rule:"):
            out.append(f"<h4>{html.escape(s[5:].strip())}</h4>")
            in_desc = False
        elif s.startswith(("Scenario Outline:", "Scenario:", "Example:", "Background:")):
            kw, _, rest = s.partition(":")
            out.append(f"<div class=scen><b>{html.escape(kw)}:</b> "
                       f"{html.escape(rest.strip())}</div>")
            in_desc = False
        elif s.startswith("Examples:"):
            out.append("<div class=exlabel>Examples:</div>")
        elif any(s.startswith(k + " ") for k in STEP_KW):
            kw, _, rest = s.partition(" ")
            out.append(f"<div class=step><span class=kw>{html.escape(kw)}</span> "
                       f"{link_terms(rest, matchers, base)}</div>")
        elif in_desc:
            out.append(f"<p class=fdesc>{link_terms(s, matchers, base)}</p>")
    flush_table()
    out.append("</div>")
    return "\n".join(out)


CSS = """
body{font:15px/1.6 system-ui,sans-serif;max-width:940px;margin:0 auto 3rem;padding:0 1rem;color:#222}
.nav{background:#0d1b2a;margin:0 -1rem 1.4rem;padding:.7rem 1rem}
.nav a{color:#cfe1ff;margin-right:1.1rem;font-weight:600}.nav a.here{color:#fff;text-decoration:underline}
.nav select{font:inherit;background:#1b2a3d;color:#fff;border:1px solid #33506f;border-radius:4px;padding:.15rem .4rem}
h1{margin:.2rem 0 .8rem}h2{margin-top:2rem;border-bottom:1px solid #ddd;padding-bottom:.2rem}
code{background:#f4f4f4;padding:1px 4px;border-radius:3px}
a{color:#0a58ca;text-decoration:none}a:hover{text-decoration:underline}
.term{margin:.8rem 0;padding:.6rem .9rem;border:1px solid #e6ebf3;border-left:4px solid #0a58ca;border-radius:5px;background:#fbfcff}
.term .hd b{font-size:1.08em}
.kindtag{font-size:.68em;text-transform:uppercase;letter-spacing:.05em;color:#fff;background:#8250df;border-radius:3px;padding:0 .35rem;margin-left:.45rem;vertical-align:middle}
.kindtag.domain{background:#0a7c4a}.kindtag.config{background:#0a6b7c}.kindtag.system{background:#8250df}
.caps{color:#0a58ca;font-weight:600;margin-left:.4rem}
.pkg{color:#999;font-size:.82em}
.def{margin:.35rem 0}
.legs{margin-top:.3rem;font-size:.9em}.legs .k{color:#555;font-weight:600;display:inline-block;min-width:6em}
.tag{display:inline-block;background:#eef;border:1px solid #cce;border-radius:12px;padding:0 .55rem;margin:1px;font-size:.85em}
.src{color:#888;font-size:.82em}
.ctx{background:#f6f8fb;border:1px solid #e3e9f2;border-radius:6px;padding:.5rem .8rem;margin:1rem 0}
.ctx .k{color:#555;font-weight:600;display:inline-block;min-width:5em}
.feature h3{margin:.3rem 0;color:#222}.feature h4{margin:1rem 0 .2rem;color:#555;font-size:1em}
.fdesc{color:#666;margin:.2rem 0}
.scen{margin:.8rem 0 .2rem;color:#111;font-weight:600}
.step{margin:.12rem 0 .12rem 1rem}.step .kw{color:#8250df;font-weight:600;display:inline-block;min-width:3.4em}
.exlabel{margin:.3rem 0 0 1rem;color:#777;font-size:.9em}
.egt{border-collapse:collapse;margin:.2rem 0 .6rem 1rem;font-size:.88em}
.egt td,.egt th{border:1px solid #ddd;padding:1px 8px}.egt th{background:#f1f3f7}
.empty{color:#aaa}
.filter{width:100%;box-sizing:border-box;padding:.45rem .6rem;margin:.4rem 0 1rem;
 border:1px solid #ccd;border-radius:5px;font:inherit}
"""


def slug(feat):
    return re.sub(r"[^a-z0-9]+", "-", feat.stem.lower()).strip("-")


def feature_title(text):
    for ln in text.splitlines():
        if ln.strip().startswith("Feature:"):
            return ln.split(":", 1)[1].strip()
    return "(feature)"


def nav_html(feature_pages, current):
    gl = "here" if current == "glossary" else ""
    opts = ['<option value="">Use cases ▾</option>']
    for sl, title, _ in feature_pages:
        sel = " selected" if current == sl else ""
        opts.append(f'<option value="feature-{sl}.html"{sel}>{html.escape(title)}</option>')
    select = ('<select onchange="if(this.value)location.href=this.value">'
              + "".join(opts) + "</select>")
    return f'<div class=nav><a href="index.html" class="{gl}">Glossary</a>{select}</div>'


def page(title, nav, body):
    return (f"<!doctype html><meta charset=utf-8><title>{html.escape(title)}</title>"
            f"<style>{CSS}</style>{nav}{body}\n")


def allure_leg(term, reports):
    if not term.get("allure"):
        return '<div class=legs><span class=k>Tested by</span> <span class=empty>—</span></div>'
    bymod = {}
    for a in term["allure"]:
        bymod.setdefault(a["module"], []).append(a)
    parts = []
    for mod, tests in sorted(bymod.items()):
        byc = {}
        for a in tests:
            byc.setdefault(a["simple"] or mod, []).append(a)
        chips = " ".join(f"<span class=tag>{_worst_emoji(v)} {html.escape(cls)} ({len(v)})</span>"
                         for cls, v in sorted(byc.items()))
        rep = reports.get(mod)
        link = f" <a href='{rep}'>open report ↗</a>" if rep else ""
        parts.append(f"{chips} <span class=pkg>{html.escape(mod)}</span>{link}")
    return "<div class=legs><span class=k>Tested by</span> " + " · ".join(parts) + "</div>"


def render_glossary_page(terms, usage, reports, feature_pages):
    slug_of = {f: sl for sl, _, f in feature_pages}
    title_of = {f: t for _, t, f in feature_pages}
    body = ["<h1>Ubiquitous-Language glossary</h1>",
            f"<p>All {len(terms)} classes from the domain, config and *-system layers, "
            "alphabetical. A class used in a feature or covered by unit tests links out.</p>",
            '<input id=q class=filter placeholder="Filter classes…" autocomplete=off>']
    for t in terms:                                    # already sorted by name
        badge = f"<span class='kindtag {t['layer']}'>{t['layer']}</span>"
        doc = html.escape(first_sentence(t["doc"]) or "") \
            or "<span class=empty>(no Javadoc yet)</span>"
        src = f"{SITE_TO_ROOT}/{rel(t['path'])}"
        legs = ""
        if t.get("used_in"):
            cuke = "".join(
                f"<a class=tag href='feature-{slug_of[p]}.html'>{html.escape(title_of[p])}</a>"
                for p in sorted(t["used_in"], key=str) if p in slug_of)
            legs += f"<div class=legs><span class=k>Used in</span> {cuke}</div>"
        if t.get("allure"):
            legs += allure_leg(t, reports)
        body.append(
            f"<div class=term id={term_anchor(t)}>"
            f"<div class=hd><b>{html.escape(t['name'])}</b>{badge}"
            f"<span class=caps>{html.escape(spaced_caps(t['name']))}</span></div>"
            f"<div class=pkg>{html.escape(t['package'])}</div>"
            f"<div class=def>{doc}</div>"
            f"<a class=src href='{src}'>{html.escape(rel(t['path']))}</a>"
            f"{legs}</div>")
    body.append("<script>const q=document.getElementById('q');"
                "q.addEventListener('input',()=>{const v=q.value.toLowerCase();"
                "document.querySelectorAll('.term').forEach(t=>{"
                "t.style.display=t.textContent.toLowerCase().includes(v)?'':'none';});});</script>")
    return page("UL glossary", nav_html(feature_pages, "glossary"), "\n".join(body))


def render_feature_page(feat, terms_used, by_id, reports, feature_pages):
    title = next(t for _, t, f in feature_pages if f == feat)
    chips = "".join(
        f"<a class=tag href='index.html#{term_anchor(t)}'>{html.escape(t['name'])}</a>"
        for t in sorted(terms_used, key=lambda x: x["name"]))
    rep_links = []
    for t in sorted(terms_used, key=lambda x: x["name"]):
        mods = sorted({a["module"] for a in t.get("allure", []) if reports.get(a["module"])})
        for mod in mods:
            rep_links.append(f"<a href='{reports[mod]}'>{html.escape(t['name'])} ↗</a>")
    ctx = ("<div class=ctx>"
           f"<div><span class=k>Terms</span> {chips or '—'}</div>"
           f"<div><span class=k>Source</span> "
           f"<a class=src href='{SITE_TO_ROOT}/{rel(feat)}'>{html.escape(rel(feat))}</a></div>"
           + (f"<div><span class=k>Allure</span> {' · '.join(rep_links)}</div>" if rep_links else "")
           + "</div>")
    body = f"<h1>{html.escape(title)}</h1>{ctx}{render_feature(feat, by_id, base='index.html')}"
    return page(f"Feature: {title}", nav_html(feature_pages, slug(feat)), body)


def build_site(terms, usage, reports):
    feature_pages = sorted(
        ((slug(f), feature_title(f.read_text(encoding="utf-8", errors="replace")), f)
         for f in usage), key=lambda x: x[0])
    by_id = build_index(terms)
    SITE.mkdir(parents=True, exist_ok=True)
    (SITE / "index.html").write_text(
        render_glossary_page(terms, usage, reports, feature_pages), encoding="utf-8")
    for sl, _, feat in feature_pages:
        (SITE / f"feature-{sl}.html").write_text(
            render_feature_page(feat, usage[feat], by_id, reports, feature_pages), encoding="utf-8")
    return feature_pages


def main():
    terms = collect_terms()
    usage, errors = scan_features(terms)
    tests, mod_dirs = load_allure()
    attach_allure(terms, tests)
    needed = {a["module"] for t in terms if t.get("used_in") for a in t.get("allure", [])}
    reports = generate_allure_reports(mod_dirs, needed)
    OUT_MD.parent.mkdir(parents=True, exist_ok=True)
    OUT_MD.write_text(render_markdown(terms, usage), encoding="utf-8")
    pages = build_site(terms, usage, reports)
    tagged = [t for t in terms if t.get("used_in")]
    by_layer = {la: sum(t["layer"] == la for t in terms) for la in ("domain", "config", "system")}
    print(f"classes: {len(terms)} "
          f"(domain {by_layer['domain']}, config {by_layer['config']}, system {by_layer['system']})")
    print(f"pages: index + {len(pages)} feature page(s); classes in play: {len(tagged)}; "
          f"Allure reports: {len(reports)}; "
          f"classes with Allure: {sum(1 for t in tagged if t.get('allure'))}")
    for e in errors:
        print(f"  WARN {e}")
    print(f"wrote {rel(OUT_MD)} and {rel(SITE)}/")


if __name__ == "__main__":
    main()
