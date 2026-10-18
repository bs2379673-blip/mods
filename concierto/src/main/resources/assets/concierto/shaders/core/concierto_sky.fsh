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
    else col = sceneFire2(dm, zen, ang, t);

    if (below) col *= 0.5;
    // golpe de graves: pulso general + destello blanco
    col *= 1.0 + 0.35 * Beat;
    col += vec3(1.0) * Flash * 0.55;
    // tonemap suave para que los brillos no se quemen
    col = 1.0 - exp(-col * 1.35);
    col = pow(col, vec3(0.92));
    fragColor = vec4(col * Fade, 1.0);
}
