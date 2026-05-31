package ae2.client;

import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.List;

public final class ClientTickHandler {
    private static final List<Runnable> TASKS = new ArrayList<>();

    private ClientTickHandler() {
    }

    @SubscribeEvent
    public static void onClientTickEnd(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        TASKS.forEach(Runnable::run);
        TASKS.clear();
    }

    public static void addTask(Runnable task) {
        TASKS.add(task);
    }
}
