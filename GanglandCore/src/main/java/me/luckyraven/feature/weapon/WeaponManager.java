package me.luckyraven.feature.weapon;

import me.luckyraven.Gangland;
import me.luckyraven.file.configuration.weapon.WeaponAddon;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class WeaponManager {

	private final Map<UUID, Weapon> weapons;
	private final WeaponAddon       weaponAddon;

	public WeaponManager(Gangland gangland) {
		this.weaponAddon = gangland.getInitializer().getWeaponAddon();
		this.weapons     = new HashMap<>();

		init();
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

	public Map<UUID, Weapon> getWeapons() {
		return Collections.unmodifiableMap(weapons);
	}

	private void init(/*Need Weapons Table*/) {
		// initialize all the weapons from the database
	}

}
