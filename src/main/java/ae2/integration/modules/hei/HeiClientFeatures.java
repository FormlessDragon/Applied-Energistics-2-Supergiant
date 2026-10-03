package ae2.integration.modules.hei;

import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.GenericStack;
import ae2.api.storage.StorageCells;
import ae2.client.ActionKey;
import ae2.client.gui.PreviousExternalGui;
import ae2.client.gui.me.common.GuiMEStorage;
import ae2.container.me.common.MEIngredientAction;
import ae2.core.AEConfig;
import ae2.core.localization.ButtonToolTips;
import ae2.core.localization.GuiText;
import ae2.core.network.InitNetwork;
import ae2.core.network.serverbound.HeiIngredientActionPacket;
import mezz.jei.config.KeyBindings;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.function.Consumer;

@SideOnly(Side.CLIENT)
final class HeiClientFeatures {
    private static final HeiClientFeatures INSTANCE = new HeiClientFeatures();
    private static boolean registered;

    private HeiClientFeatures() {
    }

    static void register() {
        if (registered) {
            return;
        }

        registered = true;
        MinecraftForge.EVENT_BUS.register(INSTANCE);
    }

    static void appendIngredientActionTooltip(ItemTooltipEvent event) {
        if (!AEConfig.instance().isShowHeiTooltip()) {
            return;
        }

        ItemStack tooltipStack = event.getItemStack();
        if (StorageCells.isCellHandled(tooltipStack)) {
            addTooltipLine(event, GuiText.CellViewShortcut.getLocal(KeyBindings.showRecipe.getDisplayName()));
        }

        Object hovered = getHoveredIngredient();
        GenericStack stack = GenericIngredientHelper.ingredientToStack(hovered);
        if (stack == null || !(stack.what() instanceof AEItemKey itemKey)) {
            return;
        }

        if (tooltipStack.isEmpty() || !itemKey.matches(tooltipStack)) {
            return;
        }

        if (Minecraft.getMinecraft().currentScreen instanceof GuiMEStorage<?>) {
            addTooltipLine(event, ButtonToolTips.HeiAutoPin.getLocal(ActionKey.HEI_RETRIEVE_INGREDIENT.getDisplayName()));
        } else {
            addTooltipLine(event,
                GuiText.HeiRetrieveIngredientTooltip.getLocal(ActionKey.HEI_RETRIEVE_INGREDIENT.getDisplayName()));
        }
        addTooltipLine(event, GuiText.HeiCraftIngredientTooltip.getLocal(ActionKey.HEI_CRAFT_INGREDIENT.getDisplayName()));
    }

    private static void handleInput(int eventKey, Consumer<Boolean> cancelEvent) {
        MEIngredientAction action = getAction(eventKey);
        if (action == null) {
            return;
        }

        GenericStack stack = GenericIngredientHelper.ingredientToStack(getHoveredIngredient());
        if (stack == null) {
            return;
        }

        if (action == MEIngredientAction.RETRIEVE
            && Minecraft.getMinecraft().currentScreen instanceof GuiMEStorage<?> terminalGui) {
            terminalGui.acceptAutoPin(stack.what());
            cancelEvent.accept(true);
            return;
        }

        if (action == MEIngredientAction.CRAFT) {
            PreviousExternalGui.capture(Minecraft.getMinecraft().currentScreen);
        }

        InitNetwork.sendToServer(new HeiIngredientActionPacket(action, stack));
        cancelEvent.accept(true);
    }

    @Nullable
    private static MEIngredientAction getAction(int eventKey) {
        if (ActionKey.HEI_RETRIEVE_INGREDIENT.isActiveAndMatches(eventKey)) {
            return MEIngredientAction.RETRIEVE;
        }
        if (ActionKey.HEI_CRAFT_INGREDIENT.isActiveAndMatches(eventKey)) {
            return MEIngredientAction.CRAFT;
        }
        return null;
    }

    @Nullable
    private static Object getHoveredIngredient() {
        var runtime = HeiPlugin.getRuntime();
        if (runtime == null) {
            return null;
        }

        Object ingredient = runtime.getIngredientListOverlay().getIngredientUnderMouse();
        if (ingredient != null) {
            return ingredient;
        }
        return runtime.getBookmarkOverlay().getIngredientUnderMouse();
    }

    private static void addTooltipLine(ItemTooltipEvent event, String line) {
        event.getToolTip().add(line);
    }

    @SubscribeEvent
    public void onKeyboardInput(GuiScreenEvent.KeyboardInputEvent.Pre event) {
        if (!Keyboard.getEventKeyState()) {
            return;
        }

        handleInput(Keyboard.getEventKey(), event::setCanceled);
    }

    @SubscribeEvent
    public void onMouseInput(GuiScreenEvent.MouseInputEvent.Pre event) {
        int button = Mouse.getEventButton();
        if (button < 0 || !Mouse.getEventButtonState()) {
            return;
        }

        handleInput(button - 100, event::setCanceled);
    }
}
