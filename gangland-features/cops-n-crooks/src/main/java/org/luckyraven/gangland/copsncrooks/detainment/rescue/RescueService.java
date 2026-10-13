package org.luckyraven.gangland.copsncrooks.detainment.rescue;

import lombok.CustomLog;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.detainment.release.ReleasePipeline;
import org.luckyraven.gangland.copsncrooks.detainment.release.ReleaseReason;
import org.luckyraven.gangland.copsncrooks.detainment.settings.RescueSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CuffLockRegistry;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.core.wanted.WantedCause;
import org.luckyraven.gangland.core.wanted.WantedStars;
import org.luckyraven.gangland.data.gang.GangMembership;
import org.luckyraven.keystone.util.ActionBarManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * 0.21.0 rescues: a crew mate holding right-click on the cop guarding a handcuffed player frees him (P-RESC-1..5).
 *
 * @since 0.21.0
 */
@CustomLog
public class RescueService {

	/** Longest pause between two clicks that still counts as one continuous hold. */
	static final long GAP_MS = 1000L;

	private record Hold(UUID prisonerId, CopNpc cop, long startedAt, long lastClickAt) {
	}

	private final Supplier<RescueSettings> settings;
	private final CopManager               copManager;
	private final DetainmentService        detainmentService;
	private final CuffLockRegistry         cuffLockRegistry;
	private final ReleasePipeline          releasePipeline;
	private final GangMembership           gangs;
	private final WantedStars              wantedStars;
	private final UserManager<Player>      users;
	private final WantedMessages           messages;
	private final JavaPlugin               plugin;
	private final Function<UUID, Player>   onlinePlayers;
	private final LongSupplier             clock;
	private final Map<UUID, Hold>          holds = new HashMap<>();

	public RescueService(Supplier<RescueSettings> settings, CopManager copManager, DetainmentService detainmentService,
	                     CuffLockRegistry cuffLockRegistry, ReleasePipeline releasePipeline, GangMembership gangs,
	                     WantedStars wantedStars, UserManager<Player> users, WantedMessages messages, JavaPlugin plugin,
	                     Function<UUID, Player> onlinePlayers, LongSupplier clock) {
		this.settings          = settings;
		this.copManager        = copManager;
		this.detainmentService = detainmentService;
		this.cuffLockRegistry  = cuffLockRegistry;
		this.releasePipeline   = releasePipeline;
		this.gangs             = gangs;
		this.wantedStars       = wantedStars;
		this.users             = users;
		this.messages          = messages;
		this.plugin            = plugin;
		this.onlinePlayers     = onlinePlayers;
		this.clock             = clock;
	}

	/** One right-click by {@code rescuer} on {@code clicked}; main thread. */
	public void onClick(Player rescuer, Entity clicked) {
		RescueSettings config = settings.get();
		if (!config.enabled()) {
			return;
		}
		UUID  rescuerId = rescuer.getUniqueId();
		CopNpc cop      = copManager.findCopByEntity(clicked);
		if (cop == null || !cop.isValid() || cop.getCurrentState() != CopState.GUARDING) {
			holds.remove(rescuerId);
			return;
		}
		UUID   prisonerId = cop.getGuardedPlayerId();
		Player prisoner   = prisonerId == null ? null : onlinePlayers.apply(prisonerId);
		if (prisoner == null || !detainmentService.isHandcuffed(prisoner)) {
			holds.remove(rescuerId);
			return;
		}
		if (rescuer.equals(prisoner) || rescuerId.equals(prisonerId)) {
			return;
		}
		if (detainmentService.isRestrained(rescuer)) {
			holds.remove(rescuerId);
			return;
		}
		if (!gangs.alliedOrSame(rescuerId, prisonerId)) {
			return;
		}

		long now  = clock.getAsLong();
		Hold hold = holds.get(rescuerId);
		if (hold == null || !hold.prisonerId().equals(prisonerId) || hold.cop() != cop
		    || now - hold.lastClickAt() > GAP_MS) {
			hold = new Hold(prisonerId, cop, now, now);
		} else {
			hold = new Hold(prisonerId, cop, hold.startedAt(), now);
		}
		holds.put(rescuerId, hold);

		long holdMs  = config.holdSeconds() * 1000L;
		long elapsed = now - hold.startedAt();
		if (elapsed >= holdMs) {
			free(rescuer, prisoner, cop, config);
			return;
		}
		int seconds = (int) Math.ceil((holdMs - elapsed) / 1000.0);
		String text = messages.format(WantedMessages.Key.RESCUE_PROGRESS,
		                              Map.of("player", prisoner.getName(), "seconds", String.valueOf(seconds)));
		ActionBarManager.send(plugin, rescuer, text, 40L);
	}

	private void free(Player rescuer, Player prisoner, CopNpc cop, RescueSettings config) {
		holds.remove(rescuer.getUniqueId());
		cuffLockRegistry.forceRelease(prisoner.getUniqueId());
		releasePipeline.release(prisoner, ReleaseReason.RESCUED);
		if (cop.getCurrentState() == CopState.GUARDING) {
			cop.setGuardedPlayerId(null);
			cop.transitionTo(CopState.PURSUING);
		}
		if (config.stars() > 0) {
			raise(rescuer, config.stars());
			raise(prisoner, config.stars());
		}
		rescuer.sendMessage(messages.format(WantedMessages.Key.RESCUE_FREED, Map.of("player", prisoner.getName())));
		prisoner.sendMessage(messages.format(WantedMessages.Key.RESCUE_FREED_BY, Map.of("player", rescuer.getName())));
		log.debug("RESCUE {} freed {}", rescuer.getName(), prisoner.getName());
	}

	private void raise(Player player, int stars) {
		User<Player> user = users.getUser(player);
		if (user != null) {
			wantedStars.raise(user, stars, WantedCause.CRIME);
		}
	}
}
