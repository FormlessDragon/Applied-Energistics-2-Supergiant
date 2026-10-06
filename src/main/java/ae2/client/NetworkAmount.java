package ae2.client;

import ae2.api.implementations.items.WirelessTerminalApi;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.AmountFormat;
import ae2.api.stacks.GenericStack;
import ae2.client.gui.me.common.GuiMEStorage;
import ae2.client.gui.me.common.RepoSlot;
import ae2.container.me.common.GridInventoryEntry;
import ae2.container.me.common.IClientRepo;
import ae2.core.AEConfig;
import ae2.core.gui.locator.GuiHostLocators;
import ae2.core.localization.GuiText;
import ae2.core.network.InitNetwork;
import ae2.core.network.clientbound.NetworkAmountResultPacket;
import ae2.core.network.serverbound.NetworkAmountQueryPacket;
import ae2.integration.modules.baubles.BaublesIntegration;
import ae2.integration.modules.hei.GenericIngredientHelper;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Shows how much of the hovered ingredient the player's ME network stores.
 */
@SideOnly(Side.CLIENT)
public class NetworkAmount {

    public static final NetworkAmount INSTANCE = new NetworkAmount();

    private static final int REFRESH_TICKS = 20;

    private static final int IDLE_TICKS = 40;
    private static final int SCAN_TICKS = 20;

    private static long inventoryTerminals;
    private static long baubleTerminals;

    private static final Object2LongOpenHashMap<AEKey> AMOUNTS = new Object2LongOpenHashMap<>();
    private static final Object2LongOpenHashMap<AEKey> REFRESH_AT = new Object2LongOpenHashMap<>();

    private static int clientTicks;
    private static int scanCountdown = SCAN_TICKS;

    private NetworkAmount() {
    }

    public static void tick() {
        clientTicks++;
        if (--scanCountdown <= 0) {
            scanCountdown = SCAN_TICKS;
            scanWirelessTerminals(Minecraft.getMinecraft().player);
        }
    }

    /**
     * Forgets a server session, whose amounts say nothing about the next one. The terminals the player carries are
     * scanned again right away rather than staying unknown for the rest of the scan period.
     */
    public static void clear() {
        AMOUNTS.clear();
        REFRESH_AT.clear();
        scanCountdown = 0;
    }

    public static void acceptAmountResult(AEKey what, long amount) {
        AMOUNTS.put(what, amount);
        // The next request is counted from the answer, so that a response slower than the refresh period does not make
        // the next frame ask again.
        REFRESH_AT.put(what,
            clientTicks + (amount == NetworkAmountResultPacket.NO_ANSWER ? IDLE_TICKS : REFRESH_TICKS));
        if (amount == NetworkAmountResultPacket.NO_ANSWER) {
            // The terminals may have moved since they were last looked for.
            scanCountdown = 0;
        }
    }

    @SubscribeEvent
    public void addNetworkAmountTooltip(ItemTooltipEvent event) {
        if (!AEConfig.instance().isShowAmountsInNetwork()) {
            return;
        }
        Object ingredient = hoveredIngredient(event);

        appendLine(event.getToolTip(), ingredient);
    }

    /**
     * Appends the amount of the network the given HEI ingredient belongs to. HEI draws the ingredients of its list and
     * its bookmarks itself, so the tooltip of an ingredient that is not an item never reaches
     * {@link ItemTooltipEvent}.
     */
    public static void appendTooltipLine(@Nullable Object ingredient, List<String> tooltip) {
        if (!AEConfig.instance().isShowAmountsInNetwork()) {
            return;
        }

        if (GenericIngredientHelper.hasItemStackTooltip(ingredient)) {
            return;
        }

        appendLine(tooltip, ingredient);
    }

    private static void appendLine(List<String> tooltip, Object ingredient) {
        GenericStack stack = toStack(ingredient);
        if (stack == null) {
            return;
        }

        AEKey what = stack.what();
        if (resolveFromOpenTerminal(tooltip, what)) {
            return;
        }

        if (!hasWirelessTerminal()) {
            return;
        }

        if (clientTicks >= REFRESH_AT.getLong(what)) {
            REFRESH_AT.put(what, clientTicks + REFRESH_TICKS);
            InitNetwork.sendToServer(new NetworkAmountQueryPacket(what, inventoryTerminals, baubleTerminals));
        }

        if (AMOUNTS.containsKey(what)) {
            long amount = AMOUNTS.getLong(what);
            if (amount == NetworkAmountResultPacket.NO_ANSWER) {
                return;
            }
            appendLine(tooltip, what, amount);
        } else {
            tooltip.add(TextFormatting.GRAY + GuiText.AmountsInNetworkQuerying.getLocal());
        }
    }

    private static boolean resolveFromOpenTerminal(List<String> tooltip, AEKey what) {
        if (!(Minecraft.getMinecraft().currentScreen instanceof GuiMEStorage<?> guiME)) {
            return false;
        }

        if (guiME.getSlotUnderMouse() instanceof RepoSlot) {
            return true;
        }

        IClientRepo repo = guiME.getContainer().getClientRepo();
        if (repo == null) {
            return false;
        }

        GridInventoryEntry entry = repo.getByKey(what);
        appendLine(tooltip, what, entry != null ? entry.storedAmount() : 0);
        return true;
    }

    private static void appendLine(List<String> tooltip, AEKey what, long amount) {
        tooltip.add(TextFormatting.GREEN + GuiText.AmountsInNetwork.getLocal(what.formatAmount(amount, AmountFormat.FULL)));
    }

    private static boolean hasWirelessTerminal() {
        return inventoryTerminals != 0 || baubleTerminals != 0;
    }

    private static void scanWirelessTerminals(EntityPlayer player) {
        if (player == null) {
            return;
        }

        long inventory = scanTerminals(player, player.inventory.getSizeInventory(), false);
        long baubles = scanTerminals(player, BaublesIntegration.getSlots(player), true);
        if (inventory != inventoryTerminals || baubles != baubleTerminals) {
            AMOUNTS.clear();
            REFRESH_AT.clear();
        }

        inventoryTerminals = inventory;
        baubleTerminals = baubles;
    }

    /**
     * Bit i of the result is set while slot i holds a wireless terminal.
     * <p>
     * A slot at or above {@link Long#SIZE} cannot be reported: {@code 1L << slot} wraps around and would claim a
     * terminal in a slot that holds none. The inventory has 41 slots and baubles has far fewer, so the limit
     * is never reached.
     */
    private static long scanTerminals(EntityPlayer player, int slots, boolean isBaubleSlot) {
        long terminals = 0;
        for (int slot = 0; slot < Math.min(slots, Long.SIZE); slot++) {
            ItemStack stack = isBaubleSlot
                ? GuiHostLocators.forBaubleSlot(slot).locateItem(player)
                : GuiHostLocators.forInventorySlot(slot).locateItem(player);
            if (WirelessTerminalApi.ofStack(stack) != null) {
                terminals |= 1L << slot;
            }
        }
        return terminals;
    }

    private static GenericStack toStack(@Nullable Object ingredient) {
        if (ingredient == null) {
            return null;
        }

        if (ingredient instanceof ItemStack itemStack) {
            GenericStack wrapped = GenericStack.unwrapItemStack(itemStack);
            if (wrapped != null) {
                return wrapped;
            }
        }

        return GenericIngredientHelper.ingredientToStack(ingredient);
    }

    @NotNull
    private static Object hoveredIngredient(ItemTooltipEvent event) {
        if (toStack(event.getItemStack()) != null) {
            return event.getItemStack();
        }

        Object ingredient = GenericIngredientHelper.getHoveredIngredient();
        return ingredient != null ? ingredient : event.getItemStack();
    }
}
