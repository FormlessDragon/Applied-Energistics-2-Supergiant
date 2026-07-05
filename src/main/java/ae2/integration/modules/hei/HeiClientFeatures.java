package ae2.integration.modules.hei;

import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.GenericStack;
import ae2.client.gui.PreviousExternalGui;
import ae2.client.gui.me.common.GuiMEStorage;
import ae2.container.me.common.MEIngredientAction;
import ae2.core.AEConfig;
import ae2.core.localization.ButtonToolTips;
import ae2.core.localization.GuiText;
import ae2.core.network.InitNetwork;
import ae2.core.network.serverbound.HeiIngredientActionPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.function.Consumer;

@SideOnly(Side.CLIENT)
final class HeiClientFeatures {
    private static final KeyBinding RETRIEVE = new KeyBinding(
        "key.ae2.hei_retrieve_ingredient",
        KeyConflictContext.GUI,
        KeyModifier.CONTROL,
        -98,
        "key.ae2.category");
    private static final KeyBinding CRAFT = new KeyBinding(
        "key.ae2.hei_craft_ingredient",
        KeyConflictContext.GUI,
        KeyModifier.ALT,
        -98,
        "key.ae2.category");
    private static final HeiClientFeatures INSTANCE = new HeiClientFeatures();
    private static boolean registered;

    private HeiClientFeatures() {
    }

    static void register() {
        if (registered) {
            return;
        }

        registered = true;
        ClientRegistry.registerKeyBinding(RETRIEVE);
        ClientRegistry.registerKeyBinding(CRAFT);
        MinecraftForge.EVENT_BUS.register(INSTANCE);
    }

    static void appendIngredientActionTooltip(ItemTooltipEvent event) {
        Object hovered = getHoveredIngredient();
        GenericStack stack = GenericIngredientHelper.ingredientToStack(hovered);
        if (stack == null || !(stack.what() instanceof AEItemKey itemKey)) {
            return;
        }

        ItemStack tooltipStack = event.getItemStack();
        if (tooltipStack.isEmpty() || !itemKey.matches(tooltipStack)) {
            return;
        }

        if (AEConfig.instance().isShowHeiTooltip()) {
            if (Minecraft.getMinecraft().currentScreen instanceof GuiMEStorage<?>) {
                addTooltipLine(event, ButtonToolTips.HeiAutoPin.getLocal(getKeyText(RETRIEVE)));
            } else {
                addTooltipLine(event, GuiText.HeiRetrieveIngredientTooltip.getLocal(getKeyText(RETRIEVE)));
            }
            addTooltipLine(event, GuiText.HeiCraftIngredientTooltip.getLocal(getKeyText(CRAFT)));
        }
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
        if (RETRIEVE.isActiveAndMatches(eventKey)) {
            return MEIngredientAction.RETRIEVE;
        }
        if (CRAFT.isActiveAndMatches(eventKey)) {
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

    private static String getKeyText(KeyBinding keyBinding) {
        return keyBinding.getKeyModifier().getLocalizedComboName(keyBinding.getKeyCode());
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
