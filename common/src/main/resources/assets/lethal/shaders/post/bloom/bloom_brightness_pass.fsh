#version 120

uniform sampler2D framebuffer;
uniform float threshold;

varying vec2 textureCoordinates;

float calculateBrightness(vec3 color) {
    return dot(color, vec3(0.2126, 0.7152, 0.0722));
}

vec4 adjustToTargetBrightness(vec4 color, float targetBrightness) {
    float currentBrightness = dot(color.rgb, vec3(0.2126, 0.7152, 0.0722));
    float scale = targetBrightness / max(currentBrightness, 0.00001);
    return color * scale;
}

vec3 extractBloomSoft(vec3 color, float brightnessThreshold, float knee) {
    float brightness = dot(color, vec3(0.2126, 0.7152, 0.0722));
    float softness = clamp((brightness - brightnessThreshold) / knee, 0.0, 1.0);
    float delta = softness * softness * (3.0 - 2.0 * softness);
    float target = delta * (brightness - brightnessThreshold);
    float scale = target / max(brightness, 0.00001);
    return color * scale;
}

void main() {
    vec4 color = texture2D(framebuffer, textureCoordinates);
    float brightness = calculateBrightness(color.rgb);

    if (brightness < threshold) {
        gl_FragColor = vec4(0.0, 0.0, 0.0, 0.0);
    } else {
        gl_FragColor = vec4(extractBloomSoft(color.rgb, threshold, 1.0), color.a);
    }
}
