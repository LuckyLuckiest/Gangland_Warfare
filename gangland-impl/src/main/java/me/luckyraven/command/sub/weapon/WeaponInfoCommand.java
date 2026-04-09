package me.luckyraven.command.sub.weapon;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.JsonFormatter;
import me.luckyraven.util.datastructure.Tree;
import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.WeaponManager;
import me.luckyraven.weapon.configuration.WeaponAddon;
import me.luckyraven.weapon.dto.AmmunitionData;
import me.luckyraven.weapon.dto.ReloadData;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

class WeaponInfoCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;
	private final WeaponManager       weaponManager;
	private final WeaponAddon         weaponAddon;

	protected WeaponInfoCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                            UserManager<Player> userManager,
	                            WeaponManager weaponManager,
	                            WeaponAddon weaponAddon) {
		super(gangland, "info", tree, parent);

		this.gangland      = gangland;
		this.tree          = tree;
		this.userManager   = userManager;
		this.weaponManager = weaponManager;
		this.weaponAddon   = weaponAddon;

		weaponInfo();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			ItemStack itemStack = player.getInventory().getItemInMainHand();
			Weapon    weapon    = weaponManager.validateAndGetWeapon(player, itemStack);

			if (weapon == null) {
				user.sendMessage(Messages.INVALID_WEAPON.toString().replace("%args%", itemStack.getType().name()));
				return;
			}

			sendInfo(user, weapon);
		};
	}

	private void weaponInfo() {
		Argument name = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			String weaponName = args[2];
			Weapon weapon     = weaponAddon.getWeapon(weaponName);

			if (weapon == null) {
				user.sendMessage(Messages.INVALID_WEAPON.toString().replace("%args%", weaponName));
				return;
			}

			sendInfo(user, weapon);
		}, sender -> weaponAddon.getWeaponKeys()
				.stream().toList());

		this.addSubArgument(name);
	}

	private void sendInfo(User<Player> user, Weapon weapon) {
		JsonFormatter jsonFormatter = new JsonFormatter();
		user.sendMessage(jsonFormatter.formatToJson(GanglandChatUtil.color(buildInfo(weapon)), " ".repeat(3)));
	}

	private String buildInfo(Weapon weapon) {
		StringBuilder info = new StringBuilder();
		info.append("&7Name&8: &b").append(weapon.getName())
		    .append("\n&7Display Name&8: &b").append(weapon.getDisplayName())
		    .append("\n&7Category&8: &b").append(weapon.getCategory())
		    .append("\n&7Material&8: &b").append(weapon.getMaterial().name())
		    .append("\n&7Custom Model Data&8: &b").append(weapon.getCustomModelData())
		    .append("\n&7Durability&8: &b").append(weapon.getCurrentDurability())
		    .append("&7/&b").append(weapon.getDurability());

		AmmunitionData ammunitionData = weapon.getAmmunitionData();
		if (ammunitionData != null) {
			info.append("\n&7Magazine&8: &b").append(weapon.getCurrentMagCapacity())
			    .append("&7/&b").append(ammunitionData.getMaxMagCapacity())
			    .append("\n&7Ammo Type&8: &b").append(ammunitionData.getAmmoType());
		}

		ReloadData reloadData = weapon.getReloadData();
		if (reloadData != null) {
			info.append("\n&7Reload Type&8: &b").append(reloadData.getType());
		}

		if (weapon.getCurrentSelectiveFire() != null) {
			info.append("\n&7Selective Fire&8: &b").append(weapon.getCurrentSelectiveFire());
		}

		return info.toString();
	}

}
