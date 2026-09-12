package com.tacz.guns.client.gui;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.gui.components.FlatColorButton;
import com.tacz.guns.client.gui.components.GunPackList;
import com.tacz.guns.client.gui.components.smith.ResultButton;
import com.tacz.guns.client.gui.components.smith.TypeButton;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.pojo.PackInfo;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.config.sync.SyncConfig;
import com.tacz.guns.crafting.GunSmithTableIngredient;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.init.ModRecipe;
import com.tacz.guns.inventory.GunSmithTableMenu;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.ClientMessageCraft;
import com.tacz.guns.resource.filter.RecipeFilter;
import com.tacz.guns.resource.pojo.data.block.TabConfig;
import com.tacz.guns.util.RenderDistance;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import net.minecraft.ChatFormatting;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import org.joml.Matrix3x2fStack;
import com.tacz.guns.client.gui.components.AtlasButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import com.tacz.guns.client.resource.ClientRecipeCache;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.*;

public class GunSmithTableScreen extends AbstractContainerScreen<GunSmithTableMenu> {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "textures/gui/gun_smith_table.png");
    private static final Identifier SIDE = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "textures/gui/gun_smith_table_side.png");

    private final LinkedHashMap<Identifier, TabConfig> recipeKeys = Maps.newLinkedHashMap();
    private final Map<Identifier, List<Identifier>> recipes = Maps.newLinkedHashMap();

    private int typePage;
    private Identifier selectedType = null;
    private List<Identifier> selectedRecipeList = new ArrayList<>();

    private int indexPage;
    private @Nullable GunSmithTableRecipe selectedRecipe;
    private @Nullable Int2IntArrayMap playerIngredientCount;

    private int scale = 70;
    private boolean filterEnabled = false;
    private GunPackList filterList;
    private boolean autoByHandFilterApplied = false;

    public GunSmithTableScreen(GunSmithTableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 344, 186);
        this.classifyRecipes();
        this.typePage = 0;
        this.indexPage = 0;
        this.selectedRecipe = this.getSelectedRecipe(selectedRecipeList != null && !this.selectedRecipeList.isEmpty() ? this.selectedRecipeList.get(0) : null);
        this.getPlayerIngredientCount(this.selectedRecipe);
    }

    public static void drawModCenteredString(GuiGraphicsExtractor gui, Font font, Component component, int pX, int pY, int color) {
        FormattedCharSequence text = component.getVisualOrderText();
        gui.text(font, text, pX - font.width(text) / 2, pY, color | 0xFF000000, false);
    }

    private void classifyRecipes() {
        this.recipes.clear();
        this.recipeKeys.clear();
        Identifier blockId = menu.getBlockId();
        if (blockId == null) {
            return;
        }
        Map<Identifier, List<Identifier>> recipes = Maps.newLinkedHashMap();
        Map<Identifier, TabConfig> recipeKeys = Maps.newLinkedHashMap();

        TimelessAPI.getCommonBlockIndex(blockId).ifPresent(blockIndex -> {
            var tabs = blockIndex.getData().getTabs();
            if (DefaultAssets.DEFAULT_BLOCK_ID.equals(blockId) && !SyncConfig.ENABLE_TABLE_FILTER.get()) {
                tabs = TabConfig.DEFAULT_TABS;
            }
            for (TabConfig tab : tabs) {
                recipes.put(tab.id(), Lists.newArrayList());
                recipeKeys.put(tab.id(), tab);
            }
        });

        List<Pair<Identifier, Identifier>> recipeIds = Lists.newArrayList();

        if (Minecraft.getInstance().level != null) {
            List<GunSmithTableRecipe> recipeList = ClientRecipeCache.all();
            Set<String> namespaces = filterList != null ? filterList.namespaceList() : null;
            for (GunSmithTableRecipe recipe : recipeList) {
                Identifier id = recipe.getId();
                if (namespaces != null && !namespaces.contains(id.getNamespace())) {
                    continue;
                }
                if (!isSuitableForMainHand(recipe)) {
                    continue;
                }
                if (!isNameMatch(recipe)) {
                    continue;
                }

                Identifier groupName = recipe.getResult().getGroup();
                if (recipeKeys.containsKey(groupName)) {
                    recipeIds.add(Pair.of(groupName, id));
                }
            }
        }

        TimelessAPI.getCommonBlockIndex(menu.getBlockId()).map(blockIndex -> {
            if (menu.getBlockId().equals(DefaultAssets.DEFAULT_BLOCK_ID) && !SyncConfig.ENABLE_TABLE_FILTER.get()) {
                return null;
            }
            RecipeFilter filter = blockIndex.getFilter();
            if (filter != null) {
                return filter.filter(recipeIds, Pair::value);
            }
            return null;
        }).orElse(recipeIds).forEach(entry -> {
            Identifier groupName = entry.key();
            if (recipeKeys.containsKey(groupName)) {
                recipes.computeIfAbsent(groupName, g -> Lists.newArrayList()).add(entry.value());
            }
        });

        for (var entry : recipes.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                this.recipes.put(entry.getKey(), entry.getValue());
                this.recipeKeys.put(entry.getKey(), recipeKeys.get(entry.getKey()));
            }
        }

        if (!this.recipeKeys.containsKey(selectedType)) {
            selectedType = null;
            selectedRecipeList = null;
            indexPage = 0;
        }

        if (!this.recipeKeys.keySet().isEmpty()) {
            if (selectedType == null) {
                selectedType = this.recipeKeys.keySet().iterator().next();
            }
        }

        if (selectedType != null) {
            selectedRecipeList = this.recipes.get(selectedType);
        }
    }

    private boolean isNameMatch(GunSmithTableRecipe recipe) {
        if (filterList != null && StringUtils.isNotBlank(filterList.getSearchText())) {
            String searchText = filterList.getSearchText().toLowerCase();
            Component name = recipe.getResult().getResult().getHoverName();
            return name.getString().toLowerCase().contains(searchText);
        }
        return true;
    }

    private boolean isSuitableForMainHand(GunSmithTableRecipe recipe) {
        if (filterList != null && filterList.isByHandSelected()) {
            ItemStack result = recipe.getResult().getResult();

            Minecraft minecraft = Minecraft.getInstance();
            ItemStack stack = minecraft.player != null ? minecraft.player.getMainHandItem() : ItemStack.EMPTY;
            if (stack.getItem() instanceof IGun igun) {
                if (result.getItem() instanceof IAmmo iAmmo) {
                    return iAmmo.isAmmoOfGun(stack, result);
                }
                if (result.getItem() instanceof IAttachment) {
                    return igun.allowAttachment(stack, result);
                }
                return false;
            }
            if (stack.getItem() instanceof IAttachment) {
                if (result.getItem() instanceof IGun iGun) {
                    return iGun.allowAttachment(result, stack);
                }
                return false;
            }
            if (stack.getItem() instanceof IAmmo iAmmo) {
                if (result.getItem() instanceof IGun) {
                    return iAmmo.isAmmoOfGun(result, stack);
                }
                return false;
            }
        }
        return true;
    }

    private boolean shouldFilterByMainHand() {
        Minecraft minecraft = Minecraft.getInstance();
        ItemStack stack = minecraft.player != null ? minecraft.player.getMainHandItem() : ItemStack.EMPTY;
        Item item = stack.getItem();
        return item instanceof IGun || item instanceof IAttachment || item instanceof IAmmo;
    }

    private void updateSelectedRecipeAfterFiltering() {
        if (selectedRecipeList == null || selectedRecipeList.isEmpty()) {
            this.selectedRecipe = null;
            this.playerIngredientCount = null;
            return;
        }
        boolean selectedRecipeExists = this.selectedRecipe != null && selectedRecipeList.contains(this.selectedRecipe.getId());
        if (!selectedRecipeExists) {
            this.selectedRecipe = this.getSelectedRecipe(selectedRecipeList.get(0));
        }
        this.getPlayerIngredientCount(this.selectedRecipe);
    }

    public void setIndexPage(int indexPage) {
        this.indexPage = indexPage;
    }

    @Nullable
    private GunSmithTableRecipe getSelectedRecipe(Identifier recipeId) {
        return Minecraft.getInstance().level == null ? null : ClientRecipeCache.get(recipeId);
    }

    private void getPlayerIngredientCount(GunSmithTableRecipe recipe) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || recipe == null) {
            return;
        }
        List<GunSmithTableIngredient> ingredients = recipe.getInputs();
        int size = ingredients.size();
        this.playerIngredientCount = new Int2IntArrayMap(size);
        for (int i = 0; i < size; i++) {
            GunSmithTableIngredient ingredient = ingredients.get(i);
            Inventory inventory = player.getInventory();
            int count = 0;
            for (ItemStack stack : inventory.getNonEquipmentItems()) {
                if (!stack.isEmpty() && ingredient.getIngredient().test(stack)) {
                    count = count + stack.getCount();
                }
            }
            playerIngredientCount.put(i, count);
        }
    }

    public void updateIngredientCount() {
        if (this.selectedRecipe != null) {
            this.getPlayerIngredientCount(selectedRecipe);
        }
        this.init();
    }

    @Override
    public void init() {
        super.init();
        if (this.filterList == null) {
            this.filterList = new GunPackList(this.minecraft, 134, this.imageHeight, topPos, topPos+imageHeight+1, 15, recipes, this);
        }
        if (!this.autoByHandFilterApplied && RenderConfig.AUTO_SELECT_GUN_SMITH_TABLE_FILTER.get()) {
            this.filterList.setByHandSelected(this.shouldFilterByMainHand());
            this.autoByHandFilterApplied = true;
        }
        this.filterList.updateSizeAndPosition(134, imageHeight + 1, leftPos, topPos);

        this.classifyRecipes();
        this.updateSelectedRecipeAfterFiltering();
        this.clearWidgets();

        this.addTypePageButtons();
        this.addTypeButtons();
        this.addIndexPageButtons();
        this.addIndexButtons();
        this.addRenderableWidget(new FlatColorButton(leftPos - 10, topPos, 9, 9, Component.literal("F"), b -> {
            this.filterEnabled = !this.filterEnabled;
            this.init();
        }).setTooltips("gui.tacz.gun_smith_table.filter"));
        if(this.filterEnabled) {
            this.addRenderableWidget(this.filterList);
        } else {
            this.addScaleButtons();
            this.addUrlButton();
        }
        this.addCraftButton();
    }

    private void addCraftButton() {
        this.addRenderableWidget(new AtlasButton(leftPos + 289, topPos + 162, 48, 18, 138, 164, 18, TEXTURE, b -> {
            if (this.selectedRecipe != null && playerIngredientCount != null) {
                // 检查是否能合成，不能就不发包
                List<GunSmithTableIngredient> inputs = selectedRecipe.getInputs();
                int size = inputs.size();
                for (int i = 0; i < size; i++) {
                    if (i >= playerIngredientCount.size()) {
                        return;
                    }
                    int hasCount = playerIngredientCount.get(i);
                    int needCount = inputs.get(i).getCount();
                    boolean isCreative = Minecraft.getInstance().player != null && Minecraft.getInstance().player.isCreative();
                    // 拥有数量小于需求数量，不发包
                    if (hasCount < needCount && !isCreative) {
                        return;
                    }
                }
                NetworkHandler.sendToServer(new ClientMessageCraft(this.selectedRecipe.getId(), this.menu.containerId));
            }
        }));
    }

    private void addUrlButton() {
        this.addRenderableWidget(new AtlasButton(leftPos + 112, topPos + 164, 18, 18, 149, 211, 18, TEXTURE, b -> {
            if (this.selectedRecipe != null) {
                ItemStack output = selectedRecipe.getOutput();
                Item item = output.getItem();
                Identifier id;
                if (item instanceof IGun iGun) {
                    id = iGun.getGunId(output);
                } else if (item instanceof IAttachment iAttachment) {
                    id = iAttachment.getAttachmentId(output);
                } else if (item instanceof IAmmo iAmmo) {
                    id = iAmmo.getAmmoId(output);
                } else {
                    return;
                }

                PackInfo packInfo = ClientAssetsManager.INSTANCE.getPackInfo(id);
                if (packInfo == null) {
                    return;
                }
                String url = packInfo.getUrl();
                if (StringUtils.isNotBlank(url) && minecraft != null) {
                    minecraft.gui.setScreen(new ConfirmLinkScreen(yes -> {
                        if (yes) {
                            Util.getPlatform().openUri(url);
                        }
                        minecraft.gui.setScreen(this);
                    }, url, false));
                }
            }
        }));
    }

    private void addIndexButtons() {
        if (selectedRecipeList == null || selectedRecipeList.isEmpty()) {
            return;
        }
        for (int i = 0; i < 6; i++) {
            int finalIndex = i + indexPage * 6;
            if (finalIndex >= selectedRecipeList.size()) {
                break;
            }
            int yOffset = topPos + 66 + 17 * i;
            Identifier recipeId = selectedRecipeList.get(finalIndex);
            GunSmithTableRecipe recipe = getSelectedRecipe(recipeId);
            if (recipe == null) {
                continue;
            }
            ResultButton button = addRenderableWidget(new ResultButton(leftPos + 144, yOffset, recipe.getOutput(), b -> {
                this.selectedRecipe = recipe;
                this.getPlayerIngredientCount(this.selectedRecipe);
                this.init();
            }));
            if (this.selectedRecipe != null && recipe.getId().equals(this.selectedRecipe.getId())) {
                button.setSelected(true);
            }
        }
    }

    private void addTypeButtons() {
        var list = Arrays.asList(recipeKeys.values().toArray(new TabConfig[0]));
        for (int i = 0; i < 7; i++) {
            int typeIndex = typePage * 7 + i;
            if (typeIndex >= recipes.size()) {
                return;
            }
            TabConfig tabConfig = list.get(typeIndex);
            Identifier type = tabConfig.id();
            int xOffset = leftPos + 157 + 24 * i;

            ItemStack icon = tabConfig.icon();

            TypeButton typeButton = new TypeButton(xOffset, topPos + 2, icon, b -> {
                this.selectedType = type;
                this.selectedRecipeList = recipes.get(type);
                this.indexPage = 0;
                this.selectedRecipe = getSelectedRecipe(this.selectedRecipeList.isEmpty() ? null : this.selectedRecipeList.get(0));
                this.getPlayerIngredientCount(this.selectedRecipe);
                this.init();
            });
            typeButton.setTooltip(Tooltip.create(tabConfig.getName(), tabConfig.getName()));
            if (this.selectedType.equals(type)) {
                typeButton.setSelected(true);
            }
            this.addRenderableWidget(typeButton);
        }
    }

    private void addIndexPageButtons() {
        this.addRenderableWidget(new AtlasButton(leftPos + 143, topPos + 56, 96, 6, 40, 166, 6, TEXTURE, b -> {
            if (this.indexPage > 0) {
                this.indexPage--;
                this.init();
            }
        }));
        this.addRenderableWidget(new AtlasButton(leftPos + 143, topPos + 171, 96, 6, 40, 186, 6, TEXTURE, b -> {
            if (selectedRecipeList != null && !selectedRecipeList.isEmpty()) {
                int maxIndexPage = (selectedRecipeList.size() - 1) / 6;
                if (this.indexPage < maxIndexPage) {
                    this.indexPage++;
                    this.init();
                }
            }
        }));
    }

    private void addTypePageButtons() {
        this.addRenderableWidget(new AtlasButton(leftPos + 136, topPos + 4, 18, 20, 0, 162, 20, TEXTURE, b -> {
            if (this.typePage > 0) {
                this.typePage--;
                this.init();
            }
        }));
        this.addRenderableWidget(new AtlasButton(leftPos + 327, topPos + 4, 18, 20, 20, 162, 20, TEXTURE, b -> {
            int maxIndexPage = (recipes.size() - 1) / 7;
            if (this.typePage < maxIndexPage) {
                this.typePage++;
                this.init();
            }
        }));
    }

    private void addScaleButtons() {
        this.addRenderableWidget(new AtlasButton(leftPos + 5, topPos + 5, 10, 10, 188, 173, 10, TEXTURE, b -> {
            this.scale = Math.min(this.scale + 20, 200);
        }));
        this.addRenderableWidget(new AtlasButton(leftPos + 17, topPos + 5, 10, 10, 200, 173, 10, TEXTURE, b -> {
            this.scale = Math.max(this.scale - 20, 10);
        }));
        this.addRenderableWidget(new AtlasButton(leftPos + 29, topPos + 5, 10, 10, 212, 173, 10, TEXTURE, b -> {
            this.scale = 70;
        }));
    }

    @Override
    public boolean mouseScrolled(double pMouseX, double pMouseY, double scrollX, double pDelta) {
        if (pMouseX > leftPos + 143 && pMouseX < leftPos + 143 + 94 && pMouseY > topPos + 66 && pMouseY < topPos + 66 + 85) {
            if (pDelta > 0) {
                this.indexPage = Math.max(0, this.indexPage - 1);
            } else {
                int maxIndexPage = (selectedRecipeList.size() - 1) / 6;
                this.indexPage = Math.min(maxIndexPage, this.indexPage + 1);
            }
            this.init();
            return true;
        }
        return super.mouseScrolled(pMouseX, pMouseY, scrollX, pDelta);
    }

    @Override
    public void extractRenderState(@NotNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        drawModCenteredString(graphics, font, Component.translatable("gui.tacz.gun_smith_table.preview"), leftPos + 108, topPos + 5, 0xFF555555);
        if (selectedType != null) {
            var config = recipeKeys.get(selectedType);
            if (config != null) {
                graphics.text(font, config.getName(), leftPos + 150, topPos + 32, 0xFF555555, false);
            }
        }
        graphics.text(font, Component.translatable("gui.tacz.gun_smith_table.ingredient"), leftPos + 254, topPos + 50, 0xFF555555, false);
        drawModCenteredString(graphics, font, Component.translatable("gui.tacz.gun_smith_table.craft"), leftPos + 312, topPos + 167, 0xFFFFFFFF);
        if (!this.filterEnabled && this.selectedRecipe != null) {
            this.renderLeftModel(graphics, this.selectedRecipe);
            this.renderPackInfo(graphics, this.selectedRecipe);
            graphics.text(font, Component.translatable("gui.tacz.gun_smith_table.count", this.selectedRecipe.getResult().getResult().getCount()), leftPos + 254, topPos + 140, 0xFF555555, false);
        }
        if (selectedRecipeList != null && !selectedRecipeList.isEmpty()) {
            renderIngredient(graphics);
        }

        this.renderables.stream().filter(w -> w instanceof ResultButton)
                .forEach(w -> ((ResultButton) w).renderTooltips(stack -> graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY)));
    }

    private void renderPackInfo(GuiGraphicsExtractor gui, GunSmithTableRecipe recipe) {
        ItemStack output = recipe.getOutput();
        Item item = output.getItem();
        Identifier id;
        if (item instanceof IGun iGun) {
            id = iGun.getGunId(output);
        } else if (item instanceof IAttachment iAttachment) {
            id = iAttachment.getAttachmentId(output);
        } else if (item instanceof IAmmo iAmmo) {
            id = iAmmo.getAmmoId(output);
        } else {
            return;
        }

        PackInfo packInfo = ClientAssetsManager.INSTANCE.getPackInfo(id);
        Matrix3x2fStack poseStack = gui.pose();
        if (packInfo != null) {
            poseStack.pushMatrix();
            poseStack.scale(0.75f, 0.75f);
            Component nameText = Component.translatable(packInfo.getName());
            gui.text(font, nameText, (int) ((leftPos + 6) / 0.75f), (int) ((topPos + 122) / 0.75f), 0xFF555555, false);
            poseStack.popMatrix();

            poseStack.pushMatrix();
            poseStack.scale(0.5f, 0.5f);

            int offsetX = (leftPos + 6) * 2;
            int offsetY = (topPos + 123) * 2;
            int nameWidth = font.width(nameText);
            Component ver = Component.literal("v" + packInfo.getVersion()).withStyle(ChatFormatting.UNDERLINE);
            gui.text(font, ver, (int) (offsetX + nameWidth * 0.75f / 0.5f + 5), offsetY, 0xFF555555, false);
            offsetY += 14;

            String descKey = packInfo.getDescription();
            if (StringUtils.isNoneBlank(descKey)) {
                Component desc = Component.translatable(descKey);
                List<FormattedCharSequence> split = font.split(desc, 245);
                for (FormattedCharSequence charSequence : split) {
                    gui.text(font, charSequence, offsetX, offsetY, 0xFF555555, false);
                    offsetY += font.lineHeight;
                }
                offsetY += 3;
            }

            gui.text(font, Component.translatable("gui.tacz.gun_smith_table.license")
                            .append(Component.literal(packInfo.getLicense()).withStyle(ChatFormatting.DARK_GRAY)),
                    offsetX, offsetY, 0xFF555555, false);
            offsetY += 12;

            List<String> authors = packInfo.getAuthors();
            if (!authors.isEmpty()) {
                gui.text(font, Component.translatable("gui.tacz.gun_smith_table.authors")
                                .append(Component.literal(StringUtils.join(authors, ", ")).withStyle(ChatFormatting.DARK_GRAY)),
                        offsetX, offsetY, 0xFF555555, false);
                offsetY += 12;
            }

            gui.text(font, Component.translatable("gui.tacz.gun_smith_table.date")
                            .append(Component.literal(packInfo.getDate()).withStyle(ChatFormatting.DARK_GRAY)),
                    offsetX, offsetY, 0xFF555555, false);

            poseStack.popMatrix();
        } else {
            Identifier recipeId = recipe.getId();
            gui.text(font, Component.translatable("gui.tacz.gun_smith_table.error").withStyle(ChatFormatting.DARK_RED), leftPos + 6, topPos + 122, 0xFFAF0000, false);
            gui.text(font, Component.translatable("gui.tacz.gun_smith_table.error.id", recipeId.toString()).withStyle(ChatFormatting.DARK_RED), leftPos + 6, topPos + 134, 0xFFFFFFFF, false);
            PackInfo errorPackInfo = ClientAssetsManager.INSTANCE.getPackInfo(id);
            if (errorPackInfo != null) {
                gui.text(font, Component.translatable(errorPackInfo.getName()).withStyle(ChatFormatting.DARK_RED), leftPos + 6, topPos + 146, 0xFFAF0000, false);
            }
        }
    }

    private void renderIngredient(GuiGraphicsExtractor gui) {
        if (this.selectedRecipe == null) {
            return;
        }
        List<GunSmithTableIngredient> inputs = this.selectedRecipe.getInputs();
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < 2; j++) {
                int index = i * 2 + j;
                if (index >= inputs.size()) {
                    return;
                }
                int offsetX = leftPos + 254 + 45 * j;
                int offsetY = topPos + 62 + 17 * i;

                GunSmithTableIngredient smithTableIngredient = inputs.get(index);
                Ingredient ingredient = smithTableIngredient.getIngredient();

                List<ItemStack> items = ingredient.display().resolveForStacks(SlotDisplayContext.fromLevel(minecraft.level));
                int itemIndex = items.isEmpty() ? 0 : (int) ((System.currentTimeMillis() / 1_000) % items.size());
                ItemStack item = items.isEmpty() ? ItemStack.EMPTY : items.get(itemIndex);

                gui.fakeItem(item, offsetX, offsetY);

                Matrix3x2fStack poseStack = gui.pose();
                poseStack.pushMatrix();

                gui.nextStratum();
                poseStack.scale(0.5f, 0.5f);
                int count = smithTableIngredient.getCount();
                if (Minecraft.getInstance().player != null && Minecraft.getInstance().player.isCreative()){
                    gui.text(font, String.format("%d/∞", count), (offsetX + 17) * 2, (offsetY + 10) * 2, 0xFFFFFFFF, false);
                } else {
                    int hasCount = 0;
                    if (playerIngredientCount != null && index < playerIngredientCount.size()) {
                        hasCount = playerIngredientCount.get(index);
                    }
                    int color = count <= hasCount ? 0xFFFFFFFF : 0xFFFF0000;
                    gui.text(font, String.format("%d/%d", count, hasCount), (offsetX + 17) * 2, (offsetY + 10) * 2, color, false);
                }


                poseStack.popMatrix();
            }
        }
    }

    private void renderLeftModel(GuiGraphicsExtractor graphics, GunSmithTableRecipe recipe) {
        RenderDistance.markGuiRenderTimestamp();
        ItemStackRenderState item = new ItemStackRenderState();
        minecraft.getItemModelResolver().updateForTopItem(item, recipe.getOutput().copy(), ItemDisplayContext.FIXED, null, null, 0);
        graphics.getRenderState().addPicturesInPictureState(new GunPreviewRenderState(item, leftPos, topPos, scale,
                System.currentTimeMillis(), graphics.pose(), graphics.getScissorStack().peek()));
    }

    @Override
    protected void extractLabels(@NotNull GuiGraphicsExtractor gui, int mouseX, int mouseY) {
    }

    @Override
    public void extractBackground(@NotNull GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(gui, mouseX, mouseY, partialTick);
        gui.blit(RenderPipelines.GUI_TEXTURED, SIDE, leftPos, topPos, 0, 0, 134, 187, 256, 256);
        gui.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos + 136, topPos + 27, 0, 0, 208, 160, 256, 256);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
