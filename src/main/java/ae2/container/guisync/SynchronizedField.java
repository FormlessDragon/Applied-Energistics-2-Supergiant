/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package ae2.container.guisync;

import ae2.api.stacks.GenericStack;
import ae2.text.TextComponents;
import com.google.common.base.Preconditions;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

/**
 * Synchronizes one field of a container to its client.
 *
 * <p>The field is accessed through one {@link VarHandle}. Primitive field
 * implementations use typed access and primitive client snapshots to avoid
 * boxing during change detection and serialization.</p>
 */
public abstract class SynchronizedField {
    private static final int MAX_STRING_BYTES = 4096;
    private static final int MAX_CUSTOM_FIELD_BYTES = 1024 * 1024;

    private static final Map<Class<?>, Factory> FACTORIES = new Reference2ObjectOpenHashMap<>();

    static {
        register(String.class, (source, varHandle, _) -> new StringField(source, varHandle));
        register(int.class, (source, varHandle, _) -> new IntegerField(source, varHandle));
        register(Integer.class, (source, varHandle, _) -> new BoxedIntegerField(source, varHandle));
        register(long.class, (source, varHandle, _) -> new LongField(source, varHandle));
        register(Long.class, (source, varHandle, _) -> new BoxedLongField(source, varHandle));
        register(double.class, (source, varHandle, _) -> new DoubleField(source, varHandle));
        register(Double.class, (source, varHandle, _) -> new BoxedDoubleField(source, varHandle));
        register(boolean.class, (source, varHandle, _) -> new BooleanField(source, varHandle));
        register(Boolean.class, (source, varHandle, _) -> new BoxedBooleanField(source, varHandle));
        register(ITextComponent.class, (source, varHandle, _) -> new TextComponentField(source, varHandle));
        register(GenericStack.class, (source, varHandle, _) -> new GenericStackField(source, varHandle));
        register(ResourceLocation.class, (source, varHandle, _) -> new ResourceLocationField(source, varHandle));
        register(PacketWritable.class, CustomField::new);
        register(Enum.class, EnumField::new);
    }

    protected final Object source;
    protected final VarHandle varHandle;
    private Object clientVersion;

    /**
     * Creates a synchronized field backed by {@code varHandle}.
     *
     * <p>This constructor is protected so integrations can provide their own
     * synchronized field implementation and register it with
     * {@link #register(Class, Factory)}.</p>
     */
    protected SynchronizedField(Object source, VarHandle varHandle) {
        this.source = Objects.requireNonNull(source, "source");
        this.varHandle = Objects.requireNonNull(varHandle, "varHandle");
    }

    /**
     * Registers a synchronized field factory for a field type.
     *
     * <p>Exact type registrations take precedence over assignable type
     * registrations. This lets an integration register a specific subtype
     * while the built-in {@link PacketWritable} and {@link Enum} registrations
     * continue to cover their respective type families.</p>
     *
     * @param fieldType the type declared by a synchronized field
     * @param factory   creates a field wrapper for the object and its VarHandle
     * @throws IllegalArgumentException if the type already has a registration
     */
    public static synchronized void register(Class<?> fieldType, Factory factory) {
        Objects.requireNonNull(fieldType, "fieldType");
        Objects.requireNonNull(factory, "factory");
        Factory previous = FACTORIES.putIfAbsent(fieldType, factory);
        if (previous != null) {
            throw new IllegalArgumentException("A synchronized field factory is already registered for " + fieldType);
        }
    }

    /**
     * Creates a wrapper for a reflected field using the registered factory for
     * its declared type.
     */
    public static SynchronizedField create(Object source, Field field) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(field, "field");

        VarHandle varHandle = createVarHandle(field);
        Factory factory = findFactory(field.getType());
        if (factory == null) {
            throw new IllegalArgumentException("Cannot synchronize field " + field);
        }

        return Objects.requireNonNull(factory.create(source, varHandle, field.getType()),
                "synchronized field factory returned null");
    }

    private static VarHandle createVarHandle(Field field) {
        if (Modifier.isFinal(field.getModifiers())) {
            throw new IllegalArgumentException("Cannot synchronize final field " + field);
        }
        field.setAccessible(true);
        try {
            var lookup = MethodHandles.privateLookupIn(field.getDeclaringClass(), MethodHandles.lookup());
            return lookup.unreflectVarHandle(field);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to access synchronized field " + field, e);
        }
    }

    private static synchronized @Nullable Factory findFactory(Class<?> fieldType) {
        Factory exact = FACTORIES.get(fieldType);
        if (exact != null) {
            return exact;
        }

        for (var entry : FACTORIES.entrySet()) {
            if (entry.getKey().isAssignableFrom(fieldType)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /** Returns the current field value through the field's VarHandle. */
    protected final Object get() {
        try {
            return this.varHandle.get(this.source);
        } catch (Throwable throwable) {
            throw new RuntimeException("Failed to read synchronized field", throwable);
        }
    }

    /** Sets the field value through the field's VarHandle. */
    protected final void set(Object value) {
        try {
            this.varHandle.set(this.source, value);
        } catch (Throwable throwable) {
            throw new RuntimeException("Failed to write synchronized field", throwable);
        }
    }

    public boolean hasChanges() {
        return !Objects.equals(get(), this.clientVersion);
    }

    public void write(ByteBuf data) {
        this.clientVersion = get();
        writeValue(data);
    }

    public void read(ByteBuf data) {
        readValue(data);
        this.clientVersion = get();
    }

    /** Writes the current value using this field's wire representation. */
    protected abstract void writeValue(ByteBuf data);

    /**
     * Reads a value and stores it in the source field.
     */
    protected abstract void readValue(ByteBuf data);

    private static void writeString(ByteBuf data, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_STRING_BYTES) {
            throw new IllegalArgumentException("Synchronized string is too long: " + bytes.length);
        }
        data.writeInt(bytes.length);
        data.writeBytes(bytes);
    }

    private static String readString(ByteBuf data) {
        int length = data.readInt();
        if (length < 0 || length > MAX_STRING_BYTES || length > data.readableBytes()) {
            throw new IllegalArgumentException("Invalid synchronized string length: " + length);
        }
        byte[] bytes = new byte[length];
        data.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static final class StringField extends SynchronizedField {
        private StringField(Object source, VarHandle varHandle) {
            super(source, varHandle);
        }

        @Override
        protected void writeValue(ByteBuf data) {
            String value = (String) get();
            writeString(data, value == null ? "" : value);
        }

        @Override
        protected void readValue(ByteBuf data) {
            set(readString(data));
        }
    }

    private abstract static class PrimitiveField extends SynchronizedField {
        protected boolean hasClientVersion;

        private PrimitiveField(Object source, VarHandle varHandle) {
            super(source, varHandle);
        }

        @Override
        public final void write(ByteBuf data) {
            writeValue(data);
            this.hasClientVersion = true;
        }

        @Override
        public final void read(ByteBuf data) {
            readValue(data);
            this.hasClientVersion = true;
        }
    }

    private static final class IntegerField extends PrimitiveField {
        private int clientVersion;

        private IntegerField(Object source, VarHandle varHandle) {
            super(source, varHandle);
        }

        private int getInt() {
            try {
                return (int) this.varHandle.get(this.source);
            } catch (Throwable throwable) {
                throw new RuntimeException("Failed to read synchronized field", throwable);
            }
        }

        private void setInt(int value) {
            try {
                this.varHandle.set(this.source, value);
            } catch (Throwable throwable) {
                throw new RuntimeException("Failed to write synchronized field", throwable);
            }
        }

        @Override
        public boolean hasChanges() {
            return !this.hasClientVersion || getInt() != this.clientVersion;
        }

        @Override
        protected void writeValue(ByteBuf data) {
            int value = getInt();
            data.writeInt(value);
            this.clientVersion = value;
        }

        @Override
        protected void readValue(ByteBuf data) {
            int value = data.readInt();
            setInt(value);
            this.clientVersion = value;
        }
    }

    private static final class BoxedIntegerField extends SynchronizedField {
        private BoxedIntegerField(Object source, VarHandle varHandle) {
            super(source, varHandle);
        }

        @Override
        protected void writeValue(ByteBuf data) {
            data.writeInt((Integer) get());
        }

        @Override
        protected void readValue(ByteBuf data) {
            set(data.readInt());
        }
    }

    private static final class LongField extends PrimitiveField {
        private long clientVersion;

        private LongField(Object source, VarHandle varHandle) {
            super(source, varHandle);
        }

        private long getLong() {
            try {
                return (long) this.varHandle.get(this.source);
            } catch (Throwable throwable) {
                throw new RuntimeException("Failed to read synchronized field", throwable);
            }
        }

        private void setLong(long value) {
            try {
                this.varHandle.set(this.source, value);
            } catch (Throwable throwable) {
                throw new RuntimeException("Failed to write synchronized field", throwable);
            }
        }

        @Override
        public boolean hasChanges() {
            return !this.hasClientVersion || getLong() != this.clientVersion;
        }

        @Override
        protected void writeValue(ByteBuf data) {
            long value = getLong();
            data.writeLong(value);
            this.clientVersion = value;
        }

        @Override
        protected void readValue(ByteBuf data) {
            long value = data.readLong();
            setLong(value);
            this.clientVersion = value;
        }
    }

    private static final class BoxedLongField extends SynchronizedField {
        private BoxedLongField(Object source, VarHandle varHandle) {
            super(source, varHandle);
        }

        @Override
        protected void writeValue(ByteBuf data) {
            data.writeLong((Long) get());
        }

        @Override
        protected void readValue(ByteBuf data) {
            set(data.readLong());
        }
    }

    private static final class DoubleField extends PrimitiveField {
        private double clientVersion;

        private DoubleField(Object source, VarHandle varHandle) {
            super(source, varHandle);
        }

        private double getDouble() {
            try {
                return (double) this.varHandle.get(this.source);
            } catch (Throwable throwable) {
                throw new RuntimeException("Failed to read synchronized field", throwable);
            }
        }

        private void setDouble(double value) {
            try {
                this.varHandle.set(this.source, value);
            } catch (Throwable throwable) {
                throw new RuntimeException("Failed to write synchronized field", throwable);
            }
        }

        @Override
        public boolean hasChanges() {
            // Match Double.equals: all NaNs are equal, but signed zeros differ.
            return !this.hasClientVersion
                || Double.doubleToLongBits(getDouble()) != Double.doubleToLongBits(this.clientVersion);
        }

        @Override
        protected void writeValue(ByteBuf data) {
            double value = getDouble();
            data.writeDouble(value);
            this.clientVersion = value;
        }

        @Override
        protected void readValue(ByteBuf data) {
            double value = data.readDouble();
            setDouble(value);
            this.clientVersion = value;
        }
    }

    private static final class BoxedDoubleField extends SynchronizedField {
        private BoxedDoubleField(Object source, VarHandle varHandle) {
            super(source, varHandle);
        }

        @Override
        protected void writeValue(ByteBuf data) {
            data.writeDouble((Double) get());
        }

        @Override
        protected void readValue(ByteBuf data) {
            set(data.readDouble());
        }
    }

    private static final class BooleanField extends PrimitiveField {
        private boolean clientVersion;

        private BooleanField(Object source, VarHandle varHandle) {
            super(source, varHandle);
        }

        private boolean getBoolean() {
            try {
                return (boolean) this.varHandle.get(this.source);
            } catch (Throwable throwable) {
                throw new RuntimeException("Failed to read synchronized field", throwable);
            }
        }

        private void setBoolean(boolean value) {
            try {
                this.varHandle.set(this.source, value);
            } catch (Throwable throwable) {
                throw new RuntimeException("Failed to write synchronized field", throwable);
            }
        }

        @Override
        public boolean hasChanges() {
            return !this.hasClientVersion || getBoolean() != this.clientVersion;
        }

        @Override
        protected void writeValue(ByteBuf data) {
            boolean value = getBoolean();
            data.writeBoolean(value);
            this.clientVersion = value;
        }

        @Override
        protected void readValue(ByteBuf data) {
            boolean value = data.readBoolean();
            setBoolean(value);
            this.clientVersion = value;
        }
    }

    private static final class BoxedBooleanField extends SynchronizedField {
        private BoxedBooleanField(Object source, VarHandle varHandle) {
            super(source, varHandle);
        }

        @Override
        protected void writeValue(ByteBuf data) {
            data.writeBoolean((Boolean) get());
        }

        @Override
        protected void readValue(ByteBuf data) {
            set(data.readBoolean());
        }
    }

    private static final class TextComponentField extends SynchronizedField {
        private TextComponentField(Object source, VarHandle varHandle) {
            super(source, varHandle);
        }

        @Override
        protected void writeValue(ByteBuf data) {
            TextComponents.writeToPacket(new PacketBuffer(data), (ITextComponent) get());
        }

        @Override
        protected void readValue(ByteBuf data) {
            set(TextComponents.readFromPacket(new PacketBuffer(data)));
        }
    }

    private static final class GenericStackField extends SynchronizedField {
        private GenericStackField(Object source, VarHandle varHandle) {
            super(source, varHandle);
        }

        @Override
        protected void writeValue(ByteBuf data) {
            GenericStack.writeBuffer((GenericStack) get(), new PacketBuffer(data));
        }

        @Override
        protected void readValue(ByteBuf data) {
            set(GenericStack.readBuffer(new PacketBuffer(data)));
        }
    }

    private static final class ResourceLocationField extends SynchronizedField {
        private ResourceLocationField(Object source, VarHandle varHandle) {
            super(source, varHandle);
        }

        @Override
        protected void writeValue(ByteBuf data) {
            ResourceLocation value = (ResourceLocation) get();
            writeString(data, value == null ? "" : value.toString());
        }

        @Override
        protected void readValue(ByteBuf data) {
            String value = readString(data);
            set(value.isEmpty() ? null : new ResourceLocation(value));
        }
    }

    private static final class EnumField extends SynchronizedField {
        private final Enum<?>[] values;

        private EnumField(Object source, VarHandle varHandle, Class<?> fieldType) {
            super(source, varHandle);
            this.values = (Enum<?>[]) fieldType.asSubclass(Enum.class).getEnumConstants();
        }

        @Override
        protected void writeValue(ByteBuf data) {
            Enum<?> value = (Enum<?>) get();
            data.writeShort(value == null ? -1 : value.ordinal());
        }

        @Override
        protected void readValue(ByteBuf data) {
            short ordinal = data.readShort();
            set(ordinal >= 0 && ordinal < this.values.length ? this.values[ordinal] : null);
        }
    }

    private static final class CustomField extends SynchronizedField {
        private static final Map<Class<?>, Constructor<? extends PacketWritable>> CONSTRUCTORS =
                new Object2ObjectLinkedOpenHashMap<>();

        private final Class<?> fieldType;

        private CustomField(Object source, VarHandle varHandle, Class<?> fieldType) {
            super(source, varHandle);
            this.fieldType = fieldType;
            Preconditions.checkArgument(PacketWritable.class.isAssignableFrom(this.fieldType));
        }

        @SuppressWarnings("unchecked")
        private static synchronized Constructor<? extends PacketWritable> getConstructor(Class<?> clazz) {
            Constructor<? extends PacketWritable> constructor = CONSTRUCTORS.get(clazz);
            if (constructor != null) {
                return constructor;
            }

            try {
                constructor = (Constructor<? extends PacketWritable>) clazz.getConstructor(ByteBuf.class);
                CONSTRUCTORS.put(clazz, constructor);
                return constructor;
            } catch (NoSuchMethodException e) {
                throw new RuntimeException("No constructor taking ByteBuf on " + clazz, e);
            }
        }

        @Override
        protected void writeValue(ByteBuf data) {
            ((PacketWritable) get()).writeToPacket(data);
        }

        @Override
        protected void readValue(ByteBuf data) {
            int before = data.readableBytes();
            if (before > MAX_CUSTOM_FIELD_BYTES) {
                throw new IllegalArgumentException("Custom synchronized field payload is too large: " + before);
            }

            try {
                set(getConstructor(this.fieldType).newInstance(data));
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException("Failed to deserialize " + this.fieldType, e);
            }
        }
    }

    @FunctionalInterface
    public interface Factory {
        SynchronizedField create(Object source, VarHandle varHandle, Class<?> fieldType);
    }
}
