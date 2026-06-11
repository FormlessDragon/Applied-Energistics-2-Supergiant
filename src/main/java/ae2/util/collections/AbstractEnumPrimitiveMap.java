package ae2.util.collections;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;

abstract class AbstractEnumPrimitiveMap<E extends Enum<E>> {
    protected final Class<E> enumClass;
    protected final E[] universe;

    protected AbstractEnumPrimitiveMap(Class<E> enumClass) {
        this.enumClass = Objects.requireNonNull(enumClass, "enumClass");
        this.universe = Objects.requireNonNull(enumClass.getEnumConstants(), "enumConstants");
        if (this.universe.length > 256) {
            throw new IllegalArgumentException("Enum " + enumClass.getName() + " has too many constants: "
                + this.universe.length);
        }
    }

    protected final byte encode(E key) {
        Objects.requireNonNull(key, "key");
        verifyKeyType(key);
        return (byte) (key.ordinal() - 128);
    }

    protected final E decode(byte key) {
        return this.universe[key + 128];
    }

    protected final boolean isCompatibleKey(@Nullable Object key) {
        return key instanceof Enum<?> enumKey && enumKey.getDeclaringClass() == this.enumClass;
    }

    @SuppressWarnings("unchecked")
    protected final E requireKey(Object key) {
        if (!isCompatibleKey(key)) {
            throw new ClassCastException("Expected key of type " + this.enumClass.getSimpleName() + " but got "
                + (key == null ? "null" : key.getClass().getSimpleName()));
        }
        return (E) key;
    }

    private void verifyKeyType(E key) {
        if (key.getDeclaringClass() != this.enumClass) {
            throw new ClassCastException("Expected key of type " + this.enumClass.getSimpleName() + " but got "
                + key.getClass().getSimpleName());
        }
    }
}
