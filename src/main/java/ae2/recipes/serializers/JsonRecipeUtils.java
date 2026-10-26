package ae2.recipes.serializers;

import ae2.api.ids.AEItemIds;
import ae2.core.AELog;
import ae2.util.EmptyArrays;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.JsonContext;
import net.minecraftforge.oredict.OreDictionary;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class JsonRecipeUtils {
    private static final ResourceLocation KNIFE_TAG = new ResourceLocation("ae2", "knife");
    private static final ResourceLocation ALL_CERTUS_QUARTZ_TAG = new ResourceLocation("ae2", "all_certus_quartz");
    private static final ResourceLocation ENDER_PEARLS_TAG = new ResourceLocation("c", "ender_pearls");
    private static final int MAX_JSON_ARRAY_ENTRIES = 1024;

    private JsonRecipeUtils() {
    }

    public static ItemStack readItemStack(JsonObject json, String key, JsonContext ctx) {
        return CraftingHelper.getItemStack(normalizeStack(JsonUtils.getJsonObject(json, key)), ctx);
    }

    public static ItemStack readItemStack(JsonObject json, JsonContext ctx) {
        return CraftingHelper.getItemStack(normalizeStack(json), ctx);
    }

    public static List<ItemStack> readItemStacks(JsonObject json, String key, JsonContext ctx) {
        JsonArray array = JsonUtils.getJsonArray(json, key);
        validateArraySize(array, key);
        List<ItemStack> result = new ObjectArrayList<>(array.size());
        for (JsonElement element : array) {
            result.add(readItemStack(readObject(element, key + " entry"), ctx));
        }
        return result;
    }

    public static JsonObject readObject(JsonElement json, String description) {
        if (json == null || json.isJsonNull()) {
            throw new JsonSyntaxException("Expected " + description + " to be an object");
        }
        if (!json.isJsonObject()) {
            throw new JsonSyntaxException("Expected " + description + " to be an object, got " + json);
        }
        return json.getAsJsonObject();
    }

    public static Ingredient readIngredient(JsonObject json, String key, JsonContext ctx) {
        return readIngredient(json.get(key), ctx);
    }

    public static List<Ingredient> readIngredients(JsonObject json, String key, JsonContext ctx) {
        JsonArray array = JsonUtils.getJsonArray(json, key);
        validateArraySize(array, key);
        List<Ingredient> result = new ObjectArrayList<>(array.size());
        for (JsonElement element : array) {
            result.add(readIngredient(element, ctx));
        }
        return result;
    }

    public static Item readItem(JsonObject json, String key) {
        ResourceLocation id = parseId(JsonUtils.getString(json, key));
        Item item = Item.REGISTRY.getObject(id);
        if (item == null) {
            throw new JsonSyntaxException("Unknown item: " + id);
        }
        return item;
    }

    public static Block readBlock(JsonObject json, String key) {
        ResourceLocation id = parseId(JsonUtils.getString(json, key));
        Block block = Block.REGISTRY.getObject(id);
        if (block == null) {
            throw new JsonSyntaxException("Unknown block: " + id);
        }
        return block;
    }

    private static Ingredient readIngredient(JsonElement json, JsonContext ctx) {
        Ingredient ingredient;
        try {
            Ingredient bridged = tryReadIngredient(json, ctx);
            ingredient = bridged != null ? bridged : CraftingHelper.getIngredient(json, ctx);
        } catch (RuntimeException e) {
            if (isOreDictionaryIngredient(json)) {
                return Ingredient.EMPTY;
            }
            if (isUnknownItemException(e)) {
                AELog.warn("Skipping recipe ingredient with no matching item: %s", json);
                return Ingredient.EMPTY;
            }
            throw e;
        }
        if (hasMatchingItems(ingredient)) {
            return ingredient;
        }
        if (!isOreDictionaryIngredient(json) && json != null && !json.isJsonNull()) {
            AELog.warn("Skipping recipe ingredient with no matching item: %s", json);
        }
        return Ingredient.EMPTY;
    }

    @Nullable
    private static Ingredient tryReadIngredient(JsonElement json, JsonContext ctx) {
        if (json == null || json.isJsonNull()) {
            return Ingredient.EMPTY;
        }

        if (json.isJsonArray()) {
            validateArraySize(json.getAsJsonArray(), "ingredient alternatives");
            List<ItemStack> alternatives = new ObjectArrayList<>();
            addAlternativeStacks(alternatives, json.getAsJsonArray(), ctx);
            return createIngredient(alternatives);
        }

        if (!json.isJsonObject()) {
            return null;
        }

        JsonObject object = json.getAsJsonObject();
        if (!object.has("tag")) {
            return null;
        }

        ResourceLocation tagId = parseId(JsonUtils.getString(object, "tag"));
        return createIngredientForTag(tagId);
    }

    private static Ingredient createIngredientForTag(ResourceLocation tagId) {
        List<ItemStack> alternatives = new ObjectArrayList<>();
        Set<String> seen = new ObjectLinkedOpenHashSet<>();
        addTagItems(alternatives, seen, tagId);
        return createIngredient(alternatives);
    }

    private static Ingredient createIngredient(List<ItemStack> alternatives) {
        if (alternatives.isEmpty()) {
            return Ingredient.EMPTY;
        }
        return Ingredient.fromStacks(alternatives.toArray(EmptyArrays.EMPTY_ITEM_STACK_ARRAY));
    }

    private static void addAlternativeStacks(List<ItemStack> alternatives, JsonArray array, JsonContext ctx) {
        validateArraySize(array, "ingredient alternatives");
        Set<String> seen = new ObjectLinkedOpenHashSet<>();
        for (JsonElement element : array) {
            Ingredient ingredient = readIngredient(element, ctx);
            addMatchingStacks(alternatives, seen, ingredient);
        }
    }

    private static void validateArraySize(JsonArray array, String description) {
        if (array.size() > MAX_JSON_ARRAY_ENTRIES) {
            throw new JsonSyntaxException(description + " has too many entries: " + array.size());
        }
    }

    private static void addMatchingStacks(List<ItemStack> alternatives, Set<String> seen, Ingredient ingredient) {
        for (ItemStack stack : ingredient.getMatchingStacks()) {
            addStack(alternatives, seen, stack);
        }
    }

    private static void addTagItems(List<ItemStack> alternatives, Set<String> seen, ResourceLocation tagId) {
        if (KNIFE_TAG.equals(tagId)) {
            addItem(alternatives, seen, AEItemIds.CERTUS_QUARTZ_KNIFE);
            addItem(alternatives, seen, AEItemIds.NETHER_QUARTZ_KNIFE);
            return;
        }

        if (ALL_CERTUS_QUARTZ_TAG.equals(tagId)) {
            addTagItems(alternatives, seen, new ResourceLocation("c", "gems/certus_quartz"));
            addItem(alternatives, seen, AEItemIds.CERTUS_QUARTZ_CRYSTAL);
            addItem(alternatives, seen, AEItemIds.CERTUS_QUARTZ_CRYSTAL_CHARGED);
            return;
        }

        if (ENDER_PEARLS_TAG.equals(tagId)) {
            addOreDictionaryItems(alternatives, seen, "enderPearl");
            return;
        }

        String oreDictionaryName = toOreDictionaryName(tagId);
        if (oreDictionaryName != null) {
            addOreDictionaryItems(alternatives, seen, oreDictionaryName);
            return;
        }

        throw new JsonSyntaxException("Unsupported ingredient tag: " + tagId);
    }

    private static void addOreDictionaryItems(List<ItemStack> alternatives, Set<String> seen, String oreDictionaryName) {
        for (ItemStack stack : OreDictionary.getOres(oreDictionaryName)) {
            addStack(alternatives, seen, stack);
        }
    }

    private static void addItem(List<ItemStack> alternatives, Set<String> seen, ResourceLocation itemId) {
        Item item = Item.REGISTRY.getObject(itemId);
        if (item == null) {
            throw new JsonSyntaxException("Unknown item: " + itemId);
        }
        addStack(alternatives, seen, new ItemStack(item));
    }

    private static void addStack(List<ItemStack> alternatives, Set<String> seen, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }

        ItemStack copy = stack.copy();
        ResourceLocation itemId = Item.REGISTRY.getNameForObject(copy.getItem());
        String tagKey = "";
        if (copy.hasTagCompound() && copy.getTagCompound() != null) {
            tagKey = copy.getTagCompound().toString();
        }
        String key = String.valueOf(itemId) + '#' + copy.getMetadata() + '#' + tagKey;
        if (seen.add(key)) {
            alternatives.add(copy);
        }
    }

    @Nullable
    private static String toOreDictionaryName(ResourceLocation tagId) {
        String namespace = tagId.getNamespace();
        if (!"forge".equals(namespace) && !"c".equals(namespace)) {
            return null;
        }

        String[] pathSegments = tagId.getPath().split("/");
        if (pathSegments.length < 2) {
            return null;
        }

        String prefix = switch (pathSegments[0]) {
            case "items" -> "item";
            case "dyes" -> "dye";
            case "ingots" -> "ingot";
            case "dusts" -> "dust";
            case "gems" -> "gem";
            case "nuggets" -> "nugget";
            case "plates" -> "plate";
            case "rods" -> "rod";
            default -> null;
        };
        if (prefix == null) {
            return null;
        }

        StringBuilder result = new StringBuilder(prefix);
        for (int i = 1; i < pathSegments.length; i++) {
            appendOreDictionaryToken(result, pathSegments[i]);
        }
        return result.toString();
    }

    private static void appendOreDictionaryToken(StringBuilder result, String token) {
        String[] parts = token.split("_");
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            result.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                result.append(part.substring(1));
            }
        }
    }

    private static JsonObject normalizeStack(JsonObject json) {
        if (!json.has("id")) {
            return json;
        }

        JsonObject normalized = copyObject(json);
        normalized.addProperty("item", JsonUtils.getString(json, "id"));
        normalized.remove("id");
        return normalized;
    }

    public static JsonObject copyObject(JsonObject source) {
        return copyElement(source).getAsJsonObject();
    }

    private static JsonElement copyElement(JsonElement element) {
        if (element.isJsonObject()) {
            JsonObject copy = new JsonObject();
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                copy.add(entry.getKey(), copyElement(entry.getValue()));
            }
            return copy;
        }
        if (element.isJsonArray()) {
            JsonArray copy = new JsonArray();
            for (JsonElement child : element.getAsJsonArray()) {
                copy.add(copyElement(child));
            }
            return copy;
        }
        return element;
    }

    private static ResourceLocation parseId(String id) {
        try {
            int separator = id.indexOf(':');
            if (separator > 0) {
                return new ResourceLocation(id.substring(0, separator), id.substring(separator + 1));
            }
            return new ResourceLocation(id);
        } catch (RuntimeException e) {
            throw new JsonSyntaxException("Invalid resource id: " + id, e);
        }
    }

    public static boolean hasMatchingItems(Ingredient ingredient) {
        if (ingredient == null || ingredient == Ingredient.EMPTY) {
            return false;
        }
        ItemStack[] stacks = ingredient.getMatchingStacks();
        if (stacks == null) {
            return false;
        }
        for (ItemStack stack : stacks) {
            if (stack != null && !stack.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasMatchingItems(List<Ingredient> ingredients) {
        if (ingredients == null || ingredients.isEmpty()) {
            return false;
        }
        for (Ingredient ingredient : ingredients) {
            if (!hasMatchingItems(ingredient)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isOreDictionaryIngredient(JsonElement json) {
        if (json == null || json.isJsonNull()) {
            return false;
        }
        if (json.isJsonArray()) {
            JsonArray array = json.getAsJsonArray();
            if (array.isEmpty()) {
                return false;
            }
            for (JsonElement element : array) {
                if (!isOreDictionaryIngredient(element)) {
                    return false;
                }
            }
            return true;
        }
        if (!json.isJsonObject()) {
            return false;
        }

        JsonObject object = json.getAsJsonObject();
        if (object.has("ore")) {
            return true;
        }
        if (object.has("type")) {
            String type = JsonUtils.getString(object, "type");
            if ("forge:ore_dict".equals(type) || "forge:oredict".equals(type) || type.endsWith(":ore_dict")) {
                return true;
            }
        }
        if (object.has("tag")) {
            ResourceLocation tagId = parseId(JsonUtils.getString(object, "tag"));
            return ENDER_PEARLS_TAG.equals(tagId) || toOreDictionaryName(tagId) != null;
        }
        return false;
    }

    private static boolean isUnknownItemException(Throwable throwable) {
        while (throwable != null) {
            String message = throwable.getMessage();
            if (message != null && (message.startsWith("Unknown item") || message.contains("Unknown item '"))) {
                return true;
            }
            throwable = throwable.getCause();
        }
        return false;
    }
}
