package org.luckyraven.gangland.copsncrooks.listener.police;

import lombok.CustomLog;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.luckyraven.gangland.copsncrooks.detainment.ArrestOrigin;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.events.police.CuffedEvent;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.detainment.wanted.WantedClearContract;
import org.luckyraven.gangland.copsncrooks.integration.detainment.DetainmentSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CuffLockRegistry;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.core.downed.DownedPlayerRegistry;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.keystone.bean.listener.ListenerHandler;
import org.luckyraven.keystone.npc.NpcSupport;
import org.luckyraven.keystone.util.ChatUtil;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 0.21.0 "Busted or wasted": a lethal hit on a wanted player next to a hunting cop of his own squad becomes an arrest.
 * <p>Ordering contract: this runs at HIGH with ignoreCancelled and cancels the hit, so core
 * {@code CustomPlayerDeathListener.onEntityDamage} (HIGHEST + ignoreCancelled) skips it and never downs the player;
 * {@code PlayerDeathListener} bills only on {@code PlayerDownedEvent}, which a bust never fires. The BUSTED title is
 * sent after {@code callEvent} and the guard handoff because CuffingListener sends CUFFED inside the event.
 */
@CustomLog
@ListenerHandler
public class BustedListener implements Listener {

	/** getNearbyEntities bound; ponytail: covers the widest shipped Cuff_Radius (5.0), the exact per-cop check follows. */
	static final double SEARCH_BOUND = 6.0;

	/** Causes that are never a bust, by name: KILL only exists on 1.20.3+ and the compile API is 1.16.5. */
	private static final int DEFAULT_MAX_ATTEMPTS = 3;

	private static final Set<String> SKIP_CAUSES = Set.of("VOID", "SUICIDE", "KILL");

	private final CopManager             copManager;
	private final CopLoader              copLoader;
	private final DetainmentService      detainmentService;
	private final DetainmentSettings     detainmentSettings;
	private final CuffLockRegistry       cuffLockRegistry;
	private final WantedClearContract    wantedClearContract;
	private final WantedMessages         wantedMessages;

	public BustedListener(CopManager copManager, CopLoader copLoader, DetainmentService detainmentService,
	                      DetainmentSettings detainmentSettings, CuffLockRegistry cuffLockRegistry,
	                      WantedClearContract wantedClearContract, WantedMessages wantedMessages) {
		this.copManager           = copManager;
		this.copLoader            = copLoader;
		this.detainmentService    = detainmentService;
		this.detainmentSettings   = detainmentSettings;
		this.cuffLockRegistry     = cuffLockRegistry;
		this.wantedClearContract  = wantedClearContract;
		this.wantedMessages       = wantedMessages;
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onDamage(EntityDamageEvent event) {
		if (!detainmentSettings.isBustedEnabled()) return;
		if (!Settings.isRespawnEnabled()) return;
		if (!(event.getEntity() instanceof Player player)) return;
		if (NpcSupport.isNpc(player)) return;

		UUID id = player.getUniqueId();
		if (DownedPlayerRegistry.isDowned(id)) return;
		if (detainmentService.isRestrained(player)) return;
		if (player.getHealth() - event.getFinalDamage() > 0) return;
		int level = wantedClearContract.getWantedLevel(id);
		if (level < 1) return;
		if (SKIP_CAUSES.contains(event.getCause().name())) return;

		CopNpc hunter = findHunter(player);
		if (hunter == null) return;

		bust(event, player, hunter, level);
	}

	/**
	 * The nearest cop of the victim's own squad that is hunting him (allowlist: PURSUING or COMBAT) and within its
	 * Cuff_Radius; null when none. Allowlist, never a denylist, so states added later never bust by accident.
	 */
	private CopNpc findHunter(Player player) {
		UUID   id      = player.getUniqueId();
		CopNpc best    = null;
		double bestGap = Double.MAX_VALUE;

		for (Entity entity : player.getNearbyEntities(SEARCH_BOUND, SEARCH_BOUND, SEARCH_BOUND)) {
			CopNpc cop = copManager.findCopByEntity(entity);
			if (cop == null || !cop.isValid()) continue;
			if (cop.getGroup() == null || !id.equals(cop.getGroup().getTargetPlayerId())) continue;

			CopState state = cop.getCurrentState();
			if (state != CopState.PURSUING && state != CopState.COMBAT) continue;

			double distance = cop.distanceTo(player);
			if (distance > cop.getTierConfig().cuffRadius() || distance >= bestGap) continue;

			best    = cop;
			bestGap = distance;
		}
		return best;
	}

	private void bust(EntityDamageEvent event, Player player, CopNpc cop, int level) {
		UUID id = player.getUniqueId();
		event.setCancelled(true);

		CopConfigProvider provider    = copLoader.getLoadedProvider();
		int               maxAttempts = provider != null ? provider.getMaxCuffAttempts() : DEFAULT_MAX_ATTEMPTS;
		Bukkit.getPluginManager().callEvent(new CuffedEvent(cop, player, cop.getTierConfig().cuffRadius(), maxAttempts,
		                                                    ArrestOrigin.BUSTED_DOWNED));

		cuffLockRegistry.forceRelease(id);
		if (cuffLockRegistry.tryAcquire(id, cop.getNpc().getUniqueId())) {
			cop.setGuardedPlayerId(id);
			cop.transitionTo(CopState.GUARDING);
		}

		// Last on purpose: CuffingListener sends CUFFED inside callEvent above, BUSTED must be what stays on screen.
		ChatUtil.sendTitle(player, wantedMessages.format(WantedMessages.Key.BUSTED_TITLE, Map.of()),
		                   wantedMessages.format(WantedMessages.Key.BUSTED_SUBTITLE, Map.of()));
		log.debug("BUSTED " + player.getName() + " cop=" + cop.getNpc().getUniqueId() + " level=" + level);
	}

}
