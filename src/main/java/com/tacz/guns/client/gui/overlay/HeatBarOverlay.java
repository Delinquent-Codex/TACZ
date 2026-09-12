package com.tacz.guns.client.gui.overlay;

import org.joml.Matrix3x2fStack;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.index.ClientGunIndex;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.resource.pojo.data.gun.GunHeatData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.gui.overlay.ForgeLayer;

import java.text.DecimalFormat;

public class HeatBarOverlay implements ForgeLayer {
    private static final Identifier HEATBASE = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "textures/hud/heat_base.png");
    private static final DecimalFormat HEAT_FORMAT_PERCENT = new DecimalFormat("0.0%");
    private static float heatScale = 0.25f;

    @Override
    public void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        int width = graphics.guiWidth(), height = graphics.guiHeight();
        if (!RenderConfig.GUN_HUD_ENABLE.get()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (!(player instanceof IClientPlayerGunOperator)) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof IGun iGun)) {
            return;
        }
        Identifier gunId = iGun.getGunId(stack);
        GunData gunData = TimelessAPI.getClientGunIndex(gunId).map(ClientGunIndex::getGunData).orElse(null);
        GunDisplayInstance display = TimelessAPI.getGunDisplay(stack).orElse(null);
        if (gunData == null || display == null) {
            return;
        }

        Matrix3x2fStack poseStack = graphics.pose();
        if(gunData.getHeatData() != null && iGun.hasHeatData(stack)) {
            poseStack.pushMatrix();
            GunHeatData heatData = gunData.getHeatData();
            float percent = iGun.getHeatAmount(stack) / heatData.getHeatMax();

            float scaleValue = ((iGun.getHeatAmount(stack) / heatData.getHeatMax()) / 8f) + 0.75f;

            if(heatScale < scaleValue) heatScale += 0.05f;
            if(heatScale > scaleValue) heatScale -= 0.025f;
            if(heatScale > scaleValue - 0.03 && heatScale < scaleValue + 0.055) heatScale = scaleValue;
            poseStack.scale(heatScale, heatScale);

            boolean locked = iGun.isOverheatLocked(stack);
            int tickCount = mc.gui.hud.getGuiTicks();
            renderOverheat(percent, graphics, (int) (width / heatScale), (int) (height / heatScale), locked, tickCount);
            poseStack.popMatrix();
        }
    }

    public void renderOverheat(float heatPercentage, GuiGraphicsExtractor pGraphics, int w, int h,
                               boolean locked, int tickCount) {
        int barColor = getHeatColor(heatPercentage, locked, tickCount);
        pGraphics.fill(w / 2 - 30, h / 2 + 30, w / 2 - 30 + (int) (heatPercentage * 60), h / 2 + 34, barColor);
        int baseColor = locked ? (tickCount % 20 < 10 ? 0xFFFF1919 : 0xFFFFFF19) : -1;
        pGraphics.blit(RenderPipelines.GUI_TEXTURED, HEATBASE, w / 2 - 64, h / 2 - 44, 0, 0, 128, 128, 128, 128, baseColor);

        Font font = Minecraft.getInstance().fontFilterFishy;
        String percentString = locked ? "!OVERHEAT!" : HEAT_FORMAT_PERCENT.format(heatPercentage);
        int color = locked ? (tickCount % 20 < 10 ? 0xFFFF0000 : 0xFFFFFF00) : 0xFFFFFFFF;

        pGraphics.text(font, percentString, w / 2 - (font.width(percentString) / 2), h / 2 + 38, color, true);
    }

    public static int getHeatColor(float percent, boolean locked, int tickCount) {
        if (locked) {
            return tickCount % 20 < 10 ? 0x9FFF0000 : 0x9FFFFF00;
        }
        if (percent < 0.4) return 0x9FFFFFFF;
        int color;
        if (percent <= 0.65) {
            color = ARGB.srgbLerp(percent * 4 - 1.6f, 0x9FFFFFFF, 0x9FFFFF00);
        } else {
            color = ARGB.srgbLerp((percent-0.65f) / 0.35f, 0x9FFFFF00, 0x9FFF0000);
        }
        return color;
    }
}
