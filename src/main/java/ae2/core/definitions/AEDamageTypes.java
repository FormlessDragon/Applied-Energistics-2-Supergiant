package ae2.core.definitions;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSource;

public final class AEDamageTypes {

    public static final String MATTER_CANNON = "matter_cannon";

    private AEDamageTypes() {
    }

    public static DamageSource causeMatterCannonDamage(EntityPlayer player) {
        return new EntityDamageSource(MATTER_CANNON, player).setProjectile();
    }
}
