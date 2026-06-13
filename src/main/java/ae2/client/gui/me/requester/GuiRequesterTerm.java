package ae2.client.gui.me.requester;

import ae2.api.config.ActionItems;
import ae2.api.config.Settings;
import ae2.api.config.TerminalStyle;
import ae2.api.stacks.AEKey;
import ae2.api.upgrades.IUpgradeableObject;
import ae2.client.gui.me.common.GuiTerminalSettings;
import ae2.client.gui.me.items.WirelessUniversalTerminalSelectorWindow;
import ae2.client.gui.style.GuiStyle;
import ae2.client.gui.widgets.AETextField;
import ae2.client.gui.widgets.ActionButton;
import ae2.client.gui.widgets.ITextFieldGui;
import ae2.client.gui.widgets.ItemStackButton;
import ae2.client.gui.widgets.SettingToggleButton;
import ae2.client.gui.widgets.UpgradesPanel;
import ae2.container.SlotSemantics;
import ae2.container.implementations.ContainerRequesterTerm;
import ae2.container.me.common.AbstractContainerRequester;
import ae2.core.AEConfig;
import ae2.core.localization.GuiText;
import ae2.helpers.WirelessTerminalGuiHost;
import ae2.items.tools.powered.WirelessUniversalTerminalItem;
import ae2.tile.crafting.requester.Request;
import com.google.common.collect.HashMultimap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.input.Keyboard;

import java.awt.Rectangle;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public class GuiRequesterTerm extends AbstractGuiRequester<ContainerRequesterTerm> implements ITextFieldGui {
    private static final Rectangle FOOTER_BBOX = new Rectangle(0, 133, GUI_WIDTH, GUI_FOOTER_HEIGHT + 2);

    private final Long2ObjectMap<ClientRequester> byId = new Long2ObjectOpenHashMap<>();
    private final HashMultimap<String, ClientRequester> byName = HashMultimap.create();
    private final List<String> requesterNames = new ArrayList<>();
    private final Map<String, Set<Object>> searchCache = new WeakHashMap<>();
    private final AETextField searchField;

    public GuiRequesterTerm(ContainerRequesterTerm container, InventoryPlayer playerInventory,
                            @Nullable ITextComponent title, GuiStyle style) {
        super(container, playerInventory,
            title != null ? title : GuiText.RequesterTerminalShort.text(), style,
            requesterTexture("requester_terminal"));

        this.addToLeftToolbar(new SettingToggleButton<>(
            Settings.TERMINAL_STYLE,
            AEConfig.instance().getTerminalStyle(),
            this::toggleTerminalStyle));
        this.addWirelessSettingsButton();
        if (container.getItemGuiHost() instanceof IUpgradeableObject upgradeableObject) {
            this.widgets.add("upgrades", UpgradesPanel.create(
                this.widgets,
                container.getSlots(SlotSemantics.UPGRADE),
                container.getSlots(SlotSemantics.WIRELESS_SINGULARITY),
                upgradeableObject));
        }
        addWirelessUniversalTerminalButton();

        this.searchField = this.widgets.addTextField("search");
        this.searchField.setResponder(ignored -> refreshList());
        this.searchField.setPlaceholder(GuiText.SearchPlaceholder.getLocal());
        this.searchField.setTooltipMessage(Collections.singletonList(GuiText.SearchTooltip.text()));
    }

    private static String requesterDisplayName(ClientRequester requester) {
        return requester.getDisplayName().getFormattedText();
    }

    private static String requesterSearchName(ClientRequester requester) {
        return requester.getSearchName();
    }

    private static boolean keyMatchesSearchQuery(@Nullable AEKey key, String searchTerm) {
        return key != null && key.getDisplayName().getFormattedText().toLowerCase(Locale.ROOT).contains(searchTerm);
    }

    @Override
    public void initGui() {
        int availableHeight = Math.max(this.height, this.ySize) - 2 * AEConfig.instance().getTerminalMargin();
        int possibleRows = Math.max(MIN_ROW_COUNT,
            (availableHeight - GUI_HEADER_HEIGHT - GUI_FOOTER_HEIGHT) / ROW_HEIGHT);
        this.rowAmount = Math.clamp(AEConfig.instance().getTerminalStyle().getRows(possibleRows), MIN_ROW_COUNT, AbstractContainerRequester.REQUEST_SLOT_COUNT);

        super.initGui();

        if (AEConfig.instance().isAutoFocusSearch()) {
            setInitialFocus(this.searchField);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton == 1 && this.searchField.isMouseOver(mouseX, mouseY)) {
            this.searchField.setText("");
            refreshList();
        }

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE && this.searchField.isFocused()) {
            this.searchField.setFocused(false);
            return;
        }

        if (typedChar == ' ' && this.searchField.isFocused() && this.searchField.getText().isEmpty()) {
            return;
        }

        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public Collection<? extends GuiTextField> getTextFields() {
        return Collections.singleton(this.searchField);
    }

    @Override
    protected void clearData() {
        this.byId.clear();
        this.byName.clear();
        this.requesterNames.clear();
        this.searchCache.clear();
    }

    @Override
    protected void refreshList() {
        this.searchCache.clear();
        this.byName.clear();

        String searchQuery = this.searchField.getText().toLowerCase(Locale.ROOT);
        Set<Object> cachedSearch = searchByQuery(searchQuery);
        boolean rebuild = cachedSearch.isEmpty();

        for (ClientRequester requester : this.byId.values()) {
            if (!rebuild && !cachedSearch.contains(requester)) {
                continue;
            }

            boolean found = searchQuery.isEmpty() || requesterSearchName(requester).contains(searchQuery);
            if (!found) {
                for (int i = 0; i < requester.getRequests().size(); i++) {
                    found = keyMatchesSearchQuery(requester.getRequests().get(i).getKey(), searchQuery);
                    if (found) {
                        break;
                    }
                }
            }

            if (found) {
                this.byName.put(requesterDisplayName(requester), requester);
                cachedSearch.add(requester);
            } else {
                cachedSearch.remove(requester);
            }
        }

        this.requesterNames.clear();
        this.requesterNames.addAll(this.byName.keySet());
        Collections.sort(this.requesterNames);

        this.lines.clear();
        this.lines.ensureCapacity(this.requesterNames.size() + this.byId.size());

        for (String name : this.requesterNames) {
            this.lines.add(name);

            List<ClientRequester> requesters = new ArrayList<>(this.byName.get(name));
            requesters.sort(Comparator.naturalOrder());

            for (ClientRequester requester : requesters) {
                for (int i = 0; i < requester.getRequests().size(); i++) {
                    Request request = requester.getRequests().get(i);
                    this.lines.add(request);
                }
            }
        }

        resetScrollbar();
    }

    @Override
    protected Collection<?> getByName(String name) {
        return this.byName.get(name);
    }

    @Override
    protected ClientRequester getById(long requesterId, @Nullable ITextComponent name, long sortValue,
                                      int requestCount) {
        int sanitizedRequestCount = Math.clamp(requestCount, 0, ClientRequester.MAX_REQUEST_COUNT);
        ClientRequester requester = this.byId.get(requesterId);
        if (requester == null || requester.getRequests().size() != sanitizedRequestCount) {
            requester = new ClientRequester(requesterId, name, sortValue, requestCount);
            this.byId.put(requesterId, requester);
        } else {
            requester.update(name, sortValue);
        }
        return requester;
    }

    @Nullable
    @Override
    protected ClientRequester findById(long requesterId) {
        return this.byId.get(requesterId);
    }

    @Override
    protected Rectangle getFooterBounds() {
        return FOOTER_BBOX;
    }

    private void toggleTerminalStyle(SettingToggleButton<TerminalStyle> button, boolean backwards) {
        TerminalStyle next = button.getNextValue(backwards);
        button.set(next);
        AEConfig.instance().setTerminalStyle(next);
        this.setWorldAndResolution(this.mc, this.width, this.height);
    }

    private Set<Object> searchByQuery(String searchQuery) {
        Set<Object> cache = this.searchCache.computeIfAbsent(searchQuery, ignored -> new HashSet<>());

        if (cache.isEmpty() && searchQuery.length() > 1) {
            cache.addAll(searchByQuery(searchQuery.substring(0, searchQuery.length() - 1)));
        }

        return cache;
    }

    private void addWirelessUniversalTerminalButton() {
        if (!(this.container.getItemGuiHost() instanceof WirelessTerminalGuiHost<?> wirelessHost)) {
            return;
        }
        ItemStack stack = wirelessHost.getItemStack();
        if (!(stack.getItem() instanceof WirelessUniversalTerminalItem)) {
            return;
        }
        WirelessUniversalTerminalSelectorWindow selector = new WirelessUniversalTerminalSelectorWindow(this);
        this.widgets.add("wirelessUniversalTerminalSelector", selector);
        this.addToLeftToolbar(new ItemStackButton(
            () -> wirelessHost.getTerminalItem().getWirelessTerminalDefinition().icon(wirelessHost.getTerminalItem()),
            GuiText.WirelessTerminalSelector.text(),
            selector::toggle));
    }

    private void addWirelessSettingsButton() {
        if (!(this.container.getItemGuiHost() instanceof WirelessTerminalGuiHost<?> wirelessHost)) {
            return;
        }
        this.addToLeftToolbar(new ActionButton(ActionItems.TERMINAL_SETTINGS,
            () -> switchToScreen(new GuiTerminalSettings(
                this,
                this.container,
                wirelessHost,
                wirelessHost.getMainContainerIcon(),
                () -> {
                },
                true))));
    }
}
