package ae2.api.util;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

public interface IAEWrench {

    boolean isUsable(ItemStack var1, EntityLivingBase var2, BlockPos var3);

}
