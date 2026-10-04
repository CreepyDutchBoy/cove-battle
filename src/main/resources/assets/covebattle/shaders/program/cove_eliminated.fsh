#version 150

uniform sampler2D DiffuseSampler;

in vec2 texCoord;
out vec4 fragColor;

// Spectating after being knocked out: drain the colour and close the edges in,
// so being out of the round reads instantly without a HUD element saying so.
void main() {
    vec4 colour = texture(DiffuseSampler, texCoord);
    float grey = dot(colour.rgb, vec3(0.299, 0.587, 0.114));

    vec2 centred = texCoord - vec2(0.5);
    centred.x *= 1.15;
    float vignette = smoothstep(0.78, 0.22, length(centred));

    vec3 drained = mix(colour.rgb, vec3(grey), 0.82);
    drained *= mix(0.35, 1.0, vignette);
    drained += vec3(0.02, 0.0, 0.04) * (1.0 - vignette);

    fragColor = vec4(drained, 1.0);
}
