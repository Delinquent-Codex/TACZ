package com.tacz.guns.client.renderer.scope;

/** The eight-bit, front/back-identical stencil operations used by the original scope renderer. */
public record ScopeMaskState(Operation operation, Comparison comparison, int reference, boolean depthTest) {
    public enum Operation { COLOR, REPLACE, INVERT }
    public enum Comparison { ALWAYS, EQUAL, GREATER }

    public static final ScopeMaskState UNMASKED = color(Comparison.ALWAYS, 0, true);

    public ScopeMaskState {
        java.util.Objects.requireNonNull(operation);
        java.util.Objects.requireNonNull(comparison);
        // glStencilFunc clamps its reference to the representable stencil range, before comparison.
        reference = Math.clamp(reference, 0, 255);
    }

    public static ScopeMaskState color(Comparison comparison, int reference, boolean depthTest) {
        return new ScopeMaskState(Operation.COLOR, comparison, reference, depthTest);
    }

    public static ScopeMaskState ocular(int reference) {
        return new ScopeMaskState(Operation.REPLACE, Comparison.GREATER, reference, true);
    }

    public static ScopeMaskState aperture(int reference) {
        return new ScopeMaskState(Operation.INVERT, Comparison.EQUAL, reference, true);
    }

    public boolean writesMask() { return operation != Operation.COLOR; }

    public boolean accepts(int stored) {
        int value = stored & 255;
        return switch (comparison) {
            case ALWAYS -> true;
            case EQUAL -> reference == value;
            case GREATER -> reference > value; // The reference is the LEFT operand in OpenGL.
        };
    }

    /** Reference implementation used by software mask consumers; depth/alpha rejection happens first. */
    public int apply(int stored, boolean fragmentSurvives) {
        int value = stored & 255;
        if (!fragmentSurvives || !accepts(value)) return value;
        return switch (operation) {
            case COLOR -> value;
            case REPLACE -> reference;
            case INVERT -> value ^ 255;
        };
    }
}
