package me.luckyraven.database.repositories.weapon;

import lombok.Setter;
import me.luckyraven.database.tables.weapon.WeaponTable;
import me.luckyraven.persistence.database.Database;
import me.luckyraven.persistence.database.DatabaseHandler;
import me.luckyraven.persistence.database.component.Table;
import me.luckyraven.persistence.repository.AbstractRepository;
import me.luckyraven.persistence.repository.Repository;
import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.configuration.WeaponAddon;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.sql.Types;
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

	public WeaponRepository(JavaPlugin plugin, DatabaseHandler databaseHandler) {
		super(plugin, databaseHandler);

		this.weaponTable = new WeaponTable();
	}

	@Override
	protected Collection<Weapon> doLoadAll() throws SQLException {
		List<Weapon>   weapons = new ArrayList<>();
		List<Object[]> data    = getDatabase().table(weaponTable.getName()).selectAll();

		for (Object[] result : data) {
			UUID   uuid = UUID.fromString(String.valueOf(result[0]));
			String type = String.valueOf(result[1]);

			if (weaponAddon == null) continue;

			Weapon template = weaponAddon.getWeapon(type);

			if (template == null) continue;

			weapons.add(new Weapon(uuid, template));
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
		Database table = getDatabase().table(weaponTable.getName());
		table.delete("uuid", data.getUuid().toString(), Types.VARCHAR);
	}
}
