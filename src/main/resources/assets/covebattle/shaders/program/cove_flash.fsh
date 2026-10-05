#version 150

uniform sampler2D DiffuseSampler;

in vec2 texCoord;
out vec4 fragColor;

// Whatever is still visible through the wash is blown out and bleached, so the
// moment the overlay thins you are looking at glare rather than a clean picture.
void main() {
    vec4 colour = texture(DiffuseSampler, texCoord);
    vec3 blown = clamp(colour.rgb * 2.4 + 0.35, 0.0, 1.0);
    float grey = dot(blown, vec3(0.299, 0.587, 0.114));
    fragColor = vec4(mix(blown, vec3(grey), 0.5), 1.0);
}
