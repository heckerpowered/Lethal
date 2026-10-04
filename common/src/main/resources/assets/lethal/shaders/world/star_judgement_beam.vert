#version 450
layout (location = 0) in float vertexNumber;
layout (location = 0) out vec2 coordinates;
layout (push_constant) uniform Beam {
    mat4 clipFromLocal;
    vec4 cornersA;
    vec4 cornersB;
    vec4 heightAndV;
    vec4 color;
} beam;

vec2 corner(int index) {
    if (index == 0) return beam.cornersA.xy;
    if (index == 1) return beam.cornersA.zw;
    if (index == 2) return beam.cornersB.xy;
    return beam.cornersB.zw;
}

void main() {
    int side = int(vertexNumber) / 6;
    int triangleVertex = int(vertexNumber) - side * 6;
    int stripVertex = triangleVertex == 0 ? 0 : triangleVertex == 1 ? 1 : triangleVertex == 2 ? 2 : triangleVertex == 3 ? 2 : triangleVertex == 4 ? 1 : 3;
    int first = side == 0 ? 0 : side == 1 ? 3 : side == 2 ? 1 : 2;
    int second = side == 0 ? 1 : side == 1 ? 2 : side == 2 ? 3 : 0;
    bool usesFirst = stripVertex == 0 || stripVertex == 2;
    bool upper = stripVertex >= 2;
    vec2 xz = corner(usesFirst ? first : second);
    float y = upper ? beam.heightAndV.y : beam.heightAndV.x;
    gl_Position = beam.clipFromLocal * vec4(xz.x, y, xz.y, 1);
    coordinates = vec2(usesFirst ? 1 : 0, upper ? beam.heightAndV.w : beam.heightAndV.z);
}
