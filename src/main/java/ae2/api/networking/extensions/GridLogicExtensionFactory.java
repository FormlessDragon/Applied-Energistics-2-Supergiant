package ae2.api.networking.extensions;

@FunctionalInterface
public interface GridLogicExtensionFactory {

    GridLogicExtension create(GridLogicContext context);
}
