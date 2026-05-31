package ae2.api.behaviors;

import ae2.api.config.Actionable;
import ae2.api.stacks.AEKey;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Experimental
@ApiStatus.NonExtendable
public interface PickupSink {
    long insert(AEKey what, long amount, Actionable mode);
}
