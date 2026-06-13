package ae2.client.render.cablebus;

import ae2.api.util.AECableType;
import ae2.api.util.AEColor;
import ae2.core.AppEng;
import ae2.helpers.cablebus.CableCoreType;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;

import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

class CableBuilder {

    private final EnumMap<CableCoreType, EnumMap<AEColor, TextureAtlasSprite>> coreTextures;
    private final EnumMap<AECableType, EnumMap<AEColor, TextureAtlasSprite>> connectionTextures;
    private final SmartCableTextures smartCableTextures;

    CableBuilder(Function<ResourceLocation, TextureAtlasSprite> bakedTextureGetter) {
        this.coreTextures = new EnumMap<>(CableCoreType.class);
        for (CableCoreType type : CableCoreType.values()) {
            EnumMap<AEColor, TextureAtlasSprite> colorTextures = new EnumMap<>(AEColor.class);
            for (AEColor color : AEColor.values()) {
                colorTextures.put(color, bakedTextureGetter.apply(type.getTexture(color)));
            }
            this.coreTextures.put(type, colorTextures);
        }

        this.connectionTextures = new EnumMap<>(AECableType.class);
        for (AECableType type : AECableType.VALIDCABLES) {
            EnumMap<AEColor, TextureAtlasSprite> colorTextures = new EnumMap<>(AEColor.class);
            for (AEColor color : AEColor.values()) {
                colorTextures.put(color, bakedTextureGetter.apply(getConnectionTexture(type, color)));
            }
            this.connectionTextures.put(type, colorTextures);
        }

        this.smartCableTextures = new SmartCableTextures(bakedTextureGetter);
    }

    static ResourceLocation getConnectionTexture(AECableType cableType, AEColor color) {
        String textureFolder = switch (cableType) {
            case GLASS -> "part/cable/glass/";
            case COVERED -> "part/cable/covered/";
            case SMART -> "part/cable/smart/";
            case DENSE_COVERED -> "part/cable/dense_covered/";
            case DENSE_SMART -> "part/cable/dense_smart/";
            default -> throw new IllegalStateException("Cable type " + cableType + " does not support connections.");
        };
        return AppEng.makeId(textureFolder + color.name().toLowerCase(Locale.ROOT));
    }

    static Collection<ResourceLocation> getTextureDependencies() {
        ObjectLinkedOpenHashSet<ResourceLocation> result = new ObjectLinkedOpenHashSet<>();
        result.addAll(CableCoreType.getTextureDependencies());
        result.addAll(SmartCableTextures.getTextureDependencies());
        for (AECableType type : AECableType.VALIDCABLES) {
            for (AEColor color : AEColor.values()) {
                result.add(getConnectionTexture(type, color));
            }
        }
        return result;
    }

    private static CubeBuilder createCubeBuilder(List<BakedQuad> quadsOut) {
        CubeBuilder cubeBuilder = new CubeBuilder(quadsOut);
        cubeBuilder.useStandardUV();
        return cubeBuilder;
    }

    private static void setStraightCableUVs(CubeBuilder cubeBuilder, EnumFacing facing, float x, float y) {
        switch (facing) {
            case DOWN, UP -> {
                cubeBuilder.setCustomUv(EnumFacing.NORTH, x, 0, y, x);
                cubeBuilder.setCustomUv(EnumFacing.EAST, x, 0, y, x);
                cubeBuilder.setCustomUv(EnumFacing.SOUTH, x, 0, y, x);
                cubeBuilder.setCustomUv(EnumFacing.WEST, x, 0, y, x);
            }
            case EAST, WEST -> {
                cubeBuilder.setCustomUv(EnumFacing.UP, 0, x, x, y);
                cubeBuilder.setCustomUv(EnumFacing.DOWN, 0, x, x, y);
                cubeBuilder.setCustomUv(EnumFacing.NORTH, 0, x, x, y);
                cubeBuilder.setCustomUv(EnumFacing.SOUTH, 0, x, x, y);
            }
            case NORTH, SOUTH -> {
                cubeBuilder.setCustomUv(EnumFacing.UP, x, 0, y, x);
                cubeBuilder.setCustomUv(EnumFacing.DOWN, x, 0, y, x);
                cubeBuilder.setCustomUv(EnumFacing.EAST, 0, x, x, y);
                cubeBuilder.setCustomUv(EnumFacing.WEST, 0, x, x, y);
            }
            default -> {
            }
        }
    }

    private static void addDenseCableSizedCube(EnumFacing facing, CubeBuilder cubeBuilder) {
        switch (facing) {
            case DOWN -> cubeBuilder.addCube(4, 0, 4, 12, 5, 12);
            case EAST -> cubeBuilder.addCube(11, 4, 4, 16, 12, 12);
            case NORTH -> cubeBuilder.addCube(4, 4, 0, 12, 12, 5);
            case SOUTH -> cubeBuilder.addCube(4, 4, 11, 12, 12, 16);
            case UP -> cubeBuilder.addCube(4, 11, 4, 12, 16, 12);
            case WEST -> cubeBuilder.addCube(0, 4, 4, 5, 12, 12);
            default -> {
            }
        }
    }

    private static void addStraightDenseCableSizedCube(EnumFacing facing, CubeBuilder cubeBuilder) {
        switch (facing) {
            case DOWN, UP -> {
                cubeBuilder.setUvRotation(EnumFacing.EAST, 3);
                cubeBuilder.addCube(3, -0.01f, 3, 13, 16.01f, 13);
                cubeBuilder.setUvRotation(EnumFacing.EAST, 0);
            }
            case EAST, WEST -> {
                cubeBuilder.setUvRotation(EnumFacing.SOUTH, 3);
                cubeBuilder.setUvRotation(EnumFacing.NORTH, 3);
                cubeBuilder.addCube(-0.01f, 3, 3, 16.01f, 13, 13);
                cubeBuilder.setUvRotation(EnumFacing.SOUTH, 0);
                cubeBuilder.setUvRotation(EnumFacing.NORTH, 0);
            }
            case NORTH, SOUTH -> {
                cubeBuilder.setUvRotation(EnumFacing.EAST, 3);
                cubeBuilder.setUvRotation(EnumFacing.WEST, 3);
                cubeBuilder.addCube(3, 3, -0.01f, 13, 13, 16.01f);
                cubeBuilder.setUvRotation(EnumFacing.EAST, 0);
                cubeBuilder.setUvRotation(EnumFacing.WEST, 0);
            }
            default -> {
            }
        }
    }

    private static void addCoveredCableSizedCube(EnumFacing facing, CubeBuilder cubeBuilder) {
        switch (facing) {
            case DOWN -> cubeBuilder.addCube(6, 0, 6, 10, 5, 10);
            case EAST -> cubeBuilder.addCube(11, 6, 6, 16, 10, 10);
            case NORTH -> cubeBuilder.addCube(6, 6, 0, 10, 10, 5);
            case SOUTH -> cubeBuilder.addCube(6, 6, 11, 10, 10, 16);
            case UP -> cubeBuilder.addCube(6, 11, 6, 10, 16, 10);
            case WEST -> cubeBuilder.addCube(0, 6, 6, 5, 10, 10);
            default -> {
            }
        }
    }

    private static void addStraightCoveredCableSizedCube(EnumFacing facing, CubeBuilder cubeBuilder) {
        switch (facing) {
            case DOWN, UP -> {
                cubeBuilder.setUvRotation(EnumFacing.EAST, 3);
                cubeBuilder.addCube(5, -0.01f, 5, 11, 16.01f, 11);
                cubeBuilder.setUvRotation(EnumFacing.EAST, 0);
            }
            case EAST, WEST -> {
                cubeBuilder.setUvRotation(EnumFacing.SOUTH, 3);
                cubeBuilder.setUvRotation(EnumFacing.NORTH, 3);
                cubeBuilder.addCube(-0.01f, 5, 5, 16.01f, 11, 11);
                cubeBuilder.setUvRotation(EnumFacing.SOUTH, 0);
                cubeBuilder.setUvRotation(EnumFacing.NORTH, 0);
            }
            case NORTH, SOUTH -> {
                cubeBuilder.setUvRotation(EnumFacing.EAST, 3);
                cubeBuilder.setUvRotation(EnumFacing.WEST, 3);
                cubeBuilder.addCube(5, 5, -0.01f, 11, 11, 16.01f);
                cubeBuilder.setUvRotation(EnumFacing.EAST, 0);
                cubeBuilder.setUvRotation(EnumFacing.WEST, 0);
            }
            default -> {
            }
        }
    }

    private static void addCoveredCableSizedCube(EnumFacing facing, int distanceFromEdge, CubeBuilder cubeBuilder) {
        switch (facing) {
            case DOWN -> cubeBuilder.addCube(6, distanceFromEdge, 6, 10, 5, 10);
            case EAST -> cubeBuilder.addCube(11, 6, 6, 16 - distanceFromEdge, 10, 10);
            case NORTH -> cubeBuilder.addCube(6, 6, distanceFromEdge, 10, 10, 5);
            case SOUTH -> cubeBuilder.addCube(6, 6, 11, 10, 10, 16 - distanceFromEdge);
            case UP -> cubeBuilder.addCube(6, 11, 6, 10, 16 - distanceFromEdge, 10);
            case WEST -> cubeBuilder.addCube(distanceFromEdge, 6, 6, 5, 10, 10);
            default -> {
            }
        }
    }

    @SuppressWarnings("unused")
    public void addCableCore(AECableType cableType, AEColor color, List<BakedQuad> quadsOut) {
        switch (cableType) {
            case GLASS -> this.addCableCore(CableCoreType.GLASS, color, quadsOut);
            case COVERED, SMART -> this.addCableCore(CableCoreType.COVERED, color, quadsOut);
            case DENSE_COVERED, DENSE_SMART -> this.addCableCore(CableCoreType.DENSE, color, quadsOut);
            default -> {
            }
        }
    }

    public void addCableCore(CableCoreType coreType, AEColor color, List<BakedQuad> quadsOut) {
        CubeBuilder cubeBuilder = createCubeBuilder(quadsOut);
        cubeBuilder.setTexture(this.coreTextures.get(coreType).get(color));
        switch (coreType) {
            case GLASS -> cubeBuilder.addCube(6, 6, 6, 10, 10, 10);
            case COVERED -> cubeBuilder.addCube(5, 5, 5, 11, 11, 11);
            case DENSE -> cubeBuilder.addCube(3, 3, 3, 13, 13, 13);
            default -> {
            }
        }
    }

    public void addGlassConnection(EnumFacing facing, AEColor cableColor, AECableType connectionType,
                                   boolean cableBusAdjacent, List<BakedQuad> quadsOut) {
        CubeBuilder cubeBuilder = createCubeBuilder(quadsOut);
        cubeBuilder.setDrawFaces(EnumSet.complementOf(EnumSet.of(facing)));

        if (connectionType != AECableType.GLASS && !cableBusAdjacent) {
            cubeBuilder.setTexture(this.connectionTextures.get(AECableType.COVERED).get(cableColor));
            this.addBigCoveredCableSizedCube(facing, cubeBuilder);
        }

        cubeBuilder.setTexture(this.connectionTextures.get(AECableType.GLASS).get(cableColor));
        switch (facing) {
            case DOWN -> cubeBuilder.addCube(6, 0, 6, 10, 6, 10);
            case EAST -> cubeBuilder.addCube(10, 6, 6, 16, 10, 10);
            case NORTH -> cubeBuilder.addCube(6, 6, 0, 10, 10, 6);
            case SOUTH -> cubeBuilder.addCube(6, 6, 10, 10, 10, 16);
            case UP -> cubeBuilder.addCube(6, 10, 6, 10, 16, 10);
            case WEST -> cubeBuilder.addCube(0, 6, 6, 6, 10, 10);
            default -> {
            }
        }
    }

    public void addStraightGlassConnection(EnumFacing facing, AEColor cableColor, List<BakedQuad> quadsOut) {
        CubeBuilder cubeBuilder = createCubeBuilder(quadsOut);
        cubeBuilder.setDrawFaces(EnumSet.complementOf(EnumSet.of(facing, facing.getOpposite())));
        cubeBuilder.setTexture(this.connectionTextures.get(AECableType.GLASS).get(cableColor));
        switch (facing) {
            case DOWN, UP -> cubeBuilder.addCube(6, 0, 6, 10, 16, 10);
            case NORTH, SOUTH -> cubeBuilder.addCube(6, 6, 0, 10, 10, 16);
            case EAST, WEST -> cubeBuilder.addCube(0, 6, 6, 16, 10, 10);
            default -> {
            }
        }
    }

    public void addConstrainedGlassConnection(EnumFacing facing, AEColor cableColor, int distanceFromEdge,
                                              List<BakedQuad> quadsOut) {
        if (distanceFromEdge >= 6) {
            return;
        }

        CubeBuilder cubeBuilder = createCubeBuilder(quadsOut);
        cubeBuilder.setTexture(this.connectionTextures.get(AECableType.GLASS).get(cableColor));
        switch (facing) {
            case DOWN -> cubeBuilder.addCube(6, distanceFromEdge, 6, 10, 6, 10);
            case EAST -> cubeBuilder.addCube(10, 6, 6, 16 - distanceFromEdge, 10, 10);
            case NORTH -> cubeBuilder.addCube(6, 6, distanceFromEdge, 10, 10, 6);
            case SOUTH -> cubeBuilder.addCube(6, 6, 10, 10, 10, 16 - distanceFromEdge);
            case UP -> cubeBuilder.addCube(6, 10, 6, 10, 16 - distanceFromEdge, 10);
            case WEST -> cubeBuilder.addCube(distanceFromEdge, 6, 6, 6, 10, 10);
            default -> {
            }
        }
    }

    public void addCoveredConnection(EnumFacing facing, AEColor cableColor, AECableType connectionType,
                                     boolean cableBusAdjacent, List<BakedQuad> quadsOut) {
        CubeBuilder cubeBuilder = createCubeBuilder(quadsOut);
        cubeBuilder.setDrawFaces(EnumSet.complementOf(EnumSet.of(facing)));
        cubeBuilder.setTexture(this.connectionTextures.get(AECableType.COVERED).get(cableColor));

        if (connectionType != AECableType.GLASS && !cableBusAdjacent) {
            this.addBigCoveredCableSizedCube(facing, cubeBuilder);
        }

        addCoveredCableSizedCube(facing, cubeBuilder);
    }

    public void addStraightCoveredConnection(EnumFacing facing, AEColor cableColor, List<BakedQuad> quadsOut) {
        CubeBuilder cubeBuilder = createCubeBuilder(quadsOut);
        cubeBuilder.setTexture(this.connectionTextures.get(AECableType.COVERED).get(cableColor));
        setStraightCableUVs(cubeBuilder, facing, 5, 11);
        addStraightCoveredCableSizedCube(facing, cubeBuilder);
    }

    public void addConstrainedCoveredConnection(EnumFacing facing, AEColor cableColor, int distanceFromEdge,
                                                List<BakedQuad> quadsOut) {
        if (distanceFromEdge >= 5) {
            return;
        }

        CubeBuilder cubeBuilder = createCubeBuilder(quadsOut);
        cubeBuilder.setTexture(this.connectionTextures.get(AECableType.COVERED).get(cableColor));
        addCoveredCableSizedCube(facing, distanceFromEdge, cubeBuilder);
    }

    public void addSmartConnection(EnumFacing facing, AEColor cableColor, AECableType connectionType,
                                   boolean cableBusAdjacent, int channels, List<BakedQuad> quadsOut) {
        if (connectionType == AECableType.COVERED || connectionType == AECableType.GLASS) {
            this.addCoveredConnection(facing, cableColor, connectionType, cableBusAdjacent, quadsOut);
            return;
        }

        CubeBuilder cubeBuilder = createCubeBuilder(quadsOut);
        cubeBuilder.setDrawFaces(EnumSet.complementOf(EnumSet.of(facing)));
        switch (facing) {
            case DOWN -> {
                cubeBuilder.setFlipU(EnumFacing.EAST, true);
                cubeBuilder.setFlipU(EnumFacing.NORTH, true);
            }
            case UP -> {
                cubeBuilder.setFlipU(EnumFacing.EAST, true);
                cubeBuilder.setFlipU(EnumFacing.NORTH, true);
                cubeBuilder.setFlipV(EnumFacing.DOWN, true);
            }
            case SOUTH -> cubeBuilder.setFlipU(EnumFacing.NORTH, true);
            case WEST -> {
                cubeBuilder.setFlipV(EnumFacing.DOWN, true);
                cubeBuilder.setFlipU(EnumFacing.EAST, true);
            }
            case EAST -> cubeBuilder.setFlipV(EnumFacing.DOWN, true);
            default -> {
            }
        }

        TextureAtlasSprite texture = this.connectionTextures.get(AECableType.SMART).get(cableColor);
        TextureAtlasSprite oddChannel = this.smartCableTextures.getOddTextureForChannels(channels);
        TextureAtlasSprite evenChannel = this.smartCableTextures.getEvenTextureForChannels(channels);
        cubeBuilder.setTexture(texture);

        if (connectionType != AECableType.GLASS && !cableBusAdjacent) {
            this.addBigCoveredCableSizedCube(facing, cubeBuilder);
            cubeBuilder.setEmissiveMaterial(true);
            cubeBuilder.setTexture(oddChannel);
            cubeBuilder.setColorRGB(cableColor.blackVariant);
            this.addBigCoveredCableSizedCube(facing, cubeBuilder);
            cubeBuilder.setTexture(evenChannel);
            cubeBuilder.setColorRGB(cableColor.whiteVariant);
            this.addBigCoveredCableSizedCube(facing, cubeBuilder);
            cubeBuilder.setEmissiveMaterial(false);
            cubeBuilder.setTexture(texture);
        }

        addCoveredCableSizedCube(facing, cubeBuilder);

        cubeBuilder.setEmissiveMaterial(true);
        cubeBuilder.setTexture(oddChannel);
        cubeBuilder.setColorRGB(cableColor.blackVariant);
        addCoveredCableSizedCube(facing, cubeBuilder);
        cubeBuilder.setTexture(evenChannel);
        cubeBuilder.setColorRGB(cableColor.whiteVariant);
        addCoveredCableSizedCube(facing, cubeBuilder);
        cubeBuilder.setEmissiveMaterial(false);
    }

    public void addStraightSmartConnection(EnumFacing facing, AEColor cableColor, int channels,
                                           List<BakedQuad> quadsOut) {
        CubeBuilder cubeBuilder = createCubeBuilder(quadsOut);
        switch (facing) {
            case EAST, WEST -> cubeBuilder.setFlipV(EnumFacing.DOWN, true);
            case UP, DOWN -> cubeBuilder.setFlipU(EnumFacing.NORTH, true);
            default -> {
            }
        }

        cubeBuilder.setTexture(this.connectionTextures.get(AECableType.SMART).get(cableColor));
        setStraightCableUVs(cubeBuilder, facing, 5, 11);
        addStraightCoveredCableSizedCube(facing, cubeBuilder);

        TextureAtlasSprite oddChannel = this.smartCableTextures.getOddTextureForChannels(channels);
        TextureAtlasSprite evenChannel = this.smartCableTextures.getEvenTextureForChannels(channels);
        cubeBuilder.setEmissiveMaterial(true);
        cubeBuilder.setTexture(oddChannel);
        cubeBuilder.setColorRGB(cableColor.blackVariant);
        addStraightCoveredCableSizedCube(facing, cubeBuilder);
        cubeBuilder.setTexture(evenChannel);
        cubeBuilder.setColorRGB(cableColor.whiteVariant);
        addStraightCoveredCableSizedCube(facing, cubeBuilder);
        cubeBuilder.setEmissiveMaterial(false);
    }

    public void addConstrainedSmartConnection(EnumFacing facing, AEColor cableColor, int distanceFromEdge, int channels,
                                              List<BakedQuad> quadsOut) {
        if (distanceFromEdge >= 5) {
            return;
        }

        CubeBuilder cubeBuilder = createCubeBuilder(quadsOut);
        switch (facing) {
            case UP, DOWN -> {
                cubeBuilder.setFlipU(EnumFacing.EAST, true);
                cubeBuilder.setFlipU(EnumFacing.NORTH, true);
            }
            case EAST, WEST -> cubeBuilder.setFlipV(EnumFacing.DOWN, true);
            default -> {
            }
        }

        cubeBuilder.setTexture(this.connectionTextures.get(AECableType.SMART).get(cableColor));
        addCoveredCableSizedCube(facing, distanceFromEdge, cubeBuilder);

        TextureAtlasSprite oddChannel = this.smartCableTextures.getOddTextureForChannels(channels);
        TextureAtlasSprite evenChannel = this.smartCableTextures.getEvenTextureForChannels(channels);
        cubeBuilder.setEmissiveMaterial(true);
        cubeBuilder.setTexture(oddChannel);
        cubeBuilder.setColorRGB(cableColor.blackVariant);
        addCoveredCableSizedCube(facing, distanceFromEdge, cubeBuilder);
        cubeBuilder.setTexture(evenChannel);
        cubeBuilder.setColorRGB(cableColor.whiteVariant);
        addCoveredCableSizedCube(facing, distanceFromEdge, cubeBuilder);
        cubeBuilder.setEmissiveMaterial(false);
    }

    public void addDenseCoveredConnection(EnumFacing facing, AEColor cableColor, AECableType connectionType,
                                          boolean cableBusAdjacent, List<BakedQuad> quadsOut) {
        if (connectionType == AECableType.COVERED || connectionType == AECableType.SMART
            || connectionType == AECableType.GLASS) {
            this.addCoveredConnection(facing, cableColor, connectionType, cableBusAdjacent, quadsOut);
            return;
        }

        CubeBuilder cubeBuilder = createCubeBuilder(quadsOut);
        cubeBuilder.setDrawFaces(EnumSet.complementOf(EnumSet.of(facing)));
        cubeBuilder.setTexture(this.connectionTextures.get(AECableType.DENSE_COVERED).get(cableColor));
        addDenseCableSizedCube(facing, cubeBuilder);
    }

    public void addDenseSmartConnection(EnumFacing facing, AEColor cableColor, AECableType connectionType,
                                        boolean cableBusAdjacent, int channels, List<BakedQuad> quadsOut) {
        if (connectionType == AECableType.SMART) {
            this.addSmartConnection(facing, cableColor, connectionType, cableBusAdjacent, channels, quadsOut);
            return;
        }
        if (connectionType == AECableType.COVERED || connectionType == AECableType.GLASS) {
            this.addCoveredConnection(facing, cableColor, connectionType, cableBusAdjacent, quadsOut);
            return;
        }
        if (connectionType == AECableType.DENSE_COVERED) {
            this.addDenseCoveredConnection(facing, cableColor, connectionType, cableBusAdjacent, quadsOut);
            return;
        }

        CubeBuilder cubeBuilder = createCubeBuilder(quadsOut);
        cubeBuilder.setDrawFaces(EnumSet.complementOf(EnumSet.of(facing)));
        switch (facing) {
            case WEST, EAST -> cubeBuilder.setFlipV(EnumFacing.DOWN, true);
            case UP, DOWN -> {
                cubeBuilder.setFlipU(EnumFacing.NORTH, true);
                cubeBuilder.setFlipU(EnumFacing.EAST, true);
            }
            default -> {
            }
        }

        cubeBuilder.setTexture(this.connectionTextures.get(AECableType.DENSE_SMART).get(cableColor));
        addDenseCableSizedCube(facing, cubeBuilder);

        int displayedChannels = (channels + 3) / 4;
        TextureAtlasSprite oddChannel = this.smartCableTextures.getOddTextureForDenseChannels(displayedChannels);
        TextureAtlasSprite evenChannel = this.smartCableTextures.getEvenTextureForDenseChannels(displayedChannels);

        cubeBuilder.setEmissiveMaterial(true);
        cubeBuilder.setTexture(oddChannel);
        cubeBuilder.setColorRGB(cableColor.blackVariant);
        addDenseCableSizedCube(facing, cubeBuilder);
        cubeBuilder.setTexture(evenChannel);
        cubeBuilder.setColorRGB(cableColor.whiteVariant);
        addDenseCableSizedCube(facing, cubeBuilder);
        cubeBuilder.setEmissiveMaterial(false);
    }

    public void addStraightDenseCoveredConnection(EnumFacing facing, AEColor cableColor, List<BakedQuad> quadsOut) {
        CubeBuilder cubeBuilder = createCubeBuilder(quadsOut);
        cubeBuilder.setTexture(this.connectionTextures.get(AECableType.DENSE_COVERED).get(cableColor));
        setStraightCableUVs(cubeBuilder, facing, 3, 13);
        addStraightDenseCableSizedCube(facing, cubeBuilder);
    }

    public void addStraightDenseSmartConnection(EnumFacing facing, AEColor cableColor, int channels,
                                                List<BakedQuad> quadsOut) {
        CubeBuilder cubeBuilder = createCubeBuilder(quadsOut);
        switch (facing) {
            case NORTH -> cubeBuilder.setFlipU(EnumFacing.NORTH, true);
            case WEST -> {
                cubeBuilder.setFlipV(EnumFacing.DOWN, true);
                cubeBuilder.setFlipU(EnumFacing.EAST, true);
            }
            case DOWN -> {
                cubeBuilder.setFlipU(EnumFacing.NORTH, true);
                cubeBuilder.setFlipV(EnumFacing.DOWN, true);
            }
            default -> {
            }
        }

        cubeBuilder.setTexture(this.connectionTextures.get(AECableType.DENSE_SMART).get(cableColor));
        setStraightCableUVs(cubeBuilder, facing, 3, 13);
        addStraightDenseCableSizedCube(facing, cubeBuilder);

        int displayedChannels = (channels + 3) / 4;
        TextureAtlasSprite oddChannel = this.smartCableTextures.getOddTextureForDenseChannels(displayedChannels);
        TextureAtlasSprite evenChannel = this.smartCableTextures.getEvenTextureForDenseChannels(displayedChannels);

        cubeBuilder.setEmissiveMaterial(true);
        cubeBuilder.setTexture(oddChannel);
        cubeBuilder.setColorRGB(cableColor.blackVariant);
        addStraightDenseCableSizedCube(facing, cubeBuilder);
        cubeBuilder.setTexture(evenChannel);
        cubeBuilder.setColorRGB(cableColor.whiteVariant);
        addStraightDenseCableSizedCube(facing, cubeBuilder);
        cubeBuilder.setEmissiveMaterial(false);
    }

    public TextureAtlasSprite getCoreTexture(CableCoreType coreType, AEColor color) {
        return this.coreTextures.get(coreType).get(color);
    }

    private void addBigCoveredCableSizedCube(EnumFacing facing, CubeBuilder cubeBuilder) {
        switch (facing) {
            case DOWN -> cubeBuilder.addCube(5, 0, 5, 11, 4, 11);
            case EAST -> cubeBuilder.addCube(12, 5, 5, 16, 11, 11);
            case NORTH -> cubeBuilder.addCube(5, 5, 0, 11, 11, 4);
            case SOUTH -> cubeBuilder.addCube(5, 5, 12, 11, 11, 16);
            case UP -> cubeBuilder.addCube(5, 12, 5, 11, 16, 11);
            case WEST -> cubeBuilder.addCube(0, 5, 5, 4, 11, 11);
            default -> {
            }
        }
    }
}
