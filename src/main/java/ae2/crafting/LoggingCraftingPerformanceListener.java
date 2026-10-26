package ae2.crafting;

import ae2.api.stacks.AEKey;
import ae2.core.AELog;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.List;

final class LoggingCraftingPerformanceListener implements CraftingPerformanceListener {
    private final CraftingPerformanceText text = CraftingPerformanceText.localized();
    private final List<String> events = new ObjectArrayList<>();
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
        this.events.clear();
        this.events.add(text.start(output, amount));
    }

    @Override
    public void stage(String name, long nanos) {
        this.stages++;
        this.events.add(text.stage(name, nanos));
    }

    @Override
    public void count(String name, long amount) {
        this.events.add(text.count(name, amount));
    }

    @Override
    public void finish(long nanos, CraftingCalculation calculation) {
        this.events.add(text.summary(output, amount, nanos, calculation, stages));
        AELog.craftingPerformance("%s", String.join(System.lineSeparator(), this.events));
        this.events.clear();
    }
}
