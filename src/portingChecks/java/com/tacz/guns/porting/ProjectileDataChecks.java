package com.tacz.guns.porting;

import com.tacz.guns.entity.BulletSpawnData;
import com.tacz.guns.entity.TracerData;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.HexFormat;

public final class ProjectileDataChecks {
    private static int assertions;
    // Source-derived fixture from b43eb84c EntityKineticBullet.writeSpawnData: big-endian primitives
    // and VarInt-prefixed UTF-8 IDs. This is not a captured multiplayer packet.
    private static final String LEGACY_HEX = "41480000c2710000405ed00000000000bfc00000000000003f1a36e2eb1c432d123456780b6164646f6e3a726f756e643d000000010001406000004130000000000057414000003e00000000000003010b6164646f6e3a7269666c65096164646f6e3a616c74";
    private static void check(boolean condition, String description) {
        assertions++;
        if (!condition) throw new AssertionError(description);
    }

    public static void main(String[] args) {
        RegistryFixture.bootstrap();
        var expected = new BulletSpawnData(12.5F, -60.25F, new Vec3(123.25, -.125, .0001), 0x12345678,
                Identifier.parse("addon:round"), .03125F, true, false, true, 3.5F, 11F,
                87, 12F, .125F, 3, true, Identifier.parse("addon:rifle"), Identifier.parse("addon:alt"));
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            expected.write(buffer);
            check(ByteBufUtil.hexDump(buffer).equals(LEGACY_HEX), "additional spawn wire layout matches baseline source");
            check(BulletSpawnData.read(buffer).equals(expected), "spawn payload round trip retains every field and velocity precision");
            check(buffer.readableBytes() == 0, "decoder consumes exactly the payload");
        } finally { buffer.release(); }
        byte[] wire = HexFormat.of().parseHex(LEGACY_HEX);
        for (int length = 0; length < wire.length; length++) {
            var truncated = new FriendlyByteBuf(Unpooled.wrappedBuffer(Arrays.copyOf(wire, length)));
            boolean rejected = false;
            try { BulletSpawnData.read(truncated); }
            catch (RuntimeException expectedFailure) { rejected = true; }
            finally { truncated.release(); }
            if (!rejected) throw new AssertionError("Accepted truncated spawn at byte " + length);
        }
        check(true, "all 102 truncated payload lengths rejected");
        var data = new CompoundTag();
        check(TracerData.color(data).isEmpty() && TracerData.size(data) == 1, "missing tracer overrides retain defaults");
        data.putString(TracerData.COLOR_KEY, "invalid");
        check(TracerData.color(data).isEmpty(), "wrong-type color ignored");
        int[][] colors = { {}, {255}, {255, 0}, {255, 0, 255}, {255, 0, 255, 0}, {255, 0, 255, 0, 127} };
        float[][] values = { null, {1, 1, 1, 1}, {1, 1, 1, 0}, {1, 0, 1, 1}, {1, 0, 1, 0}, {1, 0, 1, 0} };
        for (int i = 0; i < colors.length; i++) {
            data.putIntArray(TracerData.COLOR_KEY, colors[i]);
            check(Arrays.equals(TracerData.color(data).orElse(null), values[i]), "legacy tracer array fallback length " + i);
        }
        data.putInt(TracerData.SIZE_KEY, 2);
        check(TracerData.size(data) == 2, "integer size override remains numeric");
        data.putFloat(TracerData.SIZE_KEY, .25F);
        check(TracerData.size(data) == .25F, "fractional size override retained");
        data.putString(TracerData.SIZE_KEY, "invalid");
        check(TracerData.size(data) == 1, "wrong-type size retains default");
        System.out.println("Projectile data checks passed: " + assertions + " assertions (no entity spawn, collision or rendering).");
    }
}
