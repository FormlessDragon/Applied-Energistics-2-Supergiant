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
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public abstract class SynchronizedField<T> {
    private final Object source;
    private final MethodHandle getter;
    private final MethodHandle setter;
    private T clientVersion;

    private SynchronizedField(Object source, Field field) {
        this.source = source;
        field.setAccessible(true);
        try {
            this.getter = MethodHandles.lookup().unreflectGetter(field);
            this.setter = MethodHandles.lookup().unreflectSetter(field);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to access synchronized field " + field, e);
        }
    }

    public static SynchronizedField<?> create(Object source, Field field) {
        Class<?> fieldType = field.getType();

        if (PacketWritable.class.isAssignableFrom(fieldType)) {
            return new CustomField(source, field);
        } else if (fieldType == String.class) {
            return new StringField(source, field);
        } else if (fieldType == int.class || fieldType == Integer.class) {
            return new IntegerField(source, field);
        } else if (fieldType == long.class || fieldType == Long.class) {
            return new LongField(source, field);
        } else if (fieldType == double.class || fieldType == Double.class) {
            return new DoubleField(source, field);
        } else if (fieldType == boolean.class || fieldType == Boolean.class) {
            return new BooleanField(source, field);
        } else if (fieldType == ITextComponent.class) {
            return new TextComponentField(source, field);
        } else if (fieldType == GenericStack.class) {
            return new GenericStackField(source, field);
        } else if (fieldType == ResourceLocation.class) {
            return new ResourceLocationField(source, field);
        } else if (fieldType.isEnum()) {
            return createEnumField(source, field, fieldType);
        }

        throw new IllegalArgumentException("Cannot synchronize field " + field);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static SynchronizedField<?> createEnumField(Object source, Field field, Class<?> fieldType) {
        Class<? extends Enum> enumType = fieldType.asSubclass(Enum.class);
        return new EnumField(source, field, enumType.getEnumConstants());
    }

    private static void writeString(ByteBuf data, String value) {
        byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        data.writeInt(bytes.length);
        data.writeBytes(bytes);
    }

    private static String readString(ByteBuf data) {
        int length = data.readInt();
        byte[] bytes = new byte[length];
        data.readBytes(bytes);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    private T getCurrentValue() {
        try {
            return (T) this.getter.invoke(source);
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }

    public boolean hasChanges() {
        return !Objects.equals(getCurrentValue(), this.clientVersion);
    }

    public final void write(ByteBuf data) {
        T currentValue = getCurrentValue();
        this.clientVersion = currentValue;
        this.writeValue(data, currentValue);
    }

    public final void read(ByteBuf data) {
        T value = readValue(data);
        try {
            this.setter.invoke(source, value);
            this.clientVersion = value;
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }

    protected abstract void writeValue(ByteBuf data, T value);

    @Nullable
    protected abstract T readValue(ByteBuf data);

    private static final class StringField extends SynchronizedField<String> {
        private StringField(Object source, Field field) {
            super(source, field);
        }

        @Override
        protected void writeValue(ByteBuf data, String value) {
            writeString(data, value == null ? "" : value);
        }

        @Override
        protected String readValue(ByteBuf data) {
            return readString(data);
        }
    }

    private static final class IntegerField extends SynchronizedField<Integer> {
        private IntegerField(Object source, Field field) {
            super(source, field);
        }

        @Override
        protected void writeValue(ByteBuf data, Integer value) {
            data.writeInt(value);
        }

        @Override
        protected Integer readValue(ByteBuf data) {
            return data.readInt();
        }
    }

    private static final class LongField extends SynchronizedField<Long> {
        private LongField(Object source, Field field) {
            super(source, field);
        }

        @Override
        protected void writeValue(ByteBuf data, Long value) {
            data.writeLong(value);
        }

        @Override
        protected Long readValue(ByteBuf data) {
            return data.readLong();
        }
    }

    private static final class DoubleField extends SynchronizedField<Double> {
        private DoubleField(Object source, Field field) {
            super(source, field);
        }

        @Override
        protected void writeValue(ByteBuf data, Double value) {
            data.writeDouble(value);
        }

        @Override
        protected Double readValue(ByteBuf data) {
            return data.readDouble();
        }
    }

    private static final class BooleanField extends SynchronizedField<Boolean> {
        private BooleanField(Object source, Field field) {
            super(source, field);
        }

        @Override
        protected void writeValue(ByteBuf data, Boolean value) {
            data.writeBoolean(value);
        }

        @Override
        protected Boolean readValue(ByteBuf data) {
            return data.readBoolean();
        }
    }

    private static final class TextComponentField extends SynchronizedField<ITextComponent> {
        private TextComponentField(Object source, Field field) {
            super(source, field);
        }

        @Override
        protected void writeValue(ByteBuf data, ITextComponent value) {
            TextComponents.writeToPacket(new PacketBuffer(data), value);
        }

        @Override
        @Nullable
        protected ITextComponent readValue(ByteBuf data) {
            return TextComponents.readFromPacket(new PacketBuffer(data));
        }
    }

    private static final class GenericStackField extends SynchronizedField<GenericStack> {
        private GenericStackField(Object source, Field field) {
            super(source, field);
        }

        @Override
        protected void writeValue(ByteBuf data, GenericStack value) {
            GenericStack.writeBuffer(value, new PacketBuffer(data));
        }

        @Override
        protected GenericStack readValue(ByteBuf data) {
            return GenericStack.readBuffer(new PacketBuffer(data));
        }
    }

    private static final class ResourceLocationField extends SynchronizedField<ResourceLocation> {
        private ResourceLocationField(Object source, Field field) {
            super(source, field);
        }

        @Override
        protected void writeValue(ByteBuf data, ResourceLocation value) {
            writeString(data, value == null ? "" : value.toString());
        }

        @Override
        @Nullable
        protected ResourceLocation readValue(ByteBuf data) {
            String value = readString(data);
            return value.isEmpty() ? null : new ResourceLocation(value);
        }
    }

    private static final class EnumField<T extends Enum<T>> extends SynchronizedField<T> {
        private final T[] values;

        private EnumField(Object source, Field field, T[] values) {
            super(source, field);
            this.values = values;
        }

        @Override
        protected void writeValue(ByteBuf data, T value) {
            data.writeShort(value == null ? -1 : value.ordinal());
        }

        @Override
        @Nullable
        protected T readValue(ByteBuf data) {
            short ordinal = data.readShort();
            return ordinal < 0 ? null : this.values[ordinal];
        }
    }

    private static final class CustomField extends SynchronizedField<Object> {
        private static final Map<Class<?>, Function<ByteBuf, Object>> FACTORIES = new Object2ObjectOpenHashMap<>();
        private final Class<?> fieldType;

        private CustomField(Object source, Field field) {
            super(source, field);
            this.fieldType = field.getType();
            Preconditions.checkArgument(PacketWritable.class.isAssignableFrom(this.fieldType));
        }

        private static Function<ByteBuf, Object> getFactory(Class<?> clazz) {
            try {
                var constructor = clazz.getConstructor(ByteBuf.class);
                return buffer -> {
                    try {
                        return constructor.newInstance(buffer);
                    } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                        throw new RuntimeException("Failed to deserialize " + clazz, e);
                    }
                };
            } catch (NoSuchMethodException e) {
                throw new RuntimeException("No constructor taking ByteBuf on " + clazz, e);
            }
        }

        @Override
        protected void writeValue(ByteBuf data, Object value) {
            ((PacketWritable) value).writeToPacket(data);
        }

        @Override
        protected Object readValue(ByteBuf data) {
            return FACTORIES.computeIfAbsent(this.fieldType, CustomField::getFactory).apply(data);
        }
    }
}
