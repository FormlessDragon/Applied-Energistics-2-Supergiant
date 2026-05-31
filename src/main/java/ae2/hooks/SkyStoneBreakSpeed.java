package ae2.hooks;

import ae2.core.definitions.AEBlocks;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * This hook is intended to essentially make sky stone blocks found in meteorites minable with iron tools while
 * multiplying their destroy time by 10. To accomplish this, the blocks are created with destroy time 50, and their
 * destroy time is divided by 10 if a tool _better_ than iron is used.
 */
public final class SkyStoneBreakSpeed {
    public static final int SPEEDUP_FACTOR = 10;
    private static final float IRON_TOOL_SPEED = 6.0F;

    @SubscribeEvent
    public void handleBreakFaster(PlayerEvent.BreakSpeed event) {
        if (event.getState().getBlock() != AEBlocks.SKY_STONE_BLOCK.block()) {
            return;
        }

        ItemStack tool = event.getEntityPlayer().getHeldItemMainhand();
        if (!tool.isEmpty() && tool.getDestroySpeed(event.getState()) > IRON_TOOL_SPEED) {
            event.setNewSpeed(event.getNewSpeed() * SPEEDUP_FACTOR);
        }
    }
}
