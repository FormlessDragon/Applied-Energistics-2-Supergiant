package ae2.crafting.execution;

import ae2.api.config.Actionable;
import ae2.api.crafting.IPatternDetails;
import ae2.api.crafting.IPatternDetailsDecoder;
import ae2.api.crafting.PatternDetailsHelper;
import ae2.api.networking.crafting.CraftingJobOptions;
import ae2.api.networking.crafting.ICraftingProvider;
import ae2.api.networking.energy.IEnergyService;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import ae2.crafting.CraftingAttemptMetrics;
import ae2.crafting.CraftingLink;
import ae2.crafting.CraftingPlan;
import ae2.crafting.pattern.AECraftingPattern;
import ae2.me.cluster.implementations.CraftingCPUCluster;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;

/**
 * Drives real dispatch without a tile/world tick; the fixture alone supplies machine completion events.
 */
public final class CraftingCpuTestDriver {
    private final CraftingCpuLogic logic;
    private final IPatternDetails pattern;
    private final World world;
    private final IEnergyService energy = (IEnergyService) Proxy.newProxyInstance(IEnergyService.class.getClassLoader(),
        new Class<?>[]{IEnergyService.class}, (p, method, args) -> {
            if (method.getName().equals("extractAEPower")) return args[0];
            throw new UnsupportedOperationException(method.getName());
        });
    private ExecutingCraftingJob job;

    public CraftingCpuTestDriver(IPatternDetails pattern, long count, World world) {
        this.world = world;
        this.pattern = pattern;
        var cluster = new CraftingCPUCluster(BlockPos.ORIGIN, BlockPos.ORIGIN);
        logic = cluster.craftingLogic;
        var times = new Object2LongOpenHashMap<IPatternDetails>();
        times.put(pattern, count);
        var result = pattern.getPrimaryOutput();
        var plan = new CraftingPlan(new GenericStack(result.what(), count * result.amount()), 128, false, false,
            new KeyCounter(), new KeyCounter(), new KeyCounter(), 0, times, null, List.of(), null,
            new CraftingAttemptMetrics(), null);
        var link = new CraftingLink(CraftingCpuHelper.generateLinkData(UUID.randomUUID(), true, false), cluster);
        job = new ExecutingCraftingJob(plan, key -> {
        }, link, null, CraftingJobOptions.DEFAULT);
        attach();
    }

    private void attach() {
        try {
            var field = CraftingCpuLogic.class.getDeclaredField("job");
            field.setAccessible(true);
            field.set(logic, job);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    public void saveAndRestore() throws Exception {
        var inventory = logic.getInventory().writeToNBT();
        var saved = job.writeToNBT();
        // Fixtures encode real patterns on paper. Install only their exact decoder while restoring, then remove it.
        var decoder = new IPatternDetailsDecoder() {
            public boolean isEncodedPattern(ItemStack stack) {
                return pattern.getDefinition().equals(AEItemKey.of(stack));
            }

            public IPatternDetails decodePattern(AEItemKey key, World ignored) {
                return pattern.getDefinition().equals(key) ? new AECraftingPattern(key, world) : null;
            }
        };
        var field = PatternDetailsHelper.class.getDeclaredField("DECODERS");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var decoders = (List<IPatternDetailsDecoder>) field.get(null);
        PatternDetailsHelper.registerDecoder(decoder);
        try {
            job = new ExecutingCraftingJob(saved, key -> {
            }, logic);
            logic.getInventory().readFromNBT(inventory);
            attach();
        } finally {
            decoders.remove(decoder);
        }
    }

    public void stock(AEKey key, long amount) {
        logic.getInventory().insert(key, amount, Actionable.MODULATE);
    }

    public long stored(AEKey key) {
        return logic.getStored(key);
    }

    public long pending(AEKey key) {
        return job.waitingFor.extract(key, Long.MAX_VALUE, Actionable.SIMULATE);
    }

    public long deliver(AEKey key, long amount) {
        return logic.insert(key, amount, Actionable.MODULATE);
    }

    public long remaining() {
        return job.tasks.values().stream().mapToLong(p -> p.value).sum();
    }

    public int run(ICraftingProvider provider, int budget) {
        return logic.executeCraftingWithProviderLookup(budget, energy, world, pattern -> List.of(provider));
    }
}
