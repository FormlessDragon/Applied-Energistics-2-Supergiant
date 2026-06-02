package ae2.client.gui.me.requester;

import ae2.api.util.AEColor;
import ae2.client.gui.widgets.ITooltip;
import ae2.tile.crafting.requester.RequestStatus;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

public final class StatusDisplay implements ITooltip {

    private static final int WIDTH = 102;
    private static final int HEIGHT = 2;
    private static final int OPAQUE = 0xFF000000;

    private final BooleanSupplier isInactive;

    private final Rectangle statusBox;
    private RequestStatus status = RequestStatus.IDLE;
    private boolean visible = true;

    StatusDisplay(int x, int y, BooleanSupplier isInactive) {
        this.statusBox = new Rectangle(x, y, WIDTH, HEIGHT);
        this.isInactive = isInactive;
    }

    private static int opaque(int rgb) {
        return OPAQUE | rgb;
    }

    private static void addStatusTooltipLine(List<ITextComponent> tooltip, RequestStatus status) {
        tooltip.add(statusText(status));
        tooltip.add(new TextComponentTranslation("gui.ae2.requester.status." + status.name().toLowerCase() + ".desc"));
        tooltip.add(new TextComponentString(" "));
    }

    private static ITextComponent statusText(RequestStatus status) {
        return new TextComponentTranslation("gui.ae2.requester.status." + status.name().toLowerCase())
            .setStyle(new Style().setColor(statusFormatting(status)));
    }

    private static TextFormatting statusFormatting(RequestStatus status) {
        return switch (status) {
            case IDLE, REQUESTING, PLANNING -> TextFormatting.DARK_GREEN;
            case MISSING, NO_PATTERN -> TextFormatting.RED;
            case CPU -> TextFormatting.GOLD;
            case CRAFTING -> TextFormatting.YELLOW;
            case EXPORTING -> TextFormatting.DARK_PURPLE;
        };
    }

    public void renderWidget() {
        if (!this.visible) {
            return;
        }

        Gui.drawRect(this.statusBox.x, this.statusBox.y,
            this.statusBox.x + this.statusBox.width, this.statusBox.y + this.statusBox.height, getStatusColor());
    }

    public void move(int x, int y) {
        this.statusBox.x = x;
        this.statusBox.y = y;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public boolean isMouseOver(int mouseX, int mouseY) {
        if (!this.visible) {
            return false;
        }

        int left = this.statusBox.x;
        int top = this.statusBox.y;
        return mouseX >= left && mouseY >= top && mouseX < left + this.statusBox.width
            && mouseY < top + this.statusBox.height;
    }

    private int getStatusColor() {
        if (this.isInactive.getAsBoolean()) {
            return opaque(AEColor.GRAY.mediumVariant);
        }

        return opaque(getStatusColor(this.status));
    }

    private int getStatusColor(RequestStatus requestStatus) {
        return switch (requestStatus) {
            case IDLE, REQUESTING, PLANNING -> AEColor.GREEN.mediumVariant;
            case MISSING, NO_PATTERN -> AEColor.RED.mediumVariant;
            case CPU -> AEColor.YELLOW.blackVariant;
            case CRAFTING -> AEColor.YELLOW.mediumVariant;
            case EXPORTING -> AEColor.PURPLE.blackVariant;
        };
    }

    void setStatus(RequestStatus status) {
        this.status = status == null ? RequestStatus.IDLE : status;
    }

    @Override
    public List<ITextComponent> getTooltipMessage() {
        List<ITextComponent> tooltip = new ArrayList<>();
        tooltip.add(new TextComponentTranslation("gui.ae2.requester.status"));
        if (GuiScreen.isShiftKeyDown()) {
            tooltip.add(new TextComponentString(" "));
            addStatusTooltipLine(tooltip, RequestStatus.IDLE);
            addStatusTooltipLine(tooltip, RequestStatus.MISSING);
            addStatusTooltipLine(tooltip, RequestStatus.NO_PATTERN);
            addStatusTooltipLine(tooltip, RequestStatus.CPU);
            addStatusTooltipLine(tooltip, RequestStatus.CRAFTING);
            addStatusTooltipLine(tooltip, RequestStatus.EXPORTING);
        } else {
            tooltip.add(statusText(getVisibleStatus()));
            tooltip.add(new TextComponentString(" "));
            tooltip.add(new TextComponentTranslation("gui.ae2.requester.status.shift").setStyle(
                new Style().setColor(TextFormatting.GRAY)));
        }
        return tooltip;
    }

    private RequestStatus getVisibleStatus() {
        return this.isInactive.getAsBoolean() ? RequestStatus.IDLE : this.status;
    }

    @Override
    public Rectangle getTooltipArea() {
        return this.statusBox;
    }

    @Override
    public boolean isTooltipAreaVisible() {
        return this.visible;
    }
}
