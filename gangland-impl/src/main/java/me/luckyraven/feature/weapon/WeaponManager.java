package me.luckyraven.feature.weapon;

import me.luckyraven.database.DatabaseHandler;
import me.luckyraven.database.DatabaseHelper;
import me.luckyraven.database.tables.WeaponTable;
import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.WeaponService;
import me.luckyraven.weapon.configuration.WeaponAddon;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;

public class WeaponManager extends WeaponService {

	private final JavaPlugin      plugin;
	private final DatabaseHandler databaseHandler;
	private final WeaponAddon     weaponAddon;

	public WeaponManager(JavaPlugin plugin, DatabaseHandler databaseHandler, WeaponAddon weaponAddon) {
		super(weaponAddon);

		this.plugin          = plugin;
		this.databaseHandler = databaseHandler;
		this.weaponAddon     = weaponAddon;
	}

	public void initialize(WeaponTable table) {
		DatabaseHelper helper = new DatabaseHelper(plugin, databaseHandler);

		helper.runQueries(database -> {
			List<Object[]> data = database.table(table.getName()).selectAll();

			for (Object[] result : data) {
				UUID   uuid = UUID.fromString(String.valueOf(result[0]));
				String type = String.valueOf(result[1]);

				Weapon weaponAddon = this.weaponAddon.getWeapon(type);

				if (weaponAddon == null) continue;

				Weapon weapon = new Weapon(uuid, weaponAddon);

				getWeapons().put(uuid, weapon);
			}
		});
	}

}
