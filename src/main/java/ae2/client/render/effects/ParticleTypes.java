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

package ae2.client.render.effects;

import ae2.core.AppEng;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public final class ParticleTypes {
    public static final ParticleType CRAFTING = new ParticleType(AppEng.makeId("particle/energy"), EnumParticleTypes.SPELL);
    public static final ParticleType ENERGY = new ParticleType(AppEng.makeId("particle/energy"), EnumParticleTypes.REDSTONE);
    public static final ParticleType LIGHTNING_ARC = new ParticleType(new ResourceLocation("minecraft", "blocks/glowstone"), EnumParticleTypes.CRIT_MAGIC);
    public static final ParticleType LIGHTNING = new ParticleType(new ResourceLocation("minecraft", "blocks/glowstone"), EnumParticleTypes.CRIT);
    public static final ParticleType MATTER_CANNON = new ParticleType(AppEng.makeId("particle/matter_cannon"), EnumParticleTypes.SMOKE_NORMAL);
    public static final ParticleType VIBRANT = new ParticleType(null, EnumParticleTypes.VILLAGER_HAPPY);

    private ParticleTypes() {
    }

    public static void clearCachedSprites() {
        CRAFTING.clearCachedSprite();
        ENERGY.clearCachedSprite();
        LIGHTNING_ARC.clearCachedSprite();
        LIGHTNING.clearCachedSprite();
        MATTER_CANNON.clearCachedSprite();
        VIBRANT.clearCachedSprite();
    }

    public static void registerTextures(TextureMap textureMap) {
        CRAFTING.registerTexture(textureMap);
        ENERGY.registerTexture(textureMap);
        LIGHTNING_ARC.registerTexture(textureMap);
        LIGHTNING.registerTexture(textureMap);
        MATTER_CANNON.registerTexture(textureMap);
        VIBRANT.registerTexture(textureMap);
    }

    public static final class ParticleType {
        private final @Nullable ResourceLocation spriteName;
        private final EnumParticleTypes fallbackType;
        private TextureAtlasSprite sprite;

        private ParticleType(@Nullable ResourceLocation spriteName, EnumParticleTypes fallbackType) {
            this.spriteName = spriteName;
            this.fallbackType = fallbackType;
        }

        public EnumParticleTypes vanillaType() {
            return this.fallbackType;
        }

        public void spawn(World world, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            spawn(world, x, y, z, xSpeed, ySpeed, zSpeed, null);
        }

        public void spawn(World world, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed,
                          @Nullable Object data) {
            if (world == null || !world.isRemote) {
                return;
            }

            var effect = createParticle(world, x, y, z, xSpeed, ySpeed, zSpeed, data);
            if (effect != null) {
                Minecraft.getMinecraft().effectRenderer.addEffect(effect);
                return;
            }

            world.spawnParticle(this.fallbackType, x, y, z, xSpeed, ySpeed, zSpeed);
        }

        @Nullable
        private Particle createParticle(World world, double x, double y, double z, double xSpeed, double ySpeed,
                                        double zSpeed, @Nullable Object data) {
            if (this == CRAFTING) {
                return new CraftingFx(world, x, y, z, xSpeed, ySpeed, zSpeed, getSprite());
            }
            if (this == ENERGY) {
                var energyData = data instanceof EnergyParticleData ? (EnergyParticleData) data
                    : EnergyParticleData.FOR_BLOCK;
                return new EnergyFx(world, x, y, z, xSpeed, ySpeed, zSpeed, getSprite(), energyData);
            }
            if (this == LIGHTNING) {
                return new LightningFX(world, x, y, z, xSpeed, ySpeed, zSpeed, getSprite());
            }
            if (this == LIGHTNING_ARC) {
                if (data instanceof LightningArcParticleData(double targetX, double targetY, double targetZ)) {
                    return new LightningArcFX(world, x, y, z, targetX, targetY,
                        targetZ, xSpeed, ySpeed, zSpeed, getSprite());
                }
                return new LightningArcFX(world, x, y, z, x + xSpeed, y + ySpeed, z + zSpeed, 1.0, 1.0, 1.0,
                    getSprite());
            }
            if (this == MATTER_CANNON) {
                return new MatterCannonFX(world, x, y, z, getSprite());
            }
            if (this == VIBRANT) {
                return new VibrantFX(world, x, y, z, xSpeed, ySpeed, zSpeed, getSprite());
            }
            return null;
        }

        private void clearCachedSprite() {
            this.sprite = null;
        }

        public void registerTexture(TextureMap textureMap) {
            if (this.spriteName != null) {
                textureMap.registerSprite(this.spriteName);
            }
        }

        @Nullable
        private TextureAtlasSprite getSprite() {
            if (this.spriteName == null) {
                return null;
            }

            if (this.sprite == null) {
                this.sprite = Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(this.spriteName.toString());
            }

            return this.sprite;
        }
    }
}
