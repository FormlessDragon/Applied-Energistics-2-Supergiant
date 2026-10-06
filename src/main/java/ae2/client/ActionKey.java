package ae2.client;

import ae2.integration.Integrations;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import org.lwjgl.input.Keyboard;

import java.util.function.BooleanSupplier;

/**
 * All client-side key bindings of AE2.
 * <p>
 * Every entry is registered automatically by {@link ActionKeys#init()}, so adding a new key only requires adding an
 * enum constant plus its {@code key.ae2.<name>} translation.
 * <p>
 * An entry may declare an {@link #isAvailable() availability condition}. Unavailable entries are skipped during
 * registration, which lets optional integrations (e.g. HEI) contribute bindings without exposing dead keys in the
 * controls screen when the integration mod is absent.
 */
public enum ActionKey {

    MOUSE_WHEEL_ITEM_MODIFIER(Keyboard.KEY_LSHIFT),
    PART_PLACEMENT_OPPOSITE(Keyboard.KEY_LCONTROL),
    LOCATE_INGREDIENT_FLOW(Keyboard.KEY_NONE, KeyConflictContext.GUI),
    VIEW_PATTERN(Keyboard.KEY_P, KeyConflictContext.GUI),
    HEI_RETRIEVE_INGREDIENT(MouseButton.MIDDLE, KeyConflictContext.GUI, KeyModifier.CONTROL, () -> Integrations.hei().isEnabled()),
    HEI_CRAFT_INGREDIENT(MouseButton.MIDDLE, KeyConflictContext.GUI, KeyModifier.ALT, () -> Integrations.hei().isEnabled()),
    ;

    private final KeyBinding binding;
    private final BooleanSupplier availability;

    ActionKey(int defaultKey) {
        this(defaultKey, KeyConflictContext.UNIVERSAL, KeyModifier.NONE, () -> true);
    }

    ActionKey(int defaultKey, KeyConflictContext conflictContext) {
        this(defaultKey, conflictContext, KeyModifier.NONE, () -> true);
    }

    ActionKey(int defaultKey, KeyConflictContext conflictContext, KeyModifier modifier, BooleanSupplier availability) {
        this.binding = new KeyBinding("key.ae2." + name().toLowerCase(), conflictContext, modifier, defaultKey, "key.ae2.category");
        this.availability = availability;
    }

    public KeyBinding getBinding() {
        return this.binding;
    }

    public boolean isAvailable() {
        return this.availability.getAsBoolean();
    }

    public boolean isActiveAndMatches(int keyCode) {
        return this.binding.isActiveAndMatches(keyCode);
    }

    public boolean isKeyDown() {
        return this.binding.isKeyDown();
    }

    public boolean isUnbound() {
        return this.binding.getKeyCode() == Keyboard.KEY_NONE;
    }

    public String getDisplayName() {
        return this.binding.getKeyModifier().getLocalizedComboName(this.binding.getKeyCode());
    }

    public static final class MouseButton {
        public static final int MIDDLE = -98;
        public static final int RIGHT = -99;

        private MouseButton() {
        }
    }
}
