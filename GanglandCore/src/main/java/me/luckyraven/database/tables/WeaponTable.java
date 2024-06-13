package me.luckyraven.database.tables;

import me.luckyraven.database.component.Attribute;
import me.luckyraven.database.component.Table;
import me.luckyraven.feature.weapon.Weapon;

import java.sql.Types;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class WeaponTable extends Table<Weapon> {

	public WeaponTable() {
		super("weapon");

		Attribute<UUID>   uuid = new Attribute<>("uuid", true, UUID.class);
		Attribute<String> type = new Attribute<>("type", false, String.class);

		this.addAttribute(uuid);
		this.addAttribute(type);
	}

	@Override
	public Object[] getData(Weapon data) {
		return new Object[]{data.getUuid(), data.getCategory().name()};
	}

	@Override
	public Map<String, Object> searchCriteria(Weapon data) {
		Map<String, Object> search = new HashMap<>();

		search.put("search", "uuid = ?");
		search.put("info", new Object[]{data.getUuid()});
		search.put("type", new int[]{Types.CHAR});
		search.put("index", new int[]{0});

		return Collections.unmodifiableMap(search);
	}
}
