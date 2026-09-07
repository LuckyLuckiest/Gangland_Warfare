package org.luckyraven.gangland.database.repositories.weapon;

import lombok.Setter;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.database.tables.weapon.WeaponTable;
import org.luckyraven.keystone.persistence.database.DatabaseHandler;
import org.luckyraven.keystone.persistence.database.backend.DatabaseBackend;
import org.luckyraven.keystone.persistence.database.DatabaseHelper;
import org.luckyraven.keystone.persistence.database.component.Table;
import org.luckyraven.keystone.persistence.repository.AbstractRepository;
import org.luckyraven.keystone.persistence.repository.Repository;
import org.luckyraven.gangland.weapon.Weapon;
import org.luckyraven.gangland.weapon.configuration.WeaponAddon;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@Repository(Weapon.class)
public class WeaponRepository extends AbstractRepository<Weapon> {

	private final WeaponTable weaponTable;

	@Setter
	private WeaponAddon weaponAddon;

	public WeaponRepository(JavaPlugin plugin, DatabaseHandler databaseHandler, DatabaseBackend backend) {
		super(plugin, databaseHandler, backend);

		this.weaponTable = new WeaponTable();
	}

	/**
	 * Deletes every row from the weapon table (full-table clear).
	 */
	public void deleteAll() {
		DatabaseHelper helper = new DatabaseHelper(getPlugin(), getDatabaseHandler());
		helper.runQueriesAsync(database -> tableBackend().delete(null));
	}

	@Override
	protected Collection<Weapon> doLoadAll() throws SQLException {
		List<Weapon>   weapons = new ArrayList<>();
		List<Object[]> data    = tableBackend().selectAll();

		for (Object[] result : data) {
			UUID   uuid = UUID.fromString(String.valueOf(result[0]));
			String type = String.valueOf(result[1]);

			if (weaponAddon == null) continue;

			Weapon template = weaponAddon.getWeapon(type);

			if (template == null) continue;

			weapons.add(template.copyWithUUID(uuid));
		}

		return weapons;
	}

	@Override
	protected <E> Consumer<E> processSave() {
		return null;
	}

	@Override
	protected Table<Weapon> getTable() {
		return weaponTable;
	}

	@Override
	protected void doDelete(Weapon data) throws SQLException {
		tableBackend().delete("uuid = ?", data.getUuid().toString());
	}
}
