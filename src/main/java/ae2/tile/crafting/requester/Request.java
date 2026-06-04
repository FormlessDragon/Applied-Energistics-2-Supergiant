package ae2.tile.crafting.requester;

import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import net.minecraft.nbt.NBTTagCompound;
import org.jetbrains.annotations.Nullable;

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
        this.enabled = enabled;
    }

    public boolean isForceStart() {
        return this.forceStart;
    }

    public void setForceStart(boolean forceStart) {
        this.forceStart = forceStart;
    }

    public void updateState(boolean enabled, boolean forceStart) {
        if (this.enabled != enabled || this.forceStart != forceStart) {
            boolean wasEnabled = this.enabled;
            this.enabled = enabled;
            this.forceStart = forceStart;
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

        this.configuredStack = new GenericStack(stack.what(), stack.amount());
        this.amount = stack.amount();
        this.batchSize = stack.what().getAmountPerUnit();
        changed();
    }

    public @Nullable AEKey getKey() {
        return configuredStack == null ? null : configuredStack.what();
    }

    public long getAmount() {
        return amount;
    }

    public void setAmount(long amount) {
        this.amount = Math.max(0, amount);
    }

    public void updateAmount(long amount) {
        if (getKey() == null || amount <= 0) {
            resetConfiguredStack();
            return;
        }

        if (this.amount != amount) {
            this.amount = amount;
            updated();
        }
    }

    public long getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(long batchSize) {
        this.batchSize = Math.max(1, batchSize);
    }

    public void updateBatchSize(long batchSize) {
        long newBatchSize = Math.max(1, batchSize);
        if (this.batchSize != newBatchSize) {
            this.batchSize = newBatchSize;
            updated();
        }
    }

    public RequestStatus getClientStatus() {
        return clientStatus;
    }

    public void setClientStatus(RequestStatus clientStatus) {
        this.clientStatus = clientStatus == null ? RequestStatus.IDLE : clientStatus;
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
        var tag = new NBTTagCompound();
        tag.setBoolean(ENABLED, enabled);
        tag.setBoolean(FORCE_START, forceStart);
        tag.setTag(STACK, GenericStack.writeTag(configuredStack));
        tag.setLong(AMOUNT, amount);
        tag.setLong(BATCH, batchSize);
        tag.setString(STATUS, clientStatus.name());
        return tag;
    }

    public void readFromNBT(NBTTagCompound tag) {
        enabled = tag.getBoolean(ENABLED);
        forceStart = tag.getBoolean(FORCE_START);
        configuredStack = GenericStack.readTag(tag.getCompoundTag(STACK));
        setAmount(tag.getLong(AMOUNT));
        setBatchSize(tag.getLong(BATCH));
        clientStatus = readStatus(tag);
    }

    void reset() {
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
