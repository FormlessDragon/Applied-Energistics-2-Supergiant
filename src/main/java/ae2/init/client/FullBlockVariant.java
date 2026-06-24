package ae2.init.client;

import ae2.api.orientation.BlockOrientation;
import ae2.api.orientation.RelativeSide;
import net.minecraft.util.EnumFacing;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

final class FullBlockVariant {
    private final EnumFacing facing;
    private final int spin;
    private final LinkedHashMap<String, String> properties;

    private FullBlockVariant(EnumFacing facing, int spin, LinkedHashMap<String, String> properties) {
        this.facing = facing;
        this.spin = spin;
        this.properties = properties;
    }

    static FullBlockVariant parse(String variant) {
        LinkedHashMap<String, String> properties = new LinkedHashMap<>();
        for (String property : variant.split(",")) {
            int equalsAt = property.indexOf('=');
            if (equalsAt <= 0 || equalsAt >= property.length() - 1) {
                throw new IllegalArgumentException("Invalid blockstate variant entry: " + property);
            }

            properties.put(property.substring(0, equalsAt), property.substring(equalsAt + 1));
        }

        String facingName = properties.get("facing");
        String spinText = properties.get("spin");
        if (facingName == null || spinText == null) {
            throw new IllegalArgumentException("Variant does not define facing/spin: " + variant);
        }

        EnumFacing facing = EnumFacing.byName(facingName);
        if (facing == null) {
            throw new IllegalArgumentException("Unknown facing Value: " + facingName);
        }

        int spin = Integer.parseInt(spinText);
        if (spin < 0 || spin > 3) {
            throw new IllegalArgumentException("Unsupported spin Value: " + spinText);
        }

        return new FullBlockVariant(facing, spin, properties);
    }

    EnumFacing facing() {
        return this.facing;
    }

    int spin() {
        return this.spin;
    }

    BlockOrientation orientation() {
        return BlockOrientation.get(this.facing, this.spin);
    }

    String withOrientation(BlockOrientation orientation) {
        return this.withFacingAndSpin(orientation.getSide(RelativeSide.FRONT), orientation.getSpin());
    }

    BlockOrientation rotationFrom(BlockOrientation baseOrientation) {
        BlockOrientation target = this.orientation();
        EnumFacing rotatedNorth = target.rotate(baseOrientation.resultingRotate(EnumFacing.NORTH));
        EnumFacing rotatedUp = target.rotate(baseOrientation.resultingRotate(EnumFacing.UP));
        return BlockOrientation.get(rotatedNorth, rotatedUp);
    }

    private String withFacingAndSpin(EnumFacing newFacing, int newSpin) {
        StringJoiner joiner = new StringJoiner(",");
        for (Map.Entry<String, String> property : this.properties.entrySet()) {
            String key = property.getKey();
            String value = property.getValue();
            if ("facing".equals(key)) {
                value = newFacing.getName();
            } else if ("spin".equals(key)) {
                value = Integer.toString(newSpin);
            }
            joiner.add(key + "=" + value);
        }
        return joiner.toString();
    }
}
