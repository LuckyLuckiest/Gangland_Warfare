package me.luckyraven.listener.player;

import me.luckyraven.Gangland;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserDataInitEvent;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.feature.phone.Phone;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.util.listener.ListenerHandler;
import me.luckyraven.util.listener.ListenerPriority;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;

@ListenerHandler(condition = "isPhoneEnabled", priority = ListenerPriority.LOW)
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
		Phone  phone  = new Phone(gangland, SettingAddon.getPhoneName());

		// when the user joins, check if their inventory contains the specific nbt item
		// if they don't have the item then add it to the inventory
		if (!Phone.hasPhone(player)) phone.addPhoneToInventory(player);

		event.getUser().setPhone(phone);
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

			for (int i = 0; i < contents.length; i++) {
				if (contents[i] != null && Phone.isPhone(contents[i])) {
					player.getInventory().setItem(i, null);
					break;
				}
			}
		}

		user.setPhone(null);
	}

	@EventHandler
	public void onPlayerRespawn(PlayerRespawnEvent event) {
		Player       player = event.getPlayer();
		User<Player> user   = userManager.getUser(player);

		if (!(user.getPhone() == null && !Phone.hasPhone(player))) return;

		Phone phone = new Phone(gangland, SettingAddon.getPhoneName());

		phone.addPhoneToInventory(player);
		user.setPhone(phone);
	}

}
