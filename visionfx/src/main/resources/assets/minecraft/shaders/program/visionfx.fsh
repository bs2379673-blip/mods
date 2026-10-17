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

    // contraste: lo claro se aclara y lo oscuro se oscurece, pero con pivote bajo y piso
    // para que escenas oscuras (hojas, niebla) no se vayan a negro
    float sum = col.r + col.g + col.b;
    float mul = clamp(1.0 + (sum - 0.6) * Contrast, 0.8, 1.8);
    col *= mul;

    // desaturacion hacia gris
    float gray = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(col, vec3(gray), Desaturate);

    col *= Brightness;
    col *= max(1.0 - d * Vignette, 0.0);

    fragColor = vec4(col, src.a); // se conserva el alfa original (otros pases, como la linterna, pueden leerlo)
}
