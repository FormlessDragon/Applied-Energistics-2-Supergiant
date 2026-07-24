package ae2.api.integrations.hei;

import ae2.integration.modules.hei.FluidIngredientConverter;
import ae2.integration.modules.hei.ItemIngredientConverter;
import com.google.common.collect.ImmutableList;

import java.util.List;

public final class IngredientConverters {
    private static List<IngredientConverter<?>> converters = ImmutableList.of(
        new ItemIngredientConverter(),
        new FluidIngredientConverter()
    );

    private IngredientConverters() {
    }

    public static synchronized void register(IngredientConverter<?> converter) {
        for (IngredientConverter<?> existingConverter : converters) {
            if (existingConverter.getIngredientType() == converter.getIngredientType()) {
                return;
            }
        }
        converters = ImmutableList.<IngredientConverter<?>>builderWithExpectedSize(converters.size() + 1)
                                  .addAll(converters)
                                  .add(converter)
                                  .build();
    }

    public static synchronized List<IngredientConverter<?>> getConverters() {
        return converters;
    }
}
