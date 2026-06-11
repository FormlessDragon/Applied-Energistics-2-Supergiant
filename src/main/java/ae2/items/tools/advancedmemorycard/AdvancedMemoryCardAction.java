package ae2.items.tools.advancedmemorycard;

public record AdvancedMemoryCardAction(Mode mode, int sourceEntryId, int targetEntryId) {
    public enum Mode {
        BIND_OUTPUT,
        BIND_INPUT,
        COPY_OUTPUT,
        DELETE_BINDING
    }
}
