package ae2.api.behaviors;

import ae2.api.networking.energy.IEnergySource;
import ae2.api.networking.security.IActionSource;
import ae2.api.networking.storage.IStorageService;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.AEKeyType;
import ae2.util.prioritylist.IPartitionList;
import org.jetbrains.annotations.ApiStatus;

/**
 * Context for import and export bus transfer operations.
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
public interface StackTransferContext {

    IStorageService getInternalStorage();

    IEnergySource getEnergySource();

    IActionSource getActionSource();

    int getOperationsRemaining();

    void setOperationsRemaining(int operationsRemaining);

    boolean hasOperationsLeft();

    boolean hasDoneWork();

    boolean isKeyTypeEnabled(AEKeyType space);

    boolean isInFilter(AEKey key);

    IPartitionList getFilter();

    boolean isInverted();

    void setInverted(boolean inverted);

    boolean canInsert(AEItemKey what, long amount);

    void reduceOperationsRemaining(long inserted);
}
