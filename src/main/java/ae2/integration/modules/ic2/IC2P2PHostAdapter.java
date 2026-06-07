package ae2.integration.modules.ic2;

import ae2.integration.abstraction.IC2P2PHost;
import ae2.integration.abstraction.IC2P2PTunnel;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

final class IC2P2PHostAdapter implements IC2P2PHost {
    private final IC2P2PHostRegistry registry;
    private final World world;
    private final BlockPos pos;
    private final IC2P2PTunnel tunnel;
    private boolean attached;

    public IC2P2PHostAdapter(IC2P2PHostRegistry registry, World world, BlockPos pos, IC2P2PTunnel tunnel) {
        this.registry = registry;
        this.world = world;
        this.pos = pos;
        this.tunnel = tunnel;
    }

    @Override
    public void onLoad() {
        attachOrUpdate();
    }

    @Override
    public void invalidate() {
        if (this.attached) {
            this.registry.detach(this.world, this.pos, this.tunnel);
            this.attached = false;
        }
    }

    @Override
    public void update() {
        attachOrUpdate();
    }

    private void attachOrUpdate() {
        this.registry.attachOrUpdate(this.world, this.pos, this.tunnel);
        this.attached = true;
    }
}
