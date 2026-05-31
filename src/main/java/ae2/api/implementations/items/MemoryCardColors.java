package ae2.api.implementations.items;

import ae2.api.util.AEColor;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Describes the custom colors to show on a memory card item.
 */
public record MemoryCardColors(
    AEColor top1,
    AEColor top2,
    AEColor top3,
    AEColor top4,
    AEColor bottom1,
    AEColor bottom2,
    AEColor bottom3,
    AEColor bottom4) {
    public static final MemoryCardColors DEFAULT = new MemoryCardColors(
        AEColor.TRANSPARENT, AEColor.TRANSPARENT, AEColor.TRANSPARENT, AEColor.TRANSPARENT,
        AEColor.TRANSPARENT, AEColor.TRANSPARENT, AEColor.TRANSPARENT, AEColor.TRANSPARENT);

    public static MemoryCardColors fromArray(int[] colors) {
        if (colors == null) {
            return DEFAULT;
        }

        return new MemoryCardColors(
            getColor(colors, 0),
            getColor(colors, 1),
            getColor(colors, 2),
            getColor(colors, 3),
            getColor(colors, 4),
            getColor(colors, 5),
            getColor(colors, 6),
            getColor(colors, 7));
    }

    public static MemoryCardColors fromTag(NBTTagCompound tag, String key) {
        return fromArray(tag.getIntArray(key));
    }

    private static AEColor getColor(int[] colors, int index) {
        if (index >= colors.length) {
            return AEColor.TRANSPARENT;
        }
        int ordinal = colors[index];
        return ordinal >= 0 && ordinal < AEColor.values().length ? AEColor.values()[ordinal] : AEColor.TRANSPARENT;
    }

    public AEColor get(int x, int y) {
        int index = x + y * 4;
        return switch (index) {
            case 0 -> this.top1;
            case 1 -> this.top2;
            case 2 -> this.top3;
            case 3 -> this.top4;
            case 4 -> this.bottom1;
            case 5 -> this.bottom2;
            case 6 -> this.bottom3;
            case 7 -> this.bottom4;
            default -> AEColor.TRANSPARENT;
        };
    }

    public int[] toArray() {
        return new int[]{
            this.top1.ordinal(), this.top2.ordinal(), this.top3.ordinal(), this.top4.ordinal(),
            this.bottom1.ordinal(), this.bottom2.ordinal(), this.bottom3.ordinal(), this.bottom4.ordinal()
        };
    }
}
