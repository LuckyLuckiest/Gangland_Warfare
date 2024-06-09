package me.luckyraven.feature.weapon;

import me.luckyraven.Gangland;
import me.luckyraven.database.DatabaseHandler;
import me.luckyraven.database.sub.GanglandDatabase;
import me.luckyraven.database.tables.WeaponTable;
import me.luckyraven.file.configuration.weapon.WeaponAddon;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.util.*;

public class WeaponManager {

	private final Map<UUID, Weapon> weapons;
	private final WeaponAddon       weaponAddon;

	public WeaponManager(Gangland gangland) {
		this.weaponAddon = gangland.getInitializer().getWeaponAddon();
		this.weapons     = new HashMap<>();

		// initialize the weapons map
		gangland.getInitializer()
				.getDatabaseManager()
				.getDatabases()
				.stream()
				.filter(database -> database instanceof GanglandDatabase)
				.map(database -> (GanglandDatabase) database)
				.flatMap(ganglandDatabase -> ganglandDatabase.getTables()
															 .stream()
															 .filter(table -> table instanceof WeaponTable)
															 .map(table -> new AbstractMap.SimpleEntry<>(
																	 ganglandDatabase, (WeaponTable) table)))
				.findFirst()
				.ifPresent(entry -> init(entry.getKey(), entry.getValue()));
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

	private void init(DatabaseHandler databaseHandler, WeaponTable table) {
		// initialize all the weapons from the database
		try {
			List<Object[]> data = databaseHandler.getDatabase().selectAll();

		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

}
