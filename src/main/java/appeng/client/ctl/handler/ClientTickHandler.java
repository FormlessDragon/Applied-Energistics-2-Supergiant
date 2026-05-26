package appeng.client.ctl.handler;

import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.List;

public class ClientTickHandler {

    private static final List<Runnable> TASKS = new ArrayList<>();

    @SubscribeEvent
    public static void onClientTickEnd(final TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        TASKS.forEach(Runnable::run);
        TASKS.clear();
    }

    public static void addTask(final Runnable task) {
        TASKS.add(task);
    }

}