package ae2.api.util;

import org.jetbrains.annotations.Nullable;

public interface ICustomName {

    boolean hasCustomName();

    @Nullable
    String getCustomName();

    void setCustomName(@Nullable String customName);

    default void setCustomNameFromRenamer(@Nullable String customName) {
        setCustomName(customName == null || customName.isEmpty() ? null : customName);
        onCustomNameChanged();
    }

    default void onCustomNameChanged() {
    }

}
