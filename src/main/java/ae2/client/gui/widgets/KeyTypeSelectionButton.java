package ae2.client.gui.widgets;

import ae2.api.stacks.AEKeyType;
import ae2.api.storage.ISubGuiHost;
import ae2.client.gui.AEBaseGui;
import ae2.client.gui.Icon;
import ae2.client.gui.implementations.GuiKeyTypeSelection;
import ae2.container.AEBaseContainer;
import ae2.container.interfaces.IKeyTypeSelectionContainer;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Supplier;

public class KeyTypeSelectionButton extends IconButton {

    private final ITextComponent title;
    private final Supplier<ITextComponent> descriptionSupplier;

    private KeyTypeSelectionButton(Runnable onPress, ITextComponent title, Supplier<ITextComponent> descriptionSupplier) {
        super(onPress);
        this.title = title;
        this.descriptionSupplier = descriptionSupplier;
    }

    public static <C extends AEBaseContainer & IKeyTypeSelectionContainer, P extends AEBaseGui<C>> KeyTypeSelectionButton create(
        P parentScreen,
        ISubGuiHost subGuiHost,
        ITextComponent title) {
        return new KeyTypeSelectionButton(
            () -> {
                if (GuiScreen.isShiftKeyDown()) {
                    handleShiftClick(parentScreen.getContainer());
                } else {
                    parentScreen.switchToScreen(new GuiKeyTypeSelection<>(parentScreen, subGuiHost, title));
                }
            },
            title,
            () -> {
                StringJoiner joiner = new StringJoiner(", ");
                for (var keyType : parentScreen.getContainer().getClientKeyTypeSelection().enabledSet()) {
                    joiner.add(keyType.getDescription().getFormattedText());
                }
                return new TextComponentString(joiner.toString());
            });
    }

    private static <C extends AEBaseContainer & IKeyTypeSelectionContainer> void handleShiftClick(C container) {
        Set<AEKeyType> newSelection = getNextSelection(container.getClientKeyTypeSelection());

        for (var keyType : newSelection) {
            container.selectKeyType(container.windowId, keyType, true);
        }
        for (var keyType : container.getClientKeyTypeSelection().enabledSet()) {
            if (!newSelection.contains(keyType)) {
                container.selectKeyType(container.windowId, keyType, false);
            }
        }
    }

    private static Set<AEKeyType> getNextSelection(IKeyTypeSelectionContainer.SyncedKeyTypes keyTypes) {
        int totalCount = keyTypes.keyTypes().size();
        int enabledCount = keyTypes.enabledSet().size();

        if (totalCount == enabledCount) {
            return Collections.singleton(keyTypes.keyTypes().keySet().iterator().next());
        } else if (enabledCount > 1) {
            return new ObjectLinkedOpenHashSet<>(keyTypes.keyTypes().keySet());
        } else {
            ObjectLinkedOpenHashSet<AEKeyType> enabledKeys = new ObjectLinkedOpenHashSet<>(keyTypes.enabledSet());
            AEKeyType currentKey = enabledKeys.getFirst();
            boolean foundCurrent = false;

            for (var keyType : keyTypes.keyTypes().keySet()) {
                if (foundCurrent) {
                    return Collections.singleton(keyType);
                }
                if (keyType == currentKey) {
                    foundCurrent = true;
                }
            }

            return new ObjectLinkedOpenHashSet<>(keyTypes.keyTypes().keySet());
        }
    }

    @Override
    public List<ITextComponent> getTooltipMessage() {
        return List.of(title, descriptionSupplier.get());
    }

    @Override
    protected Icon getIcon() {
        return Icon.TYPE_FILTER_ALL;
    }
}
