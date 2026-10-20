#version 150

// Vision PlayStation 1: baja resolucion con pixeles duros, color de 15 bits con tramado (dither) y niebla corta de distancia.
uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;
uniform vec2 InSize;
uniform float Res;
uniform float Dither;
uniform float Amount;
uniform float FarPlane;
uniform vec2 FogRange;
uniform vec3 FogCol;

in vec2 texCoord;
out vec4 fragColor;

const float NEAR = 0.05;

// matriz de tramado 4x4 de Bayer (el patron tipico de la PS1)
float bayer4(vec2 p) {
    int x = int(mod(p.x, 4.0));
    int y = int(mod(p.y, 4.0));
    int i = x + y * 4;
    float m[16] = float[16](0.0, 8.0, 2.0, 10.0,
                            12.0, 4.0, 14.0, 6.0,
                            3.0, 11.0, 1.0, 9.0,
                            15.0, 7.0, 13.0, 5.0);
    return (m[i] + 0.5) / 16.0;
}

void main() {
    vec4 orig = texture(DiffuseSampler, texCoord);

    // cuadricula de baja resolucion: cada celda es un "pixel" de la consola
    vec2 size = vec2(Res * InSize.x / InSize.y, Res);
    vec2 cell = floor(texCoord * size);
    vec2 uvc = (cell + 0.5) / size;
    vec2 q = 0.22 / size;

    // 4 muestras dentro de la celda para que los bordes no titilen demasiado al moverse
    vec3 c = (texture(DiffuseSampler, uvc + vec2(-q.x, -q.y)).rgb
            + texture(DiffuseSampler, uvc + vec2( q.x, -q.y)).rgb
            + texture(DiffuseSampler, uvc + vec2(-q.x,  q.y)).rgb
            + texture(DiffuseSampler, uvc + vec2( q.x,  q.y)).rgb) * 0.25;

    // niebla de distancia corta, del color del horizonte del juego (el cielo no se toca)
    if (FogRange.y > 0.0) {
        float d = texture(DepthSampler, uvc).r;
        if (d < 0.99999) {
            float z = d * 2.0 - 1.0;
            float dist = 2.0 * NEAR * FarPlane / (FarPlane + NEAR - z * (FarPlane - NEAR));
            float f = clamp((dist - FogRange.x) / max(FogRange.y - FogRange.x, 0.001), 0.0, 1.0);
            c = mix(c, FogCol, f);
        }
    }

    // colores un poco mas vivos y duros, como en una tele de tubo
    c = (c - 0.5) * 1.1 + 0.5;
    float g = dot(c, vec3(0.299, 0.587, 0.114));
    c = mix(vec3(g), c, 1.12);

    // color de 15 bits (5 por canal) con tramado ordenado
    float b = (bayer4(cell) - 0.5) * Dither;
    c = floor(clamp(c, 0.0, 1.0) * 31.0 + 0.5 + b) / 31.0;

    fragColor = vec4(mix(orig.rgb, c, Amount), 1.0);
}
