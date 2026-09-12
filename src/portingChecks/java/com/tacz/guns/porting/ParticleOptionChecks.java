package com.tacz.guns.porting;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.tacz.guns.particles.BulletHoleOption;
import com.tacz.guns.resource.serialize.LegacyParticleParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.BlockPositionSource;
import org.joml.Vector3f;

import java.nio.file.Files;
import java.nio.file.Path;

public final class ParticleOptionChecks {
    private static int assertions, shipped;
    private static RegistryAccess registries;

    private static void check(boolean condition, String description) {
        assertions++;
        if (!condition) throw new AssertionError(description);
    }

    private static ParticleOptions parse(String value) throws CommandSyntaxException {
        return LegacyParticleParser.parse(value, registries);
    }

    public static void main(String[] args) throws Exception {
        RegistryFixture.bootstrap();
        registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        check(parse("minecraft:smoke") == ParticleTypes.SMOKE && parse("flame ") == ParticleTypes.FLAME, "simple identifiers and trailing whitespace");
        DustParticleOptions dust = (DustParticleOptions) parse("dust 1 0.5 0 2");
        check(dust.getColor().equals(new Vector3f(1, 127 / 255F, 0)) && dust.getScale() == 2, "legacy dust maps to native 24-bit RGB and scale");
        check(((DustParticleOptions) parse("dust 1 0 0 -5")).getScale() == .01F, "legacy minimum dust scale clamp");
        check(((DustParticleOptions) parse("dust 1 0 0 99")).getScale() == 4, "legacy maximum dust scale clamp");
        DustColorTransitionOptions transition = (DustColorTransitionOptions) parse("dust_color_transition 1 0 0 2 0 0 1");
        check(transition.getScale() == 2 && transition.getFromColor().equals(new Vector3f(1, 0, 0))
                && transition.getToColor().equals(new Vector3f(0, 0, 1)), "legacy transition order is from RGB, scale, to RGB");
        for (String type : new String[]{"block", "block_marker", "falling_dust"}) {
            BlockParticleOption block = (BlockParticleOption) parse(type + " minecraft:oak_log[axis=x]");
            check(block.getState().is(Blocks.OAK_LOG) && block.getState().getValue(BlockStateProperties.AXIS) == net.minecraft.core.Direction.Axis.X,
                    "legacy block state properties: " + type);
            check(BuiltInRegistries.PARTICLE_TYPE.getKey(block.getType()).getPath().equals(type), "block particle variant retained: " + type);
        }
        ItemParticleOption item = (ItemParticleOption) parse("item minecraft:diamond_sword{Damage:7,display:{Name:'{\"text\":\"Particle blade\"}'},Extension:{value:42}}");
        var stack = item.getItem().create();
        check(stack.is(Items.DIAMOND_SWORD) && stack.getDamageValue() == 7, "legacy item particle damage becomes component");
        check(stack.get(DataComponents.CUSTOM_NAME).getString().equals("Particle blade"), "legacy item particle name component");
        check(stack.get(DataComponents.CUSTOM_DATA).copyTag().getCompoundOrEmpty("Extension").getIntOr("value", 0) == 42, "legacy item particle extension preserved");
        check(((ItemParticleOption) parse("item minecraft:stone")).getItem().create().is(Items.STONE), "bare legacy item");
        check(((SculkChargeParticleOptions) parse("sculk_charge -1.25")).roll() == -1.25F, "sculk roll retained");
        check(((ShriekParticleOption) parse("shriek 12")).getDelay() == 12, "shriek delay retained");
        VibrationParticleOption vibration = (VibrationParticleOption) parse("vibration -1.25 4.99 16777217 40");
        check(((BlockPositionSource) vibration.getDestination()).pos().equals(new BlockPos(-2, 4, 16777216))
                && vibration.getArrivalInTicks() == 40, "vibration destination retains baseline float narrowing and floor");
        BulletHoleOption hole = (BulletHoleOption) parse("tacz:bullet_hole 2 0 \"tacz:9mm\" \"tacz:glock_17\" \"tacz:default\"");
        check(hole.getDirection() == net.minecraft.core.Direction.NORTH && hole.getAmmoId().equals("tacz:9mm"), "TACZ positional bullet-hole helper remains reachable");
        check(((DustParticleOptions) parse("dust{color:[1.0,0.0,0.0],scale:1.0}")).getColor().equals(new Vector3f(1, 0, 0)), "native dust SNBT unchanged");
        check(((ItemParticleOption) parse("item{item:{id:'minecraft:diamond_sword',components:{'minecraft:damage':9}}}"))
                .getItem().create().getDamageValue() == 9, "native component item particle unchanged");
        check(((BlockParticleOption) parse("block{block_state:'minecraft:stone'}")).getState().is(Blocks.STONE), "native block particle unchanged");
        for (String malformed : new String[]{"missing:particle", "dust", "dust 1 0", "dust 2 0 0 1", "dust 0 0 0 1 trailing",
                "smoke trailing", "item missing:item", "item minecraft:stone{broken", "block oak_log[axis=invalid]", "block #minecraft:logs",
                "tacz:bullet_hole 6 0 a b c", "dust{color:[1.0,0.0,0.0],scale:99}"}) {
            boolean rejected = false;
            try { parse(malformed); } catch (CommandSyntaxException expected) { rejected = true; }
            check(rejected, "invalid particle reports a parse error: " + malformed);
        }
        Path root = Path.of("src/main/resources/assets/tacz/custom/tacz_default_gun/assets/tacz/display");
        try (var paths = Files.walk(root)) {
            for (Path file : paths.filter(path -> path.toString().endsWith(".json")).toList()) {
                try (var reader = Files.newBufferedReader(file)) { checkShipped(JsonParser.parseReader(reader), file); }
            }
        }
        check(shipped == 8, "all eight active shipped particle definitions covered (comments are not definitions)");
        System.out.println("Particle-option checks passed: " + assertions + " assertions (native options/codecs and " + shipped + " shipped definitions; no particle engine or GPU).");
    }

    private static void checkShipped(JsonElement value, Path file) throws CommandSyntaxException {
        if (value.isJsonObject()) {
            for (var entry : value.getAsJsonObject().entrySet()) {
                if (entry.getKey().equals("particle") && entry.getValue().isJsonObject()) {
                    String name = entry.getValue().getAsJsonObject().get("name").getAsString();
                    check(parse(name) != null, "shipped particle parses: " + file + " / " + name);
                    shipped++;
                } else checkShipped(entry.getValue(), file);
            }
        } else if (value.isJsonArray()) {
            for (var child : value.getAsJsonArray()) checkShipped(child, file);
        }
    }
}
