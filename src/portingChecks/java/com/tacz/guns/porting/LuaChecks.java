package com.tacz.guns.porting;

import com.tacz.guns.api.item.nbt.ItemDataAccessor;
import com.tacz.guns.api.util.LuaNbtAccessor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;
import org.luaj.vm2.lib.jse.JsePlatform;

public final class LuaChecks {
    private static int assertions;

    private static void check(boolean condition, String name) {
        assertions++;
        if (!condition) throw new AssertionError(name);
    }

    public static void main(String[] args) {
        RegistryFixture.bootstrap();
        ItemStack stack = new ItemStack(Items.STICK);
        var accessor = LuaNbtAccessor.from(stack);
        check(accessor.getInt("absent") == 0 && accessor.getString("absent").isEmpty() && !accessor.getBoolean("absent"), "absent-field defaults");
        check(accessor.getCompound("absent") == null, "absent compound remains null");
        ItemStack before = stack.copy();
        var globals = JsePlatform.standardGlobals();
        globals.set("data", CoerceJavaToLua.coerce(accessor));
        globals.load("""
                data:putInt("GunCurrentAmmoCount", 9)
                data:putString("GunId", "addon:rifle")
                data:putBoolean("HasBulletInBarrel", true)
                data:putDouble("precision", 0.125)
                data:putFloat("recoil", 1.5)
                data:putLong("ticks", 123456789)
                assert(data:contains("GunCurrentAmmoCount", 99))
                local child = data:newCompoundTag()
                child:putInt("stage", 1)
                data:putCompound("script", child)
                data:getCompound("script"):putInt("stage", 2)
                assert(data:getBoolean("HasBulletInBarrel"))
                return data:getInt("GunCurrentAmmoCount")
                """, "component-nbt-fixture").call();
        var saved = ItemDataAccessor.get(stack);
        check(saved.getIntOr("GunCurrentAmmoCount", 0) == 9, "Lua integer writes reach item component");
        check(saved.getStringOr("GunId", "").equals("addon:rifle"), "Lua string write");
        check(saved.getBooleanOr("HasBulletInBarrel", false), "Lua boolean write");
        check(saved.getDoubleOr("precision", 0) == 0.125 && saved.getFloatOr("recoil", 0) == 1.5f, "Lua floating point writes");
        check(saved.getLongOr("ticks", 0) == 123456789L, "Lua long write");
        check(saved.getCompoundOrEmpty("script").getIntOr("stage", 0) == 2, "nested Lua write committed");
        check(!ItemStack.isSameItemSameComponents(before, stack) && ItemDataAccessor.get(before).isEmpty(), "component equality and copy isolation");
        ItemDataAccessor.update(stack, tag -> tag.putInt("external", 17));
        accessor.putInt("GunCurrentAmmoCount", 8);
        check(ItemDataAccessor.get(stack).getIntOr("external", 0) == 17, "external edits survive later Lua writes");
        accessor.nbt().putInt("external", 99);
        check(ItemDataAccessor.get(stack).getIntOr("external", 0) == 17, "internal snapshot cannot mutate item silently");
        var child = accessor.getCompound("script");
        ItemDataAccessor.update(stack, tag -> tag.remove("script"));
        child.putInt("stage", 3);
        check(!accessor.contains("script"), "stale child cannot resurrect removed data");
        CompoundTag detached = new CompoundTag();
        LuaNbtAccessor.from(detached).putString("standalone", "retained");
        check(detached.getStringOr("standalone", "").equals("retained"), "standalone NBT mutation retained");
        accessor.putLong("boundary", Long.MAX_VALUE);
        check(accessor.getLong("boundary") == Long.MAX_VALUE, "Java long precision retained");
        System.out.println("Lua checks passed: " + assertions + " assertions using LuaJ (no gun gameplay).");
    }
}
