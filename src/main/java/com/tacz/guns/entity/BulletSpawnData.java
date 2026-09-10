package com.tacz.guns.entity;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/** TACZ's additional Forge spawn payload; keeps the original field order and double-precision velocity. */
public record BulletSpawnData(float pitch, float yaw, Vec3 velocity, int ownerId, Identifier ammoId,
                              float gravity, boolean explosion, boolean igniteEntity, boolean igniteBlock,
                              float explosionRadius, float explosionDamage, int life, float speed, float friction,
                              int pierce, boolean tracer, Identifier gunId, Identifier gunDisplayId) {
    public void write(FriendlyByteBuf buffer) {
        buffer.writeFloat(pitch);
        buffer.writeFloat(yaw);
        buffer.writeDouble(velocity.x);
        buffer.writeDouble(velocity.y);
        buffer.writeDouble(velocity.z);
        buffer.writeInt(ownerId);
        buffer.writeIdentifier(ammoId);
        buffer.writeFloat(gravity);
        buffer.writeBoolean(explosion);
        buffer.writeBoolean(igniteEntity);
        buffer.writeBoolean(igniteBlock);
        buffer.writeFloat(explosionRadius);
        buffer.writeFloat(explosionDamage);
        buffer.writeInt(life);
        buffer.writeFloat(speed);
        buffer.writeFloat(friction);
        buffer.writeInt(pierce);
        buffer.writeBoolean(tracer);
        buffer.writeIdentifier(gunId);
        buffer.writeIdentifier(gunDisplayId);
    }

    public static BulletSpawnData read(FriendlyByteBuf buffer) {
        return new BulletSpawnData(buffer.readFloat(), buffer.readFloat(),
                new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()), buffer.readInt(), buffer.readIdentifier(),
                buffer.readFloat(), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(),
                buffer.readFloat(), buffer.readFloat(), buffer.readInt(), buffer.readFloat(), buffer.readFloat(),
                buffer.readInt(), buffer.readBoolean(), buffer.readIdentifier(), buffer.readIdentifier());
    }
}
