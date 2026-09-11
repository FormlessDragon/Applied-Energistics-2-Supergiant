package ae2.items.tools.quartz;

import ae2.api.util.IAEWrench;
import ae2.items.AEBaseItem;
import cofh.api.item.IToolHammer;
import crazypants.enderio.api.tool.ITool;
import crazypants.enderio.base.conduit.ConduitDisplayMode;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.Optional;
import org.jspecify.annotations.NonNull;

@Optional.Interface(iface = "crazypants.enderio.api.tool.ITool", modid = "enderio")
@Optional.Interface(iface = "cofh.api.item.IToolHammer", modid = "cofhcore")
public class QuartzWrenchItem extends AEBaseItem implements IToolHammer, IAEWrench, ITool {

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

    @Override
    public boolean canUse(@NonNull EnumHand enumHand, @NonNull EntityPlayer entityPlayer, @NonNull BlockPos blockPos) {
        return true;
    }

    @Override
    public void used(@NonNull EnumHand enumHand, @NonNull EntityPlayer entityPlayer, @NonNull BlockPos blockPos) {

    }

    @Override
    public boolean shouldHideFacades(@NonNull ItemStack itemStack, @NonNull EntityPlayer entityPlayer) {
        ConduitDisplayMode curMode = ConduitDisplayMode.getDisplayMode(itemStack);
        return curMode != ConduitDisplayMode.NONE && curMode != ConduitDisplayMode.NEUTRAL;
    }
}
