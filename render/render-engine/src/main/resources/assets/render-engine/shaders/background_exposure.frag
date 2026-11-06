#version 450

layout(set = 0, binding = 0) uniform sampler2D luminance;
layout(set = 0, binding = 1) uniform sampler2D history;
layout (push_constant) uniform Adaptation {
    vec4 limits;
    vec4 timing;
    vec4 histogramRange;
} adaptation;
layout(location = 0) out vec4 result;

void main() {
    float total = 0.0;
    for (int i = 0; i < 64; ++i)
    total += texture(luminance, vec2((float(i) + 0.5) / 64.0, 0.5)).r;

    float low = total * adaptation.histogramRange.z;
    float high = total * adaptation.histogramRange.w;
    float cumulative = 0.0;
    float sum = 0.0;
    float weight = 0.0;

    for (int i = 0; i < 64; ++i) {
        float count = texture(luminance, vec2((float(i) + 0.5) / 64.0, 0.5)).r;
        float retained = max(0.0, min(cumulative + count, high) - max(cumulative, low));
        sum += retained * mix(adaptation.histogramRange.x, adaptation.histogramRange.y, float(i) / 63.0);
        weight += retained;
        cumulative += count;
    }

    float average = weight > 0.0 ? exp2(sum / weight) : adaptation.limits.x;
    if (abs(adaptation.histogramRange.z - adaptation.histogramRange.w) < 0.0001) {
        average = 1.0;
    }

    float compensation = exp2(adaptation.limits.z);
    float targetWhite = clamp(average / 0.18, adaptation.limits.x, adaptation.limits.y);
    float white = targetWhite;

    if (adaptation.timing.w > 0.5 && adaptation.limits.x < adaptation.limits.y) {
        float oldScale = texture(history, vec2(0.5)).r;
        float current = log2(compensation / (oldScale != 0.0 ? oldScale : 1.0));
        float target = log2(targetWhite);
        float difference = target - current;
        float speed = difference > 0.0 ? adaptation.timing.y : adaptation.timing.z;
        float step = adaptation.timing.x * speed;
        float linear = current + sign(difference) * min(abs(difference), step);

        float startTime = adaptation.limits.w / max(speed, 0.001);
        float modifier = speed > 0.0 ? (1.0 / 60.0) / ((1.0 - exp2(-speed / 60.0)) * startTime) : 0.0;
        float exponential = mix(current, target, (1.0 - exp2(-adaptation.timing.x * speed)) * modifier);
        white = exp2(abs(difference) > adaptation.limits.w ? linear : exponential);
    }

    white = clamp(white, adaptation.limits.x, adaptation.limits.y);
    result = vec4(compensation / max(0.0001, white), compensation / max(0.0001, targetWhite), average, compensation);
}
