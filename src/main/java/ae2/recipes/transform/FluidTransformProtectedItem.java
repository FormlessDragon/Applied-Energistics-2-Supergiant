package ae2.recipes.transform;

import net.minecraftforge.fluids.Fluid;

/**
 * Internal bridge used by transform logic to mark freshly crafted item entities.
 *
 * <p>Fluid transform outputs are spawned inside the fluid that created them. In 1.12 lava would otherwise burn a
 * normal {@code EntityItem} before the player can pick it up, so the creating logic marks only that fresh entity for
 * short-lived damage protection.</p>
 */
public interface FluidTransformProtectedItem {
    /**
     * Protect this item entity from the fluid that just produced it.
     *
     * @param fluid The Forge 1.12 fluid instance, identified by its registry name such as {@code lava}.
     */
    void ae2_protectFromTransformFluid(Fluid fluid);
}
