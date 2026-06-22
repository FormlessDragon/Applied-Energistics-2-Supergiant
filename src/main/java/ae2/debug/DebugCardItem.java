/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
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

package ae2.debug;

import ae2.api.networking.GridHelper;
import ae2.api.networking.IGridNode;
import ae2.api.networking.energy.IAEPowerStorage;
import ae2.api.networking.energy.IEnergyService;
import ae2.api.networking.pathing.ControllerState;
import ae2.api.parts.IPart;
import ae2.api.parts.IPartHost;
import ae2.core.localization.GuiText;
import ae2.core.localization.PlayerMessages;
import ae2.hooks.ticking.TickHandler;
import ae2.items.AEBaseItem;
import ae2.me.Grid;
import ae2.me.GridNode;
import ae2.me.helpers.IGridConnectedTile;
import ae2.me.service.TickManagerService;
import ae2.parts.networking.CablePart;
import ae2.parts.p2p.P2PTunnelPart;
import ae2.tile.AEBaseTile;
import ae2.tile.networking.TileController;
import ae2.util.InteractionUtil;
import ae2.util.Platform;
import com.google.common.collect.Iterables;
import com.google.common.math.StatsAccumulator;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import java.util.Set;

public class DebugCardItem extends AEBaseItem {

    public DebugCardItem() {
        this.setMaxStackSize(1);
    }

    private static ITextComponent style(ITextComponent component, TextFormatting... formattings) {
        Style style = new Style();
        for (var formatting : formattings) {
            if (formatting.isColor()) {
                style.setColor(formatting);
            } else if (formatting == TextFormatting.BOLD) {
                style.setBold(true);
            } else if (formatting == TextFormatting.ITALIC) {
                style.setItalic(true);
            } else if (formatting == TextFormatting.UNDERLINE) {
                style.setUnderlined(true);
            } else if (formatting == TextFormatting.STRIKETHROUGH) {
                style.setStrikethrough(true);
            } else if (formatting == TextFormatting.OBFUSCATED) {
                style.setObfuscated(true);
            }
        }
        component.setStyle(style);
        return component;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        if (InteractionUtil.isInAlternateUseMode(player) && !world.isRemote) {
            int grids = 0;

            var stats = new StatsAccumulator();
            for (var grid : TickHandler.instance().getGridList()) {
                if (grid instanceof Grid) {
                    grids++;
                    stats.add(grid.size());
                }
            }

            divider(player);
            outputMessage(player, PlayerMessages.DebugCardGrids.text(), TextFormatting.BOLD);
            this.outputSecondaryMessage(player, PlayerMessages.DebugCardGrids.text(), Integer.toString(grids));
            if (stats.count() > 0) {
                this.outputSecondaryMessage(player, PlayerMessages.DebugCardTotalNodes.text(), "" + (long) stats.sum());
                this.outputSecondaryMessage(player, PlayerMessages.DebugCardMeanNodes.text(), "" + (long) stats.mean());
                this.outputSecondaryMessage(player, PlayerMessages.DebugCardMaxNodes.text(), "" + (long) stats.max());
            }
            divider(player);
            outputMessage(player, PlayerMessages.DebugCardTicking.text(), TextFormatting.BOLD);
            this.outputSecondaryMessage(player, PlayerMessages.DebugCardCurrentTick.text(),
                Long.toString(TickHandler.instance().getCurrentTick()));
            for (var line : TickHandler.instance().getBlockEntityReport()) {
                player.sendMessage(line);
            }
        }

        return new ActionResult<>(EnumActionResult.SUCCESS, player.getHeldItem(hand));
    }

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side, float hitX,
                                           float hitY, float hitZ, EnumHand hand) {
        if (world.isRemote) {
            return EnumActionResult.PASS;
        }

        if (player == null || InteractionUtil.isInAlternateUseMode(player)) {
            return EnumActionResult.PASS;
        }

        var gh = GridHelper.getNodeHost(world, pos);
        if (gh != null) {
            divider(player);
            var node = (GridNode) gh.getGridNode(side);
            if (node == null) {
                if (gh instanceof IGridConnectedTile gridConnectedTile) {
                    node = (GridNode) gridConnectedTile.getMainNode().getNode();
                    this.outputMessage(player, PlayerMessages.DebugCardMainNodeOfGridConnectedTile.text());
                }
            }
            if (node != null) {
                this.outputMessage(player, PlayerMessages.DebugCardGridDetails.text());
                final Grid g = node.getInternalGrid();
                final IGridNode center = g.getPivot();
                this.outputPrimaryMessage(player, PlayerMessages.DebugCardGridPowered.text(),
                    localizedBoolean(g.getEnergyService().isNetworkPowered()));
                this.outputPrimaryMessage(player, PlayerMessages.DebugCardGridBooted.text(),
                    localizedBoolean(!g.getPathingService().isNetworkBooting()));
                this.outputPrimaryMessage(player, PlayerMessages.DebugCardNodesInGrid.text(),
                    String.valueOf(Iterables.size(g.getNodes())));
                this.outputSecondaryMessage(player, PlayerMessages.DebugCardGridPivotNode.text(), String.valueOf(center));

                var tmc = (TickManagerService) g.getTickManager();
                for (var c : g.getMachineClasses()) {
                    int o = 0;
                    long totalAverageTime = 0;
                    long singleMaximumTime = 0;

                    for (var oj : g.getMachineNodes(c)) {
                        o++;
                        totalAverageTime += tmc.getAverageTime(oj);
                        singleMaximumTime = Math.max(singleMaximumTime, tmc.getMaximumTime(oj));
                    }

                    ITextComponent message = PlayerMessages.DebugCardMachineCount.text(o);
                    if (totalAverageTime > 0 || singleMaximumTime > 0) {
                        message.appendText(" ")
                               .appendSibling(PlayerMessages.DebugCardTimingSummary.text(
                                   Platform.formatTimeMeasurement(totalAverageTime),
                                   Platform.formatTimeMeasurement(singleMaximumTime)));
                    }

                    this.outputSecondaryMessage(player, c.getSimpleName(), message);
                }

                this.outputMessage(player, PlayerMessages.DebugCardNodeDetails.text());

                this.outputPrimaryMessage(player, PlayerMessages.DebugCardThisNode.text(), String.valueOf(node));
                this.outputPrimaryMessage(player, PlayerMessages.DebugCardThisNodeActive.text(), localizedBoolean(node.isActive()));
                this.outputSecondaryMessage(player, PlayerMessages.DebugCardNodeExposedOnSide.text(), side.getName());

                var pg = g.getPathingService();
                if (pg.getControllerState() == ControllerState.CONTROLLER_ONLINE) {

                    Set<IGridNode> next = new ReferenceOpenHashSet<>();
                    next.add(node);

                    final int maxLength = 10000;

                    int length = 0;
                    outer:
                    while (!next.isEmpty()) {
                        final Iterable<IGridNode> current = next;
                        next = new ReferenceOpenHashSet<>();

                        for (IGridNode n : current) {
                            if (n.getOwner() instanceof TileController) {
                                break outer;
                            }

                            for (var c : n.getConnections()) {
                                next.add(c.getOtherSide(n));
                            }
                        }

                        length++;

                        if (length > maxLength) {
                            break;
                        }
                    }

                    this.outputSecondaryMessage(player, PlayerMessages.DebugCardCableDistance.text(), Integer.toString(length));
                }

                if (center.getOwner() instanceof P2PTunnelPart<?> tunnelPart) {
                    this.outputSecondaryMessage(player, PlayerMessages.DebugCardFrequency.text(), Integer.toString(tunnelPart.getFrequency()));
                }
            } else {
                this.outputMessage(player, PlayerMessages.DebugCardNoNodeAvailable.text());
            }
        } else {
            this.outputMessage(player, PlayerMessages.DebugCardNotNetworkedBlock.text());
        }

        TileEntity te = world.getTileEntity(pos);
        if (te instanceof IPartHost partHost) {
            this.outputMessage(player, PlayerMessages.DebugCardCableBusDetails.text());
            outputSecondaryMessage(player, PlayerMessages.DebugCardInWorld.text(),
                localizedBoolean(partHost.isInWorld()));
            outputSecondaryMessage(player, PlayerMessages.DebugCardHasRedstone.text(),
                localizedBoolean(partHost.hasRedstone()));
            final IPart center = partHost.getPart(null);
            partHost.markForUpdate();
            if (center != null) {
                final GridNode n = (GridNode) center.getGridNode();
                if (n != null) {
                    this.outputSecondaryMessage(player, PlayerMessages.DebugCardNodeChannels.text(),
                        Integer.toString(n.getUsedChannels()));
                    for (var entry : n.getInWorldConnections().entrySet()) {
                        this.outputSecondaryMessage(player, PlayerMessages.DebugCardChannels.text(entry.getKey().getName()),
                            Integer.toString(entry.getValue().getUsedChannels()));
                    }
                }
            }
            if (center instanceof CablePart cablePart) {
                var msg = new TextComponentString("");
                for (var v : EnumFacing.VALUES) {
                    msg.appendSibling(
                        style(new TextComponentString(v.name().substring(0, 1)),
                            cablePart.isConnected(v) ? TextFormatting.GREEN : TextFormatting.DARK_GRAY));
                }
                player.sendMessage(style(PlayerMessages.DebugConnectedSides.text(), TextFormatting.GRAY)
                    .appendSibling(msg));
            }
        }

        if (te instanceof IAEPowerStorage ps) {
            this.outputMessage(player, PlayerMessages.DebugCardEnergyStorageDetails.text());
            this.outputSecondaryMessage(player, PlayerMessages.DebugCardEnergy.text(),
                ps.getAECurrentPower() + " / " + ps.getAEMaxPower());

            if (gh != null) {
                final IGridNode node = gh.getGridNode(side);
                if (node != null) {
                    final IEnergyService eg = node.grid().getEnergyService();
                    this.outputSecondaryMessage(player, PlayerMessages.DebugCardGridEnergy.text(),
                        eg.getStoredPower() + " : " + eg.getEnergyDemand(Double.MAX_VALUE));
                }
            }
        }

        if (te instanceof AEBaseTile be) {
            this.outputMessage(player, PlayerMessages.DebugCardDelayedInitDetails.text());
            outputSecondaryMessage(player, PlayerMessages.DebugCardQueuedForReady.text(), "" + be.getQueuedForReady());
            outputSecondaryMessage(player, PlayerMessages.DebugCardReadyInvoked.text(), "" + be.getReadyInvoked());
        }

        return EnumActionResult.SUCCESS;
    }

    private void divider(EntityPlayer player) {
        this.outputMessage(player, PlayerMessages.DebugCardDivider.text(), TextFormatting.BOLD,
            TextFormatting.DARK_PURPLE);
    }

    private void outputMessage(Entity player, ITextComponent text, TextFormatting... chatFormattings) {
        player.sendMessage(style(text, chatFormattings));
    }

    private void outputMessage(Entity player, ITextComponent text) {
        player.sendMessage(text);
    }

    private void outputPrimaryMessage(Entity player, ITextComponent label, String value) {
        this.outputLabeledMessage(player, label, value, TextFormatting.BOLD, TextFormatting.LIGHT_PURPLE);
    }

    private void outputPrimaryMessage(Entity player, ITextComponent label, ITextComponent value) {
        this.outputLabeledMessage(player, label, value, TextFormatting.BOLD, TextFormatting.LIGHT_PURPLE);
    }

    private void outputSecondaryMessage(Entity player, ITextComponent label, String value) {
        this.outputLabeledMessage(player, label, value, TextFormatting.GRAY);
    }

    private void outputSecondaryMessage(Entity player, ITextComponent label, ITextComponent value) {
        this.outputLabeledMessage(player, label, value, TextFormatting.GRAY);
    }

    private void outputSecondaryMessage(Entity player, String label, ITextComponent value) {
        this.outputSecondaryMessage(player, new TextComponentString(label), value);
    }

    private void outputLabeledMessage(Entity player, ITextComponent label, String value,
                                      TextFormatting... chatFormattings) {
        player.sendMessage(new TextComponentString("")
            .appendSibling(style(label.createCopy().appendText(": "), chatFormattings))
            .appendText(value));
    }

    private void outputLabeledMessage(Entity player, ITextComponent label, ITextComponent value,
                                      TextFormatting... chatFormattings) {
        player.sendMessage(new TextComponentString("")
            .appendSibling(style(label.createCopy().appendText(": "), chatFormattings))
            .appendSibling(value.createCopy()));
    }

    private ITextComponent localizedBoolean(boolean value) {
        return PlayerMessages.DebugCardBoolean.text(value ? GuiText.Yes.text() : GuiText.No.text());
    }
}
