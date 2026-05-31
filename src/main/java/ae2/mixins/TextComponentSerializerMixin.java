package ae2.mixins;

import ae2.text.CustomTextComponents;
import ae2.text.ICustomTextComponent;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Type;
import java.util.Map;

@SuppressWarnings("NameDoesntMatchTargetClass")
@Mixin(ITextComponent.Serializer.class)
public class TextComponentSerializerMixin {

    @Unique
    private static String ae2_getRequiredString(JsonObject jsonObject) {
        JsonElement element = jsonObject.get("ae2_custom");
        if (element == null || !element.isJsonPrimitive()) {
            throw new JsonParseException("Expected string for key: " + "ae2_custom");
        }
        return element.getAsString();
    }

    @Unique
    private static JsonObject ae2_getRequiredObject(JsonObject jsonObject) {
        JsonElement element = jsonObject.get("data");
        if (element == null || !element.isJsonObject()) {
            throw new JsonParseException("Expected object for key: " + "data");
        }
        return element.getAsJsonObject();
    }

    @Inject(
        method = "deserialize(Lcom/google/gson/JsonElement;Ljava/lang/reflect/Type;Lcom/google/gson/JsonDeserializationContext;)Lnet/minecraft/util/text/ITextComponent;",
        at = @At("HEAD"),
        cancellable = true)
    private void ae2_deserializeCustom(JsonElement jsonElement, Type type,
                                       com.google.gson.JsonDeserializationContext context,
                                       CallbackInfoReturnable<ITextComponent> cir) {
        if (!jsonElement.isJsonObject()) {
            return;
        }

        JsonObject jsonObject = jsonElement.getAsJsonObject();
        if (!jsonObject.has("ae2_custom")) {
            return;
        }

        String typeId = ae2_getRequiredString(jsonObject);
        JsonObject data = ae2_getRequiredObject(jsonObject);

        ICustomTextComponent component;
        try {
            component = CustomTextComponents.decodeJson(typeId, data);
        } catch (IllegalArgumentException e) {
            throw new JsonParseException("Could not decode custom text component: " + typeId, e);
        }

        Style style = context.deserialize(jsonObject, Style.class);
        if (style != null) {
            component.setStyle(style);
        }

        if (jsonObject.has("extra")) {
            JsonArray extra = jsonObject.getAsJsonArray("extra");
            if (extra.size() <= 0) {
                throw new JsonParseException("Unexpected empty array of components");
            }

            for (JsonElement siblingElement : extra) {
                component.appendSibling(context.deserialize(siblingElement, ITextComponent.class));
            }
        }

        cir.setReturnValue(component);
    }

    @Inject(
        method = "serialize(Lnet/minecraft/util/text/ITextComponent;Ljava/lang/reflect/Type;Lcom/google/gson/JsonSerializationContext;)Lcom/google/gson/JsonElement;",
        at = @At("HEAD"),
        cancellable = true)
    private void ae2_serializeCustom(ITextComponent component, Type type,
                                     com.google.gson.JsonSerializationContext context,
                                     CallbackInfoReturnable<JsonElement> cir) {
        if (!(component instanceof ICustomTextComponent customTextComponent)) {
            return;
        }

        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("ae2_custom", customTextComponent.getTypeId());
        jsonObject.add("data", customTextComponent.writeJson());

        if (!component.getStyle().isEmpty()) {
            JsonElement styleElement = context.serialize(component.getStyle(), Style.class);
            if (styleElement != null && styleElement.isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : styleElement.getAsJsonObject().entrySet()) {
                    jsonObject.add(entry.getKey(), entry.getValue());
                }
            }
        }

        if (!component.getSiblings().isEmpty()) {
            JsonArray extra = new JsonArray();
            for (ITextComponent sibling : component.getSiblings()) {
                extra.add(context.serialize(sibling, ITextComponent.class));
            }
            jsonObject.add("extra", extra);
        }

        cir.setReturnValue(jsonObject);
    }
}
