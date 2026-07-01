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
from pathlib import Path

ROOT = Path(__file__).resolve().parent
OUT_MD = ROOT / "docs" / "glossary" / "glossary.md"
OUT_HTML = ROOT / "docs" / "glossary" / "site" / "index.html"
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


def collect_terms():
    terms = []
    for java in ROOT.glob("**/src/main/java/**/*.java"):
        if "/target/" in str(java):
            continue
        parts = java.parts
        is_system = "security-system" in parts
        is_domain_config = "domain" in parts or "config" in parts
        if not (is_system or is_domain_config):
            continue
        for ty in parse_java(java):
            has_execute = any(m["name"] == "execute" for m in ty["methods"])
            if is_system:
                # Use-cases are command classes named as verbs; execute() is plumbing.
                if has_execute:
                    terms.append(dict(kind="verb", name=ty["name"], owner=ty["name"],
                                      java_kind="command", package=ty["package"],
                                      path=ty["path"], doc=ty["doc"]))
            elif is_domain_config:
                terms.append(dict(kind="noun", name=ty["name"], owner=ty["name"],
                                  java_kind=ty["java_kind"], package=ty["package"],
                                  path=ty["path"], doc=ty["doc"]))
                for m in ty["methods"]:
                    if PREDICATE_RE.match(m["name"]):
                        terms.append(dict(kind="verb", name=m["name"], owner=ty["name"],
                                          java_kind="method", package=ty["package"],
                                          path=ty["path"], doc=m["doc"]))
    # de-dup by (kind, owner, name), preferring an entry that has a definition
    seen = {}
    for t in terms:
        key = (t["kind"], t["owner"], t["name"])
        if key not in seen or (not seen[key]["doc"] and t["doc"]):
            seen[key] = t
    return sorted(seen.values(), key=lambda t: (t["kind"], t["owner"].lower(), t["name"].lower()))


def build_index(terms):
    """Java identifier -> term. Types/commands by simple name; methods also by Owner.method."""
    by_id = {}
    for t in terms:
        if t["kind"] == "noun" or t["java_kind"] == "command":
            by_id.setdefault(t["name"], t)
        else:  # predicate verb
            by_id[f"{t['owner']}.{t['name']}"] = t
            by_id.setdefault(t["name"], t)
    return by_id


# ---- Allure leg: match terms to unit tests by class name -----------------

def _label(data, name):
    for lab in data.get("labels", []):
        if lab.get("name") == name:
            return lab.get("value")
    return None


def load_allure():
    tests, seen = [], set()
    for rd in ROOT.glob("**/allure-results"):
        module = str(rd.relative_to(ROOT)).replace("/target/allure-results", "") \
                                          .replace("/allure-results", "")
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
    return tests


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
                              f"names no term in the code")
                continue
            if term["kind"] != e["kind"]:
                errors.append(f"{rel(feat)}: '{e['identifier']}' is a {term['kind']} "
                              f"but declared under {e['kind'].capitalize()}s")
            if not any(entry_matches(e, tok) for tok in tokens):
                errors.append(f"{rel(feat)}: legend form '{e['raw']}' never appears "
                              f"in the steps (dead entry?)")
                continue
            used[(term["kind"], term["owner"], term["name"])] = term
            term.setdefault("used_in", set()).add(feat)
        if used:
            usage[feat] = list(used.values())
    return usage, errors


def rel(path: Path) -> str:
    return str(path.relative_to(ROOT))


def term_anchor(t):
    return f"{t['kind']}-{t['owner']}-{t['name']}"


def feat_anchor(feat):
    return "feat-" + re.sub(r"[^A-Za-z0-9]+", "-", rel(feat))


# ---- Markdown (versioned changelog) --------------------------------------

def render_markdown(terms, usage):
    lines = ["# Ubiquitous-Language glossary", "",
             "_Generated from `domain`/`config`/`security-system` code by "
             "`build_glossary.py` — do not edit by hand._", "",
             f"{sum(t['kind']=='noun' for t in terms)} nouns · "
             f"{sum(t['kind']=='verb' for t in terms)} verbs · "
             f"{len(usage)} feature files tagged.", ""]
    for kind, title in (("noun", "Nouns (types)"), ("verb", "Verbs (operations)")):
        lines += [f"## {title}", ""]
        for t in (x for x in terms if x["kind"] == kind and x.get("used_in")):
            head = f"### {t['name']}" + (f" · `{t['owner']}`" if kind == "verb" else "")
            lines.append(head)
            lines.append(f"*CAPS:* `{spaced_caps(t['name'])}`  ·  *source:* `{rel(t['path'])}`")
            lines.append("")
            lines.append(first_sentence(t["doc"]) or "_(no Javadoc yet)_")
            lines.append("")
            lines.append("*Cucumber:* " + ", ".join(
                f"`{u}`" for u in sorted(rel(p) for p in t["used_in"])))
            if t.get("allure"):
                mods = sorted({a["module"] for a in t["allure"]})
                lines.append(f"*Allure:* {len(t['allure'])} test(s) in {', '.join(mods)}")
            lines.append("")
    lines += ["## Features → tags", ""]
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


def link_terms(text, matchers):
    """Escape a step/description line and hyperlink its CAPS terms to the glossary."""
    esc = html.escape(text)

    def link_word(w):
        for e, term in matchers:
            if entry_matches(e, w):
                return f'<a href="#{term_anchor(term)}">{w}</a>'
        return w

    def repl(m):
        run = m.group(0)
        for e, term in matchers:                     # whole multi-word form, e.g. SESSION FAMILY
            if entry_matches(e, run):
                return f'<a href="#{term_anchor(term)}">{run}</a>'
        return " ".join(link_word(w) for w in run.split())  # else link each CAPS word

    return CAPS_RUN.sub(repl, esc)


def render_feature(feat, by_id):
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
                       f"{link_terms(rest, matchers)}</div>")
        elif in_desc:
            out.append(f"<p class=fdesc>{link_terms(s, matchers)}</p>")
    flush_table()
    out.append("</div>")
    return "\n".join(out)


CSS = """
body{font:15px/1.6 system-ui,sans-serif;max-width:960px;margin:2rem auto;padding:0 1rem;color:#222}
h1{border-bottom:2px solid #444}h2{margin-top:2rem;border-bottom:1px solid #ddd}
code{background:#f4f4f4;padding:1px 4px;border-radius:3px}
a{color:#0a58ca;text-decoration:none}a:hover{text-decoration:underline}
.term{margin:.6rem 0;padding:.5rem .7rem;border-left:3px solid #0a58ca;background:#fbfcff}
.term b{font-size:1.05em}.caps{color:#0a58ca;font-weight:600;margin-left:.3rem}
.src{color:#888;font-size:.85em}
.legs{margin-top:.3rem;font-size:.9em}.legs .k{color:#555;font-weight:600}
.tag{display:inline-block;background:#eef;border:1px solid #cce;border-radius:12px;
 padding:0 .5rem;margin:1px;font-size:.85em}
.feature{border:1px solid #e3e3e3;border-radius:6px;padding:.4rem 1rem;margin:1rem 0;background:#fff}
.feature h3{margin:.3rem 0;color:#333}.feature h4{margin:.8rem 0 .2rem;color:#555}
.fdesc{color:#666;margin:.2rem 0}
.scen{margin:.6rem 0 .2rem;color:#111}
.step{margin:.1rem 0 .1rem 1rem}.step .kw{color:#8250df;font-weight:600;display:inline-block;min-width:3.2em}
.exlabel{margin:.3rem 0 0 1rem;color:#777;font-size:.9em}
.egt{border-collapse:collapse;margin:.2rem 0 .6rem 1rem;font-size:.88em}
.egt td,.egt th{border:1px solid #ddd;padding:1px 8px}.egt th{background:#f4f4f7}
"""


def render_html(terms, usage):
    by_id = build_index(terms)
    out = [f"<!doctype html><meta charset=utf-8><title>UL glossary</title><style>{CSS}</style>",
           "<h1>Ubiquitous-Language glossary</h1>",
           "<p>Jump: <b>Cucumber ↔ glossary ↔ Allure</b>. Terms in features "
           "link to the glossary; each term links back to its features and unit tests.</p>"]

    for kind, title in (("noun", "Nouns (types)"), ("verb", "Verbs (operations)")):
        out.append(f"<h2>{title}</h2>")
        for t in (x for x in terms if x["kind"] == kind and x.get("used_in")):
            owner = f" · <code>{html.escape(t['owner'])}</code>" if kind == "verb" else ""
            doc = html.escape(first_sentence(t["doc"]) or "(no Javadoc yet)")
            src = f"{SITE_TO_ROOT}/{rel(t['path'])}"
            cuke = "".join(
                f"<a class=tag href='#{feat_anchor(p)}'>{html.escape(rel(p))}</a>"
                for p in sorted(t["used_in"], key=str))
            legs = [f"<div class=legs><span class=k>Cucumber:</span> {cuke}</div>"]
            if t.get("allure"):
                groups = {}
                for a in t["allure"]:
                    groups.setdefault(a["simple"] or a["module"], []).append(a)
                al = " ".join(
                    f"<span class=tag>{_worst_emoji(v)} {html.escape(cls)} ({len(v)})</span>"
                    for cls, v in sorted(groups.items()))
                legs.append(f"<div class=legs><span class=k>Allure:</span> {al} "
                            f"<a href='{SITE_TO_ROOT}/allure-summary.md'>(report)</a></div>")
            out.append(
                f"<div class=term id={term_anchor(t)}><b>{html.escape(t['name'])}</b>{owner}"
                f"<span class=caps>{html.escape(spaced_caps(t['name']))}</span><br>{doc}<br>"
                f"<a class=src href='{src}'>{html.escape(rel(t['path']))}</a>"
                + "".join(legs) + "</div>")

    out.append("<h2>Features (Cucumber)</h2>")
    for feat in sorted(usage, key=str):
        out.append(render_feature(feat, by_id))
    return "\n".join(out) + "\n"


def main():
    terms = collect_terms()
    usage, errors = scan_features(terms)
    attach_allure(terms, load_allure())
    OUT_MD.parent.mkdir(parents=True, exist_ok=True)
    OUT_HTML.parent.mkdir(parents=True, exist_ok=True)
    OUT_MD.write_text(render_markdown(terms, usage), encoding="utf-8")
    OUT_HTML.write_text(render_html(terms, usage), encoding="utf-8")
    tagged_terms = [t for t in terms if t.get("used_in")]
    print(f"terms: {len(terms)} "
          f"({sum(t['kind']=='noun' for t in terms)} nouns, "
          f"{sum(t['kind']=='verb' for t in terms)} verbs)")
    print(f"features tagged: {len(usage)}  terms in play: {len(tagged_terms)}  "
          f"with Allure evidence: {sum(1 for t in tagged_terms if t.get('allure'))}")
    for e in errors:
        print(f"  WARN {e}")
    print(f"wrote {rel(OUT_MD)} and {rel(OUT_HTML)}")


if __name__ == "__main__":
    main()
