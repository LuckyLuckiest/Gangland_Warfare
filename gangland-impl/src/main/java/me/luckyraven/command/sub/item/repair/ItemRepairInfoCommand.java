package me.luckyraven.command.sub.item.repair;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.gadget.repair.RepairManager;
import me.luckyraven.gadget.repair.material.RepairMaterial;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.JsonFormatter;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

class ItemRepairInfoCommand extends SubArgument {

	private final Gangland            gangland;
	private final UserManager<Player> userManager;

	ItemRepairInfoCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, "info", tree, parent);

		this.gangland    = gangland;
		this.userManager = gangland.getInitializer().getUserManager();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			ItemStack itemStack = player.getInventory().getItemInMainHand();

			if (!RepairMaterial.isRepairMaterial(itemStack)) {
				user.sendMessage(GanglandChatUtil.prefixMessage("Not a repair item!"));
				return;
			}

			String         id            = RepairMaterial.getMaterialId(itemStack);
			RepairManager  repairManager = gangland.getInitializer().getRepairManager();
			RepairMaterial material      = repairManager.getMaterialManager().getMaterial(id);

			if (material == null) {
				user.sendMessage(GanglandChatUtil.prefixMessage("Repair item not registered: &c" + id));
				return;
			}

			String info = "&7Id&8: &b" + material.getId() +
			              "\n&7Uses&8: &b" + RepairMaterial.getCurrentUses(itemStack) +
			              "&7/&b" + material.getData().getMaxUses() +
			              "\n&7Restore Amount&8: &b" + material.getData().getRestoreAmount() +
			              "\n&7Restore Percent&8: &b" + material.getData().getRestorePercent() + "%";

			JsonFormatter jsonFormatter = new JsonFormatter();

			user.sendMessage(jsonFormatter.formatToJson(GanglandChatUtil.color(info), " ".repeat(3)));
		};
	}

}
