package me.luckyraven.feature.weapon;

import me.luckyraven.Gangland;
import me.luckyraven.bukkit.ItemBuilder;
import me.luckyraven.database.DatabaseHelper;
import me.luckyraven.database.tables.WeaponTable;
import me.luckyraven.file.configuration.weapon.WeaponAddon;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WeaponManager {

	private final Gangland          gangland;
	private final Map<UUID, Weapon> weapons;
	private final WeaponAddon       weaponAddon;

	public WeaponManager(Gangland gangland) {
		this.gangland    = gangland;
		this.weaponAddon = gangland.getInitializer().getWeaponAddon();
		this.weapons     = new ConcurrentHashMap<>();
	}

	public void initialize(WeaponTable table) {
		DatabaseHelper helper = new DatabaseHelper(gangland, gangland.getInitializer().getGanglandDatabase());

		helper.runQueries(database -> {
			List<Object[]> data = database.table(table.getName()).selectAll();

			for (Object[] result : data) {
				UUID   uuid = UUID.fromString(String.valueOf(result[0]));
				String type = String.valueOf(result[1]);

				Weapon weaponAddon = this.weaponAddon.getWeapon(type);

				if (weaponAddon == null) continue;

				Weapon weapon = new Weapon(uuid, weaponAddon);

				this.weapons.put(uuid, weapon);
			}
		});
	}

	@Nullable
	public String getHeldWeaponName(ItemStack item) {
		if (item == null || item.getType().equals(Material.AIR) || item.getAmount() == 0) return null;

		return isWeapon(item) ? new ItemBuilder(item).getStringTagData("weapon") : null;
	}

	@Nullable
	public UUID getWeaponUUID(ItemStack item) {
		if (item == null || item.getType().equals(Material.AIR) || item.getAmount() == 0) return null;

		ItemBuilder tempItem = new ItemBuilder(item);
		String      value    = String.valueOf(tempItem.getStringTagData(Weapon.getTagProperName(WeaponTag.UUID)));
		UUID        uuid     = null;

		if (!(value == null || value.equals("null") || value.isEmpty())) {
			uuid = UUID.fromString(value);
		}

		return uuid;
	}

	public boolean isWeapon(ItemStack item) {
		if (item == null || item.getType().equals(Material.AIR) || item.getAmount() == 0) return false;

		return new ItemBuilder(item).hasNBTTag("weapon");
	}

	public boolean hasAmmunition(Player player, Weapon weapon) {
		return player.getInventory().containsAtLeast(weapon.getReloadAmmoType().buildItem(), weapon.getReloadConsume());
	}

	/**
	 * Gets the held weapon.
	 *
	 * @param player Current player.
	 *
	 * @return held weapon ItemBuilder or a null.
	 */
	@Nullable
	public ItemBuilder getHeldWeaponItem(Player player) {
		ItemStack mainHandItem = itemAccordingToSlot(player, EquipmentSlot.HAND);

		if (mainHandItem == null || mainHandItem.getType().equals(Material.AIR) || mainHandItem.getAmount() == 0)
			return null;
		if (isWeapon(mainHandItem)) return new ItemBuilder(mainHandItem);

		ItemStack offHandItem = itemAccordingToSlot(player, EquipmentSlot.OFF_HAND);

		if (offHandItem == null || offHandItem.getType().equals(Material.AIR) || offHandItem.getAmount() == 0)
			return null;

		return isWeapon(offHandItem) ? new ItemBuilder(offHandItem) : null;
	}

	@Nullable
	public Weapon getWeapon(Player player, @Nullable String type) {
		return getWeapon(player, null, type);
	}

	@Nullable
	public Weapon getWeapon(Player player, UUID uuid, @Nullable String type) {
		return getWeapon(player, uuid, type, false);
	}

	/**
	 * Obtaining a weapon from the saved data is a hectic procedure, thus making sure if the weapon is already generated
	 * would be better for the system. <br/> It is fine if the weapon wasn't already registered since there can be
	 * specific ones that need an uuid attached, and these weapons are generated from this function.
	 *
	 * @param player Gets the player that called this instruction.
	 * @param uuid Get already saved weapon UUID.
	 * @param type Can be nullable if the uuid was valid, otherwise use a valid type.
	 * @param newInstance Changes the data according to the currently held item.
	 *
	 * @return A weapon from the stored data. There is a chance to return null values in two cases: <br/> 1) Invalid
	 * 		UUID and null type. <br/> 2) Invalid UUID and invalid type.
	 */
	@Nullable
	public Weapon getWeapon(Player player, UUID uuid, @Nullable String type, boolean newInstance) {
		// the weapon is already created
		if (uuid != null && weapons.containsKey(uuid)) {
			Weapon availableWeapon = weapons.get(uuid);

			setWeaponData(availableWeapon, player);
			// when the weapon is already saved
			return availableWeapon;
		}

		// type shouldn't be null
		if (type == null || type.isEmpty()) return null;

		// the type is basically the name of the weapon in the files
		Weapon weaponAddon = this.weaponAddon.getWeapon(type);

		if (weaponAddon == null) return null;

		// check if the weapon already has an uuid but not registered
		if (uuid != null) {
			Weapon uuidWeapon = new Weapon(uuid, weaponAddon);

			setWeaponData(uuidWeapon, player);
			weapons.put(uuid, uuidWeapon);

			return uuidWeapon;
		}

		// generate a new uuid if there was non found
		boolean found = false;
		UUID    generatedUuid;

		// it shouldn't take a long time given the nature the UUID's low probability of collision
		// worst case O(n^2)
		do {
			generatedUuid = UUID.randomUUID();

			if (!weapons.containsKey(generatedUuid)) found = true;
		} while (!found);

		// mostly for new weapons
		// when the weapon is registered in the system but not tagged with an uuid
		Weapon finalWeapon = new Weapon(generatedUuid, weaponAddon);

		// check if the weapon is new or not
		// if it was new then no need to set the data of the uuid since it is not even created/built
		if (!newInstance) setWeaponData(finalWeapon, player);

		weapons.put(generatedUuid, finalWeapon);

		return finalWeapon;
	}

	@Nullable
	public Weapon validateAndGetWeapon(Player player, ItemStack heldItem) {
		if (heldItem == null || heldItem.getType().equals(Material.AIR) || heldItem.getAmount() == 0) return null;
		if (!isWeapon(heldItem)) return null;

		String weaponName = getHeldWeaponName(heldItem);
		if (weaponName == null) return null;

		// get the weapon information
		UUID uuid = getWeaponUUID(heldItem);
		if (uuid == null) return null;

		// get or load the new weapon
		return getWeapon(player, uuid, weaponName);
	}

	public void clear() {
		weapons.clear();
	}

	public Map<UUID, Weapon> getWeapons() {
		return Collections.unmodifiableMap(weapons);
	}

	private ItemStack itemAccordingToSlot(Player player, EquipmentSlot equipmentSlot) {
		return player.getInventory().getItem(equipmentSlot);
	}

	private void setWeaponData(Weapon weapon, Player player) {
		// need to collect data and save their values
		ItemBuilder itemBuilder = getHeldWeaponItem(player);

		if (itemBuilder == null) return;

		// get the ammo left
		int amountLeft = itemBuilder.getIntegerTagData(Weapon.getTagProperName(WeaponTag.AMMO_LEFT));
		// get the selective fire
		SelectiveFire selectiveFire = SelectiveFire.getType(
				itemBuilder.getStringTagData(Weapon.getTagProperName(WeaponTag.SELECTIVE_FIRE)));

		weapon.setCurrentMagCapacity(amountLeft);
		weapon.setCurrentSelectiveFire(selectiveFire);
	}

}
