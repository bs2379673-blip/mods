#version 150

// Anomalia: la flor Mortifilia aparece en el cenit, se abre, suelta esporas que tiñen el cielo y queda una tormenta electrica.

in vec2 ndc;
out vec4 fragColor;

uniform mat4 InvProj;
uniform mat4 InvView;
uniform float Time;        // segundos desde que empezo
uniform float Appear;      // 0..1 la flor se hace visible
uniform float Open;        // 0..1 apertura de los petalos
uniform float Spread;      // 0..1 avance del frente de color (0 = nada, 1 = todo el cielo)
uniform float FlowerFade;  // 1 = flor entera, 0 = disuelta en el color
uniform float Fade;        // 1 = visible, 0 = cielo normal (al apagarse)
uniform float StormK;      // violencia de la tormenta
uniform float Flash;       // destello general (relampagos)
uniform float Strobe;      // 0..1 cuanto brillan los relampagos
uniform vec3 Col0;         // oscuro
uniform vec3 Col1;         // medio
uniform vec3 Col2;         // brillante
uniform float Bolts[12];   // 3 rayos: az, el, edad (<0 = apagado), semilla

const float PI = 3.14159265;
const float TAU = 6.2831853;

float h11(float p) { p = fract(p * 0.1031); p *= p + 33.33; p *= p + p; return fract(p); }
float h21(vec2 p) { vec3 p3 = fract(vec3(p.xyx) * 0.1031); p3 += dot(p3, p3.yzx + 33.33); return fract((p3.x + p3.y) * p3.z); }
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
float wrapA(float a) { return a - TAU * floor((a + PI) / TAU); }

// ---------------------------------------------------------------- la flor (vista desde abajo, centrada en el cenit)
// devuelve rgb + alfa. rho = distancia angular al cenit (rad), phi = giro
vec4 flower(float rho, float phi, float t, float open, float app) {
    // radio total: capullo cerrado -> flor abierta
    float R = mix(0.24, 0.60, smoothstep(0.0, 1.0, open)) * (0.55 + 0.45 * smoothstep(0.0, 0.7, app));
    float breathe = 1.0 + 0.012 * sin(t * 1.7);
    R *= breathe;
    vec3 col = vec3(0.0);
    float alpha = 0.0;

    // halo detras de la flor
    float halo = exp(-pow(rho / (R * 1.5), 2.0)) * app;
    col += Col1 * halo * 0.9;
    alpha = max(alpha, halo * 0.55);

    // capas de petalos, de atras hacia adelante: (cantidad, radio, giro, brillo, curvatura)
    for (int L = 0; L < 3; L++) {
        float fl = float(L);
        float n = L == 2 ? 7.0 : 9.0;
        float rl = R * (1.0 - 0.27 * fl);
        float rot = fl * (PI / n) + 0.35 * fl + 0.03 * sin(t * 0.5 + fl);
        // el capullo se cierra: los petalos de adentro se ven primero, las capas externas se abren despues
        float lo = clamp((open - 0.12 * (2.0 - fl)) / 0.7, 0.0, 1.0);
        float curl = mix(0.50, 1.0, smoothstep(0.0, 1.0, lo));
        float closed = 1.0 - smoothstep(0.0, 0.75, lo);
        float pp = phi + closed * rho * 11.0 / max(R, 0.05) * 0.35;   // capullo: petalos enrollados en espiral
        float ph2 = (pp - rot) * n / TAU;                       // petalo = tramo de 1.0
        float pidx = floor(ph2 + 0.5);
        float hv = h11(pidx * 3.7 + fl * 11.0);
        float u = cos(n * (pp - rot) * 0.5);                   // -1..1 por petalo
        float lobe = mix(pow(max(0.5 + 0.5 * u, 0.0), 0.95), 0.80 + 0.20 * (0.5 + 0.5 * u), closed);         // punta mas marcada
        float lenv = 0.82 + 0.30 * hv;                           // cada petalo con su largo
        float wob = 0.018 * sin(phi * 23.0 + fl * 3.0 + t * 0.4) * (0.5 + hv);
        float rp = rl * curl * lenv * (0.34 + 0.66 * lobe) + wob * rl;
        float inside = smoothstep(rp, rp - 0.012, rho);
        // sombra que proyecta esta capa sobre las de atras
        float castS = smoothstep(rp * 1.22, rp, rho) * (1.0 - inside);
        col *= 1.0 - 0.62 * castS * (fl < 2.0 ? 1.0 : 0.0) * step(0.02, alpha);
        if (inside <= 0.001) continue;
        float x = rho / max(rp, 1e-3);                           // 0 centro .. 1 borde
        float delta = (0.5 + 0.5 * u);                           // 1 = eje del petalo
        vec3 cream = vec3(0.93, 0.84, 0.78);
        vec3 deep = mix(vec3(0.38, 0.05, 0.06), Col0 * 3.0 + vec3(0.1, 0.0, 0.0), 0.3);
        vec3 pc = mix(deep, cream, smoothstep(0.22, 0.85, x) * (1.0 - 0.85 * closed));
        pc = mix(pc, deep * 0.55, 0.45 * closed * (1.0 - smoothstep(0.55, 1.0, x)));
        float veins = 0.5 + 0.5 * sin((phi - rot) * n * 9.0 + rho * 40.0);
        pc *= 0.93 + 0.07 * veins * smoothstep(0.1, 0.6, x);
        pc *= 0.80 + 0.20 * delta + 0.10 * (hv - 0.5);
        pc *= mix(0.22, 1.0, smoothstep(0.0, mix(0.55, 0.30, closed * 0.0 + (1.0 - closed) * 1.0), delta));   // valles oscuros entre petalos
        pc *= 0.40 + 0.60 * smoothstep(0.0, 0.9, x);
        pc *= 1.0 - 0.35 * (0.5 + 0.5 * sin((pp - rot) * n * 3.0 + 1.0)) * (1.0 - closed);   // sombra profunda hacia el centro
        pc *= 0.90 + 0.18 * fbm(vec3(phi * 3.0, rho * 14.0, fl + hv * 5.0));
        pc = mix(pc, Col2 * 1.1, 0.10 * (1.0 - x) + 0.10 * pow(x, 6.0));
        float rim = exp(-pow((x - 1.0) * 12.0, 2.0));
        pc += cream * rim * 0.30;
        // sombra entre capas
        pc *= 0.7 + 0.3 * smoothstep(0.0, 0.5, x);
        float a = inside * mix(0.92, 0.75, x * x);
        col = mix(col, pc, a);
        alpha = max(alpha, a);
    }

    // centro: nucleo rojo oscuro con estambres que terminan en esporas brillantes
    float cr = R * 0.21;
    float core = smoothstep(cr, cr * 0.55, rho) * smoothstep(0.25, 0.7, open);
    vec3 coreC = mix(Col0 * 1.4 + vec3(0.10, 0.0, 0.02), Col1 * 0.8, 0.25 + 0.2 * sin(t * 2.0));
    col = mix(col, coreC, core * 0.95);
    alpha = max(alpha, core);
    float sN = 72.0;
    float s = phi / TAU * sN;
    float idx = floor(s), fr = fract(s) - 0.5;
    float hh = h11(idx * 1.7 + 3.0);
    float len = R * (0.18 + 0.36 * hh) * smoothstep(0.5, 1.0, open);
    float wline = abs(fr) * rho * (TAU / sN);
    float st = smoothstep(0.0058, 0.0010, wline) * step(rho, len) * step(cr * 0.5, rho);
    float tip = exp(-(pow((rho - len) * 70.0, 2.0) + pow(wline * 140.0, 2.0)));
    vec3 stC = mix(vec3(0.35, 0.02, 0.04), Col2, rho / max(len, 1e-3));
    col += stC * st * 0.9 + Col2 * tip * (1.2 + 0.5 * sin(t * 3.0 + idx));
    alpha = max(alpha, max(st * 0.85, tip));
    // pulso de luz en el centro al abrirse
    col += Col2 * exp(-pow(rho / (R * 0.28), 2.0)) * 0.45 * (0.6 + 0.4 * sin(t * 2.4)) * smoothstep(0.2, 1.0, open);
    return vec4(col, clamp(alpha, 0.0, 1.0));
}

// ---------------------------------------------------------------- rayos
float bolt(vec3 d, float az0, float el0, float age, float seed, out float glow) {
    glow = 0.0;
    if (age < 0.0 || age > 0.9) return 0.0;
    float el = asin(clamp(d.y, -1.0, 1.0));
    float az = atan(d.x, d.z);
    float inten = age < 0.45 ? (1.0 - age / 0.45) * (0.65 + 0.35 * sin(age * 90.0)) : 0.0;
    // la nube se ilumina alrededor
    float dist = length(vec2(wrapA(az - az0) * cos(el0), el - el0));
    glow = exp(-dist * dist * 6.0) * exp(-age * 5.0);
    float sp = clamp((el0 - el) / 0.55, 0.0, 1.0);              // el rayo baja desde el origen
    float v = el0 - el;
    float off1 = 0.05 * sin(v * 38.0 + seed * 9.0) + 0.022 * sin(v * 113.0 + seed * 31.0) + 0.012 * sin(v * 251.0 + seed);
    float b1 = exp(-pow(wrapA(az - az0 - off1) * cos(el) * 120.0, 2.0)) * step(0.0, v) * step(v, 0.55) * (1.0 - 0.5 * sp);
    float off2 = off1 + 0.10 * (v - 0.12) + 0.03 * sin(v * 70.0 + seed * 5.0);
    float b2 = exp(-pow(wrapA(az - az0 - off2) * cos(el) * 150.0, 2.0)) * step(0.12, v) * step(v, 0.40) * 0.6;
    return (b1 + b2) * inten;
}

void main() {
    vec4 v = InvProj * vec4(ndc, 1.0, 1.0);
    vec3 vd = v.xyz / v.w;
    vec3 d = normalize((InvView * vec4(vd, 0.0)).xyz);
    float t = Time;
    float theta = acos(clamp(d.y, -1.0, 1.0));      // 0 = cenit, PI = nadir
    float phi = atan(d.z, d.x);

    // ---- frente de color: nace en el cenit y baja por el cielo con bordes irregulares (esporas pintando)
    float n1 = fbm(vec3(d * 2.4 + vec3(0.0, t * 0.04, 0.0)));
    float tend = 0.28 * sin(phi * 5.0 + t * 0.6 + n1 * 4.0) + 0.22 * (n1 - 0.5) * 3.0;
    float front = Spread * 2.85;                     // radianes: al final cubre todo el cielo visible (el horizonte es 1.57)
    float cov = smoothstep(front, front - 0.55, theta + tend * (0.4 + 0.6 * (1.0 - Spread)));
    cov *= step(0.0001, Spread);
    float edge = exp(-pow((theta + tend * 0.7 - front + 0.2) * 5.0, 2.0)) * step(0.0001, Spread) * (1.0 - smoothstep(0.85, 1.0, Spread));

    // ---- tormenta
    float c1 = fbm(d * 3.0 + vec3(0.0, t * 0.07, t * 0.04));
    float c2 = fbm(d * 7.5 - vec3(t * 0.09, 0.0, t * 0.03));
    float veins = pow(1.0 - abs(fbm(d * 5.0 + vec3(t * 0.05, 0.0, 0.0)) * 2.0 - 1.0), 7.0);
    float pulse = 0.5 + 0.5 * sin(t * (0.8 + 1.2 * StormK) + c1 * 6.0);
    vec3 storm = Col0 * (0.35 + 1.4 * c1) + Col1 * pow(c2, 2.2) * (0.55 + 0.5 * StormK) + Col1 * veins * (0.25 + 0.5 * pulse) * StormK * 1.2;
    storm += Col2 * pow(c1 * c2, 3.0) * 0.8;
    float glowSum = 0.0, boltSum = 0.0;
    for (int i = 0; i < 3; i++) {
        float g;
        float bb = bolt(d, Bolts[i * 4], Bolts[i * 4 + 1], Bolts[i * 4 + 2], Bolts[i * 4 + 3], g);
        boltSum += bb;
        glowSum += g * (0.5 + 0.5 * c1);
    }
    storm += Col2 * glowSum * 1.4 * Strobe + mix(Col2, vec3(1.0), 0.7) * boltSum * 2.4 * Strobe;
    storm += Col2 * Flash * 0.18 * (0.4 + c1);
    // luz del frente de esporas
    storm += mix(Col2, vec3(1.0), 0.3) * edge * (0.7 + 0.8 * c2);

    // ---- esporas: puntos de luz que flotan, mas densas cerca del frente
    float sp = 0.0;
    for (int k = 0; k < 2; k++) {
        float fk = float(k);
        float G = 34.0 + 26.0 * fk;
        vec3 q = d * G + vec3(0.0, -t * (0.5 + 0.4 * fk), 0.0) + vec3(sin(t * 0.4 + fk) * 2.0, 0.0, cos(t * 0.3) * 2.0);
        vec3 cell = floor(q);
        vec3 f = fract(q) - 0.5;
        float hh = h21(cell.xy + cell.z * 17.0 + fk * 5.0);
        float on = step(0.93, hh) * smoothstep(0.35, 0.0, length(f)) * (0.5 + 0.5 * sin(t * (2.0 + hh * 6.0) + hh * 50.0));
        float zone = smoothstep(front + 0.5, front - 0.2, theta) * step(0.0001, Spread);
        sp += on * zone * (0.5 + 1.5 * edge + 0.4 * cov);
    }
    // ---- flor
    vec4 fw = flower(theta, phi, t, Open, Appear);
    float fa = fw.a * FlowerFade;
    float fadeFl = smoothstep(0.0, 0.15, Appear);

    vec3 col = vec3(0.0);
    float alpha = 0.0;
    // oscurecimiento suave del cenit antes de que llegue el color
    float pre = exp(-pow(theta / 0.9, 2.0)) * Appear * 0.45;
    col = Col0 * 0.6; alpha = pre;
    // tormenta cubriendo
    col = mix(col, storm, cov);
    alpha = max(alpha, cov * 0.97);
    // esporas encima
    col += Col2 * sp * 1.4;
    alpha = max(alpha, clamp(sp, 0.0, 1.0));
    // la flor se disuelve en el mismo color de la tormenta: su alfa baja y su color se mezcla con el cielo teñido
    vec3 fc = mix(fw.rgb, storm * 1.1, 1.0 - FlowerFade);
    col = mix(col, fc, fa * fadeFl);
    alpha = max(alpha, fa * fadeFl);
    // brillo de la flor que se aleja con la disolucion
    col += Col2 * exp(-pow(theta / 0.35, 2.0)) * (1.0 - FlowerFade) * FlowerFade * 1.2;
    col = 1.0 - exp(-col * 1.25);
    col = pow(col, vec3(0.95));
    fragColor = vec4(col, clamp(alpha, 0.0, 1.0) * Fade);
}
