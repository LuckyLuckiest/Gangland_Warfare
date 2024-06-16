package me.luckyraven.database.tables;

import me.luckyraven.data.rank.Permission;
import me.luckyraven.database.component.Attribute;
import me.luckyraven.database.component.Table;

import java.sql.Types;
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
		return createSearchCriteria("id = ?", new Object[]{data.getUsedId()}, new int[]{Types.INTEGER}, new int[]{0});
	}
}
