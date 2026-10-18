#version 150

// Cielo de concierto: 8 escenas que se dibujan sobre la direccion de mirada (esfera celeste completa)
// y reaccionan al audio (32 bandas + graves/medios/agudos/volumen/golpe).

in vec2 ndc;
out vec4 fragColor;

uniform mat4 InvProj;
uniform mat4 InvView;
uniform float Time;
uniform float Scene;
uniform float Level;
uniform float Bass;
uniform float Mid;
uniform float High;
uniform float Beat;
uniform float Flash;
uniform float Fade;
uniform float Hue;
uniform float Spec[32];
uniform vec3 StageDir;
uniform float Nova;
uniform vec3 P0;
uniform vec3 P1;
uniform vec3 P2;
uniform float Kick;

const float PI = 3.14159265;
const float TAU = 6.2831853;

float hash11(float p) { p = fract(p * 0.1031); p *= p + 33.33; p *= p + p; return fract(p); }
float hash21(vec2 p) { vec3 p3 = fract(vec3(p.xyx) * 0.1031); p3 += dot(p3, p3.yzx + 33.33); return fract((p3.x + p3.y) * p3.z); }
vec3 hash31(float p) { vec3 p3 = fract(vec3(p) * vec3(0.1031, 0.1030, 0.0973)); p3 += dot(p3, p3.yzx + 33.33); return fract((p3.xxy + p3.yzz) * p3.zyx); }

float noise3(vec3 p) {
    vec3 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float n = i.x + i.y * 57.0 + i.z * 113.0;
    float a = fract(sin(n) * 43758.5453), b = fract(sin(n + 1.0) * 43758.5453);
    float c = fract(sin(n + 57.0) * 43758.5453), d = fract(sin(n + 58.0) * 43758.5453);
    float e = fract(sin(n + 113.0) * 43758.5453), g = fract(sin(n + 114.0) * 43758.5453);
    float h = fract(sin(n + 170.0) * 43758.5453), k = fract(sin(n + 171.0) * 43758.5453);
    return mix(mix(mix(a, b, f.x), mix(c, d, f.x), f.y), mix(mix(e, g, f.x), mix(h, k, f.x), f.y), f.z);
}
float fbm(vec3 p) {
    float s = 0.0, a = 0.5;
    for (int i = 0; i < 5; i++) { s += a * noise3(p); p = p * 2.03 + vec3(1.7, 9.2, 3.1); a *= 0.5; }
    return s;
}

float spec(float x) {
    float f = clamp(x, 0.0, 1.0) * 31.0;
    int i = int(floor(f));
    return mix(Spec[i], Spec[min(i + 1, 31)], fract(f));
}

vec3 pal(float t) { return 0.5 + 0.5 * cos(TAU * (t + Hue + vec3(0.0, 0.33, 0.67))); }
vec3 pal2(float t, vec3 a, vec3 b) { return mix(a, b, 0.5 + 0.5 * sin(t)); }

// 0: barras del espectro en anillo alrededor del cenit
vec3 sceneSpectrum(vec3 d, float zen, float ang, float t) {
    float u = ang / TAU + 0.5;
    float r = zen / PI;
    float nb = 64.0;
    float cell = floor(u * nb);
    float cx = fract(u * nb);
    float mirror = abs(2.0 * fract((cell + 0.5) / nb) - 1.0);
    float h = spec(mirror);
    float r0 = 0.18 + 0.03 * Bass;
    float len = 0.02 + h * h * 0.26;
    float bar = step(0.12, cx) * step(cx, 0.88);
    float inside = step(r0, r) * step(r, r0 + len);
    float g = clamp((r - r0) / max(len, 0.001), 0.0, 1.0);
    vec3 c = mix(vec3(1.0, 0.95, 0.2), vec3(0.1, 0.9, 1.0), g) * bar * inside;
    c = mix(c, c.bgr, 0.5 + 0.5 * sin(t * 0.3));
    float ring = exp(-pow((r - r0) * 60.0, 2.0)) * (0.6 + Bass);
    c += vec3(1.0, 0.9, 0.3) * ring;
    // reflejo suave: barras fantasma hacia abajo
    float down = step(r0 - len * 0.35, r) * step(r, r0) * bar * 0.12;
    c += pal(u + t * 0.05) * down;
    c += vec3(0.03, 0.01, 0.07) * (1.0 - r);
    c *= 0.85;
    return c;
}

// 1: lineas de neon ondulantes + puntos
vec3 sceneScope(vec3 d, float zen, float ang, float t) {
    vec3 c = vec3(0.0);
    float x = ang;
    float y = d.y;
    for (int i = 0; i < 6; i++) {
        float fi = float(i);
        float amp = 0.10 + 0.22 * (0.4 * Bass + 0.6 * Mid) + 0.04 * fi;
        float ly = 0.35 + 0.1 * fi - 0.25 * (fi * 0.35)
                 + amp * sin(x * (2.0 + fi * 0.7) + t * (0.6 + fi * 0.25) + fi * 1.7)
                 + 0.5 * amp * sin(x * (5.0 + fi) - t * 0.9 + High * 3.0);
        float dist = abs(y - ly);
        float glow = 0.0035 / (dist + 0.0035) + 0.004 / (dist * 8.0 + 0.1);
        vec3 col = i % 2 == 0 ? vec3(0.1, 1.0, 0.8) : vec3(0.9, 1.0, 1.0);
        c += col * glow * (0.35 + 0.65 * Level) * (1.0 - 0.1 * fi);
    }
    // puntos circulares alrededor del cenit
    for (int k = 0; k < 4; k++) {
        float rr = (0.12 + 0.09 * float(k)) * (1.0 + 0.15 * Bass);
        float n = 14.0 + 6.0 * float(k);
        float a = fract((ang / TAU + 0.5) * n + t * 0.05 * (k % 2 == 0 ? 1.0 : -1.0));
        float dd = length(vec2((a - 0.5) / n * TAU * rr * 2.0, (zen / PI - rr)));
        c += vec3(0.2, 1.0, 0.7) * smoothstep(0.010 + 0.006 * High, 0.0, dd) * (0.35 + High);
    }
    c += vec3(0.0, 0.025, 0.035);
    return c;
}

// 2: tunel de estrellas (warp) desde el cenit
vec3 sceneStar(vec3 d, float zen, float ang, float t) {
    vec3 c = vec3(0.0);
    float rad = zen / PI;
    for (int L = 0; L < 3; L++) {
        float fl = float(L);
        float n = 90.0 + fl * 60.0;
        float sec = floor((ang / TAU + 0.5) * n);
        float fx = fract((ang / TAU + 0.5) * n) - 0.5;
        float hs = hash21(vec2(sec, fl));
        float speed = (0.10 + 0.12 * hs + fl * 0.06) * (0.6 + 1.6 * Level + Bass);
        float head = fract(hs * 7.3 + t * speed);
        head = head * head;
        float behind = head - rad;
        float len = 0.04 + 0.30 * (Level + 0.3) * hs;
        float mask = step(0.0, behind) * smoothstep(len, 0.0, behind);
        float thin = smoothstep(0.09 - 0.04 * fl, 0.0, abs(fx) * (0.3 + rad * 2.0));
        c += mix(vec3(0.6, 0.75, 1.0), vec3(1.0), hs) * mask * thin * (0.5 + 0.5 * hs);
    }
    c += vec3(0.7, 0.8, 1.0) * exp(-rad * 12.0) * (0.15 + Bass * 0.6);
    return c + vec3(0.0, 0.01, 0.03);
}

// 3: caleidoscopio
vec3 sceneKaleido(vec3 d, float zen, float ang, float t) {
    float seg = TAU / 8.0;
    float a = mod(ang + t * 0.08, seg);
    a = abs(a - seg * 0.5);
    vec2 p = zen * vec2(cos(a), sin(a)) * (3.0 + 2.0 * Bass);
    float v = sin(p.x * 3.0 - t * 1.2) + sin(p.y * 4.0 + t) + sin((p.x + p.y) * 2.0 + t * 0.7 + Mid * 4.0);
    float rings = sin(length(p) * 6.0 - t * 2.0 - Bass * 5.0);
    float m = 0.5 + 0.5 * sin(v * 1.6 + rings * 1.4);
    vec3 c = pal(m * 0.7 + t * 0.03 + zen * 0.2) * smoothstep(0.35, 1.0, m);
    c *= 0.18 + 0.5 * Level + 0.45 * Beat;
    c += pal(t * 0.05) * exp(-zen * 3.0) * 0.5;
    return c;
}

// 4: espiral / tunel tipo milkdrop
vec3 sceneMilk(vec3 d, float zen, float ang, float t) {
    float lz = log(zen + 0.02);
    float s = ang * 3.0 + lz * 4.0 - t * (1.2 + Bass * 1.5);
    float arms = pow(0.5 + 0.5 * sin(s * 2.0), 3.0);
    float rings = pow(0.5 + 0.5 * sin(lz * 9.0 - t * 2.6 - Bass * 4.0), 4.0);
    vec3 c = vec3(0.0);
    c += mix(vec3(1.0, 0.1, 0.7), vec3(1.0, 0.6, 0.15), 0.5 + 0.5 * sin(s * 0.5 + Hue * TAU)) * arms * (0.5 + 0.9 * Level);
    c += mix(vec3(1.0, 0.7, 0.2), vec3(0.8, 0.2, 1.0), 0.5 + 0.5 * sin(lz * 3.0 - t)) * rings * (0.4 + Mid);
    float core = exp(-zen * 6.0);
    c += vec3(1.0, 0.55, 0.95) * core * (0.4 + Bass * 1.2);
    c *= smoothstep(0.0, 0.25, zen * 3.0 + 0.1);
    return c;
}

// 5: orbes brillantes que flotan
vec3 sceneMagneto(vec3 d, float zen, float ang, float t) {
    vec3 c = vec3(0.0);
    for (int i = 0; i < 9; i++) {
        float fi = float(i);
        float a = t * (0.15 + 0.05 * fi) * (i % 2 == 0 ? 1.0 : -1.0) + fi * 2.4;
        float e = 0.25 + 0.55 * sin(t * 0.21 + fi * 1.3) * 0.5 + 0.25 * sin(fi * 3.7);
        vec3 o = normalize(vec3(cos(a) * cos(e), sin(e) + 0.2, sin(a) * cos(e)));
        float cd = 1.0 - dot(d, o);
        float sz = 0.0012 + 0.0016 * spec(fi / 9.0) * 3.0 + 0.0012 * Bass;
        float g = sz / (cd + sz * 0.4);
        vec3 col = i % 3 == 0 ? vec3(1.0, 0.2, 0.9) : (i % 3 == 1 ? vec3(0.35, 0.4, 1.0) : vec3(0.7, 0.3, 1.0));
        c += col * g * 0.5 * (0.45 + Level);
    }
    float nebula = fbm(d * 2.2 + vec3(0.0, t * 0.05, 0.0));
    c += vec3(0.10, 0.03, 0.22) * pow(nebula, 2.0) * (0.3 + Mid * 0.7);
    return c;
}

// 6: nubes de fuego estilo cthugha
vec3 sceneFire(vec3 d, float zen, float ang, float t) {
    vec3 p = d * 2.5;
    vec3 q = vec3(fbm(p + vec3(0.0, t * 0.12, 0.0)), fbm(p + vec3(5.2, 1.3, t * 0.1)), fbm(p + vec3(t * 0.08, 8.3, 2.8)));
    float f = fbm(p + 2.2 * q + vec3(0.0, -t * 0.2 * (1.0 + Bass), 0.0));
    float heat = clamp(f * (0.75 + 0.85 * Level) + 0.12 * Bass - 0.05, 0.0, 1.5);
    vec3 c = mix(vec3(0.02, 0.0, 0.03), vec3(0.65, 0.07, 0.04), smoothstep(0.15, 0.55, heat));
    c = mix(c, vec3(1.0, 0.55, 0.08), smoothstep(0.5, 0.95, heat));
    c = mix(c, vec3(1.0, 0.95, 0.6), smoothstep(0.95, 1.4, heat));
    float horizon = exp(-abs(d.y) * 3.5);
    c += vec3(1.0, 0.45, 0.1) * horizon * (0.15 + 0.3 * Level);
    return c;
}

// 7: fuegos artificiales
vec3 sceneFire2(vec3 d, float zen, float ang, float t) {
    vec3 c = vec3(0.0);
    for (int i = 0; i < 7; i++) {
        float fi = float(i);
        float per = 1.9 + 0.2 * fi;
        float tt = t / per + fi * 0.37;
        float id = floor(tt);
        float age = fract(tt);
        vec3 h = hash31(id * 7.0 + fi * 13.0);
        float az = h.x * TAU;
        float el = 0.25 + h.y * 0.85;
        vec3 ctr = normalize(vec3(cos(az) * cos(el), sin(el), sin(az) * cos(el)));
        float cd = acos(clamp(dot(d, ctr), -1.0, 1.0));
        vec3 up = abs(ctr.y) > 0.95 ? vec3(1.0, 0.0, 0.0) : vec3(0.0, 1.0, 0.0);
        vec3 tx = normalize(cross(up, ctr));
        vec3 ty = cross(ctr, tx);
        float ra = atan(dot(d, ty), dot(d, tx));
        float R = (0.12 + 0.28 * h.z) * (1.0 - pow(1.0 - age, 2.5));
        float shell = exp(-pow((cd - R) * 22.0, 2.0));
        float rays = 24.0;
        float sp = step(0.35, hash21(vec2(floor((ra / TAU + 0.5) * rays), id + fi)));
        float line = smoothstep(0.35, 0.0, abs(fract((ra / TAU + 0.5) * rays) - 0.5));
        float life = (1.0 - age) * (1.0 - age);
        vec3 col = pal(h.x + 0.3 * h.y);
        c += col * shell * sp * (0.4 + 0.6 * line) * life * 1.7;
        c += col * exp(-cd * 25.0) * smoothstep(0.08, 0.0, age) * 3.0;
    }
    c *= 0.8 + 1.0 * Level;
    c += vec3(0.01, 0.005, 0.03);
    return c;
}


// ---------- escenas ancladas al eje del escenario (StageDir = hacia donde mira el publico) ----------
vec2 azel(vec3 d) {
    vec3 c = normalize(vec3(StageDir.x, 0.0, StageDir.z));
    vec3 T = normalize(cross(vec3(0.0, 1.0, 0.0), c));
    return vec2(atan(dot(d, T), dot(d, c)), asin(clamp(d.y, -1.0, 1.0)));
}
float dot2(vec2 v) { return dot(v, v); }
float sdHeart(vec2 p) {
    p.x = abs(p.x);
    if (p.y + p.x > 1.0) return sqrt(dot2(p - vec2(0.25, 0.75))) - sqrt(2.0) / 4.0;
    return sqrt(min(dot2(p - vec2(0.0, 1.0)), dot2(p - 0.5 * max(p.x + p.y, 0.0)))) * sign(p.x - p.y);
}
// corazon de tamano 'sz' (radianes) centrado en 'c' (az, el); devuelve [relleno, borde]
vec2 heart(vec2 ae, vec2 c, float sz) {
    vec2 p = (ae - c) / sz;
    p.y += 0.55;
    float d = sdHeart(p);
    return vec2(smoothstep(0.02, -0.02, d), exp(-pow(d * 14.0, 2.0)));
}
vec3 stars(vec2 ae, float el, float t) {
    vec2 g = ae * vec2(46.0, 46.0);
    vec2 cell = floor(g);
    float h = hash21(cell);
    vec2 f = fract(g) - 0.5;
    float tw = 0.5 + 0.5 * sin(t * (2.0 + h * 4.0) + h * 60.0);
    float on = step(0.982, h) * smoothstep(0.30, 0.18, length(f)) * tw;
    return vec3(1.0, 0.92, 0.95) * on * smoothstep(0.22, 0.6, el);
}

// 8: atardecer con el sol que se derrite
vec3 sceneSunset(vec3 d, float t) {
    vec2 ae = azel(d);
    float el = max(ae.y, 0.0);
    float front = smoothstep(-0.2, 0.5, dot(normalize(vec3(StageDir.x, 0.0, StageDir.z)), vec3(d.x, 0.0, d.z)));
    vec3 deep = mix(vec3(0.07, 0.01, 0.20), P0 * 0.22, 0.35);
    vec3 col = mix(mix(P1, P0, 0.30) * 1.05, deep, smoothstep(0.0, 0.95, el));
    col = mix(col, P0 * 0.95, exp(-pow((el - 0.28) * 4.0, 2.0)) * 0.5);
    // nubes iluminadas por el sol
    float cl = fbm(vec3(ae.x * 1.7, el * 5.0, t * 0.025));
    col += mix(P0, P2, cl) * pow(cl, 2.2) * smoothstep(0.02, 0.18, el) * smoothstep(0.7, 0.2, el) * 0.7 * (0.6 + 0.6 * Level) * front;
    // sol
    float cy = 0.30, r = 0.17 * (1.0 + 0.04 * Kick);
    vec2 sp = vec2(ae.x, el - cy);
    float dist = length(sp);
    float disc = smoothstep(r, r - 0.012, dist);
    vec3 sunC = mix(P1, P0, clamp((el - (cy - r)) / (2.0 * r), 0.0, 1.0));
    col = mix(col, sunC * (1.0 + 0.2 * Level), disc);
    col += mix(P1, P0, 0.4) * exp(-dist * 6.5) * (0.14 + 0.22 * Level + 0.12 * Bass);
    // gotas que cuelgan del sol
    float dr = 0.0;
    for (int i = 0; i < 11; i++) {
        float fi = float(i);
        float x = -r * 0.82 + r * 1.64 * fi / 10.0;
        float edge = cy - sqrt(max(r * r - x * x, 0.0));
        float len = r * (0.30 + 0.95 * hash11(fi * 3.1)) * (0.75 + 0.45 * Bass) * (0.8 + 0.2 * sin(t * 0.8 + fi * 1.7));
        float y = edge - el;
        float w = 0.0065 + 0.003 * hash11(fi);
        float line = smoothstep(w, 0.0, abs(ae.x - x)) * step(0.0, y) * smoothstep(len + 0.012, len - 0.004, y);
        float bead = smoothstep(w * 2.1, w * 1.3, length(vec2(ae.x - x, y - len)));
        dr += line + bead;
    }
    col += sunC * dr * 1.1 * front;
    col += stars(ae, el, t) * (1.0 - disc);
    return col;
}

// 9: version de estribillo: sol que late, ondas y corazones simetricos con gotas
vec3 sceneMeltGlow(vec3 d, float t) {
    vec2 ae = azel(d);
    float el = max(ae.y, 0.0);
    float front = smoothstep(-0.3, 0.6, dot(normalize(vec3(StageDir.x, 0.0, StageDir.z)), vec3(d.x, 0.0, d.z)));
    vec3 col = mix(mix(P1, P0, 0.25) * 1.1, mix(P0 * 0.30, vec3(0.12, 0.01, 0.26), 0.55), smoothstep(0.0, 0.75, el));
    // cintas de aurora rosa
    for (int k = 0; k < 3; k++) {
        float fk = float(k);
        float y = 0.30 + 0.20 * fk + 0.07 * sin(ae.x * (2.5 + fk) + t * (0.5 + 0.2 * fk) + Bass * 2.0);
        float b = exp(-pow((el - y) * (9.0 - fk), 2.0));
        col += mix(P0, P2, fk / 2.0) * b * (0.30 + 0.5 * Level) * front;
    }
    // sol que late con ondas
    float cy = 0.22, r = 0.15 * (1.0 + 0.10 * Kick);
    vec2 sp = vec2(ae.x, el - cy);
    float dist = length(sp);
    col = mix(col, mix(P1, P2, 0.35 + 0.3 * Beat) * 1.1, smoothstep(r, r - 0.012, dist));
    float wv = 0.5 + 0.5 * sin(dist * 34.0 - t * 5.0 - Bass * 3.0);
    col += P0 * pow(wv, 4.0) * smoothstep(0.7, 0.2, dist) * smoothstep(r, r + 0.05, dist) * (0.35 + 0.8 * Level);
    col += P2 * exp(-dist * 6.0) * (0.12 + 0.25 * Level);
    // corazones simetricos: dos filas a cada lado, el mayor arriba al centro
    float bob = 0.012 * sin(t * 1.4);
    vec2 h = vec2(0.0);
    vec3 hc = vec3(0.0);
    float szc = 0.20 * (1.0 + 0.08 * Kick);
    vec2 cH = heart(ae, vec2(0.0, 0.62 + bob), szc);
    hc += P0 * 1.15 * cH.x + vec3(1.0) * cH.y * 0.8;
    for (int i = 0; i < 2; i++) {
        float fi = float(i);
        float x = 0.62 + 0.45 * fi;
        float yy = 0.42 - 0.12 * fi + bob * (1.0 + fi);
        float sz = (0.115 - 0.02 * fi) * (1.0 + 0.10 * Kick);
        for (int sgn = 0; sgn < 2; sgn++) {
            float sx = sgn == 0 ? -x : x;
            vec2 hh = heart(ae, vec2(sx, yy), sz);
            hc += mix(P0, P1, 0.25 + 0.3 * fi) * 1.1 * hh.x + vec3(1.0) * hh.y * 0.7;
            // gotita debajo
            float gl = smoothstep(0.006, 0.0, abs(ae.x - sx)) * step(ae.y, yy - sz * 0.55) * smoothstep(yy - sz * 0.55 - 0.16, yy - sz * 0.55, ae.y);
            hc += P0 * gl * 0.8;
        }
    }
    col += hc * front * (0.8 + 0.5 * Level);
    col += stars(ae, el, t) * 0.8;
    return col;
}


// ============================================================================================================
// FONDOS PROPIOS DE CADA CANCION (10..19). Todos anclados al eje del escenario (azimut/altura), no giran con la camara.
// ============================================================================================================
mat2 rot2(float a) { float c = cos(a), s = sin(a); return mat2(c, -s, s, c); }
vec3 rainbow(float h) { return clamp(abs(mod(h * 6.0 + vec3(0.0, 4.0, 2.0), 6.0) - 3.0) - 1.0, 0.0, 1.0); }
float seg(vec2 p, vec2 a, vec2 b) { vec2 pa = p - a, ba = b - a; float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0); return length(pa - ba * h); }
float sdBox(vec2 p, vec2 b) { vec2 q = abs(p) - b; return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0); }

// 10: STATIC - tele sin senal: nieve, barras de color, lluvia de datos y cortes de imagen con corte de color rojo/cian
float dataPat(vec2 ae, float t) {
    vec2 f = abs(fract(ae * 8.0) - 0.5);
    float line = smoothstep(0.462, 0.5, max(f.x, f.y));
    float id = floor(ae.x * 34.0);
    float h = hash11(id + 3.0);
    float y = fract(ae.y * 2.6 - t * (0.25 + 0.9 * h) + h * 9.0);
    float trail = pow(1.0 - y, 3.0) * step(0.5, hash11(id * 1.7)) * (0.45 + 1.4 * spec(fract(id / 34.0)));
    return line * 0.55 + trail;
}
vec3 sceneStatic(vec3 d, float t) {
    vec2 ae = azel(d);
    float el = max(ae.y, 0.0);
    float band = floor(ae.y * 16.0 + floor(t * 7.0) * 3.1);
    float gh = hash11(band);
    float glitch = gh > 0.86 ? (gh - 0.93) * (0.5 + 1.2 * Kick) : 0.0;
    vec3 col = P0 * 0.04 + vec3(0.0, 0.01, 0.03);
    col += P0 * dataPat(ae + vec2(glitch, 0.0), t) * 0.85;
    col += P1 * dataPat(ae - vec2(glitch * 1.3, 0.0), t) * (0.18 + 5.0 * abs(glitch));
    // pantalla central con barras de color
    vec2 c = vec2(0.0, 0.62);
    vec2 q = ae - c;
    q.x += glitch * 0.6;
    float bx = sdBox(q, vec2(0.60, 0.33)) - 0.04;
    float inside = smoothstep(0.01, -0.01, bx);
    float bar = floor((q.x + 0.60) / 1.2 * 7.0);
    vec3 bars = bar < 0.5 ? vec3(0.95) : bar < 1.5 ? vec3(0.95, 0.9, 0.1) : bar < 2.5 ? vec3(0.1, 0.9, 0.9) : bar < 3.5 ? vec3(0.1, 0.9, 0.2) : bar < 4.5 ? vec3(0.9, 0.1, 0.9) : bar < 5.5 ? vec3(0.9, 0.1, 0.15) : vec3(0.15, 0.2, 0.95);
    float nz = hash21(floor(ae * vec2(170.0, 110.0)) + floor(t * 30.0));
    bars = mix(bars, vec3(nz), 0.28 + 0.5 * High) * (0.5 + 0.4 * sin(ae.y * 420.0 + t * 8.0) * 0.5 + 0.35);
    col = mix(col, bars * (0.7 + 0.4 * Kick), inside * 0.9);
    col += P0 * exp(-pow(bx * 22.0, 2.0)) * (0.6 + 0.8 * Kick);
    // nieve de fondo
    col += vec3(0.55, 0.8, 0.9) * nz * (0.07 + 0.32 * High + 0.25 * Kick) * smoothstep(0.0, 0.1, 1.0 - inside);
    col += stars(ae, el, t) * 0.4;
    return col * (0.85 + 0.35 * Level);
}

// 11: KONTON BOOGIE - pared de espirales mareadas que giran en sentidos opuestos y cambian de color
vec3 sceneKonton(vec3 d, float t) {
    vec2 ae = azel(d);
    float el = max(ae.y, 0.0);
    float S = 0.62;
    vec2 g = vec2(ae.x, el) / S;
    vec2 cell = floor(g + vec2(0.5, 0.0));
    vec2 p = vec2(fract(g.x + 0.5) - 0.5, fract(g.y) - 0.5);
    float par = mod(cell.x + cell.y, 2.0);
    float dir = par < 0.5 ? 1.0 : -1.0;
    float a = atan(p.y, p.x), r = length(p);
    float sp = sin(a * 3.0 * dir + log(r + 0.04) * 9.0 - t * 2.2 * dir - Bass * 3.0);
    float arms = smoothstep(-0.05, 0.05, sp);
    float id = hash21(cell);
    vec3 ca = id < 0.34 ? P0 : id < 0.67 ? P1 : P2;
    vec3 cb = id < 0.34 ? P1 : id < 0.67 ? P2 : P0;
    vec3 col = mix(cb * 0.25, ca * 1.1, arms);
    col *= smoothstep(0.52, 0.30, r + 0.12 * Bass);                 // cada espiral es un disco
    col += ca * exp(-r * r * 90.0) * (0.5 + Kick);                   // centro brillante
    // fondo oscuro con puntos
    vec3 bg = P2 * 0.05 + vec3(0.02, 0.0, 0.05) * (1.0 + el);
    col = max(col, bg);
    col *= 0.7 + 0.5 * Level;
    col += stars(ae, el, t) * 0.5;
    return col * smoothstep(-0.02, 0.1, ae.y + 0.05);
}

// 12: MAGICAL CURE - cielo pastel con un circulo magico gigante (pentagrama y runas) y lluvia de destellos
vec3 sceneMagia(vec3 d, float t) {
    vec2 ae = azel(d);
    float el = max(ae.y, 0.0);
    vec3 col = mix(P1 * 0.9, P0 * 0.5, smoothstep(0.0, 0.9, el));
    col = mix(col, vec3(0.05, 0.02, 0.15), smoothstep(0.55, 1.5, el));
    col += P2 * exp(-pow(el * 6.0, 2.0)) * 0.25;
    // circulo magico en lo alto, delante del publico
    vec2 c = vec2(0.0, 0.92);
    vec2 p = (ae - c);
    p.x *= 0.62;
    float R = 0.78 * (1.0 + 0.05 * Kick);
    p = rot2(t * 0.18) * p;
    float r = length(p), a = atan(p.y, p.x);
    float ring1 = exp(-pow((r - R) * 70.0, 2.0)), ring2 = exp(-pow((r - R * 0.88) * 120.0, 2.0)), ring3 = exp(-pow((r - R * 0.45) * 90.0, 2.0));
    float ticks = smoothstep(0.55, 0.0, abs(fract(a / TAU * 36.0) - 0.5) - 0.3) * exp(-pow((r - R * 0.94) * 60.0, 2.0));
    float star = 0.0;
    for (int k = 0; k < 5; k++) {
        float a0 = TAU * float(k) / 5.0 + PI * 0.5, a1 = TAU * float(k + 2) / 5.0 + PI * 0.5;
        float dd = seg(p, R * 0.88 * vec2(cos(a0), sin(a0)), R * 0.88 * vec2(cos(a1), sin(a1)));
        star += exp(-dd * dd * 9000.0);
    }
    // runas giratorias entre los anillos
    vec2 q2 = rot2(-t * 0.35) * p;
    float ra = atan(q2.y, q2.x);
    float runes = step(0.5, hash11(floor(ra / TAU * 18.0))) * exp(-pow((r - R * 0.68) * 55.0, 2.0)) * smoothstep(0.45, 0.2, abs(fract(ra / TAU * 18.0) - 0.5));
    vec3 mc = mix(P0, P2, 0.35) * (ring1 + ring2 * 0.8 + ring3 + ticks * 0.7 + star + runes * 0.8);
    mc += vec3(1.0) * (ring1 * 0.3 + star * 0.3);
    col += mc * (0.9 + 0.8 * Level + 0.5 * Beat);
    col += P2 * exp(-r * r * 3.0) * 0.12 * (1.0 + Level);
    // destellos que caen
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        vec2 g = ae * vec2(10.0 + fi * 7.0, 8.0 + fi * 5.0) + vec2(0.0, t * (0.6 + 0.3 * fi));
        vec2 cell = floor(g);
        float h = hash21(cell + fi * 17.0);
        vec2 f = fract(g) - 0.5;
        float tw = 0.5 + 0.5 * sin(t * (2.0 + h * 5.0) + h * 40.0);
        float on = step(0.93, h) * smoothstep(0.35, 0.0, length(f)) * tw;
        col += mix(vec3(1.0), P0, 0.4) * on * 0.9;
    }
    col += stars(ae, el, t) * 0.5;
    return col;
}

// 13: LOOPING THE ROOMS - techo infinito de las backrooms: paneles fluorescentes que parpadean y papel tapiz amarillo
vec3 sceneRooms(vec3 d, float t) {
    vec2 ae = azel(d);
    float el = max(ae.y, 0.0);
    vec3 wall = P0 * 0.55;
    float stripe = 0.5 + 0.5 * sin(ae.x * 120.0);
    wall *= 0.82 + 0.18 * stripe;
    wall *= 0.5 + 0.9 * smoothstep(0.0, 0.14, el);
    float dy = max(d.y, 0.02);
    vec2 p = d.xz / dy * 0.8;
    p = vec2(dot(p, vec2(StageDir.z, -StageDir.x)), dot(p, vec2(StageDir.x, StageDir.z)));
    vec2 cell = floor(p * 0.5);
    vec2 f = fract(p * 0.5);
    float h = hash21(cell);
    float flick = 0.55 + 0.45 * step(0.5, fract(sin(floor(t * 11.0) + h * 40.0) * 43758.5));
    float broken = step(0.9, h) * (0.15 + 0.85 * step(0.6, fract(t * 3.0 + h * 9.0)));
    float lightOn = mix(flick, broken, step(0.82, h)) * (0.75 + 0.5 * Kick);
    float panel = smoothstep(0.16, 0.2, f.x) * smoothstep(0.84, 0.8, f.x) * smoothstep(0.38, 0.42, f.y) * smoothstep(0.62, 0.58, f.y);
    float tile = smoothstep(0.02, 0.0, min(min(f.x, 1.0 - f.x), min(f.y, 1.0 - f.y)) - 0.01);
    vec3 ceil = P0 * 0.28 + P0 * 0.08 * tile;
    ceil += P1 * panel * lightOn * 1.8;
    ceil += P1 * 0.05 * exp(-pow((f.y - 0.5) * 3.0, 2.0)) * lightOn;
    float fogk = exp(-dy * 7.0);
    ceil = mix(ceil, wall * 0.6, fogk * 0.85);
    vec3 col = mix(wall, ceil, smoothstep(0.05, 0.2, el));
    col *= 0.7 + 0.5 * Level;
    col += P2 * 0.06 * Beat;
    return col;
}

// 14: ANAMANAGUCHI - synthwave: sol rayado, montanas y ciudad de barras pixeladas que bailan con el espectro
vec3 sceneSynth(vec3 d, float t) {
    vec2 ae = azel(d);
    float el = max(ae.y, 0.0);
    vec3 col = mix(P0 * 0.9, vec3(0.06, 0.0, 0.2), smoothstep(0.0, 0.8, el));
    col = mix(col, P1 * 0.25, exp(-pow(el * 9.0, 2.0)) * 0.3);
    float cy = 0.34, r = 0.27 * (1.0 + 0.03 * Kick);
    vec2 sp = vec2(ae.x, el - cy);
    float dist = length(sp);
    float disc = smoothstep(r, r - 0.012, dist);
    float yy = (el - (cy - r)) / (2.0 * r);
    float cut = smoothstep(0.0, 0.03, abs(fract(yy * 9.0 - t * 0.12) - 0.5) - (0.5 - (0.12 + 0.5 * (1.0 - yy)) * 0.55)) * step(yy, 0.6);
    cut = yy < 0.6 ? cut : 1.0;
    vec3 sunC = mix(P0, P2, clamp(yy * 1.2, 0.0, 1.0));
    col = mix(col, sunC * (1.15 + 0.3 * Level), disc * cut);
    col += mix(P0, P2, 0.5) * exp(-dist * 4.0) * (0.22 + 0.35 * Level);
    // montanas
    float m = 0.025 + 0.07 * fbm(vec3(ae.x * 3.0, 0.0, 1.0)) * smoothstep(0.0, 0.5, abs(ae.x));
    float mt = smoothstep(m + 0.004, m, el);
    col = mix(col, vec3(0.04, 0.0, 0.1), mt);
    col += P1 * exp(-pow((el - m) * 90.0, 2.0)) * 0.45 * step(el, m + 0.01) * smoothstep(0.0, 0.01, el);
    // ciudad pixelada: 32 columnas que suben con el espectro
    float bx = floor(ae.x * 14.0);
    float u = clamp((bx + 16.0) / 32.0, 0.0, 1.0);
    float bh = 0.02 + 0.16 * spec(u);
    bh = floor(bh * 40.0) / 40.0;
    float inb = step(abs(ae.x), 1.14) * step(el, bh);
    vec3 bld = vec3(0.03, 0.0, 0.08);
    float win = step(0.55, hash21(floor(vec2(ae.x * 14.0 * 3.0, el * 40.0)))) * step(0.12, fract(ae.x * 14.0));
    bld += P1 * win * 0.55 * (0.5 + Level);
    col = mix(col, bld, inb);
    col += P1 * inb * exp(-pow((el - bh) * 80.0, 2.0)) * 0.9;
    col += stars(ae, el, t) * 0.8;
    return col;
}

// 15: MINNA MIKU MIKU - cielo de caramelo: arcoiris, lunares, nubes y corazoncitos que rebotan
vec3 scenePop(vec3 d, float t) {
    vec2 ae = azel(d);
    float el = max(ae.y, 0.0);
    vec3 col = mix(P2, P1, smoothstep(0.0, 0.45, el));
    col = mix(col, P0, smoothstep(0.35, 1.0, el));
    // lunares grandes
    vec2 g = vec2(ae.x, el) * 5.5;
    vec2 cell = floor(g);
    vec2 f = fract(g) - 0.5;
    f.y *= 1.0;
    float dots = smoothstep(0.30, 0.26, length(f + 0.18 * vec2(sin(t + cell.x), cos(t * 0.8 + cell.y)))) * step(0.5, hash21(cell));
    col = mix(col, mix(col, vec3(1.0), 0.6), dots * 0.5 * (0.8 + 0.4 * Kick));
    // arcoiris
    vec2 rc = vec2(0.0, -0.45);
    float rr = length(ae - rc) * 0.62;
    float ra = (rr - 0.62) / 0.16;
    float inr = step(0.0, ra) * step(ra, 1.0);
    col = mix(col, rainbow(1.0 - ra) * 1.0 + 0.1, inr * 0.85 * (0.85 + 0.3 * Level));
    // nubes
    float cl = fbm(vec3(ae.x * 2.4, el * 6.0, t * 0.03));
    col = mix(col, vec3(1.0), pow(smoothstep(0.52, 0.8, cl), 1.4) * smoothstep(0.02, 0.15, el) * smoothstep(0.65, 0.15, el) * 0.9);
    // corazoncitos
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        float x = (fi - 1.0) * 0.55 + 0.0;
        float yb = 0.42 + 0.1 * fi + 0.045 * abs(sin(t * 2.2 + fi * 1.7)) * (1.0 + Kick);
        vec2 h = heart(ae, vec2(x * 2.0, yb), 0.07 + 0.01 * fi);
        col = mix(col, mix(P0, vec3(1.0), 0.15) * 1.15, h.x * 0.95);
        col += vec3(1.0) * h.y * 0.45;
    }
    col += stars(ae, el, t) * vec3(1.0, 0.9, 0.6) * 0.6;
    return col * (0.85 + 0.25 * Level + 0.15 * Beat);
}

// 16: MESMERIZER - tunel hipnotico de espirales con ojos que giran y parpadean con el bombo
vec3 sceneHypno(vec3 d, float t) {
    vec2 ae = azel(d);
    float el = max(ae.y, 0.0);
    vec2 c = vec2(0.0, 0.58);
    vec2 p = ae - c;
    p.x *= 0.85;
    float r = length(p), a = atan(p.y, p.x);
    float tun = 1.0 / (r + 0.12);
    float sp = sin(a * 4.0 + tun * 3.2 - t * 1.9 - Bass * 2.0);
    float rg = sin(tun * 6.0 - t * 2.6);
    float m = smoothstep(-0.1, 0.1, sp * 0.7 + rg * 0.3);
    vec3 col = mix(P0 * 0.45, P1 * 0.95, m);
    col *= 0.35 + 0.9 * smoothstep(0.0, 0.5, r);
    col += P2 * exp(-r * r * 40.0) * (0.5 + 1.2 * Kick);
    // 6 ojos en ronda
    for (int i = 0; i < 6; i++) {
        float fi = float(i);
        float ang = fi * TAU / 6.0 + t * 0.22;
        vec2 ec = c + vec2(cos(ang) * 0.95 / 0.85, sin(ang) * 0.5 + 0.0);
        vec2 q = ae - ec;
        float L = 0.30, hgt = 0.11 * (0.35 + 0.65 * (1.0 - 0.8 * Kick));
        float lens = hgt * (1.0 - pow(abs(q.x) / L, 2.0));
        float inLens = smoothstep(0.012, -0.004, abs(q.y) - max(lens, 0.0)) * step(abs(q.x), L);
        float iris = smoothstep(0.075, 0.055, length(q));
        float pup = smoothstep(0.034, 0.024, length(q));
        vec3 ec3 = mix(vec3(0.97), mix(P2, vec3(1.0), 0.2), iris);
        ec3 = mix(ec3, vec3(0.03), pup);
        col = mix(col, ec3, inLens * 0.95);
        col += P2 * exp(-pow((abs(q.y) - max(lens, 0.0)) * 40.0, 2.0)) * 0.3 * step(abs(q.x), L);
    }
    col *= 0.75 + 0.5 * Level;
    col += stars(ae, el, t) * 0.4;
    return col;
}

// 17: TRIPLE BAKA - pop art: rayos de sol de tres colores, puntos de semitono y destellos en cada golpe
vec3 sceneBaka(vec3 d, float t) {
    vec2 ae = azel(d);
    float el = max(ae.y, 0.0);
    vec2 c = vec2(0.0, 0.5);
    vec2 p = ae - c;
    p.x *= 0.8;
    float r = length(p), a = atan(p.y, p.x) + t * 0.12;
    float n = 30.0;
    float sec = floor(a / TAU * n + 0.5 * 0.0);
    float k = mod(sec, 3.0);
    vec3 col = k < 0.5 ? P0 : k < 1.5 ? P1 : P2;
    col *= 0.62 + 0.35 * smoothstep(0.0, 1.0, r) + 0.25 * Level;
    // semitono
    vec2 g = (ae + vec2(0.0, 0.0)) * 22.0;
    g = rot2(0.5) * g;
    vec2 f = fract(g) - 0.5;
    float rad = 0.10 + 0.32 * smoothstep(0.1, 1.2, r);
    float dots = smoothstep(rad, rad - 0.08, length(f));
    col = mix(col, col * 0.55, dots * 0.55);
    // anillos de impacto
    float ring = exp(-pow((fract(r * 2.5 - t * 0.5) - 0.5) * 7.0, 2.0)) * 0.18;
    col += ring;
    col += vec3(1.0) * exp(-r * r * 10.0) * (0.35 + 1.1 * Kick);
    col = mix(col, vec3(0.04), smoothstep(0.0, 0.22, -ae.y + 0.0) * 0.0);
    col += stars(ae, el, t) * 0.5;
    return col;
}

// 18: APERTURA OBSCURE LIVE - niebla turquesa oscura con latidos que salen del escenario
vec3 sceneObscure(vec3 d, float t) {
    vec2 ae = azel(d);
    float el = max(ae.y, 0.0);
    vec3 col = mix(P1 * 0.10, vec3(0.0, 0.01, 0.025), smoothstep(0.0, 0.8, el));
    float f1 = fbm(vec3(ae.x * 1.3, el * 3.0, t * 0.05)), f2 = fbm(vec3(ae.x * 2.6 + 3.0, el * 5.0, t * 0.08 + 5.0));
    col += P0 * pow(f1, 2.4) * 0.55 * smoothstep(0.0, 0.25, el) * (0.5 + 0.8 * Level);
    col += P2 * pow(f2, 3.0) * 0.25 * (0.3 + Level);
    // anillos de latido desde detras del escenario
    vec2 q = vec2(ae.x, el - 0.1);
    float r = length(q);
    float beat = Kick;
    float rings = exp(-pow((r - (0.12 + (1.0 - beat) * 0.9)) * 7.0, 2.0)) * beat * 1.2;
    col += P0 * rings + P0 * exp(-r * 6.0) * (0.15 + 0.5 * beat);
    // rendija de luz vertical al fondo
    col += mix(P0, vec3(1.0), 0.4) * exp(-pow(ae.x * 14.0, 2.0)) * exp(-el * 2.2) * (0.25 + 0.5 * Level + 0.5 * beat);
    // luna grande detras del escenario con rayos lentos
    vec2 mq = vec2(ae.x, el - 0.52);
    float mr = length(mq), ma = atan(mq.y, mq.x);
    float rays = pow(0.5 + 0.5 * sin(ma * 11.0 + t * 0.15), 3.0) * exp(-mr * 2.2);
    col += mix(P0, vec3(1.0), 0.5) * rays * (0.18 + 0.35 * Level);
    col += mix(P0, vec3(1.0), 0.6) * smoothstep(0.17, 0.15, mr) * (0.55 + 0.5 * Level + 0.4 * beat);
    col += P0 * exp(-mr * mr * 14.0) * (0.35 + 0.6 * Level);
    col *= 1.5;
    col += stars(ae, el, t) * 0.9;
    return col;
}

// 19: DESPEDIDA / INTERMEDIO - cosmos con auroras de tres colores, nebulosa y estrellas fugaces
vec3 sceneCosmos(vec3 d, float t) {
    vec2 ae = azel(d);
    float el = max(ae.y, 0.0);
    vec3 col = mix(vec3(0.03, 0.02, 0.10), vec3(0.01, 0.0, 0.03), smoothstep(0.0, 0.9, el));
    col += mix(P0, P1, 0.5 + 0.5 * sin(ae.x * 1.3)) * pow(fbm(vec3(ae.x * 1.4, el * 2.4, t * 0.03)), 2.6) * 0.8;
    for (int k = 0; k < 3; k++) {
        float fk = float(k);
        float y = 0.28 + 0.22 * fk + 0.08 * sin(ae.x * (1.8 + fk) + t * (0.4 + 0.15 * fk) + Bass * 2.0);
        float b = exp(-pow((el - y) * (7.0 - fk), 2.0)) * (0.35 + 0.6 * Level);
        col += (k == 0 ? P0 : k == 1 ? P1 : P2) * b * 0.9;
    }
    // estrellas fugaces
    for (int i = 0; i < 2; i++) {
        float fi = float(i);
        float ph = fract(t * 0.13 + fi * 0.5);
        vec2 s0 = vec2(-1.2 + fi * 0.9, 1.0), s1 = vec2(0.6 + fi * 0.3, 0.2);
        vec2 hp = mix(s0, s1, ph);
        vec2 dir = normalize(s1 - s0);
        vec2 qq = vec2(ae.x, el) - hp;
        float along = dot(qq, -dir), across = length(qq + dir * along);
        col += vec3(1.0) * exp(-across * across * 4000.0) * exp(-max(along, 0.0) * 9.0) * step(0.0, along) * (1.0 - ph);
    }
    col += stars(ae, el, t) * 1.2;
    return col * (0.8 + 0.4 * Level);
}

void main() {
    vec4 v = InvProj * vec4(ndc, 1.0, 1.0);
    vec3 vd = v.xyz / v.w;
    vec3 d = normalize((InvView * vec4(vd, 0.0)).xyz);

    bool below = d.y < 0.0;
    vec3 dm = vec3(d.x, abs(d.y), d.z);
    float zen = acos(clamp(dm.y, -1.0, 1.0));
    float ang = atan(dm.z, dm.x);
    float t = Time;

    vec3 col;
    int sc = int(Scene + 0.5);
    if (sc == 0) col = sceneSpectrum(dm, zen, ang, t);
    else if (sc == 1) col = sceneScope(d, zen, ang, t);
    else if (sc == 2) col = sceneStar(dm, zen, ang, t);
    else if (sc == 3) col = sceneKaleido(dm, zen, ang, t);
    else if (sc == 4) col = sceneMilk(dm, zen, ang, t);
    else if (sc == 5) col = sceneMagneto(dm, zen, ang, t);
    else if (sc == 6) col = sceneFire(d, zen, ang, t);
    else if (sc == 7) col = sceneFire2(dm, zen, ang, t);
    else if (sc == 8) col = sceneSunset(dm, t);
    else if (sc == 9) col = sceneMeltGlow(dm, t);
    else if (sc == 10) col = sceneStatic(dm, t);
    else if (sc == 11) col = sceneKonton(dm, t);
    else if (sc == 12) col = sceneMagia(dm, t);
    else if (sc == 13) col = sceneRooms(dm, t);
    else if (sc == 14) col = sceneSynth(dm, t);
    else if (sc == 15) col = scenePop(dm, t);
    else if (sc == 16) col = sceneHypno(dm, t);
    else if (sc == 17) col = sceneBaka(dm, t);
    else if (sc == 18) col = sceneObscure(dm, t);
    else col = sceneCosmos(dm, t);

    if (Nova >= 0.0) {
        float ang2 = acos(clamp(dot(d, normalize(vec3(StageDir.x, 0.35, StageDir.z))), -1.0, 1.0));
        float rg = exp(-pow((ang2 - Nova * 2.6) * 8.0, 2.0)) * (1.0 - Nova);
        col += mix(P1, vec3(1.0), 0.55) * rg * 1.7;
    }
    if (below) col *= 0.5;
    // golpe de graves: pulso general + destello blanco
    col *= 0.66 * (1.0 + 0.18 * Beat);
    col += vec3(1.0) * Flash * 0.22;
    // tonemap suave para que los brillos no se quemen
    col = 1.0 - exp(-col * 1.15);
    col = pow(col, vec3(0.92));
    fragColor = vec4(col * Fade, 1.0);
}
