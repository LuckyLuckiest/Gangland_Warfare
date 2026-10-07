package org.luckyraven.gangland.copsncrooks.database;

import org.luckyraven.gangland.copsncrooks.place.AdminRegion;
import org.luckyraven.keystone.persistence.database.component.Attribute;
import org.luckyraven.keystone.persistence.database.component.Table;

import java.sql.Types;
import java.util.Map;

/** {@code cop_region}: one row per admin-drawn region; {@code tags} is comma-separated, lowercase. */
public class AdminRegionTable extends Table<AdminRegion> {

	public AdminRegionTable() {
		super("cop_region");

		this.addAttribute(new Attribute<>("id", true, Integer.class));
		this.addAttribute(new Attribute<>("name", false, String.class));
		this.addAttribute(new Attribute<>("world", false, String.class));
		this.addAttribute(new Attribute<>("min_x", false, Integer.class));
		this.addAttribute(new Attribute<>("min_y", false, Integer.class));
		this.addAttribute(new Attribute<>("min_z", false, Integer.class));
		this.addAttribute(new Attribute<>("max_x", false, Integer.class));
		this.addAttribute(new Attribute<>("max_y", false, Integer.class));
		this.addAttribute(new Attribute<>("max_z", false, Integer.class));
		this.addAttribute(new Attribute<>("tags", false, String.class));
	}

	@Override
	public Object[] getData(AdminRegion data) {
		return new Object[]{data.getId(), data.getName(), data.getWorld(), data.getMinX(), data.getMinY(),
		                    data.getMinZ(), data.getMaxX(), data.getMaxY(), data.getMaxZ(),
		                    String.join(",", data.getTags())};
	}

	@Override
	public Map<String, Object> searchCriteria(AdminRegion data) {
		return createSearchCriteria("id = ?", new Object[]{data.getId()}, new int[]{Types.INTEGER}, new int[]{0});
	}
}
