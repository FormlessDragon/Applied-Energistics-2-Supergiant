package ae2.hooks;

import ae2.api.implementations.items.IFacadeItem;
import ae2.api.parts.IFacadePart;
import ae2.api.parts.IPart;
import ae2.api.parts.IPartHost;
import ae2.api.parts.IPartItem;
import ae2.core.AEConfig;
import ae2.core.definitions.AEParts;
import ae2.facade.FacadePart;
import ae2.items.parts.FacadeItem;
import ae2.parts.BusCollisionHelper;
import ae2.parts.PartPlacement;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import net.minecraftforge.client.event.DrawBlockHighlightEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

import java.util.List;

@SideOnly(Side.CLIENT)
public class RenderBlockOutlineHook {

    private static boolean replaceBlockOutline(World world, EntityPlayer player, RayTraceResult hitResult,
                                               float partialTicks) {
        if (AEConfig.instance().isPlacementPreviewEnabled()) {
            ItemStack itemInHand = player.getHeldItemMainhand();
            showPartPlacementPreview(player, hitResult, itemInHand, partialTicks, true);
            showPartPlacementPreview(player, hitResult, itemInHand, partialTicks, false);
        }

        BlockPos pos = hitResult.getBlockPos();
        if (world.getTileEntity(pos) instanceof IPartHost partHost) {
            if (AEConfig.instance().isPlacementPreviewEnabled()) {
                ItemStack itemInHand = player.getHeldItemMainhand();
                showFacadePlacementPreview(hitResult, partHost, itemInHand, partialTicks, true);
                showFacadePlacementPreview(hitResult, partHost, itemInHand, partialTicks, false);
            }

            var selectedPart = partHost.selectPartWorld(hitResult.hitVec);
            if (selectedPart.facade != null) {
                renderFacade(pos, selectedPart.facade, selectedPart.side, false, false, partialTicks);
                return true;
            }
            if (selectedPart.part != null) {
                renderPart(pos, selectedPart.part, selectedPart.side, false, false, partialTicks);
                return true;
            }
        }

        return false;
    }

    private static void showFacadePlacementPreview(RayTraceResult hitResult, IPartHost partHost, ItemStack itemInHand,
                                                   float partialTicks, boolean insideBlock) {
        if (!(itemInHand.getItem() instanceof IFacadeItem facadeItem)) {
            return;
        }

        EnumFacing side = hitResult.sideHit;
        IFacadePart facade = facadeItem.createPartFromItemStack(itemInHand, side);
        if (!(facade instanceof FacadePart facadePart) || !FacadeItem.canPlaceFacade(partHost, facadePart)) {
            return;
        }

        if (partHost.getPart(side) == null) {
            renderPart(hitResult.getBlockPos(), AEParts.CABLE_ANCHOR.get().createPart(), side, true, insideBlock,
                partialTicks);
        }

        renderFacade(hitResult.getBlockPos(), facade, side, true, insideBlock, partialTicks);
    }

    private static void showPartPlacementPreview(EntityPlayer player, RayTraceResult hitResult, ItemStack itemInHand,
                                                 float partialTicks, boolean insideBlock) {
        if (!(itemInHand.getItem() instanceof IPartItem<?> partItem)) {
            return;
        }

        PartPlacement.Placement placement = PartPlacement.getPartPlacement(player, player.world, itemInHand,
            hitResult.getBlockPos(), hitResult.sideHit, hitResult.hitVec);
        if (placement == null) {
            return;
        }

        renderPart(placement.pos(), partItem.createPart(), placement.side(), true, insideBlock, partialTicks);
    }

    private static void renderPart(BlockPos pos, IPart part, EnumFacing side, boolean preview, boolean insideBlock,
                                   float partialTicks) {
        List<AxisAlignedBB> boxes = new ObjectArrayList<>();
        BusCollisionHelper helper = new BusCollisionHelper(boxes, side, true);
        part.getBoxes(helper);
        renderBoxes(pos, boxes, preview, insideBlock, partialTicks);
    }

    private static void renderFacade(BlockPos pos, IFacadePart facade, EnumFacing side, boolean preview,
                                     boolean insideBlock, float partialTicks) {
        List<AxisAlignedBB> boxes = new ObjectArrayList<>();
        BusCollisionHelper helper = new BusCollisionHelper(boxes, side, true);
        facade.getBoxes(helper, false);
        renderBoxes(pos, boxes, preview, insideBlock, partialTicks);
    }

    private static void renderBoxes(BlockPos pos, List<AxisAlignedBB> boxes, boolean preview, boolean insideBlock,
                                    float partialTicks) {
        Entity view = Minecraft.getMinecraft().getRenderViewEntity();
        if (view == null) {
            return;
        }

        double viewX = view.lastTickPosX + (view.posX - view.lastTickPosX) * partialTicks;
        double viewY = view.lastTickPosY + (view.posY - view.lastTickPosY) * partialTicks;
        double viewZ = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * partialTicks;
        float alpha = insideBlock ? 0.2F : preview ? 0.6F : 0.4F;
        float color = preview ? 1.0F : 0.0F;

        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ZERO);
        GlStateManager.glLineWidth(2.0F);
        GlStateManager.disableTexture2D();
        GlStateManager.depthMask(false);
        if (insideBlock) {
            GlStateManager.depthFunc(GL11.GL_GREATER);
        }

        for (AxisAlignedBB box : boxes) {
            AxisAlignedBB renderBox = box.grow(0.002D).offset(pos).offset(-viewX, -viewY, -viewZ);
            RenderGlobal.drawSelectionBoundingBox(renderBox, color, color, color, alpha);
        }

        if (insideBlock) {
            GlStateManager.depthFunc(GL11.GL_LEQUAL);
        }
        GlStateManager.glLineWidth(1.0F);
        GlStateManager.depthMask(true);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    @SubscribeEvent
    public void handleEvent(DrawBlockHighlightEvent event) {
        EntityPlayer player = event.getPlayer();
        RayTraceResult target = event.getTarget();
        if (player == null || target == null || target.typeOfHit != RayTraceResult.Type.BLOCK) {
            return;
        }

        if (replaceBlockOutline(player.world, player, target, event.getPartialTicks())) {
            event.setCanceled(true);
        }
    }
}
