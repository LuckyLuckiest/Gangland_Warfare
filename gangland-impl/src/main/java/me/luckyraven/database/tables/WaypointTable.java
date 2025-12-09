package me.luckyraven.database.tables;

import me.luckyraven.data.teleportation.Waypoint;
import me.luckyraven.database.component.Attribute;
import me.luckyraven.database.component.Table;

import java.sql.Types;
import java.util.Map;

public class WaypointTable extends Table<Waypoint> {

	public WaypointTable(GangTable gangTable) {
		super("waypoint");

		Attribute<Integer> id       = new Attribute<>("id", true, Integer.class);
		Attribute<Integer> gangId   = new Attribute<>("gang_id", false, Integer.class);
		Attribute<String>  name     = new Attribute<>("name", false, String.class);
		Attribute<String>  world    = new Attribute<>("world", false, String.class);
		Attribute<Double>  x        = new Attribute<>("x", false, Double.class);
		Attribute<Double>  y        = new Attribute<>("y", false, Double.class);
		Attribute<Double>  z        = new Attribute<>("z", false, Double.class);
		Attribute<Float>   yaw      = new Attribute<>("yaw", false, Float.class);
		Attribute<Float>   pitch    = new Attribute<>("pitch", false, Float.class);
		Attribute<String>  type     = new Attribute<>("type", false, String.class);
		Attribute<Integer> shield   = new Attribute<>("shield", false, Integer.class);
		Attribute<Integer> timer    = new Attribute<>("timer", false, Integer.class);
		Attribute<Integer> cooldown = new Attribute<>("cooldown", false, Integer.class);
		Attribute<Double>  cost     = new Attribute<>("cost", false, Double.class);
		Attribute<Double>  radius   = new Attribute<>("radius", false, Double.class);

		gangId.setCanBeNull(true);
		x.setDefaultValue(0D);
		y.setDefaultValue(0D);
		z.setDefaultValue(0D);
		yaw.setDefaultValue(0F);
		pitch.setDefaultValue(0F);
		shield.setDefaultValue(0);
		timer.setDefaultValue(0);
		cooldown.setDefaultValue(0);
		cost.setDefaultValue(0D);
		radius.setDefaultValue(0D);

		gangId.setForeignKey(gangTable.get("id"), gangTable);

		this.addAttribute(id);
		this.addAttribute(gangId);
		this.addAttribute(name);
		this.addAttribute(world);
		this.addAttribute(x);
		this.addAttribute(y);
		this.addAttribute(z);
		this.addAttribute(yaw);
		this.addAttribute(pitch);
		this.addAttribute(type);
		this.addAttribute(shield);
		this.addAttribute(timer);
		this.addAttribute(cooldown);
		this.addAttribute(cost);
		this.addAttribute(radius);
	}

	@Override
	public Object[] getData(Waypoint data) {
		return new Object[]{data.getUsedId(), data.getGangId(), data.getName(), data.getWorld(), data.getX(),
							data.getY(), data.getZ(), (double) data.getYaw(), (double) data.getPitch(),
							data.getType().getName(), data.getShield(), data.getTimer(), data.getCooldown(),
							data.getCost(), data.getRadius()};
	}

	@Override
	public Map<String, Object> searchCriteria(Waypoint data) {
		return createSearchCriteria("id = ?", new Object[]{data.getUsedId()}, new int[]{Types.INTEGER}, new int[]{0});
	}
}
