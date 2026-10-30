"""Baja de Modrinth los mods de Forge 1.20.1 que usamos para compilar (no se incluyen en nuestros jars).
Uso: python3 bajar_mods.py <carpeta libs>
Cada uno queda como libs/<nombre>-1.jar (asi ForgeGradle los puede desofuscar con "blank:<nombre>:1")."""
import json, os, sys, urllib.request, urllib.parse

API = "https://api.modrinth.com/v2"
UA = {"User-Agent": "annis-mods-ci/1.0"}

def get(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60) as r:
        return json.load(r)

LOADER = sys.argv[2] if len(sys.argv) > 2 else "forge"
GAME = sys.argv[3] if len(sys.argv) > 3 else "1.20.1"

def versions(slug):
    q = urllib.parse.urlencode({"loaders": '["%s"]' % LOADER, "game_versions": '["%s"]' % GAME})
    try:
        return get(f"{API}/project/{slug}/version?{q}")
    except Exception as e:
        print(f"  {slug}: {e}")
        return []

def search(query):
    facets = urllib.parse.quote('[["categories:%s"],["versions:%s"]]' % (LOADER, GAME))
    hits = get(f"{API}/search?query={urllib.parse.quote(query)}&facets={facets}&limit=8")["hits"]
    for h in hits:
        print(f"  busqueda '{query}': {h['slug']} - {h['title']}")
    return [h["slug"] for h in hits]

def fetch(name, slugs, query, dest):
    for slug in slugs + search(query):
        vs = versions(slug)
        if not vs:
            continue
        v = vs[0]
        f = next((f for f in v["files"] if f.get("primary")), v["files"][0])
        out = os.path.join(dest, f"{name}-1.jar")
        print(f"{name}: {slug} {v['version_number']} -> {f['filename']}")
        urllib.request.urlretrieve(f["url"], out)
        return True
    print(f"{name}: NO ENCONTRADO para {LOADER} {GAME}")
    return False

dest = sys.argv[1]
os.makedirs(dest, exist_ok=True)
fetch("playeranimator", ["playeranimator"], "playerAnimator", dest)
if LOADER == "forge":
    fetch("mca", ["minecraft-comes-alive-reborn", "mca-reborn", "minecraft-comes-alive"], "Minecraft Comes Alive", dest)
fetch("punchymoves", ["punchy-moves"], "Punchy Moves", dest)
fetch("punchystudio", ["punchy-studio"], "Punchy Studio", dest)
if LOADER == "neoforge":
    fetch("emf", ["entity-model-features"], "Entity Model Features", dest)
    fetch("etf", ["entitytexturefeatures"], "Entity Texture Features", dest)
