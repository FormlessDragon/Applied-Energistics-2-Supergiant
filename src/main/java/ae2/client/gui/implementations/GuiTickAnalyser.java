package ae2.client.gui.implementations;

import ae2.client.gui.AEBaseGui;
import ae2.client.gui.color.ColorArea;
import ae2.client.gui.style.GuiStyleManager;
import ae2.client.gui.widgets.AETextField;
import ae2.container.implementations.ContainerTickAnalyser;
import ae2.core.network.InitNetwork;
import ae2.core.network.serverbound.AnalyserGenericPacket;
import ae2.core.network.serverbound.TickConfigSavePacket;
import ae2.core.network.serverbound.TickProfilerRequestPacket;
import ae2.items.tools.TickAnalyserConfig;
import ae2.util.ColorData;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.TextComponentTranslation;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

public class GuiTickAnalyser extends AEBaseGui<ContainerTickAnalyser> {
    private static final String STYLE_PATH = "/screens/tick_analyser.json";
    private static final ColorData RED = new ColorData(1.0F, 0.0F, 0.0F);
    private static final ColorData GREEN = new ColorData(0.0F, 1.0F, 0.0F);

    private int duration = 60;
    private final boolean[] enabled = new boolean[4];
    private final ColorArea[] dots = new ColorArea[4];
    private final AETextField durationInput;

    public GuiTickAnalyser(ContainerTickAnalyser container, InventoryPlayer playerInventory) {
        super(container, playerInventory, GuiStyleManager.loadStyleDoc(STYLE_PATH));
        this.widgets.addButton("start", new TextComponentTranslation("gui.ae2.tick_analyser.start"), () -> {
            syncDuration();
            InitNetwork.sendToServer(new TickProfilerRequestPacket(this.duration));
        });
        this.widgets.addButton("cancel", new TextComponentTranslation("gui.ae2.tick_analyser.cancel"),
            () -> InitNetwork.sendToServer(new TickProfilerRequestPacket(-1)));
        for (int i = 0; i < this.dots.length; i++) {
            final int index = i;
            this.widgets.add("dot_" + i, this.dots[i] = new ColorArea(0, 0, 0, 0, this, () -> cycleEnable(index)));
        }
        this.durationInput = this.widgets.addTextField("duration_input");
        this.durationInput.setMaxStringLength(String.valueOf(TickAnalyserConfig.MAX_DURATION_SECONDS).length());
        this.durationInput.setKeyFilter(this::isNumberInput);
        this.durationInput.setResponder(ignored -> {
            syncDuration();
            sendConfig();
        });
        loadConfig(container.getConfig());
        InitNetwork.sendToServer(new AnalyserGenericPacket("update"));
    }

    public void loadConfig(TickAnalyserConfig config) {
        this.duration = config.duration();
        this.enabled[0] = config.showBelow5Micros();
        this.enabled[1] = config.show5To100Micros();
        this.enabled[2] = config.show100To500Micros();
        this.enabled[3] = config.showAbove500Micros();
        if (this.durationInput != null) {
            this.durationInput.setText(String.valueOf(this.duration));
        }
        for (int i = 0; i < this.dots.length; i++) {
            if (this.dots[i] != null) {
                this.dots[i].setColor(this.enabled[i] ? GREEN : RED);
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            syncDuration();
            sendConfig();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void onGuiClosed() {
        syncDuration();
        sendConfig();
        super.onGuiClosed();
    }

    private void cycleEnable(int index) {
        this.enabled[index] = !this.enabled[index];
        this.dots[index].setColor(this.enabled[index] ? GREEN : RED);
        sendConfig();
    }

    private void syncDuration() {
        try {
            this.duration = TickAnalyserConfig.clampDurationSeconds(Integer.parseInt(this.durationInput.getText()));
        } catch (NumberFormatException ignored) {
            this.duration = 60;
        }
        if (this.durationInput != null) {
            this.durationInput.setText(String.valueOf(this.duration));
        }
    }

    private void sendConfig() {
        InitNetwork.sendToServer(new TickConfigSavePacket(new TickAnalyserConfig(this.duration, this.enabled[0],
            this.enabled[1], this.enabled[2], this.enabled[3])));
    }

    private boolean isNumberInput(char typedChar, int keyCode) {
        return keyCode == Keyboard.KEY_BACK || keyCode == Keyboard.KEY_DELETE || keyCode == Keyboard.KEY_LEFT
            || keyCode == Keyboard.KEY_RIGHT || keyCode == Keyboard.KEY_HOME || keyCode == Keyboard.KEY_END
            || typedChar >= '0' && typedChar <= '9';
    }
}
