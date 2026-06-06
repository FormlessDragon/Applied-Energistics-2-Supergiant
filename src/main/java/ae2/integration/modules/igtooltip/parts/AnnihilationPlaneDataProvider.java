package ae2.integration.modules.igtooltip.parts;

import ae2.api.integrations.igtooltip.TooltipBuilder;
import ae2.api.integrations.igtooltip.TooltipContext;
import ae2.api.integrations.igtooltip.providers.BodyProvider;
import ae2.api.integrations.igtooltip.providers.ServerDataProvider;
import ae2.core.localization.InGameTooltip;
import ae2.parts.automation.AnnihilationPlanePart;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;

public class AnnihilationPlaneDataProvider
    implements BodyProvider<AnnihilationPlanePart>, ServerDataProvider<AnnihilationPlanePart> {
    private static final String TAG_ENCHANTMENTS = "planeEnchantments";

    @Override
    public void buildTooltip(AnnihilationPlanePart plane, TooltipContext context, TooltipBuilder tooltip) {
        var serverData = context.serverData();
        if (serverData.hasKey(TAG_ENCHANTMENTS, Constants.NBT.TAG_COMPOUND)) {
            tooltip.addLine(InGameTooltip.EnchantedWith);

            var enchantments = serverData.getCompoundTag(TAG_ENCHANTMENTS);
            for (var enchantmentId : enchantments.getKeySet()) {
                var enchantment = Enchantment.getEnchantmentByLocation(enchantmentId);
                var level = enchantments.getInteger(enchantmentId);
                if (enchantment != null) {
                    tooltip.addLine(tooltip.localize(enchantment.getName()) + " " + level);
                }
            }
        }
    }

    @Override
    public void provideServerData(EntityPlayer player, AnnihilationPlanePart plane, NBTTagCompound serverData) {
        var enchantments = plane.getEnchantments();
        if (enchantments != null && !enchantments.isEmpty()) {
            var enchantmentsTag = new NBTTagCompound();
            for (Object2IntMap.Entry<Enchantment> entry : enchantments.object2IntEntrySet()) {
                var registryName = entry.getKey().getRegistryName();
                if (registryName != null) {
                    enchantmentsTag.setInteger(registryName.toString(), entry.getIntValue());
                }
            }
            serverData.setTag(TAG_ENCHANTMENTS, enchantmentsTag);
        }
    }
}
