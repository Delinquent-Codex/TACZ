package com.tacz.guns.porting;

import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.nbt.GunAttachmentData;
import com.tacz.guns.api.item.nbt.ItemDataAccessor;
import com.tacz.guns.network.SyncedDataMapping;
import com.tacz.guns.entity.sync.core.IDataSerializer;
import com.tacz.guns.entity.sync.core.Serializers;
import com.tacz.guns.entity.sync.ModSerializers;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.core.BlockPos;
import java.util.UUID;
import com.tacz.guns.network.message.event.*;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

public final class NetworkChecks {
    private static int assertions;
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
    private static void rejects(Runnable action, String message) {
        boolean rejected = false;
        try { action.run(); }
        catch (IllegalArgumentException | DecoderException | IndexOutOfBoundsException expected) { rejected = true; }
        check(rejected, message);
    }
    private static SyncedDataMapping.Entry entry(String key, int id) {
        return new SyncedDataMapping.Entry(Identifier.parse("tacz:living_entity"), Identifier.parse("tacz:" + key), id);
    }
    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }
    private static <T> void scalar(IDataSerializer<T> serializer, T value) {
        var registries = VanillaRegistries.createLookup();
        check(value.equals(serializer.read(registries, serializer.write(registries, value))), "entity-data NBT scalar round trip: " + value);
        var wire = buffer();
        try {
            serializer.write(wire, value);
            check(value.equals(serializer.read(wire)), "entity-data wire scalar round trip: " + value);
        } finally { wire.release(); }
    }
    private static <T> void roundTrip(T packet, BiConsumer<T, RegistryFriendlyByteBuf> encode,
                                     Function<RegistryFriendlyByteBuf, T> decode) {
        var first = buffer();
        var second = buffer();
        try {
            encode.accept(packet, first);
            byte[] expected = new byte[first.readableBytes()];
            first.getBytes(0, expected);
            T decoded = decode.apply(first);
            check(first.readableBytes() == 0, "packet decoder consumes its complete payload: " + packet.getClass().getSimpleName());
            encode.accept(decoded, second);
            byte[] actual = new byte[second.readableBytes()];
            second.readBytes(actual);
            check(Arrays.equals(expected, actual), "packet round trip: " + packet.getClass().getSimpleName());
        } finally { first.release(); second.release(); }
    }

    public static void main(String[] args) {
        var mutableEntries = new ArrayList<>(List.of(entry("aiming", 7), entry("reloading", 2)));
        SyncedDataMapping mapping = new SyncedDataMapping(mutableEntries);
        mutableEntries.clear();
        check(mapping.entries().size() == 2, "mapping snapshot isolates source mutation");
        FriendlyByteBuf wire = new FriendlyByteBuf(Unpooled.buffer());
        try {
            mapping.encode(wire);
            check(mapping.equals(SyncedDataMapping.decode(wire)), "mapping round trip preserves nonsequential numbering");
            check(wire.readableBytes() == 0, "mapping decoder consumes payload");
        } finally { wire.release(); }
        rejects(() -> new SyncedDataMapping(List.of(entry("aiming", 7), entry("reloading", 7))), "duplicate numbers refused");
        rejects(() -> new SyncedDataMapping(List.of(entry("aiming", 7), entry("aiming", 8))), "duplicate names refused");
        rejects(() -> entry("aiming", -1), "negative key number refused");
        rejects(() -> entry("aiming", SyncedDataMapping.MAX_KEYS), "oversized key number refused");
        for (int count : new int[]{-1, SyncedDataMapping.MAX_KEYS + 1, Integer.MAX_VALUE}) {
            FriendlyByteBuf invalid = new FriendlyByteBuf(Unpooled.buffer());
            try {
                invalid.writeInt(count);
                rejects(() -> SyncedDataMapping.decode(invalid), "invalid wire count rejected before allocation: " + count);
            } finally { invalid.release(); }
        }
        FriendlyByteBuf truncated = new FriendlyByteBuf(Unpooled.buffer());
        try {
            truncated.writeInt(1);
            rejects(() -> SyncedDataMapping.decode(truncated), "truncated mapping refused");
        } finally { truncated.release(); }
        check(mapping.resolve(e -> e.keyId().getPath(), 2).equals(Map.of(7, "aiming", 2, "reloading")), "complete name resolution");
        rejects(() -> mapping.resolve(e -> null, 2), "unknown key prevents publication");
        rejects(() -> mapping.resolve(e -> e.keyId(), 3), "incomplete table refused");

        RegistryFixture.bootstrap();
        scalar(Serializers.BOOLEAN, true);
        scalar(Serializers.BYTE, (byte) -113);
        scalar(Serializers.SHORT, (short) -2047);
        scalar(Serializers.INTEGER, 456_789);
        scalar(Serializers.LONG, 100_000_000_000L);
        scalar(Serializers.FLOAT, 1.375f);
        scalar(Serializers.DOUBLE, -78.125d);
        scalar(Serializers.CHARACTER, '\u03bb');
        scalar(Serializers.STRING, "TACZ extension \u4e2d\u6587");
        scalar(Serializers.BLOCK_POS, new BlockPos(-500, 91, 704));
        scalar(Serializers.UUID, UUID.fromString("9716b6be-412d-4474-97fe-71fe14a8c5a1"));
        scalar(Serializers.RESOURCE_LOCATION, Identifier.parse("custom:gun/variant"));
        var badReload = buffer();
        try {
            badReload.writeInt(-1);
            rejects(() -> ModSerializers.RELOAD_STATE.read(badReload), "negative reload ordinal refused");
        } finally { badReload.release(); }
        ItemStack gun = new ItemStack(Items.STICK);
        ItemDataAccessor.update(gun, tag -> {
            tag.putString("GunId", "my_pack:variant");
            tag.putInt("GunCurrentAmmoCount", 9);
            tag.putString("addon_extension", "preserve");
        });
        ItemStack scope = new ItemStack(Items.STICK);
        ItemDataAccessor.update(scope, tag -> tag.putInt("ZoomNumber", 3));
        GunAttachmentData.set(gun, AttachmentType.SCOPE, scope);
        var registries = VanillaRegistries.createLookup();
        check(ItemStack.isSameItemSameComponents(gun, Serializers.ITEM_STACK.read(registries, Serializers.ITEM_STACK.write(registries, gun))),
                "entity capability item serializer retains registered nested components");
        check(Serializers.ITEM_STACK.read(registries, Serializers.ITEM_STACK.write(registries, ItemStack.EMPTY)).isEmpty(),
                "empty capability item codec round trip");
        var draw = new ServerMessageGunDraw(731, ItemStack.EMPTY, gun);
        var inspect = buffer();
        try {
            ServerMessageGunDraw.encode(draw, inspect);
            check(inspect.readVarInt() == 731, "entity identity on wire");
            check(ItemStack.OPTIONAL_STREAM_CODEC.decode(inspect).isEmpty(), "empty previous item on wire");
            ItemStack receivedGun = ItemStack.OPTIONAL_STREAM_CODEC.decode(inspect);
            check(ItemStack.isSameItemSameComponents(gun, receivedGun), "gun fields, unknown extensions and nested components survive packet");
            check(ItemDataAccessor.get(GunAttachmentData.get(receivedGun, AttachmentType.SCOPE)).getIntOr("ZoomNumber", 0) == 3,
                    "nested scope zoom survives network codec");
        } finally { inspect.release(); }
        roundTrip(draw, ServerMessageGunDraw::encode, ServerMessageGunDraw::decode);
        roundTrip(new ServerMessageGunShoot(731, gun), ServerMessageGunShoot::encode, ServerMessageGunShoot::decode);
        roundTrip(new ServerMessageGunFire(731, gun), ServerMessageGunFire::encode, ServerMessageGunFire::decode);
        roundTrip(new ServerMessageGunReload(731, gun), ServerMessageGunReload::encode, ServerMessageGunReload::decode);
        roundTrip(new ServerMessageGunFireSelect(731, gun), ServerMessageGunFireSelect::encode, ServerMessageGunFireSelect::decode);
        roundTrip(new ServerMessageGunMelee(731, gun), ServerMessageGunMelee::encode, ServerMessageGunMelee::decode);
        roundTrip(new ServerMessageGunHurt(91, 731, 28, Identifier.parse("tacz:ak47"), Identifier.parse("custom:skin"), 12.5f, true, 1.7f),
                ServerMessageGunHurt::encode, ServerMessageGunHurt::decode);
        roundTrip(new ServerMessageGunKill(91, 731, 28, Identifier.parse("tacz:ak47"), Identifier.parse("custom:skin"), 12.5f, true, 1.7f),
                ServerMessageGunKill::encode, ServerMessageGunKill::decode);
        System.out.println("PASS: " + assertions + " mapping and packet assertions; no live handshake, mod startup or multiplayer claimed.");
    }
}
