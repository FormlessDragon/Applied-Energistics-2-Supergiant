package ae2.items.tools.quartz;

import net.minecraft.item.Item.ToolMaterial;
import net.minecraftforge.common.util.EnumHelper;

public final class QuartzToolType {
    public static final ToolMaterial CERTUS = EnumHelper.addToolMaterial("CERTUS_QUARTZ", 3, 768, 8.0F, 3.0F, 16);
    public static final ToolMaterial NETHER = EnumHelper.addToolMaterial("NETHER_QUARTZ", 3, 768, 8.0F, 3.0F, 16);

    private QuartzToolType() {
    }
}
