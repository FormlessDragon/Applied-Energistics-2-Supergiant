/**
 * Classes to allow addons to define behavior of AE2's own devices when they have to interact with custom key types.
 *
 * <h3>Part implementations</h3>
 * <ul>
 * <li>Import bus: {@link ae2.api.behaviors.StackImportStrategy}.</li>
 * <li>Export bus: {@link ae2.api.behaviors.StackExportStrategy}.</li>
 * <li>Formation plane: {@link ae2.api.behaviors.PlacementStrategy}.</li>
 * <li>Annihilation plane: {@link ae2.api.behaviors.PickupStrategy}.</li>
 * </ul>
 *
 * <h3>Working with inventories</h3>
 * <ul>
 * <li>Building {@link ae2.api.storage.MEStorage}s from other kinds of inventories, used by the storage bus and the
 * pattern provider: {@link ae2.api.behaviors.ExternalStorageStrategy}.</li>
 * <li>Exposing AE2's generic inventories, such as the interface's or pattern provider's:
 * {@link ae2.api.behaviors.GenericInternalInventory}.</li>
 * <li>Defining the max capacity of interface and pattern provider slots:
 * {@link ae2.api.behaviors.GenericSlotCapacities}.</li>
 * </ul>
 *
 * <h3>GUI interactions</h3>
 * <ul>
 * <li>Emptying and filling container items in AE2 menus: {@link ae2.api.behaviors.ContainerItemStrategy}</li>
 * </ul>
 *
 * <p>
 * API note: These classes are experimental: we might release breaking changes to them in any release.
 */
package ae2.api.behaviors;
