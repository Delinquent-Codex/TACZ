package com.tacz.guns.crafting.result;

import com.tacz.guns.resource.pojo.data.block.TabConfig;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import com.tacz.guns.resource.serialize.LegacyPackCodecs;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GunSmithTableResult {
    public static final String GUN = "gun";
    public static final String AMMO = "ammo";
    public static final String ATTACHMENT = "attachment";
    public static final String CUSTOM = "custom";

    private com.google.gson.JsonObject definition;

    public void setDefinition(com.google.gson.JsonObject definition) { this.definition = definition.deepCopy(); }

    public com.google.gson.JsonObject getDefinition() { return definition == null ? null : definition.deepCopy(); }

    private ItemStack result = ItemStack.EMPTY;
    private Identifier group = null;

    @Nullable
    private RawGunTableResult raw = null;
    private ItemStackTemplate template;

    public static GunSmithTableResult fromTemplate(ItemStackTemplate template, @Nullable Identifier group) {
        GunSmithTableResult result = new GunSmithTableResult(ItemStack.EMPTY, group);
        result.template = template;
        return result;
    }

    public GunSmithTableResult(ItemStack result, @Nullable Identifier group) {
        this.result = result;
        this.group = group==null ? TabConfig.TAB_EMPTY : group;
    }


    public GunSmithTableResult(@NotNull RawGunTableResult raw) {
        this.raw = raw;
    }

    public GunSmithTableResult(@NotNull RawGunTableResult raw, @Nullable Identifier group) {
        this.raw = raw;
        this.group = group==null ? TabConfig.TAB_EMPTY : group;
    }

    public void init() {
        if (template != null) {
            result = LegacyPackCodecs.createStack(template);
            template = null;
        }
        if (raw != null) {
            GunSmithTableResult result = RawGunTableResult.init(raw);
            this.result = result.getResult();
            if (group == null || group.equals(TabConfig.TAB_EMPTY)) {
                this.group = result.getGroup();
            }
            this.raw = null;
        }
    }

    public ItemStack getResult() {
        if (template != null) init();
        return result;
    }

    public Identifier getGroup() {
        return group;
    }
}
