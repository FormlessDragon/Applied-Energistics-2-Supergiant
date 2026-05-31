package ae2.integration.modules.igtooltip;

import ae2.api.integrations.igtooltip.BaseClassRegistration;
import ae2.api.parts.IPartHost;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

public class BaseClassRegistrationImpl implements BaseClassRegistration {
    private static final Logger LOG = LoggerFactory.getLogger(BaseClassRegistrationImpl.class);

    private final List<BaseClass> baseClasses = new ObjectArrayList<>();
    private final Set<BaseClass> partHostClasses = new ObjectOpenHashSet<>();

    @Override
    public void addBaseBlockEntity(Class<? extends TileEntity> blockEntityClass, Class<? extends Block> blockClass) {
        BaseClass defaultClass = new BaseClass(blockEntityClass, blockClass);

        // If any superclass is already in the list, don't add it
        for (BaseClass registeredClass : baseClasses) {
            if (registeredClass.isSuperclassOf(defaultClass)) {
                LOG.info("Not registering {}, because superclass {} is already registered.",
                    defaultClass, registeredClass);
                return;
            }
        }

        // Remove any subclasses of this class
        baseClasses.removeIf(otherClass -> {
            if (defaultClass.isSuperclassOf(otherClass)) {
                LOG.info("Replacing default server-data registration for {} with superclass {}.",
                    defaultClass, otherClass);
                return true;
            }
            return false;
        });

        baseClasses.add(defaultClass);
    }

    @Override
    public <T extends TileEntity & IPartHost> void addPartHost(Class<T> blockEntityClass,
                                                               Class<? extends Block> blockClass) {
        partHostClasses.add(new BaseClass(blockEntityClass, blockClass));
    }

    public List<BaseClass> getBaseClasses() {
        return baseClasses;
    }

    public Set<BaseClass> getPartHostClasses() {
        return partHostClasses;
    }

    public record BaseClass(Class<? extends TileEntity> blockEntity, Class<? extends Block> block) {
        public boolean isSuperclassOf(BaseClass other) {
            return this.blockEntity.isAssignableFrom(other.blockEntity);
        }
    }
}
