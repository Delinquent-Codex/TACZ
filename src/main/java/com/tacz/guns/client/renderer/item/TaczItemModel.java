package com.tacz.guns.client.renderer.item;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import com.tacz.guns.api.client.renderer.TaczClientItemExtensions;
import com.tacz.guns.api.client.renderer.TaczItemRenderer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/** Target item-model adapter retaining the renderer's stack and display context through submission. */
public final class TaczItemModel implements ItemModel {
    private static final Renderer SPECIAL_RENDERER = new Renderer();
    private final ModelRenderProperties properties;
    private final Matrix4fc transform;
    private final ItemModel missing;

    private TaczItemModel(ModelRenderProperties properties, Matrix4fc transform, ItemModel missing) {
        this.properties = properties;
        this.transform = new Matrix4f(transform);
        this.missing = missing;
    }

    @Override
    public void update(ItemStackRenderState output, ItemStack item, ItemModelResolver resolver,
                       ItemDisplayContext context, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
        TaczItemRenderer renderer = TaczClientItemExtensions.getRenderer(item);
        if (renderer == null) {
            missing.update(output, item, resolver, context, level, owner, seed);
            return;
        }
        Argument argument = new Argument(item.copy(), context, renderer);
        output.appendModelIdentityElement(this);
        output.appendModelIdentityElement(argument);
        var layer = output.newLayer();
        layer.setLocalTransform(transform);
        // The source builtin/entity item had a unit item footprint; display transforms apply below.
        layer.setExtents(TaczItemModel::unitExtents);
        layer.setupSpecialModel(SPECIAL_RENDERER, argument);
        properties.applyToLayer(layer, context);
    }

    private static Vector3fc[] unitExtents() {
        Vector3fc[] result = new Vector3fc[8];
        for (int i = 0; i < 8; i++) result[i] = new Vector3f(i & 1, (i >> 1) & 1, (i >> 2) & 1);
        return result;
    }

    public record Argument(ItemStack stack, ItemDisplayContext context, TaczItemRenderer renderer) {
        @Override
        public boolean equals(Object other) {
            return other instanceof Argument argument && context == argument.context && renderer == argument.renderer
                    && ItemStack.matches(stack, argument.stack);
        }

        @Override
        public int hashCode() {
            return ((ItemStack.hashItemAndComponents(stack) * 31 + stack.getCount()) * 31 + context.hashCode()) * 31
                    + System.identityHashCode(renderer);
        }
    }

    private static final class Renderer implements SpecialModelRenderer<Argument> {
        @Override
        public void submit(@Nullable Argument argument, PoseStack poseStack, SubmitNodeCollector collector,
                           int light, int overlay, boolean foil, int outlineColor) {
            if (argument != null) argument.renderer.submitByItem(argument.stack, argument.context, poseStack, collector, light, overlay);
        }

        @Override
        public void getExtents(Consumer<Vector3fc> output) {
            for (Vector3fc vertex : unitExtents()) output.accept(vertex);
        }

        @Override
        public @Nullable Argument extractArgument(ItemStack stack) {
            var renderer = TaczClientItemExtensions.getRenderer(stack);
            return renderer == null ? null : new Argument(stack.copy(), ItemDisplayContext.NONE, renderer);
        }
    }

    public record Unbaked(Identifier base) implements ItemModel.Unbaked {
        public static final MapCodec<Unbaked> CODEC = Identifier.CODEC.fieldOf("base").xmap(Unbaked::new, Unbaked::base);

        @Override
        public void resolveDependencies(ResolvableModel.Resolver resolver) { resolver.markDependency(base); }

        @Override
        public ItemModel bake(ItemModel.BakingContext context, Matrix4fc transform) {
            var baker = context.blockModelBaker();
            var model = baker.getModel(base);
            return new TaczItemModel(ModelRenderProperties.fromResolvedModel(baker, model, model.getTopTextureSlots()),
                    transform, context.missingItemModel(transform));
        }

        @Override
        public MapCodec<? extends ItemModel.Unbaked> type() { return CODEC; }
    }
}
