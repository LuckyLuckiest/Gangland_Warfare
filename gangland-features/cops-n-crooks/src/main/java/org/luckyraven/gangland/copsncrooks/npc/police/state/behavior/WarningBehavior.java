package org.luckyraven.gangland.copsncrooks.npc.police.state.behavior;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.npc.police.config.WarningSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.patrol.BrandishWarnings.Result;
import org.luckyraven.gangland.copsncrooks.npc.police.patrol.PatrolServices;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopBehavior;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;

import java.util.UUID;

/**
 * The {@code WARNING} state (0.20.0 lane patrol).
 * <p>
 * Faces the warned player and holds until the warning is decided. Never attacks. Without services (the bare factory
 * path, tests) it does nothing.
 */
public class WarningBehavior implements CopBehavior {

	private final @Nullable PatrolServices services;
	/** The player this warning was opened for, captured on enter so the open warning survives a cleared target. */
	private @Nullable UUID warned;

	public WarningBehavior() {
		this(null);
	}

	public WarningBehavior(@Nullable PatrolServices services) {
		this.services = services;
	}

	/** The services this behaviour runs on; {@code null} when unwired. */
	public @Nullable PatrolServices services() {
		return services;
	}

	@Override
	public void tick(CopNpc cop) {
		if (services == null) {
			return;
		}

		UUID id = cop.getTargetPlayerId();
		if (id == null) {
			abandonWarned();
			finish(cop);
			return;
		}

		Player player = Bukkit.getPlayer(id);
		if (player == null || !player.isOnline() || player.isDead()) {
			services.warnings().abandon(id);
			finish(cop);
			return;
		}

		cop.getNpc().faceLocation(player.getLocation());

		WarningSettings settings = services.warning().get();
		boolean inView = services.perception().acquires(cop, player, settings.range());
		Result result = services.warnings().update(player, inView);
		if (result == Result.PENDING) {
			return;
		}
		finish(cop);
	}

	@Override
	public void onEnter(CopNpc cop) {
		if (services != null) {
			warned = cop.getTargetPlayerId();
			cop.stopNavigation();
		}
	}

	@Override
	public void onExit(CopNpc cop) {
		abandonWarned();
	}

	/** Abandons the warning opened on enter, if still open; every exit path routes here. */
	private void abandonWarned() {
		if (services != null && warned != null) {
			services.warnings().abandon(warned);
		}
		warned = null;
	}

	private void finish(CopNpc cop) {
		cop.setTargetPlayerId(null);
		cop.transitionTo(CopState.PATROL);
	}
}
