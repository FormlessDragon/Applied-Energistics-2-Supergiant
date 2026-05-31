package ae2.recipes.serializers;

import ae2.recipes.mattercannon.MatterCannonAmmoSerializer;

public final class MatterCannonAmmoRecipeFactory extends AERecipeFactoryAdapter {
    public MatterCannonAmmoRecipeFactory() {
        super(new MatterCannonAmmoSerializer());
    }
}
