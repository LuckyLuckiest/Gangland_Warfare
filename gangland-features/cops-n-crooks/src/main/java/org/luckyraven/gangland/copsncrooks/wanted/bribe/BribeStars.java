package org.luckyraven.gangland.copsncrooks.wanted.bribe;

import com.cryptomorin.xseries.XMaterial;
import lombok.CustomLog;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.place.SetupPoint;
import org.luckyraven.gangland.copsncrooks.place.SetupPointRegistry;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.copsncrooks.wanted.config.BribeStarSettings;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.core.wanted.Wanted;
import org.luckyraven.gangland.core.wanted.WantedCause;
import org.luckyraven.keystone.bean.BeanLifecycle;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Police bribe stars (CONTRACTS C14): each admin {@code pickup} setup point shows a floating item; a wanted player whom no cop
 * has seen within {@code Lost_Sight_Seconds} pockets it for {@code Stars} wanted stars. A taken item returns after
 * {@code Respawn_Seconds} (in memory, so a restart respawns every star at once), and so does one that vanished in a loaded
 * chunk without a take (a hopper is cancelled by {@code BribeStarListener}, but a plugin or {@code /kill} may still remove
 * it); one unloaded with its chunk returns at once when the chunk loads again.
 *
 * @since 0.16.0
 */
@CustomLog
public class BribeStars implements BeanLifecycle {

	private static final long PERIOD_TICKS  = 10L;
	private static final long TOLD_EVERY_MS = 5_000L;

	/** A pickup point's live item and when a taken one is due back. */
	private static final class Slot {

		Item item;
		long respawnAt;
	}

	private final JavaPlugin                plugin;
	private final SetupPointRegistry        points;
	private final UserManager<Player>       users;
	private final Supplier<BribeStarSettings> settings;
	private final IntSupplier               lostSightSeconds;
	private final WantedMessages            messages;
	private final CopManager                copManager;
	private final LongSupplier              clock;

	private final Map<Integer, Slot> slots = new HashMap<>();
	private final Map<UUID, Long>    told  = new HashMap<>();

	private BukkitTask task;
	private boolean    warnedMaterial;

	public BribeStars(JavaPlugin plugin, SetupPointRegistry points, UserManager<Player> users,
	                  Supplier<BribeStarSettings> settings, IntSupplier lostSightSeconds, WantedMessages messages,
	                  CopManager copManager, LongSupplier clock) {
		this.plugin           = plugin;
		this.points           = points;
		this.users            = users;
		this.settings         = settings;
		this.lostSightSeconds = lostSightSeconds;
		this.messages         = messages;
		this.copManager       = copManager;
		this.clock            = clock;
	}

	@Override
	public void onInitialize(boolean firstLoad) {
		if (task != null || !config().enabled()) return;
		task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, PERIOD_TICKS, PERIOD_TICKS);
	}

	@Override
	public void onClear() {
		if (task != null) {
			task.cancel();
			task = null;
		}
		removeItems();
		told.clear();
	}

	/** One pass over every pickup point: respawn what is due, then let a player take what is in reach. */
	public void tick() {
		BribeStarSettings config = config();
		if (!config.enabled()) {
			removeItems();
			return;
		}

		long now = clock.getAsLong();
		// an entry this old is as good as none: drop it so a player who walked away does not stay in the map
		told.values().removeIf(last -> now - last >= TOLD_EVERY_MS);

		List<SetupPoint> pickups = points.ofKind(SetupPoint.PICKUP);
		Set<Integer>     live    = new HashSet<>();
		for (SetupPoint point : pickups) live.add(point.getId());
		// a removed point takes its star with it, so an id reused later starts clean
		slots.entrySet().removeIf(entry -> {
			if (live.contains(entry.getKey())) return false;
			if (entry.getValue().item != null) entry.getValue().item.remove();
			return true;
		});

		for (SetupPoint point : pickups) {
			Location at = point.getLocation();
			if (at == null || at.getWorld() == null) continue;

			Slot slot = slots.computeIfAbsent(point.getId(), id -> new Slot());
			if (!at.getWorld().isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) {
				// unloaded with its chunk (setPersistent false), not taken: back at once when the chunk loads
				if (slot.item != null && !slot.item.isValid()) slot.item = null;
				continue;
			}

			// an id reused at another spot, or a star pushed off its point: serve a fresh one at the point
			if (slot.item != null && slot.item.isValid() && !near(slot.item.getLocation(), at)) {
				slot.item.remove();
				slot.item = null;
			}
			if (slot.item != null && !slot.item.isValid()) {
				// vanished in a loaded chunk without a take: no instant re-drop, or a hopper farms it
				slot.item      = null;
				slot.respawnAt = now + config.respawnSeconds() * 1000L;
			}
			if (slot.item == null) {
				if (now < slot.respawnAt) continue;
				slot.item = drop(at, config.item());
				if (slot.item == null) continue;
			}

			slot.item.setTicksLived(1);   // the 5 minute despawn clock never runs out while the point is being served
			tryTake(slot, config, now);
		}
	}

	private void tryTake(Slot slot, BribeStarSettings config, long now) {
		Location at = slot.item.getLocation();
		World    world = at.getWorld();
		if (world == null) return;

		double radius = config.pickupRadius();
		for (Entity entity : world.getNearbyEntities(at, radius, radius, radius)) {
			if (!(entity instanceof Player player)) continue;

			User<Player> user = users.getUser(player);
			Wanted       wanted = user == null ? null : user.getWanted();
			if (wanted == null || !wanted.isWanted()) continue;

			if (seen(player.getUniqueId())) {
				Long last = told.get(player.getUniqueId());
				if (last == null || now - last >= TOLD_EVERY_MS) {
					told.put(player.getUniqueId(), now);
					player.sendMessage(messages.format(WantedMessages.Key.BRIBE_STAR_SEEN, Map.of()));
				}
				continue;
			}

			int taken = Math.min(config.stars(), wanted.getLevel());
			wanted.setLevel(Math.max(0, wanted.getLevel() - config.stars()), WantedCause.CONTACT);
			slot.item.remove();
			slot.item      = null;
			slot.respawnAt = now + config.respawnSeconds() * 1000L;
			player.sendMessage(messages.format(WantedMessages.Key.BRIBE_STAR_TAKEN, Map.of("stars", String.valueOf(taken))));
			return;
		}
	}

	/** True for a live bribe-star item (hoppers must not collect it). */
	public boolean isStar(Item item) {
		for (Slot slot : slots.values()) {
			if (item.equals(slot.item)) return true;
		}
		return false;
	}

	private static boolean near(Location item, Location point) {
		return item != null && item.getWorld() == point.getWorld() && item.distanceSquared(point) <= 1.0;
	}

	/** A squad has a sighting younger than {@code Lost_Sight_Seconds} (Ruling R31). */
	private boolean seen(UUID id) {
		CopGroup group = copManager.groupOf(id);
		return group != null && group.getSquad().millisSinceSighting() < lostSightSeconds.getAsInt() * 1000L;
	}

	/** Drops the floating, unpickable item; {@code null} when it could not be placed. */
	protected Item drop(Location at, String material) {
		World world = at.getWorld();
		if (world == null) return null;

		XMaterial type = XMaterial.matchXMaterial(material).orElse(null);
		if (type == null || type.parseMaterial() == null) {
			if (!warnedMaterial) {
				warnedMaterial = true;
				log.warn("Wanted.Bribe_Stars.Item '{}' is not a material; using NETHER_STAR", material);
			}
			type = XMaterial.NETHER_STAR;
		}

		ItemStack stack = type.parseItem();
		if (stack == null) return null;

		Item item = world.dropItem(at, stack);
		item.setPickupDelay(Integer.MAX_VALUE);
		item.setGravity(false);
		item.setVelocity(new Vector(0, 0, 0));
		item.setInvulnerable(true);
		item.setPersistent(false);
		return item;
	}

	private void removeItems() {
		for (Slot slot : slots.values()) {
			if (slot.item != null) slot.item.remove();
			slot.item = null;
		}
		slots.clear();
	}

	private BribeStarSettings config() {
		BribeStarSettings config = settings.get();
		return config == null ? BribeStarSettings.DEFAULT : config;
	}
}
