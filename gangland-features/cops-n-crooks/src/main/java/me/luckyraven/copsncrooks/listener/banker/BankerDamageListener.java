package me.luckyraven.copsncrooks.listener.banker;

import lombok.RequiredArgsConstructor;
import me.luckyraven.copsncrooks.npc.banker.BankerManager;
import me.luckyraven.copsncrooks.npc.banker.BankerNpc;
import me.luckyraven.copsncrooks.npc.banker.config.BankerSettings;
import me.luckyraven.core.listener.ListenerHandler;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * Belt-and-suspenders damage guard for banker NPCs. Citizens' {@code setProtected} and the {@code LivingEntity}
 * invulnerable flag already fire at spawn, but this listener cancels {@link EntityDamageEvent} at the Bukkit layer so
 * mis-configured world plugins or custom damage sources can't bypass that protection. When
 * {@link BankerSettings#isInvulnerable()} is {@code false} the listener becomes a no-op — the admin opted in to a
 * killable banker, and since no respawn service exists the banker stays gone until re-created.
 */
@ListenerHandler
@RequiredArgsConstructor
public class BankerDamageListener implements Listener {

	private final BankerManager  bankerManager;
	private final BankerSettings settings;

	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	public void onBankerDamage(EntityDamageEvent event) {
		if (!settings.isInvulnerable()) return;

		BankerNpc banker = bankerManager.getByEntity(event.getEntity());
		if (banker == null) return;

		event.setCancelled(true);
	}

}
