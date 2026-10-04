#version 150

uniform sampler2D DiffuseSampler;
uniform float Time;

in vec2 texCoord;
out vec4 fragColor;

// While the border is closing: a breathing red rim and a touch of extra contrast.
// Time is supplied by the post chain; if it ever stays at zero the effect simply
// renders at its resting intensity instead of breaking.
void main() {
    vec4 colour = texture(DiffuseSampler, texCoord);

    vec2 centred = texCoord - vec2(0.5);
    centred.x *= 1.15;
    float edge = smoothstep(0.30, 0.72, length(centred));

    float pulse = 0.72 + 0.28 * sin(Time * 6.2831 * 2.0);

    vec3 boosted = clamp((colour.rgb - 0.5) * 1.08 + 0.5, 0.0, 1.0);
    vec3 rim = vec3(0.62, 0.05, 0.08) * edge * pulse;

    fragColor = vec4(clamp(boosted + rim, 0.0, 1.0), 1.0);
}
