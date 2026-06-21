package ae2.recipes.transform;

import ae2.recipes.AERecipeTypes;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

public final class TransformLogic {
    private static ReferenceSet<Item> explosionCache;
    private static ReferenceSet<Item> anyFluidCache;
    private static final Object2ObjectMap<String, ReferenceSet<Item>> fluidCache = new Object2ObjectOpenHashMap<>();
    private static ReferenceSet<Item> anyFluidDamageProtectionCache;
    private static final Object2ObjectMap<String, ReferenceSet<Item>> fluidDamageProtectionCache =
        new Object2ObjectOpenHashMap<>();

    private TransformLogic() {
    }

    public static void clearCache() {
        explosionCache = null;
        anyFluidCache = null;
        fluidCache.clear();
        anyFluidDamageProtectionCache = null;
        fluidDamageProtectionCache.clear();
    }

    public static boolean canTransformInExplosion(EntityItem entity) {
        return getTransformableItemsExplosion().contains(entity.getItem().getItem());
    }

    public static boolean canTransformInAnyFluid(EntityItem entity) {
        return getTransformableItemsAnyFluid().contains(entity.getItem().getItem());
    }

    public static boolean canTransformInFluid(EntityItem entity, Fluid fluid) {
        return fluid != null && getTransformableItemsFluid(fluid).contains(entity.getItem().getItem());
    }

    public static boolean canProtectFromFluidDamage(EntityItem entity) {
        return getFluidDamageProtectionItemsAnyFluid().contains(entity.getItem().getItem());
    }

    public static boolean canProtectFromFluidDamage(EntityItem entity, Fluid fluid) {
        return fluid != null && getFluidDamageProtectionItemsFluid(fluid).contains(entity.getItem().getItem());
    }

    public static boolean hasIngredients(EntityItem entity, Predicate<TransformCircumstance> predicate) {
        World world = entity.world;
        for (var recipe : AERecipeTypes.TRANSFORM.getRecipes()) {
            if (!predicate.test(recipe.getCircumstance()) || recipe.getIngredients().isEmpty()) {
                continue;
            }

            double radius = recipe.getCircumstance().isExplosion() ? 4.0D : 1.0D;
            AxisAlignedBB region = new AxisAlignedBB(entity.posX - radius, entity.posY - radius, entity.posZ - radius,
                entity.posX + radius, entity.posY + radius, entity.posZ + radius);
            List<EntityItem> entities = world.getEntitiesWithinAABB(EntityItem.class, region,
                e -> e != null && !e.isDead);

            List<Ingredient> missing = new ObjectArrayList<>(recipe.getIngredients());
            Reference2IntMap<EntityItem> consumed = new Reference2IntOpenHashMap<>();
            consumed.defaultReturnValue(0);

            for (var itemEntity : entities) {
                ItemStack stack = itemEntity.getItem();
                if (stack.isEmpty()) {
                    continue;
                }

                for (var iterator = missing.iterator(); iterator.hasNext(); ) {
                    Ingredient ingredient = iterator.next();
                    int alreadyClaimed = consumed.getInt(itemEntity);
                    if (ingredient.apply(stack) && stack.getCount() - alreadyClaimed > 0) {
                        consumed.put(itemEntity, alreadyClaimed + 1);
                        iterator.remove();
                        break;
                    }
                }
            }

            if (missing.isEmpty()) {
                return true;
            }
        }

        return false;
    }

    public static boolean tryTransform(EntityItem entity, Predicate<TransformCircumstance> predicate) {
        RecipeMatch match = findMatchingRecipe(entity, predicate);
        if (match == null) {
            return false;
        }

        World world = entity.world;
        for (Reference2IntMap.Entry<EntityItem> entry : match.consumed.reference2IntEntrySet()) {
            EntityItem itemEntity = entry.getKey();
            itemEntity.getItem().splitStack(entry.getIntValue());
            if (itemEntity.getItem().isEmpty()) {
                itemEntity.setDead();
            }
        }

        ItemStack result = match.recipe.getResultItem();
        double x = Math.floor(entity.posX) + .25d + world.rand.nextDouble() * .5;
        double y = Math.floor(entity.posY) + .25d + world.rand.nextDouble() * .5;
        double z = Math.floor(entity.posZ) + .25d + world.rand.nextDouble() * .5;
        double xSpeed = world.rand.nextDouble() * .25 - .125;
        double ySpeed = world.rand.nextDouble() * .25 - .125;
        double zSpeed = world.rand.nextDouble() * .25 - .125;

        EntityItem fallbackEntity = new EntityItem(world, x, y, z, result);
        Entity crafted = result.getItem().hasCustomEntity(result)
            ? result.getItem().createEntity(world, fallbackEntity, result)
            : fallbackEntity;
        if (crafted == null) {
            crafted = fallbackEntity;
        }
        Fluid fluid = match.recipe.getCircumstance().getFluid();
        if (fluid != null && crafted instanceof FluidTransformProtectedItem protectedItem) {
            protectedItem.ae2_protectFromTransformFluid(fluid);
        }
        crafted.motionX = xSpeed;
        crafted.motionY = ySpeed;
        crafted.motionZ = zSpeed;
        world.spawnEntity(crafted);
        return true;
    }

    private static @Nullable RecipeMatch findMatchingRecipe(EntityItem entity,
                                                            Predicate<TransformCircumstance> predicate) {
        World world = entity.world;
        for (var recipe : AERecipeTypes.TRANSFORM.getRecipes()) {
            if (!predicate.test(recipe.getCircumstance()) || recipe.getIngredients().isEmpty()) {
                continue;
            }

            double radius = recipe.getCircumstance().isExplosion() ? 4.0D : 1.0D;
            AxisAlignedBB region = new AxisAlignedBB(entity.posX - radius, entity.posY - radius, entity.posZ - radius,
                entity.posX + radius, entity.posY + radius, entity.posZ + radius);
            List<EntityItem> entities = world.getEntitiesWithinAABB(EntityItem.class, region,
                e -> e != null && !e.isDead);

            List<Ingredient> missing = new ObjectArrayList<>(recipe.getIngredients());
            Reference2IntMap<EntityItem> consumed = new Reference2IntOpenHashMap<>();
            consumed.defaultReturnValue(0);

            if (recipe.getCircumstance().isExplosion()) {
                if (missing.stream().noneMatch(ingredient -> ingredient.apply(entity.getItem()))) {
                    continue;
                }
            } else if (!missing.getFirst().apply(entity.getItem())) {
                continue;
            }

            for (var itemEntity : entities) {
                ItemStack stack = itemEntity.getItem();
                if (stack.isEmpty()) {
                    continue;
                }

                for (var iterator = missing.iterator(); iterator.hasNext(); ) {
                    Ingredient ingredient = iterator.next();
                    int alreadyClaimed = consumed.getInt(itemEntity);
                    if (ingredient.apply(stack) && stack.getCount() - alreadyClaimed > 0) {
                        consumed.put(itemEntity, alreadyClaimed + 1);
                        iterator.remove();
                        break;
                    }
                }
            }

            if (!missing.isEmpty()) {
                continue;
            }

            return new RecipeMatch(recipe, consumed);
        }

        return null;
    }

    private static ReferenceSet<Item> getTransformableItemsAnyFluid() {
        var ret = anyFluidCache;
        if (ret == null) {
            ret = new ReferenceOpenHashSet<>();
            for (var recipe : AERecipeTypes.TRANSFORM.getRecipes()) {
                if (!recipe.getCircumstance().isFluid()) {
                    continue;
                }
                collectFirstIngredientItems(recipe, ret);
            }
            anyFluidCache = ret;
        }
        return ret;
    }

    private static ReferenceSet<Item> getTransformableItemsFluid(Fluid fluid) {
        String fluidName = fluid.getName();
        return fluidCache.computeIfAbsent(fluidName, ignored -> {
            var ret = new ReferenceOpenHashSet<Item>();
            for (var recipe : AERecipeTypes.TRANSFORM.getRecipes()) {
                if (!recipe.getCircumstance().isFluid(fluid)) {
                    continue;
                }
                collectFirstIngredientItems(recipe, ret);
            }
            return ret;
        });
    }

    private static ReferenceSet<Item> getFluidDamageProtectionItemsAnyFluid() {
        var ret = anyFluidDamageProtectionCache;
        if (ret == null) {
            ret = new ReferenceOpenHashSet<>();
            for (var recipe : AERecipeTypes.TRANSFORM.getRecipes()) {
                if (!recipe.getCircumstance().isFluid()) {
                    continue;
                }
                collectIngredientItems(recipe, ret);
            }
            anyFluidDamageProtectionCache = ret;
        }
        return ret;
    }

    private static ReferenceSet<Item> getFluidDamageProtectionItemsFluid(Fluid fluid) {
        String fluidName = fluid.getName();
        return fluidDamageProtectionCache.computeIfAbsent(fluidName, ignored -> {
            var ret = new ReferenceOpenHashSet<Item>();
            for (var recipe : AERecipeTypes.TRANSFORM.getRecipes()) {
                if (!recipe.getCircumstance().isFluid(fluid)) {
                    continue;
                }
                collectIngredientItems(recipe, ret);
            }
            return ret;
        });
    }

    private static ReferenceSet<Item> getTransformableItemsExplosion() {
        var ret = explosionCache;
        if (ret == null) {
            ret = new ReferenceOpenHashSet<>();
            for (var recipe : AERecipeTypes.TRANSFORM.getRecipes()) {
                if (!recipe.getCircumstance().isExplosion()) {
                    continue;
                }

                for (Ingredient ingredient : recipe.getIngredients()) {
                    for (ItemStack stack : ingredient.getMatchingStacks()) {
                        ret.add(stack.getItem());
                    }
                }
            }
            explosionCache = ret;
        }
        return ret;
    }

    private static void collectIngredientItems(TransformRecipe recipe, ReferenceSet<Item> output) {
        for (Ingredient ingredient : recipe.getIngredients()) {
            for (ItemStack stack : ingredient.getMatchingStacks()) {
                if (!stack.isEmpty()) {
                    output.add(stack.getItem());
                }
            }
        }
    }

    private static void collectFirstIngredientItems(TransformRecipe recipe, ReferenceSet<Item> output) {
        if (recipe.getIngredients().isEmpty()) {
            return;
        }

        for (ItemStack stack : recipe.getIngredients().getFirst().getMatchingStacks()) {
            if (!stack.isEmpty()) {
                output.add(stack.getItem());
            }
        }
    }

    private record RecipeMatch(TransformRecipe recipe, Reference2IntMap<EntityItem> consumed) {
    }
}
