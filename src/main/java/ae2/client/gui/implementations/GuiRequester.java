package ae2.client.gui.implementations;

import ae2.client.gui.me.requester.AbstractGuiRequester;
import ae2.client.gui.me.requester.ClientRequester;
import ae2.client.gui.style.GuiStyle;
import ae2.container.implementations.ContainerRequester;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;
import org.jetbrains.annotations.Nullable;

import java.awt.Rectangle;
import java.util.Collection;
import java.util.Collections;

public class GuiRequester extends AbstractGuiRequester<ContainerRequester> {
    private static final Rectangle FOOTER_BBOX = new Rectangle(0, 114, GUI_WIDTH, GUI_FOOTER_HEIGHT + 2);
    private static final int MAX_ROW_COUNT = 5;

    private long requesterId;
    private @Nullable ClientRequester requesterReference;

    public GuiRequester(ContainerRequester container, InventoryPlayer playerInventory, ITextComponent title,
                        GuiStyle style) {
        super(container, playerInventory, title, style, requesterTexture("requester"));
        this.rowAmount = MAX_ROW_COUNT;
    }

    @Override
    protected void afterFullUpdate(long requesterId, @Nullable ITextComponent requesterName, long sortValue,
                                   int requestCount) {
        this.requesterId = requesterId;
        this.rowAmount = Math.clamp(requestCount, MIN_ROW_COUNT, MAX_ROW_COUNT);
        if (requesterName != null) {
            setTextContent(TEXT_ID_DIALOG_TITLE, requesterName);
        }
    }

    @Override
    protected void refreshList() {
        this.lines.clear();

        ClientRequester requester = findById(this.requesterId);
        if (requester != null) {
            for (int i = 0; i < requester.getRequests().size(); i++) {
                this.lines.add(requester.getRequests().get(i));
            }
        }

        refreshList = false;
        resetScrollbar();
    }

    @Override
    protected Collection<?> getByName(String name) {
        ClientRequester requester = findById(this.requesterId);
        return requester == null ? Collections.emptyList() : Collections.singleton(requester);
    }

    @Override
    protected ClientRequester getById(long requesterId, @Nullable ITextComponent name, long sortValue,
                                         int requestCount) {
        if (this.requesterReference == null || this.requesterReference.getRequests().size() != requestCount) {
            this.requesterReference = new ClientRequester(requesterId, name, sortValue, requestCount);
        } else {
            this.requesterReference.update(name, sortValue);
        }
        return this.requesterReference;
    }

    @Nullable
    @Override
    protected ClientRequester findById(long requesterId) {
        return this.requesterReference != null && this.requesterReference.getRequesterId() == requesterId
            ? this.requesterReference
            : null;
    }

    @Override
    protected Rectangle getFooterBounds() {
        return FOOTER_BBOX;
    }
}
