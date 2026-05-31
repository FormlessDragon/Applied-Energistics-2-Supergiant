package ae2.recipes.transform;

import ae2.recipes.AERecipeTypes;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public final class TransformLogic {
    private static Set<Item> explosionCache;

    private TransformLogic() {
    }

    public static void clearCache() {
        explosionCache = null;
    }

    public static boolean canTransformInExplosion(EntityItem entity) {
        return getTransformableItemsExplosion().contains(entity.getItem().getItem());
    }

    public static boolean tryTransform(EntityItem entity, Predicate<TransformCircumstance> predicate) {
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

            for (Reference2IntMap.Entry<EntityItem> entry : consumed.reference2IntEntrySet()) {
                EntityItem itemEntity = entry.getKey();
                itemEntity.getItem().splitStack(entry.getIntValue());
                if (itemEntity.getItem().isEmpty()) {
                    itemEntity.setDead();
                }
            }

            ItemStack result = recipe.getResultItem();
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
            crafted.motionX = xSpeed;
            crafted.motionY = ySpeed;
            crafted.motionZ = zSpeed;
            world.spawnEntity(crafted);
            return true;
        }

        return false;
    }

    private static Set<Item> getTransformableItemsExplosion() {
        Set<Item> ret = explosionCache;
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
}
