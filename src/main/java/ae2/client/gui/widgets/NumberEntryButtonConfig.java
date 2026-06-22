package ae2.client.gui.widgets;

import ae2.core.AELog;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

@SideOnly(Side.CLIENT)
public final class NumberEntryButtonConfig {
    public static final long[] DEFAULT_ADD_STEPS = new long[]{1, 10, 100, 1000};
    public static final long[] DEFAULT_SHIFT_ADD_STEPS = new long[]{1, 16, 32, 64};
    public static final long[] DEFAULT_CTRL_MULTIPLY_FACTORS = new long[]{2, 3, 5, 10};
    public static final long[] DEFAULT_ALT_MULTIPLY_FACTORS = new long[]{10, 100, 1000, 10000};
    public static final OperationMode DEFAULT_OPERATION_MODE = OperationMode.ADD_SUBTRACT;
    public static final OperationMode DEFAULT_SHIFT_OPERATION_MODE = OperationMode.ADD_SUBTRACT;
    public static final OperationMode DEFAULT_CTRL_OPERATION_MODE = OperationMode.MULTIPLY_DIVIDE;
    public static final OperationMode DEFAULT_ALT_OPERATION_MODE = OperationMode.MULTIPLY_DIVIDE;
    private static final int VERSION = 3;
    private static final int BUTTON_COUNT = 4;
    private static final String DATA_DIR = "ae2";
    private static final String DATA_FILE = "number_entry_buttons.dat";
    private static final String TAG_VERSION = "version";
    private static final String TAG_DEFAULT_ADD_STEPS = "defaultAddSteps";
    private static final String TAG_SHIFT_ADD_STEPS = "shiftAddSteps";
    private static final String TAG_CTRL_MULTIPLY_FACTORS = "ctrlMultiplyFactors";
    private static final String TAG_ALT_MULTIPLY_FACTORS = "altMultiplyFactors";
    private static final String TAG_DEFAULT_OPERATION_MODE = "defaultOperationMode";
    private static final String TAG_SHIFT_OPERATION_MODE = "shiftOperationMode";
    private static final String TAG_CTRL_OPERATION_MODE = "ctrlOperationMode";
    private static final String TAG_ALT_OPERATION_MODE = "altOperationMode";

    private static Data data;

    private NumberEntryButtonConfig() {
    }

    public static Data get() {
        if (data == null) {
            data = load();
        }
        return data;
    }

    public static void save(Data newData) {
        data = sanitize(newData);
        write(data);
    }

    public static void resetToDefaults() {
        save(defaultData());
    }

    public static Data defaultData() {
        return new Data(DEFAULT_ADD_STEPS, DEFAULT_SHIFT_ADD_STEPS, DEFAULT_CTRL_MULTIPLY_FACTORS,
            DEFAULT_ALT_MULTIPLY_FACTORS, DEFAULT_OPERATION_MODE, DEFAULT_SHIFT_OPERATION_MODE,
            DEFAULT_CTRL_OPERATION_MODE, DEFAULT_ALT_OPERATION_MODE);
    }

    public static boolean isValidGroup(long[] values) {
        if (values == null || values.length != BUTTON_COUNT) {
            return false;
        }
        for (long value : values) {
            if (value <= 0) {
                return false;
            }
        }
        return true;
    }

    private static Data load() {
        File file = getFile();
        if (!file.isFile()) {
            return defaultData();
        }

        try {
            NBTTagCompound root = CompressedStreamTools.read(file);
            if (root == null) {
                return defaultData();
            }
            return sanitize(new Data(
                readGroup(root, TAG_DEFAULT_ADD_STEPS, DEFAULT_ADD_STEPS),
                readGroup(root, TAG_SHIFT_ADD_STEPS, DEFAULT_SHIFT_ADD_STEPS),
                readGroup(root, TAG_CTRL_MULTIPLY_FACTORS, DEFAULT_CTRL_MULTIPLY_FACTORS),
                readGroup(root, TAG_ALT_MULTIPLY_FACTORS, DEFAULT_ALT_MULTIPLY_FACTORS),
                readMode(root, TAG_DEFAULT_OPERATION_MODE, DEFAULT_OPERATION_MODE),
                readMode(root, TAG_SHIFT_OPERATION_MODE, DEFAULT_SHIFT_OPERATION_MODE),
                readMode(root, TAG_CTRL_OPERATION_MODE, DEFAULT_CTRL_OPERATION_MODE),
                readMode(root, TAG_ALT_OPERATION_MODE, DEFAULT_ALT_OPERATION_MODE)));
        } catch (IOException | RuntimeException e) {
            AELog.warn("Failed to load AE2 number entry button config from %s: %s", file, e);
            return defaultData();
        }
    }

    private static void write(Data data) {
        File file = getFile();
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            AELog.warn("Failed to create AE2 client data directory %s", parent);
            return;
        }

        NBTTagCompound root = new NBTTagCompound();
        root.setInteger(TAG_VERSION, VERSION);
        root.setTag(TAG_DEFAULT_ADD_STEPS, writeGroup(data.defaultAddSteps()));
        root.setTag(TAG_SHIFT_ADD_STEPS, writeGroup(data.shiftAddSteps()));
        root.setTag(TAG_CTRL_MULTIPLY_FACTORS, writeGroup(data.ctrlMultiplyFactors()));
        root.setTag(TAG_ALT_MULTIPLY_FACTORS, writeGroup(data.altMultiplyFactors()));
        root.setString(TAG_DEFAULT_OPERATION_MODE, data.defaultOperationMode().name());
        root.setString(TAG_SHIFT_OPERATION_MODE, data.shiftOperationMode().name());
        root.setString(TAG_CTRL_OPERATION_MODE, data.ctrlOperationMode().name());
        root.setString(TAG_ALT_OPERATION_MODE, data.altOperationMode().name());

        try {
            CompressedStreamTools.safeWrite(root, file);
        } catch (IOException | RuntimeException e) {
            AELog.warn("Failed to save AE2 number entry button config to %s: %s", file, e);
        }
    }

    private static long[] readGroup(NBTTagCompound root, String tagName, long[] fallback) {
        NBTTagList list = root.getTagList(tagName, 4);
        if (list.tagCount() != BUTTON_COUNT) {
            return copy(fallback);
        }

        LongArrayList values = new LongArrayList(BUTTON_COUNT);
        for (int i = 0; i < list.tagCount(); i++) {
            long value = ((NBTTagLong) list.get(i)).getLong();
            if (value <= 0) {
                return copy(fallback);
            }
            values.add(value);
        }
        return values.toLongArray();
    }

    private static OperationMode readMode(NBTTagCompound root, String tagName, OperationMode fallback) {
        if (!root.hasKey(tagName, 8)) {
            return fallback;
        }

        String value = root.getString(tagName);
        try {
            return OperationMode.valueOf(value);
        } catch (IllegalArgumentException e) {
            AELog.warn("Invalid AE2 number entry button operation mode %s for tag %s", value, tagName);
            return fallback;
        }
    }

    private static NBTTagList writeGroup(long[] values) {
        NBTTagList list = new NBTTagList();
        for (long value : values) {
            list.appendTag(new NBTTagLong(value));
        }
        return list;
    }

    private static Data sanitize(Data input) {
        return new Data(
            isValidGroup(input.defaultAddSteps()) ? input.defaultAddSteps() : DEFAULT_ADD_STEPS,
            isValidGroup(input.shiftAddSteps()) ? input.shiftAddSteps() : DEFAULT_SHIFT_ADD_STEPS,
            isValidGroup(input.ctrlMultiplyFactors()) ? input.ctrlMultiplyFactors() : DEFAULT_CTRL_MULTIPLY_FACTORS,
            isValidGroup(input.altMultiplyFactors()) ? input.altMultiplyFactors() : DEFAULT_ALT_MULTIPLY_FACTORS,
            input.defaultOperationMode(),
            input.shiftOperationMode(),
            input.ctrlOperationMode(),
            input.altOperationMode());
    }

    private static long[] copy(long[] values) {
        return Arrays.copyOf(values, BUTTON_COUNT);
    }

    private static File getFile() {
        return new File(new File(Minecraft.getMinecraft().gameDir, DATA_DIR), DATA_FILE);
    }

    public enum OperationMode {
        ADD_SUBTRACT,
        MULTIPLY_DIVIDE;

        public OperationMode next() {
            return switch (this) {
                case ADD_SUBTRACT -> MULTIPLY_DIVIDE;
                case MULTIPLY_DIVIDE -> ADD_SUBTRACT;
            };
        }
    }

    public record Data(long[] defaultAddSteps, long[] shiftAddSteps, long[] ctrlMultiplyFactors,
                       long[] altMultiplyFactors, OperationMode defaultOperationMode,
                       OperationMode shiftOperationMode, OperationMode ctrlOperationMode,
                       OperationMode altOperationMode) {
        public Data {
            defaultAddSteps = copy(defaultAddSteps);
            shiftAddSteps = copy(shiftAddSteps);
            ctrlMultiplyFactors = copy(ctrlMultiplyFactors);
            altMultiplyFactors = copy(altMultiplyFactors);
            defaultOperationMode = Objects.requireNonNull(defaultOperationMode, "defaultOperationMode");
            shiftOperationMode = Objects.requireNonNull(shiftOperationMode, "shiftOperationMode");
            ctrlOperationMode = Objects.requireNonNull(ctrlOperationMode, "ctrlOperationMode");
            altOperationMode = Objects.requireNonNull(altOperationMode, "altOperationMode");
        }
    }
}
