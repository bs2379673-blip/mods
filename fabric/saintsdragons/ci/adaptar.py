# Agrega lo que 1.21.11 pide aparte (no cambia el arte): las definiciones de items (assets/<ns>/items/*.json)
# que apuntan a los modelos de item que ya trae el mod. Los items que dibuja GeckoLib usan su modelo especial.
import json, os, sys

A = sys.argv[1]
items = os.path.join(A, 'items')
os.makedirs(items, exist_ok=True)
here = os.path.dirname(os.path.abspath(__file__))
special = set()
p = os.path.join(here, 'items_geckolib.txt')
if os.path.exists(p):
    special = {l.strip() for l in open(p) if l.strip()}
n = 0
for root, _, files in os.walk(os.path.join(A, 'models', 'item')):
    for f in files:
        if not f.endswith('.json'):
            continue
        rel = os.path.relpath(os.path.join(root, f), os.path.join(A, 'models', 'item'))[:-5].replace(os.sep, '/')
        if '/' in rel:
            continue
        if rel in special:
            model = {"type": "minecraft:special", "base": "saintsdragons:item/" + rel, "model": {"type": "geckolib:geckolib"}}
        else:
            model = {"type": "minecraft:model", "model": "saintsdragons:item/" + rel}
        json.dump({"model": model}, open(os.path.join(items, rel + '.json'), 'w'))
        n += 1
print('definiciones de items:', n)
