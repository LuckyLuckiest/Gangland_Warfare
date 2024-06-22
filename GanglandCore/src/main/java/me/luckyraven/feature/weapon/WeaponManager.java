package me.luckyraven.feature.weapon;

import me.luckyraven.Gangland;
import me.luckyraven.database.DatabaseHelper;
import me.luckyraven.database.tables.WeaponTable;
import me.luckyraven.file.configuration.weapon.WeaponAddon;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class WeaponManager {

	private final Gangland          gangland;
	private final Map<UUID, Weapon> weapons;
	private final WeaponAddon       weaponAddon;

	public WeaponManager(Gangland gangland) {
		this.gangland    = gangland;
		this.weaponAddon = gangland.getInitializer().getWeaponAddon();
		this.weapons     = new HashMap<>();
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

	/**
	 * Obtaining a weapon from the saved data is a hectic procedure, thus making sure if the weapon is already generated
	 * would be better for the system. <br/> It is fine if the weapon was not already registered since there can be
	 * specific ones that need an uuid attached, and these weapons are generated from this function.
	 *
	 * @param uuid Get already saved weapon UUID.
	 * @param type Can be nullable if the uuid was valid, otherwise use a valid type.
	 *
	 * @return A weapon from the stored data. There is a chance to return null values in two cases: <br/> 1) Invalid
	 * 		UUID and null type. <br/> 2) Invalid UUID and invalid type.
	 */
	@Nullable
	public Weapon getWeapon(UUID uuid, @Nullable String type) {
		Weapon weaponV1 = null;

		if (uuid != null) weaponV1 = weapons.get(uuid);

		if (weaponV1 != null)
			// when the weapon is already saved
			return weaponV1;

		// type should not be null
		if (type == null || type.isEmpty()) return null;

		// the type is basically the name of the weapon in the files
		Weapon weaponV2 = weaponAddon.getWeapon(type);

		if (weaponV2 == null) return null;

		// generate a new uuid
		boolean found = false;
		UUID    generatedUuid;

		// it shouldn't take a long time given the nature the UUID's low probability of collision
		// worst case O(n^2)
		do {
			generatedUuid = UUID.randomUUID();

			if (!weapons.containsKey(generatedUuid)) found = true;
		} while (!found);

		// when the weapon is registered in the system but not tagged with an uuid
		Weapon weaponV3 = new Weapon(generatedUuid, weaponV2);

		weapons.put(generatedUuid, weaponV3);

		return weaponV3;
	}

	public void clear() {
		weapons.clear();
	}

	public Map<UUID, Weapon> getWeapons() {
		return Collections.unmodifiableMap(weapons);
	}

}
