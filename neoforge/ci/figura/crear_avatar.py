"""Crea el avatar de Figura de prueba (ci/figura/prueba): cuerpo de colores con un gorro y una cola.
Animaciones: "cola" (solo mueve la cola: Punchy sigue) y "saludo" (mueve el brazo: Punchy se pausa)."""
import base64, io, json, os, struct, uuid, zlib

def png(w, h, px):
    raw = b"".join(b"\0" + bytes(px[y*w:(y+1)*w*1] and sum(px[y*w:(y+1)*w], ())) for y in range(h))
    c = lambda t, d: struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
    return b"\x89PNG\r\n\x1a\n" + c(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)) + c(b"IDAT", zlib.compress(raw)) + c(b"IEND", b"")

COLORS = [(240, 200, 160), (40, 120, 220), (230, 60, 60), (60, 190, 90), (250, 210, 40), (150, 70, 200), (255, 130, 30), (30, 30, 30)]
W = H = 16
px = [(0, 0, 0, 255)] * (W * H)
for i, col in enumerate(COLORS):
    for y in range(2):
        for x in range(2):
            px[(y) * W + i * 2 + x] = col + (255,)

def uid():
    return str(uuid.uuid4())

elements, outliner = [], []

def cube(name, a, b, color, origin=None):
    u = color * 2
    face = {"uv": [u, 0, u + 1, 1], "texture": 0}
    e = {"name": name, "type": "cube", "uuid": uid(), "from": a, "to": b, "origin": origin or [0, 0, 0],
         "rotation": [0, 0, 0], "visibility": True, "faces": {f: dict(face) for f in ["north", "east", "south", "west", "up", "down"]}}
    elements.append(e)
    return e["uuid"]

def group(name, origin, children):
    g = {"name": name, "uuid": uid(), "origin": origin, "rotation": [0, 0, 0], "visibility": True, "children": children}
    return g

head = group("Head", [0, 24, 0], [cube("cabeza", [-4, 24, -4], [4, 32, 4], 0), cube("gorro", [-2, 32, -2], [2, 38, 2], 4),
                                   cube("nariz", [-1, 27, -5], [1, 29, -4], 6)])
tail = group("cola", [0, 14, 2], [cube("cola", [-1, 13, 2], [1, 15, 10], 6)])
body = group("Body", [0, 24, 0], [cube("torso", [-4, 12, -2], [4, 24, 2], 1), tail])
rarm = group("RightArm", [-5, 22, 0], [cube("brazo_d", [-8, 12, -2], [-4, 24, 2], 2)])
larm = group("LeftArm", [5, 22, 0], [cube("brazo_i", [4, 12, -2], [8, 24, 2], 3)])
rleg = group("RightLeg", [-1.9, 12, 0], [cube("pierna_d", [-3.9, 0, -2], [-0.1, 12, 2], 5)])
lleg = group("LeftLeg", [1.9, 12, 0], [cube("pierna_i", [0.1, 0, -2], [3.9, 12, 2], 7)])
outliner = [head, body, rarm, larm, rleg, lleg]

def kf(t, x, y, z):
    return {"channel": "rotation", "data_points": [{"x": str(x), "y": str(y), "z": str(z)}], "uuid": uid(),
            "time": t, "color": -1, "interpolation": "linear"}

anims = [
    {"uuid": uid(), "name": "cola", "loop": "loop", "override": False, "length": 1, "snapping": 20, "selected": False,
     "anim_time_update": "", "blend_weight": "", "start_delay": "", "loop_delay": "",
     "animators": {tail["uuid"]: {"name": "cola", "type": "bone", "keyframes": [kf(0, 0, -35, 0), kf(0.5, 0, 35, 0), kf(1, 0, -35, 0)]}}},
    {"uuid": uid(), "name": "saludo", "loop": "loop", "override": False, "length": 1, "snapping": 20, "selected": False,
     "anim_time_update": "", "blend_weight": "", "start_delay": "", "loop_delay": "",
     "animators": {rarm["uuid"]: {"name": "RightArm", "type": "bone", "keyframes": [kf(0, 0, 0, 150), kf(0.5, 0, 0, 120), kf(1, 0, 0, 150)]}}},
]

model = {"meta": {"format_version": "4.5", "model_format": "free", "box_uv": False}, "name": "model",
         "resolution": {"width": W, "height": H}, "elements": elements, "outliner": outliner,
         "textures": [{"path": "", "name": "colores.png", "folder": "", "namespace": "", "id": "0", "width": W, "height": H,
                       "uv_width": W, "uv_height": H, "particle": False, "render_mode": "default", "visible": True,
                       "mode": "bitmap", "saved": True, "uuid": uid(),
                       "source": "data:image/png;base64," + base64.b64encode(png(W, H, px)).decode()}],
         "animations": anims}

out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "prueba")
os.makedirs(out, exist_ok=True)
json.dump(model, open(os.path.join(out, "model.bbmodel"), "w"))
json.dump({"name": "Prueba Annis Compat", "authors": ["Starlevi"]}, open(os.path.join(out, "avatar.json"), "w"))
open(os.path.join(out, "script.lua"), "w").write("vanilla_model.PLAYER:setVisible(false)\nvanilla_model.ARMOR:setVisible(false)\n")
print("avatar en", out)
