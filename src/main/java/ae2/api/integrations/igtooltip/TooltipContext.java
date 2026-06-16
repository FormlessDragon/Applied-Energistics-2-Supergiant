package ae2.api.integrations.igtooltip;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.Vec3d;

public record TooltipContext(NBTTagCompound serverData, Vec3d hitLocation, EntityPlayer player) {
}
