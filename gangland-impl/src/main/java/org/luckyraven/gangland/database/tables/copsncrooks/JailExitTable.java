package org.luckyraven.gangland.database.tables.copsncrooks;

import org.bukkit.Location;
import org.luckyraven.gangland.copsncrooks.jail.JailExit;
import org.luckyraven.keystone.persistence.database.component.Attribute;
import org.luckyraven.keystone.persistence.database.component.Table;

import java.sql.Types;
import java.util.Map;
import java.util.Objects;

/**
 * Single source of truth for every configurable jail exit — holds both {@link JailExit.Scope#GLOBAL} (a single
 * universal fallback) and {@link JailExit.Scope#SPECIFIC} (one per jail id) rows. The {@code scope} column
 * discriminates them; {@code jail_id} is populated only for {@code SPECIFIC} rows and is a soft reference to the
 * {@code jail} table — no FK is declared here so a stale {@code jail_id} doesn't block writes (a missing jail just
 * means that row is orphaned; the release pipeline simply falls through to the next source).
 */
public class JailExitTable extends Table<JailExit> {

	/**
	 * Reserved row identifier used for the single {@link JailExit.Scope#GLOBAL} row. Never collides with a real jail id
	 * (which always start at 1 via {@code JailService.ID}).
	 */
	public static final int GLOBAL_ROW_ID = -1;

	public JailExitTable() {
		super("jail_exit");

		Attribute<Integer> rowId  = new Attribute<>("row_id", true, Integer.class);
		Attribute<String>  scope  = new Attribute<>("scope", false, String.class);
		Attribute<Integer> jailId = new Attribute<>("jail_id", false, Integer.class);
		Attribute<String>  world  = new Attribute<>("world", false, String.class);
		Attribute<Double>  x      = new Attribute<>("x", false, Double.class);
		Attribute<Double>  y      = new Attribute<>("y", false, Double.class);
		Attribute<Double>  z      = new Attribute<>("z", false, Double.class);
		Attribute<Float>   yaw    = new Attribute<>("yaw", false, Float.class);
		Attribute<Float>   pitch  = new Attribute<>("pitch", false, Float.class);

		jailId.setCanBeNull(true);

		this.addAttribute(rowId);
		this.addAttribute(scope);
		this.addAttribute(jailId);
		this.addAttribute(world);
		this.addAttribute(x);
		this.addAttribute(y);
		this.addAttribute(z);
		this.addAttribute(yaw);
		this.addAttribute(pitch);
	}

	@Override
	public Object[] getData(JailExit data) {
		Location location = data.getLocation();
		int      rowId    = data.isGlobal() ? GLOBAL_ROW_ID : Objects.requireNonNull(data.getJailId());
		return new Object[]{rowId, data.getScope().name(), data.getJailId(),
		                    Objects.requireNonNull(location.getWorld()).getName(),
		                    location.getX(), location.getY(), location.getZ(),
		                    location.getYaw(), location.getPitch()};
	}

	@Override
	public Map<String, Object> searchCriteria(JailExit data) {
		int rowId = data.isGlobal() ? GLOBAL_ROW_ID : Objects.requireNonNull(data.getJailId());
		return createSearchCriteria("row_id = ?", new Object[]{rowId}, new int[]{Types.INTEGER}, new int[]{0});
	}
}
