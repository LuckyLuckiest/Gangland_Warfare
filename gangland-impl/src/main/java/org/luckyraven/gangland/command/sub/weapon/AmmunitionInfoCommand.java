package org.luckyraven.gangland.command.sub.weapon;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.argument.Argument;
import org.luckyraven.gangland.command.argument.SubArgument;
import org.luckyraven.gangland.command.argument.types.OptionalArgument;
import org.luckyraven.gangland.core.TriConsumer;
import org.luckyraven.gangland.core.datastructure.JsonFormatter;
import org.luckyraven.gangland.core.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.gangland.util.GanglandChatUtil;
import org.luckyraven.gangland.weapon.ammo.Ammunition;
import org.luckyraven.gangland.weapon.ammo.AmmunitionManager;

class AmmunitionInfoCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;
	private final AmmunitionManager   ammunitionManager;

	protected AmmunitionInfoCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                                UserManager<Player> userManager,
	                                AmmunitionManager ammunitionManager) {
		super(gangland, "info", tree, parent);

		this.gangland          = gangland;
		this.tree              = tree;
		this.userManager       = userManager;
		this.ammunitionManager = ammunitionManager;

		ammunitionInfo();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			ItemStack itemStack = player.getInventory().getItemInMainHand();

			if (!Ammunition.isAmmunition(itemStack)) {
				user.sendMessage(Messages.INVALID_AMMO.toString().replace("%args%", itemStack.getType().name()));
				return;
			}

			Ammunition ammunition = Ammunition.getHeldAmmunition(ammunitionManager, itemStack);

			if (ammunition == null) {
				user.sendMessage(Messages.INVALID_AMMO.toString().replace("%args%", itemStack.getType().name()));
				return;
			}

			sendInfo(user, ammunition);
		};
	}

	private void ammunitionInfo() {
		Argument name = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			String     ammoName   = args[2];
			Ammunition ammunition = ammunitionManager.getAmmunition(ammoName);

			if (ammunition == null) {
				user.sendMessage(Messages.INVALID_AMMO.toString().replace("%args%", ammoName));
				return;
			}

			sendInfo(user, ammunition);
		}, sender -> ammunitionManager.getAmmunitionKeys()
				.stream().toList());

		this.addSubArgument(name);
	}

	private void sendInfo(User<Player> user, Ammunition ammunition) {
		JsonFormatter jsonFormatter = new JsonFormatter();
		user.sendMessage(jsonFormatter.formatToJson(GanglandChatUtil.color(buildInfo(ammunition)), " ".repeat(3)));
	}

	private String buildInfo(Ammunition ammunition) {
		StringBuilder info = new StringBuilder();
		info.append("&7Name&8: &b").append(ammunition.getName())
		    .append("\n&7Display Name&8: &b").append(ammunition.getDisplayName())
		    .append("\n&7Material&8: &b").append(ammunition.getMaterial().name())
		    .append("\n&7Custom Model Data&8: &b").append(ammunition.getCustomModelData());

		return info.toString();
	}

}
