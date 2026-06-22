package ae2.client.render.overlay;

import ae2.api.parts.IPartCollisionHelper;
import ae2.parts.BusCollisionHelper;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class OverlayHighlightBoxes {
    private OverlayHighlightBoxes() {
    }

    public static List<AxisAlignedBB> create(OverlayHighlightShape shape, BlockPos pos, @Nullable EnumFacing side) {
        if (side == null || shape == OverlayHighlightShape.WHOLE_BLOCK) {
            return List.of(new AxisAlignedBB(pos));
        }

        ObjectArrayList<AxisAlignedBB> boxes = new ObjectArrayList<>(shape == OverlayHighlightShape.P2P_TUNNEL ? 3 : 2);
        IPartCollisionHelper helper = new BusCollisionHelper(boxes, side, true);
        switch (shape) {
            case PATTERN_PROVIDER -> addPatternProviderBoxes(helper);
            case P2P_TUNNEL -> addP2PTunnelBoxes(helper);
            case WHOLE_BLOCK -> throw new IllegalStateException("Whole block shape should have been handled first.");
        }
        ObjectArrayList<AxisAlignedBB> offsetBoxes = new ObjectArrayList<>(boxes.size());
        for (AxisAlignedBB box : boxes) {
            offsetBoxes.add(box.offset(pos));
        }
        return offsetBoxes;
    }

    private static void addPatternProviderBoxes(IPartCollisionHelper helper) {
        helper.addBox(2, 2, 14, 14, 14, 16);
        helper.addBox(5, 5, 12, 11, 11, 14);
    }

    private static void addP2PTunnelBoxes(IPartCollisionHelper helper) {
        helper.addBox(5, 5, 12, 11, 11, 13);
        helper.addBox(3, 3, 13, 13, 13, 14);
        helper.addBox(2, 2, 14, 14, 14, 16);
    }
}
