package com.tacz.guns.client.renderer.scope;

import com.tacz.guns.client.renderer.VertexCapture;
import net.minecraft.util.Mth;

/** Original 90-segment aperture fan expanded to independent triangles for the native vertex API. */
public final class ScopeAperture {
    private ScopeAperture() {}

    public static VertexCapture.Snapshot capture(float ocularX, float ocularY, float radiusModifier, float aimingProgress) {
        float x = ocularX * 16 * 90;
        float y = ocularY * 16 * 90;
        float radius = 80 * radiusModifier * aimingProgress;
        VertexCapture capture = new VertexCapture();
        for (int segment = 0; segment < 90; segment++) {
            capture.addVertex(x, y, -90).setColor(-1);
            for (int point = segment; point <= segment + 1; point++) {
                float angle = (float) point * ((float) Math.PI * 2F) / 90.0F;
                capture.addVertex(x + Mth.cos(angle) * radius, y + Mth.sin(angle) * radius, -90).setColor(-1);
            }
        }
        return capture.drain();
    }

    public static int divisionReference(int index, boolean inverted) {
        // Source accepts index 127 (reference 128) and rejects index 128. Keep the edge, even
        // though the mixed gun body's GREATER 127 test is asymmetric at these two values.
        if (index < 0 || index > Byte.MAX_VALUE) throw new IllegalArgumentException("Index of oculus is out of range for 127");
        return inverted ? ~(index + 1) & 255 : index + 1;
    }
}
