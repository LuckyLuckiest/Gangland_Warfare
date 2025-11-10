package me.luckyraven.listener.player;

import me.luckyraven.Gangland;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserDataInitEvent;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.feature.phone.Phone;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.util.listener.ListenerHandler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

@ListenerHandler(condition = "isPhoneEnabled")
public class PhoneItem implements Listener {

	private final Gangland            gangland;
	private final UserManager<Player> userManager;

	public PhoneItem(Gangland gangland) {
		this.gangland    = gangland;
		this.userManager = gangland.getInitializer().getUserManager();
	}

	@EventHandler
	public void onJoinGivePhone(UserDataInitEvent event) {
		Player player = event.getPlayer();
		Phone  phone  = new Phone(gangland, event.getUser(), SettingAddon.getPhoneName());

		// when the user joins, check if their inventory contains the specific nbt item
		// if they don't have the item then add it to the inventory
		if (!Phone.hasPhone(player)) phone.addPhoneToInventory(player);

		event.getUser().setPhone(phone);
	}

	@EventHandler
	public void onPhoneInventoryInteract(InventoryClickEvent event) {
		// Prevent the phone item from being thrown or moved in the inventory
		if (event.getClickedInventory() == null) return;

		Inventory clickedInventory = event.getClickedInventory();
		ItemStack clickedItem      = event.getCurrentItem();

		if (clickedItem == null || clickedItem.getType().name().contains("AIR") || clickedItem.getAmount() == 0) return;
		if (clickedInventory.equals(event.getWhoClicked().getInventory())) if (!Phone.isPhone(clickedItem)) return;
		if (!SettingAddon.isPhoneMovable()) return;

		event.setCancelled(true);
	}

	@EventHandler
	public void onPhoneItemInteract(PlayerInteractEvent event) {
		ItemStack heldItem = event.getItem();

		if (heldItem == null) return;
		if (!Phone.isPhone(heldItem)) return;

		Player       player = event.getPlayer();
		User<Player> user   = userManager.getUser(player);
		Phone        phone  = user.getPhone();

		if (phone == null) return;

		if (event.getAction() == Action.RIGHT_CLICK_AIR ||
			event.getAction() == Action.RIGHT_CLICK_BLOCK) phone.openInventory();

		event.setCancelled(true);
	}

	@EventHandler
	public void onPhoneItemDrop(PlayerDropItemEvent event) {
		ItemStack droppedItem = event.getItemDrop().getItemStack();

		if (!Phone.isPhone(droppedItem)) return;
		if (!SettingAddon.isPhoneDroppable()) return;

		event.setCancelled(true);
	}

	@EventHandler
	public void beforePlayerDeath(EntityDamageEvent event) {
		if (!(event.getEntity() instanceof Player player)) return;

		double remainingHealth = player.getHealth() - event.getFinalDamage();
		if (remainingHealth > 0) return;

		if (!SettingAddon.isPhoneDroppable()) return;

		User<Player> user = userManager.getUser(player);

		if (Phone.hasPhone(player)) {
			// locate where the item is and remove it
			ItemStack[] contents = player.getInventory().getContents();

			for (int i = 0; i < contents.length; i++)
				if (contents[i] != null && Phone.isPhone(contents[i])) {
					player.getInventory().setItem(i, null);
					break;
				}
		}

		user.setPhone(null);
	}

	@EventHandler
	public void onPlayerRespawn(PlayerRespawnEvent event) {
		Player       player = event.getPlayer();
		User<Player> user   = userManager.getUser(player);

		if (user.getPhone() == null && !Phone.hasPhone(player)) {
			Phone phone = new Phone(gangland, user, SettingAddon.getPhoneName());

			phone.addPhoneToInventory(player);
			user.setPhone(phone);
		}
	}

}
