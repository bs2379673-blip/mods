#!/usr/bin/env python3
"""
Genera los modelos humanos base de StarScenes (humano = brazos anchos, humano_fino = brazos de 3 px)
con la forma y la textura de una skin de jugador (64x64) y animaciones propias (propiedad de
Starlevi): idle, walk, run, talk, wave, interact, seeplayer.
El esqueleto usa los mismos nombres y la misma forma que los modelos de NPC de jugador
(head, body, right_arm, left_arm, right_leg, left_leg; el derecho en +x), asi las animaciones de
otros modelos con ese esqueleto se pueden usar tal cual.
Uso: python3 humano.py <carpeta de salida>
"""
import json, sys, uuid, os

def uid(s):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, 'starscenes/humano/' + s))

def box_faces(u, v, w, h, d, e=0.02):
    """UV de caja como Blockbench (cara de frente = north)."""
    r = lambda a, b, c, dd: [a + e, b + e, c - e, dd - e]
    return {
        'north': {'uv': r(u + d, v + d, u + d + w, v + d + h), 'texture': 0},
        'east': {'uv': r(u, v + d, u + d, v + d + h), 'texture': 0},
        'south': {'uv': r(u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h), 'texture': 0},
        'west': {'uv': r(u + d + w, v + d, u + 2 * d + w, v + d + h), 'texture': 0},
        # arriba y abajo van dadas vuelta, como en Blockbench
        'up': {'uv': [u + d + w - e, v + d - e, u + d + e, v + e], 'texture': 0},
        'down': {'uv': [u + d + 2 * w - e, v + e, u + d + w + e, v + d - e], 'texture': 0},
    }

def cube(name, frm, size, uvo, inflate=0):
    x, y, z = frm
    w, h, d = size
    return {'name': name, 'uuid': uid(name), 'type': 'cube', 'box_uv': False, 'from': [x, y, z], 'to': [x + w, y + h, z + d],
            'origin': [0, 0, 0], 'inflate': inflate, 'faces': box_faces(uvo[0], uvo[1], w, h, d)}

def build(slim):
    aw = 3 if slim else 4
    els = [
        cube('Head', (-4, 24, -4), (8, 8, 8), (0, 0)),
        cube('Hat', (-4, 24, -4), (8, 8, 8), (32, 0), 0.5),
        cube('Body', (-4, 12, -2), (8, 12, 4), (16, 16)),
        cube('Jacket', (-4, 12, -2), (8, 12, 4), (16, 32), 0.25),
        # derecho en +x (como los modelos de NPC de jugador)
        cube('RightArm', (4, 12, -2), (aw, 12, 4), (40, 16)),
        cube('RightSleeve', (4, 12, -2), (aw, 12, 4), (40, 32), 0.25),
        cube('LeftArm', (-4 - aw, 12, -2), (aw, 12, 4), (32, 48)),
        cube('LeftSleeve', (-4 - aw, 12, -2), (aw, 12, 4), (48, 48), 0.25),
        cube('RightLeg', (0, 0, -2), (4, 12, 4), (0, 16)),
        cube('RightPants', (0, 0, -2), (4, 12, 4), (0, 32), 0.25),
        cube('LeftLeg', (-4, 0, -2), (4, 12, 4), (16, 48)),
        cube('LeftPants', (-4, 0, -2), (4, 12, 4), (0, 48), 0.25),
    ]
    e = {c['name']: c['uuid'] for c in els}
    arm_x = 4 + aw / 2

    def group(name, origin, children):
        return {'name': name, 'uuid': uid('g_' + name), 'origin': origin, 'rotation': [0, 0, 0], 'children': children}

    head = group('head', [0, 24, 0], [e['Head'], e['Hat']])
    rarm = group('right_arm', [arm_x, 22, 0], [e['RightArm'], e['RightSleeve']])
    larm = group('left_arm', [-arm_x, 22, 0], [e['LeftArm'], e['LeftSleeve']])
    body = group('body', [0, 12, 0], [e['Body'], e['Jacket'], rarm, head, larm])
    rleg = group('right_leg', [1.9, 12, 0], [e['RightLeg'], e['RightPants']])
    lleg = group('left_leg', [-1.9, 12, 0], [e['LeftLeg'], e['LeftPants']])
    player = group('player', [0, 0, 0], [lleg, rleg, body])
    hitbox = {'name': 'hitbox', 'uuid': uid('g_hitbox'), 'origin': [0, 0, 0], 'rotation': [0, 0, 0], 'children': [], 'visibility': False}
    tag = {'name': 'tag_name', 'uuid': uid('g_tag'), 'origin': [0, 34, 0], 'rotation': [0, 0, 0], 'children': []}
    bones = {'player': player, 'head': head, 'body': body, 'right_arm': rarm, 'left_arm': larm, 'right_leg': rleg, 'left_leg': lleg}
    return els, [player, hitbox, tag], bones

# ---------------------------------------------------------------- animaciones (en grados / pixeles)
# Convencion (igual que los modelos de NPC de jugador): x negativo = brazo/pierna hacia adelante o
# arriba; z positivo = brazo derecho hacia afuera (izquierdo: negativo); cabeza x positivo = mira abajo.

def kf(ch, t, x, y=0, z=0, interp='catmullrom'):
    return {'channel': ch, 'time': round(t, 4), 'interpolation': interp,
            'data_points': [{'x': round(x, 3), 'y': round(y, 3), 'z': round(z, 3)}]}

def anim(name, length, loop, tracks, bones):
    animators = {}
    for bone, keys in tracks.items():
        animators[bones[bone]['uuid']] = {'name': bone, 'type': 'bone', 'keyframes': keys}
    return {'uuid': uid('a_' + name), 'name': name, 'loop': loop, 'length': length, 'snapping': 24, 'animators': animators}

def animations(bones):
    A = []
    # quieto: respira, mira un poco alrededor, los brazos se mecen apenas
    A.append(anim('idle', 6, 'loop', {
        'player': [kf('position', 0, 0, 0), kf('position', 1.5, 0, -0.12), kf('position', 3, 0, 0), kf('position', 4.5, 0, -0.12), kf('position', 6, 0, 0)],
        'body': [kf('rotation', 0, 0), kf('rotation', 1.5, 1.2), kf('rotation', 3, 0), kf('rotation', 4.5, 1.2), kf('rotation', 6, 0)],
        'head': [kf('rotation', 0, 0, 0), kf('rotation', 1.2, 2, 14, 1.5), kf('rotation', 2.6, 1, 14, 1.5), kf('rotation', 3.6, 3, -12, -1.5),
                 kf('rotation', 5, 1, -10, -1), kf('rotation', 6, 0, 0)],
        'right_arm': [kf('rotation', 0, 0, 0, 3), kf('rotation', 1.5, -2, 0, 5), kf('rotation', 3, 0, 0, 3), kf('rotation', 4.5, -2, 0, 5), kf('rotation', 6, 0, 0, 3)],
        'left_arm': [kf('rotation', 0, 0, 0, -3), kf('rotation', 1.5, -2, 0, -5), kf('rotation', 3, 0, 0, -3), kf('rotation', 4.5, -2, 0, -5), kf('rotation', 6, 0, 0, -3)],
    }, bones))
    # caminar: piernas y brazos opuestos, un saltito en cada paso y el cuerpo gira apenas
    leg, arm = 30, 24
    A.append(anim('walk', 1.0, 'loop', {
        'player': [kf('position', t, 0, b) for t, b in ((0, 0), (0.25, 0.45), (0.5, 0), (0.75, 0.45), (1, 0))],
        'right_leg': [kf('rotation', 0, -leg), kf('rotation', 0.5, leg), kf('rotation', 1, -leg)],
        'left_leg': [kf('rotation', 0, leg), kf('rotation', 0.5, -leg), kf('rotation', 1, leg)],
        'right_arm': [kf('rotation', 0, arm, 0, 3), kf('rotation', 0.5, -arm, 0, 3), kf('rotation', 1, arm, 0, 3)],
        'left_arm': [kf('rotation', 0, -arm, 0, -3), kf('rotation', 0.5, arm, 0, -3), kf('rotation', 1, -arm, 0, -3)],
        'body': [kf('rotation', 0, 2, 4), kf('rotation', 0.5, 2, -4), kf('rotation', 1, 2, 4)],
        'head': [kf('rotation', 0, -2, -3), kf('rotation', 0.5, -2, 3), kf('rotation', 1, -2, -3)],
    }, bones))
    # correr: pasos largos, brazos doblados hacia adelante, inclinado
    leg, arm = 52, 48
    A.append(anim('run', 0.6, 'loop', {
        'player': [kf('position', t, 0, b) for t, b in ((0, 0), (0.15, 1.0), (0.3, 0), (0.45, 1.0), (0.6, 0))],
        'right_leg': [kf('rotation', 0, -leg), kf('rotation', 0.3, leg * 0.8), kf('rotation', 0.6, -leg)],
        'left_leg': [kf('rotation', 0, leg * 0.8), kf('rotation', 0.3, -leg), kf('rotation', 0.6, leg * 0.8)],
        'right_arm': [kf('rotation', 0, arm, 0, 8), kf('rotation', 0.3, -arm, 0, 8), kf('rotation', 0.6, arm, 0, 8)],
        'left_arm': [kf('rotation', 0, -arm, 0, -8), kf('rotation', 0.3, arm, 0, -8), kf('rotation', 0.6, -arm, 0, -8)],
        'body': [kf('rotation', 0, 10, 6), kf('rotation', 0.3, 10, -6), kf('rotation', 0.6, 10, 6)],
        'head': [kf('rotation', 0, -8, -4), kf('rotation', 0.3, -8, 4), kf('rotation', 0.6, -8, -4)],
    }, bones))
    # hablar: asiente, gesticula con la mano derecha
    A.append(anim('talk', 3, 'loop', {
        'head': [kf('rotation', 0, 0, 0), kf('rotation', 0.4, 5, 4, 2), kf('rotation', 0.8, -1, 2), kf('rotation', 1.3, 4, -5, -2),
                 kf('rotation', 1.9, 0, -3), kf('rotation', 2.4, 5, 3, 1), kf('rotation', 3, 0, 0)],
        'right_arm': [kf('rotation', 0, -8, 0, 4), kf('rotation', 0.5, -38, -10, 12), kf('rotation', 1.0, -30, 8, 10), kf('rotation', 1.6, -42, -6, 14),
                      kf('rotation', 2.2, -20, 0, 8), kf('rotation', 3, -8, 0, 4)],
        'left_arm': [kf('rotation', 0, 0, 0, -3), kf('rotation', 1.5, -6, 0, -6), kf('rotation', 3, 0, 0, -3)],
        'body': [kf('rotation', 0, 0, 0), kf('rotation', 0.8, 1.5, 4), kf('rotation', 1.8, 1.5, -3), kf('rotation', 3, 0, 0)],
    }, bones))
    # saludar con la mano
    wave = [kf('rotation', 0, 0, 0, 3), kf('rotation', 0.3, -10, 0, 150)]
    t = 0.3
    for i in range(4):
        t += 0.2
        wave.append(kf('rotation', t, -10, 0, 128 if i % 2 == 0 else 158))
    wave += [kf('rotation', t + 0.35, -10, 0, 150), kf('rotation', t + 0.75, 0, 0, 3)]
    A.append(anim('wave', round(t + 0.75, 3), 'once', {
        'right_arm': wave,
        'head': [kf('rotation', 0, 0, 0, 0), kf('rotation', 0.3, -4, 0, -6), kf('rotation', t + 0.35, -4, 0, -6), kf('rotation', t + 0.75, 0, 0, 0)],
        'body': [kf('rotation', 0, 0, 0, 0), kf('rotation', 0.3, 0, 0, -3), kf('rotation', t + 0.35, 0, 0, -3), kf('rotation', t + 0.75, 0, 0, 0)],
    }, bones))
    # al tocarlo: se da vuelta un poco y asiente
    A.append(anim('interact', 1.2, 'once', {
        'head': [kf('rotation', 0, 0, 0), kf('rotation', 0.25, 14, 0), kf('rotation', 0.5, -4, 0), kf('rotation', 0.75, 8, 0), kf('rotation', 1.2, 0, 0)],
        'body': [kf('rotation', 0, 0, 0), kf('rotation', 0.3, 4, 0), kf('rotation', 1.2, 0, 0)],
        'right_arm': [kf('rotation', 0, 0, 0, 3), kf('rotation', 0.35, -25, 0, 10), kf('rotation', 0.8, -18, 0, 8), kf('rotation', 1.2, 0, 0, 3)],
    }, bones))
    # te vio: levanta la cabeza y la mano un momento
    A.append(anim('seeplayer', 1.4, 'once', {
        'head': [kf('rotation', 0, 0, 0), kf('rotation', 0.25, -10, 0, 4), kf('rotation', 1.0, -6, 0, 3), kf('rotation', 1.4, 0, 0)],
        'right_arm': [kf('rotation', 0, 0, 0, 3), kf('rotation', 0.3, -30, 0, 60), kf('rotation', 0.55, -30, 0, 85), kf('rotation', 0.8, -30, 0, 60),
                      kf('rotation', 1.4, 0, 0, 3)],
    }, bones))
    # --- gestos para las acciones de los aldeanos (y de cualquier NPC) ---
    def R(*keys):
        return [kf('rotation', *k) for k in keys]
    def P(*keys):
        return [kf('position', t, 0, y) for t, y in keys]
    # feliz: salta con los brazos arriba
    A.append(anim('happy', 1.3, 'once', {
        'player': P((0, 0), (0.2, -0.5), (0.4, 3), (0.6, 0), (0.75, -0.4), (0.9, 1.5), (1.05, 0), (1.3, 0)),
        'right_arm': R((0, 0, 0, 3), (0.3, -150, 0, 25), (0.6, -160, 0, 35), (0.9, -150, 0, 25), (1.3, 0, 0, 3)),
        'left_arm': R((0, 0, 0, -3), (0.3, -150, 0, -25), (0.6, -160, 0, -35), (0.9, -150, 0, -25), (1.3, 0, 0, -3)),
        'head': R((0, 0, 0), (0.3, -12, 0), (1.0, -8, 0), (1.3, 0, 0)),
    }, bones))
    # recibir algo: estira las manos, lo mira y se lo lleva al pecho
    A.append(anim('receive', 1.6, 'once', {
        'right_arm': R((0, 0, 0, 3), (0.35, -60, 0, -14), (0.7, -60, 0, -14), (1.0, -75, 0, -38), (1.35, -75, 0, -38), (1.6, 0, 0, 3)),
        'left_arm': R((0, 0, 0, -3), (0.35, -60, 0, 14), (0.7, -60, 0, 14), (1.0, -75, 0, 38), (1.35, -75, 0, 38), (1.6, 0, 0, -3)),
        'head': R((0, 0, 0), (0.35, 16, 0), (0.8, 12, 0, 4), (1.2, 4, 0, -4), (1.6, 0, 0)),
        'body': R((0, 0, 0), (0.35, 5, 0), (1.0, 2, 0), (1.6, 0, 0)),
    }, bones))
    # asco: se echa para atras, da vuelta la cara y empuja con la mano
    A.append(anim('disgust', 1.4, 'once', {
        'head': R((0, 0, 0), (0.2, -10, 25), (0.6, -8, 30, 4), (1.0, -6, 20), (1.4, 0, 0)),
        'body': R((0, 0, 0), (0.25, -8, 12), (1.0, -6, 10), (1.4, 0, 0)),
        'right_arm': R((0, 0, 0, 3), (0.25, -70, 0, -5), (0.5, -78, 0, -5), (0.9, -62, 0, -5), (1.4, 0, 0, 3)),
    }, bones))
    # enojado: pisoton, brazos tensos y mira fijo
    A.append(anim('angry', 1.4, 'once', {
        'player': P((0, 0), (0.2, 0.2), (0.35, -0.3), (0.5, 0), (1.4, 0)),
        'right_leg': R((0, 0, 0), (0.2, -25, 0), (0.35, 0, 0), (1.4, 0, 0)),
        'right_arm': R((0, 0, 0, 3), (0.35, 14, 0, 12), (1.1, 14, 0, 12), (1.4, 0, 0, 3)),
        'left_arm': R((0, 0, 0, -3), (0.35, 14, 0, -12), (1.1, 14, 0, -12), (1.4, 0, 0, -3)),
        'head': R((0, 0, 0), (0.35, 12, 0), (0.6, 12, -12), (0.8, 12, 12), (1.0, 12, 0), (1.4, 0, 0)),
        'body': R((0, 0, 0), (0.35, 8, 0), (1.1, 6, 0), (1.4, 0, 0)),
    }, bones))
    # le pegaron: se cubre y se echa para atras
    A.append(anim('hurt', 0.7, 'once', {
        'player': P((0, 0), (0.12, -0.6), (0.7, 0)),
        'body': R((0, 0, 0), (0.12, -14, 0), (0.7, 0, 0)),
        'head': R((0, 0, 0), (0.12, -18, 0), (0.7, 0, 0)),
        'right_arm': R((0, 0, 0, 3), (0.12, -110, 0, -30), (0.4, -100, 0, -25), (0.7, 0, 0, 3)),
        'left_arm': R((0, 0, 0, -3), (0.12, -110, 0, 30), (0.4, -100, 0, 25), (0.7, 0, 0, -3)),
    }, bones))
    # timido: mira el piso, manos atras, se hamaca
    A.append(anim('shy', 2.2, 'once', {
        'head': R((0, 0, 0), (0.4, 22, 0, 8), (1.2, 20, -6, -6), (1.8, 22, 0, 8), (2.2, 0, 0)),
        'right_arm': R((0, 0, 0, 3), (0.4, 25, 0, -8), (1.8, 25, 0, -8), (2.2, 0, 0, 3)),
        'left_arm': R((0, 0, 0, -3), (0.4, 25, 0, 8), (1.8, 25, 0, 8), (2.2, 0, 0, -3)),
        'body': R((0, 0, 0), (0.6, 0, 0, 3), (1.2, 0, 0, -3), (1.8, 0, 0, 3), (2.2, 0, 0, 0)),
        'right_leg': R((0, 0, 0), (0.8, 0, 15), (1.4, 0, 0), (2.2, 0, 0)),
    }, bones))
    # risa: se tira para atras agarrandose la panza y se sacude
    A.append(anim('laugh', 1.8, 'once', {
        'body': R((0, 0, 0), (0.25, -12, 0), (0.45, -8, 0), (0.65, -12, 0), (0.85, -8, 0), (1.05, -12, 0), (1.4, 8, 0), (1.8, 0, 0)),
        'head': R((0, 0, 0), (0.25, -20, 0), (0.45, -14, 0), (0.65, -20, 0), (0.85, -14, 0), (1.05, -20, 0), (1.4, 10, 0), (1.8, 0, 0)),
        'right_arm': R((0, 0, 0, 3), (0.25, -35, 0, -30), (1.4, -35, 0, -30), (1.8, 0, 0, 3)),
        'left_arm': R((0, 0, 0, -3), (0.25, -35, 0, 30), (1.4, -35, 0, 30), (1.8, 0, 0, -3)),
    }, bones))
    # pensar: mano al menton, cabeza ladeada
    A.append(anim('think', 2.2, 'once', {
        'right_arm': R((0, 0, 0, 3), (0.4, -110, 0, -25), (1.0, -114, 0, -25), (1.8, -110, 0, -25), (2.2, 0, 0, 3)),
        'left_arm': R((0, 0, 0, -3), (0.4, -40, 0, 25), (1.8, -40, 0, 25), (2.2, 0, 0, -3)),
        'head': R((0, 0, 0), (0.4, -8, 0, 10), (1.2, -10, 8, -8), (1.8, -8, 0, 10), (2.2, 0, 0)),
    }, bones))
    # triste: cabeza gacha, hombros caidos
    A.append(anim('sad', 2.4, 'once', {
        'player': P((0, 0), (0.5, -0.4), (1.9, -0.4), (2.4, 0)),
        'head': R((0, 0, 0), (0.5, 28, 0), (1.2, 26, 5), (1.9, 28, 0), (2.4, 0, 0)),
        'body': R((0, 0, 0), (0.5, 10, 0), (1.9, 10, 0), (2.4, 0, 0)),
        'right_arm': R((0, 0, 0, 3), (0.5, -8, 0, -4), (1.9, -8, 0, -4), (2.4, 0, 0, 3)),
        'left_arm': R((0, 0, 0, -3), (0.5, -8, 0, 4), (1.9, -8, 0, 4), (2.4, 0, 0, -3)),
    }, bones))
    # ni idea: levanta los hombros con las manos abiertas
    A.append(anim('shrug', 1.3, 'once', {
        'player': P((0, 0), (0.3, 0.3), (0.8, 0.3), (1.3, 0)),
        'right_arm': R((0, 0, 0, 3), (0.3, -25, 0, 35), (0.8, -25, 0, 35), (1.3, 0, 0, 3)),
        'left_arm': R((0, 0, 0, -3), (0.3, -25, 0, -35), (0.8, -25, 0, -35), (1.3, 0, 0, -3)),
        'head': R((0, 0, 0), (0.3, 0, 0, 10), (0.8, 0, 0, 10), (1.3, 0, 0, 0)),
    }, bones))
    # asentir
    A.append(anim('nod', 1.0, 'once', {
        'head': R((0, 0, 0), (0.2, 15, 0), (0.4, -2, 0), (0.6, 12, 0), (0.8, 0, 0), (1.0, 0, 0)),
    }, bones))
    # reverencia
    A.append(anim('bow', 1.6, 'once', {
        'body': R((0, 0, 0), (0.4, 25, 0), (1.0, 25, 0), (1.6, 0, 0)),
        'right_arm': R((0, 0, 0, 3), (0.4, -50, 0, -35), (1.0, -50, 0, -35), (1.6, 0, 0, 3)),
        'left_arm': R((0, 0, 0, -3), (0.4, 20, 0, 0), (1.0, 20, 0, 0), (1.6, 0, 0, -3)),
    }, bones))
    # tirar un beso
    A.append(anim('blow_kiss', 1.8, 'once', {
        'right_arm': R((0, 0, 0, 3), (0.35, -125, 0, -28), (0.55, -125, 0, -28), (0.75, -85, 0, 8), (1.1, -85, 0, 8), (1.8, 0, 0, 3)),
        'head': R((0, 0, 0), (0.35, 8, 0, -6), (0.75, -6, 0, 8), (1.2, -4, 0, 6), (1.8, 0, 0)),
        'body': R((0, 0, 0), (0.75, 0, 0, -4), (1.2, 0, 0, -3), (1.8, 0, 0, 0)),
    }, bones))
    return A

def model(slim):
    els, outliner, bones = build(slim)
    name = 'humano_fino' if slim else 'humano'
    return {
        'meta': {'format_version': '4.10', 'model_format': 'free', 'box_uv': False},
        'name': name, 'model_identifier': name,
        'resolution': {'width': 64, 'height': 64},
        'elements': els, 'outliner': outliner,
        'textures': [{'name': 'skin', 'uuid': uid('t_skin'), 'id': '0', 'width': 64, 'height': 64, 'uv_width': 64, 'uv_height': 64}],
        'animations': animations(bones),
    }

out = sys.argv[1]
os.makedirs(out, exist_ok=True)
for slim in (False, True):
    m = model(slim)
    with open(os.path.join(out, m['name'] + '.bbmodel'), 'w') as f:
        json.dump(m, f, indent=1)
print('ok')
