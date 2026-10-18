"""Genera los guiones de todo el concierto (assets/concierto/shows/<cancion>.json).
Uso: python3 make_shows.py <carpeta audio con los .v2.json> <salida shows>

Idea: cada PARTE de la cancion (intro, estrofa, estribillo, climax, pausa, final) tiene UN look; si el estribillo vuelve se ve igual.
Los stickers y GIF van en casilleros fijos respecto al eje del escenario (centro, pares simetricos), nunca al azar.
Los golpes reales (bombo, caja, platillos) los pone el mod desde el analisis del audio; aca solo van los momentos.
"""
import json, os, sys

SRC, DST = sys.argv[1], sys.argv[2]
TEAL, PINK, GOLD, RED, VIO, WHITE = "#39c5bb", "#ff6aa8", "#ffd23a", "#ff3a5a", "#b45cff", "#ffffff"


def L(scene, floor="off", eq=0.0, beams="off", ring=True, col=None, k=0.5, s=0.25, a=0.7, swap="off"):
    d = dict(scene=scene, floor=floor, eq=eq, beams=beams, ring=ring, react=dict(kick=k, snare=s, accent=a))
    if col:
        d["col"] = col
    if swap != "off":
        d["swap"] = swap
    return d


class Show:
    def __init__(self, name, d=None):
        self.name, self.d, self.cues = name, d, []

    def look(self, t, look, **kw):
        c = dict(t=round(t, 3))
        c.update(look)
        c.update(kw)
        self.cues.append(c)

    def fx(self, t, name, **kw):
        c = dict(t=round(t, 3), fx=name)
        c.update(kw)
        self.cues.append(c)

    def spr(self, t, id, slot, dur, **kw):
        self.fx(t, "sprite", id=id, slot=slot, dur=round(dur, 2), **kw)

    def onset(self, t, big=True):
        self.fx(t, "flash")
        self.fx(t, "nova")
        if big:
            self.fx(t, "shock")

    def save(self):
        self.cues.sort(key=lambda c: (c["t"], 0 if "fx" not in c else 1))
        os.makedirs(DST, exist_ok=True)
        key = self.name.replace("_", "").lower()
        json.dump(dict(song=self.name, _nota="t = segundos desde que arranca la musica. Cada orden con scene/floor/eq/beams/ring/col/react/swap queda puesta hasta la siguiente. "
                       "fx: flash, nova, shock, blackout(dur), column(col,size,dur), estrella(col,dur,side), pyro(dur,col), sprite(id,slot,dur,size,spin,grow,pulse,tint,add,flip), jugadores(modo=volar|brillo|confeti, dur, radio).",
                       cues=self.cues), open(os.path.join(DST, key + ".json"), "w"), indent=1, ensure_ascii=False)
        print(f"{key}: {len(self.cues)} ordenes")


def load(name):
    return json.load(open(os.path.join(SRC, name + ".v2.json")))


STRONG = ("chorus", "climax")


def build(name, plan, extra=None):
    d = load(name.replace("_", ""))
    sh = Show(name, d)
    secs = d["sections"]
    looks = plan["look"]

    def role_look(role):
        base = looks.get(role) or looks["verse"]
        out = json.loads(json.dumps(base))
        pal = plan.get("pal", {})
        c = pal.get(role) or pal.get("*")
        if c:
            out["col"] = c
        return out

    def section_at(t):
        for i, s in enumerate(secs):
            if s["t0"] <= t < s["t1"]:
                return i, s
        return len(secs) - 1, secs[-1]

    for i, s in enumerate(secs):
        role = s["role"] if s["role"] in looks or s["role"] == "verse" else "verse"
        t0 = 0.0 if i == 0 else s["t0"]
        sh.look(t0, role_look(role))
        prev = secs[i - 1]["role"] if i else ""
        if i and role in STRONG and prev not in STRONG:
            sh.onset(t0)
            if role in plan.get("pyro", ()):
                sh.fx(t0, "pyro", dur=3.0)
        elif i and role == "climax" and prev == "chorus":
            sh.onset(t0, big=False)
        # stickers y GIF de esta parte, siempre en los mismos casilleros
        dur = min(34.0, s["t1"] - t0 - 1.0)
        for item in plan.get("spr", {}).get(role, []):
            item = dict(item)
            sid, slot = item.pop("id"), item.pop("slot")
            delay = item.pop("delay", 0.7)
            if dur - delay > 1.5:
                sh.spr(t0 + delay, sid, slot, dur - delay, **item)
    for a, b in d.get("stops", []):
        sh.fx(a, "blackout", dur=round(max(0.15, b - a + 0.05), 2))
    if plan.get("title"):
        sh.spr(max(1.5, d["start"] + 0.8), plan["title"], "T", 5.5, size=28, pulse=0.06)
    for t, kw in plan.get("players", []):
        sh.fx(t, "jugadores", **kw)
    if extra:
        extra(sh, d, section_at, role_look)
    sh.save()
    return sh


def moment(sh, section_at, role_look, t0, t1, look):
    """un momento especial: pisa el look de la parte y al terminar vuelve al de la parte"""
    sh.look(t0, look)
    i, s = section_at(t1 + 0.01)
    sh.look(t1, role_look(s["role"] if s["role"] in ("intro", "verse", "chorus", "climax", "break", "outro") else "verse"))


# ----------------------------------------------------------------------------------------------- paletas
P_STATIC = [TEAL, RED, "#e8ffff"]
P_KONTON = [PINK, TEAL, GOLD]
P_MELT = {"*": ["#ff6a9a", "#ff9a6a", "#ffd0e0"], "break": ["#b48cff", "#ff9ac8", "#ffd9f0"], "climax": ["#ff4d94", "#ffb04d", "#fff0f5"]}
P_MAGIC = [PINK, TEAL, "#ffe27a"]
P_ROOMS = ["#e8d870", "#fff8d0", "#ff3a20"]
P_SYNTH = ["#ff3df0", "#00e5ff", GOLD]
P_MINNA = ["#ff9ad5", "#9ad8ff", "#fff3a8"]
P_MES = [VIO, TEAL, "#ff4a6a"]
P_BAKA = [GOLD, TEAL, RED]

# ----------------------------------------------------------------------------------------------- canciones
PLANS = {}

PLANS["static"] = dict(
    title="t_static", pal={"*": P_STATIC, "break": [RED, "#7a0f24", "#ffb0b8"]}, pyro=("climax",),
    look=dict(
        intro=L("oscilloscope", "off", 0.0, "off", True, k=0.3, s=0.1, a=0.5),
        verse=L("oscilloscope", "grid", 0.55, "slow", True, k=0.55, s=0.3, a=0.7),
        **{"break": L("oscilloscope", "off", 0.0, "strobe", False, k=0.5, s=0.5, a=0.8)},
        chorus=L("magnetosphere", "ripple", 1.0, "cross", True, k=0.85, s=0.5, a=1.0),
        climax=L("cthugha", "disco", 1.2, "strobe", True, k=1.0, s=0.6, a=1.0),
        outro=L("oscilloscope", "grid", 0.3, "off", True, k=0.3, s=0.1, a=0.4)),
    spr=dict(
        verse=[dict(id="s_tv", slot="C", size=26)],
        chorus=[dict(id="u_head_smug", slot="C"), dict(id="s_bolt", slot="LR1"), dict(id="u_01", slot="H", size=16)],
        climax=[dict(id="u_head_smug", slot="C"), dict(id="s_bolt", slot="LR1"), dict(id="s_bolt_red", slot="LR2"), dict(id="s_cursor", slot="LRH", size=12)],
        outro=[dict(id="s_tv", slot="C", size=24)]),
    **{"break": None})
PLANS["static"].pop("break", None)
PLANS["static"]["spr"]["break"] = [dict(id="s_cursor", slot="C", size=22)]

PLANS["konton_boogie"] = dict(
    title="t_konton", pal={"*": P_KONTON}, pyro=("chorus", "climax"),
    look=dict(
        intro=L("kaleidoscope", "disco", 0.8, "fan", True, k=0.8, s=0.5, a=0.9, swap="snare"),
        climax=L("kaleidoscope", "disco", 1.2, "cross", True, k=1.0, s=0.6, a=1.0, swap="snare"),
        verse=L("milkdrop", "grid", 0.5, "slow", True, k=0.5, s=0.35, a=0.7, swap="snare"),
        chorus=L("kaleidoscope", "ripple", 1.0, "fan", True, k=0.85, s=0.5, a=1.0, swap="snare"),
        outro=L("milkdrop", "grid", 0.3, "off", True, k=0.3, s=0.1, a=0.4)),
    spr=dict(
        intro=[dict(id="s_spiral", slot="C", spin=28, size=26)],
        verse=[dict(id="s_spiral", slot="C", spin=18, size=24), dict(id="s_eye", slot="LR2", size=13)],
        chorus=[dict(id="u_head_dizzy", slot="C"), dict(id="s_spiral", slot="LR1", spin=45), dict(id="s_eye", slot="LH", size=11)],
        climax=[dict(id="u_head_dizzy", slot="C"), dict(id="s_spiral", slot="LR1", spin=60), dict(id="s_spiral", slot="LR3", spin=-50)]))

PLANS["melt"] = dict(
    title="t_melt", pal=P_MELT, pyro=("climax",),
    look=dict(
        intro=L("sunset", "off", 0.0, "off", True, k=0.3, s=0.1, a=0.5),
        verse=L("sunset", "grid", 0.55, "slow", True, k=0.5, s=0.25, a=0.7),
        **{"break": L("sunset", "off", 0.0, "off", True, k=0.25, s=0.1, a=0.4)},
        chorus=L("meltglow", "ripple", 1.0, "fan", True, k=0.8, s=0.45, a=1.0),
        climax=L("meltglow", "disco", 1.2, "cross", True, k=1.0, s=0.6, a=1.0),
        outro=L("sunset", "grid", 0.3, "off", True, k=0.3, s=0.1, a=0.4)),
    spr=dict(
        verse=[dict(id="u_head_calm", slot="T", size=18)],
        chorus=[dict(id="u_head_happy", slot="T", size=20), dict(id="s_note_pink", slot="LR3", size=13)],
        climax=[dict(id="u_head_happy", slot="T", size=22), dict(id="s_note_pink", slot="L3", size=13), dict(id="s_note_teal", slot="R3", size=13), dict(id="s_sparkle_pink", slot="LRU", spin=30, size=11)],
        outro=[dict(id="u_head_calm", slot="T", size=18)]),
    players=[(210.6, dict(modo="confeti", dur=26, radio=80))])

PLANS["magical_cure"] = dict(
    title=None, pal={"*": P_MAGIC}, pyro=("chorus", "climax"),
    look=dict(
        intro=L("magnetosphere", "off", 0.0, "off", True, k=0.3, s=0.1, a=0.5),
        verse=L("magnetosphere", "grid", 0.6, "slow", True, k=0.55, s=0.3, a=0.7),
        **{"break": L("starfield", "off", 0.0, "off", True, k=0.3, s=0.1, a=0.5)},
        chorus=L("fireworks", "ripple", 1.0, "fan", True, k=0.85, s=0.5, a=1.0),
        climax=L("fireworks", "disco", 1.25, "cross", True, k=1.0, s=0.6, a=1.0),
        outro=L("magnetosphere", "grid", 0.3, "off", True, k=0.3, s=0.1, a=0.4)),
    spr=dict(
        intro=[dict(id="s_magiccircle", slot="C", spin=7, size=32)],
        verse=[dict(id="s_magiccircle", slot="C", spin=7, size=30), dict(id="u_wand", slot="LR2", size=14)],
        chorus=[dict(id="u_magical", slot="C", size=30), dict(id="u_wand", slot="LR1", size=17), dict(id="u_crystalheart", slot="H", size=15)],
        climax=[dict(id="u_magical", slot="C", size=32), dict(id="u_crystalheart", slot="LR1", size=17), dict(id="s_sparkle_gold", slot="LRH", spin=35, size=11), dict(id="s_sparkle_pink", slot="LRU", spin=-30, size=11)],
        outro=[dict(id="s_magiccircle", slot="C", spin=7, size=28)]))

PLANS["looping_the_rooms"] = dict(
    title="t_loop", pal={"*": P_ROOMS}, pyro=(),
    look=dict(
        intro=L("oscilloscope", "off", 0.0, "off", True, k=0.3, s=0.1, a=0.4),
        verse=L("oscilloscope", "grid", 0.5, "strobe", True, k=0.6, s=0.3, a=0.6),
        climax=L("oscilloscope", "grid", 0.9, "strobe", True, k=0.9, s=0.5, a=0.9),
        chorus=L("oscilloscope", "ripple", 0.9, "cross", True, k=0.8, s=0.45, a=0.9),
        outro=L("oscilloscope", "off", 0.2, "off", True, k=0.25, s=0.1, a=0.3)),
    spr=dict(
        intro=[dict(id="u_backroom", slot="C", size=30)],
        verse=[dict(id="u_backroom", slot="C", size=30), dict(id="u_exit", slot="H", size=14)],
        climax=[dict(id="u_backroom", slot="C", size=32), dict(id="u_exit", slot="LRH", size=12)],
        chorus=[dict(id="u_backroom", slot="C", size=32), dict(id="u_exit", slot="LRH", size=12)],
        outro=[dict(id="u_exit", slot="C", size=22)]))

PLANS["anamanaguchi"] = dict(
    title=None, pal={"*": P_SYNTH}, pyro=("chorus", "climax"),
    look=dict(
        intro=L("sunset", "grid", 0.6, "slow", True, k=0.6, s=0.3, a=0.7),
        verse=L("sunset", "grid", 0.7, "slow", True, k=0.7, s=0.3, a=0.7),
        climax=L("fireworks", "disco", 1.3, "cross", True, k=1.0, s=0.6, a=1.0),
        chorus=L("fireworks", "ripple", 1.0, "fan", True, k=0.9, s=0.5, a=1.0),
        **{"break": L("starfield", "grid", 0.4, "off", True, k=0.4, s=0.2, a=0.5)},
        outro=L("sunset", "grid", 0.3, "off", True, k=0.3, s=0.1, a=0.4)),
    spr=dict(
        verse=[dict(id="u_pxheart", slot="LR1", size=14)],
        chorus=[dict(id="u_head_smug", slot="C", size=26), dict(id="u_pxheart", slot="LR1", size=15), dict(id="s_pxleek", slot="LR2", size=13)],
        climax=[dict(id="u_head_smug", slot="C", size=28), dict(id="s_rainbow", slot="B", size=34), dict(id="u_leek", slot="LRH", size=13)],
        **{"break": [dict(id="s_eq", slot="C", size=24)]}))

PLANS["minna_miku_miku"] = dict(
    title="t_mikumiku", pal={"*": P_MINNA}, pyro=("climax",),
    look=dict(
        intro=L("sunset", "off", 0.0, "off", True, k=0.3, s=0.1, a=0.5),
        verse=L("sunset", "grid", 0.5, "slow", True, k=0.5, s=0.25, a=0.7),
        chorus=L("fireworks", "ripple", 1.0, "fan", True, k=0.8, s=0.45, a=1.0),
        climax=L("meltglow", "disco", 1.3, "cross", True, k=1.0, s=0.6, a=1.0),
        outro=L("sunset", "grid", 0.3, "off", True, k=0.3, s=0.1, a=0.4)),
    spr=dict(
        intro=[dict(id="u_leek", slot="C", size=22)],
        verse=[dict(id="u_head_calm", slot="C", size=22), dict(id="s_note_pink", slot="L2", size=12), dict(id="s_note_teal", slot="R2", size=12)],
        chorus=[dict(id="u_head_happy", slot="C", size=28), dict(id="s_heart", slot="LR1", size=15), dict(id="s_note_pink", slot="L2", size=13), dict(id="s_note_teal", slot="R2", size=13)],
        climax=[dict(id="u_head_sparkle", slot="C", size=30), dict(id="u2_wink", slot="L1", size=19), dict(id="u2_fly", slot="R1", size=19), dict(id="s_star_pink", slot="LRH", spin=30, size=11)],
        outro=[dict(id="u_head_calm", slot="C", size=22)]),
    players=[(239.0, dict(modo="volar", dur=14, radio=70)), (239.0, dict(modo="confeti", dur=14, radio=80))])

PLANS["mesmerizer"] = dict(
    title="u_mesmerizer", pal={"*": P_MES}, pyro=("climax",),
    look=dict(
        intro=L("milkdrop", "off", 0.0, "off", True, k=0.3, s=0.1, a=0.5),
        verse=L("milkdrop", "grid", 0.5, "slow", True, k=0.55, s=0.3, a=0.7),
        chorus=L("kaleidoscope", "ripple", 1.0, "fan", True, k=0.85, s=0.5, a=1.0),
        climax=L("kaleidoscope", "disco", 1.2, "cross", True, k=1.0, s=0.6, a=1.0),
        outro=L("milkdrop", "grid", 0.3, "off", True, k=0.3, s=0.1, a=0.4)),
    spr=dict(
        intro=[dict(id="s_spiral", slot="C", spin=22, size=28)],
        verse=[dict(id="s_spiral", slot="C", spin=22, size=26), dict(id="s_eye", slot="H", size=12)],
        chorus=[dict(id="u3_maid_duo", slot="L1", size=21), dict(id="u3_teto_sketch", slot="R1", size=21), dict(id="s_eye", slot="H", size=14)],
        climax=[dict(id="s_spiral", slot="C", spin=40, size=30), dict(id="s_eye", slot="LR2", size=14), dict(id="s_heart", slot="LRH", size=12)],
        outro=[dict(id="s_spiral", slot="C", spin=14, size=24)]),
    players=[(79.5, dict(modo="brillo", dur=18, radio=80))])

PLANS["triple_baka"] = dict(
    title="t_triplebaka", pal={"*": P_BAKA}, pyro=("chorus", "climax"),
    look=dict(
        intro=L("spectrum", "off", 0.0, "off", True, k=0.4, s=0.2, a=0.6, swap="kick"),
        verse=L("spectrum", "grid", 0.6, "slow", True, k=0.7, s=0.35, a=0.8, swap="snare"),
        chorus=L("fireworks", "disco", 1.0, "fan", True, k=0.9, s=0.5, a=1.0, swap="snare"),
        climax=L("fireworks", "disco", 1.3, "cross", True, k=1.0, s=0.6, a=1.0, swap="kick"),
        outro=L("spectrum", "grid", 0.3, "off", True, k=0.3, s=0.1, a=0.4)),
    spr=dict(
        verse=[dict(id="u_head_smug", slot="C", size=24)],
        chorus=[dict(id="u3_trio_wave", slot="C", size=30), dict(id="u3_neru_phone", slot="L1", size=19), dict(id="u3_teto_box", slot="R1", size=19), dict(id="s_star", slot="LRH", spin=25, size=11)],
        climax=[dict(id="u3_trio_wink", slot="C", size=32), dict(id="u3_neru_phone", slot="L1", size=19), dict(id="u3_teto_box", slot="R1", size=19), dict(id="s_star_pink", slot="LRU", spin=-25, size=11)],
        outro=[dict(id="u_head_smug", slot="C", size=22)]),
    players=[(103.5, dict(modo="confeti", dur=40, radio=80))])


# ----------------------------------------------------------------------------------------------- momentos especiales
def extra_magical(sh, d, section_at, role_look):
    """MIKU MIKU BEAM: cuenta 1-2-3 con el portal que se abre y, en el 'beaaam', el rayo gigante"""
    BEAM_OFF = 83.02            # el clip mikubeam.ogg empieza en este segundo de Magical Cure
    BEAM_T = BEAM_OFF + 12.40   # el grito "beaaam"
    BEAM_END = BEAM_OFF + 20.9
    CHARGE = dict(scene="starfield", floor="ripple", eq=1.0, beams="strobe", ring=True, col=[TEAL, WHITE, PINK], react=dict(kick=1.0, snare=0.6, accent=1.0))
    BEAM = dict(scene="fireworks", floor="disco", eq=1.4, beams="cross", ring=True, col=[TEAL, WHITE, "#9ffff4"], react=dict(kick=1.0, snare=0.7, accent=1.0))
    # la cuenta: los ultimos 3 compases de la voz
    cnt = [b for b in d["bars"] if BEAM_OFF + 2.0 <= b < BEAM_T - 0.3][-3:]
    start = cnt[0] if cnt else BEAM_T - 5
    # los sprites de estas partes se cortan antes: el momento tiene los suyos
    sh.cues = [c for c in sh.cues if not (c.get("fx") == "sprite" and start - 6 < c["t"] < BEAM_END)]
    sh.look(start - 0.3, CHARGE)
    sh.spr(start - 0.3, "s_magiccircle", "C", BEAM_T - start, size=34, grow=0.25, spin=60, pulse=0.12)
    for i, b in enumerate(cnt):
        sh.spr(b, f"t_321_{i + 1}", "C", min(2.0, BEAM_T - b - 0.1), size=16 + 7 * i, pulse=0.3)
        sh.fx(b, "flash")
        sh.fx(b, "shock")
    sh.fx(BEAM_T - 0.15, "blackout", dur=0.2)
    sh.look(BEAM_T, BEAM)
    sh.fx(BEAM_T, "flash")
    sh.fx(BEAM_T, "nova")
    sh.fx(BEAM_T, "shock")
    sh.fx(BEAM_T, "column", col=TEAL, size=9, dur=BEAM_END - BEAM_T - 1)
    sh.fx(BEAM_T, "column", col=WHITE, size=4, dur=BEAM_END - BEAM_T - 1)
    sh.fx(BEAM_T, "pyro", dur=4.0)
    sh.fx(BEAM_T + 2.4, "pyro", dur=3.0)
    sh.spr(BEAM_T, "t_beam", "T", 5.0, size=30, pulse=0.2)
    sh.spr(BEAM_T, "s_ring", "C", BEAM_END - BEAM_T, size=30, pulse=0.35)
    sh.spr(BEAM_T, "s_ring_pink", "C", BEAM_END - BEAM_T, size=38, pulse=0.5)
    sh.spr(BEAM_T + 0.4, "u_magical", "C", BEAM_END - BEAM_T - 1.0, size=22)
    sh.spr(BEAM_T + 0.4, "s_sparkle_teal", "LRH", BEAM_END - BEAM_T - 1.0, spin=40, size=12)
    sh.fx(BEAM_T, "jugadores", modo="brillo", dur=8, radio=80)
    i, s = section_at(BEAM_END + 0.01)
    sh.look(BEAM_END, role_look(s["role"] if s["role"] in ("intro", "verse", "chorus", "climax", "break", "outro") else "verse"))
    # aviso de cada LOVE SHOT! en los acentos fuertes de los estribillos
    for t, h in d["accents"]:
        i, s = section_at(t)
        if s["role"] == "chorus" and h > 8 and not (BEAM_OFF < t < BEAM_END):
            sh.spr(t, "t_loveshot", "T", 1.8, size=26, pulse=0.3)
    # "volar" no: el rayo ya es el momento. El confeti cae en el climax final
    sh.fx(171.0, "jugadores", modo="confeti", dur=20, radio=80)


def extra_rooms(sh, d, section_at, role_look):
    """en la pausa se cortan las luces: luz de emergencia roja y ojos detras del publico"""
    RED_LOOK = dict(scene="starfield", floor="off", eq=0.0, beams="off", ring=False, col=["#ff2020", "#7a0a0a", "#ffb0a0"], react=dict(kick=0.5, snare=0.3, accent=0.4))
    sh.look(85.1, RED_LOOK)
    sh.spr(86.0, "s_eyes", "XL", 11.0, size=18, pulse=0.0, bob=0.4)
    sh.spr(89.0, "s_eyes", "X", 8.0, size=24, pulse=0.0, bob=0.4)
    sh.spr(92.0, "s_eyes", "XR", 5.0, size=18, pulse=0.0, bob=0.4)
    i, s = section_at(97.6)
    sh.look(97.5, role_look(s["role"] if s["role"] in ("intro", "verse", "chorus", "climax", "break", "outro") else "verse"))
    sh.fx(97.6, "flash")
    sh.fx(97.6, "shock")


EXTRAS = {"magical_cure": extra_magical, "looping_the_rooms": extra_rooms}

for nm, pl in PLANS.items():
    build(nm, pl, EXTRAS.get(nm))


# ----------------------------------------------------------------------------------------------- apertura (OBSCURE LIVE)
def intro():
    T = json.load(open(os.path.join(SRC, "intro.timing.json")))
    BEAT, BAR, CNT, CUT, IMP, DUR = T["beat"], T["bar"], T["count"], T["cut"], T["impact"], T["dur"]
    sh = Show("intro")
    DARK = dict(scene="starfield", floor="off", eq=0, beams="off", ring=False, col=[TEAL, "#0e4a47", "#bff5f0"], react=dict(kick=0.3, snare=0.1, accent=0.3))
    sh.look(0.0, DARK)
    sh.spr(0.6, "t_obscure", "T", 8.0, size=27, pulse=0.08)
    # latido: el bombo hace crecer el piso y el aro
    sh.look(4.8, dict(scene="starfield", floor="grid", eq=0, beams="off", ring=True, col=[TEAL, "#1f8f87", "#d8fffb"], react=dict(kick=1.0, snare=0.2, accent=0.5)))
    for k, (tp, sid, slot) in enumerate([(3 * BAR, "u2_chibi", "L2"), (3 * BAR + 2 * BEAT, "u2_fly", "R2"), (4 * BAR, "u2_swirl", "L3"), (4 * BAR + 2 * BEAT, "u2_wink", "R3")]):
        sh.spr(tp, sid, slot, CNT[0] - tp - 0.2, size=15, pulse=0.2)
        sh.fx(tp, "shock")
    # cuenta 1 . 2 . 3
    sh.look(CNT[0] - 0.05, dict(scene="magnetosphere", floor="ripple", eq=0.8, beams="fan", ring=True, col=[TEAL, PINK, GOLD], react=dict(kick=1.0, snare=0.5, accent=0.9)))
    sh.spr(CNT[0] - 0.05, "s_magiccircle", "C", CUT - CNT[0], size=34, grow=0.3, spin=35, pulse=0.12)
    for i, tc in enumerate(CNT):
        sh.spr(tc, f"t_321_{i + 1}", "C", BAR - 0.2, size=15 + 7 * i, pulse=0.3)
        sh.fx(tc, "flash")
        sh.fx(tc, "shock")
    # la estrella de Miku sobrevuela todo el cielo y cae en el escenario justo antes del corte
    sh.fx(CUT - 3.6, "estrella", col=TEAL, dur=3.4, side=1)
    # redoble: destellos cada vez mas rapidos
    b8 = 8 * BAR
    tt = b8
    while tt < b8 + BAR - BEAT / 2:
        sh.fx(tt, "flash")
        tt += BEAT / (2 + 6 * (tt - b8) / BAR)
    sh.fx(CUT, "blackout", dur=round(IMP - CUT - 0.05, 2))
    # impacto: explota todo
    sh.look(IMP, dict(scene="fireworks", floor="disco", eq=1.3, beams="cross", ring=True, col=[TEAL, PINK, GOLD], react=dict(kick=1.0, snare=0.7, accent=1.0)))
    sh.fx(IMP, "flash")
    sh.fx(IMP, "nova")
    sh.fx(IMP, "shock")
    sh.fx(IMP, "pyro", dur=4.0)
    sh.spr(IMP + 0.1, "t_go", "T", 4.0, size=24, pulse=0.3)
    sh.save()


def short(name, cues, look0):
    sh = Show(name)
    sh.look(0.0, look0)
    for c in cues:
        sh.cues.append(c)
    sh.save()


intro()

# intermedio entre canciones: cielo cosmico tranquilo
short("intermedio", [dict(t=0.5, fx="sprite", id="s_sparkle_teal", slot="LRH", dur=30, spin=12, size=11)],
      L("starfield", "off", 0.0, "off", True, [TEAL, "#7a5cff", "#d8fffb"], 0.15, 0.05, 0.2))
# antes de Mesmerizer entra Teto, antes de Triple Baka entra Neru: estrella de su color
short("pre_mesmerizer", [dict(t=0.2, fx="estrella", col="#e0324b", dur=3.0, side=-1), dict(t=3.2, fx="pyro", dur=2.5, col="#e0324b")],
      L("kaleidoscope", "ripple", 0.6, "slow", True, [RED, VIO, "#ffd0d8"], 0.6, 0.3, 0.6))
short("pre_triple_baka", [dict(t=0.2, fx="estrella", col="#f2d21b", dur=3.0, side=1), dict(t=3.2, fx="pyro", dur=2.5, col="#f2d21b")],
      L("spectrum", "ripple", 0.6, "slow", True, [GOLD, TEAL, RED], 0.6, 0.3, 0.6))

# despedida de las tres: fuegos de colores a lo largo de los tiempos del datapack (2, 4.2, 7.5, 9, 10.5 y 13 s)
FIN = [dict(t=0.0, fx="sprite", id="t_gracias", slot="T", dur=15.0, size=30, pulse=0.06),
       dict(t=0.8, fx="sprite", id="u3_trio_wave", slot="C", dur=14.0, size=30),
       dict(t=1.0, fx="sprite", id="s_heart", slot="LR1", dur=13.5, size=16),
       dict(t=1.4, fx="sprite", id="s_sparkle_gold", slot="LRH", dur=13.0, size=11, spin=30),
       dict(t=0.0, fx="jugadores", modo="confeti", dur=16, radio=80)]
for t, c in [(2.0, GOLD), (4.2, TEAL), (7.5, RED), (9.0, PINK), (10.5, TEAL), (13.0, WHITE)]:
    FIN += [dict(t=t, fx="pyro", dur=2.4, col=c), dict(t=t, fx="flash"), dict(t=t, fx="nova")]
short("despedida", FIN, L("fireworks", "ripple", 1.0, "fan", True, [PINK, TEAL, GOLD], 0.8, 0.4, 0.8))
