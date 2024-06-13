package me.luckyraven.database.tables;

import me.luckyraven.data.rank.Permission;
import me.luckyraven.database.component.Attribute;
import me.luckyraven.database.component.Table;

import java.sql.Types;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class PermissionTable extends Table<Permission> {

	public PermissionTable() {
		super("permission");

		Attribute<Integer> id   = new Attribute<>("id", true, Integer.class);
		Attribute<String>  name = new Attribute<>("name", false, String.class);

		this.addAttribute(id);
		this.addAttribute(name);
	}

	@Override
	public Object[] getData(Permission data) {
		return new Object[]{data.getUsedId(), data.getPermission()};
	}

	@Override
	public Map<String, Object> searchCriteria(Permission data) {
		Map<String, Object> search = new HashMap<>();

		search.put("search", "id = ?");
		search.put("info", new Object[]{data.getUsedId()});
		search.put("type", new int[]{Types.INTEGER});
		search.put("index", new int[]{0});

		return Collections.unmodifiableMap(search);
	}
}
