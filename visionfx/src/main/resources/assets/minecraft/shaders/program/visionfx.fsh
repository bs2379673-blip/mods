#version 150

uniform sampler2D DiffuseSampler;
uniform float Contrast;
uniform float Desaturate;
uniform float Brightness;
uniform float Chromatic;
uniform float Vignette;

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

    col *= Brightness;
    col *= max(1.0 - d * Vignette, 0.0);

    fragColor = vec4(col, src.a); // se conserva el alfa original (otros pases, como la linterna, pueden leerlo)
}
