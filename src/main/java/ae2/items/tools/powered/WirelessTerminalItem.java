/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
 */
package ae2.items.tools.powered;

import ae2.api.config.Actionable;
import ae2.api.config.Settings;
import ae2.api.config.SortDir;
import ae2.api.config.SortOrder;
import ae2.api.config.ViewItems;
import ae2.api.features.GridLinkables;
import ae2.api.features.HotkeyAction;
import ae2.api.features.IGridLinkableHandler;
import ae2.api.implementations.blockentities.IWirelessAccessPoint;
import ae2.api.implementations.guiobjects.IGuiItem;
import ae2.api.implementations.items.AddWirelessTerminalEvent;
import ae2.api.implementations.items.WirelessTerminalDefinition;
import ae2.api.networking.IGrid;
import ae2.api.upgrades.IUpgradeInventory;
import ae2.api.upgrades.IUpgradeableItem;
import ae2.api.upgrades.UpgradeInventories;
import ae2.api.upgrades.Upgrades;
import ae2.api.util.DimensionalBlockPos;
import ae2.api.util.IConfigManager;
import ae2.container.GuiIds;
import ae2.core.gui.GuiOpener;
import ae2.core.gui.locator.GuiHostLocators;
import ae2.core.gui.locator.ItemGuiHostLocator;
import ae2.core.localization.GuiText;
import ae2.core.localization.PlayerMessages;
import ae2.core.localization.Tooltips;
import ae2.helpers.WirelessTerminalGuiHost;
import ae2.util.Platform;
import baubles.api.BaubleType;
import baubles.api.IBauble;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.common.Optional;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Optional.Interface(iface = "baubles.api.IBauble", modid = "baubles")
public class WirelessTerminalItem extends PoweredContainerItem implements IGuiItem, IUpgradeableItem, IBauble {
    public static final IGridLinkableHandler LINKABLE_HANDLER = new LinkableHandler();
    private final double powerCapacity;
    private final GuiIds.GuiKey fallbackGuiKey;
    private WirelessTerminalDefinition definition;

    public WirelessTerminalItem(double powerCapacity) {
        this(powerCapacity,
            "wireless_terminal",
            GuiIds.GuiKey.WIRELESS_TERMINAL,
            ItemStack::new,
            WirelessTerminalGuiHost::new,
            HotkeyAction.WIRELESS_TERMINAL,
            2);
    }

    protected WirelessTerminalItem(double powerCapacity, String id, GuiIds.GuiKey guiKey,
                                   java.util.function.Function<WirelessTerminalItem, ItemStack> iconFactory,
                                   WirelessTerminalDefinition.HostFactory hostFactory,
                                   String hotkeyName, int upgradeSlots) {
        this(powerCapacity, id, guiKey, iconFactory, hostFactory, hotkeyName, upgradeSlots, true);
    }

    protected WirelessTerminalItem(double powerCapacity, String id, GuiIds.GuiKey guiKey,
                                   java.util.function.Function<WirelessTerminalItem, ItemStack> iconFactory,
                                   WirelessTerminalDefinition.HostFactory hostFactory,
                                   String hotkeyName, int upgradeSlots, boolean registerTerminal) {
        super(powerCapacity);
        this.setMaxStackSize(1);
        this.powerCapacity = powerCapacity;
        this.fallbackGuiKey = guiKey;
        GridLinkables.register(this, LINKABLE_HANDLER);
        if (registerTerminal) {
            AddWirelessTerminalEvent.register(event -> event.builder(id, this,
                                                                (registeredDefinition, player, locator, terminalStack, returningFromSubmenu) ->
                                                                    !terminalStack.isEmpty()
                                                                        && openBuiltInGui(registeredDefinition, player, locator, returningFromSubmenu),
                                                                hostFactory,
                                                                iconFactory)
                                                            .hotkeyName(hotkeyName)
                                                            .upgradeSlots(upgradeSlots)
                                                            .addTerminal());
        }
    }

    private static boolean openBuiltInGui(WirelessTerminalDefinition definition, EntityPlayer player,
                                          ItemGuiHostLocator locator, boolean returningFromSubmenu) {
        return GuiOpener.openItemGui(player, definition.item().getGuiKey(), locator, returningFromSubmenu);
    }

    @Override
    public double getChargeRate(ItemStack stack) {
        return 800d + 800d * Upgrades.getEnergyCardMultiplier(getUpgrades(stack));
    }

    public final double powerCapacity() {
        return this.powerCapacity;
    }

    public final WirelessTerminalDefinition getWirelessTerminalDefinition() {
        if (this.definition == null) {
            WirelessTerminalDefinition registered = WirelessTerminalRegistry.ofItem(this);
            if (registered != null) {
                this.definition = registered;
            }
        }
        if (this.definition == null) {
            throw new IllegalStateException("Wireless terminal is not registered: " + getClass().getName());
        }
        return this.definition;
    }

    public final void setWirelessTerminalDefinition(WirelessTerminalDefinition definition) {
        this.definition = definition;
    }

    public final String getTerminalId() {
        return getWirelessTerminalDefinition().id();
    }

    @SuppressWarnings("unused")
    public boolean openFromInventory(EntityPlayer player, ItemGuiHostLocator locator) {
        return openFromInventory(player, locator, false);
    }

    protected boolean openFromInventory(EntityPlayer player, ItemGuiHostLocator locator, boolean returningFromSubmenu) {
        var is = locator.locateItem(player);

        if (!player.world.isRemote && checkPreconditions(is)) {
            return getWirelessTerminalDefinition().open(player, locator, is, returningFromSubmenu);
        }
        return false;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World level, EntityPlayer player, EnumHand hand) {
        var is = player.getHeldItem(hand);

        if (!player.world.isRemote && checkPreconditions(is)) {
            if (getWirelessTerminalDefinition().open(player, GuiHostLocators.forHand(player, hand), is, false)) {
                return new ActionResult<>(EnumActionResult.SUCCESS, is);
            }
        }

        return new ActionResult<>(EnumActionResult.FAIL, is);
    }

    @Override
    protected void addCheckedInformation(final ItemStack stack, final World world, final List<String> lines,
                                         final ITooltipFlag advancedTooltips) {
        super.addCheckedInformation(stack, world, lines, advancedTooltips);

        if (!isLinkedForTooltip(stack)) {
            lines.add(Tooltips.of(GuiText.Unlinked, Tooltips.RED).getFormattedText());
        } else {
            lines.add(Tooltips.of(GuiText.Linked, Tooltips.GREEN).getFormattedText());
        }
    }

    @Nullable
    @SuppressWarnings("unused")
    public DimensionalBlockPos getLinkedPosition(ItemStack item) {
        return getLinkedPosition(item, this);
    }

    @Nullable
    public DimensionalBlockPos getLinkedPosition(ItemStack item, WirelessTerminalItem terminal) {
        NBTTagCompound tag = item.getItem() instanceof WirelessUniversalTerminalItem
            ? WirelessTerminals.getUniversalSharedData(item)
            : WirelessTerminals.getExistingTerminalData(item, terminal);
        if (tag == null) {
            return null;
        }

        if (!tag.hasKey(WirelessTerminals.TAG_LINK, 10)) {
            return null;
        }
        NBTTagCompound link = tag.getCompoundTag(WirelessTerminals.TAG_LINK);
        int dim = link.getInteger(WirelessTerminals.TAG_LINK_DIM);
        WorldServer world = DimensionManager.getWorld(dim);
        if (world == null) {
            return null;
        }
        return new DimensionalBlockPos(world, link.getInteger(WirelessTerminals.TAG_LINK_X),
            link.getInteger(WirelessTerminals.TAG_LINK_Y), link.getInteger(WirelessTerminals.TAG_LINK_Z));
    }

    @Nullable
    @SuppressWarnings("unused")
    public IGrid getLinkedGrid(ItemStack item, World level, @Nullable Consumer<ITextComponent> errorConsumer) {
        return getLinkedGrid(item, this, level, errorConsumer);
    }

    @Nullable
    @SuppressWarnings("unused")
    public IGrid getLinkedGrid(ItemStack item, WirelessTerminalItem terminal, World level,
                               @Nullable Consumer<ITextComponent> errorConsumer) {
        var linkedPos = getLinkedPosition(item, terminal);
        if (linkedPos == null) {
            if (errorConsumer != null) {
                errorConsumer.accept(PlayerMessages.DeviceNotLinked.text());
            }
            return null;
        }

        if (!(linkedPos.getLevel() instanceof WorldServer linkedLevel)) {
            if (errorConsumer != null) {
                errorConsumer.accept(PlayerMessages.LinkedNetworkNotFound.text());
            }
            return null;
        }

        var be = linkedLevel.getTileEntity(linkedPos.getPos());
        if (!(be instanceof IWirelessAccessPoint accessPoint)) {
            if (errorConsumer != null) {
                errorConsumer.accept(PlayerMessages.LinkedNetworkNotFound.text());
            }
            return null;
        }

        var grid = accessPoint.getGrid();
        if (grid == null && errorConsumer != null) {
            errorConsumer.accept(PlayerMessages.LinkedNetworkNotFound.text());
        }
        return grid;
    }

    public GuiIds.GuiKey getGuiKey() {
        return this.fallbackGuiKey;
    }

    public GuiIds.GuiKey getGuiKey(ItemStack stack) {
        return this.fallbackGuiKey;
    }

    @Nullable
    @Override
    public WirelessTerminalGuiHost<?> getGuiHost(EntityPlayer player, ItemGuiHostLocator locator,
                                                 @Nullable RayTraceResult hitResult) {
        return getWirelessTerminalDefinition().hostFactory().create(this, this, player, locator,
            (p, ignored) -> openFromInventory(p, locator, true));
    }

    protected boolean checkPreconditions(ItemStack item) {
        return isTerminalStack(item);
    }

    protected boolean isTerminalStack(ItemStack item) {
        return !item.isEmpty() && item.getItem() == this;
    }

    protected boolean isLinkedForTooltip(ItemStack stack) {
        return getLinkedPosition(stack, this) != null;
    }

    @SuppressWarnings("unused")
    public boolean usePower(EntityPlayer player, double amount, ItemStack is) {
        return extractAEPower(is, amount, Actionable.MODULATE) >= amount - 0.5;
    }

    @SuppressWarnings("unused")
    public boolean hasPower(EntityPlayer player, double amt, ItemStack is) {
        return getAECurrentPower(is) >= amt;
    }

    public IConfigManager getConfigManager(Supplier<ItemStack> target) {
        return IConfigManager.builder(target)
                             .registerSetting(Settings.SORT_BY, SortOrder.NAME)
                             .registerSetting(Settings.VIEW_MODE, ViewItems.ALL)
                             .registerSetting(Settings.SORT_DIRECTION, SortDir.ASCENDING)
                             .build();
    }

    @Optional.Method(modid = "baubles")
    @Override
    public BaubleType getBaubleType(ItemStack stack) {
        return BaubleType.TRINKET;
    }

    @Override
    public IUpgradeInventory getUpgrades(ItemStack stack) {
        return UpgradeInventories.forItem(stack, getWirelessTerminalDefinition().upgradeSlots(),
            this::onUpgradesChanged);
    }

    private void onUpgradesChanged(ItemStack stack, IUpgradeInventory upgrades) {
        setAEMaxPower(stack, powerCapacity * (1 + Upgrades.getEnergyCardMultiplier(upgrades)));
    }

    private static class LinkableHandler implements IGridLinkableHandler {
        @Override
        public boolean canLink(ItemStack stack) {
            if (stack.getItem() instanceof WirelessUniversalTerminalItem universalTerminal) {
                return universalTerminal.getInstalledTerminalIds(stack)
                                        .stream()
                                        .anyMatch(id -> WirelessTerminalRegistry.definitionOfId(id) != null);
            }
            return stack.getItem() instanceof WirelessTerminalItem terminal
                && WirelessTerminalRegistry.ofItem(terminal) != null;
        }

        @Override
        public void link(ItemStack itemStack, World world, net.minecraft.util.math.BlockPos pos) {
            NBTTagCompound link = new NBTTagCompound();
            link.setInteger(WirelessTerminals.TAG_LINK_DIM, world.provider.getDimension());
            link.setInteger(WirelessTerminals.TAG_LINK_X, pos.getX());
            link.setInteger(WirelessTerminals.TAG_LINK_Y, pos.getY());
            link.setInteger(WirelessTerminals.TAG_LINK_Z, pos.getZ());

            if (itemStack.getItem() instanceof WirelessUniversalTerminalItem universalTerminal) {
                WirelessTerminals.getOrCreateUniversalSharedData(itemStack)
                                 .setTag(WirelessTerminals.TAG_LINK, link.copy());
                universalTerminal.getCurrentTerminal(itemStack);
                return;
            }

            if (itemStack.getItem() instanceof WirelessTerminalItem terminal) {
                WirelessTerminals.getTerminalData(itemStack, terminal)
                                 .setTag(WirelessTerminals.TAG_LINK, link.copy());
            }
        }

        @Override
        public void unlink(ItemStack itemStack) {
            if (itemStack.getItem() instanceof WirelessUniversalTerminalItem) {
                NBTTagCompound root = itemStack.getTagCompound();
                if (root == null) {
                    return;
                }
                root.removeTag(WirelessTerminals.TAG_LINK);
                if (Platform.isNbtEmpty(root)) {
                    itemStack.setTagCompound(null);
                }
                return;
            }

            WirelessTerminalItem terminal = WirelessTerminalRegistry.ofStack(itemStack);
            if (terminal == null) {
                return;
            }
            NBTTagCompound tag = WirelessTerminals.getExistingTerminalData(itemStack, terminal);
            if (tag != null) {
                tag.removeTag(WirelessTerminals.TAG_LINK);
                if (Platform.isNbtEmpty(tag)) {
                    if (!(itemStack.getItem() instanceof WirelessUniversalTerminalItem)) {
                        itemStack.setTagCompound(null);
                    }
                }
            }
        }
    }
}
