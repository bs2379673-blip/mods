"""Pasa el datapack MikuShow del Custom Skybox al mod concierto.
Uso: python3 patch_datapack.py <datapack original (carpeta con pack.mcmeta)> <salida>
- Saca TODOS los comandos de customskyboxes (cielos, stickers, portales, precarga).
- Arranca/corta el mod concierto junto con cada cancion (mismo tick que el audio).
- Estrella de Miku / Teto / Neru que cae al escenario justo antes de que aparezca cada una + contorno brillante del color de cada una.
- Mantiene intactos los bailes, la musica, las bailarinas, las marcas, el microfono y el celular."""
import os, re, shutil, sys

src, dst = sys.argv[1], sys.argv[2]
if os.path.exists(dst):
    shutil.rmtree(dst)
shutil.copytree(src, dst)
F = os.path.join(dst, "data", "mikushow", "functions")


def rd(p):
    return open(os.path.join(F, p), encoding="utf-8").read()


def wr(p, s):
    os.makedirs(os.path.dirname(os.path.join(F, p)), exist_ok=True)
    open(os.path.join(F, p), "w", encoding="utf-8", newline="\n").write(s)


# 1) fuera customskyboxes
removed = 0
for root, _, files in os.walk(F):
    for fn in files:
        if not fn.endswith(".mcfunction"):
            continue
        p = os.path.join(root, fn)
        lines = open(p, encoding="utf-8").read().split("\n")
        keep = []
        for ln in lines:
            if "customskyboxes" in ln:
                removed += 1
                continue
            keep.append(ln)
        open(p, "w", encoding="utf-8", newline="\n").write("\n".join(keep))
print("lineas de customskyboxes sacadas:", removed)

# 2) la precarga de texturas ya no hace falta
shutil.rmtree(os.path.join(F, "internal", "precarga"), ignore_errors=True)
wr("precargar.mcfunction", "# (ya no hace falta precargar: el mod concierto trae sus propias texturas)\ntellraw @s {\"text\":\"[MikuShow] los cielos y stickers vienen en el mod concierto: no hay nada que precargar\",\"color\":\"aqua\"}\n")

# 3) escenario del mod concierto sobre las marcas de las bailarinas
wr("concierto/escenario.mcfunction", "# el escenario del mod concierto queda en la marca de las bailarinas (escala 4) y mira hacia el publico\n"
   "execute as @e[type=marker,tag=mikushow_s4,limit=1] at @s run concierto escenario aqui 4\n"
   "execute unless entity @e[type=marker,tag=mikushow_s4] as @e[type=marker,tag=mikushow_spot,limit=1] at @s run concierto escenario aqui 4\n")

# 4) cada cancion arranca el mod junto con el audio
SONGS = ["intro", "static", "konton_boogie", "melt", "magical_cure", "looping_the_rooms", "anamanaguchi", "minna_miku_miku", "mesmerizer", "triple_baka"]
for s in SONGS:
    p = f"song/{s}/start.mcfunction"
    t = rd(p)
    out = []
    for ln in t.split("\n"):
        out.append(ln)
        if f"playsound mikushow:show.{s} " in ln:
            out.append("function mikushow:concierto/escenario")
            out.append(f"concierto iniciar {s} auto false")
    wr(p, "\n".join(out))

# 5) corte, intermedio, despedida y previas
t = rd("internal/reset.mcfunction")
wr("internal/reset.mcfunction", t.rstrip("\n") + "\nconcierto detener\n")
wr("internal/intermedio.mcfunction", "# entre canciones: cielo cosmico tranquilo\nconcierto iniciar intermedio auto false\n")
t = rd("internal/final.mcfunction")
wr("internal/final.mcfunction", "function mikushow:concierto/escenario\nconcierto iniciar despedida auto false\n" + t)
t = rd("pre/mesmerizer.mcfunction")
wr("pre/mesmerizer.mcfunction", "function mikushow:concierto/escenario\nconcierto iniciar pre_mesmerizer auto false\n" + t)
t = rd("pre/triple_baka.mcfunction")
wr("pre/triple_baka.mcfunction", "function mikushow:concierto/escenario\nconcierto iniciar pre_triple_baka auto false\n" + t)
t = rd("config/destellos_off.mcfunction")
wr("config/destellos_off.mcfunction", "concierto estrobo 0\n" + t)
t = rd("config/destellos_on.mcfunction")
wr("config/destellos_on.mcfunction", "concierto estrobo 0.5\n" + t)

# 6) contorno brillante del color de cada una al aparecer (la estrella la tira el guion del mod, o entrada_* a mano)
GLOW = {
    "aparecer": ("mks_miku", "aqua"),
    "aparece_teto": ("mks_teto", "red"),
    "aparece_neru": ("mks_neru", "yellow"),
}
for fn, (tag, color) in GLOW.items():
    p = f"bailarina/{fn}.mcfunction"
    t = rd(p)
    add = (f"team add {tag}\nteam modify {tag} color {color}\nteam join {tag} @e[type=dancers:dancer,tag={tag}]\n"
           f"data modify entity @e[type=dancers:dancer,tag={tag}] Glowing set value 1b\n"
           "schedule function mikushow:bailarina/sin_brillo 7s replace\n")
    wr(p, t.rstrip("\n") + "\n" + add)
wr("bailarina/sin_brillo.mcfunction", "# se apaga el contorno de las bailarinas\ndata modify entity @e[type=dancers:dancer,tag=mikushow] Glowing set value 0b\n")
# entrada a mano: estrella + aparicion a los 3 s (cuando cae)
for nm, col, fn, tag in (("miku", "miku", "aparecer", ""), ("teto", "teto", "aparece_teto", ""), ("neru", "neru", "aparece_neru", "")):
    wr(f"bailarina/entrada_{nm}.mcfunction",
       f"# estrella de {nm} que cae al escenario y aparece\nfunction mikushow:concierto/escenario\nconcierto estrella {col} ~ ~ ~ 3\nschedule function mikushow:bailarina/{fn} 3s append\n")
    # estrella en la posicion del escenario: se usa stageOr (sin pos) -> ver abajo
for nm in ("miku", "teto", "neru"):
    p = f"bailarina/entrada_{nm}.mcfunction"
    t = rd(p).replace(f"concierto estrella {nm} ~ ~ ~ 3", f"execute as @e[type=marker,tag=mikushow_s4,limit=1] at @s run concierto estrella {nm} ~ ~ ~ 3")
    wr(p, t)

# 7) el stop tambien frena los efectos
t = rd("stop.mcfunction")
wr("stop.mcfunction", t.rstrip("\n") + "\nschedule clear mikushow:bailarina/sin_brillo\nconcierto jugadores nada\n")

# 8) leeme
t = open(os.path.join(dst, "data", "mikushow", "functions", "..", "..", "..", "pack.mcmeta"), encoding="utf-8").read()
print("listo")
