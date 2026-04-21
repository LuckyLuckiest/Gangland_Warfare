package me.luckyraven.core.utilities;

import com.cryptomorin.xseries.messages.ActionBar;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Centralized action bar dispatcher with foreground/background priority.
 *
 * <ul>
 *   <li>{@link #send(Player, String)} and {@link #send(JavaPlugin, Player, String, long)} are <b>foreground</b> sends.
 *       They lock the action bar for the duration of the message so background displays cannot overwrite it.</li>
 *   <li>{@link #sendBackground(Player, String, int)} is a <b>background</b> send with a priority level. Each send
 *       claims the slot with a randomized grace window scaled to the sender's priority — lower-priority senders receive
 *       a longer window, giving them generous display time before a higher-priority sender can displace them. After
 *       grace expires, a strictly higher-priority background can take over and hold the slot.
 *       Use {@link #sendBackground(Player, String)} for the lowest priority (0).</li>
 * </ul>
 */
public final class ActionBarManager {

	/**
	 * Millisecond timestamp until which each player's action bar is reserved for foreground messages.
	 */
	private static final Map<UUID, Long> foregroundUntil = new ConcurrentHashMap<>();

	/**
	 * Active background slot per player. A background send is suppressed while a higher-priority background holds the
	 * slot after its grace window has passed.
	 */
	private static final Map<UUID, BackgroundSlot> backgroundSlot = new ConcurrentHashMap<>();

	/**
	 * How long (ms) a background send holds the slot after grace expires, blocking lower-priority senders.
	 */
	private static final long BACKGROUND_WINDOW_MS = 600L;

	/**
	 * Grace period bounds (ms). Each send computes a random grace duration linearly interpolated from
	 * {@code MAX_GRACE_MS} at priority 0 down to {@code MIN_GRACE_MS} at priority {@code PRIORITY_SCALE}, then
	 * randomized within 70–100% of that value. Lower-priority senders therefore hold the slot for noticeably longer
	 * before a higher-priority sender can displace them.
	 */
	private static final long MAX_GRACE_MS   = 4000L;
	private static final long MIN_GRACE_MS   = 400L;
	private static final int  PRIORITY_SCALE = 10;

	private ActionBarManager() { }

	/**
	 * Sends a foreground action bar and locks the slot for ~2.5 s (the natural action-bar fade window).
	 */
	public static void send(Player player, String message) {
		foregroundUntil.put(player.getUniqueId(), System.currentTimeMillis() + 2500L);
		ActionBar.sendActionBar(player, ChatUtil.color(message));
	}

	/**
	 * Sends a timed foreground action bar. The slot is locked for {@code duration} ticks plus a 500 ms fade buffer.
	 *
	 * @param duration display duration in ticks
	 */
	public static void send(JavaPlugin plugin, Player player, String message, long duration) {
		foregroundUntil.put(player.getUniqueId(), System.currentTimeMillis() + (duration * 50L) + 500L);
		ActionBar.sendActionBar(plugin, player, TextComponent.fromLegacy(ChatUtil.color(message)), duration);
	}

	/**
	 * Sends a background action bar at the given priority. The call is a no-op while a foreground lock is active. Each
	 * successful send claims the slot with a grace window computed by {@link #computeGrace(int)}. During grace,
	 * strictly higher-priority senders are blocked. Once grace expires, the normal rule applies: strictly
	 * lower-priority senders cannot override the current holder.
	 *
	 * @param priority higher value wins after grace; use 0 for passive displays, 10 for active-use HUDs
	 */
	public static void sendBackground(Player player, String message, int priority) {
		UUID id  = player.getUniqueId();
		long now = System.currentTimeMillis();

		if (foregroundUntil.getOrDefault(id, 0L) > now) return;

		BackgroundSlot slot = backgroundSlot.get(id);
		if (slot != null && slot.isActiveAt(now)) {
			// Grace active: block senders with strictly higher priority so the current holder gets its display time
			if (slot.isGraceActiveAt(now) && priority > slot.priority()) return;
			// Always: block senders with strictly lower priority from overriding a higher-priority holder
			if (slot.priority() > priority) return;
		}

		long graceUntilMs = now + computeGrace(priority);
		backgroundSlot.put(id, new BackgroundSlot(graceUntilMs + BACKGROUND_WINDOW_MS, priority, graceUntilMs));

		// Clear when displacing a different sender so the old message doesn't linger until the next send tick
		boolean displacing = slot != null && slot.isActiveAt(now) && slot.priority() != priority;
		if (displacing) ActionBar.clearActionBar(player);

		ActionBar.sendActionBar(player, ChatUtil.color(message));
	}

	/**
	 * Sends a background action bar at priority 0. The call is a no-op while a foreground lock or a higher-priority
	 * background is active after its grace window.
	 */
	public static void sendBackground(Player player, String message) {
		sendBackground(player, message, 0);
	}

	/**
	 * Computes a randomized grace duration for the given priority. The base value is linearly interpolated from
	 * {@code MAX_GRACE_MS} at priority 0 to {@code MIN_GRACE_MS} at priority {@code PRIORITY_SCALE}, then randomized in
	 * the range [70%, 100%] of that base to prevent equal-priority senders from syncing up.
	 */
	private static long computeGrace(int priority) {
		int  clampedPriority = Math.min(priority, PRIORITY_SCALE);
		long graceMax        = MAX_GRACE_MS - (long) clampedPriority * (MAX_GRACE_MS - MIN_GRACE_MS) / PRIORITY_SCALE;
		long graceMin        = (long) (graceMax * 0.7);
		return ThreadLocalRandom.current().nextLong(graceMin, graceMax + 1);
	}

	private record BackgroundSlot(long expiryMs, int priority, long graceUntilMs) {

		boolean isActiveAt(long now) {
			return expiryMs > now;
		}

		boolean isGraceActiveAt(long now) {
			return graceUntilMs > now;
		}
	}

}
