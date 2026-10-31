in vec2 uv;
out vec4 outColor;
uniform sampler2D scene;
uniform sampler2D widget;
uniform float opacity;

vec3 linearColor(vec3 c) {
    return mix(c / 12.92, pow((c + 0.055) / 1.055, vec3(2.4)), step(vec3(0.04045), c));
}

vec3 encodedColor(vec3 c) {
    c = max(c, vec3(0.0));
    return mix(c * 12.92, 1.055 * pow(c, vec3(1.0 / 2.4)) - 0.055, step(vec3(0.0031308), c));
}

void main() {
    vec4 layer = texture(widget, uv);
    vec3 color = linearColor(texture(scene, uv).rgb) * (1.0 - layer.a * opacity) + layer.rgb * opacity;
    outColor = vec4(clamp(encodedColor(color), 0.0, 1.0), 1.0);
}
