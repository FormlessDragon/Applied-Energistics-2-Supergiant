package ae2.api.cellterminal;

import ae2.api.config.AccessRestriction;
import ae2.api.config.FuzzyMode;
import ae2.api.config.StorageFilter;
import ae2.api.config.YesNo;

import java.util.Objects;

/**
 * Writable IO and filtering mode snapshot for storage-bus style targets.
 *
 * @param accessRestriction Current read/write access restriction.
 * @param storageFilter     Current storage filter mode.
 * @param filterOnExtract   Current extract filtering toggle.
 * @param fuzzyMode         Current fuzzy filter mode.
 */
public record CellTerminalIoFilterMode(AccessRestriction accessRestriction, StorageFilter storageFilter,
                                       YesNo filterOnExtract, FuzzyMode fuzzyMode) {
    public CellTerminalIoFilterMode {
        Objects.requireNonNull(accessRestriction, "accessRestriction");
        Objects.requireNonNull(storageFilter, "storageFilter");
        Objects.requireNonNull(filterOnExtract, "filterOnExtract");
        Objects.requireNonNull(fuzzyMode, "fuzzyMode");
    }
}
