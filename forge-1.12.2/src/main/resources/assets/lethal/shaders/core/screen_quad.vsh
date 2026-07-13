#version 120

varying vec2 textureCoordinates;

void main() {
    gl_Position = gl_Vertex;
    textureCoordinates = gl_MultiTexCoord0.xy;
}
