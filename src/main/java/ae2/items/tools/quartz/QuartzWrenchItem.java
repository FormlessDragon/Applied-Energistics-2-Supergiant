package ae2.items.tools.quartz;

import ae2.api.util.IAEWrench;
import ae2.items.AEBaseItem;
import cofh.api.item.IToolHammer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.Optional;

@Optional.Interface(iface = "cofh.api.item.IToolHammer", modid = "cofhcore")
public class QuartzWrenchItem extends AEBaseItem implements IToolHammer, IAEWrench {

    public QuartzWrenchItem() {
        this.setMaxStackSize(1);
        this.setHarvestLevel("wrench", 0);
    }

    @Override
    public boolean isUsable(ItemStack itemStack, EntityLivingBase entityLivingBase, BlockPos blockPos) {
        return true;
    }

    @Override
    public boolean isUsable(ItemStack itemStack, EntityLivingBase entityLivingBase, Entity entity) {
        return true;
    }

    @Override
    public void toolUsed(ItemStack itemStack, EntityLivingBase entityLivingBase, BlockPos blockPos) {

    }

    @Override
    public void toolUsed(ItemStack itemStack, EntityLivingBase entityLivingBase, Entity entity) {

    }
}
