package ae2.container.implementations;

import ae2.core.localization.PlayerMessages;
import ae2.crafting.CraftingCalculationFailure;
import net.minecraft.util.text.TextComponentTranslation;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CraftingErrorMessageTest {
    private static TextComponentTranslation format(Throwable error) throws Exception {
        var method = ContainerCraftConfirm.class.getDeclaredMethod("getCraftingErrorText", Throwable.class);
        method.setAccessible(true);
        return (TextComponentTranslation) method.invoke(null, error);
    }

    @Test
    void unexpectedFailureIncludesRootExceptionMessageAndLocation() throws Exception {
        var failure = new IllegalStateException("Local crafting replan limit exceeded: 1000000");
        failure.setStackTrace(new StackTraceElement[]{new StackTraceElement("example.Planner", "plan", "Planner.java", 42)});
        var message = format(new ExecutionException(new RuntimeException(failure)));
        assertEquals(PlayerMessages.CraftingUnexpectedError.getTranslationKey(), message.getKey());
        assertEquals(IllegalStateException.class.getName(), message.getFormatArgs()[0]);
        assertTrue(message.getFormatArgs()[1].toString().contains(failure.getMessage()));
        assertTrue(message.getFormatArgs()[1].toString().contains("Planner.java:42"));
    }

    @Test
    void exceptionWithoutMessageStillIdentifiesItsTypeAndOrigin() throws Exception {
        var failure = new NullPointerException();
        failure.setStackTrace(new StackTraceElement[]{new StackTraceElement("example.Pattern", "inputs", "Pattern.java", 17)});
        var message = format(failure);
        assertEquals(PlayerMessages.CraftingUnexpectedError.getTranslationKey(), message.getKey());
        assertEquals(NullPointerException.class.getName(), message.getFormatArgs()[0]);
        assertTrue(message.getFormatArgs()[1].toString().contains("Pattern.java:17"));
    }

    @Test
    void nestedKnownFailurePreservesMaterialQuantityAndLimitDetails() throws Exception {
        Object[] details = {"stone", "100000000000", "1000000"};
        var failure = CraftingCalculationFailure.withDetails(PlayerMessages.CraftingReplayLimit, details);
        var message = format(new ExecutionException(new CompletionException(failure)));
        assertEquals(PlayerMessages.CraftingReplayLimit.getTranslationKey(), message.getKey());
        assertArrayEquals(details, message.getFormatArgs());
    }

    @Test
    void cancellationAndInterruptionAreReportedAsSuch() throws Exception {
        for (var failure : new Exception[]{new CancellationException(), new InterruptedException()}) {
            assertEquals(PlayerMessages.CraftingCalculationCancelled.getTranslationKey(),
                format(new ExecutionException(failure)).getKey());
        }
    }
}
