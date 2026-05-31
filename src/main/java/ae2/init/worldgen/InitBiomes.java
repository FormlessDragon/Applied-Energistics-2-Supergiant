package ae2.init.worldgen;

import ae2.spatial.SpatialStorageDimensionIds;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.event.RegistryEvent;

public final class InitBiomes {

    private static Biome spatialStorageBiome;

    private InitBiomes() {
    }

    public static void init() {
        if (spatialStorageBiome == null) {
            spatialStorageBiome = new SpatialStorageBiome();
            spatialStorageBiome.setRegistryName(SpatialStorageDimensionIds.WORLD_ID);
        }
    }

    public static void register(RegistryEvent.Register<Biome> event) {
        init();
        event.getRegistry().register(spatialStorageBiome);
    }

    public static Biome getSpatialBiome() {
        init();
        return spatialStorageBiome;
    }

    private static final class SpatialStorageBiome extends Biome {

        private SpatialStorageBiome() {
            super(new BiomeProperties("Spatial Storage")
                .setRainDisabled()
                .setTemperature(0.5F)
                .setRainfall(0.5F)
                .setWaterColor(4159204));
            this.spawnableMonsterList.clear();
            this.spawnableCreatureList.clear();
            this.spawnableWaterCreatureList.clear();
            this.spawnableCaveCreatureList.clear();
        }

        @Override
        public int getSkyColorByTemp(float currentTemperature) {
            return 0x111111;
        }

        @Override
        public float getSpawningChance() {
            return 0;
        }
    }
}
