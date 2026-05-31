package ae2.items.materials;

import ae2.core.localization.Tooltips;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;

public final class QuantumEntangledSingularityItem extends MaterialItem {
    private static final String ENTANGLED_SINGULARITY_ID = "entangled_singularity_id";

    @SideOnly(Side.CLIENT)
    @Override
    protected void addCheckedInformation(ItemStack stack, World world, List<String> lines,
                                         ITooltipFlag advancedTooltips) {
        super.addCheckedInformation(stack, world, lines, advancedTooltips);

        NBTBase singularityId = stack.hasTagCompound() ? stack.getTagCompound().getTag(ENTANGLED_SINGULARITY_ID) : null;
        if (singularityId instanceof NBTTagLong id) {
            lines.add(Tooltips.QuantumKey.getLocal(id.getLong()));
        }
    }
}
