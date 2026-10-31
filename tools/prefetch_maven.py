#!/usr/bin/env python3
"""Mirror a Maven dependency closure into a local file repo using curl only.
Java cannot move data on this VM's network; curl can. Gradle later builds
fully offline from the mirrored repo (POM + Gradle module metadata + files).
"""
import json, os, re, subprocess, sys
import xml.etree.ElementTree as ET

REPO_DIR = os.path.expanduser("~/local-maven")
GOOGLE = "https://dl.google.com/dl/android/maven2"
CENTRAL = "https://repo.maven.apache.org/maven2"
NS = {"m": "http://maven.apache.org/POM/4.0.0"}

os.makedirs(REPO_DIR, exist_ok=True)
log = open("/tmp/prefetch.log", "a", buffering=1)

def say(msg):
    print(msg, flush=True); log.write(msg + "\n")

def fetch(url, dest):
    """curl url to dest; return True on HTTP 200."""
    if os.path.exists(dest) and os.path.getsize(dest) > 0:
        return True
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    r = subprocess.run(["curl", "-sL", "-o", dest + ".tmp", "-w", "%{http_code}",
                        "--max-time", "120", url], capture_output=True, text=True)
    code = r.stdout.strip()
    if code == "200" and os.path.exists(dest + ".tmp"):
        os.replace(dest + ".tmp", dest)
        return True
    try: os.unlink(dest + ".tmp")
    except OSError: pass
    return False

def gpath(g, a, v):
    return f"{g.replace('.', '/')}/{a}/{v}"

_bases_cache = {}
def bases_for(g, a):
    key = (g, a)
    if key in _bases_cache: return _bases_cache[key]
    order = [GOOGLE, CENTRAL] if (g.startswith(("com.android", "androidx", "com.google"))) else [CENTRAL, GOOGLE]
    _bases_cache[key] = order
    return order

def fetch_artifact_file(g, a, v, filename):
    """Fetch one file of a GAV from whichever repo has it. Return repo base or None."""
    rel = f"{gpath(g, a, v)}/{filename}"
    dest = os.path.join(REPO_DIR, rel)
    if os.path.exists(dest) and os.path.getsize(dest) > 0:
        return "cached"
    for base in bases_for(g, a):
        if fetch(f"{base}/{rel}", dest):
            return base
    return None

def resolve_range(g, a, spec):
    """Resolve a version range / RELEASE / LATEST using maven-metadata.xml."""
    rel = f"{g.replace('.', '/')}/{a}/maven-metadata.xml"
    dest = os.path.join(REPO_DIR, rel)
    got = None
    for base in bases_for(g, a):
        if fetch(f"{base}/{rel}", dest): got = base; break
    if not got: return None
    try:
        root = ET.parse(dest).getroot()
    except Exception:
        return None
    versions = [v.text for v in root.iter() if v.tag.endswith("version") and v.text]
    versions = [v for v in versions if "SNAPSHOT" not in v and "-" not in v.split(".")[-1] or True]
    clean = [v for v in versions if not any(s in v for s in ("alpha", "beta", "rc", "SNAPSHOT", "-M"))]
    pool = clean or versions
    if not pool: return None
    def vkey(v):
        parts = re.split(r"[.\-]", v)
        out = []
        for p in parts:
            out.append((0, int(p), "") if p.isdigit() else (1, 0, p))
        return out
    spec = spec.strip()
    if spec in ("RELEASE", "LATEST", "+"):
        return sorted(pool, key=vkey)[-1]
    m = re.match(r"^[\[(]([^,]*)(?:,([^]\)]*))?[\]\)]$", spec)
    if m:
        lo, hi = m.group(1).strip(), (m.group(2) or "").strip()
        inc_lo = spec.startswith("[")
        cands = []
        for v in pool:
            if lo and (vkey(v) < vkey(lo) or (vkey(v) == vkey(lo) and not inc_lo)): continue
            if hi and vkey(v) >= vkey(hi): continue
            cands.append(v)
        if cands: return sorted(cands, key=vkey)[-1]
        if lo: return lo
        return None
    return spec

# ---------- POM model ----------
class Pom:
    def __init__(self):
        self.group = self.artifact = self.version = None
        self.packaging = "jar"
        self.parent = None          # (g,a,v)
        self.props = {}
        self.dep_mgmt = {}          # (g,a) -> version
        self.bom_imports = []       # (g,a,v)
        self.deps = []              # dicts g,a,v,scope,type,optional,classifier

def parse_pom(path):
    pom = Pom()
    try:
        root = ET.parse(path).getroot()
    except Exception as e:
        say(f"  !! pom parse fail {path}: {e}")
        return pom
    def text(el, tag):
        c = el.find(f"m:{tag}", NS)
        return c.text.strip() if c is not None and c.text else None
    par = root.find("m:parent", NS)
    if par is not None:
        pom.parent = (text(par, "groupId"), text(par, "artifactId"), text(par, "version"))
    pom.group = text(root, "groupId") or (pom.parent[0] if pom.parent else None)
    pom.artifact = text(root, "artifactId")
    pom.version = text(root, "version") or (pom.parent[2] if pom.parent else None)
    pom.packaging = text(root, "packaging") or "jar"
    props_el = root.find("m:properties", NS)
    if props_el is not None:
        for child in props_el:
            tag = child.tag.split("}")[-1]
            pom.props[tag] = (child.text or "").strip()
    dm = root.find("m:dependencyManagement/m:dependencies", NS)
    if dm is not None:
        for d in dm.findall("m:dependency", NS):
            g, a, v = text(d, "groupId"), text(d, "artifactId"), text(d, "version")
            scope, typ = text(d, "scope"), text(d, "type") or "jar"
            if scope == "import" and typ == "pom" and v:
                pom.bom_imports.append((g, a, v))
            elif v:
                pom.dep_mgmt[(g, a)] = v
    deps_el = root.find("m:dependencies", NS)
    if deps_el is not None:
        for d in deps_el.findall("m:dependency", NS):
            pom.deps.append({
                "g": text(d, "groupId"), "a": text(d, "artifactId"),
                "v": text(d, "version"), "scope": text(d, "scope") or "compile",
                "type": text(d, "type") or "jar",
                "optional": (text(d, "optional") == "true"),
                "classifier": text(d, "classifier"),
            })
    return pom

def interp(s, ctx):
    if s is None: return None
    def repl(m):
        k = m.group(1)
        return ctx.get(k, m.group(0))
    prev = None
    while prev != s:
        prev = s
        s = re.sub(r"\$\{([^}]+)\}", repl, s)
    return s

seen = set()
queue = []

def enqueue(g, a, v, why=""):
    if not g or not a or not v: return
    if v.startswith("[") or v.startswith("(") or v in ("RELEASE", "LATEST", "+"):
        v2 = resolve_range(g, a, v)
        if not v2:
            say(f"  !! cannot resolve range {g}:{a}:{v} ({why})")
            return
        v = v2
    key = (g, a, v)
    if key not in seen:
        seen.add(key)
        queue.append(key)
        say(f"+ queue {g}:{a}:{v} ({why})")

def process(g, a, v):
    base_name = f"{a}-{v}"
    # metadata + pom + main file
    pom_file = fetch_artifact_file(g, a, v, f"{base_name}.pom")
    fetch_artifact_file(g, a, v, f"{base_name}.module")
    pom = None
    pom_path = os.path.join(REPO_DIR, gpath(g, a, v), f"{base_name}.pom")
    if pom_file and os.path.exists(pom_path):
        pom = parse_pom(pom_path)
    packaging = pom.packaging if pom else "jar"
    ext = {"jar": "jar", "aar": "aar", "bundle": "jar", "maven-plugin": "jar"}.get(packaging)
    if ext:
        fetch_artifact_file(g, a, v, f"{base_name}.{ext}")
    # files listed in gradle module metadata (classifiers, aapt2 native, etc.)
    mod_path = os.path.join(REPO_DIR, gpath(g, a, v), f"{base_name}.module")
    if os.path.exists(mod_path):
        try:
            mod = json.load(open(mod_path))
            for var in mod.get("variants", []):
                for f in var.get("files", []):
                    fname = f.get("name")
                    if fname:
                        fetch_artifact_file(g, a, v, fname)
                for dep in var.get("dependencies", []):
                    dv = (dep.get("version") or {}).get("requires") or (dep.get("version") or {}).get("strictly")
                    enqueue(dep.get("group"), dep.get("module"), dv, "module-meta")
                for dep in var.get("dependencyConstraints", []):
                    dv = (dep.get("version") or {}).get("requires")
                    enqueue(dep.get("group"), dep.get("module"), dv, "module-constraint")
        except Exception as e:
            say(f"  !! module parse fail {g}:{a}:{v}: {e}")
    if not pom:
        return
    # context for interpolation: own props + standard values (parent merged later approx)
    ctx = dict(pom.props)
    ctx.update({"project.groupId": g, "project.artifactId": a, "project.version": v,
                "pom.groupId": g, "pom.artifactId": a, "pom.version": v})
    if pom.parent:
        pg, pa, pv = pom.parent
        pv = interp(pv, ctx)
        enqueue(pg, pa, pv, "parent")
        # merge parent dep_mgmt + props (approx: parent processed from its file if present)
        par_path = os.path.join(REPO_DIR, gpath(pg, pa, pv or ""), f"{pa}-{pv}.pom")
        if pv and fetch_artifact_file(pg, pa, pv, f"{pa}-{pv}.pom") and os.path.exists(par_path):
            par = parse_pom(par_path)
            for k, val in par.props.items(): ctx.setdefault(k, val)
            for k2, val in par.dep_mgmt.items(): pom.dep_mgmt.setdefault(k2, val)
            for b in par.bom_imports: enqueue(*b, "parent-bom")
    for (bg, ba, bv) in pom.bom_imports:
        bv = interp(bv, ctx)
        enqueue(bg, ba, bv, "bom-import")
        bom_path = os.path.join(REPO_DIR, gpath(bg, ba, bv or ""), f"{ba}-{bv}.pom")
        if bv and fetch_artifact_file(bg, ba, bv, f"{ba}-{bv}.pom") and os.path.exists(bom_path):
            bom = parse_pom(bom_path)
            for k2, val in bom.dep_mgmt.items():
                pom.dep_mgmt.setdefault((k2[0], k2[1]), interp(val, ctx))
    for d in pom.deps:
        dg, da = interp(d["g"], ctx), interp(d["a"], ctx)
        dv = interp(d["v"], ctx)
        if not dv:
            mgd = pom.dep_mgmt.get((dg, da))
            dv = interp(mgd, ctx) if mgd else None
        if d["scope"] in ("test", "provided", "system"):
            continue
        if not dv:
            say(f"  !! no version for {dg}:{da} in {g}:{a}:{v}")
            continue
        enqueue(dg, da, dv, f"dep-of {a}")
        if d.get("classifier"):
            fetch_artifact_file(dg, da, dv if not (dv.startswith('[') or dv.startswith('(')) else resolve_range(dg, da, dv),
                                f"{da}-{dv}-{d['classifier']}.jar")

SEEDS = [
    ("com.android.application", "com.android.application.gradle.plugin", "8.5.2"),
    ("com.android.tools.build", "gradle", "8.5.2"),
    ("androidx.recyclerview", "recyclerview", "1.3.2"),
    ("androidx.dynamicanimation", "dynamicanimation", "1.0.0"),
    ("junit", "junit", "4.13.2"),
]
for s in SEEDS:
    enqueue(*s, "seed")

i = 0
while i < len(queue):
    g, a, v = queue[i]; i += 1
    say(f"[{i}/{len(queue)}] {g}:{a}:{v}")
    try:
        process(g, a, v)
    except Exception as e:
        say(f"  !! process error {g}:{a}:{v}: {e}")

say(f"DONE. artifacts={len(seen)}")
subprocess.run(["du", "-sh", REPO_DIR])
