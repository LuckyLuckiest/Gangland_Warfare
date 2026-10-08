package org.luckyraven.gangland.copsncrooks.wanted.escape;

import org.bukkit.entity.Player;
import org.luckyraven.gangland.copsncrooks.npc.police.targeting.WantedTargetingManager;
import org.luckyraven.gangland.copsncrooks.wanted.config.PostEscapeSettings;
import org.luckyraven.gangland.core.bounty.Bounty;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.core.wanted.WantedCause;
import org.luckyraven.gangland.file.configuration.Settings;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The post-escape search (0.16.1 T-187): cops keep looking for a player who escaped by evasion, with a bounty on him. One
 * record per searched player, ended by the HUD's expiry (its beat count is the timer), by re-wanted, by death, arrest or
 * quit, or by a shutdown. Targeting is marked on {@link #begin} and cleared on {@link #end}.
 *
 * @since 0.16.1
 */
public final class PostEscapeSearch {

	private final UserManager<Player>          users;
	private final WantedTargetingManager       targeting;
	private final Supplier<PostEscapeSettings> settings;
	private final Map<UUID, Player>            searches = new HashMap<>();
	/** The highest star count each player reached in his current chase; see {@link #recordLevel}. */
	private final Map<UUID, Integer>           peaks    = new HashMap<>();
	/** Told when the search runs out on its own; the cops give up then. Set by the module wiring. */
	private       Consumer<UUID>               giveUp;

	/** A search with the shipped settings and its own targeting; the production wiring passes the live pieces. */
	public PostEscapeSearch(UserManager<Player> users) {
		this(users, new WantedTargetingManager(), () -> PostEscapeSettings.DEFAULT);
	}

	public PostEscapeSearch(UserManager<Player> users, WantedTargetingManager targeting,
	                        Supplier<PostEscapeSettings> settings) {
		this.users     = users;
		this.targeting = targeting;
		this.settings  = settings;
	}

	/** Sets the hook the cops use to give up when the search runs out on its own. */
	public void onGiveUp(Consumer<UUID> giveUp) {
		this.giveUp = giveUp;
	}

	/** Whether a wanted end of {@code cause} is an escape that starts the search: evasion, with the feature on. */
	public boolean isEscape(WantedCause cause) {
		return settings.get().enabled() && cause == WantedCause.EVASION;
	}

	/** Half-second HUD beats the search lasts. */
	public int beats() {
		return settings.get().searchSeconds() * 2;
	}

	/** Notes a star count the player reached in his chase, so an escape is paid on the peak, not the last star. */
	public void recordLevel(UUID playerId, int level) {
		peaks.merge(playerId, level, Math::max);
	}

	/**
	 * The chase's peak for an escape, and forgets it: the highest level recorded, or {@code level} when that is higher.
	 * Called on every drop to 0, escape or not, so a finished chase leaves nothing behind.
	 */
	public int takePeak(UUID playerId, int level) {
		Integer peak = peaks.remove(playerId);
		return peak == null ? level : Math.max(peak, level);
	}

	/**
	 * An escape by evasion took the last star; {@code peak} is the highest star count of that chase. Adds the auto-bounty
	 * notoriety once per spell, unless the notoriety already sits at {@code Bounty.Kill.Maximum}, and marks the player as
	 * searched for. A second call while the search runs does nothing.
	 */
	public void begin(Player player, int peak) {
		if (!settings.get().enabled()) return;

		UUID id = player.getUniqueId();
		if (searches.containsKey(id)) return;

		User<Player> user = users.getUser(player);
		if (user == null) return;

		Bounty bounty = user.getBounty();
		if (!atKillCap(bounty)) {
			bounty.addNotoriety(bounty.getAutoBountyIncrease(user.getLevel().getLevelValue(), peak));
		}

		searches.put(id, player);
		targeting.markSearching(id);
	}

	/**
	 * Whether the notoriety already reached {@code Bounty.Kill.Maximum}: a kill then adds nothing (EntityDamageListener),
	 * and an escape adds nothing either. A null cap is an unloaded config (a test double), so no cap applies.
	 */
	private static boolean atKillCap(Bounty bounty) {
		BigDecimal cap = Settings.getBountyMaxKill();
		return cap != null && bounty.getNotoriety().compareTo(cap) >= 0;
	}

	/** Whether {@code playerId} is on a post-escape search. */
	public boolean isSearching(UUID playerId) {
		return searches.containsKey(playerId);
	}

	/** Ends the search, if any; the player is no longer a cop target through it. The bounty stays on the user. */
	public void end(UUID playerId) {
		if (searches.remove(playerId) != null) targeting.clearSearching(playerId);
	}

	/**
	 * Ends every search: a shutdown or reload of the cops. No cop gives up here (nobody is left to); the HUD hides the bar
	 * on its next beat, since its player is no longer searched.
	 */
	public void endAll() {
		for (UUID playerId : new ArrayList<>(searches.keySet())) end(playerId);
	}

	/** The search ran out on its own: the cops give up, then the search ends. */
	public void expire(UUID playerId) {
		if (searches.containsKey(playerId) && giveUp != null) giveUp.accept(playerId);
		end(playerId);
	}

	/** The bounty shown on the HUD: posted escrow plus notoriety. Zero when not searching. */
	public BigDecimal bountyAmount(UUID playerId) {
		Player player = searches.get(playerId);
		if (player == null) return BigDecimal.ZERO;

		User<Player> user = users.getUser(player);
		if (user == null) return BigDecimal.ZERO;

		Bounty bounty = user.getBounty();
		return bounty.getPostedAmount().add(bounty.getNotoriety());
	}
}
