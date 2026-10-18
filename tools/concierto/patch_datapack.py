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
# entrada a mano: estrella y aparicion en el impacto (todo lo hace el mod con el mismo reloj)
for nm in ("miku", "teto", "neru"):
    wr(f"bailarina/entrada_{nm}.mcfunction", f"# estrella de {nm} que cae al escenario y aparece en la explosion\nfunction mikushow:concierto/escenario\nconcierto entrada {nm}\n")

# 8) las bailarinas ya NO las invoca el datapack por su cuenta: las invoca el guion del mod en el instante exacto en que la estrella toca el escenario
#    (cue "aparecer" en los .json del mod, o /concierto entrada <quien>). Aca se saca todo disparo viejo y se crean auto_* con avisos.
p = "song/intro/b2.mcfunction"
t = rd(p)
assert "matches 432 run function mikushow:bailarina/aparecer_auto" in t
wr(p, t.replace("execute if score #t mikushow matches 432 run function mikushow:bailarina/aparecer_auto\n", "").replace("execute if score #t mikushow matches 432 run function mikushow:bailarina/aparecer_auto", ""))
for nm, fn in (("mesmerizer", "mesmerizer_2"), ("triple_baka", "triple_baka_2")):
    p = f"pre/{fn}.mcfunction"
    t = rd(p)
    wr(p, "\n".join(ln for ln in t.split("\n") if "bailarina/aparece_" not in ln))
for nm, fn in (("miku", "aparecer"), ("teto", "aparece_teto"), ("neru", "aparece_neru")):
    wr(f"bailarina/auto_{nm}.mcfunction",
       'execute unless score #baile mikushow matches 1 run tellraw @a {"text":"[MikuShow] los bailes estan APAGADOS: usa /function mikushow:config/bailes_on","color":"red"}\n'
       f"execute if score #baile mikushow matches 1 run function mikushow:bailarina/{fn}\n")

# 10) avisos en el chat cuando algo impide que aparezcan (antes fallaba en silencio)
NOMARK = 'execute unless entity @e[type=marker,tag=mikushow_spot] run tellraw @a {"text":"[MikuShow] NO HAY MARCA: parate donde bailan y usa /concierto marcar","color":"red"}\n'
for fn, tag, nm in (("aparecer", "mks_miku", "Miku"), ("aparece_teto", "mks_teto", "Teto"), ("aparece_neru", "mks_neru", "Neru")):
    p = f"bailarina/{fn}.mcfunction"
    t = rd(p).rstrip("\n")
    wr(p, NOMARK + t + "\n"
       f'execute unless entity @e[type=dancers:dancer,tag={tag}] run tellraw @a {{"text":"[MikuShow] no se pudo crear a {nm}: falta el mod dancers (servidor y clientes) o el comando dancer fallo","color":"red"}}\n')

# entrada a mano: estrella y aparicion en el impacto (todo lo hace el mod con el mismo reloj)
for nm in ("miku", "teto", "neru"):
    wr(f"bailarina/entrada_{nm}.mcfunction", f"# estrella de {nm} que cae al escenario y aparece en la explosion\nfunction mikushow:concierto/escenario\nconcierto entrada {nm}\n")

# 11) sin simbolos raros en los avisos de pantalla
for root, _, files in os.walk(F):
    for fn in files:
        if fn.endswith(".mcfunction"):
            p = os.path.join(root, fn)
            t = open(p, encoding="utf-8").read()
            if "\u266a" in t:
                open(p, "w", encoding="utf-8", newline="\n").write(t.replace("\u266a ", "").replace("\u266a", ""))

# 7) el stop tambien frena los efectos
t = rd("stop.mcfunction")
wr("stop.mcfunction", t.rstrip("\n") + "\nschedule clear mikushow:bailarina/sin_brillo\nconcierto jugadores nada\n")

# 8) prueba de 10 s con el mod
wr("prueba.mcfunction", "# prueba del escenario: piso, paneles, rayos, anillo, stickers y una estrella de Miku (hace falta el mod concierto)\nfunction mikushow:concierto/escenario\nconcierto iniciar prueba auto false\n")

# 9) leeme
open(os.path.join(dst, "LEEME.txt"), "w", encoding="utf-8").write(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "LEEME_datapack.txt"), encoding="utf-8").read())
print("listo")
