#version 120

attribute vec2 quadCoordinates;

varying vec2 textureCoordinates;

void main() {
    gl_Position = vec4(quadCoordinates.x * 2.0 - 1.0, quadCoordinates.y, 0.0, 1.0);
    textureCoordinates = vec2(quadCoordinates.x, quadCoordinates.y * 0.5 + 0.5);
}
