#version 150

uniform sampler2D DiffuseSampler;
uniform float Contrast;
uniform float Desaturate;
uniform float Brightness;
uniform float Chromatic;
uniform float Vignette;
uniform float Saturation;
uniform float Gamma;
uniform float ToneAmt;
uniform vec3 ShadowCol;
uniform vec3 HighCol;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 uv = texCoord;
    float d = length(uv - vec2(0.5));

    // aberracion cromatica: rojo y azul se separan hacia los bordes
    float off = Chromatic * d;
    vec4 src = texture(DiffuseSampler, uv);
    vec3 col;
    col.r = texture(DiffuseSampler, vec2(uv.x + off, uv.y)).r;
    col.g = texture(DiffuseSampler, uv).g;
    col.b = texture(DiffuseSampler, vec2(uv.x - off, uv.y)).b;

    // contraste: lo claro se aclara y lo oscuro se oscurece (mismo calculo que el mod de cordura)
    float mul = max(1.0 + ((col.r + col.g + col.b) - 1.0) * Contrast, 0.0);
    col *= mul;

    // desaturacion hacia gris
    float gray = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(col, vec3(gray), Desaturate);

    col = mix(vec3(gray), col, Saturation);

    // gamma y division de tonos (sombras / luces)
    col = pow(max(col, vec3(0.0)), vec3(1.0 / max(Gamma, 0.05)));
    float lum = clamp(dot(col, vec3(0.299, 0.587, 0.114)), 0.0, 1.0);
    col += ToneAmt * ((ShadowCol - 0.5) * (1.0 - lum) + (HighCol - 0.5) * lum) * 2.0;

    col *= Brightness;
    col *= max(1.0 - d * Vignette, 0.0);

    fragColor = vec4(col, 1.0);
}
