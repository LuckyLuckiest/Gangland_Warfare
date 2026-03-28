package me.luckyraven.gadget.listener.jetpack;

import me.luckyraven.gadget.jetpack.JetpackService;
import me.luckyraven.item.wearable.Wearable;
import me.luckyraven.util.autowire.AutowireTarget;
import me.luckyraven.util.listener.ListenerHandler;
import me.luckyraven.weapon.wearable.WearableService;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Handles jetpack activation via spacebar (double-tap space triggers {@link PlayerToggleFlightEvent}).
 *
 * <p>This listener:
 * <ul>
 *   <li>Intercepts flight toggle when wearing a jetpack — cancels vanilla flight and toggles jetpack</li>
 *   <li>Keeps {@code allowFlight} enabled while wearing a jetpack so the toggle event fires</li>
 *   <li>Cleans up on player disconnect</li>
 * </ul>
 */
@ListenerHandler
@AutowireTarget({WearableService.class, JetpackService.class})
public class JetpackActivateListener implements Listener {

	private final WearableService wearableService;
	private final JetpackService  jetpackService;

	public JetpackActivateListener(WearableService wearableService, JetpackService jetpackService) {
		this.wearableService = wearableService;
		this.jetpackService  = jetpackService;
	}

	/**
	 * Intercepts the flight toggle (double-tap space) when wearing a jetpack.
	 */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onToggleFlight(PlayerToggleFlightEvent event) {
		Player player = event.getPlayer();
		if (isCreativeOrSpectator(player)) return;

		Wearable wearable = resolveChestplateWearable(player);
		if (wearable == null || !wearable.isJetpack()) return;

		event.setCancelled(true);
		player.setFlying(false);

		if (jetpackService.isActive(player)) {
			jetpackService.deactivate(player);
		} else {
			jetpackService.activate(player, wearable);
		}
	}

	/**
	 * Keeps {@code allowFlight} enabled while the player wears a jetpack so the double-tap space event triggers.
	 * Removes it if the jetpack is no longer equipped.
	 */
	@EventHandler
	public void onMove(PlayerMoveEvent event) {
		Player player = event.getPlayer();
		if (isCreativeOrSpectator(player)) return;

		Wearable wearable = resolveChestplateWearable(player);

		if (wearable != null && wearable.isJetpack()) {
			if (!player.getAllowFlight()) {
				player.setAllowFlight(true);
			}
		} else {
			// Not wearing a jetpack — deactivate if active
			if (jetpackService.isActive(player)) {
				jetpackService.deactivate(player);
			}
			if (player.getAllowFlight() && !isCreativeOrSpectator(player)) {
				player.setAllowFlight(false);
			}
		}
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		Player player = event.getPlayer();
		if (jetpackService.isActive(player)) {
			jetpackService.deactivate(player);
		}
	}

	private Wearable resolveChestplateWearable(Player player) {
		ItemStack chestplate = player.getInventory().getChestplate();
		if (chestplate == null || chestplate.getType().isAir()) return null;
		return wearableService.resolveWearable(chestplate);
	}

	private boolean isCreativeOrSpectator(Player player) {
		return player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR;
	}

}
