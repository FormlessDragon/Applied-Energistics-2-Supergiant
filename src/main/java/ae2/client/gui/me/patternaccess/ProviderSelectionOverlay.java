package ae2.client.gui.me.patternaccess;

import ae2.api.client.AEKeyRendering;
import ae2.api.stacks.AEItemKey;
import ae2.client.Point;
import ae2.client.gui.AEBaseGui;
import ae2.client.gui.ICompositeWidget;
import ae2.client.gui.Icon;
import ae2.client.gui.Tooltip;
import ae2.client.gui.style.BackgroundGenerator;
import ae2.client.gui.style.GeneratedBackground;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.style.GuiStyleManager;
import ae2.client.gui.style.WidgetStyle;
import ae2.client.gui.widgets.AETextField;
import ae2.client.gui.widgets.DynamicIconButton;
import ae2.client.gui.widgets.ITooltip;
import ae2.client.gui.widgets.Scrollbar;
import ae2.client.gui.widgets.SimpleIconButton;
import ae2.client.gui.widgets.TooltipButton;
import ae2.container.AEBaseContainer;
import ae2.container.me.patternencode.IProviderSelectionEndpoint;
import ae2.core.localization.ButtonToolTips;
import ae2.core.localization.GuiText;
import ae2.core.localization.Tooltips;
import ae2.core.worlddata.PatternProviderMappingData;
import ae2.container.me.patternencode.ProviderDirectoryPage;
import ae2.container.me.patternencode.ProviderDirectoryPageRequest;
import ae2.container.me.patternencode.ProviderMappingPage;
import ae2.container.me.patternencode.ProviderPageLimits;
import ae2.crafting.execution.CraftingSupplierLocator;
import ae2.integration.Integrations;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Provider Selection overlay shown on top of the current Pattern Encoding GUI.
 */
public final class ProviderSelectionOverlay<C extends AEBaseContainer & IProviderSelectionEndpoint> implements ICompositeWidget {

    public static final String WIDGET_ID = "providerSelectionOverlay";
    private static final String STYLE_PATH = "/screens/provider_selection.json";
    private static final String BACK_WIDGET = "back";
    private static final String SEARCH_WIDGET = "search";
    private static final String MAPPING_INPUT_WIDGET = "mapping";
    private static final String RELOAD_WIDGET = "reload";
    private static final String SCROLLBAR_WIDGET = "scrollbar";
    private static final String ENTRY_WIDGET_PREFIX = "entry";
    private static final int DEFAULT_WIDTH = 135;
    private static final int DEFAULT_HEIGHT = 145;
    private static final int PAGE_SIZE = 5;
    private static final int MAPPING_PROTOCOL_PAGE_SIZE = ProviderPageLimits.PAGE_SIZE;
    private static final int MAX_MAPPING_CACHE_PAGES = 64;
    private static final int TITLE_BAR_HEIGHT = 20;
    private static final int TITLE_X = 8;
    private static final int TITLE_Y = 8;
    private static final int TITLE_INFO_GAP = 4;
    private static final int TITLE_INFO_ICON_SIZE = 16;
    private static final int TITLE_INFO_ICON_TOP = 2;
    private static final long SEARCH_DEBOUNCE_NANOS = 100_000_000L;
    private static final long PAGE_REQUEST_RETRY_NANOS = 1_000_000_000L;

    private final AEBaseGui<C> parent;
    private final DynamicIconButton closeButton;
    private final SimpleIconButton reloadButton;
    private final DirectoryState directory = new DirectoryState();
    private final MappingState mapping = new MappingState();
    private final ButtonPressState buttonPressState = new ButtonPressState();
    private final ObjectArrayList<GuiButton> dynamicButtons = new ObjectArrayList<>();
    private final Scrollbar scrollbar = new Scrollbar(Scrollbar.SMALL);
    private Rectangle bounds = new Rectangle(0, 0, DEFAULT_WIDTH, DEFAULT_HEIGHT);
    private Point screenOrigin = Point.ZERO;
    private boolean visible;
    private boolean dragging;
    private boolean scrollbarMouseCapture;
    private boolean resetPositionOnOpen = true;
    private Point dragOffset = Point.ZERO;
    private TextInputTarget textInputTarget = TextInputTarget.NONE;
    private final AETextField searchField;
    private final AETextField mappingField;

    public ProviderSelectionOverlay(AEBaseGui<C> parent) {
        this.parent = parent;
        this.searchField = addTextField(
            GuiText.SearchPlaceholder.text(),
            Collections.singletonList(GuiText.SearchTooltip.text())
        );
        this.mappingField = addTextField(
            GuiText.ProviderSelectionMappingInputPlaceholder.text(),
            Collections.singletonList(ButtonToolTips.ProviderSelectionMappingInput.text())
        );
        this.closeButton = new DynamicIconButton(
            this::getCloseButtonIcon,
            GuiText.Close.text(),
            () -> List.of(getCloseButtonTooltip()),
            this::closeOrExitMappingManagement
        );
        this.closeButton.setVisibility(false);
        this.reloadButton = new SimpleIconButton(
            Icon.S_CYCLE,
            ButtonToolTips.ProviderSelectionMappingReload.text(),
            () -> reloadProviderMappings(currentViewKey()));
        this.reloadButton.setHalfSize(true);
        this.reloadButton.setVisibility(false);
        this.scrollbar.setCaptureMouseWheel(false);
    }

    private AETextField addTextField(ITextComponent placeHolder, List<ITextComponent> tooltips) {
        GuiStyle style = style();
        AETextField textField = new AETextField(style, Minecraft.getMinecraft().fontRenderer, 0, 0, 0, 0);
        textField.setEnableBackgroundDrawing(false);
        textField.setMaxStringLength(ProviderPageLimits.MAX_QUERY_UTF16_LENGTH);
        textField.setTextColor(0xFFFFFF);
        textField.setPlaceholder(placeHolder);
        textField.setTooltipMessage(tooltips);
        textField.setVisible(false);
        return textField;
    }

    public void open(String initialSearchText, String initialMappingText) {
        Objects.requireNonNull(initialSearchText, "initialSearchText");
        Objects.requireNonNull(initialMappingText, "initialMappingText");
        this.directory.searchText = initialSearchText;
        this.mapping.text = initialMappingText;
        this.mapping.enabled = PatternProviderMappingData.isMappingEnabled();
        this.textInputTarget = TextInputTarget.NONE;
        exitMappingManagement();
        this.directory.scrollOffset = 0;
        this.resetPositionOnOpen = true;
        if (!this.visible) {
            this.visible = true;
            this.dragging = false;
            this.scrollbarMouseCapture = false;
            this.dragOffset = Point.ZERO;
        }
        this.screenOrigin = Point.fromTopLeft(this.parent.getBounds(true));
        applyPendingOpenPositionReset();
        layoutCommandButtons();
        moveTextFields();
        syncTextFieldsFromState();
        beginRequestGeneration(readHostDirectoryRevision(), true, false);
    }

    private void close() {
        this.buttonPressState.clearPressedButton();
        this.dynamicButtons.clear();
        this.visible = false;
        this.closeButton.setVisibility(false);
        this.reloadButton.setVisibility(false);
        this.searchField.setVisible(false);
        this.mappingField.setVisible(false);
        this.scrollbar.setVisible(false);
        this.dragging = false;
        this.scrollbarMouseCapture = false;
        this.directory.scrollOffset = 0;
        this.textInputTarget = TextInputTarget.NONE;
        this.directory.searchText = "";
        this.mapping.text = "";
        exitMappingManagement();
        clearPageState();
        this.directory.activeWindowId = -1;
        this.directory.activeRequestNonce = 0;
        this.directory.activeRevision = -1;
        this.mapping.enabled = false;
        this.directory.searchRequestPending = false;
        syncTextFieldsFromState();
    }

    private void closeOrExitMappingManagement() {
        if (this.mapping.managedProvider == null) {
            close();
            return;
        }

        exitMappingManagement();
        this.directory.scrollOffset = 0;
        this.textInputTarget = TextInputTarget.NONE;
        updateMappingInputVisibility();
        syncTextFieldFocusFromState();
        beginRequestGeneration(Math.max(this.directory.activeRevision, readHostDirectoryRevision()), true, false);
    }

    @Override
    public boolean isVisible() {
        return this.visible;
    }

    @Override
    public void setPosition(Point position) {
        this.bounds = new Rectangle(position.x(), position.y(), this.bounds.width, this.bounds.height);
    }

    @Override
    public void setSize(int width, int height) {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Provider Selection overlay size must not be negative");
        }
        int nextWidth = width == 0 ? this.bounds.width : width;
        int nextHeight = height == 0 ? this.bounds.height : height;
        this.bounds = new Rectangle(this.bounds.x, this.bounds.y, nextWidth, nextHeight);
    }

    @Override
    public Rectangle getBounds() {
        return new Rectangle(this.bounds);
    }

    @Override
    public void populateScreen(Consumer<GuiButton> addWidget, Rectangle screenBounds, ae2.client.gui.AEBaseGui<?> screen) {
        this.screenOrigin = Point.fromTopLeft(screenBounds);
        synchronizeHostDirectory();
        if (this.visible && !applyPendingOpenPositionReset()) {
            clampToScreen();
        }
        syncTextFieldsFromState();
        rebuildButtons();
    }

    @Override
    public void updateBeforeRender() {
        if (!this.visible) {
            return;
        }

        synchronizeHostDirectory();
        applyPendingOpenPositionReset();
        boolean searchChanged = updateSearchText();
        boolean mappingChanged = updateMappingText();
        if (searchChanged || mappingChanged) {
            rebuildButtons();
        }
        updateMappingInputVisibility();
        if (!this.dragging) {
            return;
        }

        int mouseX = Mouse.getX() * this.parent.width / this.parent.mc.displayWidth;
        int mouseY = this.parent.height - Mouse.getY() * this.parent.height / this.parent.mc.displayHeight - 1;
        int relativeMouseX = mouseX - this.parent.getGuiLeft();
        int relativeMouseY = mouseY - this.parent.getGuiTop();

        this.bounds.setLocation(
            relativeMouseX - this.dragOffset.x(),
            relativeMouseY - this.dragOffset.y());
        clampToScreen();
        rebuildButtons();
    }

    @Override
    public void drawAbsoluteLayer(Rectangle screenBounds, Point mouse) {
        if (!this.visible) {
            return;
        }

        this.screenOrigin = Point.fromTopLeft(this.parent.getBounds(true));
        Point overlayOrigin = getOverlayOrigin();

        GlStateManager.pushMatrix();
        GlStateManager.translate(0, 0, 350);
        try {
            BackgroundGenerator.draw(this.bounds.width, this.bounds.height, overlayOrigin.x(), overlayOrigin.y());
            drawTitleText(Minecraft.getMinecraft(), overlayOrigin.x(), overlayOrigin.y());
            drawManagedProviderTitleInfo(Minecraft.getMinecraft(), overlayOrigin.x(), overlayOrigin.y());
            drawProviderDirectoryIcons(Minecraft.getMinecraft());
            this.closeButton.drawButton(Minecraft.getMinecraft(), mouse.x(), mouse.y(), 0);
            this.reloadButton.drawButton(Minecraft.getMinecraft(), mouse.x(), mouse.y(), 0);
            for (GuiButton button : this.dynamicButtons) {
                button.drawButton(Minecraft.getMinecraft(), mouse.x(), mouse.y(), 0);
            }
            if (this.searchField.getVisible()) {
                this.searchField.drawTextBox();
            }
            if (this.mappingField.getVisible()) {
                this.mappingField.drawTextBox();
            }
            if (this.scrollbar.isVisible()) {
                this.scrollbar.drawForegroundLayer(screenBounds, mouse);
            }
        } finally {
            GlStateManager.popMatrix();
        }
    }

    @Override
    public boolean onMouseDown(Point mousePos, int button) {
        if (!this.visible) {
            return false;
        }

        synchronizeHostDirectory();
        if (!contains(this.bounds, mousePos.x(), mousePos.y())) {
            clearFocusedTextInput();
            return false;
        }

        this.buttonPressState.clearPressedButton();
        Point absoluteMouse = this.screenOrigin.move(mousePos.x(), mousePos.y());
        if (handleTextFieldMouseDown(absoluteMouse.x(), absoluteMouse.y(), button)) {
            return true;
        }
        clearFocusedTextInput();
        if (button == 1 && handleEntryRightClick(mousePos)) {
            return true;
        }
        if (button == 0 && handleScrollbarMouseDown(absoluteMouse)) {
            return true;
        }

        GuiButton widget = getButtonAt(absoluteMouse);
        if (widget != null) {
            if (button != 0) {
                return true;
            }
            this.buttonPressState.recordPressedButton(widget);
            playPressSound(widget);
            return true;
        }

        if (button == 0 && canStartDrag(mousePos)) {
            this.dragging = true;
            this.dragOffset = new Point(mousePos.x() - this.bounds.x, mousePos.y() - this.bounds.y);
        }
        return true;
    }

    @Override
    public boolean wantsAllMouseDownEvents() {
        return this.visible;
    }

    @Override
    public boolean onMouseUp(Point mousePos, int button) {
        if (!this.visible) {
            this.buttonPressState.clearPressedButton();
            this.dragging = false;
            this.scrollbarMouseCapture = false;
            return false;
        }

        synchronizeHostDirectory();
        boolean wasDragging = this.dragging;
        boolean hadScrollbarMouseCapture = this.scrollbarMouseCapture;
        boolean hadButtonPress = this.buttonPressState.hasPressedButton();
        boolean inside = contains(this.bounds, mousePos.x(), mousePos.y());
        if (!inside && !wasDragging && !hadScrollbarMouseCapture && !hadButtonPress) {
            return false;
        }

        this.dragging = false;
        this.scrollbarMouseCapture = false;
        Point absoluteMouse = this.screenOrigin.move(mousePos.x(), mousePos.y());
        if (this.scrollbar.isVisible() && (hadScrollbarMouseCapture || inside)) {
            this.scrollbar.onMouseUp(absoluteMouse, button);
        }
        this.buttonPressState.releasePressedButton(wasDragging, absoluteMouse.x(), absoluteMouse.y());
        return true;
    }

    @Override
    public boolean wantsAllMouseUpEvents() {
        return this.visible
            && (this.dragging || this.scrollbarMouseCapture || this.buttonPressState.hasPressedButton());
    }

    @Override
    public boolean onMouseDrag(Point mousePos, int button) {
        if (!this.visible) {
            return false;
        }
        boolean inside = contains(this.bounds, mousePos.x(), mousePos.y());
        if (button != 0) {
            return inside;
        }
        Point absoluteMouse = this.screenOrigin.move(mousePos.x(), mousePos.y());
        if (this.scrollbar.isVisible()
            && (this.scrollbarMouseCapture || contains(this.scrollbar.getBounds(), absoluteMouse.x(), absoluteMouse.y()))
            && this.scrollbar.onMouseDrag(absoluteMouse, button)) {
            applyScrollbarScroll();
            return true;
        }
        if (!this.dragging) {
            return inside || this.scrollbarMouseCapture;
        }

        this.bounds.setLocation(
            mousePos.x() - this.dragOffset.x(),
            mousePos.y() - this.dragOffset.y());
        clampToScreen();
        rebuildButtons();
        return true;
    }

    @Override
    public boolean onMouseWheel(Point mousePos, double delta) {
        if (!this.visible) {
            return false;
        }
        if (!contains(this.bounds, mousePos.x(), mousePos.y())) {
            return false;
        }

        Point absoluteMouse = this.screenOrigin.move(mousePos.x(), mousePos.y());
        if (this.scrollbar.isVisible() && this.scrollbar.onMouseWheel(absoluteMouse, delta)) {
            applyScrollbarScroll();
        }
        return true;
    }

    @Override
    public void tick() {
        if (!this.visible) {
            return;
        }
        tickTextFields();
        synchronizeHostDirectory();
        expirePendingPageRequests();
        if (this.directory.searchRequestPending
            && System.nanoTime() - this.directory.searchChangedAtNanos >= SEARCH_DEBOUNCE_NANOS) {
            this.directory.searchRequestPending = false;
            requestVisiblePages();
        }
        if (this.mapping.managedProvider != null) {
            requestVisibleMappingPages(this.mapping.managedProvider);
        }
        requestVisiblePages();
        if (this.scrollbar.isVisible()) {
            this.scrollbar.tick();
            applyScrollbarScroll();
        }
    }

    private void tickTextFields() {
        tickTextField(this.searchField);
        tickTextField(this.mappingField);
    }

    private static void tickTextField(AETextField field) {
        if (!field.getVisible()) {
            return;
        }
        field.updateCursorCounter();
        field.tickKeyRepeat();
    }

    @Override
    public boolean onKeyTyped(char typedChar, int keyCode) {
        if (!this.visible) {
            return false;
        }

        if (keyCode == Keyboard.KEY_ESCAPE && this.mapping.managedProvider != null) {
            closeOrExitMappingManagement();
            return true;
        }

        if (keyCode == Keyboard.KEY_ESCAPE && clearFocusedTextInput()) {
            return true;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            closeOrExitMappingManagement();
            return true;
        }

        TextInputTarget textInputTarget = getFocusedTextInputTarget();
        if (textInputTarget == TextInputTarget.MAPPING
            && this.mappingField.textboxKeyTyped(typedChar, keyCode)) {
            updateMappingText();
            rebuildButtonsIfMinecraftAvailable();
            return true;
        }
        if (textInputTarget == TextInputTarget.SEARCH
            && this.searchField.textboxKeyTyped(typedChar, keyCode)) {
            acceptSearchTextInput(readText(this.searchField));
            rebuildButtonsIfMinecraftAvailable();
            return true;
        }
        return false;
    }

    @Nullable
    @Override
    public Tooltip getTooltip(int mouseX, int mouseY) {
        if (!this.visible || !contains(this.bounds, mouseX, mouseY)) {
            return null;
        }

        Point absoluteMouse = this.screenOrigin.move(mouseX, mouseY);
        Tooltip providerIconTooltip = getProviderDirectoryIconTooltip(absoluteMouse);
        if (providerIconTooltip != null) {
            return providerIconTooltip;
        }
        Tooltip buttonTooltip = getButtonTooltip(absoluteMouse);
        if (buttonTooltip != null) {
            return buttonTooltip;
        }
        if (this.mapping.managedProvider != null
            && contains(getManagedProviderTitleInfoTooltipArea(), absoluteMouse.x(), absoluteMouse.y())) {
            List<ITextComponent> tooltip = getManagedProviderTitleTooltip();
            if (!tooltip.isEmpty()) {
                return new Tooltip(tooltip);
            }
        }
        return null;
    }

    private void drawProviderDirectoryIcons(Minecraft minecraft) {
        if (this.mapping.managedProvider != null) {
            return;
        }
        for (GuiButton button : this.dynamicButtons) {
            if (!(button instanceof ProviderEntryButton row) || !row.isProviderRow()) {
                continue;
            }
            ProviderEntry entry = row.providerEntry();
            if (entry != null && entry.icon() != null) {
                AEKeyRendering.drawInGui(minecraft, row.x - 18, row.y, entry.icon());
                restoreGuiStateAfterTitleIcon();
            }
        }
    }

    @Nullable
    private Tooltip getProviderDirectoryIconTooltip(Point absoluteMouse) {
        if (this.mapping.managedProvider != null) {
            return null;
        }
        for (GuiButton button : this.dynamicButtons) {
            if (!(button instanceof ProviderEntryButton row) || !row.isProviderRow()) {
                continue;
            }
            ProviderEntry entry = row.providerEntry();
            if (entry != null && entry.icon() != null
                && contains(new Rectangle(row.x - 18, row.y, 16, row.height), absoluteMouse.x(), absoluteMouse.y())) {
                ObjectList<ITextComponent> tooltip = new ObjectArrayList<>(AEKeyRendering.getTooltip(entry.icon()));

                ProviderLocation location = entry.location();
                if (location != null) {
                    ITextComponent locationTooltip = GuiText.CraftingTreeLocationInDimension.text(
                        location.pos().getX(),
                        location.pos().getY(),
                        location.pos().getZ(),
                        CraftingSupplierLocator.getDimensionName(location.dimensionId()));
                    locationTooltip.getStyle().setColor(TextFormatting.GRAY);
                    tooltip.add(locationTooltip);
                }
                return new Tooltip(tooltip);
            }
        }
        return null;
    }

    @Override
    public boolean blocksTooltips(int mouseX, int mouseY) {
        return this.visible && contains(this.bounds, mouseX, mouseY);
    }

    @Override
    public boolean blocksMouseInteraction(int mouseX, int mouseY) {
        return this.visible && contains(this.bounds, mouseX, mouseY);
    }

    private static Rectangle getTitleBarBounds(Rectangle windowBounds) {
        Objects.requireNonNull(windowBounds, "windowBounds");
        return new Rectangle(windowBounds.x, windowBounds.y, windowBounds.width, TITLE_BAR_HEIGHT);
    }

    private ITextComponent getTitleText() {
        return this.mapping.managedProvider != null
            ? GuiText.ProviderSelectionMappingManagement.text()
            : GuiText.ProviderSelection.text();
    }

    private void drawTitleText(Minecraft minecraft, int x, int y) {
        int maxWidth = getTitleTextMaxWidth(minecraft);
        if (maxWidth <= 0) {
            return;
        }

        minecraft.fontRenderer.drawString(
            minecraft.fontRenderer.trimStringToWidth(getTitleText().getFormattedText(), maxWidth),
            x + TITLE_X,
            y + TITLE_Y,
            0x404040);
    }

    private int getTitleTextMaxWidth(Minecraft minecraft) {
        int right = getTitleInfoAvailableRight();
        if (getManagedProviderTitleIcon() != null) {
            right = getManagedProviderTitleInfoIconLeft(minecraft) - TITLE_INFO_GAP;
        }
        return Math.max(0, right - TITLE_X);
    }

    private void drawManagedProviderTitleInfo(Minecraft minecraft, int x, int y) {
        AEItemKey icon = getManagedProviderTitleIcon();
        if (icon == null) {
            return;
        }

        AEKeyRendering.drawInGui(minecraft,
            x + getManagedProviderTitleInfoIconLeft(minecraft),
            y + TITLE_INFO_ICON_TOP,
            icon);
        restoreGuiStateAfterTitleIcon();
    }

    private Rectangle getManagedProviderTitleInfoTooltipArea() {
        Minecraft minecraft = this.parent.getMinecraft();
        Point overlayOrigin = getOverlayOrigin();
        return new Rectangle(
            overlayOrigin.x() + getManagedProviderTitleInfoIconLeft(minecraft),
            overlayOrigin.y() + TITLE_INFO_ICON_TOP,
            TITLE_INFO_ICON_SIZE,
            TITLE_INFO_ICON_SIZE);
    }

    private int getManagedProviderTitleInfoIconLeft(@Nullable Minecraft minecraft) {
        int titleInfoStart = getTitleInfoStart(minecraft);
        int right = getTitleInfoAvailableRight();
        int iconLeft = right - TITLE_INFO_ICON_SIZE;
        return Math.max(TITLE_X, Math.min(titleInfoStart, iconLeft));
    }

    @Nullable
    private AEItemKey getManagedProviderTitleIcon() {
        ProviderEntry providerEntry = this.mapping.managedProvider;
        return providerEntry == null ? null : providerEntry.icon();
    }

    private int getTitleInfoStart(@Nullable Minecraft minecraft) {
        return TITLE_X + getTitleTextWidth(minecraft) + TITLE_INFO_GAP;
    }

    private int getTitleTextWidth(@Nullable Minecraft minecraft) {
        if (minecraft != null) {
            return minecraft.fontRenderer.getStringWidth(getTitleText().getFormattedText());
        }
        return this.mapping.managedProvider != null
            ? estimateVanillaFontWidth("Mapping Management")
            : estimateVanillaFontWidth("Select Pattern Provider");
    }

    private static int estimateVanillaFontWidth(String text) {
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            width += text.charAt(i) == ' ' ? 4 : 6;
        }
        return width;
    }

    private int getTitleInfoAvailableRight() {
        Point closePos = resolveWidgetOffset(BACK_WIDGET);
        return closePos.x() - 4;
    }

    private static void restoreGuiStateAfterTitleIcon() {
        RenderHelper.disableStandardItemLighting();
        GlStateManager.colorMask(true, true, true, true);
        GlStateManager.enableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private List<ITextComponent> getManagedProviderTitleTooltip() {
        ProviderEntry providerEntry = this.mapping.managedProvider;
        if (providerEntry == null) {
            return Collections.emptyList();
        }

        List<ITextComponent> tooltip = new ArrayList<>();
        tooltip.add(new TextComponentString(providerEntry.providerName()));
        ProviderLocation location = providerEntry.location();
        if (location != null) {
            tooltip.add(GuiText.CraftingTreeLocationInDimension.text(
                location.pos().getX(),
                location.pos().getY(),
                location.pos().getZ(),
                CraftingSupplierLocator.getDimensionName(location.dimensionId())));
        }
        return tooltip;
    }

    private List<ITextComponent> getProviderRowTooltip(ProviderEntry entry) {
        Objects.requireNonNull(entry, "entry");
        List<ITextComponent> tooltip = new ArrayList<>();
        tooltip.add(new TextComponentString(entry.providerName()));
        if (!entry.recipeTypeUids().isEmpty()) {
            for (String uid : entry.recipeTypeUids()) {
                tooltip.add(new TextComponentString(getRecipeTypeDisplayName(uid)));
            }
        }
        tooltip.add(Tooltips.muted(ButtonToolTips.ProviderSelectionEntryUpload.text()));
        if (!entry.hasMappingTarget() && !this.mapping.enabled) {
            return tooltip;
        }

        if (!this.mapping.text.isEmpty()) {
            ITextComponent mappingComponent = new TextComponentString(this.mapping.text);
            tooltip.add(Tooltips.muted(ButtonToolTips.ProviderSelectionMappingBind.text(mappingComponent)));
            tooltip.add(Tooltips.muted(ButtonToolTips.ProviderSelectionMappingBindAndUpload.text(mappingComponent)));
        }
        tooltip.add(Tooltips.muted(ButtonToolTips.ProviderSelectionMappingUnbind.text()));
        tooltip.add(Tooltips.muted(ButtonToolTips.ProviderSelectionMappingManage.text()));
        return tooltip;
    }

    private static String getRecipeTypeDisplayName(String uid) {
        String title = Integrations.hei().getRecipeCategoryTitle(uid);
        return title == null || title.isEmpty() ? uid : title;
    }

    private static ITextComponent getMappingAddTooltip(String mappingInputText, String providerName) {
        Objects.requireNonNull(mappingInputText, "mappingInputText");
        Objects.requireNonNull(providerName, "providerName");
        if (mappingInputText.isEmpty()) {
            return ButtonToolTips.ProviderSelectionMappingInputRequired.text();
        }
        return ButtonToolTips.ProviderSelectionMappingAdd.text(
            new TextComponentString(mappingInputText),
            new TextComponentString(providerName));
    }

    private static int getMaxScrollOffset(int entryCount) {
        if (entryCount < 0) {
            throw new IllegalArgumentException("Provider Selection entry count must not be negative");
        }
        return Math.max(0, entryCount - PAGE_SIZE);
    }

    private static int getMaxManagedMappingScrollOffset(int entryCount) {
        if (entryCount < 0) {
            throw new IllegalArgumentException("Provider Selection managed mapping entry count must not be negative");
        }
        if (entryCount <= PAGE_SIZE) {
            return 0;
        }
        return (entryCount - 1) / PAGE_SIZE * PAGE_SIZE;
    }

    private static boolean contains(Rectangle area, int mouseX, int mouseY) {
        return mouseX >= area.x
            && mouseY >= area.y
            && mouseX < area.x + area.width
            && mouseY < area.y + area.height;
    }

    private static GuiStyle style() {
        return StyleHolder.STYLE;
    }

    private static int getWindowWidth() {
        GeneratedBackground background = style().getGeneratedBackground();
        return background != null ? background.getWidth() : DEFAULT_WIDTH;
    }

    private static int getWindowHeight() {
        GeneratedBackground background = style().getGeneratedBackground();
        return background != null ? background.getHeight() : DEFAULT_HEIGHT;
    }

    private void syncTextFieldsFromState() {
        this.searchField.setText(this.directory.searchText);
        this.mappingField.setText(this.mapping.text);
        updateMappingInputVisibility();
        syncTextFieldFocusFromState();
    }

    private boolean updateSearchText() {
        if (this.mapping.managedProvider != null) {
            return false;
        }
        return acceptSearchTextInput(readText(this.searchField));
    }

    boolean acceptSearchTextInput(String text) {
        return acceptSearchTextInput(text, false);
    }

    private boolean acceptSearchTextInput(String text, boolean requestImmediately) {
        if (this.mapping.managedProvider != null) {
            return false;
        }
        String boundedText = ProviderPageLimits.requireBoundedText(
            "provider directory query",
            text,
            ProviderPageLimits.MAX_QUERY_UTF16_LENGTH,
            ProviderPageLimits.MAX_QUERY_UTF8_BYTES);
        if (this.directory.searchText.equals(boundedText)) {
            return false;
        }
        this.directory.searchText = boundedText;
        scheduleSearchRequest(requestImmediately);
        return true;
    }

    private boolean updateMappingText() {
        String text = readText(this.mappingField);
        if (this.mapping.text.equals(text)) {
            return false;
        }
        this.mapping.text = text;
        return true;
    }

    private static String readText(AETextField field) {
        String text = field.getText();
        return text != null ? text : "";
    }

    private void updateMappingInputVisibility() {
        this.mappingField.setVisible(this.mapping.enabled);
        this.mappingField.setEnabled(this.mapping.enabled);
        if (!this.mapping.enabled && this.textInputTarget == TextInputTarget.MAPPING) {
            this.textInputTarget = TextInputTarget.NONE;
        }
    }

    private TextInputTarget getFocusedTextInputTarget() {
        return switch (this.textInputTarget) {
            case SEARCH -> this.searchField.getVisible() && this.searchField.isFocused()
                ? TextInputTarget.SEARCH : TextInputTarget.NONE;
            case MAPPING -> this.mappingField.getVisible() && this.mappingField.isFocused()
                ? TextInputTarget.MAPPING : TextInputTarget.NONE;
            case NONE -> TextInputTarget.NONE;
        };
    }

    private void syncTextFieldFocusFromState() {
        this.searchField.setFocused(this.textInputTarget == TextInputTarget.SEARCH);
        this.mappingField.setFocused(this.textInputTarget == TextInputTarget.MAPPING);
        if (this.textInputTarget == TextInputTarget.MAPPING) {
            this.mappingField.setCursorPositionEnd();
        }
    }

    private void focusSearchInput() {
        this.textInputTarget = TextInputTarget.SEARCH;
        syncTextFieldFocusFromState();
    }

    private void focusMappingInput() {
        this.textInputTarget = TextInputTarget.MAPPING;
        syncTextFieldFocusFromState();
    }

    private boolean clearFocusedTextInput() {
        if (this.textInputTarget == TextInputTarget.NONE) {
            return false;
        }
        this.textInputTarget = TextInputTarget.NONE;
        syncTextFieldFocusFromState();
        return true;
    }

    private void rebuildButtons() {
        layoutCommandButtons();
        if (this.directory.refreshPreservingButtons && this.mapping.managedProvider == null) {
            requestVisiblePages();
            repositionPreservedDirectoryButtons();
            return;
        }
        this.dynamicButtons.clear();
        if (!this.visible) {
            return;
        }

        requestVisiblePages();

        if (this.mapping.managedProvider != null) {
            rebuildMappingManagementButtons();
            return;
        }

        if (this.directory.total >= 0) {
            updateScrollbar(this.directory.total);
        }
        ProviderViewKey viewKey = currentViewKey();
        int start = this.directory.scrollOffset;
        for (int i = 0; i < PAGE_SIZE; i++) {
            int visibleIndex = start + i;
            ProviderEntry entry = getDirectoryEntry(visibleIndex);
            if (entry != null) {
                ProviderEntryButton entryButton = ProviderEntryButton.provider(
                    new TextComponentString(entry.providerName() + " (" + entry.emptySlots() + ")"),
                    () -> handleProviderEntryLeftClick(viewKey, entry),
                    () -> getProviderRowTooltip(entry),
                    viewKey,
                    entry);
                moveButton(entryButton, ENTRY_WIDGET_PREFIX + i);
                // Keep a fixed icon gutter outside the shortened action button.
                entryButton.x += 18;
                entryButton.width -= 18;
                this.dynamicButtons.add(entryButton);
            }
        }

        moveTextFields();
    }

    private void repositionPreservedDirectoryButtons() {
        int providerRow = 0;
        for (GuiButton button : this.dynamicButtons) {
            if (button instanceof ProviderEntryButton entryButton && entryButton.isProviderRow()) {
                moveButton(button, ENTRY_WIDGET_PREFIX + providerRow++);
                button.x += 18;
                button.width -= 18;
            }
        }
        moveTextFields();
        moveScrollbar();
    }

    private void rebuildMappingManagementButtons() {
        if (!this.mapping.enabled) {
            exitMappingManagement();
            rebuildButtons();
            return;
        }
        ProviderEntry providerEntry = this.mapping.managedProvider;
        if (providerEntry == null) {
            updateManagedMappingScrollbar(0);
            return;
        }

        int mappingEntryCount = Math.incrementExact(providerEntry.recipeTypeCount());
        updateManagedMappingScrollbar(mappingEntryCount);
        requestVisibleMappingPages(providerEntry);
        ProviderViewKey viewKey = currentViewKey();
        int start = this.directory.scrollOffset;
        for (int i = 0; i < PAGE_SIZE; i++) {
            int visibleIndex = start + i;
            String recipeTypeUid = getCachedMappingUid(visibleIndex);
            if (recipeTypeUid != null) {
                ProviderEntryButton entryButton = ProviderEntryButton.mapping(
                    new TextComponentString(getRecipeTypeDisplayName(recipeTypeUid)),
                    () -> unbindManagedRecipeType(viewKey, providerEntry.providerEntryId(), recipeTypeUid),
                    () -> List.of(new TextComponentString(recipeTypeUid),
                        ButtonToolTips.ProviderSelectionMappingUnbindRecipe.text(new TextComponentString(recipeTypeUid))),
                    viewKey);
                moveButton(entryButton, ENTRY_WIDGET_PREFIX + i);
                this.dynamicButtons.add(entryButton);
            } else if (visibleIndex == providerEntry.recipeTypeCount()) {
                ProviderEntryButton entryButton = ProviderEntryButton.mapping(
                    GuiText.ProviderSelectionMappingAdd.text(),
                    () -> addManagedRecipeType(viewKey, providerEntry.providerEntryId()),
                    () -> Collections.singletonList(getMappingAddTooltip(this.mapping.text,
                        providerEntry.providerName())),
                    viewKey);
                moveButton(entryButton, ENTRY_WIDGET_PREFIX + i);
                this.dynamicButtons.add(entryButton);
            }
        }

        moveTextFields();
    }

    private void layoutCommandButtons() {
        this.closeButton.setVisibility(this.visible);
        this.reloadButton.setVisibility(this.visible && this.mapping.enabled);
        if (!this.visible) {
            return;
        }

        moveButton(this.closeButton, BACK_WIDGET);
        if (this.reloadButton.visible) {
            moveButton(this.reloadButton, RELOAD_WIDGET);
        }
    }

    private Icon getCloseButtonIcon() {
        return this.mapping.managedProvider != null ? Icon.BACK : Icon.CLEAR;
    }

    private ITextComponent getCloseButtonTooltip() {
        return this.mapping.managedProvider != null ? GuiText.ReturnToPreviousGui.text() : GuiText.Close.text();
    }

    private Point getOverlayOrigin() {
        return this.screenOrigin.move(this.bounds.x, this.bounds.y);
    }

    private Point resolveWidgetOffset(String widgetId) {
        WidgetStyle widgetStyle = style().getWidget(widgetId);
        return widgetStyle.resolve(new Rectangle(0, 0, this.bounds.width, this.bounds.height));
    }

    private Point resolveWidgetPosition(String widgetId) {
        Point offset = resolveWidgetOffset(widgetId);
        return getOverlayOrigin().move(offset.x(), offset.y());
    }

    private void moveButton(GuiButton button, String widgetId) {
        WidgetStyle widgetStyle = style().getWidget(widgetId);
        Point position = resolveWidgetPosition(widgetId);
        button.x = position.x();
        button.y = position.y();
        button.width = widgetStyle.getWidth() != 0 ? widgetStyle.getWidth() : button.width;
        button.height = widgetStyle.getHeight() != 0 ? widgetStyle.getHeight() : button.height;
    }

    private void moveTextFields() {
        this.searchField.setVisible(this.mapping.managedProvider == null);
        moveTextField(this.searchField, SEARCH_WIDGET);
        moveTextField(this.mappingField, MAPPING_INPUT_WIDGET);
    }

    private void updateScrollbar(int visibleEntryCount) {
        moveScrollbar();
        int maxScroll = getMaxScrollOffset(visibleEntryCount);
        int scroll = Math.clamp(this.directory.scrollOffset, 0, maxScroll);
        this.directory.scrollOffset = scroll;
        this.scrollbar.setRange(0, maxScroll, 1);
        this.scrollbar.setCurrentScroll(scroll);
        this.scrollbar.setVisible(visibleEntryCount > PAGE_SIZE);
    }

    private void updateManagedMappingScrollbar(int visibleEntryCount) {
        moveScrollbar();
        int maxScroll = getMaxManagedMappingScrollOffset(visibleEntryCount);
        int scroll = Math.clamp(this.directory.scrollOffset, 0, maxScroll);
        this.directory.scrollOffset = scroll;
        this.scrollbar.setRange(0, maxScroll, PAGE_SIZE);
        this.scrollbar.setCurrentScroll(scroll);
        this.scrollbar.setVisible(visibleEntryCount > PAGE_SIZE);
    }

    private void moveScrollbar() {
        WidgetStyle widgetStyle = style().getWidget(SCROLLBAR_WIDGET);
        this.scrollbar.setPosition(resolveWidgetPosition(SCROLLBAR_WIDGET));
        this.scrollbar.setSize(widgetStyle.getWidth(), widgetStyle.getHeight());
    }

    private void moveTextField(AETextField field, String widgetId) {
        WidgetStyle widgetStyle = style().getWidget(widgetId);
        field.move(resolveWidgetPosition(widgetId));
        field.resize(widgetStyle.getWidth(), widgetStyle.getHeight());
    }

    public void receiveProviderDirectoryPage(ProviderDirectoryPage page) {
        Objects.requireNonNull(page, "page");
        if (!this.visible
            || page.windowId() != this.directory.activeWindowId
            || page.nonce() != this.directory.activeRequestNonce
            || page.directoryRevision() < this.directory.activeRevision) {
            return;
        }

        if (this.mapping.managedProvider != null) {
            receiveManagedProviderDirectoryPage(page);
            return;
        }

        if (page.directoryRevision() > this.directory.activeRevision) {
            this.directory.activeRevision = page.directoryRevision();
            clearPageState();
        }
        ProviderDirectoryPageCache.DirectoryPageView existing = this.directory.pageCache.getDirectoryPage(
            page.windowId(), page.nonce(), page.directoryRevision(), page.page());
        this.directory.pendingPages.remove(page.page());
        if (existing != null) {
            return;
        }

        this.directory.pageCache.put(page);
        this.directory.total = page.total();
        if (this.directory.refreshPreservingButtons && !areVisibleDirectoryPagesLoaded()) {
            requestVisiblePages();
            return;
        }
        this.directory.refreshPreservingButtons = false;
        this.buttonPressState.clearPressedButton();
        rebuildButtons();
    }

    /**
     * Mapping pages are only accepted for the provider currently being managed.
     */
    public void receiveProviderMappingPage(ProviderMappingPage page) {
        Objects.requireNonNull(page, "page");
        ProviderEntry provider = this.mapping.managedProvider;
        if (!this.visible || provider == null || page.windowId() != this.directory.activeWindowId
            || page.nonce() != this.directory.activeRequestNonce || page.directoryRevision() != this.directory.activeRevision
            || page.providerEntryId() != provider.providerEntryId()) {
            return;
        }
        this.mapping.pendingPages.remove(page.page());
        this.mapping.pages.put(page.page(), page.recipeTypeUids());
        while (this.mapping.pages.size() > MAX_MAPPING_CACHE_PAGES) {
            this.mapping.pages.remove(this.mapping.pages.keySet().iterator().next());
        }
        this.mapping.managedProvider = new ProviderEntry(provider.providerEntryId(), provider.icon(), provider.location(),
            provider.hasMappingTarget(), provider.providerName(), provider.emptySlots(), page.total(),
            provider.recipeTypeUids(), provider.acceptsProcessingPatterns());
        rebuildButtons();
    }

    @Nullable
    private String getCachedMappingUid(int index) {
        if (index < 0) {
            return null;
        }
        List<String> mappingPage = this.mapping.pages.get(index / MAPPING_PROTOCOL_PAGE_SIZE);
        int pageIndex = index % MAPPING_PROTOCOL_PAGE_SIZE;
        return mappingPage != null && pageIndex < mappingPage.size() ? mappingPage.get(pageIndex) : null;
    }

    private void requestVisibleMappingPages(ProviderEntry provider) {
        int total = provider.recipeTypeCount();
        if (total == 0) {
            return;
        }
        int first = this.directory.scrollOffset;
        int last = Math.min(total - 1, first + PAGE_SIZE - 1);
        for (int protocolPage = first / MAPPING_PROTOCOL_PAGE_SIZE;
             protocolPage <= last / MAPPING_PROTOCOL_PAGE_SIZE; protocolPage++) {
            if (this.mapping.pages.containsKey(protocolPage) || this.mapping.pendingPages.containsKey(protocolPage)) {
                continue;
            }
            this.mapping.pendingPages.put(protocolPage, System.nanoTime());
            this.parent.getContainer().requestProviderMappingPage(this.directory.activeRequestNonce, this.directory.activeRevision,
                provider.providerEntryId(), protocolPage);
        }
    }

    private void receiveManagedProviderDirectoryPage(ProviderDirectoryPage page) {
        ProviderDirectoryPageRequest.Focus focus = this.mapping.directoryFocus;
        if (!this.mapping.directoryRefreshPending || focus == null || page.page() != 0) {
            return;
        }

        this.directory.pendingPages.remove(0);
        this.directory.activeRevision = page.directoryRevision();
        ProviderEntry refreshedProvider = page.entries().isEmpty()
            ? null
            : ProviderDirectoryPageCache.convert(page.entries().getFirst());
        if (refreshedProvider == null
            || !matchesManagedDirectoryFocus(refreshedProvider, focus)
            || !refreshedProvider.hasMappingTarget()) {
            returnToDirectoryAfterManagedProviderDisappeared();
            return;
        }

        this.mapping.managedProvider = refreshedProvider;
        clearMappingPageState();
        this.mapping.directoryFocus = null;
        this.mapping.directoryRefreshPending = false;
        this.buttonPressState.clearPressedButton();
        rebuildButtons();
    }

    private static boolean matchesManagedDirectoryFocus(ProviderEntry entry,
                                                        ProviderDirectoryPageRequest.Focus focus) {
        if (entry.providerEntryId() == focus.providerEntryId()) {
            return true;
        }
        ProviderLocation location = entry.location();
        return location != null
            && location.dimensionId() == focus.dimension()
            && location.pos().toLong() == focus.position()
            && sideOrdinal(location.side()) == focus.side();
    }

    private void returnToDirectoryAfterManagedProviderDisappeared() {
        exitMappingManagement();
        this.directory.scrollOffset = 0;
        this.textInputTarget = TextInputTarget.NONE;
        updateMappingInputVisibility();
        syncTextFieldFocusFromState();
        this.directory.searchRequestPending = false;
        beginRequestGeneration(Math.max(this.directory.activeRevision, readHostDirectoryRevision()), true, false);
    }

    private void synchronizeHostDirectory() {
        if (!this.visible) {
            return;
        }
        int windowId = readHostWindowId();
        long revision = readHostDirectoryRevision();
        if (windowId < 0 || revision < 0) {
            close();
            return;
        }
        if (windowId != this.directory.activeWindowId) {
            this.directory.searchRequestPending = false;
            if (this.mapping.managedProvider != null) {
                returnToDirectoryForNewRevision(revision);
                return;
            }
            beginRequestGeneration(revision, true, false);
            return;
        }
        if (revision > this.directory.activeRevision) {
            this.directory.searchRequestPending = false;
            if (this.mapping.managedProvider != null) {
                beginManagedDirectoryRefresh(revision, true);
                return;
            }
            beginRequestGeneration(revision, true, true);
        }
    }

    private void returnToDirectoryForNewRevision(long revision) {
        exitMappingManagement();
        this.directory.scrollOffset = 0;
        this.textInputTarget = TextInputTarget.NONE;
        updateMappingInputVisibility();
        syncTextFieldFocusFromState();
        this.directory.searchRequestPending = false;
        beginRequestGeneration(revision, true, false);
    }

    private int readHostWindowId() {
        int windowId = this.parent.getContainer().windowId;
        return Math.max(windowId, -1);
    }

    private long readHostDirectoryRevision() {
        long revision = this.parent.getContainer().getProviderDirectoryRevision();
        return Math.max(revision, -1);
    }

    private void beginRequestGeneration(long revision, boolean requestImmediately,
                                        boolean preserveVisibleButtons) {
        if (revision < 0) {
            return;
        }
        int windowId = readHostWindowId();
        if (windowId < 0) {
            return;
        }
        boolean windowChanged = windowId != this.directory.activeWindowId;
        this.directory.activeWindowId = windowId;
        if (windowChanged) {
            this.directory.scrollOffset = 0;
        }
        this.directory.activeRevision = windowChanged
            ? revision
            : Math.max(revision, this.directory.activeRevision);
        this.directory.activeRequestNonce = incrementRequestNonce();
        clearPageState();
        clearMappingPageState();
        this.buttonPressState.clearPressedButton();
        this.directory.refreshPreservingButtons = preserveVisibleButtons && !this.dynamicButtons.isEmpty();
        if (requestImmediately) {
            this.directory.searchRequestPending = false;
            requestVisiblePages();
        }
        rebuildButtons();
    }

    private void beginManagedDirectoryRefresh(long revision, boolean requestImmediately) {
        if (revision < 0) {
            returnToDirectoryForNewRevision(revision);
            return;
        }
        ProviderEntry provider = this.mapping.managedProvider;
        ProviderDirectoryPageRequest.Focus focus = provider == null ? null : createManagedDirectoryFocus(provider);
        if (focus == null) {
            returnToDirectoryForNewRevision(revision);
            return;
        }
        int windowId = readHostWindowId();
        if (windowId < 0) {
            close();
            return;
        }
        if (windowId != this.directory.activeWindowId) {
            returnToDirectoryForNewRevision(revision);
            return;
        }

        this.directory.activeRevision = Math.max(revision, this.directory.activeRevision);
        this.directory.activeRequestNonce = incrementRequestNonce();
        clearPageState();
        clearMappingPageState();
        this.buttonPressState.clearPressedButton();
        this.directory.refreshPreservingButtons = false;
        this.mapping.directoryFocus = focus;
        this.mapping.directoryRefreshPending = true;
        if (requestImmediately) {
            this.directory.searchRequestPending = false;
            requestVisiblePages();
        }
    }

    @Nullable
    private static ProviderDirectoryPageRequest.Focus createManagedDirectoryFocus(ProviderEntry provider) {
        ProviderLocation location = provider.location();
        if (!provider.hasMappingTarget() || location == null) {
            return null;
        }
        return new ProviderDirectoryPageRequest.Focus(
            provider.providerEntryId(),
            location.dimensionId(),
            location.pos().toLong(),
            sideOrdinal(location.side()));
    }

    private static int sideOrdinal(@Nullable EnumFacing side) {
        return side == null ? -1 : side.ordinal();
    }

    private long incrementRequestNonce() {
        this.directory.nextRequestNonce = Math.incrementExact(this.directory.nextRequestNonce);
        if (this.directory.nextRequestNonce <= 0) {
            throw new IllegalStateException("Provider Selection request nonce space exhausted");
        }
        return this.directory.nextRequestNonce;
    }

    private void clearPageState() {
        this.directory.pageCache.clear();
        this.directory.pendingPages.clear();
        this.directory.total = -1;
    }

    private void clearMappingPageState() {
        this.mapping.pages.clear();
        this.mapping.pendingPages.clear();
    }

    private void expirePendingPageRequests() {
        long now = System.nanoTime();
        expirePendingPageRequests(this.directory.pendingPages, now);
        expirePendingPageRequests(this.mapping.pendingPages, now);
    }

    private static void expirePendingPageRequests(Map<Integer, Long> pendingPages, long now) {
        pendingPages.entrySet().removeIf(entry -> {
            long elapsed = now - entry.getValue();
            return elapsed >= PAGE_REQUEST_RETRY_NANOS;
        });
    }

    private void scheduleSearchRequest(boolean requestImmediately) {
        this.directory.scrollOffset = 0;
        this.directory.searchRequestPending = true;
        this.directory.searchChangedAtNanos = System.nanoTime();
        if (this.mapping.managedProvider != null && this.mapping.directoryRefreshPending) {
            beginManagedDirectoryRefresh(
                Math.max(this.directory.activeRevision, readHostDirectoryRevision()), requestImmediately);
            return;
        }
        beginRequestGeneration(Math.max(this.directory.activeRevision, readHostDirectoryRevision()),
            requestImmediately, false);
    }

    private void requestVisiblePages() {
        if (!this.visible || this.directory.searchRequestPending || this.directory.activeRequestNonce <= 0) {
            return;
        }
        if (this.mapping.managedProvider != null) {
            requestManagedProviderDirectoryPage();
            return;
        }
        requestVisibleDirectoryPages();
    }

    private void requestManagedProviderDirectoryPage() {
        ProviderDirectoryPageRequest.Focus focus = this.mapping.directoryFocus;
        if (!this.mapping.directoryRefreshPending || focus == null || this.directory.pendingPages.containsKey(0)) {
            return;
        }
        this.directory.pendingPages.put(0, System.nanoTime());
        this.parent.getContainer().requestProviderDirectoryPage(this.directory.activeRequestNonce, this.directory.searchText, 0, focus);
    }

    private void requestVisibleDirectoryPages() {
        if (this.directory.total < 0) {
            requestDirectoryPage(this.directory.scrollOffset / ProviderPageLimits.PAGE_SIZE);
            return;
        }
        if (this.directory.total == 0) {
            return;
        }
        int firstVisible = Math.clamp(this.directory.scrollOffset, 0, getMaxScrollOffset(this.directory.total));
        int lastVisible = Math.min(this.directory.total - 1, firstVisible + PAGE_SIZE - 1);
        requestDirectoryPage(firstVisible / ProviderPageLimits.PAGE_SIZE);
        requestDirectoryPage(lastVisible / ProviderPageLimits.PAGE_SIZE);
    }

    private boolean areVisibleDirectoryPagesLoaded() {
        if (this.directory.total < 0) {
            return false;
        }
        if (this.directory.total == 0) {
            return true;
        }
        int firstVisible = Math.clamp(this.directory.scrollOffset, 0, getMaxScrollOffset(this.directory.total));
        int lastVisible = Math.min(this.directory.total - 1, firstVisible + PAGE_SIZE - 1);
        return hasDirectoryPage(firstVisible / ProviderPageLimits.PAGE_SIZE)
            && hasDirectoryPage(lastVisible / ProviderPageLimits.PAGE_SIZE);
    }

    private boolean hasDirectoryPage(int protocolPage) {
        return this.directory.pageCache.getDirectoryPage(
            this.directory.activeWindowId, this.directory.activeRequestNonce, this.directory.activeRevision, protocolPage) != null;
    }

    private void requestDirectoryPage(int protocolPage) {
        if (this.directory.pageCache.getDirectoryPage(this.directory.activeWindowId, this.directory.activeRequestNonce,
            this.directory.activeRevision, protocolPage) != null || this.directory.pendingPages.containsKey(protocolPage)) {
            return;
        }
        this.directory.pendingPages.put(protocolPage, System.nanoTime());
        this.parent.getContainer().requestProviderDirectoryPage(this.directory.activeRequestNonce, this.directory.searchText, protocolPage, null);
    }

    @Nullable
    private ProviderEntry getDirectoryEntry(int index) {
        if (index < 0 || this.directory.total < 0 || index >= this.directory.total) {
            return null;
        }
        int protocolPage = index / ProviderPageLimits.PAGE_SIZE;
        ProviderDirectoryPageCache.DirectoryPageView page = this.directory.pageCache.getDirectoryPage(
            this.directory.activeWindowId, this.directory.activeRequestNonce, this.directory.activeRevision, protocolPage);
        int pageOffset = index % ProviderPageLimits.PAGE_SIZE;
        return page == null || pageOffset >= page.entries().size() ? null : page.entries().get(pageOffset);
    }

    private ProviderViewKey currentViewKey() {
        long providerEntryId = this.mapping.managedProvider == null
            ? Long.MIN_VALUE
            : this.mapping.managedProvider.providerEntryId();
        return new ProviderViewKey(this.directory.activeWindowId, this.directory.activeRequestNonce, this.directory.activeRevision,
            this.directory.scrollOffset, providerEntryId);
    }

    private void applyScrollbarScroll() {
        int scroll = this.scrollbar.getCurrentScroll();
        if (this.directory.scrollOffset == scroll) {
            return;
        }
        this.directory.scrollOffset = scroll;
        this.buttonPressState.clearPressedButton();
        rebuildButtons();
    }

    private boolean handleTextFieldMouseDown(int mouseX, int mouseY, int button) {
        if (this.searchField.getVisible() && this.searchField.isMouseOver(mouseX, mouseY)) {
            focusSearchInput();
            if (button == 1) {
                this.searchField.setText("");
                acceptSearchTextInput("", true);
                return true;
            }
            return this.searchField.mouseClicked(mouseX, mouseY, button);
        }
        if (this.mappingField.getVisible()
            && this.mappingField.isMouseOver(mouseX, mouseY)) {
            focusMappingInput();
            if (button == 1) {
                this.mappingField.setText("");
                this.mapping.text = "";
                rebuildButtons();
                return true;
            }
            return this.mappingField.mouseClicked(mouseX, mouseY, button);
        }
        return false;
    }

    private boolean handleEntryRightClick(Point mousePos) {
        if (!this.mapping.enabled) {
            return false;
        }
        if (this.mapping.managedProvider != null) {
            return true;
        }
        Point absoluteMouse = this.screenOrigin.move(mousePos.x(), mousePos.y());
        for (GuiButton widget : this.dynamicButtons) {
            if (!(widget instanceof ProviderEntryButton entryButton)
                || !entryButton.isProviderRow()
                || !entryButton.visible
                || !entryButton.enabled
                || !entryButton.viewKey().equals(currentViewKey())
                || !contains(getButtonBounds(entryButton), absoluteMouse.x(), absoluteMouse.y())) {
                continue;
            }

            ProviderEntry entry = entryButton.providerEntry();
            if (entry == null) {
                continue;
            }
            if (isShiftKeyDown()) {
                unbindProviderMapping(entryButton.viewKey(), entry);
                return true;
            }
            if (!getMappingText().isEmpty()) {
                bindProviderMapping(entryButton.viewKey(), entry);
            }
            return true;
        }
        return false;
    }

    private boolean handleScrollbarMouseDown(Point absoluteMouse) {
        if (!this.scrollbar.isVisible() || !contains(this.scrollbar.getBounds(), absoluteMouse.x(), absoluteMouse.y())) {
            return false;
        }
        boolean handled = this.scrollbar.onMouseDown(absoluteMouse, 0);
        if (handled) {
            this.scrollbarMouseCapture = true;
            applyScrollbarScroll();
        }
        return handled;
    }

    private void rebuildButtonsIfMinecraftAvailable() {
        if (this.parent.getMinecraft() != null) {
            rebuildButtons();
        }
    }

    private void playPressSound(GuiButton widget) {
        Minecraft minecraft = this.parent.getMinecraft();
        if (minecraft != null) {
            widget.playPressSound(minecraft.getSoundHandler());
        }
    }

    @Nullable
    private GuiButton getButtonAt(Point mouse) {
        if (isPointerOverButton(this.closeButton, mouse)) {
            return this.closeButton;
        }
        if (isPointerOverButton(this.reloadButton, mouse)) {
            return this.reloadButton;
        }
        for (GuiButton button : this.dynamicButtons) {
            if (isPointerOverButton(button, mouse)) {
                return button;
            }
        }
        return null;
    }

    @Nullable
    private Tooltip getButtonTooltip(Point mouse) {
        Tooltip tooltip = getButtonTooltip(this.closeButton, mouse);
        if (tooltip != null) {
            return tooltip;
        }
        tooltip = getButtonTooltip(this.reloadButton, mouse);
        if (tooltip != null) {
            return tooltip;
        }
        for (GuiButton button : this.dynamicButtons) {
            tooltip = getButtonTooltip(button, mouse);
            if (tooltip != null) {
                return tooltip;
            }
        }
        return null;
    }

    @Nullable
    private static Tooltip getButtonTooltip(GuiButton button, Point mouse) {
        if (button instanceof ITooltip tooltip
            && tooltip.isTooltipAreaVisible()
            && contains(tooltip.getTooltipArea(), mouse.x(), mouse.y())) {
            return new Tooltip(tooltip.getTooltipMessage());
        }
        return null;
    }

    private static boolean isPointerOverButton(GuiButton button, Point mouse) {
        return button.visible && contains(getButtonBounds(button), mouse.x(), mouse.y());
    }

    private void handleProviderEntryLeftClick(ProviderViewKey viewKey, ProviderEntry entry) {
        if (!isCurrentProviderDirectory(viewKey)) {
            return;
        }
        if (isCtrlKeyDown()) {
            if (this.mapping.enabled && entry.hasMappingTarget()) {
                enterMappingManagement(entry);
            }
            return;
        }
        if (isShiftKeyDown()) {
            String mappingText = getMappingText();
            if (this.mapping.enabled && entry.hasMappingTarget() && !mappingText.isEmpty()) {
                this.parent.getContainer().bindAndUploadProcessingPatternToProvider(viewKey.revision(), entry.providerEntryId(), mappingText);
                close();
                return;
            }
            uploadToProvider(viewKey, entry);
            return;
        }

        uploadToProvider(viewKey, entry);
    }

    private void uploadToProvider(ProviderViewKey viewKey, ProviderEntry entry) {
        if (!isCurrentProviderDirectory(viewKey)) {
            return;
        }
        this.parent.getContainer().uploadProcessingPatternToProvider(viewKey.revision(), entry.providerEntryId());
        close();
    }

    private void bindProviderMapping(ProviderViewKey viewKey, ProviderEntry entry) {
        if (!this.mapping.enabled || !isCurrentProviderDirectory(viewKey)) {
            return;
        }
        String mappingText = getMappingText();
        if (entry.hasMappingTarget() && !mappingText.isEmpty()) {
            this.parent.getContainer().bindProviderMapping(viewKey.revision(), entry.providerEntryId(), mappingText);
        }
    }

    private void unbindProviderMapping(ProviderViewKey viewKey, ProviderEntry entry) {
        if (!this.mapping.enabled || !isCurrentProviderDirectory(viewKey)) {
            return;
        }
        if (entry.hasMappingTarget()) {
            this.parent.getContainer().unbindProviderMapping(viewKey.revision(), entry.providerEntryId());
        }
    }

    private void reloadProviderMappings(ProviderViewKey viewKey) {
        if (!this.mapping.enabled || !isCurrentProviderDirectory(viewKey)) {
            return;
        }
        this.parent.getContainer().rebuildMappingsFromActiveProviders();
    }

    private void enterMappingManagement(ProviderEntry entry) {
        Objects.requireNonNull(entry, "entry");
        if (!this.mapping.enabled || !entry.hasMappingTarget()) {
            return;
        }
        this.mapping.managedProvider = entry;
        clearMappingPageState();
        this.directory.scrollOffset = 0;
        this.textInputTarget = TextInputTarget.NONE;
        updateMappingInputVisibility();
        syncTextFieldFocusFromState();
        this.buttonPressState.clearPressedButton();
        rebuildButtons();
    }

    private void exitMappingManagement() {
        this.mapping.managedProvider = null;
        this.mapping.directoryFocus = null;
        this.mapping.directoryRefreshPending = false;
        clearMappingPageState();
    }

    private void unbindManagedRecipeType(ProviderViewKey viewKey, long providerEntryId, String recipeTypeUid) {
        if (!this.mapping.enabled || !isCurrentProviderDirectory(viewKey)) {
            return;
        }
        ProviderEntry providerEntry = this.mapping.managedProvider;
        if (providerEntry == null || providerEntry.providerEntryId() != providerEntryId) {
            return;
        }
        this.parent.getContainer().unbindProviderMapping(viewKey.revision(), providerEntryId, recipeTypeUid);
    }

    private void addManagedRecipeType(ProviderViewKey viewKey, long providerEntryId) {
        if (!this.mapping.enabled) {
            return;
        }
        ProviderEntry provider = this.mapping.managedProvider;
        if (!isCurrentProviderDirectory(viewKey) || provider == null || provider.providerEntryId() != providerEntryId) {
            return;
        }
        String mappingText = getMappingText();
        if (!mappingText.isEmpty()) {
            this.parent.getContainer().bindProviderMapping(viewKey.revision(), providerEntryId, mappingText);
        }
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean isCurrentProviderDirectory(ProviderViewKey viewKey) {
        return this.visible
            && !this.mapping.directoryRefreshPending
            && Objects.equals(viewKey, currentViewKey());
    }

    private String getMappingText() {
        return this.mapping.text;
    }

    private static Rectangle getButtonBounds(GuiButton button) {
        return new Rectangle(button.x, button.y, button.width, button.height);
    }

    private boolean canStartDrag(Point mousePos) {
        if (!contains(getTitleBarBounds(this.bounds), mousePos.x(), mousePos.y())) {
            return false;
        }

        Point absoluteMouse = this.screenOrigin.move(mousePos.x(), mousePos.y());
        return getButtonAt(absoluteMouse) == null
            && !isMouseOverTextField(this.searchField, absoluteMouse)
            && !isMouseOverTextField(this.mappingField, absoluteMouse);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean isMouseOverTextField(AETextField field, Point absoluteMouse) {
        return field.getVisible() && field.isMouseOver(absoluteMouse.x(), absoluteMouse.y());
    }

    private boolean applyPendingOpenPositionReset() {
        if (!this.visible || !this.resetPositionOnOpen) {
            return false;
        }

        updateWindowSize();
        this.bounds.x = (this.parent.width - this.bounds.width) / 2 - this.parent.getGuiLeft();
        this.bounds.y = (this.parent.height - this.bounds.height) / 2 - this.parent.getGuiTop();
        clampToScreen();
        this.resetPositionOnOpen = false;
        return true;
    }

    private void clampToScreen() {
        updateWindowSize();
        int left = this.parent.getGuiLeft() + this.bounds.x;
        int top = this.parent.getGuiTop() + this.bounds.y;
        int maxLeft = Math.max(0, this.parent.width - this.bounds.width);
        int maxTop = Math.max(0, this.parent.height - this.bounds.height);
        this.bounds.x = Math.clamp(left, 0, maxLeft) - this.parent.getGuiLeft();
        this.bounds.y = Math.clamp(top, 0, maxTop) - this.parent.getGuiTop();
    }

    private void updateWindowSize() {
        this.bounds.width = getWindowWidth();
        this.bounds.height = getWindowHeight();
    }

    private static boolean isCtrlKeyDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
    }

    private static boolean isShiftKeyDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
    }

    public Collection<? extends GuiTextField> getTextFields() {
        return this.visible ? List.of(this.searchField, this.mappingField) : List.of();
    }

    private static final class StyleHolder {
        private static final GuiStyle STYLE = GuiStyleManager.loadStyleDoc(STYLE_PATH);
    }

    private static final class ButtonPressState {
        @Nullable
        private GuiButton pressedButton;

        void recordPressedButton(GuiButton pressedButton) {
            this.pressedButton = Objects.requireNonNull(pressedButton, "pressedButton");
        }

        void clearPressedButton() {
            this.pressedButton = null;
        }

        boolean hasPressedButton() {
            return this.pressedButton != null;
        }

        void releasePressedButton(boolean dragging, int mouseX, int mouseY) {
            GuiButton button = this.pressedButton;
            this.pressedButton = null;
            if (!dragging && button != null) {
                button.mouseReleased(mouseX, mouseY);
            }
        }
    }

    /**
     * State whose lifetime follows a directory request generation.
     */
    private static final class DirectoryState {
        private final ProviderDirectoryPageCache pageCache = new ProviderDirectoryPageCache();
        private final Map<Integer, Long> pendingPages = new HashMap<>();
        private int scrollOffset;
        private String searchText = "";
        private int activeWindowId = -1;
        private long nextRequestNonce;
        private long activeRequestNonce;
        private long activeRevision = -1;
        private int total = -1;
        private boolean searchRequestPending;
        private long searchChangedAtNanos;
        private boolean refreshPreservingButtons;
    }

    /**
     * State used only while mapping-capable provider rows are shown or managed.
     */
    private static final class MappingState {
        private final Map<Integer, Long> pendingPages = new HashMap<>();
        private final LinkedHashMap<Integer, List<String>> pages = new LinkedHashMap<>(16, 0.75F, true);
        private boolean enabled;
        private String text = "";
        @Nullable
        private ProviderEntry managedProvider;
        @Nullable
        private ProviderDirectoryPageRequest.Focus directoryFocus;
        private boolean directoryRefreshPending;
    }

    private enum TextInputTarget {
        NONE,
        SEARCH,
        MAPPING
    }

    public record ProviderEntry(long providerEntryId,
                                @Nullable AEItemKey icon,
                                @Nullable ProviderLocation location,
                                boolean hasMappingTarget,
                                String providerName,
                                int emptySlots,
                                int recipeTypeCount,
                                List<String> recipeTypeUids,
                                boolean acceptsProcessingPatterns) {

        public ProviderEntry {
            Objects.requireNonNull(providerName, "providerName");
            Objects.requireNonNull(recipeTypeUids, "recipeTypeUids");
            if (emptySlots < 0) {
                throw new IllegalArgumentException("Provider entry empty slot count must not be negative");
            }
            if (recipeTypeCount < 0 || recipeTypeUids.size() > recipeTypeCount) {
                throw new IllegalArgumentException("Invalid provider entry recipe type count");
            }
            if (recipeTypeUids.size() > PatternProviderMappingData.DIRECTORY_RECIPE_TYPE_PREVIEW_SIZE) {
                throw new IllegalArgumentException("Provider entry exceeds "
                    + PatternProviderMappingData.DIRECTORY_RECIPE_TYPE_PREVIEW_SIZE + " recipe type preview UIDs");
            }
            Set<String> uniqueRecipeTypeUids = new HashSet<>(recipeTypeUids.size());
            for (String recipeTypeUid : recipeTypeUids) {
                Objects.requireNonNull(recipeTypeUid, "recipe type UID");
                if (!uniqueRecipeTypeUids.add(recipeTypeUid)) {
                    throw new IllegalArgumentException(
                        "Provider entry contains duplicate recipe type UID " + recipeTypeUid);
                }
            }
            recipeTypeUids = List.copyOf(recipeTypeUids);
        }
    }

    private record ProviderViewKey(int windowId, long nonce, long revision, int firstVisibleRow,
                                   long managedProviderId) {
    }

    public record ProviderLocation(int dimensionId, BlockPos pos, @Nullable EnumFacing side) {
    }

    private static final class ProviderEntryButton extends TooltipButton {
        private final ProviderViewKey viewKey;
        @Nullable
        private final ProviderEntry providerEntry;

        private ProviderEntryButton(ITextComponent component, Runnable onPress,
                                       Supplier<List<ITextComponent>> tooltipSupplier,
                                       ProviderViewKey viewKey,
                                       @Nullable ProviderEntry providerEntry) {
            super(component, tooltipSupplier, onPress);
            this.viewKey = Objects.requireNonNull(viewKey, "viewKey");
            this.providerEntry = providerEntry;
        }

        static ProviderEntryButton provider(ITextComponent component, Runnable onPress,
                                               Supplier<List<ITextComponent>> tooltipSupplier,
                                               ProviderViewKey viewKey, ProviderEntry providerEntry) {
            Objects.requireNonNull(providerEntry, "providerEntry");
            return new ProviderEntryButton(component, onPress, tooltipSupplier, viewKey, providerEntry);
        }

        static ProviderEntryButton mapping(ITextComponent component, Runnable onPress,
                                              Supplier<List<ITextComponent>> tooltipSupplier,
                                              ProviderViewKey viewKey) {
            return new ProviderEntryButton(component, onPress, tooltipSupplier, viewKey, null);
        }

        ProviderViewKey viewKey() {
            return this.viewKey;
        }

        boolean isProviderRow() {
            return this.providerEntry != null;
        }

        @Nullable
        ProviderEntry providerEntry() {
            return this.providerEntry;
        }
    }

}
