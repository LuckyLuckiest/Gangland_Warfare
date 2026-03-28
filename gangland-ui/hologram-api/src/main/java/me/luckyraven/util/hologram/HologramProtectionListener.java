package me.luckyraven.util.hologram;

import lombok.RequiredArgsConstructor;
import me.luckyraven.util.listener.ListenerHandler;
import org.bukkit.entity.ArmorStand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;

@ListenerHandler
@RequiredArgsConstructor
public class HologramProtectionListener implements Listener {

	private final HologramService hologramService;

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
		if (!isHologramArmorStand(event.getRightClicked())) return;

		event.setCancelled(true);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
		if (!(event.getRightClicked() instanceof ArmorStand armorStand) || !isHologramArmorStand(armorStand)) return;

		event.setCancelled(true);
	}

	private boolean isHologramArmorStand(ArmorStand armorStand) {
		// Check if this armor stand belongs to any of our holograms
		return hologramService.getHolograms().values()
				.stream().anyMatch(hologram -> hologram.getLines().contains(armorStand));
	}

}
