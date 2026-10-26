package ae2.crafting;

import ae2.api.crafting.IPatternDetails;
import ae2.api.networking.IGrid;
import ae2.api.networking.IGridNode;
import ae2.api.networking.crafting.CalculationStrategy;
import ae2.api.networking.crafting.ICraftingPlan;
import ae2.api.networking.crafting.ICraftingProvider;
import ae2.api.networking.crafting.ICraftingService;
import ae2.api.networking.crafting.ICraftingSimulationRequester;
import ae2.api.networking.energy.IEnergyService;
import ae2.api.networking.security.IActionSource;
import ae2.api.networking.storage.IStorageService;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.AEKeyType;
import ae2.api.stacks.AEKeyTypes;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import ae2.core.AEConfig;
import ae2.crafting.graph.CraftingGraph;
import ae2.hooks.ticking.TickHandler;
import ae2.me.service.CraftingService;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.IFMLSidedHandler;
import net.minecraftforge.fml.relauncher.Side;
import org.jetbrains.annotations.Nullable;
import sun.misc.Unsafe;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * A network of test patterns behind a real {@link CraftingService}, calculated the way the server does it: the job runs
 * on the crafting pool while the server tick steps it, see {@link TickHandler}.
 */
final class CraftingTestNetwork {
    private static final KeyType TYPE = new KeyType();
    private static final Map<String, Object> GROUPS = new HashMap<>();
    private static final Map<String, TestKey> KEYS = new HashMap<>();
    private static boolean bootstrapped;
    private static World world;
    private static Method simulateCraftingJobs;

    final KeyCounter stock = new KeyCounter();
    final List<TestPattern> patterns = new ArrayList<>();
    final Map<AEKey, AEKey> containers = new HashMap<>();
    private final Map<IPatternDetails, Provider> providers = new IdentityHashMap<>();
    private final CraftingService service;
    private final IGrid grid;
    private int nextDefinition;

    CraftingTestNetwork() {
        bootstrap();
        var storage = proxy(IStorageService.class, (method, args) -> switch (method.getName()) {
            case "getCachedInventory", "getInventory" -> method.getReturnType().isInstance(stock) ? stock : null;
            default -> null;
        });
        var energy = proxy(IEnergyService.class, (method, args) -> null);
        var holder = new Object[1];
        this.grid = proxy(IGrid.class, (method, args) -> {
            if (method.getName().equals("getNodes")) {
                return List.of();
            }
            if (!method.getName().equals("getService")) {
                return null;
            }
            var type = (Class<?>) args[0];
            if (type == IStorageService.class) return storage;
            if (type == ICraftingService.class) return holder[0];
            if (type == IEnergyService.class) return energy;
            return null;
        });
        this.service = new CraftingService(grid, storage, energy);
        holder[0] = this.service;
    }

    static World world() {
        return world;
    }

    /**
     * Replays the plan like a crafting CPU: starting from the used, emitted and missing items, the CPU visits its tasks
     * in turn, in the order of ExecutingCraftingJob#tasks, and pushes one craft per visit whose inputs are there, until
     * every planned craft ran. An input takes its possible stacks in order until it has enough, see
     * CraftingCpuHelper#extractPatternInputs. A plan without cycles, input choices or containers runs in any order, so
     * its crafts are run many at a time. Returns null if the plan runs and covers the request, else why not.
     */
    static @Nullable String replay(ICraftingPlan plan) {
        Map<AEKey, Long> inventory = new LinkedHashMap<>();
        for (var counter : List.of(plan.usedItems(), plan.emittedItems(), plan.missingItems())) {
            for (var entry : counter) {
                inventory.merge(entry.getKey(), entry.getLongValue(), Long::sum);
            }
        }
        var order = CraftingDispatchOrder.forPlan(plan);
        if (!order.isEmpty()) {
            var counts = new Object2LongOpenHashMap<IPatternDetails>();
            var noReservations = new CraftingSeedReservations(List.of(), ignored -> 0);
            long visits = 0;
            for (var batch : order) {
                if (!batch.ordered()) return "test replay expects an ordered certificate";
                for (long repeat = 0; repeat < batch.repetitions(); repeat++) {
                    for (var step : batch.steps()) {
                        if (++visits > 50_000_000) return "ordered replay gave up";
                        long done = runCrafts(step.pattern(), inventory, step.crafts(), noReservations);
                        if (done != step.crafts()) return "stuck in certificate: " + step.pattern();
                        counts.addTo(step.pattern(), done);
                    }
                }
            }
            if (!counts.equals(plan.patternTimes())) return "certificate does not match tasks";
            return inventory.getOrDefault(plan.finalOutput().what(), 0L) >= plan.finalOutput().amount()
                ? null : "certificate did not produce the requested output";
        }
        // The tasks of the executing job are an Object2ObjectOpenHashMap filled in the order of the plan
        var left = new Object2ObjectOpenHashMap<IPatternDetails, long[]>();
        plan.patternTimes().forEach((pattern, times) -> left.put(pattern, new long[]{times}));
        var seeds = new CraftingSeedReservations(left.keySet(), pattern -> left.get(pattern)[0]);
        boolean anyOrder = runsInAnyOrder(plan);
        boolean progress = true;
        long visits = 0;
        while (progress) {
            progress = false;
            for (var entry : left.entrySet()) {
                var times = entry.getValue();
                if (times[0] <= 0) continue;
                long done = runCrafts(entry.getKey(), inventory, anyOrder ? times[0] : 1, seeds);
                if (done > 0) {
                    times[0] -= done;
                    progress = true;
                }
                if (++visits > 50_000_000) {
                    return "replay gave up";
                }
            }
        }
        for (var entry : left.entrySet()) {
            if (entry.getValue()[0] > 0) {
                return "stuck: " + entry.getKey() + " x" + entry.getValue()[0] + " left";
            }
        }
        var output = plan.finalOutput();
        if (inventory.getOrDefault(output.what(), 0L) < output.amount()) {
            return "short of the requested output: " + inventory.getOrDefault(output.what(), 0L);
        }
        return null;
    }

    /*
     * Runs crafts one after another while their inputs are there. Crafts that would take the same stacks are run k at a
     * time when the inventory holds k times what one takes, which changes nothing about the order.
     */
    private static long runCrafts(IPatternDetails pattern, Map<AEKey, Long> inventory, long wanted,
                                  CraftingSeedReservations seeds) {
        long done = 0;
        while (done < wanted) {
            var taken = new HashMap<AEKey, Long>();
            boolean[] exact = {true};
            if (!take(pattern, inventory, taken, exact, seeds)) {
                break;
            }
            long times = 1;
            if (exact[0]) {
                times = wanted - done;
                for (var entry : taken.entrySet()) {
                    times = Math.min(times, Math.max(0, inventory.getOrDefault(entry.getKey(), 0L)
                        - seeds.reserved(pattern, entry.getKey())) / entry.getValue());
                }
                times = Math.max(1, times);
            }
            for (var entry : taken.entrySet()) {
                inventory.merge(entry.getKey(), -entry.getValue() * times, Long::sum);
            }
            for (var input : pattern.getInputs()) {
                for (var possible : input.possibleInputs()) {
                    var remaining = input.getRemainingKey(possible.what());
                    long amount = taken.getOrDefault(possible.what(), 0L);
                    if (remaining != null && amount > 0) {
                        // One container per stack taken
                        inventory.merge(remaining, amount / possible.amount() * times, Long::sum);
                    }
                }
            }
            for (var output : pattern.getOutputs()) {
                inventory.merge(output.what(), output.amount() * times, Long::sum);
            }
            done += times;
        }
        return done;
    }

    static synchronized void bootstrap() {
        if (bootstrapped) {
            return;
        }
        try {
            var field = FMLCommonHandler.class.getDeclaredField("sidedDelegate");
            field.setAccessible(true);
            if (field.get(FMLCommonHandler.instance()) == null) {
                field.set(FMLCommonHandler.instance(), Proxy.newProxyInstance(
                    IFMLSidedHandler.class.getClassLoader(), new Class<?>[]{IFMLSidedHandler.class},
                    (p, method, args) -> method.getName().equals("getSide") ? Side.SERVER : null));
            }
            Bootstrap.register();
            AEConfig.init();
            AEKeyTypes.register(TYPE);
            var unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            world = (World) ((Unsafe) unsafeField.get(null)).allocateInstance(WorldServer.class);
            simulateCraftingJobs = TickHandler.class.getDeclaredMethod("simulateCraftingJobs", World.class);
            simulateCraftingJobs.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        bootstrapped = true;
    }

    /**
     * A key of the given name. Names {@code group@n} share the primary key of {@code group}, like the damage variants of
     * a tool.
     */
    static synchronized TestKey key(String name) {
        return KEYS.computeIfAbsent(name, n -> {
            var group = n.contains("@") ? n.substring(0, n.indexOf('@')) : n;
            return new TestKey(n, GROUPS.computeIfAbsent(group, g -> new Object()));
        });
    }

    void store(String name, long amount) {
        stock.add(key(name), amount);
    }

    static long crafts(ICraftingPlan plan, IPatternDetails pattern) {
        return plan.patternTimes().getLong(pattern);
    }

    static Set<String> names(KeyCounter counter) {
        var names = new TreeSet<String>();
        for (var entry : counter) {
            names.add(entry.getKey() + " x" + entry.getLongValue());
        }
        return names;
    }

    /** One craft of the pattern leaves {@code remaining} in place of each {@code input} it takes. */
    void container(String input, String remaining) {
        containers.put(key(input), key(remaining));
    }

    /**
     * Adds a pattern of the highest priority so far. Inputs and outputs alternate names and amounts; an input name
     * {@code a|b} accepts either key.
     */
    TestPattern pattern(String name, Object[] inputs, Object[] outputs) {
        return pattern(name, -this.patterns.size(), inputs, outputs);
    }

    void store(AEKey what, long amount) {
        stock.add(what, amount);
    }

    void setStock(String name, long amount) {
        stock.set(key(name), amount);
    }

    TestPattern pattern(String name, int priority, Object[] inputs, Object[] outputs) {
        var pattern = new TestPattern(name, AEItemKey.of(new ItemStack(Items.PAPER, 1, nextDefinition++)), inputs,
            outputs, containers);
        this.patterns.add(pattern);
        return addPattern(pattern, priority);
    }

    TestPattern pattern(String name, IPatternDetails.IInput[] inputs, Object[] outputs) {
        var pattern = new TestPattern(name, AEItemKey.of(new ItemStack(Items.PAPER, 1, nextDefinition++)),
            inputs, outputs);
        this.patterns.add(pattern);
        return addPattern(pattern, -this.patterns.size());
    }

    IPatternDetails.IInput input(long multiplier, String... variants) {
        var stacks = new GenericStack[variants.length];
        for (int i = 0; i < variants.length; i++) stacks[i] = new GenericStack(key(variants[i]), 1);
        return new Input(stacks, containers, multiplier);
    }

    IPatternDetails.IInput input(long multiplier, GenericStack... variants) {
        return new Input(variants.clone(), containers, multiplier);
    }

    <T extends IPatternDetails> T addPattern(T pattern, int priority) {
        var provider = new Provider(pattern, priority);
        if (providers.putIfAbsent(pattern, provider) != null) throw new IllegalArgumentException("Already registered");
        this.service.addGlobalCraftingProvider(provider);
        return pattern;
    }

    static Object[] of(Object... values) {
        return values;
    }

    ICraftingPlan plan(String what, long amount) {
        return plan(what, amount, CalculationStrategy.REPORT_MISSING_ITEMS);
    }

    void removePattern(IPatternDetails pattern) {
        var provider = providers.remove(pattern);
        if (provider == null) throw new IllegalArgumentException("Not registered");
        this.service.removeGlobalCraftingProvider(provider);
        patterns.remove(pattern);
    }

    void priority(IPatternDetails pattern, int priority) {
        var provider = providers.get(pattern);
        if (provider == null) throw new IllegalArgumentException("Not registered");
        provider.priority = priority;
        this.service.refreshGlobalCraftingProvider(provider);
    }

    @Nullable CraftingGraph cachedGraph(String what) {
        return this.service.peekCachedGraph(key(what));
    }

    ICraftingPlan plan(String what, long amount, CalculationStrategy strategy) {
        return plan(key(what), amount, strategy);
    }

    ICraftingPlan plan(AEKey what, long amount) {
        return plan(what, amount, CalculationStrategy.REPORT_MISSING_ITEMS);
    }

    ICraftingPlan plan(AEKey what, long amount, CalculationStrategy strategy) {
        var requester = requester();
        var future = this.service.beginCraftingCalculation(world, requester, what, amount, strategy);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(120);
        try {
            while (!future.isDone()) {
                if (System.nanoTime() > deadline) {
                    future.cancel(true);
                    throw new AssertionError("Crafting calculation of " + amount + "x " + what + " did not finish");
                }
                simulateCraftingJobs.invoke(TickHandler.instance(), world);
                Thread.onSpinWait();
            }
            return future.get();
        } catch (ExecutionException e) {
            // Thrown as it reaches the crafting confirm screen, see ContainerCraftConfirm#getCraftingErrorText
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException(e.getCause());
        } catch (ReflectiveOperationException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    static long total(KeyCounter counter) {
        long total = 0;
        for (var entry : counter) {
            total += entry.getLongValue();
        }
        return total;
    }

    CraftingCalculation calculation(AEKey what, long amount) {
        this.service.canEmitFor(what); // Initialize the provider index on the driving thread, as beginCalculation does.
        return new CraftingCalculation(world, this.grid, requester(), new GenericStack(what, amount),
            CalculationStrategy.REPORT_MISSING_ITEMS, null);
    }

    static long used(ICraftingPlan plan, String what) {
        return plan.usedItems().get(key(what));
    }

    static long missing(ICraftingPlan plan, String what) {
        return plan.missingItems().get(key(what));
    }

    /*
     * What one craft takes, without taking it. Like CraftingCpuHelper#extractPatternInputs, an input is taken in whole
     * stacks of its possible inputs, in order. Exact when every input was covered by its first possible stack alone, so
     * further crafts take the same until that stack runs out.
     */
    private static boolean take(IPatternDetails pattern, Map<AEKey, Long> inventory, Map<AEKey, Long> taken,
                                boolean[] exact, CraftingSeedReservations seeds) {
        for (var input : pattern.getInputs()) {
            long need = input.getMultiplier();
            var first = input.possibleInputs()[0].what();
            for (var possible : input.possibleInputs()) {
                long available = inventory.getOrDefault(possible.what(), 0L) - taken.getOrDefault(possible.what(), 0L)
                    - seeds.reserved(pattern, possible.what());
                long stacks = Math.min(need, Math.max(0, available) / possible.amount());
                if (stacks <= 0) continue;
                if (possible.what() != first || stacks < need) exact[0] = false;
                taken.merge(possible.what(), stacks * possible.amount(), Long::sum);
                need -= stacks;
                if (need == 0) break;
            }
            if (need > 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean runsInAnyOrder(ICraftingPlan plan) {
        var makers = new HashMap<AEKey, List<IPatternDetails>>();
        for (var pattern : plan.patternTimes().keySet()) {
            for (var input : pattern.getInputs()) {
                if (input.possibleInputs().length != 1
                    || input.getRemainingKey(input.possibleInputs()[0].what()) != null) {
                    return false;
                }
            }
            for (var output : pattern.getOutputs()) {
                makers.computeIfAbsent(output.what(), k -> new ArrayList<>()).add(pattern);
            }
        }
        var state = new HashMap<IPatternDetails, Integer>();
        for (var pattern : plan.patternTimes().keySet()) {
            if (cycle(pattern, makers, state)) return false;
        }
        return true;
    }

    private static boolean cycle(IPatternDetails pattern, Map<AEKey, List<IPatternDetails>> makers,
                                 Map<IPatternDetails, Integer> state) {
        int seen = state.getOrDefault(pattern, 0);
        if (seen == 2) return false;
        if (seen == 1) return true;
        state.put(pattern, 1);
        for (var input : pattern.getInputs()) {
            for (var maker : makers.getOrDefault(input.possibleInputs()[0].what(), List.of())) {
                if (cycle(maker, makers, state)) return true;
            }
        }
        state.put(pattern, 2);
        return false;
    }

    IGrid grid() {
        return this.grid;
    }

    void recursiveReserve(long amount) {
        this.service.setRecursiveIngredientReserveAmount(amount);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Handler handler) {
        InvocationHandler invocation = (p, method, args) -> {
            if (method.isDefault()) {
                return InvocationHandler.invokeDefault(p, method, args);
            }
            switch (method.getName()) {
                case "equals":
                    return p == args[0];
                case "hashCode":
                    return System.identityHashCode(p);
                case "toString":
                    return type.getSimpleName() + " proxy";
                default:
                    break;
            }
            var result = handler.handle(method, args);
            return result != null ? result : defaultValue(method.getReturnType());
        };
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, invocation);
    }

    private static @Nullable Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0.0;
        if (type == float.class) return 0.0f;
        if (type == short.class) return (short) 0;
        if (type == byte.class) return (byte) 0;
        if (type == char.class) return (char) 0;
        return null;
    }

    private interface Handler {
        @Nullable
        Object handle(Method method, Object[] args) throws Throwable;
    }

    private ICraftingSimulationRequester requester() {
        var node = proxy(IGridNode.class, (method, args) -> method.getName().equals("grid") ? this.grid : null);
        return new ICraftingSimulationRequester() {
            @Override
            public @Nullable IActionSource getActionSource() {
                return null;
            }

            @Override
            public IGridNode getGridNode() {
                return node;
            }
        };
    }

    String describe() {
        var text = new StringBuilder();
        for (var pattern : this.patterns) {
            text.append(pattern).append(':');
            for (var input : pattern.getInputs()) {
                text.append(' ');
                for (var possible : input.possibleInputs()) {
                    text.append(possible.what()).append(" x").append(possible.amount()).append('|');
                }
                text.setLength(text.length() - 1);
                text.append(" (units ").append(input.getMultiplier()).append(')');
            }
            text.append(" ->");
            for (var output : pattern.getOutputs()) {
                text.append(' ').append(output.what()).append(" x").append(output.amount());
            }
            text.append(System.lineSeparator());
        }
        text.append("stock ").append(names(this.stock));
        return text.toString();
    }

    private static final class KeyType extends AEKeyType {
        KeyType() {
            super(new ResourceLocation("ae2test", "key"), TestKey.class, new TextComponentString("test"));
        }

        @Override
        public @Nullable AEKey readFromPacket(PacketBuffer input) {
            return null;
        }

        @Override
        public @Nullable AEKey loadKeyFromTag(NBTTagCompound tag) {
            return KEYS.get(tag.getString("name"));
        }
    }

    static final class TestKey extends AEKey {
        private final String name;
        private final Object group;

        private TestKey(String name, Object group) {
            this.name = name;
            this.group = group;
        }

        @Override
        public AEKeyType getType() {
            return TYPE;
        }

        @Override
        public AEKey dropSecondary() {
            return this;
        }

        @Override
        public NBTTagCompound toTag() {
            var tag = new NBTTagCompound();
            tag.setString("name", this.name);
            return tag;
        }

        @Override
        public Object getPrimaryKey() {
            return this.group;
        }

        @Override
        public ResourceLocation getId() {
            return new ResourceLocation("ae2test", Integer.toHexString(this.name.hashCode()));
        }

        @Override
        public void writeToPacket(PacketBuffer data) {
        }

        @Override
        public Object getReadOnlyStack() {
            return this.name;
        }

        @Override
        protected ITextComponent computeDisplayName() {
            return new TextComponentString(this.name);
        }

        @Override
        public @Nullable NBTBase get(String componentId) {
            return null;
        }

        @Override
        public boolean equals(Object o) {
            return super.equals(o);
        }

        @Override
        public int hashCode() {
            return this.name.hashCode();
        }

        @Override
        public String toString() {
            return this.name;
        }
    }

    static final class TestPattern implements IPatternDetails {
        private final String name;
        private final AEItemKey definition;
        private final IInput[] inputs;
        private final List<GenericStack> outputs = new ArrayList<>();

        TestPattern(String name, AEItemKey definition, Object[] inputs, Object[] outputs,
                    Map<AEKey, AEKey> containers) {
            this.name = name;
            this.definition = definition;
            var list = new ArrayList<IInput>();
            for (int i = 0; i < inputs.length; i += 2) {
                var names = ((String) inputs[i]).split("\\|");
                long amount = ((Number) inputs[i + 1]).longValue();
                var variants = new GenericStack[names.length];
                for (int v = 0; v < names.length; v++) {
                    variants[v] = new GenericStack(key(names[v]), amount);
                }
                list.add(new Input(variants, containers, 1));
            }
            this.inputs = list.toArray(new IInput[0]);
            for (int i = 0; i < outputs.length; i += 2) {
                this.outputs.add(new GenericStack(key((String) outputs[i]), ((Number) outputs[i + 1]).longValue()));
            }
        }

        TestPattern(String name, AEItemKey definition, IInput[] inputs, Object[] outputs) {
            this.name = name;
            this.definition = definition;
            this.inputs = inputs.clone();
            for (int i = 0; i < outputs.length; i += 2) {
                this.outputs.add(new GenericStack(key((String) outputs[i]), ((Number) outputs[i + 1]).longValue()));
            }
        }

        @Override
        public AEItemKey getDefinition() {
            return this.definition;
        }

        @Override
        public IInput[] getInputs() {
            return this.inputs;
        }

        @Override
        public List<GenericStack> getOutputs() {
            return this.outputs;
        }

        @Override
        public String toString() {
            return this.name;
        }

        @Override
        public int hashCode() {
            // CPU task iteration must be reproducible, including in independently constructed fixtures.
            return this.name.hashCode();
        }
    }

    private record Input(GenericStack[] variants, Map<AEKey, AEKey> containers, long multiplier)
        implements IPatternDetails.IInput {
        @Override
        public GenericStack[] possibleInputs() {
            return this.variants;
        }

        @Override
        public long getMultiplier() {
            return multiplier;
        }

        @Override
        public boolean isValid(AEKey input, World level) {
            for (var variant : this.variants) {
                if (variant.what().equals(input)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public @Nullable AEKey getRemainingKey(AEKey template) {
            return this.containers.get(template);
        }
    }

    private static final class Provider implements ICraftingProvider {
        private final IPatternDetails pattern;
        private int priority;

        Provider(IPatternDetails pattern, int priority) {
            this.pattern = pattern;
            this.priority = priority;
        }
        @Override
        public List<? extends IPatternDetails> getAvailablePatterns() {
            return List.of(this.pattern);
        }

        @Override
        public int getPatternPriority() {
            return this.priority;
        }

        @Override
        public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder, int multiplier) {
            return false;
        }

        @Override
        public boolean isBusy() {
            return false;
        }
    }
}
