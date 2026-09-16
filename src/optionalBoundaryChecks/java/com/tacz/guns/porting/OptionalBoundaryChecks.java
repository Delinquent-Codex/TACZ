package com.tacz.guns.porting;

import com.tacz.guns.GunMod;
import com.tacz.guns.compat.OptionalIntegration;
import com.tacz.guns.compat.kubejs.KubeJSIntegration;
import com.tacz.guns.compat.playeranimator.PlayerAnimatorCompat;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Uses production main classes with main runtime dependencies, including no companion mods. */
public final class OptionalBoundaryChecks {
    private static int assertions;
    private static int initialized;

    public interface Contract { int value(); }
    public static final class Available implements Contract {
        public Available() { initialized++; }
        public int value() { return 26; }
    }
    public static final class Broken implements Contract {
        public Broken() { throw new IllegalArgumentException("fixture constructor failure"); }
        public int value() { throw new AssertionError("must not reach a broken provider"); }
    }
    public static final class MissingDependency implements Contract {
        public MissingDependency() { throw new NoClassDefFoundError("fixture missing dependency"); }
        public int value() { throw new AssertionError("must not reach an unlinked provider"); }
    }

    private static void check(boolean condition, String reason) {
        assertions++;
        if (!condition) throw new AssertionError(reason);
    }

    private static void absent(String type) throws Exception {
        try {
            Class.forName(type, false, OptionalBoundaryChecks.class.getClassLoader());
            throw new AssertionError("Unexpected optional class on main runtime: " + type);
        } catch (ClassNotFoundException expected) { assertions++; }
    }

    private static void rejected(String type, Class<? extends Throwable> rootCause) {
        try {
            OptionalIntegration.load("fixture", true, type, Contract.class);
            throw new AssertionError("Accepted incompatible adapter " + type);
        } catch (IllegalStateException expected) {
            Throwable cause = expected;
            while (cause.getCause() != null) cause = cause.getCause();
            check(rootCause.isInstance(cause), "Preserve the real linkage/constructor failure");
            check(expected.getMessage().contains("fixture") && expected.getMessage().contains("Forge 26.2"),
                    "Installed but unsupported integrations fail with an actionable diagnosis");
        }
    }

    private static boolean annotated(List<AnnotationNode> annotations, String descriptor) {
        return annotations != null && annotations.stream().anyMatch(a -> a.desc.equals(descriptor));
    }

    private static void checkDistributionMetadata() throws Exception {
        Path classes = Path.of(GunMod.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        List<String> stripping = new ArrayList<>();
        List<String> emptySubscribers = new ArrayList<>();
        int count = 0;
        try (var files = Files.walk(classes.resolve("com/tacz"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".class")).toList()) {
                ClassNode node = new ClassNode();
                new ClassReader(Files.readAllBytes(file)).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG);
                count++;
                String onlyIn = "Lnet/minecraftforge/api/distmarker/OnlyIn;";
                if (annotated(node.visibleAnnotations, onlyIn)
                        || node.methods.stream().anyMatch(m -> annotated(m.visibleAnnotations, onlyIn))
                        || node.fields.stream().anyMatch(f -> annotated(f.visibleAnnotations, onlyIn))) stripping.add(node.name);
                if (annotated(node.visibleAnnotations, "Lnet/minecraftforge/fml/common/Mod$EventBusSubscriber;")
                        && node.methods.stream().noneMatch(m -> annotated(m.visibleAnnotations,
                        "Lnet/minecraftforge/eventbus/api/listener/SubscribeEvent;"))) emptySubscribers.add(node.name);
            }
        }
        check(count > 0 && stripping.isEmpty(), "No unsupported Forge stripping metadata: " + stripping);
        check(emptySubscribers.isEmpty(), "No empty automatic event subscribers: " + emptySubscribers);
        System.out.println("Inspected distribution metadata of " + count + " actual production classes.");
    }

    public static void main(String[] args) throws Exception {
        checkDistributionMetadata();
        absent("dev.latvian.mods.kubejs.KubeJSPlugin");
        absent("dev.latvian.mods.rhino.Context");
        absent("dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess");
        absent("com.tacz.guns.compat.kubejs.TimelessKubeJSPlugin");
        absent("com.tacz.guns.compat.playeranimator.PlayerAnimatorAdapter");
        check(OptionalIntegration.load("kubejs", false, "com.tacz.guns.compat.kubejs.TimelessKubeJSPlugin",
                KubeJSIntegration.class) == null, "Absent KubeJS never resolves the missing adapter");
        check(OptionalIntegration.load("fixture", false, Available.class.getName(), Contract.class) == null
                && initialized == 0, "Absent companion never constructs a provider");
        Contract loaded = OptionalIntegration.load("fixture", true, Available.class.getName(), Contract.class);
        check(loaded.value() == 26 && initialized == 1, "Available provider loads and retains its behavior");
        rejected("missing.integration.Adapter", ClassNotFoundException.class);
        rejected(String.class.getName(), ClassCastException.class);
        rejected(Broken.class.getName(), IllegalArgumentException.class);
        rejected(MissingDependency.class.getName(), NoClassDefFoundError.class);
        check(GunMod.class.getDeclaredConstructors().length == 1, "Common entry point links without optional APIs");
        check(!PlayerAnimatorCompat.isInstalled(), "Uninstalled animator remains absent");
        check(!PlayerAnimatorCompat.hasPlayerAnimator3rd(null, null), "Absent animator does not claim custom animation");
        check(!PlayerAnimatorCompat.loadAnimationFromZip(null, "unused"), "Absent animator does not consume a pack entry");
        PlayerAnimatorCompat.loadAnimationFromFile(null);
        PlayerAnimatorCompat.clearAllAnimationCache();
        PlayerAnimatorCompat.stopAllAnimation(null);
        PlayerAnimatorCompat.stopAllAnimation(null, 8);
        PlayerAnimatorCompat.playAnimation(null, null, 0);
        PlayerAnimatorCompat.registerReloadListener(listener -> { throw new AssertionError("Absent adapter registered a listener"); });
        check(!PlayerAnimatorCompat.isInstalled(), "Absence calls leave native third-person fallback selected");
        System.out.println("PASS: " + assertions + " optional-boundary assertions; production main classes, no KubeJS/Player Animator runtime or FML launch claimed.");
    }
}
