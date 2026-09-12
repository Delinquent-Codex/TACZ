package com.tacz.guns.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class GuiDraw {
    private GuiDraw() {}

    /** Keep the source's fractional text placement with the target's integer text API. */
    public static void text(GuiGraphicsExtractor graphics, Font font, String text, float x, float y, int color, boolean shadow) {
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(x, y);
            graphics.text(font, text, 0, 0, color, shadow);
        } finally {
            graphics.pose().popMatrix();
        }
    }
}
