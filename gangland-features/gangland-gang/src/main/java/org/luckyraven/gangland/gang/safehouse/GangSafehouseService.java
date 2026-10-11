package org.luckyraven.gangland.gang.safehouse;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.data.teleportation.IllegalTeleportException;
import org.luckyraven.gangland.data.teleportation.Waypoint;
import org.luckyraven.gangland.data.teleportation.WaypointTeleport;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.GangWorldPolicy;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.gangland.gang.file.GangRules;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.permission.GangPermissions;
import org.luckyraven.keystone.bean.BeanLifecycle;
import org.luckyraven.keystone.economy.Currency;
import org.luckyraven.keystone.persistence.repository.IRepository;
import org.luckyraven.keystone.timer.CountdownTimer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.LongSupplier;

/**
 * A gang's safehouses and rally calls (GP-26, GP-27, GP-42).
 *
 * <p>Rows live in memory, keyed by gang and lower-cased name, and are written through the safehouse repository (set and
 * remove save at once; autosave covers the rest). A teleport builds a transient {@link Waypoint} that is never
 * registered in the waypoint manager and hands it to {@link WaypointTeleport}, which fires {@code TeleportEvent}; the
 * turf guard and the combat tag veto through that event. The waypoint's shield is {@code 0}: a safehouse arrival grants
 * no invulnerability.
 */
public class GangSafehouseService implements BeanLifecycle {

	/** The teleport engine; {@link WaypointTeleport} in production, a recorder in tests. */
	@FunctionalInterface
	public interface Teleporter {

		CompletableFuture<WaypointTeleport.TeleportResult> teleport(JavaPlugin plugin, User<Player> user,
		                                                            Waypoint waypoint,
		                                                            BiConsumer<User<Player>, CountdownTimer> during)
				throws IllegalTeleportException;
	}

	/** An open rally call: where members are called to, who was asked, and when the answer window closes. */
	private record Call(Safehouse spot, String where, long expiresAt, Set<UUID> invited) { }

	private final JavaPlugin                           plugin;
	private final IRepository<Safehouse>               repository;
	private final UserManager<Player>                  users;
	private final MemberManager                        members;
	private final GangManager                          gangs;
	private final GangRules                            rules;
	private final GangMessages                         messages;
	private final GangWorldPolicy                      worldPolicy;
	private final LongSupplier                         clock;
	private final Teleporter                           teleporter;
	private final Map<Integer, Map<String, Safehouse>> byGang = new HashMap<>();
	private final Map<Integer, Call>                   calls  = new HashMap<>();

	private boolean loaded;

	public GangSafehouseService(JavaPlugin plugin, IRepository<Safehouse> repository, UserManager<Player> users,
	                            MemberManager members, GangManager gangs, GangRules rules, GangMessages messages,
	                            GangWorldPolicy worldPolicy) {
		this(plugin, repository, users, members, gangs, rules, messages, worldPolicy, System::currentTimeMillis,
		     (pl, user, waypoint, during) -> waypoint.getWaypointTeleport().teleport(pl, user, during));
	}

	public GangSafehouseService(JavaPlugin plugin, IRepository<Safehouse> repository, UserManager<Player> users,
	                            MemberManager members, GangManager gangs, GangRules rules, GangMessages messages,
	                            GangWorldPolicy worldPolicy, LongSupplier clock, Teleporter teleporter) {
		this.plugin      = plugin;
		this.repository  = repository;
		this.users       = users;
		this.members     = members;
		this.gangs       = gangs;
		this.rules       = rules;
		this.messages    = messages;
		this.worldPolicy = worldPolicy;
		this.clock       = clock;
		this.teleporter  = teleporter;
	}

	@Override
	public void onInitialize(boolean firstLoad) {
		if (firstLoad && loaded) return;

		loaded = true;
		for (Safehouse safehouse : repository.loadAll()) {
			byGang.computeIfAbsent(safehouse.gangId(), id -> new LinkedHashMap<>())
			      .put(key(safehouse.name()), safehouse);
		}
		repository.setDataSupplier(this::all);
	}

	@Override
	public void onClear() {
		byGang.clear();
		calls.clear();
	}

	// ---------------------------------------------------------------- rows

	public int count(int gangId) {
		return byGang.getOrDefault(gangId, Map.of()).size();
	}

	public Optional<Safehouse> find(int gangId, String name) {
		return Optional.ofNullable(byGang.getOrDefault(gangId, Map.of()).get(key(name)));
	}

	public List<Safehouse> list(int gangId) {
		return List.copyOf(byGang.getOrDefault(gangId, Map.of()).values());
	}

	/** Saves a safehouse, replacing a same-named one of the same gang. */
	public void add(Safehouse safehouse) {
		Safehouse stored = new Safehouse(safehouse.gangId(), key(safehouse.name()), safehouse.world(), safehouse.x(),
		                                 safehouse.y(), safehouse.z(), safehouse.yaw(), safehouse.pitch());
		byGang.computeIfAbsent(stored.gangId(), id -> new LinkedHashMap<>()).put(stored.name(), stored);
		repository.save(stored);
	}

	public void remove(int gangId, String name) {
		Map<String, Safehouse> rows = byGang.get(gangId);
		Safehouse              gone = rows == null ? null : rows.remove(key(name));
		if (gone != null) repository.delete(gone);
	}

	/** Drops every safehouse and open rally of a disbanded gang. */
	public void removeAll(int gangId) {
		calls.remove(gangId);
		Map<String, Safehouse> rows = byGang.remove(gangId);
		if (rows != null) rows.values().forEach(repository::delete);
	}

	private Collection<Safehouse> all() {
		List<Safehouse> everything = new ArrayList<>();
		byGang.values().forEach(rows -> everything.addAll(rows.values()));
		return everything;
	}

	static String key(String name) {
		return name.toLowerCase(Locale.ROOT);
	}

	// ---------------------------------------------------------------- what the commands ask

	public GangRules rules() {
		return rules;
	}

	/** The gang id of {@code player}, or {@code -1} when they have none. */
	public int gangIdOf(Player player) {
		User<Player> user = users.getUser(player);
		return user == null || !user.hasGang() ? -1 : user.getGangId();
	}

	/** The rank gate: the server node, the rank's own nodes or the top rank (see {@link GangPermissions#allows}). */
	public boolean allows(Player player, String node) {
		return GangPermissions.allows(members.getMember(player.getUniqueId()), player, node);
	}

	/**
	 * The gate every safehouse verb passes first: the player has a gang, safehouses are on, gangs exist in their world
	 * and their rank holds {@code node}.
	 *
	 * @return the gang id, or {@code -1} after telling the player why not
	 */
	public int gate(Player player, String node) {
		int gangId = gangIdOf(player);
		if (gangId < 0) {
			player.sendMessage(Messages.MUST_CREATE_GANG.toString());
		} else if (!worldPolicy.enabledIn(player.getWorld())) {
			player.sendMessage(text("Safehouse.Disabled_World", "&cGangs are disabled in this world."));
		} else if (!rules.isSafehouseEnabled()) {
			player.sendMessage(text("Safehouse.Off", "&cSafehouses are turned off."));
		} else if (!allows(player, node)) {
			player.sendMessage(Messages.COMMAND_NO_PERM.toString());
		} else {
			return gangId;
		}
		return -1;
	}

	public String text(String key, String fallback, Object... pairs) {
		return messages.text(key, fallback, pairs);
	}

	// ---------------------------------------------------------------- teleport

	/** The transient GANG waypoint a safehouse teleport hands to {@link WaypointTeleport}; shield is always 0. */
	public Waypoint waypoint(Safehouse safehouse, double cost) {
		Waypoint waypoint = new Waypoint(safehouse.name(), "gangland.gang");
		waypoint.setType(Waypoint.WaypointType.GANG);
		waypoint.setGangId(safehouse.gangId());
		waypoint.setTimer(rules.getSafehouseWarmUpSeconds());
		waypoint.setCooldown(rules.getSafehouseCooldownSeconds());
		waypoint.setShield(0);
		waypoint.setCost(cost);
		waypoint.setCoordinates(safehouse.world(), safehouse.x(), safehouse.y(), safehouse.z(), safehouse.yaw(),
		                        safehouse.pitch());
		return waypoint;
	}

	/** @return {@code true} when the warm-up started; refusal lines are sent inside. */
	public boolean teleport(Player player, Safehouse safehouse) {
		return arrive(player, safehouse, rules.getSafehouseCost());
	}

	private boolean arrive(Player player, Safehouse spot, double cost) {
		if (!rules.isSafehouseEnabled()) {
			player.sendMessage(text("Safehouse.Off", "&cSafehouses are turned off."));
			return false;
		}

		World target = Bukkit.getWorld(spot.world());
		if (!worldPolicy.enabledIn(player.getWorld()) || (target != null && !worldPolicy.enabledIn(target))) {
			player.sendMessage(text("Safehouse.Disabled_World", "&cGangs are disabled in this world."));
			return false;
		}

		if (rules.isSafehouseSafeCheck() && target != null &&
		    !SafeSpot.isSafe(new Location(target, spot.x(), spot.y(), spot.z()))) {
			player.sendMessage(text("Safehouse.Unsafe", "&c%name% is not safe to stand on.", "name", spot.name()));
			return false;
		}

		User<Player> user = users.getUser(player);
		if (user == null) return false;

		if (cost > 0D && user.getEconomy().getAmount().compareTo(Currency.of(cost)) < 0) {
			player.sendMessage(text("Safehouse.Too_Poor", "&cA safehouse teleport costs %cost%.", "cost", cost));
			return false;
		}

		Waypoint waypoint = waypoint(spot, cost);
		try {
			teleporter.teleport(plugin, user, waypoint, (u, timer) -> player.sendMessage(
					text("Safehouse.Warm_Up", "&7Teleporting in %seconds%s. Do not move.", "seconds",
					     timer.getTimeLeft()))).thenAccept(result -> {
				if (result.success()) {
					if (cost > 0D) user.getEconomy().withdrawAmount(Currency.of(cost));
					player.sendMessage(text("Safehouse.Arrived", "&aYou arrived at %name%.", "name", spot.name()));
				} else {
					player.sendMessage(text("Safehouse.Failed", "&cYou could not teleport to %name%.", "name",
					                        spot.name()));
				}
			});
		} catch (IllegalTeleportException exception) {
			CountdownTimer cooldown = WaypointTeleport.getCooldownTimer(player);
			player.sendMessage(text("Safehouse.Cooldown", "&cYou can teleport again in %seconds%s.", "seconds",
			                        cooldown == null ? 0 : cooldown.getTimeLeft()));
			return false;
		}
		return true;
	}

	// ---------------------------------------------------------------- rally

	/**
	 * Calls the caller's online gang members to {@code target}, or to the caller's own position when it is {@code null}.
	 * The spot is read now, so a caller who walks away does not move the call. A newer call replaces the gang's open
	 * one.
	 *
	 * @return how many members were asked; {@code 0} means nobody was online and no call was opened
	 */
	public int rally(Player caller, int gangId, @Nullable Safehouse target) {
		Gang gang = gangs.getGang(gangId);
		if (gang == null) return 0;

		Set<UUID>          invited = new HashSet<>();
		List<User<Player>> asked   = new ArrayList<>();
		for (User<Player> member : gang.getOnlineMembers(users::getUser)) {
			if (member.getUser().equals(caller)) continue;

			invited.add(member.getUser().getUniqueId());
			asked.add(member);
		}
		if (asked.isEmpty()) return 0;

		Safehouse spot;
		String    where;
		if (target != null) {
			spot  = target;
			where = target.name();
		} else {
			Location at = caller.getLocation().clone();
			World    in = at.getWorld();
			spot  = new Safehouse(gangId, "rally", in == null ? "" : in.getName(), at.getX(), at.getY(), at.getZ(),
			                      at.getYaw(), at.getPitch());
			where = caller.getName();
		}

		int seconds = rules.getSafehouseRallyConsentSeconds();
		calls.put(gangId, new Call(spot, where, clock.getAsLong() + seconds * 1000L, invited));

		String line = text("Safehouse.Rally_Call",
		                   "&6%player% calls the gang to %where%. &7Answer with /glw gang rally join within %seconds%s.",
		                   "player", caller.getName(), "where", where, "seconds", seconds);
		for (User<Player> member : asked) {
			member.sendMessage(line);
		}
		return asked.size();
	}

	/** @return {@code true} when the member's warm-up started; every refusal is told inside */
	public boolean joinRally(Player player) {
		int  gangId = gangIdOf(player);
		Call call   = gangId < 0 ? null : calls.get(gangId);

		boolean expired = call != null && clock.getAsLong() >= call.expiresAt();
		if (expired) calls.remove(gangId);

		if (call == null || expired || !call.invited().contains(player.getUniqueId())) {
			player.sendMessage(text("Safehouse.Rally_Expired", "&cThere is no rally call open for you."));
			return false;
		}

		// the answer is single-use: one join, one teleport
		call.invited().remove(player.getUniqueId());
		// a member answering a call never pays the safehouse fee
		return arrive(player, call.spot(), 0D);
	}
}
