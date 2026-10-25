package ae2.crafting;

import ae2.api.crafting.IPatternDetails;
import ae2.api.networking.crafting.CalculationStrategy;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.AEFluidKey;
import ae2.api.stacks.GenericStack;
import ae2.container.implementations.ContainerCraftConfirm;
import ae2.core.localization.PlayerMessages;
import net.minecraft.init.Items;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fluids.FluidRegistry;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigInteger;
import java.util.concurrent.ExecutionException;

import static ae2.crafting.CraftingRemainderFixtures.*;
import static ae2.crafting.CraftingTestNetwork.*;
import static org.junit.jupiter.api.Assertions.*;

class CraftingAmountOverflowTest extends CraftingRecipeTestSupport {
    private static final long HALF_UP = Long.MAX_VALUE / 2 + 1;
    private static final BigInteger ABOVE_MAX = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);

    private CraftingAmountOverflow overflow(CraftingTestNetwork net, AEKey target, long count, AEKey material,
                                           BigInteger expected) {
        var before = amounts(net.stock);
        var failure = assertThrows(CraftingAmountOverflow.class, () -> net.plan(target, count));
        assertEquals(PlayerMessages.CraftingAmountOverflow, failure.getMessageKey());
        assertEquals(material, failure.getOverflow().getKey());
        assertEquals(expected, failure.getOverflow().getAmount());
        assertEquals(before, amounts(net.stock), "failed planning must not change real storage");
        return failure;
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 10_000})
    void inputMultiplicationFailsForNativeAndLocalPatternsAndRecoversAfterwards(int cache) {
        memo(cache);
        for (boolean local : new boolean[]{false, true}) {
            var f = new CraftingAdversarialFixtures.Fixture(0);
            f.recipe("root", of("raw", 2), local ? of("result", 1, "waste", 1) : of("result", 1));
            f.register(false);
            f.stock("raw", Long.MAX_VALUE);
            complete(f.net, f.net.plan(f.key("result"), 1), f.key("result"), 1);
            overflow(f.net, f.key("result"), HALF_UP, f.key("raw"), ABOVE_MAX);
            complete(f.net, f.net.plan(f.key("result"), 2), f.key("result"), 2);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 10_000})
    void sharedLeafAndCraftableIntermediateDemandsCannotOverflowWhenAdded(int cache) {
        memo(cache);
        for (boolean intermediate : new boolean[]{false, true}) {
            var f = new CraftingAdversarialFixtures.Fixture(0);
            f.recipe("left", of("shared", HALF_UP), of("left", 1));
            f.recipe("right", of("shared", HALF_UP), of("right", 1));
            f.recipe("root", of("left", 1, "right", 1), of("result", 1));
            if (intermediate) f.recipe("shared", of("raw", 1), of("shared", 1));
            f.register(false);
            overflow(f.net, f.key("result"), 1, f.key("shared"), ABOVE_MAX);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 10_000})
    void outputBatchRoundingCannotSilentlyClampProducedItems(int cache) {
        memo(cache);
        var net = new CraftingTestNetwork();
        net.pattern("root", of("raw", 1), of("result", 3));
        net.store("raw", Long.MAX_VALUE);
        overflow(net, key("result"), Long.MAX_VALUE, key("result"),
            BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.TWO));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 10_000})
    void differentProducersCannotOverflowTheSameByproduct(int cache) {
        memo(cache);
        var f = new CraftingAdversarialFixtures.Fixture(0);
        f.recipe("left", of("raw", 1), of("left", 1, "waste", HALF_UP));
        f.recipe("right", of("raw", 1), of("right", 1, "waste", HALF_UP));
        f.recipe("root", of("left", 1, "right", 1), of("result", 1));
        f.register(false);
        f.stock("raw", 2);
        overflow(f.net, f.key("result"), 1, f.key("waste"), ABOVE_MAX);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 10_000})
    void theLimitAppliesPerMaterialRatherThanToTheSumOfDifferentMaterials(int cache) {
        memo(cache);
        var f = new CraftingAdversarialFixtures.Fixture(0);
        f.recipe("root", of("a", Long.MAX_VALUE, "b", Long.MAX_VALUE), of("result", 1));
        f.register(false);
        var plan = f.net.plan(f.key("result"), 1);
        materials(plan.missingItems(), f.key("a"), Long.MAX_VALUE, f.key("b"), Long.MAX_VALUE);
        assertTrue(plan.simulation());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 10_000})
    void multipliedThirdPartyInputReportsItsActualMaterial(int cache) {
        memo(cache);
        var net = new CraftingTestNetwork();
        var water = AEFluidKey.of(FluidRegistry.WATER);
        net.pattern("root", new IPatternDetails.IInput[]{net.input(HALF_UP, new GenericStack(water, 2))},
            of("result", 1));
        overflow(net, key("result"), 1, water, ABOVE_MAX);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 10_000})
    void reusableAssemblerReturnsCannotHideAnOverflowingOutput(int cache) {
        memo(cache);
        var f = new CraftingRemainderFixtures.Fixture();
        var apple = item(Items.APPLE);
        var carrot = item(Items.CARROT);
        var bowl = item(Items.BOWL);
        f.rule("overflowing_assembler", carrot, 3, false, new Slot[]{exact(apple), exact(bowl)},
            grid -> at(grid, 1, bowl));
        f.stock(apple, Long.MAX_VALUE);
        f.stock(bowl, 1);
        overflow(f.net, carrot, Long.MAX_VALUE, carrot, BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.TWO));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 10_000})
    void exactlyLongMaxIsAllowedAndCraftLessDoesNotSuppressOverflow(int cache) {
        memo(cache);
        var net = new CraftingTestNetwork();
        net.pattern("root", of("raw", 1), of("result", 1));
        net.store("raw", Long.MAX_VALUE);
        var exact = net.plan("result", Long.MAX_VALUE);
        complete(net, exact, "result", Long.MAX_VALUE);
        materials(exact.usedItems(), "raw", Long.MAX_VALUE);

        var overflowing = new CraftingTestNetwork();
        overflowing.pattern("root", of("raw", 2), of("result", 1));
        overflowing.store("raw", 1);
        var failure = assertThrows(CraftingAmountOverflow.class,
            () -> overflowing.plan("result", HALF_UP, CalculationStrategy.CRAFT_LESS));
        assertEquals(key("raw"), failure.getOverflow().getKey());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 10_000})
    void aSeededAmplifierCannotExceedTheRepresentableProducedQuantity(int cache) {
        memo(cache);
        var net = new CraftingTestNetwork();
        net.pattern("grow", of("seed", 1), of("seed", 2));
        net.store("seed", 1);
        overflow(net, key("seed"), Long.MAX_VALUE, key("seed"), ABOVE_MAX);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 10_000})
    void alternativeFallbackCannotTurnOverflowIntoASuccessfulPlan(int cache) {
        memo(cache);
        var net = new CraftingTestNetwork();
        net.pattern("preferred", 10, of("raw", 2), of("result", 1));
        net.pattern("fallback", 0, of("other", 1), of("result", 1));
        net.store("other", Long.MAX_VALUE);
        overflow(net, key("result"), HALF_UP, key("raw"), ABOVE_MAX);
    }

    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void chatFormattingKeepsTheMaterialExactAmountAndLimit(boolean futureWrapped, boolean batchFailure) throws Exception {
        var net = new CraftingTestNetwork();
        net.pattern("root", of("raw", 2), of("result", 1));
        var failure = overflow(net, key("result"), HALF_UP, key("raw"), ABOVE_MAX);
        var formatter = ContainerCraftConfirm.class.getDeclaredMethod("getCraftingErrorText", Throwable.class);
        formatter.setAccessible(true);
        Throwable cause = batchFailure ? failure.getOverflow() : failure;
        var text = (TextComponentTranslation) formatter.invoke(null,
            futureWrapped ? new ExecutionException(cause) : cause);
        assertEquals(PlayerMessages.CraftingAmountOverflow.getTranslationKey(), text.getKey());
        assertEquals(key("raw").getDisplayName(), text.getFormatArgs()[0]);
        assertEquals(ABOVE_MAX.toString(), text.getFormatArgs()[1]);
        assertEquals(Long.toString(Long.MAX_VALUE), text.getFormatArgs()[2]);
    }
}
