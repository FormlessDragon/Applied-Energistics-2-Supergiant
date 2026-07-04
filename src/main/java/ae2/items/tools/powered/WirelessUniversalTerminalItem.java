package ae2.items.tools.powered;

import ae2.api.config.Actionable;
import ae2.api.implementations.items.WirelessTerminalDefinition;
import ae2.api.upgrades.IUpgradeInventory;
import ae2.api.upgrades.Upgrades;
import ae2.container.GuiIds;
import ae2.container.ISubGui;
import ae2.core.gui.locator.GuiHostLocator;
import ae2.core.gui.locator.GuiHostLocators;
import ae2.core.gui.locator.ItemGuiHostLocator;
import ae2.core.localization.GuiText;
import ae2.core.localization.PlayerMessages;
import ae2.helpers.WirelessTerminalGuiHost;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.input.Keyboard;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class WirelessUniversalTerminalItem extends WirelessTerminalItem {
    public WirelessUniversalTerminalItem(double powerCapacity) {
        super(powerCapacity,
            "wireless_universal_terminal",
            ItemStack::new,
            WirelessTerminalGuiHost::new,
            "wireless_universal_terminal",
            0, false);
    }

    private static @NotNull NBTTagCompound copyTag(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        return tag != null ? tag.copy() : new NBTTagCompound();
    }

    @Override
    protected boolean checkPreconditions(ItemStack item) {
        return isTerminalStack(item) && getCurrentTerminal(item) != null;
    }

    @Override
    public GuiIds.GuiKey getGuiKey(ItemStack stack) {
        WirelessTerminalItem terminal = getCurrentTerminal(stack);
        return terminal != null ? terminal.getGuiKey(stack) : super.getGuiKey(stack);
    }

    @Override
    public @Nullable GuiIds.GuiKey getLegacyGuiKey(ItemStack stack) {
        WirelessTerminalItem terminal = getCurrentTerminal(stack);
        return terminal == null ? null : terminal.getLegacyGuiKey(stack);
    }

    @Override
    public WirelessTerminalGuiHost<?> getGuiHost(EntityPlayer player, ItemGuiHostLocator locator,
                                                 @Nullable RayTraceResult hitResult) {
        ItemStack stack = locator.locateItem(player);
        WirelessTerminalItem terminal = getCurrentTerminal(stack);
        if (terminal == null) {
            return null;
        }
        return terminal.getWirelessTerminalDefinition().hostFactory().create(this, terminal, player, locator,
            this::returnToMainContainer);
    }

    @Override
    protected boolean openFromInventory(EntityPlayer player, ItemGuiHostLocator locator, boolean returningFromSubmenu) {
        ItemStack stack = locator.locateItem(player);
        if (player.world.isRemote || !isTerminalStack(stack)) {
            return false;
        }

        WirelessTerminalItem terminal = ensureCurrentTerminal(stack);
        if (terminal == null) {
            player.sendStatusMessage(PlayerMessages.DeviceNotLinked.text(), true);
            return false;
        }

        return terminal.getWirelessTerminalDefinition().open(player, locator, stack, returningFromSubmenu);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World level, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (!level.isRemote && openFromInventory(player, GuiHostLocators.forHand(player, hand), false)) {
            return new ActionResult<>(EnumActionResult.SUCCESS, stack);
        }
        return new ActionResult<>(level.isRemote ? EnumActionResult.PASS : EnumActionResult.FAIL, stack);
    }

    @Override
    public IUpgradeInventory getUpgrades(ItemStack stack) {
        return new WirelessUniversalTerminalUpgradeInventory(this, stack);
    }

    @Override
    protected void getCheckedSubItems(CreativeTabs creativeTab, NonNullList<ItemStack> itemStacks) {
        itemStacks.add(createCreativeStack(false));
        itemStacks.add(createCreativeStack(true));
    }

    @Override
    public double getChargeRate(ItemStack stack) {
        return 800d * (countInstalledTerminals(stack) + 1 + Upgrades.getEnergyCardMultiplier(getUpgrades(stack)));
    }

    public int countInstalledTerminals(ItemStack stack) {
        return getInstalledTerminalIds(stack).size();
    }

    public boolean hasTerminal(ItemStack stack, WirelessTerminalItem terminal) {
        WirelessTerminalDefinition definition = WirelessTerminalRegistry.ofItem(terminal);
        return definition != null && getInstalledTerminalIds(stack).contains(definition.id());
    }

    public void addTerminal(ItemStack universal, ItemStack terminalStack, WirelessTerminalItem terminal) {
        WirelessTerminalDefinition definition = WirelessTerminalRegistry.ofItem(terminal);
        if (definition == null) {
            throw new IllegalArgumentException("Wireless terminal is not registered: " + terminal);
        }
        NBTTagCompound tag = WirelessTerminals.getOrCreateTag(universal);
        Set<String> ids = getInstalledTerminalIds(universal);
        ids.add(definition.id());
        writeInstalledTerminalIds(tag, ids);
        if (!tag.hasKey(WirelessTerminals.TAG_CURRENT_TERMINAL, Constants.NBT.TAG_STRING)) {
            tag.setString(WirelessTerminals.TAG_CURRENT_TERMINAL, definition.id());
        }
        NBTTagCompound terminalData = copyTag(terminalStack);
        if (terminalData.hasKey(WirelessTerminals.TAG_LINK, 10)) {
            tag.setTag(WirelessTerminals.TAG_LINK, terminalData.getCompoundTag(WirelessTerminals.TAG_LINK).copy());
            terminalData.removeTag(WirelessTerminals.TAG_LINK);
        }
        NBTTagCompound allData = tag.getCompoundTag(WirelessTerminals.TAG_TERMINAL_DATA);
        allData.setTag(definition.id(), terminalData);
        tag.setTag(WirelessTerminals.TAG_TERMINAL_DATA, allData);
        mergeUpgrades(universal, terminalStack, terminal);
        onUpgradesChanged(universal, getUpgrades(universal));
        injectAEPower(universal, terminal.getAECurrentPower(terminalStack), Actionable.MODULATE);
    }

    private void mergeUpgrades(ItemStack universal, ItemStack terminalStack, WirelessTerminalItem terminal) {
        IUpgradeInventory source = terminal.getUpgrades(terminalStack);
        IUpgradeInventory target = getUpgrades(universal);
        for (ItemStack upgrade : source) {
            if (!upgrade.isEmpty()) {
                target.addItems(upgrade.copy());
            }
        }
    }

    private void returnToMainContainer(EntityPlayer player, ISubGui subGui) {
        GuiHostLocator subLocator = subGui.getLocator();
        if (subLocator instanceof ItemGuiHostLocator itemLocator) {
            openFromInventory(player, itemLocator, true);
        }
    }

    public Set<String> getInstalledTerminalIds(ItemStack stack) {
        Set<String> ids = new LinkedHashSet<>();
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(WirelessTerminals.TAG_INSTALLED_TERMINALS, Constants.NBT.TAG_LIST)) {
            return ids;
        }
        NBTTagList list = tag.getTagList(WirelessTerminals.TAG_INSTALLED_TERMINALS, Constants.NBT.TAG_STRING);
        for (int i = 0; i < list.tagCount(); i++) {
            ids.add(list.getStringTagAt(i));
        }
        return ids;
    }

    private void writeInstalledTerminalIds(NBTTagCompound tag, Set<String> ids) {
        NBTTagList list = new NBTTagList();
        for (String id : ids) {
            list.appendTag(new NBTTagString(id));
        }
        tag.setTag(WirelessTerminals.TAG_INSTALLED_TERMINALS, list);
    }

    @Nullable
    public WirelessTerminalItem getCurrentTerminal(ItemStack stack) {
        return resolveCurrentTerminal(stack, false);
    }

    @Nullable
    public WirelessTerminalItem ensureCurrentTerminal(ItemStack stack) {
        return resolveCurrentTerminal(stack, true);
    }

    @Nullable
    private WirelessTerminalItem resolveCurrentTerminal(ItemStack stack, boolean initializeMissing) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            return null;
        }
        String current = tag.getString(WirelessTerminals.TAG_CURRENT_TERMINAL);
        if (current.isEmpty()) {
            for (String id : getInstalledTerminalIds(stack)) {
                WirelessTerminalDefinition definition = WirelessTerminalRegistry.definitionOfId(id);
                if (definition != null) {
                    if (initializeMissing) {
                        tag.setString(WirelessTerminals.TAG_CURRENT_TERMINAL, id);
                    }
                    return definition.item();
                }
            }
            return null;
        }
        if (!getInstalledTerminalIds(stack).contains(current)) {
            return null;
        }
        WirelessTerminalDefinition definition = WirelessTerminalRegistry.definitionOfId(current);
        return definition == null ? null : definition.item();
    }

    public boolean selectTerminal(ItemStack stack, String terminalId) {
        if (!getInstalledTerminalIds(stack).contains(terminalId)) {
            return false;
        }
        if (WirelessTerminalRegistry.definitionOfId(terminalId) == null) {
            return false;
        }
        WirelessTerminals.getOrCreateTag(stack).setString(WirelessTerminals.TAG_CURRENT_TERMINAL, terminalId);
        return true;
    }

    @Override
    protected boolean isLinkedForTooltip(ItemStack stack) {
        WirelessTerminalItem current = getCurrentTerminal(stack);
        return current != null && getLinkedPosition(stack, current) != null;
    }

    @Override
    protected void addCheckedInformation(ItemStack stack, World world, List<String> lines,
                                         ITooltipFlag advancedTooltips) {
        WirelessTerminalItem current = getCurrentTerminal(stack);
        if (current != null) {
            lines.add(GuiText.WirelessUniversalTerminalCurrent.getLocal(
                current.getWirelessTerminalDefinition().displayName().getFormattedText()));
        }
        boolean shiftDown = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
        if (shiftDown) {
            lines.add(GuiText.WirelessUniversalTerminalInstalledTitle.getLocal());
            boolean foundInstalled = false;
            for (String id : getInstalledTerminalIds(stack)) {
                WirelessTerminalDefinition definition = WirelessTerminalRegistry.definitionOfId(id);
                if (definition != null) {
                    lines.add(GuiText.WirelessUniversalTerminalInstalledLine.getLocal(
                        definition.displayName().getFormattedText()));
                    foundInstalled = true;
                }
            }
            if (!foundInstalled) {
                lines.add(GuiText.WirelessUniversalTerminalInstalledEmpty.getLocal());
            }
        } else {
            lines.add(GuiText.WirelessUniversalTerminalDetailsHint.getLocal());
        }
        super.addCheckedInformation(stack, world, lines, advancedTooltips);
    }

    void onUpgradesChanged(ItemStack stack, IUpgradeInventory upgrades) {
        setAEMaxPower(stack, powerCapacity() * Math.max(1,
            countInstalledTerminals(stack) + Upgrades.getEnergyCardMultiplier(upgrades)));
    }

    private ItemStack createCreativeStack(boolean charged) {
        ItemStack stack = new ItemStack(this);
        for (WirelessTerminalDefinition definition : WirelessTerminalRegistry.allDefinitions()) {
            addTerminal(stack, new ItemStack(definition.item()), definition.item());
        }

        if (charged) {
            injectAEPower(stack, getAEMaxPower(stack), Actionable.MODULATE);
        }

        return stack;
    }

}
