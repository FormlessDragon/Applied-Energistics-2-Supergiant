package ae2.util;

import ae2.me.NetworkData;
import ae2.me.ticker.ProfileData;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;

public final class EmptyArrays {

    public static final byte[] EMPTY_BYTE_ARRAY = new byte[0];
    public static final long[] EMPTY_LONG_ARRAY = new long[0];
    public static final String[] EMPTY_STRING_ARRAY = new String[0];
    public static final EnumFacing[] EMPTY_FACING_ARRAY = new EnumFacing[0];
    public static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];
    public static final ItemStack[] EMPTY_ITEM_STACK_ARRAY = new ItemStack[0];
    public static final NetworkData.ALink[] EMPTY_NETWORK_DATA_ALINK_ARRAY = new NetworkData.ALink[0];
    public static final NetworkData.ANode[] EMPTY_NETWORK_DATA_ANODE_ARRAY = new NetworkData.ANode[0];
    public static final ProfileData.ATick[] EMPTY_PROFILE_DATA_ATICK_ARRAY = new ProfileData.ATick[0];

    private EmptyArrays() {
    }

}
