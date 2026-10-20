#version 150

uniform sampler2D DiffuseSampler;
uniform vec2 OutSize;
uniform float Mode;
uniform float Amount;
uniform float Clock;

in vec2 texCoord;
out vec4 fragColor;

float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float hash1(float x) { return fract(sin(x * 91.3458) * 47453.5453); }
float luma(vec3 c) { return dot(c, vec3(0.299, 0.587, 0.114)); }
vec3 tex(vec2 uv) { return texture(DiffuseSampler, clamp(uv, 0.001, 0.999)).rgb; }

void main() {
    vec2 uv = texCoord;
    vec3 base = tex(uv);
    vec3 col = base;
    int m = int(Mode + 0.5);
    float t = Clock;
    vec2 px = 1.0 / OutSize;
    float d = length(uv - 0.5);

    if (m == 1) { // VHS
        float band = fract(t * 0.13);
        float bd = abs(uv.y - band);
        float wob = (hash1(floor(uv.y * 240.0) + floor(t * 20.0)) - 0.5) * 0.004;
        wob += smoothstep(0.08, 0.0, bd) * 0.03 * sin(uv.y * 80.0 + t * 30.0);
        vec2 u2 = vec2(uv.x + wob, uv.y);
        col.r = tex(u2 + vec2(px.x * 4.0, 0.0)).r;
        col.g = tex(u2).g;
        col.b = tex(u2 - vec2(px.x * 4.0, 0.0)).b;
        col *= 0.88 + 0.12 * sin(uv.y * OutSize.y * 1.5);
        col += (hash(uv * OutSize + t) - 0.5) * 0.10;
        col = mix(col, vec3(luma(col)), 0.15);
        col += smoothstep(0.05, 0.0, bd) * 0.18;
        col *= 1.0 - smoothstep(0.55, 0.95, d) * 0.5;
    } else if (m == 2) { // cine antiguo
        float flick = 0.88 + 0.12 * hash1(floor(t * 14.0));
        float g = luma(col);
        col = vec3(g) * vec3(1.15, 0.95, 0.72);
        col *= flick;
        col += (hash(uv * OutSize * 0.7 + floor(t * 24.0)) - 0.5) * 0.14;
        float sx = hash1(floor(t * 12.0)) ;
        float scratch = step(0.9985, hash(vec2(floor(uv.x * 400.0), floor(t * 12.0))));
        col += scratch * 0.35;
        col *= 1.0 - smoothstep(0.25, 0.85, d) * 0.85;
        vec2 jit = vec2(hash1(floor(t * 10.0)) - 0.5, hash1(floor(t * 10.0) + 7.0) - 0.5) * 0.002;
        col = mix(col, tex(uv + jit) * vec3(1.15, 0.95, 0.72) * flick, 0.0);
    } else if (m == 3) { // vision nocturna
        float g = luma(col);
        g = pow(clamp(g * 2.6, 0.0, 1.0), 0.7);
        g += (hash(uv * OutSize + t * 3.0) - 0.5) * 0.18;
        col = vec3(0.08, 1.0, 0.18) * g;
        col *= 0.9 + 0.1 * sin(uv.y * OutSize.y * 1.2 + t * 4.0);
        col *= 1.0 - smoothstep(0.45, 0.72, d);
    } else if (m == 4) { // termica
        float g = luma(base);
        g = clamp(g * 1.5 + (base.r - base.b) * 0.5, 0.0, 1.0);
        vec3 c0 = vec3(0.0, 0.0, 0.25), c1 = vec3(0.45, 0.0, 0.6), c2 = vec3(0.9, 0.1, 0.1), c3 = vec3(1.0, 0.75, 0.0), c4 = vec3(1.0, 1.0, 0.8);
        if (g < 0.25) col = mix(c0, c1, g / 0.25);
        else if (g < 0.5) col = mix(c1, c2, (g - 0.25) / 0.25);
        else if (g < 0.75) col = mix(c2, c3, (g - 0.5) / 0.25);
        else col = mix(c3, c4, (g - 0.75) / 0.25);
        col += (hash(uv * OutSize + t) - 0.5) * 0.04;
    } else if (m == 5) { // glitch
        float slot = floor(t * 9.0);
        float on = step(0.55, hash1(slot));
        float row = floor(uv.y * 24.0);
        float shift = (hash(vec2(row, slot)) - 0.5) * 0.25 * on * step(0.6, hash(vec2(row + 3.0, slot)));
        vec2 u2 = vec2(uv.x + shift, uv.y);
        float ch = 0.01 * on + 0.002;
        col.r = tex(u2 + vec2(ch, 0.0)).r;
        col.g = tex(u2).g;
        col.b = tex(u2 - vec2(ch, 0.0)).b;
        vec2 blk = floor(uv * vec2(14.0, 9.0));
        float bl = step(0.93, hash(blk + slot)) * on;
        col = mix(col, 1.0 - col, bl);
        col += step(0.985, hash1(slot + 11.0)) * 0.3;
    } else if (m == 6) { // pesadilla
        float beat = pow(max(sin(t * 5.2), 0.0), 6.0) + pow(max(sin(t * 5.2 + 1.1), 0.0), 8.0) * 0.6;
        vec2 c = uv - 0.5;
        vec2 u2 = 0.5 + c * (1.0 - beat * 0.035 - 0.01 * sin(t * 1.3));
        col = tex(u2);
        col = mix(col, vec3(luma(col)), 0.35);
        col.r += beat * 0.08;
        float v = smoothstep(0.25, 0.8, d + beat * 0.08);
        col = mix(col, col * vec3(0.9, 0.12, 0.1), v);
        col *= 1.0 - v * 0.55;
    } else if (m == 7) { // ebrio
        float s1 = sin(t * 1.3) * 0.012, s2 = cos(t * 1.7) * 0.012;
        vec2 w = vec2(sin(uv.y * 9.0 + t * 1.8), cos(uv.x * 9.0 + t * 1.5)) * 0.007;
        vec3 a = tex(uv + w + vec2(s1, s2));
        vec3 b = tex(uv + w - vec2(s1, s2));
        vec3 cc = tex(uv + w * 1.5);
        col = (a + b + cc) / 3.0;
        col *= 1.0 - smoothstep(0.35, 0.9, d) * 0.4;
    } else if (m == 8) { // comic
        vec3 q = floor(base * 4.0 + 0.5) / 4.0;
        float g00 = luma(tex(uv + px * vec2(-1.0, -1.0)));
        float g20 = luma(tex(uv + px * vec2(1.0, -1.0)));
        float g02 = luma(tex(uv + px * vec2(-1.0, 1.0)));
        float g22 = luma(tex(uv + px * vec2(1.0, 1.0)));
        float gx = (g20 + g22) - (g00 + g02);
        float gy = (g02 + g22) - (g00 + g20);
        float edge = smoothstep(0.08, 0.2, length(vec2(gx, gy)));
        col = mix(q * 1.1, vec3(0.03), edge);
    } else if (m == 9) { // gameboy
        vec2 res = vec2(160.0, 144.0);
        vec2 u2 = (floor(uv * res) + 0.5) / res;
        float g = luma(tex(u2));
        float bay = mod(floor(u2.x * res.x) + floor(u2.y * res.y) * 2.0, 4.0) / 4.0 - 0.375;
        g = clamp(g + bay * 0.12, 0.0, 1.0);
        float lvl = floor(g * 3.99);
        vec3 pal[4] = vec3[4](vec3(0.06, 0.22, 0.06), vec3(0.19, 0.38, 0.19), vec3(0.55, 0.67, 0.06), vec3(0.61, 0.74, 0.06));
        col = pal[int(lvl)];
        col = lvl > 2.5 ? vec3(0.61, 0.74, 0.06) : col;
    } else if (m == 10) { // tunel
        float v = smoothstep(0.12, 0.45, d);
        vec3 acc = vec3(0.0);
        for (int i = 0; i < 6; i++) {
            float k = float(i) / 5.0;
            acc += tex(0.5 + (uv - 0.5) * (1.0 - v * 0.04 * k));
        }
        col = mix(base, acc / 6.0, v);
        col = mix(col, vec3(luma(col)) * 0.5, v * 0.5);
        col *= 1.0 - smoothstep(0.25, 0.6, d);
    } else if (m == 11) { // negativo
        float p = 0.5 + 0.5 * sin(t * 2.0);
        col = mix(base, 1.0 - base, 0.7 + 0.3 * p);
    }

    fragColor = vec4(mix(base, col, clamp(Amount, 0.0, 1.0)), 1.0);
}
