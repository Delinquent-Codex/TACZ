package com.tacz.guns.entity.sync.core;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * Framework provided serializers used for creating a {@link SyncedDataKey}. This covers all
 * primitive types and common objects. You can create your custom serializer by implementing
 * {@link IDataSerializer}.
 * <p>
 * Author: MrCrayfish
 * Open source at <a href="https://github.com/MrCrayfish/Framework">Github</a> under LGPL License.
 */
public class Serializers {
    public static final IDataSerializer<Boolean> BOOLEAN = new IDataSerializer<>() {
        @Override
        public void write(RegistryFriendlyByteBuf buf, Boolean value) {
            buf.writeBoolean(value);
        }

        @Override
        public Boolean read(RegistryFriendlyByteBuf buf) {
            return buf.readBoolean();
        }

        @Override
        public Tag write(HolderLookup.Provider registries, Boolean value) {
            return ByteTag.valueOf(value);
        }

        @Override
        public Boolean read(HolderLookup.Provider registries, Tag tag) {
            return ((ByteTag) tag).byteValue() != 0;
        }
    };

    public static final IDataSerializer<Byte> BYTE = new IDataSerializer<>() {
        @Override
        public void write(RegistryFriendlyByteBuf buf, Byte value) {
            buf.writeByte(value);
        }

        @Override
        public Byte read(RegistryFriendlyByteBuf buf) {
            return buf.readByte();
        }

        @Override
        public Tag write(HolderLookup.Provider registries, Byte value) {
            return ByteTag.valueOf(value);
        }

        @Override
        public Byte read(HolderLookup.Provider registries, Tag tag) {
            return ((ByteTag) tag).byteValue();
        }
    };

    public static final IDataSerializer<Short> SHORT = new IDataSerializer<>() {
        @Override
        public void write(RegistryFriendlyByteBuf buf, Short value) {
            buf.writeShort(value);
        }

        @Override
        public Short read(RegistryFriendlyByteBuf buf) {
            return buf.readShort();
        }

        @Override
        public Tag write(HolderLookup.Provider registries, Short value) {
            return ShortTag.valueOf(value);
        }

        @Override
        public Short read(HolderLookup.Provider registries, Tag tag) {
            return ((ShortTag) tag).shortValue();
        }
    };

    public static final IDataSerializer<Integer> INTEGER = new IDataSerializer<>() {
        @Override
        public void write(RegistryFriendlyByteBuf buf, Integer value) {
            buf.writeVarInt(value);
        }

        @Override
        public Integer read(RegistryFriendlyByteBuf buf) {
            return buf.readVarInt();
        }

        @Override
        public Tag write(HolderLookup.Provider registries, Integer value) {
            return IntTag.valueOf(value);
        }

        @Override
        public Integer read(HolderLookup.Provider registries, Tag tag) {
            return ((IntTag) tag).intValue();
        }
    };

    public static final IDataSerializer<Long> LONG = new IDataSerializer<>() {
        @Override
        public void write(RegistryFriendlyByteBuf buf, Long value) {
            buf.writeLong(value);
        }

        @Override
        public Long read(RegistryFriendlyByteBuf buf) {
            return buf.readLong();
        }

        @Override
        public Tag write(HolderLookup.Provider registries, Long value) {
            return LongTag.valueOf(value);
        }

        @Override
        public Long read(HolderLookup.Provider registries, Tag tag) {
            return ((LongTag) tag).longValue();
        }
    };

    public static final IDataSerializer<Float> FLOAT = new IDataSerializer<>() {
        @Override
        public void write(RegistryFriendlyByteBuf buf, Float value) {
            buf.writeFloat(value);
        }

        @Override
        public Float read(RegistryFriendlyByteBuf buf) {
            return buf.readFloat();
        }

        @Override
        public Tag write(HolderLookup.Provider registries, Float value) {
            return FloatTag.valueOf(value);
        }

        @Override
        public Float read(HolderLookup.Provider registries, Tag tag) {
            return ((FloatTag) tag).floatValue();
        }
    };

    public static final IDataSerializer<Double> DOUBLE = new IDataSerializer<>() {
        @Override
        public void write(RegistryFriendlyByteBuf buf, Double value) {
            buf.writeDouble(value);
        }

        @Override
        public Double read(RegistryFriendlyByteBuf buf) {
            return buf.readDouble();
        }

        @Override
        public Tag write(HolderLookup.Provider registries, Double value) {
            return DoubleTag.valueOf(value);
        }

        @Override
        public Double read(HolderLookup.Provider registries, Tag tag) {
            return ((DoubleTag) tag).doubleValue();
        }
    };

    public static final IDataSerializer<Character> CHARACTER = new IDataSerializer<>() {
        @Override
        public void write(RegistryFriendlyByteBuf buf, Character value) {
            buf.writeChar(value);
        }

        @Override
        public Character read(RegistryFriendlyByteBuf buf) {
            return buf.readChar();
        }

        @Override
        public Tag write(HolderLookup.Provider registries, Character value) {
            return IntTag.valueOf(value);
        }

        @Override
        public Character read(HolderLookup.Provider registries, Tag tag) {
            return (char) ((IntTag) tag).intValue();
        }
    };

    public static final IDataSerializer<String> STRING = new IDataSerializer<>() {
        @Override
        public void write(RegistryFriendlyByteBuf buf, String value) {
            buf.writeUtf(value);
        }

        @Override
        public String read(RegistryFriendlyByteBuf buf) {
            return buf.readUtf();
        }

        @Override
        public Tag write(HolderLookup.Provider registries, String value) {
            return StringTag.valueOf(value);
        }

        @Override
        public String read(HolderLookup.Provider registries, Tag tag) {
            return ((StringTag) tag).value();
        }
    };

    public static final IDataSerializer<CompoundTag> TAG_COMPOUND = new IDataSerializer<>() {
        @Override
        public void write(RegistryFriendlyByteBuf buf, CompoundTag value) {
            buf.writeNbt(value);
        }

        @Override
        public CompoundTag read(RegistryFriendlyByteBuf buf) {
            return buf.readNbt();
        }

        @Override
        public Tag write(HolderLookup.Provider registries, CompoundTag value) {
            return value;
        }

        @Override
        public CompoundTag read(HolderLookup.Provider registries, Tag tag) {
            return (CompoundTag) tag;
        }
    };

    public static final IDataSerializer<BlockPos> BLOCK_POS = new IDataSerializer<>() {
        @Override
        public void write(RegistryFriendlyByteBuf buf, BlockPos value) {
            buf.writeBlockPos(value);
        }

        @Override
        public BlockPos read(RegistryFriendlyByteBuf buf) {
            return buf.readBlockPos();
        }

        @Override
        public Tag write(HolderLookup.Provider registries, BlockPos value) {
            return LongTag.valueOf(value.asLong());
        }

        @Override
        public BlockPos read(HolderLookup.Provider registries, Tag tag) {
            return BlockPos.of(((LongTag) tag).longValue());
        }
    };

    public static final IDataSerializer<UUID> UUID = new IDataSerializer<>() {
        @Override
        public void write(RegistryFriendlyByteBuf buf, UUID value) {
            buf.writeUUID(value);
        }

        @Override
        public UUID read(RegistryFriendlyByteBuf buf) {
            return buf.readUUID();
        }

        @Override
        public Tag write(HolderLookup.Provider registries, UUID value) {
            CompoundTag compound = new CompoundTag();
            compound.putLong("Most", value.getMostSignificantBits());
            compound.putLong("Least", value.getLeastSignificantBits());
            return compound;
        }

        @Override
        public UUID read(HolderLookup.Provider registries, Tag tag) {
            CompoundTag compound = (CompoundTag) tag;
            return new UUID(compound.getLongOr("Most", 0L), compound.getLongOr("Least", 0L));
        }
    };

    public static final IDataSerializer<ItemStack> ITEM_STACK = new IDataSerializer<>() {
        @Override
        public void write(RegistryFriendlyByteBuf buf, ItemStack value) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, value);
        }

        @Override
        public ItemStack read(RegistryFriendlyByteBuf buf) {
            return ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
        }

        @Override
        public Tag write(HolderLookup.Provider registries, ItemStack value) {
            return ItemStack.OPTIONAL_CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), value).getOrThrow();
        }

        @Override
        public ItemStack read(HolderLookup.Provider registries, Tag tag) {
            return ItemStack.OPTIONAL_CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag).getOrThrow();
        }
    };

    public static final IDataSerializer<Identifier> RESOURCE_LOCATION = new IDataSerializer<>() {
        @Override
        public void write(RegistryFriendlyByteBuf buf, Identifier value) {
            buf.writeIdentifier(value);
        }

        @Override
        public Identifier read(RegistryFriendlyByteBuf buf) {
            return buf.readIdentifier();
        }

        @Override
        public Tag write(HolderLookup.Provider registries, Identifier value) {
            return StringTag.valueOf(value.toString());
        }

        @Override
        public Identifier read(HolderLookup.Provider registries, Tag tag) {
            return Identifier.tryParse(((StringTag) tag).value());
        }
    };
}
