package ae2.me.netdata;

public final class State<T extends Enum<T>> {
    private T value;

    public State(T value) {
        this.value = value;
    }

    public T get() {
        return value;
    }

    public void set(T value) {
        if (value.ordinal() > this.value.ordinal()) {
            this.value = value;
        }
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof State<?> state && state.value == value;
    }

    @Override
    public String toString() {
        return "state[" + value + "]";
    }
}
