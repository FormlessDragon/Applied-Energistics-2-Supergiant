package appeng.util.ctl;

import io.netty.buffer.ByteBuf;

public class ByteBufUtils {

    private static final short SHORT = (0xFF - 1);
    private static final short INT   = (0xFF - 2);
    private static final short LONG  = (0xFF - 3);

    private static final long  UINT_MAX   = 0xFFFFFFFFL;
    private static final int   USHORT_MAX = 0xFFFF;
    private static final short UBYTE_MAX  = 0xFF - 4;

    public static void writeVarLong(ByteBuf buf, long value) {
        if (value > UINT_MAX) {
            writeUnsignedByte(buf, LONG);
            buf.writeLong(value);
            return;
        }
        if (value > USHORT_MAX) {
            writeUnsignedByte(buf, INT);
            writeUnsignedInt(buf, value);
            return;
        }
        if (value > UBYTE_MAX) {
            writeUnsignedByte(buf, SHORT);
            writeUnsignedShort(buf, (int) value);
            return;
        }
        writeUnsignedByte(buf, (short) value);
    }

    public static long readVarLong(ByteBuf buf) {
        short type = buf.readUnsignedByte();
        return switch (type) {
            case SHORT -> buf.readUnsignedShort();
            case INT -> buf.readUnsignedInt();
            case LONG -> buf.readLong();
            default -> type;
        };
    }

    public static void writeUnsignedByte(ByteBuf buf, short value) {
        if (value < 0 || value > 0xFF) {
            throw new IllegalArgumentException("Value out of range for unsigned byte: " + value);
        }
        buf.writeByte((byte) value);
    }

    public static void writeUnsignedShort(ByteBuf buf, int value) {
        if (value < 0 || value > 0xFFFF) {
            throw new IllegalArgumentException("Value out of range for unsigned short: " + value);
        }
        buf.writeShort((short) value);
    }

    public static void writeUnsignedInt(ByteBuf buf, long value) {
        if (value < 0 || value > 0xFFFFFFFFL) {
            throw new IllegalArgumentException("Value out of range for unsigned int: " + value);
        }
        buf.writeInt((int) value);
    }

}