"""Baja de Modrinth la ultima version de un mod para Fabric y una version de Minecraft.
Uso: bajar_modrinth.py <busqueda o slug> <version mc> <carpeta> [nombre del archivo]"""
import json, sys, urllib.request, urllib.parse, os

q, mc, out = sys.argv[1], sys.argv[2], sys.argv[3]
name = sys.argv[4] if len(sys.argv) > 4 else None
H = {'User-Agent': 'starlevi-mods-ci (github actions)'}


def get(url):
    return json.load(urllib.request.urlopen(urllib.request.Request(url, headers=H), timeout=60))


slug = q
try:
    get('https://api.modrinth.com/v2/project/' + urllib.parse.quote(q))
except Exception:
    hits = get('https://api.modrinth.com/v2/search?limit=5&query=' + urllib.parse.quote(q))['hits']
    print('busqueda:', [(h['slug'], h['title']) for h in hits])
    slug = hits[0]['slug']
vs = get('https://api.modrinth.com/v2/project/%s/version?loaders=%s&game_versions=%s' % (
    slug, urllib.parse.quote('["fabric"]'), urllib.parse.quote('["%s"]' % mc)))
if not vs:
    sys.exit('no hay version de %s para fabric %s' % (slug, mc))
v = vs[0]
f = next((x for x in v['files'] if x.get('primary')), v['files'][0])
os.makedirs(out, exist_ok=True)
dest = os.path.join(out, name or f['filename'])
urllib.request.urlretrieve(f['url'], dest)
print('%s %s -> %s (%d bytes)' % (slug, v['version_number'], dest, os.path.getsize(dest)))
