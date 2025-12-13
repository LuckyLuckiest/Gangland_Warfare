package me.luckyraven.weapon;

import lombok.Getter;
import me.luckyraven.util.ItemBuilder;
import me.luckyraven.weapon.configuration.WeaponAddon;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public abstract class WeaponService implements Comparator<Weapon> {

	private final WeaponAddon weaponAddon;

	@Getter
	private final Map<UUID, Weapon> weapons;

	public WeaponService(WeaponAddon weaponAddon) {
		this.weaponAddon = weaponAddon;
		this.weapons     = new HashMap<>();
	}

	@Nullable
	public static UUID getWeaponUUID(ItemStack item) {
		if (item == null || item.getType().equals(Material.AIR) || item.getAmount() == 0) return null;

		String      tagProperName = Weapon.getTagProperName(WeaponTag.UUID);
		ItemBuilder tempItem      = new ItemBuilder(item);

		String stringTagData = tempItem.getStringTagData(tagProperName);
		String value         = String.valueOf(stringTagData);
		UUID   uuid          = null;

		if (!(value == null || value.equals("null") || value.isEmpty())) {
			uuid = UUID.fromString(value);
		}

		return uuid;
	}

	@Override
	public int compare(Weapon weapon1, Weapon weapon2) {
		return weapon1.compareTo(weapon2);
	}

	@Nullable
	public String getHeldWeaponName(ItemStack item) {
		if (item == null || item.getType().equals(Material.AIR) || item.getAmount() == 0) return null;

		return isWeapon(item) ? new ItemBuilder(item).getStringTagData("weapon") : null;
	}

	public boolean isWeapon(ItemStack item) {
		if (item == null || item.getType().equals(Material.AIR) || item.getAmount() == 0) return false;

		// check the uuid of the weapon, and if it is available or not
		UUID weaponUuid = getWeaponUUID(item);

		if (weaponUuid == null) return false;

		// check if the uuid is in the weapons map
		return weapons.containsKey(weaponUuid);
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
	public Weapon getWeapon(@Nullable String type) {
		return getWeapon(null, null, type);
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
	 * Getting a weapon from the saved data is a hectic procedure, thus making sure if the weapon is already generated
	 * would be better for the system.
	 * <p/>
	 * It is fine if the weapon wasn't already registered since there can be specific ones that need an uuid attached,
	 * and these weapons are generated from this function.
	 *
	 * @param player Gets the player that called this instruction and can be null if it was a new instance.
	 * @param uuid Get already saved weapon UUID.
	 * @param type Can be nullable if the uuid was valid, otherwise use a valid type.
	 * @param newInstance Changes the data according to the currently held item.
	 *
	 * @return A weapon from the stored data. There is a chance to return null values in two cases:
	 * 		<p/>
	 * 		1) Invalid UUID and null type.
	 * 		<p/>
	 * 		2) Invalid UUID and invalid type.
	 */
	@Nullable
	public Weapon getWeapon(@Nullable Player player, UUID uuid, @Nullable String type, boolean newInstance) {
		// the weapon is already created
		if (uuid != null) {
			Weapon existing = weapons.get(uuid);
			if (existing != null) {
				if (player != null && !newInstance) setWeaponData(existing, player);
				// when the weapon is already saved
				return existing;
			}
		}

		// type shouldn't be null
		if (type == null || type.isEmpty()) return null;

		// the type is basically the name of the weapon in the files
		Weapon weaponAddon = this.weaponAddon.getWeapon(type);

		if (weaponAddon == null) return null;

		// check if the weapon already has an uuid but not registered, use it; otherwise generate one
		UUID finalUuid = (uuid != null) ? uuid : UUID.randomUUID();

		while (weapons.containsKey(finalUuid)) finalUuid = UUID.randomUUID();

		// mostly for new weapons
		// when the weapon is registered in the system but not tagged with an uuid
		Weapon finalWeapon = new Weapon(finalUuid, weaponAddon);

		// check if the weapon is new or not
		// if it was new, then no need to set the data of the uuid since it is not even created/built
		if (player != null && !newInstance) setWeaponData(finalWeapon, player);

		weapons.put(finalUuid, finalWeapon);

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

	public boolean isHeadPosition(Location l1, Location l2) {
		return Math.abs(l1.getY() - l2.getY()) > 1.4;
	}

	private ItemStack itemAccordingToSlot(Player player, EquipmentSlot equipmentSlot) {
		return player.getInventory().getItem(equipmentSlot);
	}

	private void setWeaponData(Weapon weapon, Player player) {
		// need to collect data and save their values
		ItemBuilder itemBuilder = getHeldWeaponItem(player);

		setWeaponData(weapon, itemBuilder);
	}

	private void setWeaponData(Weapon weapon, @Nullable ItemBuilder itemBuilder) {
		if (itemBuilder == null) return;

		// get the ammo left
		int amountLeft = itemBuilder.getIntegerTagData(Weapon.getTagProperName(WeaponTag.AMMO_LEFT));
		// get the selective fire
		SelectiveFire selectiveFire = SelectiveFire.getType(
				itemBuilder.getStringTagData(Weapon.getTagProperName(WeaponTag.SELECTIVE_FIRE)));
		// set weapon durability
		short durability = weapon.getDurabilityCalculator().calculateWeaponDurabilityFromItem(itemBuilder);

		weapon.setCurrentDurability(durability);
		weapon.setCurrentMagCapacity(amountLeft);
		weapon.setCurrentSelectiveFire(selectiveFire);
	}

}
