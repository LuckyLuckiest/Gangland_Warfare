package me.luckyraven.database.tables;

import me.luckyraven.copsncrooks.detainment.jail.Jail;
import me.luckyraven.persistence.database.component.Attribute;
import me.luckyraven.persistence.database.component.Table;
import org.bukkit.Location;

import java.sql.Types;
import java.util.Map;

public class JailTable extends Table<Jail> {

	public JailTable() {
		super("jail");

		Attribute<Integer> id = new Attribute<>("id", true, Integer.class);
		Attribute<Integer> x  = new Attribute<>("x", false, Integer.class);
		Attribute<Integer> y  = new Attribute<>("y", false, Integer.class);
		Attribute<Integer> z  = new Attribute<>("z", false, Integer.class);

		this.addAttribute(id);
		this.addAttribute(x);
		this.addAttribute(y);
		this.addAttribute(z);
	}

	@Override
	public Object[] getData(Jail data) {
		Location location = data.getLocation();

		int x = location.getBlockX();
		int y = location.getBlockY();
		int z = location.getBlockZ();

		return new Object[]{data.getId(), x, y, z};
	}

	@Override
	public Map<String, Object> searchCriteria(Jail data) {
		return createSearchCriteria("id = ?", new Object[]{data.getId()}, new int[]{Types.INTEGER}, new int[]{0});
	}
}
