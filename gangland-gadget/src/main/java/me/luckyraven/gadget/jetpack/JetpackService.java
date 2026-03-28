package me.luckyraven.gadget.jetpack;

import me.luckyraven.gadget.fuel.FuelService;
import me.luckyraven.util.item.wearable.Wearable;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages active jetpack sessions. Handles activation, deactivation, and lifecycle of jetpack flight for all players.
 */
public class JetpackService {

	private final Map<UUID, JetpackSession> activeSessions = new ConcurrentHashMap<>();
	private final FuelService               fuelService;
	private final JavaPlugin                plugin;

	public JetpackService(FuelService fuelService, JavaPlugin plugin) {
		this.fuelService = fuelService;
		this.plugin      = plugin;
	}

	/**
	 * Activates the jetpack for the given player. Creates a session and starts the tick task.
	 */
	public void activate(Player player, Wearable jetpackWearable) {
		if (activeSessions.containsKey(player.getUniqueId())) return;

		JetpackSession session = new JetpackSession(player, jetpackWearable);
		JetpackTask    task    = new JetpackTask(session, this, fuelService);
		session.setTask(task);

		activeSessions.put(player.getUniqueId(), session);
		task.runTaskTimer(plugin, 1L, 1L);

		player.setAllowFlight(true);
	}

	/**
	 * Deactivates the jetpack for the given player. Cancels the task and cleans up flight state.
	 */
	public void deactivate(Player player) {
		JetpackSession session = activeSessions.remove(player.getUniqueId());
		if (session == null) return;

		if (session.getTask() != null && !session.getTask().isCancelled()) {
			session.getTask().cancel();
		}

		if (player.isOnline()) {
			player.setAllowFlight(false);
			player.setFlying(false);
		}
	}

	/**
	 * Returns whether the given player has an active jetpack session.
	 */
	public boolean isActive(Player player) {
		return activeSessions.containsKey(player.getUniqueId());
	}

	/**
	 * Returns the active jetpack session for the player, or {@code null}.
	 */
	@Nullable
	public JetpackSession getSession(Player player) {
		return activeSessions.get(player.getUniqueId());
	}

	/**
	 * Deactivates all active jetpack sessions. Called on plugin disable.
	 */
	public void deactivateAll() {
		for (JetpackSession session : activeSessions.values()) {
			if (session.getTask() != null && !session.getTask().isCancelled()) {
				session.getTask().cancel();
			}
			Player player = session.getPlayer();
			if (player.isOnline()) {
				player.setAllowFlight(false);
				player.setFlying(false);
			}
		}
		activeSessions.clear();
	}

}
