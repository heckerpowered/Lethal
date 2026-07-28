#version 120

uniform sampler2D framebuffer;
uniform vec2 texelSize;

varying vec2 textureCoordinates;

void main() {
    vec4 color = vec4(0.0);
    color += texture2D(framebuffer, textureCoordinates + vec2(-texelSize.x, texelSize.y));
    color += texture2D(framebuffer, textureCoordinates + vec2(0.0, texelSize.y)) * 2.0;
    color += texture2D(framebuffer, textureCoordinates + texelSize);
    color += texture2D(framebuffer, textureCoordinates + vec2(-texelSize.x, 0.0)) * 2.0;
    color += texture2D(framebuffer, textureCoordinates) * 4.0;
    color += texture2D(framebuffer, textureCoordinates + vec2(texelSize.x, 0.0)) * 2.0;
    color += texture2D(framebuffer, textureCoordinates - texelSize);
    color += texture2D(framebuffer, textureCoordinates + vec2(0.0, -texelSize.y)) * 2.0;
    color += texture2D(framebuffer, textureCoordinates + vec2(texelSize.x, -texelSize.y));
    gl_FragColor = color / 16.0;
}
