package ae2.crafting;

import com.mojang.authlib.GameProfile;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fml.common.FMLCommonHandler;

import java.util.Objects;
import java.util.UUID;

/**
 * Applies the player-crafting lifecycle ({@code Item.onCreated} plus {@code PlayerEvent.ItemCraftedEvent}) to
 * recipe results computed outside a real crafting context, so patterns bake in the same result mutations a
 * manual craft would apply.
 *
 * <p>The simulated player is anonymous on purpose: it has no inventory contents and its position is unset. Its
 * world is a {@link SimulatedCraftingWorld}, a server-side stand-in that reports {@code isRemote == true}, so
 * mods which gate their side effects on the client/server distinction stay out of the way instead of running
 * server-side logic for a craft that never happened. A mod that ignores that distinction still cannot reach the
 * live level, because every read on the simulated world answers empty and every write is discarded.</p>
 *
 * <p>The player and its world are created once per server and shared by all callers, on the game thread and on
 * crafting calculation threads alike. Neither is mutated after construction apart from the player's world
 * reference, which every call re-asserts to the same value.</p>
 */
public final class CraftingEventSimulation {
    private static final GameProfile SIMULATED_PLAYER_PROFILE =
        new GameProfile(UUID.fromString("0ae2a7ce-9a52-4b7e-8b1c-6f6f51e2a0be"), "[AE2]");
    private static volatile SimulatedCraftingWorld simulatedWorld;
    private static volatile FakePlayer simulatedPlayer;

    private CraftingEventSimulation() {
    }

    /**
     * Runs the crafting lifecycle hooks on a computed recipe result and returns the (possibly mutated) output.
     *
     * <p>The {@code world} argument is the real level the craft was computed for. It only decides whether to
     * simulate at all and provides the server; the hooks themselves are handed the simulated world, so listeners
     * that key off a client level behave the way they would during a manual craft.</p>
     *
     * @throws RuntimeException when the recipe or an event listener fails; callers treat that as "this recipe
     *                          cannot be encoded/produced"
     */
    public static ItemStack processCraftingResult(ItemStack output, InventoryCrafting input, World world) {
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(input, "input");
        if (!(world instanceof WorldServer worldServer)) {
            return output;
        }
        var simulatedWorld = getSimulatedWorld(worldServer);
        var player = getSimulatedPlayer(worldServer, simulatedWorld);
        output.getItem().onCreated(output, simulatedWorld, player);
        FMLCommonHandler.instance().firePlayerCraftingEvent(player, output, input);
        return output;
    }

    private static SimulatedCraftingWorld getSimulatedWorld(WorldServer worldServer) {
        var world = simulatedWorld;
        if (world == null || world.getMinecraftServer() != worldServer.getMinecraftServer()) {
            synchronized (CraftingEventSimulation.class) {
                world = simulatedWorld;
                if (world == null || world.getMinecraftServer() != worldServer.getMinecraftServer()) {
                    world = new SimulatedCraftingWorld(worldServer);
                    simulatedWorld = world;
                    // The old player's world reference and interaction manager belong to the previous server.
                    simulatedPlayer = null;
                }
            }
        }
        return world;
    }

    private static FakePlayer getSimulatedPlayer(WorldServer worldServer, SimulatedCraftingWorld simulatedWorld) {
        var player = simulatedPlayer;
        if (player == null) {
            synchronized (CraftingEventSimulation.class) {
                player = simulatedPlayer;
                if (player == null) {
                    player = new FakePlayer(worldServer, SIMULATED_PLAYER_PROFILE);
                    simulatedPlayer = player;
                }
            }
        }
        // Re-asserted on every call: idempotent, cheap, and it heals a world reference left dirty by a listener.
        // Callers run on calculation threads as well as the game thread, so this must stay free of side effects.
        player.world = simulatedWorld;
        player.dimension = simulatedWorld.provider.getDimension();
        return player;
    }
}
