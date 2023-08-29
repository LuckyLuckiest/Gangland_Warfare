package me.luckyraven.data.teleportation;

import lombok.Getter;
import lombok.Setter;
import me.luckyraven.data.user.User;
import me.luckyraven.util.timer.CountdownTimer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

@Getter
public class Waypoint {

	private static final Map<User<Player>, CountdownTimer> teleportCooldown = new HashMap<>();

	private static int ID = 0;

	private final String name;

	private double x, y, z;
	private float yaw, pitch;
	private String world;

	private @Setter WaypointType type;
	private @Setter int          gangId, timer, cooldown, shield;
	private @Setter double cost, radius;
	private @Setter int usedId;

	public Waypoint(String name) {
		this.name = name;
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

	public static boolean userOnCooldown(User<Player> user) {
		return teleportCooldown.containsKey(user);
	}

	public static void removeCooldown(User<Player> user) {
		teleportCooldown.remove(user);
	}

	@Nullable
	public static CountdownTimer getCooldownTimer(User<Player> user) {
		return teleportCooldown.get(user);
	}

	public void setCoordinates(String world, double x, double y, double z, float yaw, float pitch) {
		this.world = world;
		this.x = x;
		this.y = y;
		this.z = z;
		this.yaw = yaw;
		this.pitch = pitch;
	}

	/**
	 * Teleports the user to this waypoint.
	 *
	 * @param plugin      the plugin used
	 * @param user        the user that would be teleported
	 * @param duringTimer access the user and countdown timer during the timer
	 * @return the {@link TeleportResult} using {@link CompletableFuture} of the user
	 * @throws IllegalTeleportException when the user tries to teleport again while having a cooldown
	 */
	public CompletableFuture<TeleportResult> teleport(JavaPlugin plugin, User<Player> user,
	                                                  BiConsumer<User<Player>, CountdownTimer> duringTimer)
			throws IllegalTeleportException {
		if (userOnCooldown(user)) throw new IllegalTeleportException("Can't teleport on a cooldown");

		CompletableFuture<TeleportResult> teleportResult = new CompletableFuture<>();

		CountdownTimer timer = new CountdownTimer(plugin, this.timer == 0 ? 0L : 1L, this.timer, null, t -> {
			if (t.getTimeLeft() == 0) return;

			duringTimer.accept(user, t);
		}, t -> {
			World locWorld = Bukkit.getWorld(world);

			// if locWorld was a valid world
			if (locWorld != null) {
				Location location = new Location(locWorld, x, y, z, yaw, pitch);

				TeleportEvent event = new TeleportEvent(this, user);
				Bukkit.getPluginManager().callEvent(event);

				if (!event.isCancelled()) {
					user.getUser().teleport(location);

					// create a cooldown
					if (this.cooldown != 0) {
						CountdownTimer countdownTimer = new CountdownTimer(plugin, this.cooldown, null, null,
						                                                   cooldownTimer -> teleportCooldown.remove(
								                                                   user));
						teleportCooldown.put(user, countdownTimer);

						countdownTimer.start();
					}

					// successfully teleported
					TeleportResult result = new TeleportResult(true, user, this);
					teleportResult.complete(result);
				}
				// event cancelled teleportation
				else {
					TeleportResult result = new TeleportResult(false, user, this);
					teleportResult.complete(result);
				}
			}
			// when locWorld was not valid
			else {
				TeleportResult result = new TeleportResult(false, user, this);
				teleportResult.complete(result);
			}
		});

		timer.start();

		return teleportResult;
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

	public record TeleportResult(boolean success, User<Player> playerUser, Waypoint waypoint) {}

}
