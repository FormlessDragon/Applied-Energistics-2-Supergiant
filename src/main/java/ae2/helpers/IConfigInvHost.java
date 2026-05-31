package ae2.helpers;

import ae2.helpers.externalstorage.GenericStackInv;

/**
 * Used by the memory card to export/import the config inventory.
 */
public interface IConfigInvHost {
    GenericStackInv getConfig();
}
