package ae2.integration.modules.igtooltip.blocks;

import ae2.api.client.AEKeyRendering;
import ae2.api.integrations.igtooltip.TooltipBuilder;
import ae2.api.integrations.igtooltip.TooltipContext;
import ae2.api.integrations.igtooltip.providers.BodyProvider;
import ae2.api.integrations.igtooltip.providers.ServerDataProvider;
import ae2.api.stacks.AmountFormat;
import ae2.api.stacks.GenericStack;
import ae2.core.localization.GuiText;
import ae2.helpers.patternprovider.PatternProviderLogicHost;
import ae2.integration.modules.theoneprobe.TopText;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

public final class PatternProviderDataProvider
    implements BodyProvider<PatternProviderLogicHost>, ServerDataProvider<PatternProviderLogicHost> {

    private static final String NBT_LOCK_REASON = "craftingLockReason";
    private static final String NBT_LOCK_UNTIL_RESULT_STACK = "craftingLockUntilResultStack";

    @Override
    public void buildTooltip(PatternProviderLogicHost host, TooltipContext context, TooltipBuilder tooltip) {
        var lockReason = context.serverData().getString(NBT_LOCK_REASON);
        if (!lockReason.isEmpty()) {
            tooltip.addLine(ITextComponent.Serializer.jsonToComponent(lockReason));
        }

        var stack = context.serverData().getCompoundTag(NBT_LOCK_UNTIL_RESULT_STACK);
        if (!stack.isEmpty()) {
            var genericStack = GenericStack.readTag(stack);
            ITextComponent stackName;
            ITextComponent stackAmount;
            if (genericStack == null) {
                stackName = GuiText.Error.text();
                stackAmount = GuiText.Error.text();
            } else {
                stackName = AEKeyRendering.getDisplayName(genericStack.what());
                stackAmount = new TextComponentString(
                    genericStack.what().formatAmount(genericStack.amount(), AmountFormat.FULL));
            }
            tooltip.addLine(TopText.crafting_locked_until_result.text(stackName, stackAmount)
                                                                .setStyle(new Style().setColor(TextFormatting.RED)));
        }
    }

    @Override
    public void provideServerData(EntityPlayer player, PatternProviderLogicHost host, NBTTagCompound serverData) {
        var logic = host.getLogic();

        ITextComponent reason = null;
        switch (logic.getCraftingLockedReason()) {
            case LOCK_UNTIL_PULSE -> reason = TopText.crafting_locked_until_pulse.text();
            case LOCK_WHILE_HIGH -> reason = TopText.crafting_locked_by_redstone_signal.text();
            case LOCK_WHILE_LOW -> reason = TopText.crafting_locked_by_lack_of_redstone_signal.text();
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
            serverData.setString(NBT_LOCK_REASON,
                ITextComponent.Serializer.componentToJson(reason.createCopy().setStyle(new Style().setColor(TextFormatting.RED))));
        }
    }
}
