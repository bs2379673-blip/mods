#!/usr/bin/env python3
"""Compone gestos nuevos de obscurenpc mezclando canales de los ya existentes (cuerpo/cabeza de uno, cada brazo de otro)."""
import json, os, bisect
D = os.path.join(os.path.dirname(__file__), "..", "..", "obscurenpc", "src", "main", "resources", "assets", "obscurenpc", "animmodels", "animations", "biped", "npc")
def load(n): return {j["name"]: j for j in json.load(open(os.path.join(D, n + ".json")))["animation"]}
ARM_R = ["Shoulder_R", "Arm_R", "Hand_R", "Tool_R", "Elbow_R"]
ARM_L = ["Shoulder_L", "Arm_L", "Hand_L", "Tool_L", "Elbow_L"]
HEAD = ["Head"]
def sample(j, t, T):
    # estira el clip de origen sobre la duracion T del nuevo
    ts = j["time"]; src = t / T * ts[-1]
    i = min(bisect.bisect_left(ts, src), len(ts) - 1)
    return j["transform"][i]
def make(name, T, base, head, armR, armL):
    clips = {k: load(k) for k in {base, head, armR, armL}}
    n = int(round(T * 30)) + 1
    times = [round(i / 30, 4) for i in range(n)]
    out = []
    for jn in clips[base]:
        if jn in HEAD: src = clips[head]
        elif jn in ARM_R: src = clips[armR]
        elif jn in ARM_L: src = clips[armL]
        else: src = clips[base]
        j = src[jn]
        out.append({"name": jn, "time": times, "transform": [sample(j, t, T) for t in times]})
    json.dump({"animation": out}, open(os.path.join(D, name + ".json"), "w"), separators=(",", ":"))
    print(name, n, "frames")
# nombre, duracion, cuerpo, cabeza, brazo der, brazo izq
make("scan", 2.4, "look_around", "look_around", "salute", "look_around")
make("plead", 2.2, "shrug", "nod", "shrug", "shrug")
make("nervous", 2.6, "hands_back", "look_around", "hands_back", "hands_back")
make("grieve", 2.4, "facepalm", "shake_head", "facepalm", "hands_hip")
make("defiant", 2.4, "cross_arms", "shake_head", "cross_arms", "cross_arms")
make("command", 2.0, "point", "nod", "point", "hands_hip")
make("listen", 2.8, "hands_back", "think", "hands_back", "hands_back")
