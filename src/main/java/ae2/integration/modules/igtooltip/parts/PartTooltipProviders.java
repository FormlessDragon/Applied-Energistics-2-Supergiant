package ae2.integration.modules.igtooltip.parts;

import ae2.api.integrations.igtooltip.providers.BodyProvider;
import ae2.api.integrations.igtooltip.providers.IconProvider;
import ae2.api.integrations.igtooltip.providers.NameProvider;
import ae2.api.integrations.igtooltip.providers.ServerDataProvider;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class PartTooltipProviders {
    private static final Comparator<Registration<?>> COMPARATOR = Comparator.comparingInt(Registration::priority);
    private static final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private static final ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();
    private static final ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
    private static final ObjectArrayList<Registration<ServerDataProvider<?>>> serverDataProviders = new ObjectArrayList<>();
    private static final ObjectArrayList<Registration<BodyProvider<?>>> bodyProviders = new ObjectArrayList<>();
    private static final ObjectArrayList<Registration<NameProvider<?>>> nameProviders = new ObjectArrayList<>();
    private static final ObjectArrayList<Registration<IconProvider<?>>> iconProviders = new ObjectArrayList<>();
    private static final Reference2ObjectOpenHashMap<Class<?>, CachedProviders<?>> cache = new Reference2ObjectOpenHashMap<>();

    private PartTooltipProviders() {
    }

    public static <T> void addServerData(Class<T> baseClass, ServerDataProvider<? super T> provider, int priority) {
        add(serverDataProviders, baseClass, provider, priority);
    }

    public static <T> void addBody(Class<T> baseClass, BodyProvider<? super T> provider, int priority) {
        add(bodyProviders, baseClass, provider, priority);
    }

    public static <T> void addName(Class<T> baseClass, NameProvider<? super T> provider, int priority) {
        add(nameProviders, baseClass, provider, priority);
    }

    public static <T> void addIcon(Class<T> baseClass, IconProvider<? super T> provider, int priority) {
        add(iconProviders, baseClass, provider, priority);
    }

    private static <T> void add(List<Registration<T>> registrations, Class<?> baseClass, T provider, int priority) {
        writeLock.lock();
        try {
            registrations.add(new Registration<>(baseClass, provider, priority));
            registrations.sort(COMPARATOR);
            cache.clear();
        } finally {
            writeLock.unlock();
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> CachedProviders<T> getProviders(T object) {
        return getProviders((Class<T>) object.getClass());
    }

    @SuppressWarnings("unchecked")
    public static <U> CachedProviders<U> getProviders(Class<U> objectClass) {
        CachedProviders<U> providers;

        readLock.lock();
        try {
            providers = ((CachedProviders<U>) cache.get(objectClass));
        } finally {
            readLock.unlock();
        }

        // Lazily create the cache
        if (providers == null) {
            writeLock.lock();
            try {
                providers = (CachedProviders<U>) cache.get(objectClass);
                if (providers == null) {
                    providers = createProviderLists(objectClass);
                    cache.put(objectClass, providers);
                }
            } finally {
                writeLock.unlock();
            }
        }

        return providers;
    }

    /**
     * Guarded by {@link #writeLock}.
     */
    @SuppressWarnings("unchecked")
    private static <U> CachedProviders<U> createProviderLists(Class<U> clazz) {

        ObjectArrayList<NameProvider<? super U>> compatibleNameProviders = new ObjectArrayList<>();
        for (Registration<NameProvider<?>> registration : nameProviders) {
            if (registration.baseClass().isAssignableFrom(clazz)) {
                compatibleNameProviders.add((NameProvider<? super U>) registration.provider());
            }
        }

        ObjectArrayList<BodyProvider<? super U>> compatibleBodyProviders = new ObjectArrayList<>();
        for (Registration<BodyProvider<?>> registration : bodyProviders) {
            if (registration.baseClass().isAssignableFrom(clazz)) {
                compatibleBodyProviders.add((BodyProvider<? super U>) registration.provider());
            }
        }

        ObjectArrayList<IconProvider<? super U>> compatibleIconProviders = new ObjectArrayList<>();
        for (Registration<IconProvider<?>> registration : iconProviders) {
            if (registration.baseClass().isAssignableFrom(clazz)) {
                compatibleIconProviders.add((IconProvider<? super U>) registration.provider());
            }
        }

        ObjectArrayList<ServerDataProvider<? super U>> compatibleServerDataProviders = new ObjectArrayList<>();
        for (Registration<ServerDataProvider<?>> registration : serverDataProviders) {
            if (registration.baseClass().isAssignableFrom(clazz)) {
                compatibleServerDataProviders.add((ServerDataProvider<? super U>) registration.provider());
            }
        }

        return new CachedProviders<>(
            compatibleServerDataProviders,
            compatibleBodyProviders,
            compatibleIconProviders,
            compatibleNameProviders);
    }

    private record Registration<T>(Class<?> baseClass, T provider, int priority) {
    }

    public record CachedProviders<U>(
        List<ServerDataProvider<? super U>> serverDataProviders,
        List<BodyProvider<? super U>> bodyProviders,
        List<IconProvider<? super U>> iconProviders,
        List<NameProvider<? super U>> nameProviders) {
    }
}
