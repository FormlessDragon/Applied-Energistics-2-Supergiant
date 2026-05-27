package appeng.text;

import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;

public class TextComponentItemStack implements ICustomTextComponent {
    public static final String TYPE_ID = "item_stack";

    static {
        CustomTextComponents.register(TYPE_ID, TextComponentItemStack::fromJson, TextComponentItemStack::fromPacket);
    }

    private final ItemStack itemStack;
    private final ObjectList<ITextComponent> siblings = new ObjectArrayList<>();
    private Style style;
    private ITextComponent cache;

    public TextComponentItemStack() {
        this(ItemStack.EMPTY);
    }

    private TextComponentItemStack(ItemStack itemStack) {
        this.itemStack = itemStack.copy();
        this.style = resolve().getStyle().createDeepCopy();
    }

    private TextComponentItemStack(ItemStack itemStack, @Nullable ITextComponent decorations) {
        this(itemStack);
        if (decorations != null) {
            this.setStyle(decorations.getStyle().createDeepCopy());
            for (ITextComponent sibling : decorations.getSiblings()) {
                this.appendSibling(sibling.createCopy());
            }
        }
    }

    public static void bootstrap() {
    }

    public static TextComponentItemStack of(ItemStack itemStack) {
        return new TextComponentItemStack(itemStack);
    }

    public static TextComponentItemStack fromJson(JsonObject data) {
        try {
            String serializedStack = JsonUtils.getString(data, "stack");
            NBTTagCompound stackTag = JsonToNBT.getTagFromJson(serializedStack);
            return new TextComponentItemStack(new ItemStack(stackTag));
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not read item stack text component json", e);
        }
    }

    public static TextComponentItemStack fromPacket(PacketBuffer buffer) {
        try {
            return new TextComponentItemStack(buffer.readItemStack(), TextComponents.readFromPacket(buffer));
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not read item stack text component packet", e);
        }
    }

    @Override
    public String getTypeId() {
        return TYPE_ID;
    }

    @Override
    public JsonObject writeJson() {
        JsonObject data = new JsonObject();
        data.addProperty("stack", this.itemStack.writeToNBT(new NBTTagCompound()).toString());
        return data;
    }

    @Override
    public void writeToPacket(PacketBuffer buffer) {
        buffer.writeItemStack(this.itemStack);
        TextComponents.writeToPacket(buffer, createDecorations());
    }

    @Override
    public ITextComponent setStyle(Style style) {
        this.style = style == null ? new Style() : style;
        for (ITextComponent sibling : this.siblings) {
            sibling.getStyle().setParentStyle(this.style);
        }
        return this;
    }

    @Override
    public Style getStyle() {
        return this.style;
    }

    @Override
    public ITextComponent appendText(String text) {
        return this.appendSibling(new TextComponentString(text));
    }

    @Override
    public ITextComponent appendSibling(ITextComponent component) {
        component.getStyle().setParentStyle(this.style);
        this.siblings.add(component);
        return this;
    }

    @Override
    public String getUnformattedComponentText() {
        return resolve().getUnformattedComponentText();
    }

    @Override
    public String getUnformattedText() {
        return resolve().getUnformattedText();
    }

    @Override
    public String getFormattedText() {
        return resolve().getFormattedText();
    }

    @Override
    public List<ITextComponent> getSiblings() {
        return this.siblings;
    }

    @Override
    public ITextComponent createCopy() {
        return new TextComponentItemStack(this.itemStack, createDecorations());
    }

    @Override
    public @NonNull Iterator<ITextComponent> iterator() {
        return resolve().iterator();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof TextComponentItemStack other)) {
            return false;
        }
        return ItemStack.areItemStacksEqual(this.itemStack, other.itemStack)
            && Objects.equals(this.style, other.style)
            && Objects.equals(this.siblings, other.siblings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            this.itemStack.writeToNBT(new NBTTagCompound()).toString(),
            this.style,
            this.siblings);
    }

    private ITextComponent createDecorations() {
        TextComponentString decorations = new TextComponentString("");
        decorations.setStyle(this.style.createDeepCopy());
        for (ITextComponent sibling : this.siblings) {
            decorations.appendSibling(sibling.createCopy());
        }
        return decorations;
    }

    private ITextComponent resolve() {
        if (cache == null) {
            cache = itemStack.getTextComponent();
        }
        return cache;
    }
}
