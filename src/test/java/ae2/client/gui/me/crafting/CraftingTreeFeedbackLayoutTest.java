package ae2.client.gui.me.crafting;

import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.GenericStack;
import ae2.client.Point;
import ae2.integration.data.LiteCraftTreeNode;
import ae2.integration.data.LiteCraftTreeProc;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CraftingTreeFeedbackLayoutTest {
    @Test
    void crossingReturnsKeepStableColorsAndSeparateParallelSegments() throws Exception {
        Bootstrap.register();
        var keys = List.of(Items.DIAMOND, Items.IRON_INGOT, Items.GOLD_INGOT, Items.APPLE).stream()
            .map(AEItemKey::of).toList();
        var children = new ArrayList<LiteCraftTreeNode>();
        var rootProcess = new LiteCraftTreeProc(children, List.of(), Map.of(), null, 0, 0);
        var root = new LiteCraftTreeNode(null, new GenericStack(keys.getFirst(), 1_000_000_000),
            new ArrayList<>(List.of(rootProcess)), 0);
        for (int id = 1; id < keys.size(); id++) {
            var returns = new ArrayList<LiteCraftTreeNode>();
            var process = new LiteCraftTreeProc(returns, List.of(), Map.of(), null, 0, id);
            for (int source = 0; source < keys.size(); source++) {
                if (source != id) returns.add(new LiteCraftTreeNode(process, new GenericStack(keys.get(source), 47_000_000),
                    new ArrayList<>(), 0, LiteCraftTreeNode.sourceFeedback(source)));
            }
            children.add(new LiteCraftTreeNode(rootProcess, new GenericStack(keys.get(id), 190_000_000),
                new ArrayList<>(List.of(process)), 0));
        }
        var widget = new CraftingTreeWidget();
        widget.setSize(2000, 2000);
        widget.setRoot(root);
        var regions = CraftingTreeCycleView.contract(root);
        var open = CraftingTreeWidget.class.getDeclaredMethod("openRing", CraftingTreeCycleView.Region.class);
        open.setAccessible(true);
        open.invoke(widget, regions.cycles().values().iterator().next());

        var lines = widget.layoutFeedbackLines();
        assertEquals(9, lines.size(), "every return to a shared producer has a route");
        assertTrue(lines.stream().map(CraftingTreeWidget.FeedbackLine::color).distinct().count() >= 6);
        assertEquals(lines, widget.layoutFeedbackLines(), "colors and routes must not flicker between frames");
        var shifted = widget.layoutFeedbackLines(11, -8);
        for (int i = 0; i < lines.size(); i++) {
            assertEquals(lines.get(i).color(), shifted.get(i).color());
            assertEquals(lines.get(i).points().stream().map(point -> point.move(11, -8)).toList(), shifted.get(i).points(),
                "screenshot tiles must use the same routes as the screen");
        }
        for (int i = 0; i < lines.size(); i++) {
            for (int j = i + 1; j < lines.size(); j++) {
                assertFalse(overlap(lines.get(i).points(), lines.get(j).points()),
                    "parallel returns must not obscure each other: " + i + ", " + j);
            }
        }
    }

    private static boolean overlap(List<Point> first, List<Point> second) {
        for (int i = 1; i < first.size(); i++) {
            var a = first.get(i - 1);
            var b = first.get(i);
            for (int j = 1; j < second.size(); j++) {
                var c = second.get(j - 1);
                var d = second.get(j);
                if (a.y() == b.y() && c.y() == d.y() && a.y() == c.y()
                    && Math.max(Math.min(a.x(), b.x()), Math.min(c.x(), d.x()))
                    < Math.min(Math.max(a.x(), b.x()), Math.max(c.x(), d.x()))) return true;
                if (a.x() == b.x() && c.x() == d.x() && a.x() == c.x()
                    && Math.max(Math.min(a.y(), b.y()), Math.min(c.y(), d.y()))
                    < Math.min(Math.max(a.y(), b.y()), Math.max(c.y(), d.y()))) return true;
            }
        }
        return false;
    }
}
