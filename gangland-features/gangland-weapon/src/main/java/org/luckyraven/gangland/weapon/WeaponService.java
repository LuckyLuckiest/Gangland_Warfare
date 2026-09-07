package org.luckyraven.gangland.weapon;

import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.gangland.weapon.configuration.WeaponAddon;
import org.luckyraven.gangland.weapon.dto.AmmunitionData;
import org.luckyraven.gangland.weapon.types.WeaponType;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
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

		return new ItemBuilder(item).getStringTagData(Weapon.getTagProperName(WeaponTag.WEAPON));
	}

	public boolean isWeapon(ItemStack item) {
		if (item == null || item.getType().equals(Material.AIR) || item.getAmount() == 0) return false;

		// check the uuid of the weapon, and if it is available or not
		UUID weaponUuid = getWeaponUUID(item);

		if (weaponUuid == null) return false;

		// check if the uuid is in the weapons map
		if (weapons.containsKey(weaponUuid)) return true;

		// The uuid may not have been minted into the registry yet: converters, refreshers and shop deliveries build
		// transient copies so a registry entry (and a database row) is only created once the item is actually used.
		// Fall back to the configured catalogue so those items are still recognised as weapons.
		String weaponName = getHeldWeaponName(item);

		return weaponName != null && weaponAddon.getWeapon(weaponName) != null;
	}

	public boolean hasAmmunition(Player player, Weapon weapon) {
		AmmunitionData ammunitionData = weapon.getAmmunitionData();

		if (ammunitionData == null) return true;

		var item        = ammunitionData.getAmmoType().buildItem(player);
		var consumeRate = ammunitionData.getConsumeRate();

		return player.getInventory().containsAtLeast(item, consumeRate);
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

	/**
	 * The shared, read-only catalogue entry for {@code type} exactly as it was parsed from its YAML file.
	 * <p/>
	 * Unlike {@link #getWeapon(String)} this never mints a uuid and never registers anything, so it is the correct
	 * lookup for every read-only caller (display names, death messages, sign validation). The returned instance is
	 * shared — never hand it to a player and never mutate it; use {@link #createTransientWeapon(String)} for that.
	 *
	 * @param type weapon file name.
	 *
	 * @return the catalogue template, or {@code null} when no weapon file carries that name.
	 */
	@Nullable
	public Weapon getWeaponTemplate(@Nullable String type) {
		if (type == null || type.isEmpty()) return null;

		return weaponAddon.getWeapon(type);
	}

	/**
	 * Every catalogue template, for callers that need to scan the configured weapons by name. Previously these callers
	 * scanned {@link #getWeapons()}, which only ever held the instances minted so far.
	 */
	public Collection<Weapon> getWeaponTemplates() {
		return Collections.unmodifiableCollection(weaponAddon.getWeapons());
	}

	/**
	 * A fresh, unregistered copy of the {@code type} template carrying a valid uuid.
	 * <p/>
	 * Used by item converters, refreshers and anything else that only needs an {@code ItemStack}: the instance stays
	 * out of {@link #getWeapons()} (and therefore out of the {@code weapon} table) until the item is really picked up,
	 * at which point {@link #validateAndGetWeapon(Player, ItemStack)} registers it under the uuid the item carries.
	 *
	 * @param type weapon file name.
	 *
	 * @return an unregistered copy, or {@code null} when no weapon file carries that name.
	 */
	@Nullable
	public Weapon createTransientWeapon(@Nullable String type) {
		Weapon template = getWeaponTemplate(type);

		if (template == null) return null;

		return template.copyWithUUID(mintUuid(template, type, null));
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

		UUID finalUuid = mintUuid(weaponAddon, type, uuid);

		// mostly for new weapons
		// when the weapon is registered in the system but not tagged with an uuid
		Weapon finalWeapon = weaponAddon.copyWithUUID(finalUuid);

		// Register the weapon first so isWeapon() can find it when setWeaponData
		// calls getHeldWeaponItem — otherwise the map lookup returns null and the
		// NBT ammo/durability data is never applied (weapon stays at max capacity).
		weapons.put(finalUuid, finalWeapon);

		// check if the weapon is new or not
		// if it was new, then no need to set the data of the uuid since it is not even created/built
		if (player != null && !newInstance) setWeaponData(finalWeapon, player);

		return finalWeapon;
	}

	@Nullable
	public Weapon validateAndGetWeapon(Player player, ItemStack heldItem) {
		if (heldItem == null || heldItem.getType().equals(Material.AIR) || heldItem.getAmount() == 0) return null;

		String weaponName = getHeldWeaponName(heldItem);
		if (weaponName == null) return null;

		// get the weapon information
		UUID uuid = getWeaponUUID(heldItem);
		if (uuid == null) return null;

		// newInstance=true skips the internal player-hand re-fetch inside getWeapon.
		// We sync directly from heldItem so dropped items, off-hand items, and
		// first-login cases all read the correct NBT ammo/durability values.
		Weapon weapon = getWeapon(player, uuid, weaponName, true);
		if (weapon == null) return null;

		setWeaponData(weapon, new ItemBuilder(heldItem));

		return weapon;
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

	/**
	 * Derives the uuid a fresh copy of {@code template} should carry. Throwables share one deterministic uuid per type
	 * so identical items stack in the inventory; everything else keeps the caller's uuid when there is one, otherwise
	 * gets a random uuid that does not collide with an already registered instance.
	 */
	private UUID mintUuid(Weapon template, @Nullable String type, @Nullable UUID uuid) {
		if (template.getCategory() == WeaponType.THROWABLE) {
			return UUID.nameUUIDFromBytes(("throwable:" + type).getBytes(StandardCharsets.UTF_8));
		}

		UUID finalUuid = (uuid != null) ? uuid : UUID.randomUUID();
		while (weapons.containsKey(finalUuid)) finalUuid = UUID.randomUUID();

		return finalUuid;
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
