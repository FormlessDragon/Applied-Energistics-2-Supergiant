package ae2.util;

import ae2.me.NetworkData;
import ae2.me.ticker.ProfileData;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;

import java.awt.Rectangle;

public final class EmptyArrays {

    public static final byte[] EMPTY_BYTE_ARRAY = {};
    public static final long[] EMPTY_LONG_ARRAY = {};
    public static final String[] EMPTY_STRING_ARRAY = {};
    public static final Rectangle[] EMPTY_RECTANGLE_ARRAY = {};
    public static final EnumFacing[] EMPTY_FACING_ARRAY = {};
    public static final Object[] EMPTY_OBJECT_ARRAY = {};
    public static final ItemStack[] EMPTY_ITEM_STACK_ARRAY = {};
    public static final NetworkData.ALink[] EMPTY_NETWORK_DATA_ALINK_ARRAY = {};
    public static final NetworkData.ANode[] EMPTY_NETWORK_DATA_ANODE_ARRAY = {};
    public static final ProfileData.ATick[] EMPTY_PROFILE_DATA_ATICK_ARRAY = {};

    private EmptyArrays() {
    }

}
