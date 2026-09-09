package com.tacz.guns.porting;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.init.ModPainting;
import com.tacz.guns.particles.BulletHoleOption;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.decoration.painting.PaintingVariant;

import java.nio.file.Files;
import java.nio.file.Path;

/** Actual target data codecs, isolated from FML loading and client rendering. */
public final class ContentChecks {
    private static int assertions;

    private static void check(boolean condition, String name) {
        assertions++;
        if (!condition) throw new AssertionError(name);
    }

    private static boolean same(BulletHoleOption first, BulletHoleOption second) {
        return first.getDirection() == second.getDirection() && first.getPos().equals(second.getPos())
                && first.getAmmoId().equals(second.getAmmoId()) && first.getGunId().equals(second.getGunId())
                && first.getGunDisplayId().equals(second.getGunDisplayId());
    }

    public static void main(String[] args) throws Exception {
        RegistryFixture.bootstrap();
        for (Direction direction : Direction.values()) {
            var option = new BulletHoleOption(direction, new BlockPos(-12345, -64, 30123456),
                    "addon:ammo/test", "addon:gun/test", "addon:display/test");
            var encoded = BulletHoleOption.CODEC.codec().encodeStart(JsonOps.INSTANCE, option).getOrThrow();
            check(same(option, BulletHoleOption.CODEC.codec().parse(JsonOps.INSTANCE, encoded).getOrThrow()),
                    "particle JSON round trip " + direction);
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                BulletHoleOption.STREAM_CODEC.encode(buffer, option);
                check(buffer.getByte(0) == direction.ordinal(), "original direction wire ordinal " + direction);
                check(same(option, BulletHoleOption.STREAM_CODEC.decode(buffer)) && !buffer.isReadable(),
                        "particle stream round trip " + direction);
            } finally {
                buffer.release();
            }
        }
        var oldJson = JsonParser.parseString("{\"dir\":2,\"pos\":0,\"ammo_id\":\"tacz:9mm\",\"gun_id\":\"tacz:glock_17\"}");
        check(BulletHoleOption.CODEC.codec().parse(JsonOps.INSTANCE, oldJson).getOrThrow()
                .getGunDisplayId().equals(DefaultAssets.DEFAULT_GUN_DISPLAY_ID.toString()), "absent display ID fallback");
        for (int invalid : new int[]{-1, 6, Integer.MAX_VALUE}) {
            oldJson.getAsJsonObject().addProperty("dir", invalid);
            check(BulletHoleOption.CODEC.codec().parse(JsonOps.INSTANCE, oldJson).error().isPresent(), "invalid JSON direction " + invalid);
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                buffer.writeVarInt(invalid);
                boolean rejected = false;
                try { BulletHoleOption.STREAM_CODEC.decode(buffer); }
                catch (io.netty.handler.codec.DecoderException expected) { rejected = true; }
                check(rejected, "invalid wire direction " + invalid);
            } finally {
                buffer.release();
            }
        }
        var truncated = new FriendlyByteBuf(Unpooled.buffer());
        try {
            truncated.writeVarInt(0);
            boolean rejected = false;
            try { BulletHoleOption.STREAM_CODEC.decode(truncated); }
            catch (IndexOutOfBoundsException expected) { rejected = true; }
            check(rejected, "truncated particle rejected");
        } finally {
            truncated.release();
        }
        var paintingJson = JsonParser.parseString(Files.readString(Path.of("src/main/resources/data/tacz/painting_variant/blood_strike_1.json")));
        var painting = PaintingVariant.DIRECT_CODEC.parse(JsonOps.INSTANCE, paintingJson).getOrThrow();
        check(painting.width() == 2 && painting.height() == 2, "32 pixel baseline painting remains 2 by 2 blocks");
        check(painting.assetId().equals(ModPainting.BLOOD_STRIKE_1.identifier()), "painting key and asset ID preserved");
        check(painting.title().isPresent() && painting.author().isPresent(), "painting title and author translations retained");
        check(Files.isRegularFile(Path.of("src/main/resources/assets/tacz/textures/painting/blood_strike_1.png")), "painting texture exists");
        var placeable = JsonParser.parseString(Files.readString(Path.of("src/main/resources/data/minecraft/tags/painting_variant/placeable.json")));
        check(placeable.getAsJsonObject().getAsJsonArray("values").asList().stream()
                .anyMatch(value -> value.getAsString().equals("tacz:blood_strike_1")), "painting remains placeable");
        System.out.println("Content checks passed: " + assertions + " assertions (no FML registration or rendering).");
    }
}
