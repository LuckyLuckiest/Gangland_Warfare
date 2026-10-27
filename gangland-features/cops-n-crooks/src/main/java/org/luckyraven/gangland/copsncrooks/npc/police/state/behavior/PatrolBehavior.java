package org.luckyraven.gangland.copsncrooks.npc.police.state.behavior;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.config.WarningSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.patrol.BrandishWarnings;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;
import org.luckyraven.keystone.npc.NpcSquad;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.patrol.PatrolServices;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopBehavior;

import java.util.HashMap;
import java.util.Map;

/** The {@code PATROL} state (0.20.0 lane patrol): walks a beat around the anchor, reports wanted players to their squad and starts a warning for a brandished weapon. Never attacks. Without services (the bare factory path, tests) it does nothing. */
public class PatrolBehavior implements CopBehavior {

	private static final class Beat {

		boolean walking;
		long    pauseUntil;
	}

	private final @Nullable PatrolServices services;
	private final Map<CopNpc, Beat>        beats = new HashMap<>();

	public PatrolBehavior() {
		this(null);
	}

	public PatrolBehavior(@Nullable PatrolServices services) {
		this.services = services;
	}

	/** The services this behaviour runs on; {@code null} when unwired. */
	public @Nullable PatrolServices services() {
		return services;
	}

	@Override
	public void tick(CopNpc cop) {
		if (services == null) return;
		if (scan(cop)) return;
		walk(cop);
	}

	/** Reports wanted players in view to their squad and opens a warning for a brandished weapon; true when a warning took over this tick. */
	private boolean scan(CopNpc cop) {
		WarningSettings ws = services.warning().get();
		boolean report     = services.patrol().get().reportWanted();
		for (Player player : services.players().get()) {
			if (!player.isOnline() || player.isDead() || services.isNpc().test(player)) continue;
			if (cop.distanceTo(player) > ws.range() || !services.perception().acquires(cop, player, ws.range())) continue;

			if (services.wanted().test(player)) {
				if (report) reportWanted(cop, player, services.perception().rangeFor(cop, player, ws.range()));
				continue;
			}
			BrandishWarnings warnings = services.warnings();
			if (!ws.enabled() || !warnings.holding(player) || !warnings.canWarn(player)) continue;
			if (!warnings.begin(player)) continue;
			cop.setTargetPlayerId(player.getUniqueId());
			cop.transitionTo(CopState.WARNING);
			return true;
		}
		return false;
	}

	/** {@code range}: the sight range this cop looked with, so its eyes-on mark ends when it can no longer see him. */
	private void reportWanted(CopNpc cop, Player player, double range) {
		CopGroup group = services.groupOf().apply(player.getUniqueId());
		if (group == null) return;
		NpcSquad squad = group.getSquad();
		if (!squad.hasFreshSighting()) {
			services.radio().sayAs(group, cop, "Patrol_Sighting", Map.of("place", services.radio().placeOf(player.getLocation())));
		}
		squad.reportSighting(cop, player.getLocation(), range);
	}

	private void walk(CopNpc cop) {
		var      ps     = services.patrol().get();
		Location anchor = cop.getSpawnLocation();
		World    world  = anchor == null ? null : anchor.getWorld();
		if (world == null) return;
		if (cop.getNpc().getNavigator().isNavigating()) return;

		long now  = services.clock().getAsLong();
		Beat beat = beats.computeIfAbsent(cop, key -> new Beat());

		Entity body = cop.getEntity();
		if (body != null) {
			Location at = body.getLocation();
			if (Math.hypot(at.getX() - anchor.getX(), at.getZ() - anchor.getZ()) > ps.beatRadius()) {
				cop.navigateTo(anchor);
				beat.walking = true;
				return;
			}
		}

		if (beat.walking) {
			beat.walking = false;
			int min = ps.pauseSecondsMin();
			int max = Math.max(min, ps.pauseSecondsMax());
			beat.pauseUntil = now + (min + services.random().nextInt(max - min + 1)) * 1000L;
			return;
		}
		if (now < beat.pauseUntil) return;

		for (int attempt = 0; attempt < 5; attempt++) {
			double angle = services.random().nextDouble() * 2 * Math.PI;
			double r     = ps.beatRadius() * Math.sqrt(services.random().nextDouble());
			double x     = anchor.getX() + r * Math.cos(angle);
			double z     = anchor.getZ() + r * Math.sin(angle);
			int    bx    = (int) Math.floor(x);
			int    bz    = (int) Math.floor(z);
			if (!world.isChunkLoaded(bx >> 4, bz >> 4)) continue;
			double y = world.getHighestBlockYAt(bx, bz) + 1;
			cop.navigateTo(new Location(world, x, y, z));
			beat.walking = true;
			return;
		}
	}

	@Override
	public void onEnter(CopNpc cop) {
		if (services != null) beats.remove(cop);
	}

	@Override
	public void onExit(CopNpc cop) {
		if (services != null) beats.remove(cop);
	}
}
