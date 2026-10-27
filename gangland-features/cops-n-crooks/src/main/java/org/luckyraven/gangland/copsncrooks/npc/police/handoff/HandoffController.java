package org.luckyraven.gangland.copsncrooks.npc.police.handoff;

import lombok.CustomLog;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.HandoffSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.dispatch.SpawnBias;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNullElse;

/**
 * The pursuit hand-off (0.16.0): when the cops chasing a wanted player stop being engaged because the chase leashed out
 * (a cop is walking home beyond {@code Cops.Pursuit.Max_Distance}), the leader radios the suspect's heading (heard
 * around him as well as around the speaker) and units dispatched in the next {@code Bias_Seconds} spawn ahead of him,
 * seeded with the last-known position. The heading is read from the squad's own last-known spots (0.21.1), never from
 * where the suspect really runs once no cop sees him. A perimeter post counts as engaged only within {@code Max_Distance} of him.
 * <p>
 * Reads the config on every call ({@code /glw reload}); main thread only, like the cop AI tick that drives {@link #tick}.
 *
 * @since 0.16.0
 */
@CustomLog
public class HandoffController {

	/** Travel under this many blocks over the heading window is no heading: the last-seen facing is used instead. */
	private static final double MIN_TRAVEL = 2.0;

	private record Sample(long time, Location at) { }

	private final CopManager                  copManager;
	private final CopRadio                    copRadio;
	private final Supplier<CopConfigProvider> provider;

	private final Map<UUID, Deque<Sample>> samples = new HashMap<>();
	/** Players whose group was engaged on the previous tick. */
	private final Set<UUID>                engaged = new HashSet<>();

	public HandoffController(CopManager copManager, CopRadio copRadio, Supplier<CopConfigProvider> provider) {
		this.copManager = copManager;
		this.copRadio   = copRadio;
		this.provider   = provider;
	}

	/** Per AI tick: records the squad's last-known spot and sets the bias on the engaged -> not engaged edge. */
	public void tick(Player player, @Nullable CopGroup group) {
		UUID              id     = player.getUniqueId();
		CopConfigProvider config = provider.get();
		if (config == null) return;   // no cop config loaded (a reload window): skip the tick, keep the state

		HandoffSettings settings = requireNonNullElse(config.getHandoffSettings(), HandoffSettings.DEFAULT);
		// the post-escape search runs its group at one star (T-187) but he has none: no hand-off, nobody chases (T-432)
		if (!settings.enabled() || group == null || group.getLevel() <= 0 || copManager.isSearching(id)) {
			samples.remove(id);
			engaged.remove(id);
			if (group != null) group.clearLeashReleased();
			return;
		}

		long          now    = copRadio.now();
		Deque<Sample> window = samples.computeIfAbsent(id, key -> new ArrayDeque<>());
		// a hand-off lead is the squad's own projection, not where anyone saw or reported him: no heading sample (R5)
		Location lastKnown = group.getSquad().lastKnownLocation();
		if (!group.isHandoffSeed(lastKnown)) record(window, lastKnown, now, settings.headingSeconds() * 1000L);

		double  maxDistance = config.getPursuitMaxDistance();
		boolean wasEngaged  = engaged.contains(id);
		boolean isEngaged   = isEngaged(group, player, maxDistance);
		if (isEngaged) engaged.add(id);
		else engaged.remove(id);

		if (!wasEngaged || isEngaged || group.biasAt(now) != null) return;
		// a leashed walker removed shortly before the chase fell away still leashed it out, whoever chased on since; one
		// older than Bias_Seconds is no cause of this fall-away (a wipe or a stand-off a minute later), unless he has now
		// left a post of the group behind (0.21.1 r6: posts held the chase since; a post killed or called off is no
		// leash). Only this edge (or a wanted start or end, CopManager) consumes the record
		boolean leashed = group.isLeashReleased(now, settings.biasSeconds() * 1000L) ||
		                  group.isLeashReleased(now, Long.MAX_VALUE) && postLeftBehind(group, player, maxDistance) ||
		                  leashedOut(group, id, player, maxDistance);
		group.clearLeashReleased();
		if (window.isEmpty() || !leashed) return;

		Location oldest  = window.peekFirst().at();
		Location newest  = window.peekLast().at();
		Vector   heading = new Vector(newest.getX() - oldest.getX(), 0, newest.getZ() - oldest.getZ());
		if (heading.length() < MIN_TRAVEL) heading = newest.getDirection();

		group.setBias(new SpawnBias(heading, newest, now + settings.biasSeconds() * 1000L, settings.coneDegrees()));
		// the word names the heading itself, so a fallback to the last-seen facing is said the same way
		String word = copRadio.compassWord(newest, newest.clone().add(heading));
		// heard around him too: the speaker is a cop walking home beyond the radio's reach of him
		copRadio.sayFromLeader(group, "Handoff", Map.of("direction", word), player);
		log.debug("HANDOFF {} heading={}", player.getName(), word);
	}

	/**
	 * Adds the squad's last-known spot when it moved (a stale spot repeated every tick is no travel; the same spot only
	 * refreshes the facing) and keeps the spots within {@code windowMs} of the newest one, so the heading is the last
	 * stretch the squad knew of, however long ago.
	 */
	private static void record(Deque<Sample> window, @Nullable Location lastKnown, long now, long windowMs) {
		if (lastKnown == null) return;
		Sample newest = window.peekLast();
		if (newest != null && samePlace(newest.at(), lastKnown))
			window.addLast(new Sample(window.removeLast().time(), lastKnown));
		else window.addLast(new Sample(now, lastKnown));
		long latest = window.peekLast().time();
		while (window.size() > 1 && latest - window.peekFirst().time() > windowMs) window.removeFirst();
	}

	private static boolean samePlace(Location a, Location b) {
		return a.getWorld() == b.getWorld() && a.getX() == b.getX() && a.getY() == b.getY() && a.getZ() == b.getZ();
	}

	/**
	 * A cop of the group chases, fights, cuffs or guards him, or holds a perimeter post within {@code maxDistance} of
	 * him: a post left behind where he was lost watches an empty zone, so it no longer holds the chase. A cuff is the
	 * chase won, never lost (the rest sent home beside him are no leash).
	 */
	private static boolean isEngaged(CopGroup group, Player player, double maxDistance) {
		if (closing(group)) return true;
		for (CopNpc cop : List.copyOf(group.getCops())) {
			CopState state = cop.getCurrentState();
			if (!cop.isValid()) continue;
			Entity body = cop.getEntity();
			if ((state == CopState.POSTED || state == CopState.OVERWATCH) &&
			    (body == null || !farFrom(body.getLocation(), player, maxDistance)))
				return true;
		}
		return false;
	}

	/** A cop of the group chases, fights, cuffs or guards him. */
	private static boolean closing(CopGroup group) {
		for (CopNpc cop : List.copyOf(group.getCops())) {
			CopState state = cop.getCurrentState();
			if (cop.isValid() && (state == CopState.PURSUING || state == CopState.COMBAT || state == CopState.CUFFING ||
			                      state == CopState.GUARDING)) return true;
		}
		return false;
	}

	/** A live POSTED/OVERWATCH cop of the group stands farther than {@code maxDistance} from him: he left it behind. */
	private static boolean postLeftBehind(CopGroup group, Player player, double maxDistance) {
		for (CopNpc cop : List.copyOf(group.getCops())) {
			CopState state = cop.getCurrentState();
			Entity   body  = cop.getEntity();
			if (cop.isValid() && body != null && (state == CopState.POSTED || state == CopState.OVERWATCH) &&
			    farFrom(body.getLocation(), player, maxDistance)) return true;
		}
		return false;
	}

	/**
	 * A cop still hunting this player (target kept: a stood-down chase clears it) is RETURNING beyond the pursuit range,
	 * or one was and a spawn or AI run removed it lately ({@link CopGroup#isLeashReleased}, read by the caller).
	 */
	private static boolean leashedOut(CopGroup group, UUID id, Player player, double maxDistance) {
		for (CopNpc cop : List.copyOf(group.getCops())) {
			Entity body = cop.getEntity();
			if (!cop.isValid() || body == null || cop.getCurrentState() != CopState.RETURNING ||
			    !id.equals(cop.getTargetPlayerId())) continue;
			if (farFrom(body.getLocation(), player, maxDistance)) return true;
		}
		return false;
	}

	/** {@code at} is in another world than {@code player} or farther than {@code maxDistance} from him. */
	private static boolean farFrom(Location at, Player player, double maxDistance) {
		return at.getWorld() != player.getWorld() || at.distanceSquared(player.getLocation()) > maxDistance * maxDistance;
	}
}
