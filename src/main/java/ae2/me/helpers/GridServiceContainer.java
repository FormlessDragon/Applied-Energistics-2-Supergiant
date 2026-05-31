package ae2.me.helpers;

import ae2.api.networking.IGridServiceProvider;

import java.util.Map;

public record GridServiceContainer(
    Map<Class<?>, IGridServiceProvider> services,
    IGridServiceProvider[] serverStartTickServices,
    IGridServiceProvider[] levelStartTickServices,
    IGridServiceProvider[] levelEndTickServices,
    IGridServiceProvider[] serverEndTickServices) {
}
