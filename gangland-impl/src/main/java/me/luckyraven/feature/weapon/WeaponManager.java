package me.luckyraven.feature.weapon;

import me.luckyraven.Gangland;
import me.luckyraven.database.DatabaseHelper;
import me.luckyraven.database.tables.WeaponTable;
import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.WeaponService;
import me.luckyraven.weapon.configuration.WeaponAddon;

import java.util.List;
import java.util.UUID;

public class WeaponManager extends WeaponService {

	private final Gangland gangland;

	public WeaponManager(Gangland gangland, WeaponAddon weaponAddon) {
		super(weaponAddon);

		this.gangland = gangland;
	}

	public void initialize(WeaponTable table) {
		DatabaseHelper helper = new DatabaseHelper(gangland, gangland.getInitializer().getGanglandDatabase());

		helper.runQueries(database -> {
			List<Object[]> data = database.table(table.getName()).selectAll();

			for (Object[] result : data) {
				UUID   uuid = UUID.fromString(String.valueOf(result[0]));
				String type = String.valueOf(result[1]);

				Weapon weaponAddon = gangland.getInitializer().getWeaponAddon().getWeapon(type);

				if (weaponAddon == null) continue;

				Weapon weapon = new Weapon(uuid, weaponAddon);

				getWeapons().put(uuid, weapon);
			}
		});
	}

}
