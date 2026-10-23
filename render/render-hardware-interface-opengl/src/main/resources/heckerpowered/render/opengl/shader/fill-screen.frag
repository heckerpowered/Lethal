#version 450
layout(push_constant) uniform ScreenConstants {
    layout(offset = 0) vec4 color;
} constants;
layout(location = 0) out vec4 outputColor;
void main() {
    outputColor = constants.color;
}
