package ae2.client;

import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import org.lwjgl.input.Keyboard;

/**
 * All client-side key bindings of AE2.
 * <p/>
 * Every entry is registered automatically by {@link ActionKeys#init()}, so adding a new key only requires adding an
 * enum constant plus its {@code key.ae2.<name>} translation.
 */
public enum ActionKey {

    LOCATE_INGREDIENT_FLOW(Keyboard.KEY_NONE, KeyConflictContext.GUI);

    private final KeyBinding binding;

    ActionKey(int defaultKey, KeyConflictContext conflictContext) {
        this(defaultKey, conflictContext, KeyModifier.NONE);
    }

    ActionKey(int defaultKey, KeyConflictContext conflictContext, KeyModifier modifier) {
        this.binding = new KeyBinding("key.ae2." + name().toLowerCase(), conflictContext, modifier, defaultKey,
            "key.ae2.category");
    }

    public KeyBinding getBinding() {
        return this.binding;
    }

    /**
     * @return true if the given key code triggers this action, ignoring conflicts with other keys.
     */
    public boolean isActiveAndMatches(int keyCode) {
        return keyCode != 0 && keyCode == this.binding.getKeyCode();
    }
}
