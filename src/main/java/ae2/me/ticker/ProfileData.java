package ae2.me.ticker;

import ae2.core.AppEngBase;
import ae2.util.ColorData;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import net.minecraft.util.math.BlockPos;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class ProfileData {
    public static final int MAX_PROFILE_TICKS = 262_144;
    public static final ProfileData EMPTY = new ProfileData(new ATick[0]);

    public ATick[] ticks;
    private boolean corrupt;

    public ProfileData(ATick[] ticks) {
        this.ticks = ticks;
    }

    private ProfileData() {
    }

    public boolean isCorrupt() {
        return corrupt;
    }

    public static ProfileData read(ByteBuf buf) {
        ProfileData data = new ProfileData();
        try (var stream = new DataInputStream(new BufferedInputStream(new GZIPInputStream(new ByteBufInputStream(buf))))) {
            int len = stream.readInt();
            if (len < 0 || len > MAX_PROFILE_TICKS) {
                throw new IllegalArgumentException("Invalid profile tick count: " + len);
            }
            data.ticks = new ATick[len];
            for (int i = 0; i < len; i++) {
                int dimension = stream.readInt();
                BlockPos pos = BlockPos.fromLong(stream.readLong());
                double rate = stream.readDouble();
                data.ticks[i] = new ATick(dimension, pos, rate, getColor(rate));
            }
        } catch (Exception e) {
            AppEngBase.LOGGER.error("Fail to profile ticks. The packet is corrupted!", e);
            data.corrupt = true;
            data.ticks = new ATick[0];
        }
        return data;
    }

    public void write(ByteBuf buf) {
        try (var stream = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(new ByteBufOutputStream(buf))))) {
            stream.writeInt(ticks.length);
            for (ATick tick : ticks) {
                stream.writeInt(tick.dimension());
                stream.writeLong(tick.pos().toLong());
                stream.writeDouble(tick.rate());
            }
        } catch (Exception e) {
            AppEngBase.LOGGER.error("Fail to write tick profiler data.", e);
            corrupt = true;
        }
    }

    public static ColorData getColor(double rate) {
        float gradient = (float) Math.clamp(rate / 100.0, 0.0, 1.0);
        return new ColorData(Math.clamp(gradient, 0.07f, 0.7f), gradient, 1.0f - gradient, 0.0f);
    }

    public record ATick(int dimension, BlockPos pos, double rate, ColorData color) {
    }
}
