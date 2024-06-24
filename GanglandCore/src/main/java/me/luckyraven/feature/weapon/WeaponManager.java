package me.luckyraven.feature.weapon;

import me.luckyraven.Gangland;
import me.luckyraven.bukkit.ItemBuilder;
import me.luckyraven.database.DatabaseHelper;
import me.luckyraven.database.tables.WeaponTable;
import me.luckyraven.file.configuration.weapon.WeaponAddon;
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
	public synchronized Weapon getWeapon(@Nullable String type) {
		return getWeapon(null, type);
	}

	/**
	 * Obtaining a weapon from the saved data is a hectic procedure, thus making sure if the weapon is already generated
	 * would be better for the system. <br/> It is fine if the weapon wasn't already registered since there can be
	 * specific ones that need an uuid attached, and these weapons are generated from this function.
	 *
	 * @param uuid Get already saved weapon UUID.
	 * @param type Can be nullable if the uuid was valid, otherwise use a valid type.
	 *
	 * @return A weapon from the stored data. There is a chance to return null values in two cases: <br/> 1) Invalid
	 * 		UUID and null type. <br/> 2) Invalid UUID and invalid type.
	 */
	@Nullable
	public synchronized Weapon getWeapon(UUID uuid, @Nullable String type) {
		if (uuid != null && weapons.containsKey(uuid)) {
			Weapon availableWeapon = weapons.get(uuid);

			setWeaponData(availableWeapon);
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

			setWeaponData(uuidWeapon);
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

		// when the weapon is registered in the system but not tagged with an uuid
		Weapon finalWeapon = new Weapon(generatedUuid, weaponAddon);

		setWeaponData(finalWeapon);
		weapons.put(generatedUuid, finalWeapon);

		return finalWeapon;
	}

	public void clear() {
		weapons.clear();
	}

	public Map<UUID, Weapon> getWeapons() {
		return Collections.unmodifiableMap(weapons);
	}

	private void setWeaponData(Weapon weapon) {
		// need to collect data and save their values
		ItemBuilder itemBuilder = new ItemBuilder(weapon.getMaterial());
		// get the ammo left
		int amountLeft = itemBuilder.getIntegerTagData(Weapon.getTagProperName(WeaponTag.AMMO_LEFT));
		// get the selective fire
		SelectiveFire selectiveFire = SelectiveFire.getType(
				itemBuilder.getStringTagData(Weapon.getTagProperName(WeaponTag.SELECTIVE_FIRE)));

		weapon.setCurrentMagCapacity(amountLeft);
		weapon.setCurrentSelectiveFire(selectiveFire);
	}

}
