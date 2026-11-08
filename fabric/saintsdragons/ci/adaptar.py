#!/usr/bin/env python3
"""
Port 1.21.11 de Saint's Dragons: adapta los recursos del repo original (rama 1.20.1) al formato de 1.21.11.
No cambia el arte (texturas, modelos, animaciones, sonidos): solo los mueve a las carpetas nuevas y
agrega los archivos que 1.21.11 pide aparte (definiciones de items, equipos, canciones de tocadiscos).
Los datos (recetas, botin, logros, etiquetas, mundo) son JSON con licencia MIT y se reescriben al formato nuevo.

uso: adaptar.py <checkout original> <src/main/resources del port> <ModItems.java>
"""
import json, os, re, shutil, sys

SRC, DST, MODITEMS = sys.argv[1], sys.argv[2], sys.argv[3]
NS = 'saintsdragons'
HERE = os.path.dirname(os.path.abspath(__file__))


def load(p):
    with open(p, encoding='utf-8') as f:
        return json.load(f)


def dump(o, p):
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, 'w', encoding='utf-8') as f:
        json.dump(o, f, indent=2, ensure_ascii=False)


# --------------------------------------------------------------------------------------------- arte
A = os.path.join(DST, 'assets', NS)
shutil.rmtree(os.path.join(DST, 'assets'), ignore_errors=True)
shutil.copytree(os.path.join(SRC, 'common/src/main/resources/assets'), os.path.join(DST, 'assets'))
os.makedirs(os.path.join(A, 'geckolib'), exist_ok=True)
shutil.move(os.path.join(A, 'geo'), os.path.join(A, 'geckolib/models'))
shutil.move(os.path.join(A, 'animations'), os.path.join(A, 'geckolib/animations'))
# los shaders "core" de json ya no se usan en 1.21.11
shutil.rmtree(os.path.join(A, 'shaders'), ignore_errors=True)

# huevos de spawn: colores de ModItems (en 1.21.11 ya no hay huevo pintable de Minecraft)
src = open(MODITEMS, encoding='utf-8').read()
eggs = {}
for m in re.finditer(r'register\("(\w+_spawn_egg)",.*?create\w*SpawnEgg\(\s*[^,]+,\s*(0x[0-9A-Fa-f]+),\s*(0x[0-9A-Fa-f]+)', src, re.S):
    eggs[m.group(1)] = (int(m.group(2), 16), int(m.group(3), 16))
EGG_BASE, EGG_SPOTS = NS + ':item/port_spawn_egg', NS + ':item/port_spawn_egg_overlay'
os.makedirs(os.path.join(A, 'textures/item'), exist_ok=True)
for n in ('port_spawn_egg.png', 'port_spawn_egg_overlay.png'):
    shutil.copy(os.path.join(HERE, 'extra', n), os.path.join(A, 'textures/item', n))

items = os.path.join(A, 'items')
os.makedirs(items, exist_ok=True)
n_items = 0
for root, _, files in os.walk(os.path.join(A, 'models/item')):
    for f in files:
        if not f.endswith('.json'):
            continue
        rel = os.path.relpath(os.path.join(root, f), os.path.join(A, 'models/item'))[:-5].replace(os.sep, '/')
        if '/' in rel:
            continue
        mp = os.path.join(root, f)
        model = load(mp)
        parent = model.get('parent', '')
        ref = NS + ':item/' + rel
        if rel in eggs:
            dump({'parent': 'minecraft:item/generated', 'textures': {'layer0': EGG_BASE, 'layer1': EGG_SPOTS}}, mp)
            p, s = eggs[rel]
            definition = {'type': 'minecraft:model', 'model': ref,
                          'tints': [{'type': 'minecraft:constant', 'value': p}, {'type': 'minecraft:constant', 'value': s}]}
        elif parent.endswith('builtin/entity'):
            # dibujado por GeckoLib; en el inventario se ve su dibujo plano si tiene
            flat = model.get('textures', {}).get('particle')
            dump({'textures': {'particle': flat} if flat else {}, 'gui_light': 'front'}, mp)
            special = {'type': 'minecraft:special', 'base': ref, 'model': {'type': 'geckolib:geckolib'}}
            if flat:
                gui = NS + ':item/' + rel + '_gui'
                dump({'parent': 'minecraft:item/generated', 'textures': {'layer0': flat}},
                     os.path.join(A, 'models/item', rel + '_gui.json'))
                definition = {'type': 'minecraft:select', 'property': 'minecraft:display_context',
                              'cases': [{'when': ['gui', 'ground', 'fixed'], 'model': {'type': 'minecraft:model', 'model': gui}}],
                              'fallback': special}
            else:
                definition = special
        else:
            definition = {'type': 'minecraft:model', 'model': ref}
        dump({'model': definition}, os.path.join(items, rel + '.json'))
        n_items += 1

# armaduras sin GeckoLib: capas de equipo (las texturas se copian con el nombre que pide 1.21.11)
EQUIP = {
    'draconian_armor': ('textures/armor/draconian_armor_layer_1_0.png', 'textures/armor/draconian_armor_layer_2.png'),
    'dragonheart_chunk': ('textures/models/armor/dragonheart_chunk_layer_1.png', 'textures/models/armor/dragonheart_chunk_layer_2.png'),
    'dragonheart_alloy': ('textures/models/armor/dragonheart_alloy_layer_1.png', 'textures/models/armor/dragonheart_alloy_layer_2.png'),
}
for name, (outer, inner) in EQUIP.items():
    layers = {}
    for kind, tex in (('humanoid', outer), ('humanoid_leggings', inner)):
        p = os.path.join(A, tex)
        if os.path.exists(p):
            d = os.path.join(A, 'textures/entity/equipment', kind)
            os.makedirs(d, exist_ok=True)
            shutil.copy(p, os.path.join(d, name + '.png'))
            layers[kind] = [{'texture': NS + ':' + name}]
    if layers:
        dump({'layers': layers}, os.path.join(A, 'equipment', name + '.json'))

# --------------------------------------------------------------------------------------------- datos
D = os.path.join(DST, 'data')
shutil.rmtree(D, ignore_errors=True)
RENAME = {'recipes': 'recipe', 'loot_tables': 'loot_table', 'advancements': 'advancement', 'structures': 'structure',
          'tags/items': 'tags/item', 'tags/blocks': 'tags/block', 'tags/entity_types': 'tags/entity_type',
          'tags/fluids': 'tags/fluid', 'tags/game_events': 'tags/game_event', 'tags/functions': 'tags/function'}


def newpath(rel):
    # rel: <ns>/<resto>
    ns, rest = rel.split('/', 1)
    for old, new in RENAME.items():
        if rest == old or rest.startswith(old + '/'):
            rest = new + rest[len(old):]
            break
    return ns + '/' + rest


def ingredient(x):
    if isinstance(x, list):
        out = [ingredient(i) for i in x]
        flat = [o for o in out if isinstance(o, str)]
        tags = [o for o in flat if o.startswith('#')]
        return tags[0] if tags else flat
    if isinstance(x, dict):
        if 'item' in x:
            return x['item']
        if 'tag' in x:
            return '#' + x['tag']
    return x


def result(x):
    if isinstance(x, str):
        return {'id': x}
    if isinstance(x, dict) and 'item' in x:
        r = {'id': x['item']}
        if x.get('count', 1) != 1:
            r['count'] = x['count']
        return r
    return x


def item_predicate(p):
    if not isinstance(p, dict):
        return p
    q = dict(p)
    if 'item' in q:
        q['items'] = q.pop('item')
    if 'tag' in q:
        q['items'] = '#' + q.pop('tag')
    if 'enchantments' in q:
        ench = []
        for e in q.pop('enchantments'):
            e = dict(e)
            if 'enchantment' in e:
                e['enchantments'] = e.pop('enchantment')
            ench.append(e)
        q.setdefault('predicates', {})['minecraft:enchantments'] = ench
    q.pop('nbt', None)
    return q


def fix_loot(o):
    if isinstance(o, dict):
        if o.get('function') == 'minecraft:looting_enchant':
            o['function'] = 'minecraft:enchanted_count_increase'
            o['enchantment'] = 'minecraft:looting'
        if o.get('condition') == 'minecraft:match_tool' and 'predicate' in o:
            o['predicate'] = item_predicate(o['predicate'])
        if o.get('function') == 'minecraft:set_nbt':
            o['function'] = 'minecraft:set_custom_data'
        for v in o.values():
            fix_loot(v)
    elif isinstance(o, list):
        for v in o:
            fix_loot(v)


def fix_recipe(o):
    t = o.get('type', '')
    if t == 'minecraft:crafting_shaped':
        o['key'] = {k: ingredient(v) for k, v in o.get('key', {}).items()}
        o['result'] = result(o['result'])
    elif t == 'minecraft:crafting_shapeless':
        o['ingredients'] = [ingredient(i) for i in o.get('ingredients', [])]
        o['result'] = result(o['result'])
    elif t in ('minecraft:smelting', 'minecraft:smoking', 'minecraft:blasting', 'minecraft:campfire_cooking'):
        o['ingredient'] = ingredient(o['ingredient'])
        o['result'] = result(o['result'])
    return o


def fix_advancement(o):
    disp = o.get('display')
    if disp and isinstance(disp.get('icon'), dict) and 'item' in disp['icon']:
        icon = disp['icon']
        disp['icon'] = {'id': icon['item']}
        if icon.get('count', 1) != 1:
            disp['icon']['count'] = icon['count']
    for c in o.get('criteria', {}).values():
        cond = c.get('conditions')
        if not isinstance(cond, dict):
            continue
        if c.get('trigger') == 'minecraft:inventory_changed' and 'items' in cond:
            cond['items'] = [item_predicate(p) for p in cond['items']]
        if 'item' in cond and isinstance(cond['item'], dict):
            cond['item'] = item_predicate(cond['item'])
    return o


def fix_tag(o, ns):
    vals = []
    for v in o.get('values', []):
        if isinstance(v, str):
            own = v.lstrip('#').startswith(NS + ':')
            vals.append(v if own else {'id': v, 'required': False})
        else:
            vals.append(v)
    o['values'] = vals
    return o


crucible = 0
sources = [os.path.join(SRC, 'common/src/main/resources/data'), os.path.join(SRC, 'common/src/generated/resources/data'),
           os.path.join(SRC, 'fabric/src/main/resources/data')]
for base in sources:
    for root, _, files in os.walk(base):
        for f in files:
            sp = os.path.join(root, f)
            rel = os.path.relpath(sp, base).replace(os.sep, '/')
            np = newpath(rel)
            dp = os.path.join(D, np)
            parts = np.split('/')
            if not f.endswith('.json'):
                os.makedirs(os.path.dirname(dp), exist_ok=True)
                shutil.copy(sp, dp)
                continue
            o = load(sp)
            kind = parts[1] if len(parts) > 1 else ''
            if kind == 'recipe':
                if str(o.get('type', '')).startswith(NS + ':draconic_crucible'):
                    dp = os.path.join(D, NS, 'draconic_crucible_recipe', '/'.join(parts[2:]))
                    crucible += 1
                else:
                    fix_recipe(o)
            elif kind == 'loot_table':
                fix_loot(o)
            elif kind == 'advancement':
                fix_advancement(o)
            elif kind == 'tags':
                fix_tag(o, parts[0])
            dump(o, dp)

# lo agregado para 1.21.11 (canciones de tocadiscos, etiquetas de reparacion)
extra = os.path.join(HERE, 'extra', 'data')
if os.path.isdir(extra):
    for root, _, files in os.walk(extra):
        for f in files:
            sp = os.path.join(root, f)
            dp = os.path.join(D, os.path.relpath(sp, extra))
            os.makedirs(os.path.dirname(dp), exist_ok=True)
            shutil.copy(sp, dp)

print('definiciones de items:', n_items, '| huevos:', len(eggs), '| recetas del crisol:', crucible)
