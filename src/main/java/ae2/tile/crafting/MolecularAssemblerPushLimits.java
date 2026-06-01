package ae2.tile.crafting;

final class MolecularAssemblerPushLimits {
    private MolecularAssemblerPushLimits() {
    }

    static int parallelLimit(int parallelCards) {
        return switch (parallelCards) {
            case 1 -> 4;
            case 2 -> 16;
            case 3 -> 64;
            default -> 1;
        };
    }

    static int maxPushMultiplier(int parallelLimit, int pendingCrafts, int maxMultiplier) {
        if (maxMultiplier <= 0) {
            return 0;
        }
        return Math.clamp(parallelLimit - pendingCrafts, 0, maxMultiplier);
    }

    static boolean isBusy(int parallelLimit, int pendingCrafts) {
        return pendingCrafts >= parallelLimit;
    }
}
