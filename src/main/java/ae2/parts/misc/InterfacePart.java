package ae2.parts.misc;

import ae2.api.inventories.InternalInventory;
import ae2.api.networking.GridHelper;
import ae2.api.networking.IGridNode;
import ae2.api.networking.IGridNodeListener;
import ae2.api.networking.IManagedGridNode;
import ae2.api.parts.IPartCollisionHelper;
import ae2.api.parts.IPartItem;
import ae2.api.parts.IPartModel;
import ae2.api.util.AECableType;
import ae2.core.AppEng;
import ae2.core.gui.locator.GuiHostLocators;
import ae2.helpers.InterfaceLogic;
import ae2.helpers.InterfaceLogicHost;
import ae2.items.parts.PartModels;
import ae2.parts.AEBasePart;
import ae2.parts.PartModel;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.common.util.Constants;

import org.jetbrains.annotations.Nullable;
import java.util.List;

public class InterfacePart extends AEBasePart implements InterfaceLogicHost {
    private static final String CELL_TERMINAL_SUBNET_ID_TAG = "cellTerminalSubnetId";

    public static final ResourceLocation MODEL_BASE = AppEng.makeId("part/interface_base");
    @PartModels
    public static final PartModel MODELS_OFF = new PartModel(MODEL_BASE, AppEng.makeId("part/interface_off"));
    @PartModels
    public static final PartModel MODELS_ON = new PartModel(MODEL_BASE, AppEng.makeId("part/interface_on"));
    @PartModels
    public static final PartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE,
        AppEng.makeId("part/interface_has_channel"));
    private static final IGridNodeListener<InterfacePart> NODE_LISTENER = new NodeListener<>() {
        @Override
        public void onGridChanged(InterfacePart nodeOwner, IGridNode node) {
            super.onGridChanged(nodeOwner, node);
            nodeOwner.getInterfaceLogic().gridChanged();
        }
    };
    private final InterfaceLogic logic = this.createLogic();
    @Nullable
    private String cellTerminalSubnetId;

    public InterfacePart(IPartItem<?> partItem) {
        super(partItem);
    }

    protected InterfaceLogic createLogic() {
        return new InterfaceLogic(this.getMainNode(), this, this.getPartItem().asItem());
    }

    @Override
    public void saveChanges() {
        this.getHost().markForSave();
    }

    @Override
    protected IManagedGridNode createMainNode() {
        return GridHelper.createManagedNode(this, NODE_LISTENER);
    }

    @Override
    protected void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);
        if (this.getMainNode().hasGridBooted()) {
            this.logic.notifyNeighbors();
        }
    }

    @Override
    public void getBoxes(IPartCollisionHelper bch) {
        bch.addBox(2, 2, 14, 14, 14, 16);
        bch.addBox(5, 5, 12, 11, 11, 14);
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        this.logic.readFromNBT(data);
        this.cellTerminalSubnetId = data.hasKey(CELL_TERMINAL_SUBNET_ID_TAG, Constants.NBT.TAG_STRING)
            ? data.getString(CELL_TERMINAL_SUBNET_ID_TAG)
            : null;
    }

    @Override
    public void writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        this.logic.writeToNBT(data);
        if (this.cellTerminalSubnetId != null && !this.cellTerminalSubnetId.isEmpty()) {
            data.setString(CELL_TERMINAL_SUBNET_ID_TAG, this.cellTerminalSubnetId);
        }
    }

    @Override
    public void addAdditionalDrops(List<ItemStack> drops, boolean wrenched) {
        super.addAdditionalDrops(drops, wrenched);
        this.logic.addDrops(drops);
    }

    @Override
    public void clearContent() {
        super.clearContent();
        this.logic.clearContent();
    }

    @Override
    public float getCableConnectionLength(AECableType cable) {
        return 4;
    }

    @Override
    public boolean onUseWithoutItem(EntityPlayer player, Vec3d pos) {
        if (!player.world.isRemote) {
            this.openGui(player, GuiHostLocators.forPart(this));
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
    public InterfaceLogic getInterfaceLogic() {
        return this.logic;
    }

    @Override
    public @Nullable String getCellTerminalSubnetId() {
        return this.cellTerminalSubnetId;
    }

    @Override
    public void setCellTerminalSubnetId(String subnetId) {
        if (subnetId == null || subnetId.isEmpty()) {
            throw new IllegalArgumentException("subnetId must not be empty");
        }
        if (!subnetId.equals(this.cellTerminalSubnetId)) {
            this.cellTerminalSubnetId = subnetId;
            saveChanges();
        }
    }

    @Override
    public IPartModel getStaticModels() {
        if (this.isActive() && this.isPowered()) {
            return MODELS_HAS_CHANNEL;
        } else if (this.isPowered()) {
            return MODELS_ON;
        } else {
            return MODELS_OFF;
        }
    }

    @Nullable
    @Override
    public InternalInventory getSubInventory(ResourceLocation id) {
        if (id.equals(UPGRADES)) {
            return this.logic.getUpgrades();
        }
        return super.getSubInventory(id);
    }

    @Override
    public ItemStack getMainContainerIcon() {
        return this.getPartItem().asItemStack();
    }
}
