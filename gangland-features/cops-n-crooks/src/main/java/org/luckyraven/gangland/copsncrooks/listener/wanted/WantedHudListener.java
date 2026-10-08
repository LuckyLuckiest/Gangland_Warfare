package org.luckyraven.gangland.copsncrooks.listener.wanted;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopTierConfig;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.gangland.copsncrooks.npc.police.spawn.CopSpawnManager;
import org.luckyraven.gangland.copsncrooks.events.police.ArrestedEvent;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.gangland.copsncrooks.wanted.config.HudSettings;
import org.luckyraven.gangland.copsncrooks.wanted.config.PostEscapeSettings;
import org.luckyraven.gangland.copsncrooks.wanted.escape.PostEscapeSearch;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.AutoDrop.DropPlan;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.ChaseArcs;
import org.luckyraven.gangland.copsncrooks.wanted.heat.CrimeRecord;
import org.luckyraven.gangland.copsncrooks.wanted.heat.HeatLedger;
import org.luckyraven.gangland.copsncrooks.wanted.hud.StarCard;
import org.luckyraven.gangland.copsncrooks.wanted.hud.TitleCue;
import org.luckyraven.gangland.copsncrooks.wanted.hud.WantedHud;
import org.luckyraven.gangland.core.downed.PlayerDownedEvent;
import org.luckyraven.gangland.core.events.wanted.WantedEndEvent;
import org.luckyraven.gangland.core.events.wanted.WantedLevelChangeEvent;
import org.luckyraven.gangland.core.events.wanted.WantedStartEvent;
import org.luckyraven.gangland.core.wanted.Wanted;
import org.luckyraven.gangland.core.wanted.WantedCause;
import org.luckyraven.gangland.core.wanted.WantedStars;
import org.luckyraven.gangland.events.wanted.WantedEvasionStateEvent;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.keystone.bean.listener.ListenerHandler;
import org.luckyraven.keystone.sound.SoundEffect;
import org.luckyraven.keystone.sound.SoundEffect.SoundType;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * The wanted HUD: title with the star card, siren, boss bar, zone ring and compass. Each piece sits behind its own
 * {@code Wanted.Hud.*.Enable}; the star-chat line is replaced by the card while the card is on. An escape by evasion
 * with a bounty keeps the bar up as the bounty bar until the post-escape search runs out (0.16.1 T-187).
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
	private final PostEscapeSearch  search;

	public WantedHudListener(JavaPlugin plugin, ChaseConfigLoader chase, WantedMessages messages, HeatLedger ledger,
	                         CopSpawnManager spawns, CopLoader copLoader, WantedStars stars, ChaseArcs arcs,
	                         PostEscapeSearch search) {
		this.chase     = chase;
		this.messages  = messages;
		this.ledger    = ledger;
		this.spawns    = spawns;
		this.copLoader = copLoader;
		this.hud       = new WantedHud(chase, messages);
		this.arcs      = arcs;
		this.search    = search;

		stars.suppressStarChat(() -> chase.get().hud().starCard());
		Bukkit.getScheduler().runTaskTimer(plugin, this::beat, 10L, 10L);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onStart(WantedStartEvent event) {
		// A new wanted start is a new episode: the post-escape search, if any, is over (0.16.1 T-187)
		search.end(event.getPlayer().getUniqueId());
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

	/** An escape by evasion turns the bar into the bounty bar; any other end hides it and ends the search (0.16.1 T-187). */
	@EventHandler(priority = EventPriority.MONITOR)
	public void onWantedEnd(WantedEndEvent event) {
		Player player = event.getPlayer();
		UUID   id     = player.getUniqueId();

		if (!search.isEscape(event.getCause())) {
			hud.hide(player);
			search.end(id);
			return;
		}

		// An escape that started no search (no user to bounty) shows no bounty: the chase bar goes
		if (!search.isSearching(id)) {
			hud.hide(player);
			return;
		}

		hud.bounty(player, search.beats(), () -> search.bountyAmount(id));
		announceBounty(player, search.bountyAmount(id));
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onQuit(PlayerQuitEvent event) {
		endView(event.getPlayer());
	}

	/** A searched player is at 0 stars, so his death fires no wanted end: the bar and the search go here (0.16.1 T-187). */
	@EventHandler(priority = EventPriority.MONITOR)
	public void onDeath(PlayerDeathEvent event) {
		endView(event.getEntity());
	}

	/** As {@link #onDeath}, for the downed state. */
	@EventHandler(priority = EventPriority.MONITOR)
	public void onDowned(PlayerDownedEvent event) {
		endView(event.getPlayer());
	}

	/** A player admitted to jail at zero stars fires no wanted end: the bar and the search go here (0.16.1 T-187). */
	@EventHandler(priority = EventPriority.MONITOR)
	public void onArrested(ArrestedEvent event) {
		endView(event.getPlayer());
	}

	private void endView(Player player) {
		hud.hide(player);
		search.end(player.getUniqueId());
	}

	/**
	 * One half-second beat: a bounty bar whose search ended without a wanted end (a shutdown) is hidden, then the HUD's tick,
	 * which counts the post-escape bounty down; an expired search ends here.
	 */
	private void beat() {
		for (Player player : hud.bountyViewers()) {
			if (!search.isSearching(player.getUniqueId())) hud.hide(player);
		}

		for (Player player : hud.tick()) {
			UUID       id       = player.getUniqueId();
			BigDecimal amount   = search.bountyAmount(id);
			boolean    searched = search.isSearching(id);

			search.expire(id);
			if (searched && announces()) player.sendMessage(gaveUp(amount));
		}
	}

	/** Whether the escape and give-up chat lines show: {@code Wanted.Post_Escape.Announce}. */
	private boolean announces() {
		PostEscapeSettings settings = chase.getPostEscape();
		return settings == null || settings.announce();
	}

	/** The chat line when an escape starts: the bounty on him, or that the cops are still looking. */
	private void announceBounty(Player player, BigDecimal amount) {
		if (!announces()) return;

		if (amount.signum() > 0) {
			player.sendMessage(messages.format(WantedMessages.Key.ANNOUNCE_BOUNTY, moneyValues(amount)));
		} else {
			player.sendMessage(messages.format(WantedMessages.Key.ANNOUNCE_NO_BOUNTY, Map.of()));
		}
	}

	/** The chat line when the search runs out, saying whether a bounty still stands. */
	private String gaveUp(BigDecimal amount) {
		if (amount.signum() > 0) {
			return messages.format(WantedMessages.Key.ANNOUNCE_GAVE_UP_BOUNTY, moneyValues(amount));
		}

		return messages.format(WantedMessages.Key.ANNOUNCE_GAVE_UP, Map.of());
	}

	private static Map<String, String> moneyValues(BigDecimal amount) {
		return Map.of("money_symbol", Settings.getMoneySymbol(), "amount", Settings.formatAmount(amount));
	}

	private static boolean isGetaway(WantedCause cause) {
		return cause == WantedCause.EVASION || cause == WantedCause.DECAY;
	}

	private String raiseCard(Player player, WantedLevelChangeEvent event) {
		CrimeRecord record = event.getCause() == WantedCause.CRIME ? ledger.lastCrime(player.getUniqueId()) : null;
		// An UNKNOWN raise of a searched player is a sighting (PostEscapeSpotting): it fires before the search ends (wanted-15)
		boolean spotted = record == null && event.getCause() == WantedCause.UNKNOWN &&
		                  search.isSearching(player.getUniqueId());
		String crime = messages.crimeName(record != null ? record.crimeId() : spotted ? "Spotted" : "Unknown_Crime");

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
