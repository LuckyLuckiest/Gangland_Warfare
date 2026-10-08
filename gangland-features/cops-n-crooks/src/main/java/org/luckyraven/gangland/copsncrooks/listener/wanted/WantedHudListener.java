package org.luckyraven.gangland.copsncrooks.listener.wanted;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopTierConfig;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.gangland.copsncrooks.npc.police.spawn.CopSpawnManager;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.gangland.copsncrooks.wanted.config.HudSettings;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.AutoDrop.DropPlan;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.ChaseArcs;
import org.luckyraven.gangland.copsncrooks.wanted.heat.CrimeRecord;
import org.luckyraven.gangland.copsncrooks.wanted.heat.HeatLedger;
import org.luckyraven.gangland.copsncrooks.wanted.hud.StarCard;
import org.luckyraven.gangland.copsncrooks.wanted.hud.TitleCue;
import org.luckyraven.gangland.copsncrooks.wanted.hud.WantedHud;
import org.luckyraven.gangland.core.events.wanted.WantedEndEvent;
import org.luckyraven.gangland.core.events.wanted.WantedLevelChangeEvent;
import org.luckyraven.gangland.core.events.wanted.WantedStartEvent;
import org.luckyraven.gangland.core.wanted.Wanted;
import org.luckyraven.gangland.core.wanted.WantedCause;
import org.luckyraven.gangland.core.wanted.WantedStars;
import org.luckyraven.gangland.events.wanted.WantedEvasionStateEvent;
import org.luckyraven.keystone.bean.listener.ListenerHandler;
import org.luckyraven.keystone.sound.SoundEffect;
import org.luckyraven.keystone.sound.SoundEffect.SoundType;

import java.util.Map;

/**
 * The wanted HUD: title with the star card, siren, boss bar, zone ring and compass. Each piece sits behind its own
 * {@code Wanted.Hud.*.Enable}; the star-chat line is replaced by the card while the card is on.
 *
 * @since 0.15.0
 */
@ListenerHandler
public class WantedHudListener implements Listener {

	private final ChaseConfigLoader chase;
	private final WantedMessages    messages;
	private final HeatLedger        ledger;
	private final CopSpawnManager   spawns;
	private final CopLoader         copLoader;
	private final WantedHud         hud;
	private final ChaseArcs         arcs;

	public WantedHudListener(JavaPlugin plugin, ChaseConfigLoader chase, WantedMessages messages, HeatLedger ledger,
	                         CopSpawnManager spawns, CopLoader copLoader, WantedStars stars, ChaseArcs arcs) {
		this.chase     = chase;
		this.messages  = messages;
		this.ledger    = ledger;
		this.spawns    = spawns;
		this.copLoader = copLoader;
		this.hud       = new WantedHud(chase, messages);
		this.arcs      = arcs;

		stars.suppressStarChat(() -> chase.get().hud().starCard());
		Bukkit.getScheduler().runTaskTimer(plugin, hud::tick, 10L, 10L);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onStart(WantedStartEvent event) {
		hud.show(event.getPlayer(), event.getWantedLevel(),
		         Wanted.buildStars(event.getWantedLevel(), event.getWanted().getMaxLevel()));
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onLevelChange(WantedLevelChangeEvent event) {
		Player player   = event.getPlayer();
		int    newLevel = event.getNewLevel();
		String stars    = Wanted.buildStars(newLevel, event.getWanted().getMaxLevel());

		if (newLevel > event.getOldLevel()) {
			announce(player, newLevel, stars, raiseCard(player, event), chase.get().hud().gain());
			playSiren(player);
		} else if (newLevel < event.getOldLevel() && (newLevel > 0 || isGetaway(event.getCause()))) {
			int          lost     = event.getOldLevel() - newLevel;
			DropPlan     pending  = event.getCause() == WantedCause.EVASION ? arcs.takePending(player.getUniqueId()) : null;
			String       card     = StarCard.dropCard(messages, event.getCause(), pending, lost);
			HudSettings  settings = chase.get().hud();

			announce(player, newLevel, stars, card, newLevel > 0 ? settings.lost() : settings.escaped());
			hud.lost(player, lost);
		}

		if (newLevel > 0) hud.stars(player, newLevel, stars);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onEvasionState(WantedEvasionStateEvent event) {
		hud.state(event.getPlayer(), event.getState(), event.getLevel(), event.getSecondsLeft(), event.getZoneCentre(),
		          event.getZoneRadius());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onWantedEnd(WantedEndEvent event) {
		hud.hide(event.getPlayer());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onQuit(PlayerQuitEvent event) {
		hud.hide(event.getPlayer());
	}

	private static boolean isGetaway(WantedCause cause) {
		return cause == WantedCause.EVASION || cause == WantedCause.DECAY;
	}

	private String raiseCard(Player player, WantedLevelChangeEvent event) {
		CrimeRecord record = event.getCause() == WantedCause.CRIME ? ledger.lastCrime(player.getUniqueId()) : null;
		String crime = messages.crimeName(record == null ? "Unknown_Crime" : record.crimeId());

		CopConfigProvider provider = copLoader.getLoadedProvider();
		CopTierConfig     tier     = provider == null ? null
		                                                : provider.getTierConfig(
				                                                spawns.getTierForWantedLevel(event.getNewLevel()));

		return StarCard.raiseCard(messages, crime, CopRadio.tierName(tier), tier != null && tier.skipCuffing());
	}

	/**
	 * The event's title cue, with the card shown only while Star_Card is on. The card goes to chat only when the cue is
	 * switched off; a cue that is on but blank sends nothing.
	 */
	private void announce(Player player, int level, String stars, String card, TitleCue cue) {
		HudSettings settings = chase.get().hud();
		String      shownCard = settings.starCard() ? card : "";

		boolean sent = cue.send(player, Map.of("stars", stars, "level", String.valueOf(level), "card", shownCard));
		if (!sent && !cue.enabled() && settings.starCard()) {
			player.sendMessage(card);
		}
	}

	private void playSiren(Player player) {
		HudSettings settings = chase.get().hud();

		if (settings.siren()) {
			new SoundEffect(SoundType.VANILLA, settings.sirenSound(), settings.sirenVolume(),
			                settings.sirenPitch()).playSound(player);
		}
	}
}
