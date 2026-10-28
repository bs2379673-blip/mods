#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Video a baile - Annis World
Convierte un video de una persona bailando en un archivo .bvh (captura de movimiento)
que el mod Annis NPC importa con brazos, piernas, codos, rodillas, cabeza y giros.

Uso con ventana:   python video_a_baile.py
Uso por consola:   python video_a_baile.py video.mp4 [--salida baile.bvh] [--desde 2] [--hasta 30]
                   [--calidad lite|full|heavy] [--suavizado 0-3] [--preview]
"""
import argparse
import json
import math
import os
import sys
import threading
import time
import urllib.request

import numpy as np

MODELOS = {
    "lite": "https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/latest/pose_landmarker_lite.task",
    "full": "https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_full/float16/latest/pose_landmarker_full.task",
    "heavy": "https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_heavy/float16/latest/pose_landmarker_heavy.task",
}
AQUI = os.path.dirname(os.path.abspath(__file__))
AJUSTES = os.path.join(AQUI, "ajustes.json")

# indices de MediaPipe Pose (33 puntos)
NARIZ, OREJA_I, OREJA_D = 0, 7, 8
HOMBRO_I, HOMBRO_D, CODO_I, CODO_D, MUNECA_I, MUNECA_D = 11, 12, 13, 14, 15, 16
CADERA_I, CADERA_D, RODILLA_I, RODILLA_D, TOBILLO_I, TOBILLO_D = 23, 24, 25, 26, 27, 28
TALON_I, TALON_D, PUNTA_I, PUNTA_D = 29, 30, 31, 32
BOCA_I, BOCA_D, OJO_I, OJO_D = 9, 10, 2, 5

# esqueleto del BVH: (nombre, padre, como calcular la posicion)
ESQUELETO = [
    ("Hips", None, ("mid", CADERA_I, CADERA_D)),
    ("UpperLeg_L", "Hips", ("pt", CADERA_I)),
    ("LowerLeg_L", "UpperLeg_L", ("pt", RODILLA_I)),
    ("Foot_L", "LowerLeg_L", ("pt", TOBILLO_I)),
    ("Toes_L", "Foot_L", ("pt", PUNTA_I)),
    ("UpperLeg_R", "Hips", ("pt", CADERA_D)),
    ("LowerLeg_R", "UpperLeg_R", ("pt", RODILLA_D)),
    ("Foot_R", "LowerLeg_R", ("pt", TOBILLO_D)),
    ("Toes_R", "Foot_R", ("pt", PUNTA_D)),
    ("Neck", "Hips", ("mid", HOMBRO_I, HOMBRO_D)),
    ("Head", "Neck", ("mid", OREJA_I, OREJA_D)),
    ("UpperArm_L", "Neck", ("pt", HOMBRO_I)),
    ("LowerArm_L", "UpperArm_L", ("pt", CODO_I)),
    ("Hand_L", "LowerArm_L", ("pt", MUNECA_I)),
    ("UpperArm_R", "Neck", ("pt", HOMBRO_D)),
    ("LowerArm_R", "UpperArm_R", ("pt", CODO_D)),
    ("Hand_R", "LowerArm_R", ("pt", MUNECA_D)),
]
CONEXIONES_2D = [(11, 12), (11, 13), (13, 15), (12, 14), (14, 16), (11, 23), (12, 24), (23, 24),
                 (23, 25), (25, 27), (27, 31), (24, 26), (26, 28), (28, 32), (7, 8), (0, 7), (0, 8)]


def log_por_defecto(msg):
    print(msg, flush=True)


def descargar_modelo(calidad, log):
    carpeta = os.path.join(AQUI, "modelos")
    os.makedirs(carpeta, exist_ok=True)
    ruta = os.path.join(carpeta, "pose_landmarker_%s.task" % calidad)
    if os.path.isfile(ruta) and os.path.getsize(ruta) > 1000:
        return ruta
    log("Descargando el modelo de deteccion (%s), solo la primera vez..." % calidad)
    tmp = ruta + ".part"
    urllib.request.urlretrieve(MODELOS[calidad], tmp)
    os.replace(tmp, ruta)
    return ruta


# ---------------------------------------------------------------- deteccion

def detectar(video, desde, hasta, calidad, preview, log, progreso):
    import cv2
    import mediapipe as mp
    from mediapipe.tasks.python import vision
    from mediapipe.tasks.python.core.base_options import BaseOptions

    modelo = descargar_modelo(calidad, log)
    cap = cv2.VideoCapture(video)
    if not cap.isOpened():
        raise RuntimeError("No se pudo abrir el video")
    fps = cap.get(cv2.CAP_PROP_FPS) or 30.0
    total = int(cap.get(cv2.CAP_PROP_FRAME_COUNT) or 0)
    ini = int(max(0.0, desde) * fps)
    fin = int(hasta * fps) if hasta and hasta > 0 else (total if total > 0 else 10 ** 9)
    # como mucho 30 fotogramas por segundo (mas no mejora y tarda el doble)
    paso = max(1, int(round(fps / 30.0)))
    fps_salida = fps / paso
    if ini > 0:
        cap.set(cv2.CAP_PROP_POS_FRAMES, ini)

    opciones = vision.PoseLandmarkerOptions(
        base_options=BaseOptions(model_asset_path=modelo),
        running_mode=vision.RunningMode.VIDEO,
        num_poses=1,
        min_pose_detection_confidence=0.5,
        min_pose_presence_confidence=0.5,
        min_tracking_confidence=0.5)

    mundo = []      # (33, 4): x, y, z, visibilidad  (metros, centrado en la cadera)
    pantalla = []   # (33, 3): x, y normalizados y visibilidad
    escritor = None
    n = ini
    hechos = 0
    with vision.PoseLandmarker.create_from_options(opciones) as detector:
        while n < fin:
            ok, frame = cap.read()
            if not ok:
                break
            if (n - ini) % paso != 0:
                n += 1
                continue
            h, w = frame.shape[:2]
            escala = min(1.0, 1280.0 / max(h, w))
            if escala < 1.0:
                frame = cv2.resize(frame, (int(w * escala), int(h * escala)))
            rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            imagen = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb)
            ts = int((n - ini) * 1000.0 / fps)
            res = detector.detect_for_video(imagen, ts)
            if res.pose_world_landmarks:
                wl = res.pose_world_landmarks[0]
                il = res.pose_landmarks[0]
                mundo.append(np.array([[p.x, p.y, p.z, p.visibility if p.visibility is not None else 1.0] for p in wl]))
                pantalla.append(np.array([[p.x, p.y, p.visibility if p.visibility is not None else 1.0] for p in il]))
            else:
                mundo.append(None)
                pantalla.append(None)
            if preview:
                if escritor is None:
                    fourcc = cv2.VideoWriter_fourcc(*"mp4v")
                    escritor = cv2.VideoWriter(preview, fourcc, fps_salida, (frame.shape[1], frame.shape[0]))
                dibujar(frame, pantalla[-1])
                escritor.write(frame)
            hechos += 1
            if total > 0:
                progreso(min(1.0, (n - ini) / max(1, (min(fin, total) - ini))))
            if hechos % 30 == 0:
                log("  %d fotogramas analizados..." % hechos)
            n += 1
    cap.release()
    if escritor is not None:
        escritor.release()
    return mundo, fps_salida


def dibujar(frame, pts):
    import cv2
    if pts is None:
        cv2.putText(frame, "sin persona", (20, 40), cv2.FONT_HERSHEY_SIMPLEX, 1.0, (0, 0, 255), 2)
        return
    h, w = frame.shape[:2]
    for a, b in CONEXIONES_2D:
        if pts[a][2] > 0.4 and pts[b][2] > 0.4:
            pa = (int(pts[a][0] * w), int(pts[a][1] * h))
            pb = (int(pts[b][0] * w), int(pts[b][1] * h))
            color = (60, 200, 60) if (a % 2 == 1 and a > 10) else (230, 120, 40)
            cv2.line(frame, pa, pb, color, 3)
    for i in range(33):
        if pts[i][2] > 0.4:
            cv2.circle(frame, (int(pts[i][0] * w), int(pts[i][1] * h)), 3, (255, 255, 255), -1)


# ---------------------------------------------------------------- limpieza

def rellenar_y_suavizar(mundo, fps, suavizado, log):
    n = len(mundo)
    validos = [i for i, m in enumerate(mundo) if m is not None]
    if len(validos) < max(5, n * 0.2):
        raise RuntimeError("Casi no se detecto a la persona (%d de %d fotogramas). "
                           "Prueba con un video con el cuerpo entero visible y buena luz." % (len(validos), n))
    log("Persona detectada en %d de %d fotogramas" % (len(validos), n))
    # recortar el principio y el final sin deteccion
    a, b = validos[0], validos[-1]
    mundo = mundo[a:b + 1]
    n = len(mundo)
    datos = np.full((n, 33, 3), np.nan)
    for i, m in enumerate(mundo):
        if m is None:
            continue
        for j in range(33):
            # puntos poco visibles: mejor inventarlos interpolando que usar un dato malo
            if m[j][3] >= 0.35 or j in (CADERA_I, CADERA_D, HOMBRO_I, HOMBRO_D):
                datos[i, j] = m[j][:3]
    t = np.arange(n)
    for j in range(33):
        for c in range(3):
            col = datos[:, j, c]
            ok = ~np.isnan(col)
            if ok.sum() == 0:
                datos[:, j, c] = 0.0
            elif ok.sum() < n:
                datos[:, j, c] = np.interp(t, t[ok], col[ok])
    # filtro gaussiano en el tiempo (sin desfase): quita el temblor de la deteccion
    sigma = [0.0, 1.0, 1.8, 2.8][max(0, min(3, int(suavizado)))] * fps / 30.0
    if sigma > 0:
        r = int(math.ceil(sigma * 3))
        k = np.exp(-0.5 * (np.arange(-r, r + 1) / sigma) ** 2)
        k /= k.sum()
        pad = np.pad(datos, ((r, r), (0, 0), (0, 0)), mode="edge")
        datos = np.stack([np.tensordot(k, pad[i:i + 2 * r + 1], axes=(0, 0)) for i in range(n)])
    # MediaPipe: x a la derecha de la imagen, y hacia abajo, z hacia la camara negativa.
    # BVH: Y arriba. Pasamos a centimetros.
    datos = np.stack([datos[..., 0], -datos[..., 1], -datos[..., 2]], axis=-1) * 100.0
    # apoyar en el suelo: el pie mas bajo toca y=0 (asi el rebote de cadera se conserva)
    pies = datos[:, [TOBILLO_I, TOBILLO_D, TALON_I, TALON_D, PUNTA_I, PUNTA_D], 1]
    suelo = pies.min(axis=1)
    if sigma > 0:
        suelo = np.convolve(np.pad(suelo, (r, r), mode="edge"), k, mode="valid")
    datos[..., 1] -= suelo[:, None]
    return datos


# ---------------------------------------------------------------- BVH

def posicion(frame, regla):
    if regla[0] == "mid":
        return (frame[regla[1]] + frame[regla[2]]) / 2.0
    return frame[regla[1]]


def rot_cabeza(frame):
    """Matriz de la cabeza: columnas izquierda, arriba, delante (en el sistema del BVH)."""
    izq = frame[OREJA_I] - frame[OREJA_D]
    centro = (frame[OREJA_I] + frame[OREJA_D]) / 2.0
    delante = frame[NARIZ] - centro
    x = izq / (np.linalg.norm(izq) + 1e-9)
    z = delante - np.dot(delante, x) * x
    z = z / (np.linalg.norm(z) + 1e-9)
    y = np.cross(z, x)
    return np.stack([x, y, z], axis=1)


def euler_zxy(R):
    """R = Rz(a) * Rx(b) * Ry(c), en grados (orden de canales Zrotation Xrotation Yrotation)."""
    b = math.asin(max(-1.0, min(1.0, R[2, 1])))
    c = math.atan2(-R[2, 0], R[2, 2])
    a = math.atan2(-R[0, 1], R[1, 1])
    return math.degrees(a), math.degrees(b), math.degrees(c)


def escribir_bvh(datos, fps, ruta):
    n = len(datos)
    pos = {}
    for nombre, padre, regla in ESQUELETO:
        pos[nombre] = np.array([posicion(datos[i], regla) for i in range(n)])
    # cabeza: rotacion relativa al primer fotograma (el importador toma el primero como "mirando al frente")
    rots = [rot_cabeza(datos[i]) for i in range(n)]
    hijos = {}
    for nombre, padre, _ in ESQUELETO:
        hijos.setdefault(padre, []).append(nombre)

    lineas = ["HIERARCHY"]

    def escribir(nombre, nivel):
        sangria = "  " * nivel
        padre = [p for (nm, p, _) in ESQUELETO if nm == nombre][0]
        off = pos[nombre][0] - (pos[padre][0] if padre else 0)
        lineas.append(sangria + ("ROOT " if padre is None else "JOINT ") + nombre)
        lineas.append(sangria + "{")
        lineas.append(sangria + "  OFFSET %.4f %.4f %.4f" % tuple(off))
        if padre is None or nombre == "Head":
            lineas.append(sangria + "  CHANNELS 6 Xposition Yposition Zposition Zrotation Xrotation Yrotation")
        else:
            lineas.append(sangria + "  CHANNELS 3 Xposition Yposition Zposition")
        for h in hijos.get(nombre, []):
            escribir(h, nivel + 1)
        if not hijos.get(nombre):
            lineas.append(sangria + "  End Site")
            lineas.append(sangria + "  {")
            lineas.append(sangria + "    OFFSET 0 0 0")
            lineas.append(sangria + "  }")
        lineas.append(sangria + "}")

    escribir("Hips", 0)
    lineas.append("MOTION")
    lineas.append("Frames: %d" % n)
    lineas.append("Frame Time: %.6f" % (1.0 / fps))
    orden = []

    def recorrer(nombre):
        orden.append(nombre)
        for h in hijos.get(nombre, []):
            recorrer(h)

    recorrer("Hips")
    padres = {nm: p for (nm, p, _) in ESQUELETO}
    for i in range(n):
        vals = []
        for nombre in orden:
            p = padres[nombre]
            local = pos[nombre][i] - (pos[p][i] if p else 0)
            vals += ["%.4f" % v for v in local]
            if p is None:
                vals += ["0", "0", "0"]
            elif nombre == "Head":
                vals += ["%.4f" % v for v in euler_zxy(rots[i])]
        lineas.append(" ".join(vals))
    with open(ruta, "w", encoding="utf-8") as f:
        f.write("\n".join(lineas) + "\n")


def convertir(video, salida, desde=0.0, hasta=0.0, calidad="full", suavizado=2, preview=False,
              log=log_por_defecto, progreso=lambda x: None):
    t0 = time.time()
    log("Analizando %s ..." % os.path.basename(video))
    prev = os.path.splitext(salida)[0] + "_comprobacion.mp4" if preview else None
    mundo, fps = detectar(video, desde, hasta, calidad, prev, log, progreso)
    datos = rellenar_y_suavizar(mundo, fps, suavizado, log)
    escribir_bvh(datos, fps, salida)
    log("Listo en %.0f s: %s (%.1f s de baile)" % (time.time() - t0, salida, len(datos) / fps))
    if prev:
        log("Video de comprobacion: %s" % prev)
    log("En el juego: Editor del NPC > Escena > Importar baile (o /npcimport \"%s\")" % os.path.basename(salida))
    return salida


# ---------------------------------------------------------------- ventana

def carpeta_minecraft_por_defecto():
    appdata = os.environ.get("APPDATA")
    candidatos = []
    if appdata:
        candidatos.append(os.path.join(appdata, ".minecraft", "config", "annisnpc", "import"))
    candidatos.append(os.path.join(os.path.expanduser("~"), ".minecraft", "config", "annisnpc", "import"))
    for c in candidatos:
        if os.path.isdir(c):
            return c
    return ""


def cargar_ajustes():
    try:
        with open(AJUSTES, encoding="utf-8") as f:
            return json.load(f)
    except Exception:
        return {}


def guardar_ajustes(d):
    try:
        with open(AJUSTES, "w", encoding="utf-8") as f:
            json.dump(d, f, indent=2, ensure_ascii=False)
    except Exception:
        pass


def ventana():
    import tkinter as tk
    from tkinter import filedialog, messagebox, ttk

    aj = cargar_ajustes()
    root = tk.Tk()
    root.title("Video a baile - Annis World")
    root.geometry("620x520")
    pad = {"padx": 8, "pady": 4}

    v_video = tk.StringVar()
    v_destino = tk.StringVar(value=aj.get("destino") or carpeta_minecraft_por_defecto())
    v_nombre = tk.StringVar()
    v_desde = tk.StringVar(value="0")
    v_hasta = tk.StringVar(value="0")
    v_calidad = tk.StringVar(value=aj.get("calidad", "full"))
    v_suav = tk.IntVar(value=aj.get("suavizado", 2))
    v_prev = tk.BooleanVar(value=aj.get("preview", True))

    def elegir_video():
        p = filedialog.askopenfilename(title="Elige el video", filetypes=[("Videos", "*.mp4 *.mov *.avi *.mkv *.webm"), ("Todos", "*.*")])
        if p:
            v_video.set(p)
            if not v_nombre.get():
                base = os.path.splitext(os.path.basename(p))[0].lower()
                v_nombre.set("".join(ch if ch.isalnum() else "_" for ch in base)[:40])

    def elegir_destino():
        p = filedialog.askdirectory(title="Carpeta config/annisnpc/import de tu Minecraft")
        if p:
            v_destino.set(p)

    f = ttk.Frame(root)
    f.pack(fill="x", **pad)
    ttk.Label(f, text="Video:").grid(row=0, column=0, sticky="w")
    ttk.Entry(f, textvariable=v_video, width=55).grid(row=0, column=1, sticky="we")
    ttk.Button(f, text="Elegir...", command=elegir_video).grid(row=0, column=2)
    ttk.Label(f, text="Nombre del baile:").grid(row=1, column=0, sticky="w")
    ttk.Entry(f, textvariable=v_nombre, width=30).grid(row=1, column=1, sticky="w")
    ttk.Label(f, text="Guardar en:").grid(row=2, column=0, sticky="w")
    ttk.Entry(f, textvariable=v_destino, width=55).grid(row=2, column=1, sticky="we")
    ttk.Button(f, text="Elegir...", command=elegir_destino).grid(row=2, column=2)
    ttk.Label(f, text="(la carpeta config/annisnpc/import de tu Minecraft)", foreground="#666").grid(row=3, column=1, sticky="w")

    g = ttk.Frame(root)
    g.pack(fill="x", **pad)
    ttk.Label(g, text="Desde (s):").grid(row=0, column=0)
    ttk.Entry(g, textvariable=v_desde, width=6).grid(row=0, column=1)
    ttk.Label(g, text="Hasta (s, 0 = final):").grid(row=0, column=2)
    ttk.Entry(g, textvariable=v_hasta, width=6).grid(row=0, column=3)
    ttk.Label(g, text="Calidad:").grid(row=1, column=0)
    ttk.Combobox(g, textvariable=v_calidad, values=["lite", "full", "heavy"], width=8, state="readonly").grid(row=1, column=1)
    ttk.Label(g, text="Suavizado (0-3):").grid(row=1, column=2)
    ttk.Spinbox(g, from_=0, to=3, textvariable=v_suav, width=5).grid(row=1, column=3)
    ttk.Checkbutton(g, text="Crear video de comprobacion (esqueleto dibujado)", variable=v_prev).grid(row=2, column=0, columnspan=4, sticky="w")

    barra = ttk.Progressbar(root, maximum=1.0)
    barra.pack(fill="x", **pad)
    texto = tk.Text(root, height=14)
    texto.pack(fill="both", expand=True, **pad)

    def log(msg):
        root.after(0, lambda: (texto.insert("end", msg + "\n"), texto.see("end")))

    def progreso(x):
        root.after(0, lambda: barra.configure(value=x))

    boton = ttk.Button(root, text="Convertir")

    def empezar():
        video = v_video.get().strip()
        if not os.path.isfile(video):
            messagebox.showerror("Video a baile", "Elige un video primero")
            return
        destino = v_destino.get().strip() or os.path.dirname(video)
        os.makedirs(destino, exist_ok=True)
        nombre = v_nombre.get().strip() or "baile"
        salida = os.path.join(destino, nombre + ".bvh")
        guardar_ajustes({"destino": v_destino.get().strip(), "calidad": v_calidad.get(),
                         "suavizado": int(v_suav.get()), "preview": bool(v_prev.get())})
        boton.configure(state="disabled")

        def trabajo():
            try:
                convertir(video, salida, float(v_desde.get() or 0), float(v_hasta.get() or 0),
                          v_calidad.get(), int(v_suav.get()), bool(v_prev.get()), log, progreso)
                progreso(1.0)
            except Exception as e:
                log("ERROR: %s" % e)
            finally:
                root.after(0, lambda: boton.configure(state="normal"))

        threading.Thread(target=trabajo, daemon=True).start()

    boton.configure(command=empezar)
    boton.pack(**pad)
    log("Consejos: cuerpo entero en el video, una sola persona, buena luz y camara quieta.")
    log("La primera vez se descarga el modelo de deteccion (necesita internet).")
    root.mainloop()


def main():
    if len(sys.argv) == 1:
        ventana()
        return
    ap = argparse.ArgumentParser(description="Video a baile (BVH) para Annis NPC")
    ap.add_argument("video")
    ap.add_argument("--salida", default=None)
    ap.add_argument("--desde", type=float, default=0.0)
    ap.add_argument("--hasta", type=float, default=0.0)
    ap.add_argument("--calidad", choices=["lite", "full", "heavy"], default="full")
    ap.add_argument("--suavizado", type=int, default=2)
    ap.add_argument("--preview", action="store_true")
    a = ap.parse_args()
    salida = a.salida or os.path.splitext(a.video)[0] + ".bvh"
    convertir(a.video, salida, a.desde, a.hasta, a.calidad, a.suavizado, a.preview)


if __name__ == "__main__":
    main()
