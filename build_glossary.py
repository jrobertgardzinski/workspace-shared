#!/usr/bin/env python3
"""Build the Ubiquitous-Language glossary from domain/config/security-system code.

Source of truth: the code. A *noun* term is a public type (class/record/
interface/enum) under a `domain` or `config` package; a *verb* term is either a
use-case command class in `security-system` (a class with an `execute()` method
— the class name carries the verb, `execute` is plumbing) or a domain predicate
method (`is/has/can…`). Each term's definition is its Javadoc.

Cucumber `.feature` files do not carry hand-written tags. Instead each feature
declares, in its description header, a per-feature legend:

    Nouns:
      USER  -> User
      EMAIL -> Email
    Verbs:
      REGISTER, REGISTERS, REGISTERED, REGISTRATION -> Register

The left side lists the CAPS surface forms as they appear in the steps (a
trailing `*` is a prefix wildcard, e.g. `REGIST*`); the right side is the exact
Java identifier (`User`, `Register`, or `Owner.method`). The generator resolves
each identifier against the code glossary (and errors if it does not exist),
then turns nouns/verbs into navigable tags: term -> features that use it, and
feature -> its terms.

Outputs both:
  - docs/glossary/glossary.md      (versioned; the readable changelog)
  - docs/glossary/site/index.html  (generated; navigation, git-ignored)
"""

import html
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent
OUT_MD = ROOT / "docs" / "glossary" / "glossary.md"
OUT_HTML = ROOT / "docs" / "glossary" / "site" / "index.html"

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
    joined = re.sub(r"\s+", " ", joined).strip()
    return joined


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


# ---- per-feature legend --------------------------------------------------

LEGEND_SECTION = re.compile(r"^\s*(Nouns|Verbs)\s*:\s*$")
LEGEND_ENTRY = re.compile(r"^\s*([A-Z0-9 ,*]+?)\s*->\s*([A-Za-z_][\w.]*)\s*$")
CAPS_RUN = re.compile(r"\b[A-Z]{2,}(?:\s+[A-Z]{2,})*\b")


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
    seen = []
    for feat in list(ROOT.glob("*/specs/*.feature")) + list(
        ROOT.glob("**/src/test/resources/**/*.feature")
    ):
        if "/target/" not in str(feat):
            seen.append(feat)
    return sorted(set(seen), key=str)


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


def render_markdown(terms, usage):
    lines = ["# Ubiquitous-Language glossary", "",
             "_Generated from `domain`/`config`/`security-system` code by "
             "`build_glossary.py` — do not edit by hand._", "",
             f"{sum(t['kind']=='noun' for t in terms)} nouns · "
             f"{sum(t['kind']=='verb' for t in terms)} verbs · "
             f"{len(usage)} feature files tagged.", ""]
    for kind, title in (("noun", "Nouns (types)"), ("verb", "Verbs (operations)")):
        lines += [f"## {title}", ""]
        for t in (x for x in terms if x["kind"] == kind):
            if not t.get("used_in"):
                continue                          # skeleton: list only terms in play
            head = f"### {t['name']}" + (f" · `{t['owner']}`" if kind == "verb" else "")
            lines.append(head)
            lines.append(f"*CAPS:* `{spaced_caps(t['name'])}`  ·  *source:* `{rel(t['path'])}`")
            lines.append("")
            lines.append(first_sentence(t["doc"]) or "_(no Javadoc yet)_")
            lines.append("")
            lines.append("*Tagged in:* " + ", ".join(
                f"`{u}`" for u in sorted(rel(p) for p in t["used_in"])))
            lines.append("")
    lines += ["## Features → tags", ""]
    for feat, ts in sorted(usage.items(), key=lambda kv: str(kv[0])):
        tags = " ".join(f"`{t['name']}`" for t in sorted(ts, key=lambda x: x["name"]))
        lines.append(f"- `{rel(feat)}` — {tags}")
    return "\n".join(lines).rstrip() + "\n"


def render_html(terms, usage):
    def anchor(t):
        return f"{t['kind']}-{t['owner']}-{t['name']}"
    css = ("body{font:15px/1.5 system-ui,sans-serif;max-width:900px;margin:2rem auto;"
           "padding:0 1rem;color:#222}h1{border-bottom:2px solid #444}"
           "code{background:#f4f4f4;padding:1px 4px;border-radius:3px}"
           ".term{margin:.6rem 0;padding:.4rem .6rem;border-left:3px solid #ccc}"
           ".caps{color:#0a58ca;font-weight:600}.src{color:#888;font-size:.85em}"
           ".tag{display:inline-block;background:#eef;border:1px solid #cce;border-radius:12px;"
           "padding:0 .5rem;margin:1px;font-size:.85em}"
           "a{color:#0a58ca;text-decoration:none}a:hover{text-decoration:underline}")
    out = [f"<!doctype html><meta charset=utf-8><title>UL glossary</title><style>{css}</style>",
           "<h1>Ubiquitous-Language glossary</h1>"]
    for kind, title in (("noun", "Nouns (types)"), ("verb", "Verbs (operations)")):
        out.append(f"<h2>{title}</h2>")
        for t in (x for x in terms if x["kind"] == kind and x.get("used_in")):
            owner = f" · <code>{html.escape(t['owner'])}</code>" if kind == "verb" else ""
            doc = html.escape(first_sentence(t["doc"]) or "(no Javadoc yet)")
            tags = "".join(f"<a class=tag href='#feat-{i}'>{html.escape(rel(p))}</a>"
                           for i, p in enumerate(sorted(t["used_in"], key=str)))
            out.append(
                f"<div class=term id={anchor(t)}><b>{html.escape(t['name'])}</b>{owner} "
                f"<span class=caps>{html.escape(spaced_caps(t['name']))}</span><br>{doc}<br>"
                f"<span class=src>{html.escape(rel(t['path']))}</span><br>{tags}</div>")
    out.append("<h2>Features → tags</h2>")
    feats = sorted(usage.items(), key=lambda kv: str(kv[0]))
    index = {p: i for i, (p, _) in enumerate(feats)}
    for feat, ts in feats:
        chips = "".join(f"<a class=tag href='#{anchor(t)}'>{html.escape(t['name'])}</a>"
                        for t in sorted(ts, key=lambda x: x["name"]))
        out.append(f"<p id=feat-{index[feat]}><code>{html.escape(rel(feat))}</code><br>{chips}</p>")
    return "\n".join(out) + "\n"


def main():
    terms = collect_terms()
    usage, errors = scan_features(terms)
    OUT_MD.parent.mkdir(parents=True, exist_ok=True)
    OUT_HTML.parent.mkdir(parents=True, exist_ok=True)
    OUT_MD.write_text(render_markdown(terms, usage), encoding="utf-8")
    OUT_HTML.write_text(render_html(terms, usage), encoding="utf-8")
    print(f"terms: {len(terms)} "
          f"({sum(t['kind']=='noun' for t in terms)} nouns, "
          f"{sum(t['kind']=='verb' for t in terms)} verbs)")
    print(f"features tagged: {len(usage)}  "
          f"tag-usages: {sum(len(v) for v in usage.values())}")
    for e in errors:
        print(f"  WARN {e}")
    print(f"wrote {rel(OUT_MD)} and {rel(OUT_HTML)}")


if __name__ == "__main__":
    main()
