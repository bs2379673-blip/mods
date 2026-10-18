"""Convierte los stickers y GIF del show en sprites livianos para el mod concierto.
Uso: python3 make_sprites.py <carpeta rp/assets/customskyboxes> <salida assets/concierto>
PNG -> 256 px (paleta). GIF -> hasta 16 cuadros de 128 px en una hoja de 4x4 (paleta) + fps.
Escribe textures/spr/<nombre>.png (+ .mcmeta con filtro suave) y sprites.json con cols/rows/frames/fps."""
import json, os, sys
from PIL import Image

src, dst = sys.argv[1], sys.argv[2]
PNG = """t_obscure t_321_1 t_321_2 t_321_3 t_static t_konton t_melt t_beam t_loveshot t_loop t_mikumiku t_teto t_neru t_triplebaka t_gracias t_go
u_head_calm u_head_happy u_head_smug u_head_dizzy u_head_sparkle u_leek u_crystalheart u_wand u_bow u_mesmerizer u_pxheart u_backroom u_exit u_01
s_spiral s_eye s_eyes s_heart s_heart_teal s_heartdrip s_sundrip s_synthsun s_magiccircle s_note_pink s_note_teal s_ring s_ring_pink
s_sparkle_gold s_sparkle_pink s_sparkle_teal s_star s_star_pink s_bolt s_bolt_red s_eq s_tv s_rainbow s_light s_cursor s_pxleek""".split()
GIF = "u_magical u_beam u2_wink u2_chibi u2_fly u2_swirl u2_teary u2_magicard u3_maid_duo u3_neru_phone u3_teto_box u3_teto_sketch u3_trio_wave u3_trio_wink".split()

out = os.path.join(dst, "textures", "spr")
os.makedirs(out, exist_ok=True)
meta = {}


def save(im, name):
    q = im.quantize(colors=255, method=Image.Quantize.FASTOCTREE, dither=Image.Dither.NONE)
    p = os.path.join(out, name + ".png")
    q.save(p, optimize=True)
    open(p + ".mcmeta", "w").write('{"texture":{"blur":true,"clamp":true}}')


for n in PNG:
    f = os.path.join(src, "custom", f"ms_{n}.png")
    if not os.path.exists(f):
        print("falta", f); continue
    im = Image.open(f).convert("RGBA").resize((256, 256), Image.LANCZOS)
    save(im, n)
    meta[n] = dict(cols=1, rows=1, frames=1, fps=0)

for n in GIF:
    f = os.path.join(src, "custom", f"ms_{n}.gif")
    if not os.path.exists(f):
        print("falta", f); continue
    g = Image.open(f)
    N = g.n_frames
    durs = []
    frames = []
    for i in range(N):
        g.seek(i)
        durs.append(g.info.get("duration", 100) or 100)
        frames.append(g.convert("RGBA").copy())
    take = list(range(N)) if N <= 16 else [round(i * (N - 1) / 15) for i in range(16)]
    sheet = Image.new("RGBA", (4 * 128, 4 * 128), (0, 0, 0, 0))
    for k, i in enumerate(take):
        sheet.paste(frames[i].resize((128, 128), Image.LANCZOS), ((k % 4) * 128, (k // 4) * 128))
    total = sum(durs) / 1000.0
    meta[n] = dict(cols=4, rows=4, frames=len(take), fps=round(len(take) / max(total, 0.2), 2))
    save(sheet, n)

json.dump(meta, open(os.path.join(dst, "sprites.json"), "w"), indent=0)
tot = sum(os.path.getsize(os.path.join(out, x)) for x in os.listdir(out) if x.endswith(".png"))
print(len(meta), "sprites,", tot // 1024, "KB")
