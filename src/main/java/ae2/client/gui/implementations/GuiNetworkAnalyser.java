package ae2.client.gui.implementations;

import ae2.client.gui.AEBaseGui;
import ae2.client.gui.style.Blitter;
import ae2.core.AEConfig;
import ae2.core.AppEng;
import ae2.core.network.InitNetwork;
import ae2.client.gui.elements.ClickableArea;
import ae2.client.gui.elements.ColorArea;
import ae2.client.gui.elements.DraggableArea;
import ae2.client.gui.elements.DrawableArea;
import ae2.client.render.NetworkDataHandler;
import ae2.container.implementations.ContainerNetworkAnalyser;
import ae2.items.tools.NetworkAnalyserConfig;
import ae2.me.AnalyserMode;
import ae2.me.netdata.LinkFlag;
import ae2.me.netdata.NodeFlag;
import ae2.core.network.serverbound.AnalyserGenericPacket;
import ae2.core.network.serverbound.NetworkConfigSavePacket;
import ae2.util.ColorData;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GuiNetworkAnalyser extends AEBaseGui<ContainerNetworkAnalyser> {
    private static final ResourceLocation TEXTURE = AppEng.makeId("textures/guis/network_analyser.png");
    private static final ResourceLocation COLOR_SUB_MEN_TEXTURE = AppEng.makeId("textures/guis/color_configer.png");
    public static final Blitter COLOR_SUB_MENU = Blitter.texture(COLOR_SUB_MEN_TEXTURE, 200, 200).src(0, 0, 110, 95);

    private static final List<Enum<?>> COLOR_ORDER = List.of(
        LinkFlag.NORMAL, LinkFlag.DENSE, LinkFlag.COMPRESSED,
        NodeFlag.NORMAL, NodeFlag.DENSE, NodeFlag.MISSING
    );

    private final Reference2ObjectMap<Enum<?>, ColorData> colors = new Reference2ObjectOpenHashMap<>();
    private final Reference2ObjectMap<Enum<?>, ColorArea> colorBtns = new Reference2ObjectOpenHashMap<>();
    private final ArrayList<ClickableArea> clickables = new ArrayList<>();
    private final ColorWindow colorWindow;
    private final DraggableArea colorRed;
    private final DraggableArea colorGreen;
    private final DraggableArea colorBlue;
    private final DraggableArea colorAlpha;
    private final ColorArea colorShow;

    private AnalyserMode mode = AnalyserMode.FULL;
    private float size = 0.4F;

    public GuiNetworkAnalyser(ContainerNetworkAnalyser container, InventoryPlayer playerInventory) {
        super(container, playerInventory);
        this.xSize = 251;
        this.ySize = 168;
        this.colors.putAll(NetworkAnalyserConfig.DEFAULT_COLORS);

        this.clickables.add(new ClickableArea(39, 21, 6, 11, this, () -> changeMode(-1)));
        this.clickables.add(new ClickableArea(107, 21, 6, 11, this, () -> changeMode(1)));
        this.clickables.add(new ClickableArea(39, 49, 6, 11, this, () -> changeSize(-0.1F)));
        this.clickables.add(new ClickableArea(107, 49, 6, 11, this, () -> changeSize(0.1F)));
        this.clickables.add(new ClickableArea(146, 142, 65, 14, this, this::loadDefault));
        for (int i = 0; i < COLOR_ORDER.size(); i++) {
            Enum<?> type = COLOR_ORDER.get(i);
            ColorArea button = new ColorArea(198, 22 + i * 21, 25, 9, this, () -> beginColorConfig(type));
            this.clickables.add(button);
            this.colorBtns.put(type, button);
        }

        this.colorWindow = new ColorWindow(73, 48, 110, 95, this);
        this.colorWindow.addElement(this.colorRed = new DraggableArea(81, 57, 90, 7, this));
        this.colorWindow.addElement(this.colorGreen = new DraggableArea(81, 72, 90, 7, this));
        this.colorWindow.addElement(this.colorBlue = new DraggableArea(81, 87, 90, 7, this));
        this.colorWindow.addElement(this.colorAlpha = new DraggableArea(81, 102, 90, 7, this));
        this.colorWindow.addElement(this.colorShow = new ColorArea(114, 121, 27, 7, this, () -> {
        }));
        this.colorWindow.addElement(new ClickableArea(90, 118, 13, 13, this, () -> closeColorConfig(true)));
        this.colorWindow.addElement(new ClickableArea(152, 118, 13, 13, this, () -> closeColorConfig(false)));

        loadConfig(container.getConfig());
        InitNetwork.sendToServer(new AnalyserGenericPacket("update"));
    }

    public void loadConfig(NetworkAnalyserConfig config) {
        this.mode = config.mode();
        this.size = config.nodeSize();
        this.colors.clear();
        this.colors.putAll(NetworkAnalyserConfig.DEFAULT_COLORS);
        this.colors.putAll(config.colors());
        for (var entry : this.colors.entrySet()) {
            ColorArea button = this.colorBtns.get(entry.getKey());
            if (button != null) {
                button.setColor(entry.getValue());
            }
        }
        NetworkDataHandler.updateConfig(new NetworkAnalyserConfig(this.mode, this.size, this.colors));
    }

    public void loadDefault() {
        this.colors.clear();
        this.colors.putAll(NetworkAnalyserConfig.DEFAULT_COLORS);
        for (var entry : this.colors.entrySet()) {
            ColorArea button = this.colorBtns.get(entry.getKey());
            if (button != null) {
                button.setColor(entry.getValue());
            }
        }
        syncConfig();
    }

    public void closeColorConfig(boolean save) {
        if (save) {
            ColorArea colorBtn = this.colorBtns.get(this.colorWindow.configType);
            ColorData newData = this.colorShow.getColor();
            colorBtn.setColor(newData);
            this.colors.put(this.colorWindow.configType, newData);
            syncConfig();
        }
        this.colorWindow.isOn = false;
    }

    private void beginColorConfig(Enum<?> type) {
        this.colorWindow.isOn = true;
        this.colorWindow.configType = type;
        ColorData color = this.colorBtns.get(type).getColor();
        this.colorRed.setValue(color.red());
        this.colorGreen.setValue(color.green());
        this.colorBlue.setValue(color.blue());
        this.colorAlpha.setValue(color.alpha());
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY, float partialTicks) {
        bindTexture(TEXTURE);
        drawTexturedModalRect(offsetX, offsetY, 0, 0, xSize, ySize);
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        int textColor = 0x404040;
        drawCentered(this.mode.getTranslatedName().getFormattedText(), 76, 27, 0xFFFFFFFF);
        drawCentered(new TextComponentTranslation("gui.ae2.network_analyser.mode").getFormattedText(), 24, 26, textColor);
        drawCentered(String.valueOf((int) (this.size * 10)), 76, 55, 0xFFFFFFFF);
        drawCentered(new TextComponentTranslation("gui.ae2.network_analyser.node_size").getFormattedText(), 24, 54, textColor);
        drawCentered(new TextComponentTranslation("gui.ae2.network_analyser.reset").getFormattedText(), 179, 149, 0xFFFFFFFF);
        for (int i = 0; i < COLOR_ORDER.size(); i++) {
            Enum<?> entry = COLOR_ORDER.get(i);
            String type = entry instanceof NodeFlag ? "NODE" : "LINK";
            fontRenderer.drawString(new TextComponentTranslation(
                "gui.ae2.network_analyser." + type + "." + entry.name()).getFormattedText(),
                134, 23 + 21 * i, textColor);
        }
        fontRenderer.drawString(new TextComponentTranslation(
            "gui.ae2.network_analyser.channel." + AEConfig.instance().getChannelMode().name()).getFormattedText(), 16, 72, textColor);
        fontRenderer.drawString(new TextComponentTranslation("gui.ae2.network_analyser.state.normal_nodes",
            NetworkDataHandler.pullData().countNode(NodeFlag.NORMAL)).getFormattedText(), 16, 86, 0x32A843);
        fontRenderer.drawString(new TextComponentTranslation("gui.ae2.network_analyser.state.dense_nodes",
            NetworkDataHandler.pullData().countNode(NodeFlag.DENSE)).getFormattedText(), 16, 100, 0x19B3A3);
        fontRenderer.drawString(new TextComponentTranslation("gui.ae2.network_analyser.state.missing_nodes",
            NetworkDataHandler.pullData().countNode(NodeFlag.MISSING)).getFormattedText(), 16, 114, 0xAA1A1A);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        GlStateManager.disableDepth();
        for (ClickableArea clickable : this.clickables) {
            if (clickable instanceof DrawableArea drawable) {
                drawable.draw();
            }
        }
        if (this.colorWindow.isOn) {
            this.colorShow.setColor(new ColorData(this.colorAlpha.getValue(), this.colorRed.getValue(),
                this.colorGreen.getValue(), this.colorBlue.getValue()));
            this.colorWindow.draw();
        }
        GlStateManager.enableDepth();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (this.colorWindow.isOn) {
            if (this.colorWindow.click(mouseX, mouseY)) {
                return;
            }
        } else {
            for (ClickableArea clickable : this.clickables) {
                if (clickable.click(mouseX, mouseY)) {
                    return;
                }
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        if (this.colorWindow.isOn) {
            this.colorWindow.release(mouseX, mouseY);
            return;
        }
        this.clickables.forEach(clickable -> clickable.release(mouseX, mouseY));
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (this.colorWindow.isOn && Mouse.isButtonDown(clickedMouseButton)) {
            this.colorWindow.drag(mouseX, mouseY);
            return;
        }
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    @Override
    public void onGuiClosed() {
        syncConfig();
        super.onGuiClosed();
    }

    private void drawCentered(String text, int centerX, int centerY, int color) {
        this.drawCentered(new TextComponentString(text), centerX, centerY, color);
    }

    private void drawCentered(TextComponentString text, int centerX, int centerY, int color) {
        String value = text.getFormattedText();
        fontRenderer.drawString(value, centerX - fontRenderer.getStringWidth(value) / 2, centerY - fontRenderer.FONT_HEIGHT / 2, color);
    }

    private void changeMode(int offset) {
        this.mode = AnalyserMode.byIndex(this.mode.ordinal() + offset);
        syncConfig();
    }

    private void changeSize(float offset) {
        this.size = Math.clamp(this.size + offset, 0.1F, 0.9F);
        syncConfig();
    }

    private void syncConfig() {
        NetworkAnalyserConfig config = new NetworkAnalyserConfig(this.mode, this.size,
            new Reference2ObjectOpenHashMap<>(this.colors));
        NetworkDataHandler.updateConfig(config);
        getContainer().saveConfig(config);
        InitNetwork.sendToServer(new NetworkConfigSavePacket(config));
    }

    private static final class ColorWindow extends ClickableArea {
        private final List<ClickableArea> elements = new ArrayList<>();
        private Enum<?> configType;
        private boolean isOn;

        private ColorWindow(int x, int y, int width, int height, AEBaseGui<?> parent) {
            super(x, y, width, height, parent, () -> {
            });
        }

        private void draw() {
            if (this.isOn) {
                COLOR_SUB_MENU.copy().dest(this.x + this.screen.getGuiLeft(), this.y + this.screen.getGuiTop()).blit();
                for (ClickableArea element : this.elements) {
                    if (element instanceof DrawableArea drawable) {
                        drawable.draw();
                    }
                }
            }
        }

        private void addElement(ClickableArea area) {
            this.elements.add(area);
        }

        @Override
        public boolean click(double x, double y) {
            if (isMouseOver(x, y)) {
                for (ClickableArea element : this.elements) {
                    if (element.click(x, y)) {
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public void release(double x, double y) {
            for (ClickableArea element : this.elements) {
                element.release(x, y);
            }
        }

        private void drag(double x, double y) {
            for (ClickableArea element : this.elements) {
                if (element instanceof DraggableArea draggable) {
                    draggable.drag(x, y);
                }
            }
        }
    }
}
