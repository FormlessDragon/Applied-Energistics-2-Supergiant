package ae2.client.gui.implementations;

import ae2.client.gui.AEBaseGui;
import ae2.core.AppEng;
import ae2.core.network.InitNetwork;
import ae2.client.gui.elements.ClickableArea;
import ae2.client.gui.elements.ColorArea;
import ae2.client.gui.elements.DrawableArea;
import ae2.container.implementations.ContainerTickAnalyser;
import ae2.items.tools.TickAnalyserConfig;
import ae2.core.network.serverbound.AnalyserGenericPacket;
import ae2.core.network.serverbound.TickConfigSavePacket;
import ae2.core.network.serverbound.TickProfilerRequestPacket;
import ae2.util.ColorData;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentTranslation;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.ArrayList;

public class GuiTickAnalyser extends AEBaseGui<ContainerTickAnalyser> {
    private static final ResourceLocation TEXTURE = AppEng.makeId("textures/guis/tick_analyser.png");
    private static final ColorData RED = new ColorData(1.0F, 0.0F, 0.0F);
    private static final ColorData GREEN = new ColorData(0.0F, 1.0F, 0.0F);

    private int duration = 60;
    private final boolean[] enabled = new boolean[4];
    private final ArrayList<ClickableArea> clickables = new ArrayList<>();
    private final ColorArea[] dots = new ColorArea[4];
    private GuiTextField durationInput;

    public GuiTickAnalyser(ContainerTickAnalyser container, InventoryPlayer playerInventory) {
        super(container, playerInventory);
        this.xSize = 207;
        this.ySize = 131;
        this.clickables.add(new ClickableArea(15, 98, 56, 19, this, () -> {
            syncDuration();
            InitNetwork.sendToServer(new TickProfilerRequestPacket(this.duration));
        }));
        this.clickables.add(new ClickableArea(136, 98, 56, 19, this,
            () -> InitNetwork.sendToServer(new TickProfilerRequestPacket(-1))));
        this.clickables.add(this.dots[0] = new ColorArea(83, 47, 4, 4, this, () -> cycleEnable(0)));
        this.clickables.add(this.dots[1] = new ColorArea(180, 47, 4, 4, this, () -> cycleEnable(1)));
        this.clickables.add(this.dots[2] = new ColorArea(83, 76, 4, 4, this, () -> cycleEnable(2)));
        this.clickables.add(this.dots[3] = new ColorArea(180, 76, 4, 4, this, () -> cycleEnable(3)));
        loadConfig(container.getConfig());
        InitNetwork.sendToServer(new AnalyserGenericPacket("update"));
    }

    @Override
    public void initGui() {
        super.initGui();
        this.durationInput = new GuiTextField(0, this.fontRenderer, this.guiLeft + 89, this.guiTop + 23, 32, 9);
        this.durationInput.setMaxStringLength(4);
        this.durationInput.setEnableBackgroundDrawing(false);
        this.durationInput.setTextColor(0xFFFFFF);
        this.durationInput.setText(String.valueOf(this.duration));
    }

    public void loadConfig(TickAnalyserConfig config) {
        this.duration = config.duration();
        this.enabled[0] = config.op1();
        this.enabled[1] = config.op2();
        this.enabled[2] = config.op3();
        this.enabled[3] = config.op4();
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
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        for (ClickableArea clickable : this.clickables) {
            if (clickable instanceof DrawableArea drawable) {
                drawable.draw();
            }
        }
        if (this.durationInput != null) {
            this.durationInput.drawTextBox();
        }
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY, float partialTicks) {
        bindTexture(TEXTURE);
        drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        int textColor = 0x404040;
        drawCentered("gui.ae2.tick_analyser.set_duration", 103, 11, textColor);
        drawCentered("gui.ae2.tick_analyser.begin", 42, 107, 0xFFFFFFFF);
        drawCentered("gui.ae2.tick_analyser.cancel", 163, 107, 0xFFFFFFFF);
        drawRight("gui.ae2.tick_analyser.range1", 80, 49, textColor);
        drawRight("gui.ae2.tick_analyser.range2", 177, 49, textColor);
        drawRight("gui.ae2.tick_analyser.range3", 80, 78, textColor);
        drawRight("gui.ae2.tick_analyser.range4", 177, 78, textColor);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (this.durationInput != null && this.durationInput.mouseClicked(mouseX, mouseY, mouseButton)) {
            return;
        }
        for (ClickableArea clickable : this.clickables) {
            if (clickable.click(mouseX, mouseY)) {
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (this.durationInput != null && isNumberInput(typedChar, keyCode)
            && this.durationInput.textboxKeyTyped(typedChar, keyCode)) {
            syncDuration();
            sendConfig();
            return;
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            syncDuration();
            sendConfig();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (this.durationInput != null) {
            this.durationInput.updateCursorCounter();
        }
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
            this.duration = Math.max(1, Integer.parseInt(this.durationInput.getText()));
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

    private void drawCentered(String key, int centerX, int centerY, int color) {
        String text = new TextComponentTranslation(key).getFormattedText();
        this.fontRenderer.drawString(text, centerX - this.fontRenderer.getStringWidth(text) / 2,
            centerY - this.fontRenderer.FONT_HEIGHT / 2, color);
    }

    private void drawRight(String key, int rightX, int y, int color) {
        String text = new TextComponentTranslation(key).getFormattedText();
        this.fontRenderer.drawString(text, rightX - this.fontRenderer.getStringWidth(text),
            y - this.fontRenderer.FONT_HEIGHT / 2, color);
    }
}
