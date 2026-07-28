#version 120

uniform sampler2D framebuffer;

varying vec2 textureCoordinates;

void main() {
    gl_FragColor = texture2D(framebuffer, textureCoordinates);
}
