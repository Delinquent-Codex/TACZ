package com.tacz.guns.client.renderer.scope;

import java.util.regex.Pattern;

/** Adds a mask test to the CURRENT resource-pack fragment source, retaining its alpha rejection. */
public final class ScopeShader {
    private ScopeShader() {}
    private static final Pattern MAIN = Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*(?:void\\s*)?\\)");
    private static final Pattern OUTPUT = Pattern.compile("\\bout\\s+vec4\\s+fragColor\\s*;");
    private static final Pattern COMMENT = Pattern.compile("/\\*.*?\\*/|//[^\\r\\n]*", Pattern.DOTALL);

    public static String wrap(String source) {
        if (source == null) throw new IllegalArgumentException("Missing scope fragment shader source");
        // Preserve offsets/newlines while ignoring commented-out declarations in pack shaders.
        String code = COMMENT.matcher(source).replaceAll(match -> match.group().replaceAll("[^\\r\\n]", " "));
        var main = MAIN.matcher(code);
        if (!main.find() || !OUTPUT.matcher(code).find()) {
            throw new IllegalArgumentException("Scope shader requires a main function and vec4 fragColor output");
        }
        int start = main.start(), end = main.end();
        if (main.find()) throw new IllegalArgumentException("Scope shader has multiple main functions");
        String renamed = source.substring(0, start) + "void tacz_scope_original_main()" + source.substring(end);
        return renamed + """

                // TACZ: sampled eight-bit mask; the original shader retains its version and imports.
                uniform sampler2D TaczScopeMask;
                layout(std140) uniform TaczScopeControl {
                    ivec4 TaczScopeState; // operation, comparison, reference, reserved
                };
                void main() {
                    int stored = int(round(texelFetch(TaczScopeMask, ivec2(gl_FragCoord.xy), 0).r * 255.0));
                    int comparison = TaczScopeState.y;
                    int reference = TaczScopeState.z;
                    if ((comparison == 1 && reference != stored) ||
                        (comparison == 2 && reference <= stored)) discard;
                    tacz_scope_original_main();
                    if (TaczScopeState.x == 1) fragColor = vec4(float(reference) / 255.0, 0.0, 0.0, 1.0);
                    if (TaczScopeState.x == 2) fragColor = vec4(float(255 - stored) / 255.0, 0.0, 0.0, 1.0);
                }
                """;
    }
}
