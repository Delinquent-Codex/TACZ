package com.tacz.guns.compat;

/** Resolves an optional adapter only after its companion has been discovered by Forge. */
public final class OptionalIntegration {
    private OptionalIntegration() {}

    public static <T> T load(String modId, boolean installed, String implementation, Class<T> contract) {
        if (!installed) {
            return null;
        }
        try {
            return Class.forName(implementation, true, contract.getClassLoader())
                    .asSubclass(contract).getConstructor().newInstance();
        } catch (ReflectiveOperationException | LinkageError | ClassCastException exception) {
            // An installed companion without a working adapter must not silently lose its features.
            throw new IllegalStateException("TACZ cannot load its " + modId + " integration (" + implementation
                    + "). This Forge 26.2 port requires a compatible companion and a compiled target adapter. "
                    + "See docs/porting/dependencies.md; the retained optional adapter sources are not runtime support.", exception);
        }
    }
}
