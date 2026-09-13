package com.tacz.guns.client.renderer.scope;

import static com.tacz.guns.client.renderer.scope.ScopeMaskState.Comparison.EQUAL;

/** The baseline optic pass sequence, evaluated while model animation/functional renderers are live. */
public final class ScopeSequence {
    public enum Mode { SIGHT, SCOPE, MIXED }
    private ScopeSequence() {}

    public interface Parts {
        int ocularCount();
        int divisionCount();
        boolean scopeOcular(int index);
        void ring();
        void body();
        void ocular(int index);
        void division(int index);
        void aperture(int index);
        void remaining();
    }

    public static void render(Mode mode, Parts parts) {
        if (mode != Mode.SIGHT) ScopeCapture.withMask(ScopeMaskState.UNMASKED, parts::ring);
        ocularMask(parts, mode == Mode.MIXED);
        if (mode != Mode.SIGHT) ScopeCapture.withMask(ScopeMaskState.color(EQUAL, 0, true), parts::body);
        if (mode == Mode.MIXED) ocularMask(parts, false);
        if (mode == Mode.SIGHT) {
            for (int i = 0; i < parts.divisionCount(); i++) {
                int index = i;
                ScopeCapture.withMask(ScopeMaskState.color(EQUAL, i + 1, false), () -> parts.division(index));
            }
            ScopeCapture.withMask(ScopeMaskState.UNMASKED, parts::body);
        } else {
            for (int i = 0; i < parts.ocularCount(); i++) {
                int index = i;
                if (mode != Mode.MIXED || parts.scopeOcular(i)) {
                    ScopeCapture.withMask(ScopeMaskState.aperture(i + 1), () -> parts.aperture(index));
                }
            }
            for (int i = 0; i < Math.min(parts.ocularCount(), parts.divisionCount()); i++) {
                int index = i;
                int reference = ScopeAperture.divisionReference(i, false);
                if (mode == Mode.MIXED && !parts.scopeOcular(i)) {
                    ScopeCapture.withMask(ScopeMaskState.color(EQUAL, reference, true), () -> parts.division(index));
                } else {
                    ScopeCapture.withMask(ScopeMaskState.color(EQUAL, reference, true), () -> parts.ocular(index));
                    ScopeCapture.withMask(ScopeMaskState.color(EQUAL, ScopeAperture.divisionReference(i, true), true), () -> parts.division(index));
                }
            }
        }
        // The outer attachment render performs its own second super.render afterward, as before.
        ScopeCapture.withMask(ScopeMaskState.UNMASKED, parts::remaining);
    }

    private static void ocularMask(Parts parts, boolean scope) {
        for (int i = parts.ocularCount() - 1; i >= 0; i--) {
            int index = i;
            if (parts.scopeOcular(i) == scope) {
                ScopeCapture.withMask(ScopeMaskState.ocular(i + 1), () -> parts.ocular(index));
            }
        }
    }
}
