package me.luckyraven.copsncrooks.listener.police;

import lombok.RequiredArgsConstructor;
import me.luckyraven.copsncrooks.detainment.DetainmentService;
import me.luckyraven.copsncrooks.detainment.paperwork.DetainmentGuiAccess;
import me.luckyraven.copsncrooks.detainment.paperwork.PaperworkItemFactory;
import me.luckyraven.copsncrooks.detainment.paperwork.PaperworkView;
import me.luckyraven.copsncrooks.detainment.transit.TransitService;
import me.luckyraven.copsncrooks.jail.JailService;
import me.luckyraven.util.listener.ListenerHandler;
import me.luckyraven.weapon.events.projectile.WeaponShootEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.inventory.ItemStack;

@ListenerHandler
@RequiredArgsConstructor
public class DetainmentListener implements Listener {

	private final DetainmentService    detainmentService;
	private final JailService          jailService;
	private final TransitService       transitService;
	private final PaperworkItemFactory paperworkItemFactory;
	private final PaperworkView        paperworkView;

	private static boolean isOwnInventoryClick(InventoryClickEvent event) {
		// The native player inventory view is the CRAFTING type (2x2 grid + armour + hotbar + main).
		// When it's open, the clicked inventory is always the player's own — any external inventory (chest, trader)
		// would be a different view type.
		return event.getView().getType() == InventoryType.CRAFTING;
	}

	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void onWeaponShoot(WeaponShootEvent event) {
		if (!(event.getShooter() instanceof Player player)) return;
		if (!detainmentService.isRestrained(player)) return;

		event.setCancelled(true);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onJoin(PlayerJoinEvent event) {
		detainmentService.handleJoin(event.getPlayer());
		transitService.resumeOnJoin(event.getPlayer());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onQuit(PlayerQuitEvent event) {
		detainmentService.handleQuit(event.getPlayer());
		DetainmentGuiAccess.revoke(event.getPlayer().getUniqueId());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onDeath(PlayerDeathEvent event) {
		Player player = event.getEntity();
		if (!detainmentService.isHandcuffed(player)) return;
		// Death short-circuits the transit timer so respawn lands the player inside jail with items seized.
		transitService.commitNow(player);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onRespawn(PlayerRespawnEvent event) {
		Player player = event.getPlayer();

		if (!detainmentService.isJailed(player)) return;

		var jail = jailService.getJailRegistry().getJailLocation(player.getUniqueId());
		if (jail != null) event.setRespawnLocation(jail);

		detainmentService.handleRespawn(player);
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onMove(PlayerMoveEvent event) {
		Player player = event.getPlayer();

		if (!detainmentService.isRestrained(player)) return;
		if (event.getTo() == null) return;

		if (event.getFrom().getX() == event.getTo().getX() && event.getFrom().getY() == event.getTo().getY() &&
		    event.getFrom().getZ() == event.getTo().getZ()) {
			return;
		}

		player.setSprinting(false);
		detainmentService.tickVisuals(player);
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onInventoryOpen(InventoryOpenEvent event) {
		if (!(event.getPlayer() instanceof Player player)) return;
		if (!detainmentService.isRestrained(player)) return;
		if (DetainmentGuiAccess.isAuthorized(player.getUniqueId())) return;

		event.setCancelled(true);
		player.closeInventory();
		detainmentService.tickVisuals(player);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onInventoryClose(InventoryCloseEvent event) {
		if (!(event.getPlayer() instanceof Player player)) return;
		DetainmentGuiAccess.revoke(player.getUniqueId());
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onInventoryClick(InventoryClickEvent event) {
		if (!(event.getWhoClicked() instanceof Player player)) return;
		if (!detainmentService.isRestrained(player)) return;

		// While jailed, allow the player to click around their own inventory so they can grab the paperwork item.
		if (detainmentService.isJailed(player) && isOwnInventoryClick(event)) return;

		event.setCancelled(true);
		detainmentService.tickVisuals(player);
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onInventoryDrag(InventoryDragEvent event) {
		if (!(event.getWhoClicked() instanceof Player player)) return;
		if (!detainmentService.isRestrained(player)) return;

		if (detainmentService.isJailed(player) &&
		    event.getView().getType() == InventoryType.CRAFTING) return;

		event.setCancelled(true);
		detainmentService.tickVisuals(player);
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onCraft(CraftItemEvent event) {
		if (!(event.getWhoClicked() instanceof Player player)) return;
		if (!detainmentService.isRestrained(player)) return;

		event.setCancelled(true);
		detainmentService.tickVisuals(player);
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onInteract(PlayerInteractEvent event) {
		Player player = event.getPlayer();

		if (!detainmentService.isRestrained(player)) return;

		Action action = event.getAction();
		if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK &&
		    action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK && action != Action.PHYSICAL) {
			return;
		}

		// Right-click on paperwork item → open PaperworkView (bypasses the normal restraint cancellation).
		if (detainmentService.isJailed(player) &&
		    (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK)) {
			ItemStack inHand = event.getItem();
			if (paperworkItemFactory.isPaperwork(inHand)) {
				event.setCancelled(true);
				paperworkView.open(player);
				return;
			}
		}

		event.setCancelled(true);
		detainmentService.tickVisuals(player);
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onInteractEntity(PlayerInteractEntityEvent event) {
		Player player = event.getPlayer();

		if (!detainmentService.isRestrained(player)) return;

		event.setCancelled(true);
		detainmentService.tickVisuals(player);
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
		Player player = event.getPlayer();

		if (!detainmentService.isRestrained(player)) return;

		event.setCancelled(true);
		detainmentService.tickVisuals(player);
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
		Player player = event.getPlayer();

		if (!detainmentService.isRestrained(player)) return;

		event.setCancelled(true);
		detainmentService.tickVisuals(player);
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onDrop(PlayerDropItemEvent event) {
		Player player = event.getPlayer();

		if (!detainmentService.isRestrained(player)) return;

		event.setCancelled(true);
		detainmentService.tickVisuals(player);
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onPickup(EntityPickupItemEvent event) {
		if (!(event.getEntity() instanceof Player player)) return;
		if (!detainmentService.isRestrained(player)) return;

		event.setCancelled(true);
		detainmentService.tickVisuals(player);
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onBreak(BlockBreakEvent event) {
		Player player = event.getPlayer();

		if (!detainmentService.isRestrained(player)) return;

		event.setCancelled(true);
		detainmentService.tickVisuals(player);
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onPlace(BlockPlaceEvent event) {
		Player player = event.getPlayer();

		if (!detainmentService.isRestrained(player)) return;

		event.setCancelled(true);
		detainmentService.tickVisuals(player);
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onSwapHand(PlayerSwapHandItemsEvent event) {
		Player player = event.getPlayer();

		if (!detainmentService.isRestrained(player)) return;
		if (detainmentService.isJailed(player)) return;

		event.setCancelled(true);
		detainmentService.tickVisuals(player);
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onHeldSlotChange(PlayerItemHeldEvent event) {
		Player player = event.getPlayer();

		if (!detainmentService.isRestrained(player)) return;
		// Jailed players must be able to select the paperwork item via hotbar number keys.
		if (detainmentService.isJailed(player)) return;

		event.setCancelled(true);
		detainmentService.tickVisuals(player);
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onCommand(PlayerCommandPreprocessEvent event) {
		Player player = event.getPlayer();

		if (!detainmentService.isRestrained(player)) return;
		if (player.hasPermission(detainmentService.getCommandBypassPermission())) return;

		event.setCancelled(true);
		detainmentService.tickVisuals(player);
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onVehicleEnter(VehicleEnterEvent event) {
		if (!(event.getEntered() instanceof Player player)) return;
		if (!detainmentService.isRestrained(player)) return;

		event.setCancelled(true);
		detainmentService.tickVisuals(player);
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onVehicleExit(VehicleExitEvent event) {
		if (!(event.getExited() instanceof Player player)) return;
		if (!detainmentService.isRestrained(player)) return;

		event.setCancelled(true);
		detainmentService.tickVisuals(player);
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onGlide(EntityToggleGlideEvent event) {
		if (!(event.getEntity() instanceof Player player)) return;
		if (!detainmentService.isRestrained(player)) return;

		event.setCancelled(true);
		detainmentService.tickVisuals(player);
	}
}