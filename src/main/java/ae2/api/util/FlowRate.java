package ae2.api.util;

public record FlowRate(long in, long out) {

    public long net() {
        return this.in - this.out;
    }
}