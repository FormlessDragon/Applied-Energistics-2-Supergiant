package ae2.integration.modules.igtooltip.blocks;

import ae2.api.integrations.igtooltip.TooltipBuilder;
import ae2.api.integrations.igtooltip.TooltipContext;
import ae2.api.integrations.igtooltip.providers.BodyProvider;
import ae2.api.integrations.igtooltip.providers.ServerDataProvider;
import ae2.api.stacks.AmountFormat;
import ae2.api.stacks.GenericStack;
import ae2.core.localization.GuiText;
import ae2.helpers.patternprovider.PatternProviderLogicHost;
import ae2.integration.modules.theoneprobe.TopText;
import ae2.integration.modules.theoneprobe.TopTooltipFormatter;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextFormatting;

public final class PatternProviderDataProvider
    implements BodyProvider<PatternProviderLogicHost>, ServerDataProvider<PatternProviderLogicHost> {

    private static final String NBT_LOCK_REASON = "craftingLockReason";
    private static final String NBT_LOCK_UNTIL_RESULT_STACK = "craftingLockUntilResultStack";

    @Override
    public void buildTooltip(PatternProviderLogicHost host, TooltipContext context, TooltipBuilder tooltip) {
        var lockReason = context.serverData().getString(NBT_LOCK_REASON);
        if (!lockReason.isEmpty()) {
            tooltip.addLine(TextFormatting.RED + tooltip.localize(lockReason));
        }

        var stack = context.serverData().getCompoundTag(NBT_LOCK_UNTIL_RESULT_STACK);
        if (!stack.isEmpty()) {
            var genericStack = GenericStack.readTag(stack);
            String stackName;
            String stackAmount;
            if (genericStack == null) {
                stackName = tooltip.localize(GuiText.Error);
                stackAmount = tooltip.localize(GuiText.Error);
            } else {
                stackName = TopTooltipFormatter.displayName(genericStack.what());
                stackAmount = genericStack.what().formatAmount(genericStack.amount(), AmountFormat.FULL);
            }
            tooltip.addLine(TextFormatting.RED + tooltip.localize(TopText.crafting_locked_until_result)
                + ": " + stackName + " (" + stackAmount + ")");
        }
    }

    @Override
    public void provideServerData(EntityPlayer player, PatternProviderLogicHost host, NBTTagCompound serverData) {
        var logic = host.getLogic();

        TopText reason = null;
        switch (logic.getCraftingLockedReason()) {
            case LOCK_UNTIL_PULSE -> reason = TopText.crafting_locked_until_pulse;
            case LOCK_WHILE_HIGH -> reason = TopText.crafting_locked_by_redstone_signal;
            case LOCK_WHILE_LOW -> reason = TopText.crafting_locked_by_lack_of_redstone_signal;
            case LOCK_UNTIL_RESULT -> {
                var stack = logic.getUnlockStack();
                if (stack != null) {
                    serverData.setTag(NBT_LOCK_UNTIL_RESULT_STACK, GenericStack.writeTag(stack));
                } else {
                    final NBTTagCompound errorDummy = new NBTTagCompound();
                    errorDummy.setString("error", "error");
                    serverData.setTag(NBT_LOCK_UNTIL_RESULT_STACK, errorDummy);
                }
                return;
            }
            case NONE -> {
            }
        }

        if (reason != null) {
            serverData.setString(NBT_LOCK_REASON, reason.getTranslationKey());
        }
    }
}
