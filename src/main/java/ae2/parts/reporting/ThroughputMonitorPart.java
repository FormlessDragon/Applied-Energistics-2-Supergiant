package ae2.parts.reporting;

import ae2.api.networking.IGridNode;
import ae2.api.networking.ticking.IGridTickable;
import ae2.api.networking.ticking.TickRateModulation;
import ae2.api.networking.ticking.TickingRequest;
import ae2.api.parts.IPartItem;
import ae2.api.parts.IPartModel;
import ae2.api.stacks.AmountFormat;
import ae2.core.AppEng;
import ae2.items.parts.PartModels;
import ae2.parts.PartModel;
import ae2.util.InteractionUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class ThroughputMonitorPart extends AbstractMonitorPart implements IGridTickable {
    @PartModels
    public static final ResourceLocation MODEL_OFF = AppEng.makeId("part/throughput_monitor_off");
    @PartModels
    public static final ResourceLocation MODEL_ON = AppEng.makeId("part/throughput_monitor_on");
    @PartModels
    public static final ResourceLocation MODEL_LOCKED_OFF = AppEng.makeId("part/throughput_monitor_locked_off");
    @PartModels
    public static final ResourceLocation MODEL_LOCKED_ON = AppEng.makeId("part/throughput_monitor_locked_on");

    public static final IPartModel MODELS_OFF = new PartModel(MODEL_BASE, MODEL_OFF, MODEL_STATUS_OFF);
    public static final IPartModel MODELS_ON = new PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_ON);
    public static final IPartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_HAS_CHANNEL);
    public static final IPartModel MODELS_LOCKED_OFF = new PartModel(MODEL_BASE, MODEL_LOCKED_OFF, MODEL_STATUS_OFF);
    public static final IPartModel MODELS_LOCKED_ON = new PartModel(MODEL_BASE, MODEL_LOCKED_ON, MODEL_STATUS_ON);
    public static final IPartModel MODELS_LOCKED_HAS_CHANNEL = new PartModel(MODEL_BASE, MODEL_LOCKED_ON,
        MODEL_STATUS_HAS_CHANNEL);

    private final ThroughputCache cache = new ThroughputCache();
    private double lastReportedValue = -1;
    private WorkRoutine workRoutine = WorkRoutine.SECOND;
    private WorkRoutine lastWorkRoutine = WorkRoutine.SECOND;

    public ThroughputMonitorPart(IPartItem<?> partItem) {
        super(partItem, false);
        getMainNode().addService(IGridTickable.class, this);
    }

    @SideOnly(Side.CLIENT)
    private static void renderCenteredFaceText(String text, int color) {
        var fontRenderer = Minecraft.getMinecraft().fontRenderer;
        int width = fontRenderer.getStringWidth(text);
        GlStateManager.pushMatrix();
        try {
            GlStateManager.scale(1.0f / 62.0f, -1.0f / 62.0f, 1.0f / 62.0f);
            GlStateManager.scale(0.5f, 0.5f, 1.0f);
            GlStateManager.translate(-0.5f * width, 0.0f, 0.5f);
            GlStateManager.disableLighting();
            GlStateManager.disableDepth();
            fontRenderer.drawString(text, 0, 0, color);
        } finally {
            GlStateManager.enableDepth();
            GlStateManager.enableLighting();
            GlStateManager.popMatrix();
        }
    }

    @Override
    public IPartModel getStaticModels() {
        return this.selectModel(MODELS_OFF, MODELS_ON, MODELS_HAS_CHANNEL, MODELS_LOCKED_OFF, MODELS_LOCKED_ON,
            MODELS_LOCKED_HAS_CHANNEL);
    }

    @Override
    public void writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setDouble("throughput", this.lastReportedValue);
        data.setInteger("routine", this.workRoutine.ordinal());
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        this.lastReportedValue = data.getDouble("throughput");
        this.workRoutine = WorkRoutine.fromInt(data.getInteger("routine"));
    }

    @Override
    public void writeToStream(PacketBuffer data) {
        super.writeToStream(data);
        data.writeDouble(this.lastReportedValue);
        data.writeByte(this.workRoutine.ordinal());
    }

    @Override
    public boolean readFromStream(PacketBuffer data) {
        boolean needRedraw = super.readFromStream(data);
        double value = data.readDouble();
        WorkRoutine routine = WorkRoutine.fromInt(data.readByte());
        needRedraw |= this.lastReportedValue != value || this.workRoutine != routine;
        this.lastReportedValue = value;
        this.workRoutine = routine;
        return needRedraw;
    }

    @Override
    public void writeVisualStateToNBT(NBTTagCompound data) {
        super.writeVisualStateToNBT(data);
        data.setDouble("lastValue", this.lastReportedValue);
        data.setInteger("routine", this.workRoutine.ordinal());
    }

    @Override
    public void readVisualStateFromNBT(NBTTagCompound data) {
        super.readVisualStateFromNBT(data);
        this.lastReportedValue = data.getDouble("lastValue");
        this.workRoutine = WorkRoutine.fromInt(data.getInteger("routine"));
    }

    @Override
    public boolean onUseWithoutItem(EntityPlayer player, Vec3d pos) {
        if (!InteractionUtil.isInAlternateUseMode(player)) {
            if (isClientSide()) {
                return true;
            }
            cycleWorkRoutine();
            player.sendStatusMessage(new TextComponentTranslation("chat.ae2.ThroughputMonitorRoutine",
                new TextComponentTranslation(getRoutineTranslationKey()).getFormattedText()), true);
            return true;
        }
        return super.onUseWithoutItem(player, pos);
    }

    private String getRoutineTranslationKey() {
        return switch (this.workRoutine) {
            case TICK -> "gui.ae2.ThroughputMonitorRoutineTick";
            case SECOND -> "gui.ae2.ThroughputMonitorRoutineSecond";
            case MINUTE -> "gui.ae2.ThroughputMonitorRoutineMinute";
            case TEN_MINUTE -> "gui.ae2.ThroughputMonitorRoutineTenMinute";
        };
    }

    private void cycleWorkRoutine() {
        this.workRoutine = WorkRoutine.cycle(this.workRoutine);
        getMainNode().ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
        getHost().markForSave();
        getHost().markForUpdate();
    }

    @Override
    protected void configureWatchers() {
        if (getDisplayed() != null) {
            updateState(getAmount(), getLevel().getTotalWorldTime());
            getMainNode().ifPresent((grid, node) -> grid.getTickManager().wakeDevice(node));
        } else {
            getMainNode().ifPresent((grid, node) -> grid.getTickManager().sleepDevice(node));
        }
        super.configureWatchers();
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(20, 100, !isActive() || getDisplayed() == null);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (!getMainNode().isActive() || getDisplayed() == null) {
            resetState();
            return TickRateModulation.SLEEP;
        }

        long currentTick = getLevel().getTotalWorldTime();
        long currentAmount = getAmount();
        if (cache.size() == 0) {
            updateState(currentAmount, currentTick);
            this.lastReportedValue = 0;
            return TickRateModulation.URGENT;
        }

        if (this.workRoutine == this.lastWorkRoutine) {
            this.lastReportedValue = cache.averagePerTick(currentTick, this.workRoutine.timeLimitSeconds)
                * this.workRoutine.ticks;
        } else {
            this.lastReportedValue = 0;
        }
        updateState(currentAmount, currentTick);
        getHost().markForUpdate();
        return TickRateModulation.SLOWER;
    }

    private void resetState() {
        cache.clear();
        this.lastReportedValue = 0;
    }

    private void updateState(long amount, long tick) {
        cache.push(amount, tick);
        this.lastWorkRoutine = this.workRoutine;
    }

    private String getThroughputText() {
        if (getDisplayed() == null) {
            return "";
        }
        var sign = this.lastReportedValue > 0 ? "+" : this.lastReportedValue == 0 ? "" : "-";
        String valueText = Math.abs(this.lastReportedValue) > 10 || this.lastReportedValue == 0
            ? getDisplayed().formatAmount(Math.round(Math.abs(this.lastReportedValue)), AmountFormat.SLOT)
            : String.format("%.2f", Math.abs(this.lastReportedValue));
        return switch (this.workRoutine) {
            case TICK ->
                new TextComponentTranslation("gui.ae2.ThroughputMonitorValueTick", sign, valueText).getFormattedText();
            case SECOND ->
                new TextComponentTranslation("gui.ae2.ThroughputMonitorValueSecond", sign, valueText).getFormattedText();
            case MINUTE ->
                new TextComponentTranslation("gui.ae2.ThroughputMonitorValueMinute", sign, valueText).getFormattedText();
            case TEN_MINUTE ->
                new TextComponentTranslation("gui.ae2.ThroughputMonitorValueTenMinute", sign, valueText).getFormattedText();
        };
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderDynamic(double x, double y, double z, float partialTicks, int destroyStage) {
        if (!isActive() || getDisplayed() == null) {
            return;
        }
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x + 0.5, y + 0.5, z + 0.5);
            ae2.client.render.BlockEntityRenderHelper.rotateToFace(
                ae2.api.orientation.BlockOrientation.get(getSide(), getSpin()));
            GlStateManager.translate(0, 0.05, 0.5);

            ae2.client.render.BlockEntityRenderHelper.renderItem2dWithAmount(getDisplayed(), getAmount(), canCraft(),
                6 / 16f, -0.12f, getColor().contrastTextColor);

            GlStateManager.translate(0, -0.22, 0.02);
            renderCenteredFaceText(getThroughputText(),
                this.lastReportedValue > 0 ? 0x55FF55 : this.lastReportedValue < 0 ? 0xFF5555 : getColor().contrastTextColor);
        } finally {
            GlStateManager.popMatrix();
        }
    }

    private enum WorkRoutine {
        TICK(1, 10),
        SECOND(20, 20),
        MINUTE(1200, 300),
        TEN_MINUTE(12000, 3000);

        final int ticks;
        final int timeLimitSeconds;

        WorkRoutine(int ticks, int timeLimitSeconds) {
            this.ticks = ticks;
            this.timeLimitSeconds = timeLimitSeconds;
        }

        static WorkRoutine cycle(WorkRoutine routine) {
            return switch (routine) {
                case TICK -> SECOND;
                case SECOND -> MINUTE;
                case MINUTE -> TEN_MINUTE;
                case TEN_MINUTE -> TICK;
            };
        }

        static WorkRoutine fromInt(int value) {
            return switch (value) {
                case 0 -> TICK;
                case 2 -> MINUTE;
                case 3 -> TEN_MINUTE;
                default -> SECOND;
            };
        }
    }
}
