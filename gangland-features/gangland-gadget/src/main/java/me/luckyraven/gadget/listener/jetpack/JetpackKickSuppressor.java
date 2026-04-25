package me.luckyraven.gadget.listener.jetpack;

import lombok.RequiredArgsConstructor;
import me.luckyraven.core.bean.autowire.AutowireTarget;
import me.luckyraven.core.bean.listener.ListenerHandler;
import me.luckyraven.gadget.jetpack.JetpackService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerKickEvent;

import java.util.Locale;

/**
 * Suppresses the vanilla "Flying is not enabled on this server" kick (NMS {@code aboveGroundTickCount} > threshold)
 * while a player has an active jetpack session. The jetpack drives motion via velocity rather than creative-flight
 * mode, so the floating counter still ticks up even after {@code setAllowFlight(true)}.
 *
 * <p>Spigot's {@link PlayerKickEvent} exposes only the reason string (no {@code Cause} enum — that's Paper-only), so
 * we match on the English reason text. NMS resolves both the toggle-flight kick and the floating-too-long kick from the
 * same translation key {@code multiplayer.disconnect.flying}, so a substring match on "flying" covers both — and both
 * should be canceled while a jetpack is active.
 */
@ListenerHandler
@RequiredArgsConstructor
@AutowireTarget({JetpackService.class})
public class JetpackKickSuppressor implements Listener {

	private final JetpackService jetpackService;

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onKick(PlayerKickEvent event) {
		if (!jetpackService.isActive(event.getPlayer())) return;
		if (!event.getReason().toLowerCase(Locale.ROOT).contains("flying")) return;
		event.setCancelled(true);
	}

}
