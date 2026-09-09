package ae2.client.gui.widgets;

import ae2.client.gui.AEBaseGui;
import ae2.client.gui.Icon;
import ae2.container.crafting.RecipeSelection;
import ae2.core.localization.ButtonToolTips;
import ae2.core.localization.Tooltips;
import ae2.util.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public final class RecipeSelectionButton {
    private static final int RECIPES_PER_PAGE = 34;

    private final AEBaseGui<?> screen;
    private final Supplier<List<RecipeSelection.Candidate>> candidates;
    @Nullable
    private final BooleanSupplier hasConflict;
    @Nullable
    private final LongSupplier candidatesRevision;
    private final Supplier<ResourceLocation> selectedRecipe;
    private final Consumer<ResourceLocation> selectionHandler;
    private final TabButton button;
    private List<ResourceLocation> visibleCandidateIds = List.of();
    private long lastCandidatesRevision = Long.MIN_VALUE;

    public RecipeSelectionButton(AEBaseGui<?> screen,
                                 Supplier<List<RecipeSelection.Candidate>> candidates,
                                 Supplier<ResourceLocation> selectedRecipe,
                                 Consumer<ResourceLocation> selectionHandler) {
        this(screen, candidates, null, null, selectedRecipe, selectionHandler);
    }

    public RecipeSelectionButton(AEBaseGui<?> screen,
                                 Supplier<List<RecipeSelection.Candidate>> candidates,
                                 @Nullable BooleanSupplier hasConflict,
                                 @Nullable LongSupplier candidatesRevision,
                                 Supplier<ResourceLocation> selectedRecipe,
                                 Consumer<ResourceLocation> selectionHandler) {
        this.screen = screen;
        this.candidates = candidates;
        this.hasConflict = hasConflict;
        this.candidatesRevision = candidatesRevision;
        this.selectedRecipe = selectedRecipe;
        this.selectionHandler = selectionHandler;
        this.button = new CompactTabButton(this::open);
        this.button.visible = false;
        this.button.enabled = false;
    }

    public TabButton button() {
        return this.button;
    }

    public void update(boolean parentVisible) {
        if (!parentVisible) {
            if (this.button.visible) {
                this.screen.closeSelectionPopup();
            }
            this.button.visible = false;
            this.button.enabled = false;
            return;
        }

        if (this.hasConflict == null) {
            List<ResourceLocation> candidateIds = this.candidates.get().stream()
                                                                 .map(RecipeSelection.Candidate::id)
                                                                 .toList();
            boolean candidatesChanged = !candidateIds.equals(this.visibleCandidateIds);
            boolean visible = candidateIds.size() > 1;
            if (candidatesChanged || !visible && this.button.visible) {
                this.screen.closeSelectionPopup();
            }
            this.visibleCandidateIds = candidateIds;
            this.button.visible = visible;
            this.button.enabled = visible;
            return;
        }

        boolean visible = this.hasConflict.getAsBoolean();
        long revision = Objects.requireNonNull(this.candidatesRevision).getAsLong();
        if (revision != this.lastCandidatesRevision) {
            this.screen.closeSelectionPopup();
            this.lastCandidatesRevision = revision;
        }
        this.button.visible = visible;
        this.button.enabled = visible;
    }

    private void open() {
        openPage(pageContainingSelection());
    }

    private int pageContainingSelection() {
        ResourceLocation selected = this.selectedRecipe.get();
        List<RecipeSelection.Candidate> current = this.candidates.get();
        for (int i = 0; i < current.size(); i++) {
            if (current.get(i).id().equals(selected)) {
                return i / RECIPES_PER_PAGE;
            }
        }
        return 0;
    }

    private void openPage(int page) {
        List<RecipeSelection.Candidate> current = this.candidates.get();
        int pageCount = Math.max(1, (current.size() + RECIPES_PER_PAGE - 1) / RECIPES_PER_PAGE);
        int actualPage = Math.clamp(page, 0, pageCount - 1);
        int start = actualPage * RECIPES_PER_PAGE;
        int end = Math.min(start + RECIPES_PER_PAGE, current.size());

        var entries = GridSelectionPopup.<Choice>entries();
        if (actualPage > 0) {
            entries.add(GridSelectionPopup.Entry.icon(new PageChoice(actualPage - 1), Icon.ARROW_LEFT,
                List.of(ButtonToolTips.PreviousRecipePage.text(actualPage, pageCount))));
        }
        for (int i = start; i < end; i++) {
            RecipeSelection.Candidate candidate = current.get(i);
            entries.add(GridSelectionPopup.Entry.item(new RecipeChoice(candidate.id()), candidate.output().copy(),
                recipeTooltip(candidate)));
        }
        if (actualPage + 1 < pageCount) {
            entries.add(GridSelectionPopup.Entry.icon(new PageChoice(actualPage + 1), Icon.ARROW_RIGHT,
                List.of(ButtonToolTips.NextRecipePage.text(actualPage + 2, pageCount))));
        }

        ResourceLocation selected = this.selectedRecipe.get();
        Choice selectedChoice = selected == null ? null : new RecipeChoice(selected);
        var bounds = this.screen.getBounds(false);
        var absoluteBounds = this.screen.getBounds(true);
        this.screen.openSelectionPopup(GridSelectionPopup.forButton(this.button, absoluteBounds.x,
            absoluteBounds.y, bounds.width, bounds.height, entries, selectedChoice, choice -> {
                switch (choice) {
                    case PageChoice(int targetPage) -> openPage(targetPage);
                    case RecipeChoice(ResourceLocation id) -> this.selectionHandler.accept(id);
                }
            }));
    }

    private static List<ITextComponent> recipeTooltip(RecipeSelection.Candidate candidate) {
        String modName = Platform.getModName(candidate.id().getNamespace());
        return List.of(
            new TextComponentString(candidate.output().getDisplayName()),
            Tooltips.muted(ButtonToolTips.RecipeId.text(candidate.id().toString())),
            new TextComponentString(modName).setStyle(Tooltips.muted(new TextComponentString("")).getStyle())
        );
    }

    private static final class CompactTabButton extends TabButton {
        private CompactTabButton(Runnable onPress) {
            super(Icon.RECIPE_CONFLICT_SELECTION, ButtonToolTips.RecipeConflictSelection.text(), onPress);
            this.width = 16;
            this.height = 16;
        }

        @Override
        public void drawButton(Minecraft minecraft, int mouseX, int mouseY, float partialTicks) {
            if (!this.visible) {
                return;
            }

            this.hovered = mouseX >= this.x && mouseY >= this.y
                && mouseX < this.x + this.width && mouseY < this.y + this.height;
            Icon background = this.isFocused() ? Icon.TAB_BUTTON_BACKGROUND_FOCUS : Icon.TAB_BUTTON_BACKGROUND;
            background.getBlitter().dest(this.x, this.y, this.width, this.height).blit();

            GlStateManager.pushMatrix();
            GlStateManager.translate(this.x + this.width / 2.0F,
                this.y + this.height / 2.0F, 100.0F);
            GlStateManager.scale(0.75F, 0.75F, 1.0F);
            Icon.RECIPE_CONFLICT_SELECTION.getBlitter().dest(-8, -8).blit();
            GlStateManager.popMatrix();
        }
    }

    private sealed interface Choice permits RecipeChoice, PageChoice {
    }

    private record RecipeChoice(ResourceLocation id) implements Choice {
    }

    private record PageChoice(int page) implements Choice {
    }
}
