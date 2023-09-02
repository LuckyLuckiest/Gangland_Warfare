package me.luckyraven.data.teleportation;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

@Getter
public class Waypoint {

	private static int ID = 0;

	private final String           name;
	private final WaypointTeleport waypointTeleport;

	private double x, y, z;
	private float yaw, pitch;
	private String world;

	private @Setter WaypointType type;
	private @Setter int          gangId, timer, cooldown, shield;
	private @Setter double cost, radius;
	private @Setter int usedId;

	public Waypoint(String name) {
		this.name = name;
		this.waypointTeleport = new WaypointTeleport(this);
		this.x = this.y = this.z = 0D;
		this.yaw = this.pitch = 0F;
		this.world = "";
		this.type = WaypointType.SAFE_ZONE;
		this.gangId = -1;
		this.timer = this.cooldown = this.shield = 0;
		this.cost = this.radius = 0D;
		this.usedId = ++ID;
	}

	protected static void setID(int id) {
		Waypoint.ID = id;
	}

	public void setCoordinates(String world, double x, double y, double z, float yaw, float pitch) {
		this.world = world;
		this.x = x;
		this.y = y;
		this.z = z;
		this.yaw = yaw;
		this.pitch = pitch;
	}

	@Nullable
	public Location getLocation() {
		World world = Bukkit.getWorld(this.world);

		if (world == null) return null;

		return new Location(world, x, y, z, yaw, pitch);
	}

	public boolean match(int id) {
		return id == usedId;
	}

	public boolean forGang() {
		return gangId != -1;
	}

	@Override
	public String toString() {
		return String.format(
				"{id=%d,name=%s,x=%.2f,y=%.2f,z=%.2f,yaw=%.2f,pitch=%.2f,world=%s,type=%s,gangId=%d,cooldown=%d,shield=%d,cost=%.2f,radius=%.2f}",
				usedId, name, x, y, z, yaw, pitch, world, type.getName(), gangId, cooldown, shield, cost, radius);
	}

	@Getter
	public enum WaypointType {

		SPAWN(true), GANG(true), QUEST(false), SAFE_ZONE(true), GLOBAL(false);

		private final boolean safe;

		WaypointType(boolean safe) {
			this.safe = safe;
		}

		public String getName() {
			return this.name().toLowerCase();
		}
	}

}
