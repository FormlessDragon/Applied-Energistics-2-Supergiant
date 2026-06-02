package ae2.parts.reporting;

import ae2.api.networking.IGridNode;
import ae2.api.parts.IPartItem;
import ae2.api.parts.IPartModel;
import ae2.api.storage.ILinkStatus;
import ae2.container.GuiIds;
import ae2.core.AppEng;
import ae2.core.gui.GuiOpener;
import ae2.helpers.RequesterTerminalHost;
import ae2.items.parts.PartModels;
import ae2.parts.PartModel;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3d;

public class RequesterTerminalPart extends AbstractDisplayPart implements RequesterTerminalHost {

    @PartModels
    public static final ResourceLocation MODEL_OFF = AppEng.makeId("part/requester_terminal_off");
    @PartModels
    public static final ResourceLocation MODEL_ON = AppEng.makeId("part/requester_terminal_on");

    public static final IPartModel MODELS_OFF = new PartModel(MODEL_BASE, MODEL_OFF, MODEL_STATUS_OFF);
    public static final IPartModel MODELS_ON = new PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_ON);
    public static final IPartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_HAS_CHANNEL);

    public RequesterTerminalPart(IPartItem<?> partItem) {
        super(partItem, true);
    }

    @Override
    public boolean onUseWithoutItem(EntityPlayer player, Vec3d pos) {
        if (!super.onUseWithoutItem(player, pos) && !isClientSide()) {
            GuiOpener.openPartGui(player, GuiIds.GuiKey.REQUESTER_TERMINAL, this);
        }
        return true;
    }

    @Override
    public boolean onUseItemOn(ItemStack heldItem, EntityPlayer player, EnumHand hand, Vec3d pos) {
        if (super.onUseItemOn(heldItem, player, hand, pos)) {
            return true;
        }
        return this.onUseWithoutItem(player, pos);
    }

    @Override
    public IPartModel getStaticModels() {
        return this.selectModel(MODELS_OFF, MODELS_ON, MODELS_HAS_CHANNEL);
    }

    @Override
    public IGridNode getGridNode() {
        return this.getMainNode().getNode();
    }

    @Override
    public ILinkStatus getLinkStatus() {
        return ILinkStatus.ofManagedNode(getMainNode());
    }
}
