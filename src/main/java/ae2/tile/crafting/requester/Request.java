package ae2.tile.crafting.requester;

import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import net.minecraft.nbt.NBTTagCompound;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class Request {
    private static final String ENABLED = "enabled";
    private static final String FORCE_START = "force";
    private static final String STACK = "stack";
    private static final String AMOUNT = "amount";
    private static final String BATCH = "batch";
    private static final String STATUS = "status";

    @Nullable
    private final RequestHost host;
    private final int index;
    private long requesterId;
    private int clientIndex;
    private boolean hasRequesterLocation;

    private boolean enabled = true;
    private boolean forceStart = false;
    @Nullable
    private GenericStack configuredStack;
    private long amount;
    private long batchSize = 1;
    private RequestStatus clientStatus = RequestStatus.IDLE;
    private long syncRevision;

    public Request() {
        this(null, -1);
    }

    Request(@Nullable RequestHost host, int index) {
        this.host = host;
        this.index = index;
        this.clientIndex = index;
    }

    private static RequestStatus readStatus(NBTTagCompound tag) {
        if (!tag.hasKey(STATUS, 8)) {
            return RequestStatus.IDLE;
        }

        try {
            return RequestStatus.valueOf(tag.getString(STATUS));
        } catch (IllegalArgumentException ignored) {
            return RequestStatus.IDLE;
        }
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled != enabled) {
            this.enabled = enabled;
            this.syncRevision++;
        }
    }

    public boolean isForceStart() {
        return this.forceStart;
    }

    public void setForceStart(boolean forceStart) {
        if (this.forceStart != forceStart) {
            this.forceStart = forceStart;
            this.syncRevision++;
        }
    }

    public void updateState(boolean enabled, boolean forceStart) {
        if (this.enabled != enabled || this.forceStart != forceStart) {
            boolean wasEnabled = this.enabled;
            this.enabled = enabled;
            this.forceStart = forceStart;
            this.syncRevision++;
            if (enabled || wasEnabled == enabled) {
                updated();
            } else {
                changed();
            }
        }
    }

    public void disableWithStatus(RequestStatus status) {
        if (!this.enabled && this.clientStatus == status) {
            return;
        }

        this.enabled = false;
        this.clientStatus = status;
        this.syncRevision++;
        updated();
    }

    public @Nullable GenericStack getConfiguredStack() {
        return configuredStack;
    }

    public void updateConfiguredStack(@Nullable GenericStack stack) {
        if (stack == null) {
            resetConfiguredStack();
            return;
        }

        AEKey key = getKey();
        if (key != null && key.matches(stack)) {
            updateAmount(stack.amount());
            return;
        }

        long clampedAmount = Math.max(stack.amount(), 0);
        long initialBatchSize = Math.max(stack.what().getAmountPerUnit(), 1);
        this.configuredStack = new GenericStack(stack.what(), clampedAmount);
        this.amount = clampedAmount;
        this.batchSize = initialBatchSize;
        this.syncRevision++;
        changed();
    }

    public @Nullable AEKey getKey() {
        return configuredStack == null ? null : configuredStack.what();
    }

    public long getAmount() {
        return amount;
    }

    public void setAmount(long amount) {
        long clamped = Math.max(amount, 0);
        if (this.amount != clamped) {
            this.amount = clamped;
            this.syncRevision++;
        }
    }

    public void updateAmount(long amount) {
        if (getKey() == null || amount <= 0) {
            resetConfiguredStack();
            return;
        }

        if (this.amount != amount) {
            this.amount = amount;
            this.configuredStack = new GenericStack(Objects.requireNonNull(this.configuredStack).what(), amount);
            this.syncRevision++;
            updated();
        }
    }

    public long getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(long batchSize) {
        long clamped = Math.max(batchSize, 1);
        if (this.batchSize != clamped) {
            this.batchSize = clamped;
            this.syncRevision++;
        }
    }

    public void updateBatchSize(long batchSize) {
        long newBatchSize = Math.max(batchSize, 1);
        if (this.batchSize != newBatchSize) {
            this.batchSize = newBatchSize;
            this.syncRevision++;
            updated();
        }
    }

    public RequestStatus getClientStatus() {
        return clientStatus;
    }

    public void setClientStatus(RequestStatus clientStatus) {
        RequestStatus status = clientStatus == null ? RequestStatus.IDLE : clientStatus;
        if (this.clientStatus != status) {
            this.clientStatus = status;
            this.syncRevision++;
        }
    }

    /**
     * Owner-thread change token for the serialized request fields; never persisted or sent over the wire.
     */
    public long getSyncRevision() {
        return this.syncRevision;
    }

    public boolean isEmpty() {
        return getKey() == null || amount <= 0 || batchSize <= 0;
    }

    public int getIndex() {
        return this.clientIndex;
    }

    public long getRequesterId() {
        return this.requesterId;
    }

    public boolean hasRequesterLocation() {
        return this.hasRequesterLocation;
    }

    public void setRequesterLocation(long requesterId, int index) {
        this.requesterId = requesterId;
        this.clientIndex = index;
        this.hasRequesterLocation = true;
    }

    private void resetConfiguredStack() {
        if (this.configuredStack == null && this.amount == 0 && this.batchSize == 1) {
            return;
        }

        this.configuredStack = null;
        this.amount = 0;
        this.batchSize = 1;
        this.syncRevision++;
        changed();
    }

    private void changed() {
        if (this.host != null) {
            this.host.onRequestChanged(this.index);
        }
    }

    private void updated() {
        if (this.host != null) {
            this.host.onRequestUpdated(this.index);
        }
    }

    public NBTTagCompound writeToNBT() {
        // Freeze all fields before key serialization, which may call addon code.
        boolean enabled = this.enabled;
        boolean forceStart = this.forceStart;
        GenericStack stack = this.configuredStack;
        long amount = this.amount;
        long batchSize = this.batchSize;
        RequestStatus status = this.clientStatus;
        var tag = new NBTTagCompound();
        tag.setBoolean(ENABLED, enabled);
        tag.setBoolean(FORCE_START, forceStart);
        tag.setTag(STACK, GenericStack.writeTag(stack));
        tag.setLong(AMOUNT, amount);
        tag.setLong(BATCH, batchSize);
        tag.setString(STATUS, status.name());
        return tag;
    }

    public void readFromNBT(NBTTagCompound tag) {
        try {
            enabled = tag.getBoolean(ENABLED);
            forceStart = tag.getBoolean(FORCE_START);
            configuredStack = GenericStack.readTag(tag.getCompoundTag(STACK));
            setAmount(tag.getLong(AMOUNT));
            setBatchSize(tag.getLong(BATCH));
            if (configuredStack != null) {
                if (amount <= 0) {
                    configuredStack = null;
                    batchSize = 1;
                } else {
                    configuredStack = new GenericStack(configuredStack.what(), amount);
                }
            }
            clientStatus = readStatus(tag);
        } finally {
            this.syncRevision++;
        }
    }

    void reset() {
        this.syncRevision++;
        enabled = true;
        forceStart = false;
        configuredStack = null;
        amount = 0;
        batchSize = 1;
        clientStatus = RequestStatus.IDLE;
        requesterId = 0;
        clientIndex = index;
        hasRequesterLocation = false;
    }
}
