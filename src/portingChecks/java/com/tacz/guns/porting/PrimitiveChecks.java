package com.tacz.guns.porting;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.tacz.guns.api.item.nbt.ItemDataAccessor;
import com.tacz.guns.api.item.nbt.GunAttachmentData;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.init.ModDataComponents;
import com.tacz.guns.resource.serialize.IdentifierSerializer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.registries.RegistryManager;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Uses real 26.2 classes. Failures throw even when JVM assertions are disabled. */
public final class PrimitiveChecks {
    private static int assertions;

    private static void check(boolean condition, String name) {
        assertions++;
        if (!condition) throw new AssertionError(name);
    }

    public static void main(String[] args) {
        Gson gson = new GsonBuilder().registerTypeAdapter(Identifier.class, new IdentifierSerializer()).create();
        Identifier custom = Identifier.parse("my_pack:gun/variant");
        check(gson.fromJson(gson.toJson(custom), Identifier.class).equals(custom), "custom namespace round trip");
        check(gson.fromJson("\"stone\"", Identifier.class).toString().equals("minecraft:stone"), "default namespace");
        for (String invalid : new String[]{"\"Bad:ID\"", "\"pack:has space\"", "12", "{}", "[]"}) {
            boolean rejected = false;
            try { gson.fromJson(invalid, Identifier.class); }
            catch (JsonParseException expected) { rejected = true; }
            check(rejected, "invalid identifier rejected: " + invalid);
        }

        CompoundTag legacy = new CompoundTag();
        legacy.putString("GunId", "tacz:aa12");
        legacy.putInt("GunCurrentAmmoCount", 7);
        legacy.putBoolean("HasBulletInBarrel", true);
        CompoundTag extension = new CompoundTag();
        extension.putString("unknown_addon", "preserve me");
        legacy.put("extension", extension);
        CustomData data = CustomData.of(legacy);
        legacy.putInt("GunCurrentAmmoCount", 99);
        extension.putString("unknown_addon", "changed");
        check(data.copyTag().getIntOr("GunCurrentAmmoCount", 0) == 7, "source copy isolation");
        check(data.copyTag().getCompoundOrEmpty("extension").getStringOr("unknown_addon", "").equals("preserve me"), "nested copy isolation");
        CompoundTag read = data.copyTag();
        read.putInt("GunCurrentAmmoCount", 66);
        check(data.copyTag().getIntOr("GunCurrentAmmoCount", 0) == 7, "read snapshot isolation");
        CustomData edited = data.update(tag -> tag.putInt("GunCurrentAmmoCount", 6));
        check(!data.equals(edited), "component equality sees ammo edit");
        check(edited.copyTag().getCompoundOrEmpty("extension").equals(data.copyTag().getCompoundOrEmpty("extension")), "unknown extension preserved");
        check(ItemDataAccessor.contains(read, "GunId", Tag.TAG_STRING), "typed string presence");
        check(!ItemDataAccessor.contains(read, "GunId", Tag.TAG_INT), "typed mismatch rejected");
        check(ItemDataAccessor.contains(read, "GunCurrentAmmoCount", ItemDataAccessor.LEGACY_ANY_NUMERIC), "numeric wildcard presence");
        check(!ItemDataAccessor.contains(read, "absent", Tag.TAG_INT), "missing key rejected");
        check(data.copyTag().getBooleanOr("HasBulletInBarrel", false), "chamber flag preserved");
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // Real registry fixture, in a disposable test JVM; does not test FML loading.
        var componentRegistry = RegistryManager.ACTIVE.getRegistry(Registries.DATA_COMPONENT_TYPE);
        componentRegistry.unfreeze();
        componentRegistry.register(Identifier.parse("tacz:attachments"), ModDataComponents.ATTACHMENT_DATA);
        componentRegistry.freeze();
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(VanillaRegistries.createLookup()).forEach(pending -> pending.apply());
        ItemStack item = new ItemStack(Items.STICK);
        ItemDataAccessor.set(item, data.copyTag());
        ItemStack copy = item.copy();
        ItemDataAccessor.update(copy, tag -> tag.putInt("GunCurrentAmmoCount", 5));
        check(ItemDataAccessor.get(item).getIntOr("GunCurrentAmmoCount", 0) == 7, "item copy does not share mutable data");
        check(ItemDataAccessor.get(copy).getIntOr("GunCurrentAmmoCount", 0) == 5, "component mutation committed");
        check(!ItemStack.isSameItemSameComponents(item, copy), "stack equality detects different ammunition");
        CompoundTag oldScope = new CompoundTag();
        oldScope.putString("id", "minecraft:stick");
        oldScope.putByte("Count", (byte) 1);
        CompoundTag scopeFields = new CompoundTag();
        scopeFields.putString("AttachmentId", "tacz:scope_acog_ta31");
        scopeFields.putString("Skin", "my_pack:scope_skin");
        scopeFields.putInt("ZoomNumber", 2);
        scopeFields.putInt("LaserColor", 0x123456);
        scopeFields.put("extension", data.copyTag().getCompoundOrEmpty("extension"));
        oldScope.put("tag", scopeFields);
        ItemDataAccessor.update(item, tag -> tag.put("AttachmentSCOPE", oldScope.copy()));
        GunAttachmentData.migrateLegacy(item);
        ItemStack scope = GunAttachmentData.get(item, AttachmentType.SCOPE);
        check(ItemDataAccessor.get(scope).equals(scopeFields), "nested attachment fields preserved");
        check(ItemDataAccessor.get(item).getCompoundOrEmpty(GunAttachmentData.BACKUP_KEY).getCompoundOrEmpty("AttachmentSCOPE").equals(oldScope), "recoverable original saved");
        ItemStack migratedCopy = item.copy();
        GunAttachmentData.migrateLegacy(item);
        check(ItemStack.isSameItemSameComponents(item, migratedCopy), "migration idempotence");
        GunAttachmentData.updateTag(item, AttachmentType.SCOPE, tag -> tag.putInt("ZoomNumber", 3));
        check(ItemDataAccessor.get(GunAttachmentData.get(item, AttachmentType.SCOPE)).getIntOr("ZoomNumber", 0) == 3, "scope zoom write-back");
        check(ItemDataAccessor.get(GunAttachmentData.get(migratedCopy, AttachmentType.SCOPE)).getIntOr("ZoomNumber", 0) == 2, "nested stack copy isolation");
        var registryOps = VanillaRegistries.createLookup().createSerializationContext(NbtOps.INSTANCE);
        Tag encoded = ItemStack.CODEC.encodeStart(registryOps, item).getOrThrow();
        ItemStack decoded = ItemStack.CODEC.parse(registryOps, encoded).getOrThrow();
        check(ItemStack.isSameItemSameComponents(item, decoded), "item and nested attachment codec round trip");
        GunAttachmentData.set(item, AttachmentType.SCOPE, ItemStack.EMPTY);
        check(GunAttachmentData.get(item, AttachmentType.SCOPE).isEmpty(), "attachment removal");
        ItemStack invalidItem = new ItemStack(Items.STICK);
        CompoundTag invalidScope = oldScope.copy();
        invalidScope.getCompoundOrEmpty("tag").putString("display", "unsupported");
        ItemDataAccessor.update(invalidItem, tag -> tag.put("AttachmentSCOPE", invalidScope));
        ItemStack originalInvalid = invalidItem.copy();
        boolean refused = false;
        try { GunAttachmentData.migrateLegacy(invalidItem); }
        catch (IllegalArgumentException expected) { refused = true; }
        check(refused, "unsupported legacy vanilla metadata refused");
        check(ItemStack.isSameItemSameComponents(invalidItem, originalInvalid), "failed migration leaves original intact");
        System.out.println("PASS: " + assertions + " primitive assertions against Minecraft 26.2; no mod startup or gameplay claimed.");
    }
}
