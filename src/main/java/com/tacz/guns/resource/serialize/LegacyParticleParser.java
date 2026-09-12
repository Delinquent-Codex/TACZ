package com.tacz.guns.resource.serialize;

import com.google.gson.JsonObject;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.ParticleArgument;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.gameevent.BlockPositionSource;

/** Pack-only adapter for the positional particle arguments used by Minecraft 1.20.1. */
public final class LegacyParticleParser {
    private LegacyParticleParser() {}

    public static ParticleOptions parse(String value, HolderLookup.Provider registries) throws CommandSyntaxException {
        StringReader reader = new StringReader(value);
        Identifier id = Identifier.read(reader);
        ParticleOptions options;
        try {
            // Native SNBT payloads (including other mods' codecs) are decoded unchanged.
            if (!reader.canRead() || reader.peek() != ' ' || reader.getRemaining().isBlank()) {
                reader.setCursor(0);
                options = ParticleArgument.readParticle(reader, registries);
            } else {
                options = switch (id.toString()) {
                    case "minecraft:dust" -> new DustParticleOptions(readColor(reader), readFloat(reader));
                    case "minecraft:dust_color_transition" -> {
                        int from = readColor(reader);
                        float scale = readFloat(reader);
                        yield new DustColorTransitionOptions(from, readColor(reader), scale);
                    }
                    case "minecraft:block" -> block(reader, registries, ParticleTypes.BLOCK);
                    case "minecraft:block_marker" -> block(reader, registries, ParticleTypes.BLOCK_MARKER);
                    case "minecraft:falling_dust" -> block(reader, registries, ParticleTypes.FALLING_DUST);
                    case "minecraft:item" -> item(reader, registries);
                    case "minecraft:sculk_charge" -> new SculkChargeParticleOptions(readFloat(reader));
                    case "minecraft:shriek" -> new ShriekParticleOption(readInt(reader));
                    case "minecraft:vibration" -> {
                        // The source command parser narrows doubles to floats before flooring.
                        float x = readCoordinate(reader), y = readCoordinate(reader), z = readCoordinate(reader);
                        yield new VibrationParticleOption(new BlockPositionSource(BlockPos.containing(x, y, z)), readInt(reader));
                    }
                    case "tacz:bullet_hole" -> com.tacz.guns.particles.BulletHoleOption.fromCommand(reader);
                    default -> throw ParticleArgument.ERROR_INVALID_OPTIONS.createWithContext(reader,
                            "No legacy positional particle adapter for " + id + "; use the particle's 26.2 SNBT options");
                };
            }
        } catch (IllegalArgumentException exception) {
            throw ParticleArgument.ERROR_INVALID_OPTIONS.createWithContext(reader, exception.getMessage());
        }
        reader.skipWhitespace();
        if (reader.canRead()) {
            throw ParticleArgument.ERROR_INVALID_OPTIONS.createWithContext(reader, "Unexpected trailing particle data");
        }
        return options;
    }

    private static BlockParticleOption block(StringReader reader, HolderLookup.Provider registries,
                                              ParticleType<BlockParticleOption> type) throws CommandSyntaxException {
        reader.expect(' ');
        return new BlockParticleOption(type,
                BlockStateParser.parseForBlock(registries.lookupOrThrow(Registries.BLOCK), reader, false).blockState());
    }

    private static ItemParticleOption item(StringReader reader, HolderLookup.Provider registries) throws CommandSyntaxException {
        reader.expect(' ');
        Identifier itemId = Identifier.read(reader);
        JsonObject legacy = new JsonObject();
        legacy.addProperty("item", itemId.toString());
        if (reader.canRead() && reader.peek() == '{') {
            // Parse the complete NBT suffix, then let the existing item data fixer migrate it.
            legacy.addProperty("nbt", TagParser.parseCompoundFully(reader.getRemaining()).toString());
            reader.setCursor(reader.getTotalLength());
        }
        var stack = LegacyPackCodecs.ITEM_STACK.parse(registries.createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE), legacy)
                .getOrThrow(IllegalArgumentException::new);
        return new ItemParticleOption(ParticleTypes.ITEM, ItemStackTemplate.fromNonEmptyStack(stack));
    }

    private static int readColor(StringReader reader) throws CommandSyntaxException {
        float r = readFloat(reader), g = readFloat(reader), b = readFloat(reader);
        if (r < 0 || r > 1 || g < 0 || g > 1 || b < 0 || b > 1) {
            throw ParticleArgument.ERROR_INVALID_OPTIONS.createWithContext(reader, "Particle RGB channels must be in [0, 1]");
        }
        // Target dust stores 24-bit RGB. Keep the native truncation, rather than rounding.
        return ARGB.colorFromFloat(1, r, g, b) & 0xFFFFFF;
    }

    private static float readFloat(StringReader reader) throws CommandSyntaxException {
        reader.expect(' ');
        float value = reader.readFloat();
        if (!Float.isFinite(value)) throw ParticleArgument.ERROR_INVALID_OPTIONS.createWithContext(reader, "Non-finite particle value");
        return value;
    }

    private static float readCoordinate(StringReader reader) throws CommandSyntaxException {
        reader.expect(' ');
        float value = (float) reader.readDouble();
        if (!Float.isFinite(value)) throw ParticleArgument.ERROR_INVALID_OPTIONS.createWithContext(reader, "Non-finite particle coordinate");
        return value;
    }

    private static int readInt(StringReader reader) throws CommandSyntaxException {
        reader.expect(' ');
        return reader.readInt();
    }
}
