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
import ae2.client.gui.widgets.AE2Button;
import ae2.client.gui.widgets.AETextField;
import ae2.client.gui.widgets.ITooltip;
import ae2.client.gui.widgets.Scrollbar;
import ae2.client.gui.widgets.SmallSquareButtonRenderer;
import ae2.client.gui.widgets.TooltipButton;
import ae2.container.AEBaseContainer;
import ae2.container.me.patternencode.IPatternProviderSelection;
import ae2.core.localization.ButtonToolTips;
import ae2.core.localization.GuiText;
import ae2.core.worlddata.PatternProviderMappingData;
import ae2.container.me.patternencode.ProviderDirectoryPage;
import ae2.container.me.patternencode.ProviderDirectoryPageRequest;
import ae2.container.me.patternencode.ProviderMappingPage;
import ae2.container.me.patternencode.ProviderPageLimits;
import ae2.crafting.execution.CraftingSupplierLocator;
import ae2.crafting.pattern.RecipeTypeUid;
import ae2.core.AELog;
import ae2.integration.Integrations;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Provider-select overlay shown on top of the current Pattern Encoding GUI.
 */
public final class GuiProviderSelect<C extends AEBaseContainer & IPatternProviderSelection> implements ICompositeWidget {

    public static final String WIDGET_ID = "providerSelectOverlay";
    private static final String STYLE_PATH = "/screens/provider_select.json";
    private static final String BACK_WIDGET = "back";
    private static final String SEARCH_WIDGET = "search";
    private static final String MAPPING_INPUT_WIDGET = "mappingInput";
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
    private static final long SEARCH_DEBOUNCE_NANOS = 150_000_000L;
    private static final TextComponentString EMPTY_MESSAGE = new TextComponentString("");
    private static final long WARNING_INTERVAL_NANOS = 10_000_000_000L;
    private static final AtomicLong LAST_INVALID_MAPPING_WARNING = new AtomicLong(Long.MIN_VALUE);

    private final AEBaseGui<C> parent;
    private final LongSupplier nanoTime;
    private final ProviderDirectoryPageCache pageCache = new ProviderDirectoryPageCache();
    private final ButtonPressState buttonPressState = new ButtonPressState();
    private final ObjectArrayList<GuiButton> buttons = new ObjectArrayList<>();
    private final Scrollbar scrollbar = new Scrollbar(Scrollbar.SMALL);
    private final Set<Integer> pendingDirectoryPages = new HashSet<>();
    private final Set<Integer> pendingMappingPages = new HashSet<>();
    private final LinkedHashMap<Integer, List<String>> mappingPages =
        new LinkedHashMap<>(16, 0.75F, true);
    private Rectangle bounds = new Rectangle(0, 0, DEFAULT_WIDTH, DEFAULT_HEIGHT);
    private Point screenOrigin = Point.ZERO;
    private boolean visible;
    private boolean dragging;
    private boolean scrollbarMouseCapture;
    private boolean resetPositionOnOpen = true;
    private Point dragOffset = Point.ZERO;
    private int page;
    private ProviderSelectTextFocusState textFocusState = ProviderSelectTextFocusState.none();
    private String searchText = "";
    private String mappingText = "";
    private int activeWindowId = -1;
    private long nextRequestNonce;
    private long activeRequestNonce;
    private long activeDirectoryRevision = -1;
    private int directoryTotal = -1;
    private boolean searchRequestPending;
    private long searchChangedAtNanos;
    @Nullable
    private AETextField searchField;
    @Nullable
    private AETextField mappingField;
    private ProviderEntry managedMappingProvider;
    @Nullable
    private ProviderDirectoryPageRequest.Focus managedDirectoryFocus;
    private boolean managedDirectoryRefreshPending;
    private boolean directoryRefreshPreservingButtons;

    public GuiProviderSelect(AEBaseGui<C> parent) {
        this(parent, System::nanoTime);
    }

    GuiProviderSelect(AEBaseGui<C> parent, LongSupplier nanoTime) {
        this.parent = Objects.requireNonNull(parent, "parent");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        this.scrollbar.setCaptureMouseWheel(false);
        this.scrollbar.setVisible(false);
    }

    public void open(String initialSearchText, String initialMappingText) {
        Objects.requireNonNull(initialSearchText, "initialSearchText");
        Objects.requireNonNull(initialMappingText, "initialMappingText");
        this.searchText = initialSearchText;
        this.mappingText = initialMappingText;
        this.textFocusState = ProviderSelectTextFocusState.none();
        exitMappingManagement();
        this.page = 0;
        if (!this.visible) {
            this.visible = true;
            this.dragging = false;
            this.scrollbarMouseCapture = false;
            this.dragOffset = Point.ZERO;
        }
        syncTextFieldsFromState();
        beginRequestGeneration(readHostDirectoryRevision(), true, false);
    }

    private void close() {
        this.buttonPressState.clearPressedButton();
        this.visible = false;
        this.dragging = false;
        this.scrollbarMouseCapture = false;
        this.page = 0;
        this.textFocusState = ProviderSelectTextFocusState.none();
        this.searchText = "";
        this.mappingText = "";
        exitMappingManagement();
        clearPageState();
        this.activeWindowId = -1;
        this.activeRequestNonce = 0;
        this.activeDirectoryRevision = -1;
        this.searchRequestPending = false;
        syncTextFieldsFromState();
        this.buttons.clear();
        this.scrollbar.setVisible(false);
    }

    private void closeOrExitMappingManagement() {
        if (this.managedMappingProvider == null) {
            close();
            return;
        }

        exitMappingManagement();
        this.page = 0;
        this.textFocusState = ProviderSelectTextFocusState.none();
        updateMappingInputVisibility();
        syncTextFieldFocusFromState();
        beginRequestGeneration(Math.max(this.activeDirectoryRevision, readHostDirectoryRevision()), true, false);
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
            throw new IllegalArgumentException("Provider select overlay size must not be negative");
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
        ensureTextFields();
        if (!applyPendingOpenPositionReset()) {
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
        ensureTextFields();
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

        int x = screenBounds.x + this.bounds.x;
        int y = screenBounds.y + this.bounds.y;

        GlStateManager.pushMatrix();
        GlStateManager.translate(0, 0, 350);
        try {
            BackgroundGenerator.draw(this.bounds.width, this.bounds.height, x, y);
            drawTitleText(Minecraft.getMinecraft(), x, y);
            drawManagedProviderTitleInfo(Minecraft.getMinecraft(), x, y);
            drawProviderDirectoryIcons(Minecraft.getMinecraft());
            for (GuiButton button : this.buttons) {
                button.drawButton(Minecraft.getMinecraft(), mouse.x(), mouse.y(), 0);
            }
            if (this.searchField != null && this.searchField.getVisible()) {
                this.searchField.drawTextBox();
            }
            if (this.mappingField != null && this.mappingField.getVisible()) {
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
        ensureTextFields();
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

        for (GuiButton widget : this.buttons) {
            if (widget.visible && contains(getButtonBounds(widget), absoluteMouse.x(), absoluteMouse.y())) {
                if (button != 0) {
                    return true;
                }
                this.buttonPressState.recordPressedButton(widget);
                playPressSound(widget);
                return true;
            }
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
        if (!contains(this.bounds, mousePos.x(), mousePos.y()) || !this.scrollbar.isVisible()) {
            return false;
        }

        Point absoluteMouse = this.screenOrigin.move(mousePos.x(), mousePos.y());
        if (this.scrollbar.onMouseWheel(absoluteMouse, delta)) {
            applyScrollbarScroll();
            return true;
        }
        return false;
    }

    @Override
    public void tick() {
        if (!this.visible) {
            return;
        }
        synchronizeHostDirectory();
        if (this.searchRequestPending
            && this.nanoTime.getAsLong() - this.searchChangedAtNanos >= SEARCH_DEBOUNCE_NANOS) {
            this.searchRequestPending = false;
            requestVisiblePages();
        }
        if (this.scrollbar.isVisible()) {
            this.scrollbar.tick();
            applyScrollbarScroll();
        }
    }

    @Override
    public boolean onKeyTyped(char typedChar, int keyCode) {
        if (!this.visible) {
            return false;
        }

        ensureTextFields();

        if (keyCode == Keyboard.KEY_ESCAPE && this.managedMappingProvider != null) {
            closeOrExitMappingManagement();
            return true;
        }

        if (keyCode == Keyboard.KEY_ESCAPE && clearFocusedTextInput()) {
            return true;
        }

        TextInputTarget textInputTarget = getFocusedTextInputTarget();
        if (textInputTarget == TextInputTarget.MAPPING
            && this.mappingField != null
            && this.mappingField.textboxKeyTyped(typedChar, keyCode)) {
            updateMappingText();
            rebuildButtonsIfMinecraftAvailable();
            return true;
        }
        if (textInputTarget == TextInputTarget.SEARCH
            && this.searchField != null
            && this.searchField.textboxKeyTyped(typedChar, keyCode)) {
            acceptSearchTextInput(nonNullText(this.searchField, "search"));
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
        for (GuiButton button : this.buttons) {
            if (button instanceof ITooltip tooltip
                && tooltip.isTooltipAreaVisible()
                && contains(tooltip.getTooltipArea(), absoluteMouse.x(), absoluteMouse.y())) {
                return new Tooltip(tooltip.getTooltipMessage());
            }
        }
        if (this.managedMappingProvider != null
            && contains(getManagedProviderTitleInfoTooltipArea(), absoluteMouse.x(), absoluteMouse.y())) {
            List<ITextComponent> tooltip = getManagedProviderTitleTooltip();
            if (!tooltip.isEmpty()) {
                return new Tooltip(tooltip);
            }
        }
        return null;
    }

    private void drawProviderDirectoryIcons(Minecraft minecraft) {
        if (this.managedMappingProvider != null) {
            return;
        }
        for (GuiButton button : this.buttons) {
            if (!(button instanceof ProviderSnapshotButton row) || !row.isProviderRow()) {
                continue;
            }
            ProviderEntry entry = row.providerEntry();
            if (entry != null && entry.icon() != null) {
                AEKeyRendering.drawInGui(minecraft, row.x - 18, row.y, entry.icon());
            }
        }
    }

    @Nullable
    private Tooltip getProviderDirectoryIconTooltip(Point absoluteMouse) {
        if (this.managedMappingProvider != null) {
            return null;
        }
        for (GuiButton button : this.buttons) {
            if (!(button instanceof ProviderSnapshotButton row) || !row.isProviderRow()) {
                continue;
            }
            ProviderEntry entry = row.providerEntry();
            if (entry != null && entry.icon() != null
                && contains(new Rectangle(row.x - 18, row.y, 16, row.height), absoluteMouse.x(), absoluteMouse.y())) {
                List<ITextComponent> tooltip = new ArrayList<>(AEKeyRendering.getTooltip(entry.icon()));
                appendProviderLocationTooltip(tooltip, entry);
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
        return this.managedMappingProvider != null
            ? GuiText.ProviderSelectMappingManagement.text()
            : new TextComponentTranslation("gui.ae2.ProviderSelect");
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
        return new Rectangle(
            this.screenOrigin.x() + this.bounds.x + getManagedProviderTitleInfoIconLeft(minecraft),
            this.screenOrigin.y() + this.bounds.y + TITLE_INFO_ICON_TOP,
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
        ProviderEntry providerEntry = this.managedMappingProvider;
        return providerEntry == null ? null : providerEntry.icon();
    }

    private int getTitleInfoStart(@Nullable Minecraft minecraft) {
        return TITLE_X + getTitleTextWidth(minecraft) + TITLE_INFO_GAP;
    }

    private int getTitleTextWidth(@Nullable Minecraft minecraft) {
        if (minecraft != null) {
            return minecraft.fontRenderer.getStringWidth(getTitleText().getFormattedText());
        }
        return this.managedMappingProvider != null
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
        WidgetStyle closeStyle = style().getWidget(BACK_WIDGET);
        Point closePos = closeStyle.resolve(new Rectangle(0, 0, this.bounds.width, this.bounds.height));
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
        ProviderEntry providerEntry = this.managedMappingProvider;
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
        if (!entry.hasMappingTarget()) {
            tooltip.add(ButtonToolTips.ProviderSelectEntryUpload.text());
            return tooltip;
        }
        tooltip.addAll(getProviderEntryTooltip(this.mappingText));
        tooltip.add(ButtonToolTips.ProviderSelectMappingManage.text());
        return tooltip;
    }

    private static String getRecipeTypeDisplayName(String uid) {
        String title = Integrations.hei().getRecipeCategoryTitle(uid);
        return title == null || title.isEmpty() ? uid : title;
    }

    private static void appendProviderLocationTooltip(List<ITextComponent> tooltip, ProviderEntry entry) {
        ProviderLocation location = entry.location();
        if (location != null) {
            tooltip.add(GuiText.CraftingTreeLocationInDimension.text(location.pos().getX(), location.pos().getY(),
                location.pos().getZ(), CraftingSupplierLocator.getDimensionName(location.dimensionId())));
        }
    }

    private static List<ITextComponent> getProviderEntryTooltip(String mappingInputText) {
        Objects.requireNonNull(mappingInputText, "mappingInputText");

        List<ITextComponent> tooltip = new ArrayList<>();
        tooltip.add(ButtonToolTips.ProviderSelectEntryUpload.text());
        String recipeTypeUid = RecipeTypeUid.normalize(mappingInputText);
        if (recipeTypeUid != null) {
            tooltip.add(ButtonToolTips.ProviderSelectMappingBind.text(new TextComponentString(recipeTypeUid)));
            tooltip.add(ButtonToolTips.ProviderSelectMappingBindAndUpload.text());
        }
        tooltip.add(ButtonToolTips.ProviderSelectMappingUnbind.text());
        return tooltip;
    }

    private static ITextComponent getMappingAddTooltip(String mappingInputText, String providerName) {
        Objects.requireNonNull(mappingInputText, "mappingInputText");
        Objects.requireNonNull(providerName, "providerName");
        String trimmedMappingInput = RecipeTypeUid.normalize(mappingInputText);
        if (trimmedMappingInput == null) {
            return ButtonToolTips.ProviderSelectMappingInputRequired.text();
        }
        return ButtonToolTips.ProviderSelectMappingAdd.text(
            new TextComponentString(trimmedMappingInput),
            new TextComponentString(providerName));
    }

    private static int getMaxScrollOffset(int entryCount) {
        if (entryCount < 0) {
            throw new IllegalArgumentException("Provider select entry count must not be negative");
        }
        return Math.max(0, entryCount - PAGE_SIZE);
    }

    private static int getMaxManagedMappingScrollOffset(int entryCount) {
        if (entryCount < 0) {
            throw new IllegalArgumentException("Provider select managed mapping entry count must not be negative");
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

    private void ensureTextFields() {
        if (this.searchField != null && this.mappingField != null) {
            return;
        }

        Minecraft minecraft = this.parent.getMinecraft();
        if (minecraft == null) {
            return;
        }

        GuiStyle style = style();
        this.searchField = new AETextField(style, minecraft.fontRenderer, 0, 0, 0, 0);
        this.searchField.setEnableBackgroundDrawing(false);
        this.searchField.setMaxStringLength(ProviderPageLimits.MAX_QUERY_UTF16_LENGTH);
        this.searchField.setTextColor(0xFFFFFF);
        this.searchField.setPlaceholder(GuiText.SearchPlaceholder.getLocal());
        this.searchField.setTooltipMessage(Collections.singletonList(GuiText.SearchTooltip.text()));

        this.mappingField = new AETextField(style, minecraft.fontRenderer, 0, 0, 0, 0);
        this.mappingField.setEnableBackgroundDrawing(false);
        this.mappingField.setMaxStringLength(RecipeTypeUid.MAX_UTF16_LENGTH);
        this.mappingField.setTextColor(0xFFFFFF);
        this.mappingField.setPlaceholder(GuiText.ProviderSelectMappingInputPlaceholder.text());
        this.mappingField.setTooltipMessage(Collections.singletonList(ButtonToolTips.ProviderSelectMappingInput.text()));
        syncTextFieldsFromState();
        moveTextFields();
        updateMappingInputVisibility();
    }

    private void syncTextFieldsFromState() {
        if (this.searchField != null) {
            this.searchField.setText(this.searchText);
        }
        if (this.mappingField != null) {
            this.mappingField.setText(this.mappingText);
            updateMappingInputVisibility();
        }
        syncTextFieldFocusFromState();
    }

    private boolean updateSearchText() {
        if (this.searchField == null) {
            return false;
        }
        return acceptSearchTextInput(nonNullText(this.searchField, "search"));
    }

    boolean acceptSearchTextInput(String text) {
        String boundedText = ProviderPageLimits.requireBoundedText(
            "provider directory query",
            text,
            ProviderPageLimits.MAX_QUERY_UTF16_LENGTH,
            ProviderPageLimits.MAX_QUERY_UTF8_BYTES);
        if (this.searchText.equals(boundedText)) {
            return false;
        }
        this.searchText = boundedText;
        scheduleSearchRequest();
        return true;
    }

    private boolean updateMappingText() {
        if (this.mappingField == null) {
            return false;
        }
        String text = nonNullText(this.mappingField, "mapping");
        if (this.mappingText.equals(text)) {
            return false;
        }
        this.mappingText = text;
        return true;
    }

    private static String nonNullText(AETextField field, String fieldName) {
        String text = field.getText();
        if (text == null) {
            throw new IllegalStateException("Unexpected null text from provider overlay " + fieldName + " field");
        }
        return text;
    }

    private void updateMappingInputVisibility() {
        if (this.mappingField != null) {
            this.mappingField.setVisible(true);
        }
    }

    private TextInputTarget getFocusedTextInputTarget() {
        return ProviderSelectTextFocusState.resolveKeyTarget(
            this.searchField != null && this.searchField.getVisible(),
            this.searchField != null && this.searchField.isFocused(),
            this.mappingField != null && this.mappingField.getVisible(),
            this.mappingField != null && this.mappingField.isFocused());
    }

    private void syncTextFieldFocusFromState() {
        if (this.searchField != null) {
            this.searchField.setFocused(this.textFocusState.searchFocused());
        }
        if (this.mappingField != null) {
            this.mappingField.setFocused(this.textFocusState.mappingFocused());
            if (this.textFocusState.mappingFocused()) {
                this.mappingField.setCursorPositionEnd();
            }
        }
    }

    private void focusSearchInput() {
        this.textFocusState = ProviderSelectTextFocusState.search();
        syncTextFieldFocusFromState();
    }

    private void focusMappingInput() {
        this.textFocusState = ProviderSelectTextFocusState.mapping();
        syncTextFieldFocusFromState();
    }

    private boolean clearFocusedTextInput() {
        if (!this.textFocusState.hasFocusedInput()) {
            return false;
        }
        this.textFocusState = ProviderSelectTextFocusState.none();
        syncTextFieldFocusFromState();
        return true;
    }

    private void rebuildButtons() {
        if (this.directoryRefreshPreservingButtons && this.managedMappingProvider == null) {
            requestVisiblePages();
            repositionPreservedDirectoryButtons();
            return;
        }
        this.buttons.clear();
        if (!this.visible) {
            return;
        }

        requestVisiblePages();

        CloseButton closeButton = new CloseButton(this::closeOrExitMappingManagement, this::getCloseButtonIcon,
            this::getCloseButtonTooltip);
        moveButton(closeButton, BACK_WIDGET);
        this.buttons.add(closeButton);

        if (this.managedMappingProvider != null) {
            rebuildMappingManagementButtons();
            return;
        }

        if (this.directoryTotal >= 0) {
            updateScrollbar(this.directoryTotal);
        }
        ProviderViewKey viewKey = currentViewKey();
        int start = this.page;
        for (int i = 0; i < PAGE_SIZE; i++) {
            int visibleIndex = start + i;
            ProviderEntry entry = getDirectoryEntry(visibleIndex);
            if (entry != null) {
                ProviderSnapshotButton entryButton = ProviderSnapshotButton.provider(
                    new TextComponentString(entry.providerName() + " (" + entry.emptySlots() + ")"),
                    () -> handleProviderEntryLeftClick(viewKey, entry),
                    () -> getProviderRowTooltip(entry),
                    viewKey,
                    entry);
                moveButton(entryButton, ENTRY_WIDGET_PREFIX + i);
                // Keep a fixed icon gutter outside the shortened action button.
                entryButton.x += 18;
                entryButton.width -= 18;
                this.buttons.add(entryButton);
            }
        }

        moveTextFields();
        addFooterButtons();
    }

    private void repositionPreservedDirectoryButtons() {
        int providerRow = 0;
        for (GuiButton button : this.buttons) {
            if (button instanceof CloseButton) {
                moveButton(button, BACK_WIDGET);
            } else if (button instanceof ProviderSnapshotButton snapshotButton && snapshotButton.isProviderRow()) {
                moveButton(button, ENTRY_WIDGET_PREFIX + providerRow++);
                button.x += 18;
                button.width -= 18;
            } else if (button instanceof IconTooltipsButton) {
                moveButton(button, RELOAD_WIDGET);
            }
        }
        moveTextFields();
        moveScrollbar();
    }

    private void rebuildMappingManagementButtons() {
        ProviderEntry providerEntry = this.managedMappingProvider;
        if (providerEntry == null) {
            updateManagedMappingScrollbar(0);
            return;
        }

        int mappingEntryCount = Math.incrementExact(providerEntry.recipeTypeCount());
        updateManagedMappingScrollbar(mappingEntryCount);
        requestVisibleMappingPages(providerEntry);
        ProviderViewKey viewKey = currentViewKey();
        int start = this.page;
        for (int i = 0; i < PAGE_SIZE; i++) {
            int visibleIndex = start + i;
            String recipeTypeUid = getCachedMappingUid(visibleIndex);
            if (recipeTypeUid != null) {
                ProviderSnapshotButton entryButton = ProviderSnapshotButton.mapping(
                    new TextComponentString(getRecipeTypeDisplayName(recipeTypeUid)),
                    () -> unbindManagedRecipeType(viewKey, providerEntry.inventoryId(), recipeTypeUid),
                    () -> List.of(new TextComponentString(recipeTypeUid),
                        ButtonToolTips.ProviderSelectMappingUnbindRecipe.text(new TextComponentString(recipeTypeUid))),
                    viewKey,
                    recipeTypeUid);
                moveButton(entryButton, ENTRY_WIDGET_PREFIX + i);
                this.buttons.add(entryButton);
            } else if (visibleIndex == providerEntry.recipeTypeCount()) {
                ProviderSnapshotButton entryButton = ProviderSnapshotButton.mapping(
                    GuiText.ProviderSelectMappingAdd.text(),
                    () -> addManagedRecipeType(viewKey, providerEntry.inventoryId()),
                    () -> Collections.singletonList(getMappingAddTooltip(this.mappingText,
                        providerEntry.providerName())),
                    viewKey,
                    null);
                moveButton(entryButton, ENTRY_WIDGET_PREFIX + i);
                this.buttons.add(entryButton);
            }
        }

        moveTextFields();
        addFooterButtons();
    }

    private void addFooterButtons() {
        ProviderViewKey viewKey = currentViewKey();
        IconTooltipsButton reloadButton = new IconTooltipsButton(
            Icon.ADVANCED_MEMORY_CARD_REFRESH,
            () -> reloadProviderMappings(viewKey),
            () -> Collections.singletonList(ButtonToolTips.ProviderSelectMappingReload.text()));
        moveButton(reloadButton, RELOAD_WIDGET);
        this.buttons.add(reloadButton);
    }

    private Icon getCloseButtonIcon() {
        return this.managedMappingProvider != null ? Icon.BACK : Icon.CLEAR;
    }

    private ITextComponent getCloseButtonTooltip() {
        return this.managedMappingProvider != null ? GuiText.CraftingTreeBack.text() : GuiText.Close.text();
    }

    private void moveButton(GuiButton button, String widgetId) {
        WidgetStyle widgetStyle = style().getWidget(widgetId);
        Point pos = widgetStyle.resolve(new Rectangle(0, 0, this.bounds.width, this.bounds.height));
        button.x = this.screenOrigin.x() + this.bounds.x + pos.x();
        button.y = this.screenOrigin.y() + this.bounds.y + pos.y();
        button.width = widgetStyle.getWidth() != 0 ? widgetStyle.getWidth() : button.width;
        button.height = widgetStyle.getHeight() != 0 ? widgetStyle.getHeight() : button.height;
    }

    private void moveTextFields() {
        moveTextField(this.searchField, SEARCH_WIDGET);
        moveTextField(this.mappingField, MAPPING_INPUT_WIDGET);
    }

    private void updateScrollbar(int visibleEntryCount) {
        moveScrollbar();
        int maxScroll = getMaxScrollOffset(visibleEntryCount);
        int scroll = Math.clamp(this.page, 0, maxScroll);
        this.page = scroll;
        this.scrollbar.setRange(0, maxScroll, 1);
        this.scrollbar.setCurrentScroll(scroll);
        this.scrollbar.setVisible(visibleEntryCount > PAGE_SIZE);
    }

    private void updateManagedMappingScrollbar(int visibleEntryCount) {
        moveScrollbar();
        int maxScroll = getMaxManagedMappingScrollOffset(visibleEntryCount);
        int scroll = Math.clamp(this.page, 0, maxScroll);
        this.page = scroll;
        this.scrollbar.setRange(0, maxScroll, PAGE_SIZE);
        this.scrollbar.setCurrentScroll(scroll);
        this.scrollbar.setVisible(visibleEntryCount > PAGE_SIZE);
    }

    private void moveScrollbar() {
        WidgetStyle widgetStyle = style().getWidget(SCROLLBAR_WIDGET);
        Point pos = widgetStyle.resolve(new Rectangle(0, 0, this.bounds.width, this.bounds.height));
        this.scrollbar.setPosition(this.screenOrigin.move(this.bounds.x + pos.x(), this.bounds.y + pos.y()));
        this.scrollbar.setSize(widgetStyle.getWidth(), widgetStyle.getHeight());
    }

    private void moveTextField(@Nullable AETextField field, String widgetId) {
        if (field == null) {
            return;
        }
        WidgetStyle widgetStyle = style().getWidget(widgetId);
        Point pos = widgetStyle.resolve(new Rectangle(0, 0, this.bounds.width, this.bounds.height));
        field.move(this.screenOrigin.move(this.bounds.x + pos.x(), this.bounds.y + pos.y()));
        field.resize(widgetStyle.getWidth(), widgetStyle.getHeight());
    }

    public void receiveProviderDirectoryPage(ProviderDirectoryPage page) {
        Objects.requireNonNull(page, "page");
        if (!this.visible
            || page.windowId() != this.activeWindowId
            || page.nonce() != this.activeRequestNonce
            || page.directoryRevision() < this.activeDirectoryRevision) {
            return;
        }

        if (this.managedMappingProvider != null) {
            receiveManagedProviderDirectoryPage(page);
            return;
        }

        if (page.directoryRevision() > this.activeDirectoryRevision) {
            this.activeDirectoryRevision = page.directoryRevision();
            clearPageState();
        }
        ProviderDirectoryPageCache.DirectoryPageView existing = this.pageCache.getDirectoryPage(
            page.windowId(), page.nonce(), page.directoryRevision(), page.page());
        this.pendingDirectoryPages.remove(page.page());
        if (existing != null) {
            return;
        }

        this.pageCache.put(page);
        this.directoryTotal = page.total();
        if (this.directoryRefreshPreservingButtons && !areVisibleDirectoryPagesLoaded()) {
            requestVisiblePages();
            return;
        }
        this.directoryRefreshPreservingButtons = false;
        this.buttonPressState.clearPressedButton();
        rebuildButtons();
    }

    /** Mapping pages are only accepted for the provider currently being managed. */
    public void receiveProviderMappingPage(ProviderMappingPage page) {
        Objects.requireNonNull(page, "page");
        ProviderEntry provider = this.managedMappingProvider;
        if (!this.visible || provider == null || page.windowId() != this.activeWindowId
            || page.nonce() != this.activeRequestNonce || page.directoryRevision() != this.activeDirectoryRevision
            || page.providerId() != provider.inventoryId()) {
            return;
        }
        this.pendingMappingPages.remove(page.page());
        this.mappingPages.put(page.page(), page.recipeTypeUids());
        while (this.mappingPages.size() > MAX_MAPPING_CACHE_PAGES) {
            this.mappingPages.remove(this.mappingPages.keySet().iterator().next());
        }
        this.managedMappingProvider = new ProviderEntry(provider.inventoryId(), provider.icon(), provider.location(),
            provider.hasMappingTarget(), provider.providerName(), provider.emptySlots(), page.total(),
            provider.recipeTypeUids(), provider.acceptsProcessingPatterns());
        rebuildButtons();
    }

    @Nullable
    private String getCachedMappingUid(int index) {
        if (index < 0) {
            return null;
        }
        List<String> mappingPage = this.mappingPages.get(index / MAPPING_PROTOCOL_PAGE_SIZE);
        int pageIndex = index % MAPPING_PROTOCOL_PAGE_SIZE;
        return mappingPage != null && pageIndex < mappingPage.size() ? mappingPage.get(pageIndex) : null;
    }

    private void requestVisibleMappingPages(ProviderEntry provider) {
        int total = provider.recipeTypeCount();
        if (total == 0) {
            return;
        }
        int first = this.page;
        int last = Math.min(total - 1, first + PAGE_SIZE - 1);
        for (int protocolPage = first / MAPPING_PROTOCOL_PAGE_SIZE;
             protocolPage <= last / MAPPING_PROTOCOL_PAGE_SIZE; protocolPage++) {
            if (this.mappingPages.containsKey(protocolPage) || !this.pendingMappingPages.add(protocolPage)) {
                continue;
            }
            this.parent.getContainer().requestProviderMappingPage(this.activeRequestNonce, this.activeDirectoryRevision,
                provider.inventoryId(), protocolPage);
        }
    }

    private void receiveManagedProviderDirectoryPage(ProviderDirectoryPage page) {
        ProviderDirectoryPageRequest.Focus focus = this.managedDirectoryFocus;
        if (!this.managedDirectoryRefreshPending || focus == null || page.page() != 0) {
            return;
        }

        this.pendingDirectoryPages.remove(0);
        this.activeDirectoryRevision = page.directoryRevision();
        ProviderEntry refreshedProvider = page.entries().isEmpty()
            ? null
            : ProviderDirectoryPageCache.convert(page.entries().getFirst());
        if (refreshedProvider == null
            || !matchesManagedDirectoryFocus(refreshedProvider, focus)
            || !refreshedProvider.hasMappingTarget()) {
            returnToDirectoryAfterManagedProviderDisappeared();
            return;
        }

        this.managedMappingProvider = refreshedProvider;
        clearMappingPageState();
        this.managedDirectoryFocus = null;
        this.managedDirectoryRefreshPending = false;
        this.buttonPressState.clearPressedButton();
        rebuildButtons();
    }

    private static boolean matchesManagedDirectoryFocus(ProviderEntry entry,
                                                        ProviderDirectoryPageRequest.Focus focus) {
        if (entry.inventoryId() == focus.providerId()) {
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
        this.page = 0;
        this.textFocusState = ProviderSelectTextFocusState.none();
        updateMappingInputVisibility();
        syncTextFieldFocusFromState();
        this.searchRequestPending = false;
        beginRequestGeneration(Math.max(this.activeDirectoryRevision, readHostDirectoryRevision()), true, false);
    }

    private void synchronizeHostDirectory() {
        if (!this.visible) {
            return;
        }
        int windowId = readHostWindowId();
        long revision = readHostDirectoryRevision();
        if (windowId != this.activeWindowId) {
            this.searchRequestPending = false;
            if (this.managedMappingProvider != null) {
                returnToDirectoryForNewRevision(revision);
                return;
            }
            beginRequestGeneration(revision, true, false);
            return;
        }
        if (revision > this.activeDirectoryRevision) {
            this.searchRequestPending = false;
            if (this.managedMappingProvider != null) {
                beginManagedDirectoryRefresh(revision, true);
                return;
            }
            beginRequestGeneration(revision, true, true);
        }
    }

    private void returnToDirectoryForNewRevision(long revision) {
        exitMappingManagement();
        this.page = 0;
        this.textFocusState = ProviderSelectTextFocusState.none();
        updateMappingInputVisibility();
        syncTextFieldFocusFromState();
        this.searchRequestPending = false;
        beginRequestGeneration(revision, true, false);
    }

    private int readHostWindowId() {
        int windowId = this.parent.getContainer().windowId;
        if (windowId < 0) {
            throw new IllegalStateException("Provider-select host window id must not be negative");
        }
        return windowId;
    }

    private long readHostDirectoryRevision() {
        long revision = this.parent.getContainer().getProviderDirectoryRevision();
        if (revision < 0) {
            throw new IllegalStateException("Provider-select host directory revision must not be negative");
        }
        return revision;
    }

    private void beginRequestGeneration(long revision, boolean requestImmediately,
                                        boolean preserveVisibleButtons) {
        if (revision < 0) {
            throw new IllegalArgumentException("Provider-select directory revision must not be negative");
        }
        int windowId = readHostWindowId();
        boolean windowChanged = windowId != this.activeWindowId;
        this.activeWindowId = windowId;
        if (windowChanged) {
            this.page = 0;
        }
        this.activeDirectoryRevision = windowChanged
            ? revision
            : Math.max(revision, this.activeDirectoryRevision);
        this.activeRequestNonce = incrementRequestNonce();
        clearPageState();
        clearMappingPageState();
        this.buttonPressState.clearPressedButton();
        this.directoryRefreshPreservingButtons = preserveVisibleButtons && !this.buttons.isEmpty();
        if (requestImmediately) {
            this.searchRequestPending = false;
            requestVisiblePages();
        }
        rebuildButtons();
    }

    private void beginManagedDirectoryRefresh(long revision, boolean requestImmediately) {
        if (revision < 0) {
            throw new IllegalArgumentException("Provider-select directory revision must not be negative");
        }
        ProviderEntry provider = Objects.requireNonNull(
            this.managedMappingProvider, "managed mapping provider");
        int windowId = readHostWindowId();
        if (windowId != this.activeWindowId) {
            returnToDirectoryForNewRevision(revision);
            return;
        }

        this.activeDirectoryRevision = Math.max(revision, this.activeDirectoryRevision);
        this.activeRequestNonce = incrementRequestNonce();
        clearPageState();
        clearMappingPageState();
        this.buttonPressState.clearPressedButton();
        this.directoryRefreshPreservingButtons = false;
        this.managedDirectoryFocus = createManagedDirectoryFocus(provider);
        this.managedDirectoryRefreshPending = true;
        if (requestImmediately) {
            this.searchRequestPending = false;
            requestVisiblePages();
        }
    }

    private static ProviderDirectoryPageRequest.Focus createManagedDirectoryFocus(ProviderEntry provider) {
        ProviderLocation location = provider.location();
        if (!provider.hasMappingTarget() || location == null) {
            throw new IllegalStateException("Managed provider must have a stable mapping location");
        }
        return new ProviderDirectoryPageRequest.Focus(
            provider.inventoryId(),
            location.dimensionId(),
            location.pos().toLong(),
            sideOrdinal(location.side()));
    }

    private static int sideOrdinal(@Nullable EnumFacing side) {
        return side == null ? -1 : side.ordinal();
    }

    private long incrementRequestNonce() {
        this.nextRequestNonce = Math.incrementExact(this.nextRequestNonce);
        if (this.nextRequestNonce <= 0) {
            throw new IllegalStateException("Provider-select request nonce space exhausted");
        }
        return this.nextRequestNonce;
    }

    private void clearPageState() {
        this.pageCache.clear();
        this.pendingDirectoryPages.clear();
        this.directoryTotal = -1;
    }

    private void clearMappingPageState() {
        this.mappingPages.clear();
        this.pendingMappingPages.clear();
    }

    private void scheduleSearchRequest() {
        this.page = 0;
        this.searchRequestPending = true;
        this.searchChangedAtNanos = this.nanoTime.getAsLong();
        if (this.managedMappingProvider != null && this.managedDirectoryRefreshPending) {
            beginManagedDirectoryRefresh(
                Math.max(this.activeDirectoryRevision, readHostDirectoryRevision()), false);
            return;
        }
        beginRequestGeneration(Math.max(this.activeDirectoryRevision, readHostDirectoryRevision()), false, false);
    }

    private void requestVisiblePages() {
        if (!this.visible || this.searchRequestPending || this.activeRequestNonce <= 0) {
            return;
        }
        if (this.managedMappingProvider != null) {
            requestManagedProviderDirectoryPage();
            return;
        }
        requestVisibleDirectoryPages();
    }

    private void requestManagedProviderDirectoryPage() {
        ProviderDirectoryPageRequest.Focus focus = this.managedDirectoryFocus;
        if (!this.managedDirectoryRefreshPending || focus == null || !this.pendingDirectoryPages.add(0)) {
            return;
        }
        this.parent.getContainer().requestProviderDirectoryPage(this.activeRequestNonce, this.searchText, 0, focus);
    }

    private void requestVisibleDirectoryPages() {
        if (this.directoryTotal < 0) {
            requestDirectoryPage(this.page / ProviderPageLimits.PAGE_SIZE);
            return;
        }
        if (this.directoryTotal == 0) {
            return;
        }
        int firstVisible = Math.clamp(this.page, 0, getMaxScrollOffset(this.directoryTotal));
        int lastVisible = Math.min(this.directoryTotal - 1, firstVisible + PAGE_SIZE - 1);
        requestDirectoryPage(firstVisible / ProviderPageLimits.PAGE_SIZE);
        requestDirectoryPage(lastVisible / ProviderPageLimits.PAGE_SIZE);
    }

    private boolean areVisibleDirectoryPagesLoaded() {
        if (this.directoryTotal < 0) {
            return false;
        }
        if (this.directoryTotal == 0) {
            return true;
        }
        int firstVisible = Math.clamp(this.page, 0, getMaxScrollOffset(this.directoryTotal));
        int lastVisible = Math.min(this.directoryTotal - 1, firstVisible + PAGE_SIZE - 1);
        return hasDirectoryPage(firstVisible / ProviderPageLimits.PAGE_SIZE)
            && hasDirectoryPage(lastVisible / ProviderPageLimits.PAGE_SIZE);
    }

    private boolean hasDirectoryPage(int protocolPage) {
        return this.pageCache.getDirectoryPage(
            this.activeWindowId, this.activeRequestNonce, this.activeDirectoryRevision, protocolPage) != null;
    }

    private void requestDirectoryPage(int protocolPage) {
        if (this.pageCache.getDirectoryPage(this.activeWindowId, this.activeRequestNonce,
            this.activeDirectoryRevision, protocolPage) != null || !this.pendingDirectoryPages.add(protocolPage)) {
            return;
        }
        this.parent.getContainer().requestProviderDirectoryPage(this.activeRequestNonce, this.searchText, protocolPage, null);
    }

    @Nullable
    private ProviderEntry getDirectoryEntry(int index) {
        if (index < 0 || this.directoryTotal < 0 || index >= this.directoryTotal) {
            return null;
        }
        int protocolPage = index / ProviderPageLimits.PAGE_SIZE;
        ProviderDirectoryPageCache.DirectoryPageView page = this.pageCache.getDirectoryPage(
            this.activeWindowId, this.activeRequestNonce, this.activeDirectoryRevision, protocolPage);
        int pageOffset = index % ProviderPageLimits.PAGE_SIZE;
        return page == null || pageOffset >= page.entries().size() ? null : page.entries().get(pageOffset);
    }

    private ProviderViewKey currentViewKey() {
        long providerId = this.managedMappingProvider == null
            ? Long.MIN_VALUE
            : this.managedMappingProvider.inventoryId();
        return new ProviderViewKey(this.activeWindowId, this.activeRequestNonce, this.activeDirectoryRevision,
            this.page, providerId);
    }

    private void applyScrollbarScroll() {
        int scroll = this.scrollbar.getCurrentScroll();
        if (this.page == scroll) {
            return;
        }
        this.page = scroll;
        this.buttonPressState.clearPressedButton();
        rebuildButtons();
    }

    private boolean handleTextFieldMouseDown(int mouseX, int mouseY, int button) {
        if (this.searchField != null && this.searchField.getVisible() && this.searchField.isMouseOver(mouseX, mouseY)) {
            focusSearchInput();
            if (button == 1) {
                this.searchField.setText("");
                updateSearchText();
                rebuildButtons();
                return true;
            }
            return this.searchField.mouseClicked(mouseX, mouseY, button);
        }
        if (this.mappingField != null
            && this.mappingField.getVisible()
            && this.mappingField.isMouseOver(mouseX, mouseY)) {
            focusMappingInput();
            if (button == 1) {
                this.mappingField.setText("");
                this.mappingText = "";
                rebuildButtons();
                return true;
            }
            return this.mappingField.mouseClicked(mouseX, mouseY, button);
        }
        return false;
    }

    private boolean handleEntryRightClick(Point mousePos) {
        if (this.managedMappingProvider != null) {
            return true;
        }
        Point absoluteMouse = this.screenOrigin.move(mousePos.x(), mousePos.y());
        for (GuiButton widget : this.buttons) {
            if (!(widget instanceof ProviderSnapshotButton entryButton)
                || !entryButton.isProviderRow()
                || !entryButton.visible
                || !entryButton.enabled
                || !entryButton.viewKey().equals(currentViewKey())
                || !contains(getButtonBounds(entryButton), absoluteMouse.x(), absoluteMouse.y())) {
                continue;
            }

            ProviderEntry entry = Objects.requireNonNull(entryButton.providerEntry(), "providerEntry");
            if (isShiftKeyDown()) {
                unbindProviderMapping(entryButton.viewKey(), entry);
                return true;
            }
            if (!getTrimmedMappingText().isEmpty()) {
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

    private void handleProviderEntryLeftClick(ProviderViewKey viewKey, ProviderEntry entry) {
        if (!isCurrentProviderSnapshot(viewKey)) {
            return;
        }
        if (isCtrlKeyDown()) {
            if (entry.hasMappingTarget()) {
                enterMappingManagement(entry);
            }
            return;
        }
        if (isShiftKeyDown()) {
            String mappingText = getTrimmedMappingText();
            if (entry.hasMappingTarget() && !mappingText.isEmpty()) {
                this.parent.getContainer().bindAndUploadProcessingPatternToProvider(entry.inventoryId(), mappingText);
                close();
                return;
            }
            uploadToProvider(viewKey, entry);
            return;
        }

        uploadToProvider(viewKey, entry);
    }

    private void uploadToProvider(ProviderViewKey viewKey, ProviderEntry entry) {
        if (!isCurrentProviderSnapshot(viewKey)) {
            return;
        }
        this.parent.getContainer().uploadProcessingPatternToProvider(entry.inventoryId());
        close();
    }

    private void bindProviderMapping(ProviderViewKey viewKey, ProviderEntry entry) {
        if (!isCurrentProviderSnapshot(viewKey)) {
            return;
        }
        String mappingText = getTrimmedMappingText();
        if (entry.hasMappingTarget() && !mappingText.isEmpty()) {
            this.parent.getContainer().bindProviderMapping(entry.inventoryId(), mappingText);
        }
    }

    private void unbindProviderMapping(ProviderViewKey viewKey, ProviderEntry entry) {
        if (!isCurrentProviderSnapshot(viewKey)) {
            return;
        }
        if (entry.hasMappingTarget()) {
            this.parent.getContainer().unbindProviderMapping(entry.inventoryId());
        }
    }

    private void reloadProviderMappings(ProviderViewKey viewKey) {
        if (!isCurrentProviderSnapshot(viewKey)) {
            return;
        }
        this.parent.getContainer().reloadAllCurrentProviders();
    }

    private void enterMappingManagement(ProviderEntry entry) {
        Objects.requireNonNull(entry, "entry");
        if (!entry.hasMappingTarget()) {
            return;
        }
        this.managedMappingProvider = entry;
        clearMappingPageState();
        this.page = 0;
        this.textFocusState = ProviderSelectTextFocusState.none();
        updateMappingInputVisibility();
        syncTextFieldFocusFromState();
        this.buttonPressState.clearPressedButton();
        rebuildButtons();
    }

    private void exitMappingManagement() {
        this.managedMappingProvider = null;
        this.managedDirectoryFocus = null;
        this.managedDirectoryRefreshPending = false;
        clearMappingPageState();
    }

    private void unbindManagedRecipeType(ProviderViewKey viewKey, long providerId, String recipeTypeUid) {
        if (!isCurrentProviderSnapshot(viewKey)) {
            return;
        }
        ProviderEntry providerEntry = this.managedMappingProvider;
        if (providerEntry == null || providerEntry.inventoryId() != providerId) {
            return;
        }
        this.parent.getContainer().unbindProviderMapping(providerId, recipeTypeUid);
    }

    private void addManagedRecipeType(ProviderViewKey viewKey, long providerId) {
        ProviderEntry provider = this.managedMappingProvider;
        if (!isCurrentProviderSnapshot(viewKey) || provider == null || provider.inventoryId() != providerId) {
            return;
        }
        String mappingText = getTrimmedMappingText();
        if (!mappingText.isEmpty()) {
            this.parent.getContainer().bindProviderMapping(providerId, mappingText);
        }
    }

    private boolean isCurrentProviderSnapshot(ProviderViewKey viewKey) {
        return this.visible
            && !this.managedDirectoryRefreshPending
            && Objects.equals(viewKey, currentViewKey());
    }

    private String getTrimmedMappingText() {
        String normalized = RecipeTypeUid.normalize(this.mappingText);
        if (normalized != null) {
            return normalized;
        }
        if (!this.mappingText.isEmpty()) {
            if (shouldLogInvalidMappingWarning(LAST_INVALID_MAPPING_WARNING, System.nanoTime())) {
                AELog.warn("Ignoring invalid provider mapping input (UTF-16 length: %d, UTF-8 bytes: %d)",
                    this.mappingText.length(),
                    this.mappingText.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
            }
        }
        return "";
    }

    static boolean shouldLogInvalidMappingWarning(AtomicLong lastWarning, long now) {
        Objects.requireNonNull(lastWarning, "lastWarning");
        while (true) {
            long previous = lastWarning.get();
            if (previous != Long.MIN_VALUE && now - previous < WARNING_INTERVAL_NANOS) {
                return false;
            }
            if (lastWarning.compareAndSet(previous, now)) {
                return true;
            }
        }
    }

    private static Rectangle getButtonBounds(GuiButton button) {
        return new Rectangle(button.x, button.y, button.width, button.height);
    }

    private boolean canStartDrag(Point mousePos) {
        if (!contains(getTitleBarBounds(this.bounds), mousePos.x(), mousePos.y())) {
            return false;
        }

        Point absoluteMouse = this.screenOrigin.move(mousePos.x(), mousePos.y());
        for (GuiButton button : this.buttons) {
            if (button.visible && contains(getButtonBounds(button), absoluteMouse.x(), absoluteMouse.y())) {
                return false;
            }
        }
        return !isMouseOverTextField(this.searchField, absoluteMouse)
            && !isMouseOverTextField(this.mappingField, absoluteMouse);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean isMouseOverTextField(@Nullable AETextField field, Point absoluteMouse) {
        return field != null && field.getVisible() && field.isMouseOver(absoluteMouse.x(), absoluteMouse.y());
    }

    private boolean applyPendingOpenPositionReset() {
        if (!this.resetPositionOnOpen) {
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

    private enum TextInputTarget {
        NONE,
        SEARCH,
        MAPPING
    }

    private record ProviderSelectTextFocusState(boolean searchFocused, boolean mappingFocused) {
        static ProviderSelectTextFocusState none() {
            return new ProviderSelectTextFocusState(false, false);
        }

        static ProviderSelectTextFocusState search() {
            return new ProviderSelectTextFocusState(true, false);
        }

        static ProviderSelectTextFocusState mapping() {
            return new ProviderSelectTextFocusState(false, true);
        }

        boolean hasFocusedInput() {
            return this.searchFocused || this.mappingFocused;
        }

        static TextInputTarget resolveKeyTarget(boolean searchVisible, boolean searchFocused,
                                                boolean mappingVisible, boolean mappingFocused) {
            if (mappingVisible && mappingFocused) {
                return TextInputTarget.MAPPING;
            }
            if (searchVisible && searchFocused) {
                return TextInputTarget.SEARCH;
            }
            return TextInputTarget.NONE;
        }
    }

    public record ProviderEntry(long inventoryId,
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
            if (emptySlots < 0) {
                throw new IllegalArgumentException("Provider entry empty slot count must not be negative");
            }
            if (recipeTypeCount < 0 || recipeTypeUids.size() > recipeTypeCount) {
                throw new IllegalArgumentException("Invalid provider entry recipe type count");
            }
            Objects.requireNonNull(recipeTypeUids, "recipeTypeUids");
            if (recipeTypeUids.size() > PatternProviderMappingData.DIRECTORY_RECIPE_TYPE_PREVIEW_SIZE) {
                throw new IllegalArgumentException("Provider entry exceeds "
                    + PatternProviderMappingData.DIRECTORY_RECIPE_TYPE_PREVIEW_SIZE + " recipe type preview UIDs");
            }
            List<String> normalizedRecipeTypeUids = new ArrayList<>(recipeTypeUids.size());
            Set<String> uniqueRecipeTypeUids = new HashSet<>(recipeTypeUids.size());
            for (String recipeTypeUid : recipeTypeUids) {
                String normalizedRecipeTypeUid = RecipeTypeUid.requireValid(recipeTypeUid);
                if (!uniqueRecipeTypeUids.add(normalizedRecipeTypeUid)) {
                    throw new IllegalArgumentException(
                        "Provider entry contains duplicate recipe type UID " + normalizedRecipeTypeUid);
                }
                normalizedRecipeTypeUids.add(normalizedRecipeTypeUid);
            }
            recipeTypeUids = List.copyOf(normalizedRecipeTypeUids);
        }
    }

    private record ProviderViewKey(int windowId, long nonce, long revision, int firstVisibleRow,
                                   long managedProviderId) {
    }

    public record ProviderLocation(int dimensionId, BlockPos pos, @Nullable EnumFacing side) {
        public ProviderLocation {
            Objects.requireNonNull(pos, "pos");
        }
    }

    private static final class IconTooltipsButton extends AE2Button implements ITooltip {
        private final Icon icon;
        private final Supplier<List<ITextComponent>> tooltipSupplier;

        private IconTooltipsButton(Icon icon, Runnable onPress, Supplier<List<ITextComponent>> tooltipSupplier) {
            super(EMPTY_MESSAGE, onPress);
            this.icon = Objects.requireNonNull(icon, "icon");
            this.tooltipSupplier = Objects.requireNonNull(tooltipSupplier, "tooltipSupplier");
        }

        @Override
        public void drawButton(Minecraft minecraft, int mouseX, int mouseY, float partialTicks) {
            if (!this.visible) {
                return;
            }

            this.hovered = mouseX >= this.x
                && mouseY >= this.y
                && mouseX < this.x + this.width
                && mouseY < this.y + this.height;
            SmallSquareButtonRenderer.drawBackground(this.x, this.y, this.width, this.height, this.hovered);
            SmallSquareButtonRenderer.drawIcon(this.x, this.y, this.width, this.height, this.icon, 0);
        }

        @Override
        public List<ITextComponent> getTooltipMessage() {
            return this.tooltipSupplier.get();
        }

        @Override
        public Rectangle getTooltipArea() {
            return new Rectangle(this.x, this.y, this.width, this.height);
        }

        @Override
        public boolean isTooltipAreaVisible() {
            return this.visible;
        }
    }

    private static final class CloseButton extends AE2Button implements ITooltip {
        private final Supplier<Icon> iconSupplier;
        private final Supplier<ITextComponent> tooltipSupplier;

        private CloseButton(Runnable onPress, Supplier<Icon> iconSupplier,
                            Supplier<ITextComponent> tooltipSupplier) {
            super(EMPTY_MESSAGE, onPress);
            this.iconSupplier = Objects.requireNonNull(iconSupplier, "iconSupplier");
            this.tooltipSupplier = Objects.requireNonNull(tooltipSupplier, "tooltipSupplier");
        }

        @Override
        public void drawButton(Minecraft minecraft, int mouseX, int mouseY, float partialTicks) {
            if (!this.visible) {
                return;
            }

            this.hovered = mouseX >= this.x
                && mouseY >= this.y
                && mouseX < this.x + this.width
                && mouseY < this.y + this.height;
            SmallSquareButtonRenderer.drawBackground(this.x, this.y, this.width, this.height, this.hovered);
            SmallSquareButtonRenderer.drawIcon(this.x, this.y, this.width, this.height,
                Objects.requireNonNull(this.iconSupplier.get(), "icon"), 0);
        }

        @Override
        public List<ITextComponent> getTooltipMessage() {
            return Collections.singletonList(Objects.requireNonNull(this.tooltipSupplier.get(), "tooltip"));
        }

        @Override
        public Rectangle getTooltipArea() {
            return new Rectangle(this.x, this.y, this.width, this.height);
        }

        @Override
        public boolean isTooltipAreaVisible() {
            return this.visible;
        }
    }

    private static final class ProviderSnapshotButton extends TooltipButton {
        private final ProviderViewKey viewKey;
        @Nullable
        private final ProviderEntry providerEntry;
        @Nullable
        private final String recipeTypeUid;

        private ProviderSnapshotButton(ITextComponent component, Runnable onPress,
                                       Supplier<List<ITextComponent>> tooltipSupplier,
                                       ProviderViewKey viewKey,
                                       @Nullable ProviderEntry providerEntry,
                                       @Nullable String recipeTypeUid) {
            super(component, tooltipSupplier, onPress);
            this.viewKey = Objects.requireNonNull(viewKey, "viewKey");
            this.providerEntry = providerEntry;
            this.recipeTypeUid = recipeTypeUid;
        }

        static ProviderSnapshotButton provider(ITextComponent component, Runnable onPress,
                                               Supplier<List<ITextComponent>> tooltipSupplier,
                                               ProviderViewKey viewKey, ProviderEntry providerEntry) {
            Objects.requireNonNull(providerEntry, "providerEntry");
            return new ProviderSnapshotButton(component, onPress, tooltipSupplier, viewKey, providerEntry, null);
        }

        static ProviderSnapshotButton mapping(ITextComponent component, Runnable onPress,
                                              Supplier<List<ITextComponent>> tooltipSupplier,
                                              ProviderViewKey viewKey,
                                              @Nullable String recipeTypeUid) {
            return new ProviderSnapshotButton(component, onPress, tooltipSupplier, viewKey,
                null, recipeTypeUid);
        }

        ProviderViewKey viewKey() {
            return this.viewKey;
        }

        boolean isProviderRow() {
            return this.providerEntry != null && this.recipeTypeUid == null;
        }

        @Nullable
        ProviderEntry providerEntry() {
            return this.providerEntry;
        }

    }
}
