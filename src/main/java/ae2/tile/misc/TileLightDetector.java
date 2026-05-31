package ae2.tile.misc;

import ae2.tile.AEBaseTile;
import ae2.tile.ServerTickingTile;
import ae2.util.Platform;

public class TileLightDetector extends AEBaseTile implements ServerTickingTile {

    private int ticksSinceCheck = 30;
    private int lastLight;

    public boolean isExposedToLight() {
        return this.lastLight > 0;
    }

    @Override
    public void serverTick() {
        this.ticksSinceCheck++;
        if (this.ticksSinceCheck > 30) {
            this.ticksSinceCheck = 0;
            this.updateLight();
        }
    }

    public void updateLight() {
        if (this.world == null) {
            return;
        }

        int light = this.world.getLightFromNeighbors(this.pos);
        if (this.lastLight != light) {
            this.lastLight = light;
            this.markForUpdate();
            Platform.notifyBlocksOfNeighbors(this.world, this.pos);
        }
    }
}
