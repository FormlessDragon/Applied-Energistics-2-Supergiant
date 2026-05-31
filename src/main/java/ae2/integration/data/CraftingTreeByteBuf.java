package ae2.integration.data;

import io.netty.buffer.ByteBuf;

final class CraftingTreeByteBuf {
    private static final short SHORT = 0xFF - 1;
    private static final short INT = 0xFF - 2;
    private static final short LONG = 0xFF - 3;

    private static final long UINT_MAX = 0xFFFFFFFFL;
    private static final int USHORT_MAX = 0xFFFF;
    private static final short UBYTE_MAX = 0xFF - 4;

    private CraftingTreeByteBuf() {
    }

    static void writeVarLong(ByteBuf buf, long value) {
        if (value > UINT_MAX) {
            writeUnsignedByte(buf, LONG);
            buf.writeLong(value);
            return;
        }
        if (value > USHORT_MAX) {
            writeUnsignedByte(buf, INT);
            buf.writeInt((int) value);
            return;
        }
        if (value > UBYTE_MAX) {
            writeUnsignedByte(buf, SHORT);
            buf.writeShort((int) value);
            return;
        }
        writeUnsignedByte(buf, (short) value);
    }

    static long readVarLong(ByteBuf buf) {
        short type = buf.readUnsignedByte();
        return switch (type) {
            case SHORT -> buf.readUnsignedShort();
            case INT -> buf.readUnsignedInt();
            case LONG -> buf.readLong();
            default -> type;
        };
    }

    private static void writeUnsignedByte(ByteBuf buf, short value) {
        if (value < 0 || value > 0xFF) {
            throw new IllegalArgumentException("Value out of range for unsigned byte: " + value);
        }
        buf.writeByte(value);
    }
}
