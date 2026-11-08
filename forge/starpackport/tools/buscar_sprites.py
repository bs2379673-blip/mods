# Busca automaticamente donde estaba cada sprite de 1.21.11 dentro de las hojas de 1.20.1
# comparando pixeles. Uso: python3 buscar_sprites.py <v121 textures> <v120 textures> <tabla.json existente> <salida.json>
import json, os, sys
import numpy as np
from PIL import Image
v121, v120, base, out = sys.argv[1:5]
T = json.load(open(base))
SP = os.path.join(v121, 'gui/sprites')
def load(p):
    return np.asarray(Image.open(p).convert('RGBA')).astype(np.int16)
cands = {
    'container/creative_inventory': ['gui/container/creative_inventory/tabs.png', 'gui/container/creative_inventory/tab_items.png'],
    'container/villager': ['gui/container/villager2.png'],
    'recipe_book': ['gui/recipe_book.png', 'gui/recipe_button.png'],
    'advancements': ['gui/advancements/widgets.png', 'gui/advancements/tabs.png'],
    'container/inventory': ['gui/container/inventory.png'],
}
atl = {}
found = 0
for root, _, fs in os.walk(SP):
    for f in fs:
        if not f.endswith('.png'):
            continue
        name = os.path.relpath(os.path.join(root, f), SP)[:-4]
        if name in T or name.startswith('container/slot/'):
            continue
        grp = '/'.join(name.split('/')[:2]) if name.startswith('container/') else name.split('/')[0]
        lst = cands.get(grp)
        if lst is None and name.startswith('container/'):
            lst = ['gui/container/%s.png' % name.split('/')[1]]
        if not lst:
            continue
        s = load(os.path.join(root, f))
        if os.path.exists(os.path.join(root, f) + '.mcmeta'):
            m = json.load(open(os.path.join(root, f) + '.mcmeta'))
            if 'animation' in m:
                continue
            if 'gui' in m and m['gui'].get('scaling', {}).get('type') in ('nine_slice', 'tile'):
                continue
        h, w = s.shape[:2]
        if h * w < 16:
            continue
        mask = s[:, :, 3] > 0
        if mask.sum() < 8:
            continue
        best = None
        for a in lst:
            p = os.path.join(v120, a)
            if not os.path.exists(p):
                continue
            if a not in atl:
                atl[a] = load(p)
            A = atl[a]
            H, W = A.shape[:2]
            if H < h or W < w:
                continue
            win = np.lib.stride_tricks.sliding_window_view(A, (h, w, 4))[:, :, 0]
            diff = (np.abs(win - s).max(axis=-1) > 24) & mask
            bad = diff.sum(axis=(-1, -2))
            y, x = np.unravel_index(np.argmin(bad), bad.shape)
            score = bad[y, x] / mask.sum()
            if best is None or score < best[0]:
                best = (score, a, int(x), int(y))
        if best and best[0] <= 0.02:
            T.setdefault(name, []).append([best[1], best[2], best[3], w, h])
            found += 1
print('encontrados', found)
json.dump(T, open(out, 'w'), indent=0)
