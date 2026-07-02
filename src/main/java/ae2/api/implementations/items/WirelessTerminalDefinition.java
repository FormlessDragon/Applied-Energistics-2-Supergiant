package ae2.api.implementations.items;

import ae2.container.AEBaseContainer;
import ae2.container.ISubGui;
import ae2.core.gui.locator.ItemGuiHostLocator;
import ae2.helpers.WirelessTerminalGuiHost;
import ae2.items.tools.powered.WirelessTerminalItem;
import ae2.text.TextComponentItemStack;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

public final class WirelessTerminalDefinition {
    private final String id;
    private final WirelessTerminalItem item;
    private final GuiOpener guiOpener;
    private final Function<WirelessTerminalItem, ItemStack> iconFactory;
    private final HostFactory hostFactory;
    private final ContainerFactory containerFactory;
    private final ScreenFactory screenFactory;
    private final String hotkeyName;
    private final int upgradeSlots;

    WirelessTerminalDefinition(String id, WirelessTerminalItem item, GuiOpener guiOpener,
                               Function<WirelessTerminalItem, ItemStack> iconFactory, HostFactory hostFactory,
                               ContainerFactory containerFactory, ScreenFactory screenFactory, String hotkeyName,
                               int upgradeSlots) {
        this.id = requireText(id, "id");
        this.item = Objects.requireNonNull(item, "item");
        this.guiOpener = Objects.requireNonNull(guiOpener, "guiOpener");
        this.iconFactory = Objects.requireNonNull(iconFactory, "iconFactory");
        this.hostFactory = Objects.requireNonNull(hostFactory, "hostFactory");
        this.containerFactory = Objects.requireNonNull(containerFactory, "containerFactory");
        this.screenFactory = Objects.requireNonNull(screenFactory, "screenFactory");
        this.hotkeyName = requireText(hotkeyName, "hotkeyName");
        if (upgradeSlots < 0) {
            throw new IllegalArgumentException("upgradeSlots must be >= 0");
        }
        this.upgradeSlots = upgradeSlots;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    public String id() {
        return this.id;
    }

    public WirelessTerminalItem item() {
        return this.item;
    }

    @SuppressWarnings("unused")
    public GuiOpener guiOpener() {
        return this.guiOpener;
    }

    public HostFactory hostFactory() {
        return this.hostFactory;
    }

    public ContainerFactory containerFactory() {
        return this.containerFactory;
    }

    public ScreenFactory screenFactory() {
        return this.screenFactory;
    }

    public String hotkeyName() {
        return this.hotkeyName;
    }

    public int upgradeSlots() {
        return this.upgradeSlots;
    }

    public boolean open(EntityPlayer player, ItemGuiHostLocator locator, ItemStack stack, boolean returningFromSubmenu) {
        return this.guiOpener.tryOpen(this, player, locator, stack, returningFromSubmenu);
    }

    public ItemStack icon() {
        return icon(this.item);
    }

    public ItemStack icon(WirelessTerminalItem terminal) {
        ItemStack icon = this.iconFactory.apply(terminal);
        return icon == null ? ItemStack.EMPTY : icon;
    }

    public ITextComponent displayName() {
        return displayName(this.item);
    }

    public ITextComponent displayName(WirelessTerminalItem terminal) {
        return TextComponentItemStack.of(icon(terminal));
    }

    @FunctionalInterface
    public interface GuiOpener {
        boolean tryOpen(WirelessTerminalDefinition definition, EntityPlayer player, ItemGuiHostLocator locator,
                        ItemStack stack, boolean returningFromSubmenu);
    }

    @FunctionalInterface
    public interface HostFactory {
        WirelessTerminalGuiHost<?> create(WirelessTerminalItem stackItem, WirelessTerminalItem terminalItem,
                                          EntityPlayer player, ItemGuiHostLocator locator,
                                          BiConsumer<EntityPlayer, ISubGui> returnToMainContainer);
    }

    /**
     * Creates the server container for this wireless terminal definition.
     * <p>
     * The dynamic wireless terminal opener resolves a definition first, then asks the definition to create the
     * container. This keeps external wireless terminals from having to reserve or depend on an AE2 {@code GuiKey}.
     */
    @FunctionalInterface
    public interface ContainerFactory {
        @Nullable
        AEBaseContainer create(WirelessTerminalDefinition definition, InventoryPlayer inventory,
                               WirelessTerminalGuiHost<?> host);
    }

    /**
     * Creates the client screen for this wireless terminal definition.
     * <p>
     * The return type is intentionally {@code Object} because Forge 1.12 GUI handlers use raw object returns and the
     * common API package must not force callers into a specific GUI base class.
     */
    @FunctionalInterface
    public interface ScreenFactory {
        @Nullable
        Object create(WirelessTerminalDefinition definition, AEBaseContainer container, InventoryPlayer inventory);
    }
}
