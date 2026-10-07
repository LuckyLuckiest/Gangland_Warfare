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
 * (a cop is walking home beyond {@code Cops.Pursuit.Max_Distance}), the leader radios the suspect's heading and units
 * dispatched in the next {@code Bias_Seconds} spawn ahead of him, seeded with the last-known position.
 * <p>
 * Reads the config on every call ({@code /glw reload}); main thread only, like the cop AI tick that drives {@link #tick}.
 *
 * @since 0.16.0
 */
@CustomLog
public class HandoffController {

	/** Travel under this many blocks over the heading window is no heading: the player's facing is used instead. */
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

	/** Per AI tick: records the suspect's position and sets the bias on the engaged -> not engaged edge. */
	public void tick(Player player, @Nullable CopGroup group) {
		UUID              id     = player.getUniqueId();
		CopConfigProvider config = provider.get();
		if (config == null) return;   // no cop config loaded (a reload window): skip the tick, keep the state

		HandoffSettings settings = requireNonNullElse(config.getHandoffSettings(), HandoffSettings.DEFAULT);
		if (!settings.enabled() || group == null || group.getLevel() <= 0) {
			samples.remove(id);
			engaged.remove(id);
			return;
		}

		long now = copRadio.now();
		Deque<Sample> window = samples.computeIfAbsent(id, key -> new ArrayDeque<>());
		window.addLast(new Sample(now, player.getLocation().clone()));
		while (window.size() > 1 && now - window.peekFirst().time() > settings.headingSeconds() * 1000L)
			window.removeFirst();

		boolean wasEngaged = engaged.contains(id);
		boolean isEngaged  = isEngaged(group);
		if (isEngaged) engaged.add(id);
		else engaged.remove(id);

		if (!wasEngaged || isEngaged || group.biasAt(now) != null) return;
		if (!leashedOut(group, id, player, config.getPursuitMaxDistance())) return;

		Location oldest  = window.peekFirst().at();
		Location newest  = window.peekLast().at();
		Vector   heading = new Vector(newest.getX() - oldest.getX(), 0, newest.getZ() - oldest.getZ());
		if (heading.length() < MIN_TRAVEL) heading = player.getLocation().getDirection();

		group.setBias(new SpawnBias(heading, newest, now + settings.biasSeconds() * 1000L, settings.coneDegrees()));
		String word = copRadio.compassWord(oldest, newest);
		copRadio.sayFromLeader(group, "Handoff", Map.of("direction", word));
		log.debug("HANDOFF {} heading={}", player.getName(), word);
	}

	private static boolean isEngaged(CopGroup group) {
		for (CopNpc cop : List.copyOf(group.getCops())) {
			CopState state = cop.getCurrentState();
			if (cop.isValid() && (state == CopState.PURSUING || state == CopState.COMBAT || state == CopState.POSTED))
				return true;
		}
		return false;
	}

	/** A cop still hunting this player (target kept: a stood-down chase clears it) is RETURNING beyond the pursuit range. */
	private static boolean leashedOut(CopGroup group, UUID id, Player player, double maxDistance) {
		for (CopNpc cop : List.copyOf(group.getCops())) {
			Entity body = cop.getEntity();
			if (!cop.isValid() || body == null || cop.getCurrentState() != CopState.RETURNING ||
			    !id.equals(cop.getTargetPlayerId())) continue;
			Location at = body.getLocation();
			if (at.getWorld() != player.getWorld() || at.distanceSquared(player.getLocation()) > maxDistance * maxDistance)
				return true;
		}
		return false;
	}
}
