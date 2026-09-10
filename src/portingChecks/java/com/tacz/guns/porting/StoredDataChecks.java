package com.tacz.guns.porting;

import com.tacz.guns.api.item.nbt.StoredItemData;
import com.tacz.guns.api.item.nbt.GunAttachmentData;
import com.tacz.guns.api.item.nbt.ItemDataAccessor;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.util.LegacyProfileData;
import com.tacz.guns.api.util.LegacyComponentData;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.RegistryFriendlyByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntityTypes;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

public final class StoredDataChecks {
    private static int assertions;
    private static void check(boolean condition, String name) {
        assertions++;
        if (!condition) throw new AssertionError(name);
    }

    private static CompoundTag legacy(String id, int count, CompoundTag data) {
        CompoundTag saved = new CompoundTag();
        saved.putString("id", id);
        saved.putByte("Count", (byte) count);
        if (data != null) saved.put("tag", data);
        return saved;
    }

    public static void main(String[] args) {
        RegistryFixture.bootstrap();
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        CompoundTag data = new CompoundTag();
        data.putString("GunId", "addon:rifle");
        data.putInt("GunCurrentAmmoCount", 7);
        data.putInt("HideFlags", 35);
        CompoundTag scopeData = new CompoundTag();
        scopeData.putString("AttachmentId", "addon:scope");
        scopeData.putInt("Extension", 23);
        data.put("AttachmentSCOPE", legacy("minecraft:stick", 1, scopeData));
        var original = legacy("minecraft:stick", 1, data);
        var copy = original.copy();
        ItemStack migrated = StoredItemData.decode(original, registries).getOrThrow();
        check(ItemDataAccessor.get(migrated).getIntOr("GunCurrentAmmoCount", 0) == 7, "saved ammo field preserved");
        check(com.tacz.guns.item.GunTooltipPart.getHideFlags(migrated) == 35, "TACZ tooltip bits survive vanilla HideFlags conversion");
        com.tacz.guns.item.GunTooltipPart.setHideFlags(migrated, 17);
        check(com.tacz.guns.item.GunTooltipPart.getHideFlags(migrated) == 17, "TACZ tooltip writes commit custom data");
        var scope = GunAttachmentData.get(migrated, AttachmentType.SCOPE);
        check(ItemDataAccessor.get(scope).getStringOr("AttachmentId", "").equals("addon:scope"), "nested attachment migrated");
        check(ItemDataAccessor.get(scope).getIntOr("Extension", 0) == 23, "nested extension preserved");
        check(original.equals(copy), "source saved stack untouched");
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        output.store("Item", ItemStack.OPTIONAL_CODEC, migrated);
        var input = TagValueInput.create(ProblemReporter.DISCARDING, registries, output.buildResult());
        var savedAgain = input.read("Item", CompoundTag.CODEC).orElseThrow();
        var loadedAgain = StoredItemData.decode(savedAgain, input.lookup()).getOrThrow();
        check(ItemStack.isSameItemSameComponents(migrated, loadedAgain), "target ValueInput/ValueOutput round trip");
        check(ItemDataAccessor.get(loadedAgain).contains(GunAttachmentData.BACKUP_KEY), "legacy attachment backup survives save");
        check(StoredItemData.decode(new CompoundTag(), registries).getOrThrow().isEmpty(), "target empty stack");
        check(StoredItemData.decode(legacy("minecraft:air", 0, null), registries).getOrThrow().isEmpty(), "legacy canonical empty stack");
        var invalid = legacy("minecraft:stick", 1, data.copy());
        invalid.put("ForgeCaps", new CompoundTag());
        var unchanged = invalid.copy();
        check(StoredItemData.decode(invalid, registries).error().isPresent() && invalid.equals(unchanged), "unsupported capability rejected without mutation");
        invalid = legacy("minecraft:stick", 1, data.copy());
        invalid.put("components", new CompoundTag());
        check(StoredItemData.decode(invalid, registries).error().isPresent(), "mixed old/new representation rejected");
        check(StoredItemData.decode(legacy("addon:missing", 1, null), registries).error().isPresent(), "unknown saved item rejected");
        check(StoredItemData.decode(legacy("minecraft:stick", 100, null), registries).error().isPresent(), "unrepresentable saved quantity rejected");

        var failedBlockData = new CompoundTag();
        failedBlockData.put("Item", unchanged.copy());
        var recovery = StoredItemData.recoveryBlockItem(new ItemStack(Items.CHEST), BlockEntityTypes.CHEST, failedBlockData);
        var recoverySaved = ItemStack.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), recovery).getOrThrow();
        var recovered = ItemStack.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), recoverySaved).getOrThrow();
        check(recovered.get(DataComponents.BLOCK_ENTITY_DATA).copyTagWithoutId().equals(failedBlockData), "unreadable original survives recovery item save");
        var wire = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        try {
            ItemStack.STREAM_CODEC.encode(wire, recovery);
            var networkRecovery = ItemStack.STREAM_CODEC.decode(wire);
            check(networkRecovery.get(DataComponents.BLOCK_ENTITY_DATA).copyTagWithoutId().equals(failedBlockData), "recovery item network round trip");
            check(networkRecovery.get(DataComponents.BLOCK_ENTITY_DATA).type() == BlockEntityTypes.CHEST, "recovery preserves placement entity type");
        } finally { wire.release(); }
        failedBlockData.remove("Item");
        check(recovery.get(DataComponents.BLOCK_ENTITY_DATA).contains("Item"), "recovery does not alias its source compound");
        var malformedValue = net.minecraft.nbt.StringTag.valueOf("unreadable-but-preserved");
        var rawOutput = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        rawOutput.store("Item", StoredItemData.RAW_TAG_CODEC, malformedValue);
        check(TagValueInput.create(ProblemReporter.DISCARDING, registries, rawOutput.buildResult())
                .read("Item", StoredItemData.RAW_TAG_CODEC).orElseThrow().equals(malformedValue), "malformed non-compound saved item can be preserved verbatim");

        var coloredData = new CompoundTag();
        var display = new CompoundTag();
        display.putInt("color", 0x12ab34);
        coloredData.put("display", display);
        var dyed = StoredItemData.decode(legacy("minecraft:leather_helmet", 1, coloredData), registries).getOrThrow();
        check(dyed.get(DataComponents.DYED_COLOR).rgb() == 0x12ab34, "legacy display color becomes dye component");

        var owner = new CompoundTag();
        owner.putString("Name", "TaczFixture");
        owner.putIntArray("Id", new int[]{1, 2, 3, 4});
        var texture = new CompoundTag();
        texture.putString("Value", "encoded-fixture");
        texture.putString("Signature", "signature-fixture");
        var textures = new ListTag();
        textures.add(texture);
        var properties = new CompoundTag();
        properties.put("textures", textures);
        owner.put("Properties", properties);
        var ownerCopy = owner.copy();
        var profile = LegacyProfileData.decode(owner).getOrThrow();
        check(profile.partialProfile().name().equals("TaczFixture"), "legacy profile name preserved");
        check(profile.partialProfile().id().equals(new java.util.UUID((1L << 32) | 2, (3L << 32) | 4)), "legacy profile UUID preserved");
        var serializedProfile = ResolvableProfile.CODEC.encodeStart(NbtOps.INSTANCE, profile).getOrThrow();
        check(serializedProfile.toString().contains("signature-fixture") && serializedProfile.toString().contains("encoded-fixture"), "profile texture and signature retained");
        check(owner.equals(ownerCopy), "legacy owner source untouched");
        check(LegacyProfileData.decode((CompoundTag) serializedProfile).getOrThrow().equals(profile), "target profile round trip");
        var nameOnly = new CompoundTag();
        nameOnly.putString("Name", "TaczFixture");
        check(LegacyProfileData.decode(nameOnly).getOrThrow() instanceof ResolvableProfile.Dynamic, "name-only owner remains resolvable");
        check(LegacyProfileData.decode(net.minecraft.nbt.StringTag.valueOf("TaczFixture")).getOrThrow() instanceof ResolvableProfile.Dynamic,
                "target shorthand owner is accepted");
        var patchedOwner = new CompoundTag();
        patchedOwner.putString("name", "TaczFixture");
        patchedOwner.putString("model", "slim");
        patchedOwner.putString("texture", "tacz:fixture/skin");
        var patchedProfile = LegacyProfileData.decode(patchedOwner).getOrThrow();
        var resolvedProfile = LegacyProfileData.withResolvedProfile(patchedProfile, profile.partialProfile());
        check(resolvedProfile.skinPatch().equals(patchedProfile.skinPatch()), "profile resolution retains explicit skin/model overrides");
        check(resolvedProfile.partialProfile().equals(profile.partialProfile()), "resolved texture properties and identity installed");
        var name = LegacyComponentData.decode("{\"text\":\"Target fixture\",\"color\":\"red\",\"italic\":true}", registries).getOrThrow();
        check(name.getString().equals("Target fixture") && name.getStyle().isItalic(), "legacy custom name and style retained");
        var nameOutput = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        nameOutput.store("CustomName", ComponentSerialization.CODEC, name);
        check(TagValueInput.create(ProblemReporter.DISCARDING, registries, nameOutput.buildResult())
                .read("CustomName", ComponentSerialization.CODEC).orElseThrow().equals(name), "target component name round trip");
        System.out.println("Stored data checks passed: " + assertions + " assertions (no live block entity).");
    }
}
