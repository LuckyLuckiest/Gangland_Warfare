package org.luckyraven.gangland.copsncrooks.npc.police.breach;

import com.cryptomorin.xseries.XPotion;
import lombok.CustomLog;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.config.BreachSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopRole;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;
import org.luckyraven.keystone.npc.NpcPost;

import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Stack up and breach (0.21.0): a suspect hidden indoors with a door near his last-known spot gets up to
 * {@code Stack_Size} hunting cops holding the cell outside the door, then the entry (Defender first, the rest
 * {@code Entry_Gap_Ticks} apart, a flashbang first at {@code Flashbang.Min_Stars}). No block is ever changed.
 * Main thread only, like the cop AI tick that drives {@link #tick}.
 *
 * @since 0.21.0
 */
@CustomLog
public class BreachController {

	private static final long SCAN_INTERVAL_MS = 1000L;

	/** What the controller reads from the world; the production one is built on {@link DoorFinder}. */
	public interface Surroundings {

		boolean indoors(Location at, int roofBlocks);

		Optional<DoorFinder.Door> doorNear(Location at, int radius);
	}

	/** Blinds and slows one player for {@code ticks}. */
	@FunctionalInterface
	public interface Flashbang {

		void blind(Player player, int ticks);
	}

	private final Supplier<CopConfigProvider> provider;
	private final CopRadio                    radio;
	private final LongSupplier                clock;
	private final Surroundings                surroundings;
	private final Flashbang                   flashbang;

	private final Map<UUID, Breach> active        = new HashMap<>();
	private final Map<UUID, Long>   lastScan      = new HashMap<>();
	private final Map<UUID, Long>   cooldownUntil = new HashMap<>();

	/** One running stack; the go, entry and abort logic (later chunks) advance it. */
	private static final class Breach {

		final Location      door;
		final Location      cell;
		final List<CopNpc>  stack;
		final long          startedAt;
		final Location      lastKnownAtStart;
		long                goAt = -1L;
		List<CopNpc>        order;
		int                 next;

		Breach(Location door, Location cell, List<CopNpc> stack, long startedAt, Location lastKnownAtStart) {
			this.door             = door;
			this.cell             = cell;
			this.stack            = stack;
			this.startedAt        = startedAt;
			this.lastKnownAtStart = lastKnownAtStart;
		}
	}

	public BreachController(Supplier<CopConfigProvider> provider, CopRadio radio, LongSupplier clock) {
		this(provider, radio, clock, worldSurroundings(), BreachController::applyFlashbang);
	}

	BreachController(Supplier<CopConfigProvider> provider, CopRadio radio, LongSupplier clock,
	                 Surroundings surroundings, Flashbang flashbang) {
		this.provider     = provider;
		this.radio        = radio;
		this.clock        = clock;
		this.surroundings = surroundings;
		this.flashbang    = flashbang;
	}

	/** Per cop AI tick: advances this player's breach, or (at most once a second) looks for one to start. */
	public void tick(Player player, @Nullable CopGroup group) {
		UUID id = player.getUniqueId();
		BreachSettings settings = settings();
		Breach         running  = active.get(id);
		if (running != null) {
			if (group != null) advance(id, running, group, settings);
			return;
		}
		if (group == null) return;
		if (!settings.enabled()) return;
		long now = clock.getAsLong();
		Long last = lastScan.get(id);
		if (last != null && now - last < SCAN_INTERVAL_MS) return;
		lastScan.put(id, now);
		if (group.isStandoff(now)) return;
		if (now < cooldownUntil.getOrDefault(id, 0L)) return;

		Location lastKnown = group.getSquad().lastKnownLocation();
		if (lastKnown == null || lastKnown.getWorld() == null) return;
		if (!surroundings.indoors(lastKnown, settings.roofBlocks())) return;
		Optional<DoorFinder.Door> door = surroundings.doorNear(lastKnown, settings.doorRadius());
		if (door.isEmpty()) return;

		Location cell    = door.get().stackCell();
		List<CopNpc> stack = draft(group, id, cell, settings.stackSize());
		if (stack.isEmpty()) return;
		NpcPost post = new NpcPost(cell, 1.5, door.get().door());
		for (CopNpc cop : stack) {
			// leave the chase state first: its exit stops navigation, which would cancel the post walk
			cop.transitionTo(CopState.POSTED);
			cop.holdPost(post);
		}
		active.put(id, new Breach(door.get().door(), cell, stack, now, lastKnown.clone()));
		radio.sayFromLeader(group, "Stack_Up");
		log.debug("BREACH {} stack={}", player.getName(), stack.size());
	}

	private BreachSettings settings() {
		CopConfigProvider cfg = provider.get();
		return cfg != null && cfg.getBreachSettings() != null ? cfg.getBreachSettings() : BreachSettings.DEFAULT;
	}

	/** Waits for the go, then releases the stack one entrant per Entry_Gap_Ticks; the last release starts the cooldown. */
	private void advance(UUID id, Breach breach, CopGroup group, BreachSettings settings) {
		long now = clock.getAsLong();
		if (breach.goAt < 0) {
			boolean waited = now - breach.startedAt >= settings.stackWaitSeconds() * 1000L;
			if (!waited && !allArrived(breach)) return;
			breach.goAt  = now;
			breach.order = entryOrder(breach.stack, group);
			radio.sayFromLeader(group, "Breach");
			if (group.getLevel() >= settings.flashbangMinStars()) {
				throwFlashbang(breach.door, settings);
				radio.sayFromLeader(group, "Flashbang");
			}
		}
		long gapMs = settings.entryGapTicks() * 50L;
		while (breach.next < breach.order.size() && now >= breach.goAt + breach.next * gapMs) {
			CopNpc cop = breach.order.get(breach.next++);
			if (cop.isValid() && cop.getCurrentState() == CopState.POSTED) {
				cop.releasePost();
				cop.transitionTo(CopState.PURSUING);
			}
		}
		if (breach.next >= breach.order.size()) {
			active.remove(id);
			cooldownUntil.put(id, now + settings.cooldownSeconds() * 1000L);
		}
	}

	/** Flashes every online player within the radius of the door who has an unobstructed line to its centre. */
	private void throwFlashbang(Location door, BreachSettings settings) {
		World world = door.getWorld();
		if (world == null) return;
		Location centre = door.clone().add(0.5, 0.5, 0.5);
		for (Player target : world.getPlayers()) {
			if (!target.isOnline() || target.getWorld() != world) continue;
			if (target.getLocation().distance(centre) > settings.flashbangRadius()) continue;
			Location eye = target.getEyeLocation();
			Vector   to  = centre.toVector().subtract(eye.toVector());
			double   len = to.length();
			if (len > 0 && world.rayTraceBlocks(eye, to.normalize(), len, FluidCollisionMode.NEVER, true) != null) continue;
			flashbang.blind(target, settings.flashbangTicks());
		}
	}

	private static boolean allArrived(Breach breach) {
		for (CopNpc cop : breach.stack) {
			if (!cop.isValid() || cop.getCurrentState() != CopState.POSTED) continue;
			NpcPost post = cop.getPost();
			if (post == null || cop.getEntity() == null || !post.arrived(cop.getEntity().getLocation())) return false;
		}
		return true;
	}

	/** The shield role (by Block_Fraction, not name) first, else the squad leader, else draft order; then the rest. */
	private static List<CopNpc> entryOrder(List<CopNpc> stack, CopGroup group) {
		CopNpc first = null;
		for (CopNpc cop : stack) {
			CopRole role = cop.getRole();
			if (role != null && !role.commander() && !role.medic() && role.blockFraction() > 0) {
				first = cop;
				break;
			}
		}
		if (first == null) {
			for (CopNpc cop : stack) {
				if (cop == group.getSquad().leader()) first = cop;
			}
		}
		if (first == null) first = stack.get(0);
		List<CopNpc> order = new ArrayList<>(stack);
		order.remove(first);
		order.add(0, first);
		return order;
	}

	/** Up to {@code size} live cops hunting {@code playerId}, nearest the stack cell first. */
	private static List<CopNpc> draft(CopGroup group, UUID playerId, Location cell, int size) {
		World world = cell.getWorld();
		List<CopNpc> hunters = new ArrayList<>();
		synchronized (group.getCops()) {
			for (CopNpc cop : group.getCops()) {
				CopState state = cop.getCurrentState();
				if (!cop.isValid() || !playerId.equals(cop.getTargetPlayerId())) continue;
				if (state != CopState.PURSUING && state != CopState.COMBAT) continue;
				if (cop.getEntity() == null || cop.getEntity().getLocation().getWorld() != world) continue;
				hunters.add(cop);
			}
		}
		hunters.sort(Comparator.comparingDouble(c -> c.getEntity().getLocation().distanceSquared(cell)));
		return new ArrayList<>(hunters.subList(0, Math.min(size, hunters.size())));
	}

	public boolean isActive(UUID playerId) {
		return active.containsKey(playerId);
	}

	/** Releases every stack (reload, disable); no cooldown. */
	public void endAll() {
		for (Breach breach : active.values()) {
			for (CopNpc cop : breach.stack) {
				if (cop.getCurrentState() != CopState.POSTED) continue;
				cop.releasePost();
				cop.transitionTo(CopState.PURSUING);
			}
		}
		active.clear();
	}

	/** Blindness and Slowness for {@code ticks} through XPotion. */
	static void applyFlashbang(Player player, int ticks) {
		for (XPotion potion : new XPotion[]{XPotion.BLINDNESS, XPotion.SLOWNESS}) {
			XPotion.of(potion.name())
			       .map(XPotion::getPotionEffectType)
			       .ifPresent(type -> player.addPotionEffect(new PotionEffect(type, ticks, 0)));
		}
	}

	/** The production surroundings: {@link DoorFinder#indoors} and {@link DoorFinder#find} over the suspect's world. */
	static Surroundings worldSurroundings() {
		return new Surroundings() {

			@Override
			public boolean indoors(Location at, int roofBlocks) {
				return DoorFinder.indoors(at, roofBlocks);
			}

			@Override
			public Optional<DoorFinder.Door> doorNear(Location at, int radius) {
				World world = at.getWorld();
				if (world == null) return Optional.empty();
				return DoorFinder.find(at, radius, DoorFinder.worldLookup(world));
			}
		};
	}
}
