package ae2.client.render.mesh;

public interface RenderContext {
    @FunctionalInterface
    interface QuadTransform {
        /**
         * Return false to filter out quads from rendering. When more than one transform is in effect, returning false
         * means unapplied transforms will not receive the quad.
         */
        boolean transform(MutableQuadView quad);
    }
}
