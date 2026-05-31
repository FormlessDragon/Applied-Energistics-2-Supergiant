/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2021, TeamAppliedEnergistics, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package ae2.client.gui.style;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.util.JsonUtils;

import java.awt.Rectangle;
import java.lang.reflect.Type;

public enum Rectangle2dDeserializer implements JsonDeserializer<Rectangle> {
    INSTANCE;

    @Override
    public Rectangle deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
        throws JsonParseException {
        if (json.isJsonArray()) {
            JsonArray arr = json.getAsJsonArray();
            if (arr.size() != 4) {
                throw new JsonParseException("Rectangles expressed as arrays must have 4 elements.");
            }

            int x = arr.get(0).getAsInt();
            int y = arr.get(1).getAsInt();
            int width = arr.get(2).getAsInt();
            int height = arr.get(3).getAsInt();
            return new Rectangle(x, y, width, height);
        } else {
            JsonObject obj = json.getAsJsonObject();
            int x = JsonUtils.getInt(obj, "x", 0);
            int y = JsonUtils.getInt(obj, "y", 0);
            int width = JsonUtils.getInt(obj, "width");
            int height = JsonUtils.getInt(obj, "height");
            return new Rectangle(x, y, width, height);
        }
    }
}

