#version 150

// Escenario: piso de cuadricula (0), panel ecualizador de puntos LED (1), rayo de luz (2), anillo (3), columna de luz (4)

in vec4 vColor;
in vec2 vUV;
out vec4 fragColor;

uniform float Kind;
uniform float Time;
uniform float Alpha;
uniform float Level;
uniform float Bass;
uniform float Mid;
uniform float High;
uniform float Beat;
uniform float Flash;
uniform float FloorMode;
uniform float PanelGain;
uniform float Shock;
uniform float Prog;
uniform float Scale;
uniform vec3 P0;
uniform vec3 P1;
uniform vec3 P2;
uniform float Spec[32];

float hash21(vec2 p) { vec3 p3 = fract(vec3(p.xyx) * 0.1031); p3 += dot(p3, p3.yzx + 33.33); return fract((p3.x + p3.y) * p3.z); }
vec3 rainbow(float h) { return clamp(abs(mod(h * 6.0 + vec3(0.0, 4.0, 2.0), 6.0) - 3.0) - 1.0, 0.0, 1.0); }

float spec(float x) {
    float f = clamp(x, 0.0, 1.0) * 31.0;
    int i = int(floor(f));
    return mix(Spec[i], Spec[min(i + 1, 31)], fract(f));
}

vec4 floorCell() {
    vec2 uv = vUV;
    vec2 cell = floor(uv);
    vec2 f = fract(uv);
    float edge = min(min(f.x, 1.0 - f.x), min(f.y, 1.0 - f.y));
    float line = smoothstep(0.075, 0.0, edge);
    float d = length(uv) / max(Scale, 0.001);
    vec3 fill, lineC;
    int m = int(FloorMode + 0.5);
    if (m == 2) {
        float w = 0.5 + 0.5 * sin(d * 3.2 - Time * 3.6 - Bass * 2.0);
        fill = mix(P0, P1, w) * (0.16 + 0.32 * Level);
        lineC = mix(P2, vec3(1.0), 0.35 + 0.4 * w);
    } else if (m == 3) {
        float step_ = floor(Time * 2.0);
        float h = hash21(cell + step_);
        float lit = step(1.0 - (0.18 + 0.5 * Level), h);
        float zone = smoothstep(7.0, 4.5, d);
        vec3 disco = rainbow(fract(h * 7.0 + Time * 0.05));
        float w = 0.5 + 0.5 * sin(d * 3.0 - Time * 3.0);
        fill = mix(mix(P0, P1, w) * (0.16 + 0.25 * Level), disco * 0.8, lit * zone);
        lineC = P2;
    } else {
        fill = P0 * (0.16 + 0.20 * Level);
        lineC = mix(P1, P2, 0.5 + 0.5 * sin(Time * 0.7));
    }
    fill *= 1.0 + 0.3 * Beat;
    vec3 col = fill + lineC * line * (0.55 + 0.45 * Beat + 0.3 * Level);
    // onda de choque desde el centro del escenario
    if (Shock >= 0.0) {
        float r = Shock * 9.0;
        float band = exp(-pow((d - r) * 2.4, 2.0)) * (1.0 - Shock);
        col += mix(P2, vec3(1.0), 0.6) * band * 1.2;
    }
    col += vec3(Flash * 0.12);
    float rr = length(vColor.rg);
    float edgeFade = smoothstep(1.0, 0.92, rr);
    // aro luminoso en el borde de la tarima redonda y brillo suave hacia afuera
    float rim = exp(-pow((rr - 0.955) * 28.0, 2.0));
    col += mix(P2, vec3(1.0), 0.5) * rim * (0.7 + 0.5 * Beat);
    col = 1.0 - exp(-col * 1.15);
    float a = clamp(0.72 + 0.2 * line + 0.2 * rim, 0.0, 1.0) * edgeFade;
    return vec4(col, a * Alpha);
}

vec4 panelLed() {
    const float COLS = 30.0;
    const float ROWS = 18.0;
    vec2 g = vUV * vec2(COLS, ROWS);
    vec2 cell = floor(g);
    vec2 c = fract(g) - 0.5;
    float x = (cell.x + 0.5) / COLS;
    float h = spec(1.0 - abs(2.0 * x - 1.0));
    h = clamp(h * PanelGain * 1.05, 0.0, 1.0);
    float rowN = (cell.y + 0.5) / ROWS;
    float lit = step(rowN, h);
    float dot_ = smoothstep(0.46, 0.30, length(c));
    vec3 grad = mix(P0, P1, rowN);
    vec3 on = grad * (0.95 + 0.5 * Beat) + P2 * smoothstep(0.85, 1.0, rowN) * 0.6;
    float cap = step(abs(rowN - h), 0.5 / ROWS) * lit;
    on = mix(on, vec3(1.0), cap * 0.7);
    vec3 off = P0 * 0.10;
    vec3 col = mix(off, on, lit) * dot_;
    float a = mix(0.55, 1.0, lit) * (0.35 + 0.65 * dot_);
    // marco
    float fr = min(min(vUV.x, 1.0 - vUV.x) * COLS, min(vUV.y, 1.0 - vUV.y) * ROWS);
    a *= smoothstep(0.0, 0.35, fr);
    col += vec3(Flash * 0.2) * dot_;
    return vec4(col, a * Alpha);
}

vec4 beam() {
    float x = abs(vUV.x);
    float edge = clamp(1.0 - x, 0.0, 1.0);
    float core = pow(edge, 1.25);
    float hot = pow(edge, 5.0);
    float along = pow(clamp(1.0 - vUV.y, 0.0, 1.0), 0.55) * smoothstep(0.0, 0.02, vUV.y);
    vec3 col = vColor.rgb * (core * 0.95) + vec3(hot * 0.5);
    float a = (core * 0.42 + hot * 0.30) * along * vColor.a;
    return vec4(col, a * Alpha);
}

vec4 ring() {
    float r = length(vUV);
    float pulse = 1.0 + 0.12 * Beat + 0.05 * sin(Time * 2.0);
    float rr = r / pulse;
    float band = exp(-pow((rr - 0.80) * 18.0, 2.0));
    float band2 = exp(-pow((rr - 0.62) * 30.0, 2.0)) * 0.6;
    float glow = exp(-pow(max(rr - 0.8, 0.0) * 5.0, 2.0)) * smoothstep(0.55, 0.8, rr) * 0.35;
    float tick = 0.5 + 0.5 * sin(atan(vUV.y, vUV.x) * 24.0 - Time * 1.5);
    vec3 col = (mix(P1, vec3(1.0), 0.6) * band + P2 * band2 * (0.6 + 0.4 * tick) + P0 * glow) * (1.0 + 0.8 * Beat);
    float a = clamp(band + band2 + glow, 0.0, 1.0) * smoothstep(1.0, 0.92, r);
    return vec4(col, a * Alpha);
}

vec4 column() {
    float x = abs(vUV.x);
    float core = exp(-x * x * 7.0);
    float hot = exp(-x * x * 40.0);
    float head = 1.0 - Prog;
    float vis = smoothstep(head - 0.02, head + 0.06, vUV.y);
    float tip = exp(-pow((vUV.y - head) * 14.0, 2.0)) * step(Prog, 0.999);
    float up = pow(clamp(vUV.y, 0.0, 1.0), 0.15);
    vec3 col = vColor.rgb * core * 1.4 + vec3(hot * 1.1) + vColor.rgb * tip * 1.6;
    float a = (core * 0.8 + hot * 0.5 + tip) * vis * up * vColor.a;
    return vec4(col, clamp(a, 0.0, 1.0) * Alpha);
}

void main() {
    int k = int(Kind + 0.5);
    vec4 c;
    if (k == 0) c = floorCell();
    else if (k == 1) c = panelLed();
    else if (k == 2) c = beam();
    else if (k == 3) c = ring();
    else c = column();
    fragColor = c;
}
