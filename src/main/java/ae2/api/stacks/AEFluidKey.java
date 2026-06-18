package ae2.api.stacks;

import ae2.api.storage.AEKeyFilter;
import ae2.core.AELog;
import com.google.common.base.Preconditions;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class AEFluidKey extends AEKey {
    public static final int AMOUNT_BUCKET = 1000;
    public static final int AMOUNT_BLOCK = 1000;

    private final FluidStack stack;
    private final int hashCode;

    private AEFluidKey(FluidStack stack) {
        Preconditions.checkArgument(stack != null && stack.getFluid() != null && stack.amount > 0, "stack was empty");
        this.stack = stack.copy();
        this.stack.amount = 1;
        this.hashCode = hashStack(this.stack);
    }

    @Nullable
    public static AEFluidKey of(Fluid fluid) {
        return fluid == null ? null : of(new FluidStack(fluid, 1));
    }

    @Nullable
    public static AEFluidKey of(FluidStack fluidVariant) {
        if (fluidVariant == null || fluidVariant.getFluid() == null || fluidVariant.amount <= 0) {
            return null;
        }
        return new AEFluidKey(fluidVariant);
    }

    public static boolean matches(AEKey what, FluidStack fluid) {
        return what instanceof AEFluidKey fluidKey && fluidKey.matches(fluid);
    }

    public static boolean is(AEKey what) {
        return what instanceof AEFluidKey;
    }

    public static AEKeyFilter filter() {
        return AEFluidKey::is;
    }

    @Nullable
    public static AEFluidKey fromTag(NBTTagCompound tag) {
        try {
            return of(FluidStack.loadFluidStackFromNBT(tag));
        } catch (Exception e) {
            AELog.debug("Tried to load an invalid fluid key from NBT: %s", tag, e);
            return null;
        }
    }

    public static AEFluidKey fromPacket(PacketBuffer data) {
        try {
            return fromTag(data.readCompoundTag());
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not read fluid key", e);
        }
    }

    public static boolean is(@Nullable GenericStack stack) {
        return stack != null && stack.what() instanceof AEFluidKey;
    }

    private static int hashStack(FluidStack stack) {
        int result = stack.getFluid().hashCode();
        result = 31 * result + (stack.tag == null ? 0 : stack.tag.hashCode());
        return result;
    }

    public boolean matches(FluidStack variant) {
        return variant != null && stack.isFluidEqual(variant) && Objects.equals(stack.tag, variant.tag);
    }

    @Override
    public AEKeyType getType() {
        return AEKeyType.fluids();
    }

    @Override
    public AEFluidKey dropSecondary() {
        return of(new FluidStack(getFluid(), 1));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AEFluidKey aeFluidKey = (AEFluidKey) o;
        return hashCode == aeFluidKey.hashCode && matches(aeFluidKey.stack);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public NBTTagCompound toTag() {
        return stack.writeToNBT(new NBTTagCompound());
    }

    @Override
    public Object getPrimaryKey() {
        return getFluid();
    }

    @Override
    public String getModId() {
        var modId = FluidRegistry.getModId(stack);
        return modId != null ? modId : "";
    }

    @Override
    @Nullable
    public ResourceLocation getId() {
        String fluidName = FluidRegistry.getFluidName(stack);
        return fluidName != null ? new ResourceLocation(fluidName) : null;
    }

    @Override
    protected ITextComponent computeDisplayName() {
        return new TextComponentString(stack.getLocalizedName());
    }

    @Override
    public boolean hasTagCompound() {
        var tag = stack.tag;
        return tag != null && !tag.isEmpty();
    }

    @Override
    @Nullable
    public NBTBase get(String componentId) {
        var tag = stack.tag;
        var value = tag == null ? null : tag.getTag(componentId);
        return value == null ? null : value.copy();
    }

    @Override
    public @Nullable NBTTagCompound getTagCompound() {
        var tag = stack.tag;
        return tag == null || tag.isEmpty() ? null : tag.copy();
    }

    @Override
    public int getTagCompoundSize() {
        var tag = stack.tag;
        return tag == null ? 0 : tag.getSize();
    }

    @Override
    public FluidStack getReadOnlyStack() {
        return stack;
    }

    public FluidStack toStack(int amount) {
        FluidStack result = stack.copy();
        result.amount = amount;
        return result;
    }

    public Fluid getFluid() {
        return stack.getFluid();
    }

    @Override
    public void writeToPacket(PacketBuffer data) {
        NBTTagCompound tag = toTag();
        data.writeCompoundTag(tag);
    }

    @Override
    public String toString() {
        var id = getId();
        String idString = id != null ? id.toString() : getFluid().getClass().getSimpleName() + "(unregistered)";
        return hasTagCompound() ? idString + " (+components)" : idString;
    }
}
