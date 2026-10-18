"""Guion de Melt (atardecer que se derrite: la mas tranquila). Sale de las secciones de audio/melt.v2.json.
Cada parte tiene UN look; si el estribillo vuelve, se ve igual. Se puede editar a mano el JSON resultante."""
import json, sys
src, dst = sys.argv[1], sys.argv[2]
d = json.load(open(src))
PINK = ["#ff6a9a", "#ff9a6a", "#ffd0e0"]
HOT = ["#ff4d94", "#ffb04d", "#fff0f5"]
LAV = ["#b48cff", "#ff9ac8", "#ffd9f0"]
LOOK = {
    "intro": dict(scene="sunset", floor="off", eq=0, beams="off", ring=True, col=PINK, react=dict(kick=0.3, snare=0.1, accent=0.5)),
    "verse": dict(scene="sunset", floor="grid", eq=0.55, beams="slow", ring=True, col=PINK, react=dict(kick=0.5, snare=0.25, accent=0.7)),
    "break": dict(scene="sunset", floor="off", eq=0, beams="off", ring=True, col=LAV, react=dict(kick=0.25, snare=0.1, accent=0.4)),
    "chorus": dict(scene="meltglow", floor="ripple", eq=1.0, beams="fan", ring=True, col=PINK, react=dict(kick=0.8, snare=0.45, accent=1.0)),
    "climax": dict(scene="meltglow", floor="disco", eq=1.2, beams="cross", ring=True, col=HOT, react=dict(kick=1.0, snare=0.6, accent=1.0)),
    "outro": dict(scene="sunset", floor="grid", eq=0.3, beams="off", ring=True, col=PINK, react=dict(kick=0.3, snare=0.1, accent=0.4)),
}
cues = []
for i, s in enumerate(d["sections"]):
    role = s["role"] if s["role"] in LOOK else "verse"
    t0 = round(s["t0"] if i else 0.0, 2)
    c = dict(t=t0, **LOOK[role])
    c["_parte"] = f"{role}"
    cues.append(c)
    if "drop" in s or (role in ("chorus", "climax") and i and d["sections"][i - 1]["role"] in ("verse", "break", "intro")):
        cues.append(dict(t=t0, fx="flash"))
        cues.append(dict(t=t0, fx="nova"))
        cues.append(dict(t=t0, fx="shock"))
# la subida a cada estribillo: pulsos de onda en el ultimo compas
for s in d["sections"]:
    if "build_from" in s:
        pass
for a, b in d.get("stops", []):
    cues.append(dict(t=round(a, 3), fx="blackout", dur=round(b - a + 0.05, 2)))
cues.sort(key=lambda c: c["t"])
json.dump(dict(song="melt", _nota="t = segundos desde que arranca la musica. Cada orden con 'scene/floor/eq/beams/ring/col/react' queda puesta hasta la siguiente. 'fx': flash, nova, shock, blackout(dur), column(col,size,dur).", cues=cues), open(dst, "w"), indent=1, ensure_ascii=False)
print(len(cues), "ordenes")
