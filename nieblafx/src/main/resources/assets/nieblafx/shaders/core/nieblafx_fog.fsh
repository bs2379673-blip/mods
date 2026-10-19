#version 150

// Niebla volumetrica: raymarching a lo largo de cada rayo de vision, cortado por la profundidad de la escena.

in vec2 ndc;
out vec4 fragColor;

uniform sampler2D Depth;
uniform mat4 InvProj;
uniform mat4 InvView;
uniform float Time;
uniform float GDens;      // densidad global ya multiplicada por el fundido
uniform float GBase;      // altura base relativa a la camara
uniform float GFall;
uniform float MaxDist;
uniform float NoiseAmt;
uniform float Wind;
uniform float SkyFog;
uniform float Steps;
uniform float Scale;      // tamano de las nubes de niebla en bloques
uniform vec3 SunDir;      // direccion hacia el sol/luna (mundo)
uniform vec3 SunCol;      // luz directa (ya con dia/noche)
uniform vec3 Amb;         // luz ambiente
uniform float ZN;
uniform vec3 GCol;
uniform vec3 CamN;        // posicion de la camara (modulo) para que el ruido sea del mundo
uniform float ZA[32];     // x, y, z (relativos a la camara), radio
uniform float ZB[32];     // semialtura, densidad (x fundido), 0, 0
uniform float ZC[24];     // color

float hash(vec3 p) {
    p = fract(p * 0.3183099 + vec3(0.1, 0.2, 0.3));
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

float vnoise(vec3 x) {
    vec3 i = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash(i + vec3(0, 0, 0)), hash(i + vec3(1, 0, 0)), f.x),
                   mix(hash(i + vec3(0, 1, 0)), hash(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(hash(i + vec3(0, 0, 1)), hash(i + vec3(1, 0, 1)), f.x),
                   mix(hash(i + vec3(0, 1, 1)), hash(i + vec3(1, 1, 1)), f.x), f.y), f.z);
}

float fbm(vec3 p) {
    return vnoise(p) * 0.55 + vnoise(p * 2.03 + 11.3) * 0.3 + vnoise(p * 4.11 + 3.7) * 0.15;
}

float fbm2(vec3 p) {
    return vnoise(p) * 0.65 + vnoise(p * 2.03 + 11.3) * 0.35;
}

// nubes de niebla: grumos con huecos claros
float cloudy(vec3 p, float tm) {
    vec3 q = p / max(Scale, 1.0);
    q += vec3(Wind * tm * 0.02, tm * 0.004, Wind * tm * 0.012);
    vec3 w = vec3(vnoise(q * 0.7 + 5.0), vnoise(q * 0.7 + 9.0), vnoise(q * 0.7 + 2.0)) - 0.5;
    float f = fbm(q + w * 1.2);
    return smoothstep(0.28, 0.72, f) * 1.9;
}

float cloudy2(vec3 p, float tm) {
    vec3 q = p / max(Scale, 1.0);
    q += vec3(Wind * tm * 0.02, tm * 0.004, Wind * tm * 0.012);
    return smoothstep(0.28, 0.72, fbm2(q)) * 1.9;
}

void main() {
    vec2 uv = ndc * 0.5 + 0.5;
    float d = texture(Depth, uv).r;
    bool sky = d >= 0.99999;

    vec4 vf = InvProj * vec4(ndc, 1.0, 1.0);
    vec3 dirV = normalize(vf.xyz / vf.w);
    vec3 dir = normalize(mat3(InvView) * dirV);

    float dist = MaxDist;
    if (!sky) {
        vec4 vp = InvProj * vec4(ndc, d * 2.0 - 1.0, 1.0);
        dist = length(vp.xyz / vp.w);
    }
    float len = min(dist, MaxDist);
    float n = max(Steps, 4.0);
    float dt = len / n;
    float t = dt * hash(vec3(gl_FragCoord.xy, fract(Time) * 7.0));

    float tau = 0.0;
    vec3 cw = vec3(0.0);
    int zn = int(ZN);
    for (int i = 0; i < 64; i++) {
        if (float(i) >= n || tau > 6.0) break;
        vec3 p = dir * t;
        float nm = 1.0;
        vec3 wp = p + CamN;
        if (NoiseAmt > 0.001) nm = mix(1.0, cloudy(wp, Time), NoiseAmt);
        float g = GDens;
        if (GFall > 0.0001) g *= exp(-max(p.y - GBase, 0.0) * GFall);
        g *= nm;
        float sum = g;
        vec3 col = GCol * g;
        vec3 zsum = vec3(0.0);
        for (int k = 0; k < 8; k++) {
            if (k >= zn) break;
            vec3 zc = vec3(ZA[k * 4], ZA[k * 4 + 1], ZA[k * 4 + 2]);
            float rad = ZA[k * 4 + 3];
            vec3 o = p - zc;
            float h = length(o.xz) / rad;
            float v = abs(o.y) / max(ZB[k * 4], 0.001);
            float q = length(vec2(h, v));
            float f = 1.0 - smoothstep(0.3, 1.0, q);
            if (f > 0.0) {
                float zd = ZB[k * 4 + 1] * f * nm;
                sum += zd;
                col += vec3(ZC[k * 3], ZC[k * 3 + 1], ZC[k * 3 + 2]) * zd;
            }
        }
        // luz: ambiente + sol disperso con autosombra (el centro de las nubes queda mas oscuro, los bordes brillan)
        float sh = 1.0;
        if (NoiseAmt > 0.001 && sum > 0.0005) {
            vec3 wq = wp + SunDir * 10.0;
            float nd = mix(1.0, cloudy2(wq, Time), NoiseAmt);
            sh = exp(-sum * nd * 10.0 * 0.8);
        }
        float ph = 0.45 + 0.55 * pow(max(dot(dir, SunDir), 0.0), 3.0);
        vec3 lit = Amb + SunCol * sh * ph;
        tau += sum * dt;
        cw += col * lit * dt;
        t += dt;
    }
    float a = 1.0 - exp(-tau);
    if (sky) a *= SkyFog;
    vec3 c = cw / max(tau, 1e-5);
    fragColor = vec4(c, clamp(a, 0.0, 1.0));
}
