package me.luckyraven.command.sub.weapon;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.core.TriConsumer;
import me.luckyraven.core.datastructure.JsonFormatter;
import me.luckyraven.core.datastructure.Tree;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.gang.user.User;
import me.luckyraven.gang.user.UserManager;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.weapon.ammo.Ammunition;
import me.luckyraven.weapon.ammo.AmmunitionManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

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
