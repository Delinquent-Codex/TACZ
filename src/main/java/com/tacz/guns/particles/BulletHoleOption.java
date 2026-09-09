package com.tacz.guns.particles;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.init.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraftforge.registries.ForgeRegistries;

public class BulletHoleOption implements ParticleOptions {
    public static final MapCodec<BulletHoleOption> CODEC = RecordCodecBuilder.mapCodec(builder ->
            builder.group(Codec.intRange(0, Direction.values().length - 1).fieldOf("dir").forGetter(option -> option.direction.ordinal()),
                    Codec.LONG.fieldOf("pos").forGetter(option -> option.pos.asLong()),
                    Codec.STRING.fieldOf("ammo_id").forGetter(option -> option.ammoId),
                    Codec.STRING.fieldOf("gun_id").forGetter(option -> option.gunId),
                    Codec.STRING.optionalFieldOf("gun_display_id", DefaultAssets.DEFAULT_GUN_DISPLAY_ID.toString()).forGetter(option -> option.gunDisplayId)
            ).apply(builder, BulletHoleOption::new));

    public static final StreamCodec<FriendlyByteBuf, BulletHoleOption> STREAM_CODEC = StreamCodec.ofMember(
            BulletHoleOption::writeToNetwork, BulletHoleOption::fromNetwork);

    /** Compatibility helper for TACZ's original positional command syntax. */
    public static BulletHoleOption fromCommand(StringReader reader) throws CommandSyntaxException {
        reader.expect(' ');
        int dir = reader.readInt();
        reader.expect(' ');
        long pos = reader.readLong();
        reader.expect(' ');
        String ammoId = reader.readString();
        reader.expect(' ');
        String gunId = reader.readString();
        reader.expect(' ');
        String gunDisplayId = reader.readString();
        return new BulletHoleOption(dir, pos, ammoId, gunId, gunDisplayId);
    }

    public static BulletHoleOption fromNetwork(FriendlyByteBuf buffer) {
        int direction = buffer.readVarInt();
        if (direction < 0 || direction >= Direction.values().length) {
            throw new io.netty.handler.codec.DecoderException("Invalid bullet-hole direction: " + direction);
        }
        return new BulletHoleOption(direction, buffer.readLong(), buffer.readUtf(), buffer.readUtf(), buffer.readUtf());
    }

    private final Direction direction;
    private final BlockPos pos;
    private final String ammoId;
    private final String gunId;
    private final String gunDisplayId;

    public BulletHoleOption(int dir, long pos, String ammoId, String gunId, String gunDisplayId) {
        if (dir < 0 || dir >= Direction.values().length) {
            throw new IllegalArgumentException("Invalid bullet-hole direction: " + dir);
        }
        this.direction = Direction.values()[dir];
        this.pos = BlockPos.of(pos);
        this.ammoId = ammoId;
        this.gunId = gunId;
        this.gunDisplayId = gunDisplayId;
    }

    public BulletHoleOption(Direction dir, BlockPos pos, String ammoId, String gunId, String gunDisplayId) {
        this.direction = dir;
        this.pos = pos;
        this.ammoId = ammoId;
        this.gunId = gunId;
        this.gunDisplayId = gunDisplayId;
    }

    public Direction getDirection() {
        return this.direction;
    }

    public BlockPos getPos() {
        return this.pos;
    }

    public String getAmmoId() {
        return ammoId;
    }

    public String getGunId() {
        return gunId;
    }

    public String getGunDisplayId() {
        return gunDisplayId;
    }

    @Override
    public ParticleType<?> getType() {
        return ModParticles.BULLET_HOLE.get();
    }

    public void writeToNetwork(FriendlyByteBuf buffer) {
        buffer.writeEnum(this.direction);
        buffer.writeBlockPos(this.pos);
        buffer.writeUtf(this.ammoId);
        buffer.writeUtf(this.gunId);
        buffer.writeUtf(this.gunDisplayId);
    }

    public String writeToString() {
        return ForgeRegistries.PARTICLE_TYPES.getKey(this.getType()) + " " + this.direction.getName();
    }
}
