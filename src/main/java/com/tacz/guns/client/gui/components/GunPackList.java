package com.tacz.guns.client.gui.components;

import com.google.common.collect.ImmutableList;
import com.tacz.guns.client.gui.GunSmithTableScreen;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.pojo.PackInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.ARGB;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.util.*;

public class GunPackList extends ContainerObjectSelectionList<GunPackList.Entry> {
    private final GunSmithTableScreen parent;
    private final List<Checkbox> gunPackList = new ArrayList<>();
    private final Set<String> selectedNamespaces = new HashSet<>();
    private final Checkbox byHandCheckbox;
    private final EditBox byName;

    public GunPackList(Minecraft pMinecraft, int pWidth, int pHeight, int pY0, int pY1, int pItemHeight,
                       Map<Identifier, List<Identifier>> recipes, GunSmithTableScreen parent) {
        super(pMinecraft, pWidth, pY1 - pY0, pY0, pItemHeight);
        this.parent = parent;
        Set<String> namespaces = new HashSet<>();
        for (List<Identifier> entry : recipes.values()) {
            entry.forEach((resourceLocation) -> namespaces.add(resourceLocation.getNamespace()));
        }

        this.byName = new EditBox(pMinecraft.font, 3, 0, 94, 10, Component.empty());
        this.byName.setHint(Component.translatable("gui.tacz.gun_smith_table.filter.search"));
        this.byName.setResponder((pText) -> {
            parent.init();
            parent.setIndexPage(0);
        });
        this.addEntry(new GunPackList.Entry(byName));

        this.byHandCheckbox = new Checkbox(0, 0, 10, 10, Component.translatable("gui.tacz.gun_smith_table.filter.handgun"), false) {
            @Override
            public void onPress() {
                super.onPress();
                parent.init();
                parent.setIndexPage(0);
            }
        };
        this.addEntry(new GunPackList.Entry(byHandCheckbox));

        Checkbox checkbox1 = new Checkbox(0, 0, 10, 10, Component.translatable("gui.tacz.gun_smith_table.filter.all"), true) {
            @Override
            public void onPress() {
                super.onPress();
                gunPackList.forEach((checkbox) -> checkbox.selected = this.selected);
                updateSelectedNamespaces();
            }
        };
        this.addEntry(new GunPackList.Entry(checkbox1));

        for (String namespace : namespaces) {
            PackInfo packInfo = ClientAssetsManager.INSTANCE.getPackInfo(namespace);
            Component name = packInfo == null ? Component.literal(namespace) : Component.translatable(packInfo.getName());

            Checkbox checkbox = new Checkbox(0, 0, 10, 10, name, namespace, true) {
                @Override
                public void onPress() {
                    super.onPress();
                    checkbox1.selected = gunPackList.stream().allMatch(Checkbox::selected);
                    updateSelectedNamespaces();
                }
            };
            gunPackList.add(checkbox);
            selectedNamespaces.add(namespace);
            this.addEntry(new GunPackList.Entry(checkbox));
        }
    }

    public String getSearchText() {
        return byName.getValue();
    }

    public boolean isByHandSelected() {
        return byHandCheckbox.selected;
    }

    public void setByHandSelected(boolean selected) {
        byHandCheckbox.selected = selected;
    }

    public Set<String> namespaceList() {
        return selectedNamespaces;
    }

    public void updateSelectedNamespaces() {
        selectedNamespaces.clear();
        gunPackList.forEach((checkbox) -> {
            if (checkbox.selected) {
                selectedNamespaces.add(checkbox.getId());
            }
        });
        parent.init();
        parent.setIndexPage(0);
    }

    protected int scrollBarX() {
        return this.getRight() - 2;
    }

    @Override
    protected void extractListBackground(GuiGraphicsExtractor graphics) {
        graphics.fill(getX(), getY(), getRight(), getBottom(), 0x80000000);
    }

    @Override
    protected void extractListSeparators(GuiGraphicsExtractor graphics) {
        // Source setRenderTopAndBottom(false): no default vanilla separator textures.
    }

    @Override
    protected void extractScrollbar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int maxScroll = maxScrollAmount();
        if (maxScroll <= 0) return;
        int sourceContentHeight = contentHeight() - 4;
        int handleHeight = Mth.clamp(getHeight() * getHeight() / sourceContentHeight, 32, getHeight() - 8);
        int y = Math.max(getY(), (int) scrollAmount() * (getHeight() - handleHeight) / maxScroll + getY());
        int x = scrollBarX();
        graphics.fill(x, y, x + 6, y + handleHeight, -8355712);
        graphics.fill(x, y, x + 5, y + handleHeight - 1, -4144960);
    }

    public int getRowLeft() {
        return this.getX() + 4;
    }

    public int getRowWidth() {
        return this.width;
    }

    public static class Entry extends ContainerObjectSelectionList.Entry<GunPackList.Entry> {
        private final AbstractWidget widget;

        public Entry(AbstractWidget widget) {
            this.widget = widget;
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
             return ImmutableList.of(widget);
        }

        @Override
        public void extractContent(GuiGraphicsExtractor pGuiGraphics, int pMouseX, int pMouseY, boolean pHovering, float pPartialTick) {
            this.widget.setX(getX());
            this.widget.setY(getContentY());
            this.widget.extractRenderState(pGuiGraphics, pMouseX, pMouseY, pPartialTick);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return ImmutableList.of(widget);
        }
    }

    public static class Checkbox extends AbstractButton {
        private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("tacz", "textures/gui/checkbox_legacy.png");
        protected boolean selected;
        protected final boolean showLabel;
        private String id;

        public Checkbox(int pX, int pY, int pWidth, int pHeight, Component pMessage, String id, boolean pSelected) {
            this(pX, pY, pWidth, pHeight, pMessage, pSelected, true);
            this.id = id;
        }

        public Checkbox(int pX, int pY, int pWidth, int pHeight, Component pMessage, boolean pSelected) {
            this(pX, pY, pWidth, pHeight, pMessage, pSelected, true);
        }

        public Checkbox(int pX, int pY, int pWidth, int pHeight, Component pMessage, boolean pSelected, boolean pShowLabel) {
            super(pX, pY, pWidth, pHeight, pMessage);
            this.selected = pSelected;
            this.showLabel = pShowLabel;
        }

        public String getId() {
            return id;
        }

        @Override
        public void onPress(net.minecraft.client.input.InputWithModifiers input) { onPress(); }

        public void onPress() {
            this.selected = !this.selected;
        }

        public boolean selected() {
            return this.selected;
        }

        public void updateWidgetNarration(NarrationElementOutput pNarrationElementOutput) {
            pNarrationElementOutput.add(NarratedElementType.TITLE, this.createNarrationMessage());
            if (this.active) {
                if (this.isFocused()) {
                    pNarrationElementOutput.add(NarratedElementType.USAGE, Component.translatable("narration.checkbox.usage.focused"));
                } else {
                    pNarrationElementOutput.add(NarratedElementType.USAGE, Component.translatable("narration.checkbox.usage.hovered"));
                }
            }

        }

        public void extractContents(GuiGraphicsExtractor pGuiGraphics, int pMouseX, int pMouseY, float pPartialTick) {
            Minecraft minecraft = Minecraft.getInstance();
            Font font = minecraft.font;
            pGuiGraphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.getX(), this.getY(), this.isFocused() ? 10.0F : 0.0F, this.selected ? 10.0F : 0.0F, 10, 10, 32, 32, ARGB.white(this.alpha));
            if (this.showLabel) {
                pGuiGraphics.text(font, this.getMessage(), this.getX() + 24, this.getY() + (this.height - 8) / 2, 14737632 | Mth.ceil(this.alpha * 255.0F) << 24);
            }

        }
    }
}
