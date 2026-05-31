package ae2.api.components;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.item.ItemStack;

import java.util.List;
import java.util.Objects;

public record ExportedUpgrades(List<ItemStack> upgrades) {
    public ExportedUpgrades {
        upgrades = new ObjectArrayList<>(upgrades);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof ExportedUpgrades(List<ItemStack> upgrades1))) {
            return false;
        }
        if (this.upgrades.size() != upgrades1.size()) {
            return false;
        }
        for (int i = 0; i < this.upgrades.size(); i++) {
            if (!ItemStack.areItemStacksEqual(this.upgrades.get(i), upgrades1.get(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = 1;
        for (ItemStack stack : this.upgrades) {
            result = 31 * result + Objects.hashCode(stack == null ? null : stack.serializeNBT());
        }
        return result;
    }
}
