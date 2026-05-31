package ae2.crafting;

import ae2.api.stacks.AEKey;
import ae2.core.AELog;

final class LoggingCraftingPerformanceListener implements CraftingPerformanceListener {
    private final CraftingPerformanceText text = CraftingPerformanceText.localized();
    private AEKey output;
    private long amount;
    private long stages;

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void start(AEKey output, long amount) {
        this.output = output;
        this.amount = amount;
        this.stages = 0;
        AELog.craftingPerformance("%s", text.start(output, amount));
    }

    @Override
    public void stage(String name, long nanos) {
        this.stages++;
        AELog.craftingPerformance("%s", text.stage(name, nanos));
    }

    @Override
    public void count(String name, long amount) {
        AELog.craftingPerformance("%s", text.count(name, amount));
    }

    @Override
    public void finish(long nanos, CraftingCalculation calculation) {
        AELog.craftingPerformance("%s", text.summary(output, amount, nanos, calculation, stages));
    }
}
