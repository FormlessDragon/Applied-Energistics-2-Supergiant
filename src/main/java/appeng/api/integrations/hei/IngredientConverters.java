package appeng.api.integrations.hei;

import com.google.common.collect.ImmutableList;

import java.util.List;

public final class IngredientConverters {
    private static List<IngredientConverter<?>> converters = ImmutableList.of();

    private IngredientConverters() {
    }

    public static synchronized void register(IngredientConverter<?> converter) {
        for (IngredientConverter<?> existingConverter : converters) {
            if (existingConverter.getIngredientType() == converter.getIngredientType()) {
                return;
            }
        }
        converters = ImmutableList.<IngredientConverter<?>>builder()
                                  .addAll(converters)
                                  .add(converter)
                                  .build();
    }

    public static synchronized List<IngredientConverter<?>> getConverters() {
        return converters;
    }
}
