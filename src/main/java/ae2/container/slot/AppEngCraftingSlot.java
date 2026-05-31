package ae2.container.slot;

import ae2.api.inventories.InternalInventory;
import ae2.util.inv.AppEngInternalInventory;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.fml.common.FMLCommonHandler;
import org.jetbrains.annotations.Nullable;

public class AppEngCraftingSlot extends AppEngSlot {
    private static final Container DUMMY_CONTAINER = new Container() {
        @Override
        public boolean canInteractWith(EntityPlayer playerIn) {
            return false;
        }
    };

    private final InternalInventory craftingGrid;
    private final EntityPlayer player;
    private int amountCrafted;
    @Nullable
    private IRecipe recipeUsed;

    public AppEngCraftingSlot(EntityPlayer player, InternalInventory craftingGrid) {
        this(player, craftingGrid, 0, 0);
    }

    public AppEngCraftingSlot(EntityPlayer player, InternalInventory craftingGrid, int x, int y) {
        super(new AppEngInternalInventory(1), 0, x, y);
        this.player = player;
        this.craftingGrid = craftingGrid;
    }

    @Override
    public boolean isItemValid(ItemStack stack) {
        return false;
    }

    @Override
    protected void onCrafting(ItemStack stack, int amount) {
        this.amountCrafted += amount;
        this.onCrafting(stack);
    }

    @Override
    protected void onCrafting(ItemStack stack) {
        stack.onCrafting(this.player.world, this.player, this.amountCrafted);
        this.amountCrafted = 0;
    }

    @Override
    public ItemStack onTake(EntityPlayer playerIn, ItemStack stack) {
        this.onCrafting(stack);
        stack.getItem().onCreated(stack, playerIn.world, playerIn);

        InventoryCrafting crafting = new InventoryCrafting(this.getCraftingContainer(), 3, 3);
        for (int slot = 0; slot < this.craftingGrid.size(); slot++) {
            crafting.setInventorySlotContents(slot, this.craftingGrid.getStackInSlot(slot));
        }

        FMLCommonHandler.instance().firePlayerCraftingEvent(playerIn, stack, crafting);
        ForgeHooks.setCraftingPlayer(playerIn);
        NonNullList<ItemStack> remainingItems = this.getRemainingItems(crafting, playerIn.world);
        ForgeHooks.setCraftingPlayer(null);

        for (int slot = 0; slot < remainingItems.size(); slot++) {
            ItemStack inSlot = this.craftingGrid.getStackInSlot(slot);
            ItemStack remainder = remainingItems.get(slot);

            if (!inSlot.isEmpty()) {
                this.craftingGrid.extractItem(slot, 1, false);
            }

            if (!remainder.isEmpty()) {
                if (this.craftingGrid.getStackInSlot(slot).isEmpty()) {
                    this.craftingGrid.setItemDirect(slot, remainder);
                } else if (!this.player.inventory.addItemStackToInventory(remainder)) {
                    this.player.dropItem(remainder, false);
                }
            }
        }

        if (getContainer() != null) {
            getContainer().onCraftMatrixChanged(this.craftingGrid.toContainer());
        }

        return stack;
    }

    public void setDisplayedCraftingOutput(ItemStack stack) {
        getInventory().setItemDirect(0, stack);
    }

    @Override
    public ItemStack decrStackSize(int amount) {
        if (this.getHasStack()) {
            this.amountCrafted += Math.min(amount, this.getStack().getCount());
        }

        return super.decrStackSize(amount);
    }

    protected NonNullList<ItemStack> getRemainingItems(InventoryCrafting crafting, World world) {
        return CraftingManager.getRemainingItems(crafting, world);
    }

    @Nullable
    public IRecipe getRecipeUsed() {
        return this.recipeUsed;
    }

    public void setRecipeUsed(@Nullable IRecipe recipeUsed) {
        this.recipeUsed = recipeUsed;
    }

    private Container getCraftingContainer() {
        return getContainer() != null ? getContainer() : DUMMY_CONTAINER;
    }
}
