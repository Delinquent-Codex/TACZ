package com.tacz.guns.client.gui.components;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** The source buttons select rectangles from TACZ's existing 256-pixel atlas. */
public final class AtlasButton extends Button {
    private final int u, v, hoverOffset;
    private final Identifier texture;

    public AtlasButton(int x, int y, int width, int height, int u, int v, int hoverOffset, Identifier texture, OnPress onPress) {
        super(x, y, width, height, Component.empty(), onPress, DEFAULT_NARRATION);
        this.u = u;
        this.v = v;
        this.hoverOffset = hoverOffset;
        this.texture = texture;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, getX(), getY(), u,
                v + (isHoveredOrFocused() ? hoverOffset : 0), width, height, 256, 256);
    }
}
