package com.tacz.guns.api.item.nbt;

import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.init.ModDataComponents;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.Set;
import java.util.function.Consumer;

/** Component storage plus a transactional converter for TACZ's legacy attachment slots. */
public final class GunAttachmentData {
    public static final String VERSION_KEY = "tacz:attachment_schema";
    public static final String BACKUP_KEY = "tacz:legacy_attachments_v0";
    private static final AttachmentType[] SLOTS = {AttachmentType.SCOPE, AttachmentType.MUZZLE,
            AttachmentType.STOCK, AttachmentType.GRIP, AttachmentType.LASER, AttachmentType.EXTENDED_MAG};
    private static final Set<String> VANILLA_LEGACY_FIELDS = Set.of("display", "Enchantments", "StoredEnchantments",
            "AttributeModifiers", "CanDestroy", "CanPlaceOn", "HideFlags", "Damage", "RepairCost", "Unbreakable");

    private GunAttachmentData() {
    }

    private static int slot(AttachmentType type) {
        // Persisted numbering is deliberate; never replace with enum.ordinal().
        return switch (type) {
            case SCOPE -> 0;
            case MUZZLE -> 1;
            case STOCK -> 2;
            case GRIP -> 3;
            case LASER -> 4;
            case EXTENDED_MAG -> 5;
            case NONE -> throw new IllegalArgumentException("NONE is not an attachment slot");
        };
    }

    public static ItemStack get(ItemStack gun, AttachmentType type) {
        if (type == AttachmentType.NONE || gun.isEmpty()) return ItemStack.EMPTY;
        migrateLegacy(gun);
        NonNullList<ItemStack> items = NonNullList.withSize(SLOTS.length, ItemStack.EMPTY);
        gun.getOrDefault(ModDataComponents.ATTACHMENT_DATA, ItemContainerContents.EMPTY).copyInto(items);
        return items.get(slot(type));
    }

    public static void set(ItemStack gun, AttachmentType type, ItemStack attachment) {
        if (gun.isEmpty()) throw new IllegalArgumentException("Cannot attach to an empty gun stack");
        int index = slot(type);
        migrateLegacy(gun);
        NonNullList<ItemStack> items = NonNullList.withSize(SLOTS.length, ItemStack.EMPTY);
        gun.getOrDefault(ModDataComponents.ATTACHMENT_DATA, ItemContainerContents.EMPTY).copyInto(items);
        items.set(index, attachment.copy());
        gun.set(ModDataComponents.ATTACHMENT_DATA, ItemContainerContents.fromItems(items));
    }

    public static void updateTag(ItemStack gun, AttachmentType type, Consumer<CompoundTag> edit) {
        ItemStack attachment = get(gun, type);
        if (attachment.isEmpty()) return;
        ItemDataAccessor.update(attachment, edit);
        set(gun, type, attachment);
    }

    /**
     * Converts only the TACZ-owned nested stack format. It is not a world upgrader.
     * The entire input is validated before modifying the item; unsupported vanilla
     * metadata and Forge capabilities are reported instead of discarded.
     */
    public static void migrateLegacy(ItemStack gun) {
        CompoundTag data = ItemDataAccessor.get(gun);
        if (data.contains(VERSION_KEY) && !ItemDataAccessor.contains(data, VERSION_KEY, Tag.TAG_INT)) {
            throw new IllegalArgumentException("TACZ attachment schema must be an integer");
        }
        int version = data.getIntOr(VERSION_KEY, 0);
        if (version == 1) return;
        if (version != 0) throw new IllegalArgumentException("Unsupported TACZ attachment schema " + version);
        NonNullList<ItemStack> items = NonNullList.withSize(SLOTS.length, ItemStack.EMPTY);
        CompoundTag backup = new CompoundTag();
        for (int i = 0; i < SLOTS.length; i++) {
            String key = "Attachment" + SLOTS[i].name();
            if (!data.contains(key)) continue;
            if (!ItemDataAccessor.contains(data, key, Tag.TAG_COMPOUND)) {
                throw new IllegalArgumentException("Legacy " + key + " is not a compound");
            }
            CompoundTag original = data.getCompoundOrEmpty(key);
            backup.put(key, original.copy());
            items.set(i, decodeLegacyAttachment(original));
        }
        if (backup.isEmpty()) return;
        if (gun.has(ModDataComponents.ATTACHMENT_DATA) || data.contains(BACKUP_KEY)) {
            throw new IllegalArgumentException("Conflicting legacy and component attachment data; original preserved");
        }
        ItemContainerContents converted = ItemContainerContents.fromItems(items);
        for (AttachmentType type : SLOTS) data.remove("Attachment" + type.name());
        data.put(BACKUP_KEY, backup);
        data.putInt(VERSION_KEY, 1);
        gun.set(ModDataComponents.ATTACHMENT_DATA, converted);
        ItemDataAccessor.set(gun, data);
    }

    public static ItemStack decodeLegacyAttachment(CompoundTag original) {
        if (original.isEmpty()) return ItemStack.EMPTY;
        Identifier id = Identifier.tryParse(original.getStringOr("id", ""));
        if (!ItemDataAccessor.contains(original, "Count", Tag.TAG_BYTE)) {
            throw new IllegalArgumentException("Legacy attachment Count must be a byte");
        }
        int count = original.getByteOr("Count", (byte) 0);
        if (id == null) throw new IllegalArgumentException("Invalid legacy attachment item ID");
        if (id.equals(Identifier.withDefaultNamespace("air")) && count == 0) return ItemStack.EMPTY;
        if (count < 1 || count > 99 || !BuiltInRegistries.ITEM.containsKey(id)) {
            throw new IllegalArgumentException("Unknown legacy attachment item or invalid count: " + id + " x " + count);
        }
        if (original.contains("ForgeCaps") || original.contains("ForgeData")) {
            throw new IllegalArgumentException("Legacy attachment Forge capabilities need a companion-specific converter: " + id);
        }
        if (original.contains("tag") && !ItemDataAccessor.contains(original, "tag", Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Legacy attachment tag must be a compound");
        }
        CompoundTag tag = original.getCompoundOrEmpty("tag");
        for (String key : VANILLA_LEGACY_FIELDS) {
            if (tag.contains(key)) throw new IllegalArgumentException("Legacy vanilla item field requires a separate converter: " + key);
        }
        ItemStack result = new ItemStack(BuiltInRegistries.ITEM.getValue(id), count);
        ItemDataAccessor.set(result, tag);
        return result;
    }
}
