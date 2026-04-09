package me.luckyraven.command.sub.item.wearable;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.gadget.wearable.WearableAddon;
import me.luckyraven.item.wearable.Wearable;
import me.luckyraven.item.wearable.WearableTrait;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.JsonFormatter;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.StringJoiner;

class ItemWearableInfoCommand extends SubArgument {

	private final UserManager<Player> userManager;
	private final WearableAddon       wearableAddon;

	ItemWearableInfoCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                        UserManager<Player> userManager,
	                        WearableAddon wearableAddon) {
		super(gangland, "info", tree, parent);

		this.userManager   = userManager;
		this.wearableAddon = wearableAddon;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			ItemStack itemStack = player.getInventory().getItemInMainHand();

			if (!Wearable.isRegisteredWearable(itemStack)) {
				user.sendMessage(GanglandChatUtil.prefixMessage("Not a registered wearable!"));
				return;
			}

			String key = Wearable.getWearableKey(itemStack);

			if (key == null) return;

			Wearable wearable = wearableAddon.getWearable(key);

			if (wearable == null) {
				user.sendMessage(GanglandChatUtil.prefixMessage("Wearable not registered: &c" + key));
				return;
			}

			StringBuilder               traitsBuilder = new StringBuilder();
			Map<WearableTrait, Integer> traits        = wearable.getTraits();

			if (traits == null || traits.isEmpty()) {
				traitsBuilder.append("&7none");
			} else {
				StringJoiner joiner = new StringJoiner("&7, ");
				for (Map.Entry<WearableTrait, Integer> entry : traits.entrySet()) {
					joiner.add("&b" + entry.getKey().getKey() + " &7(" + entry.getValue() + ")");
				}
				traitsBuilder.append(joiner);
			}

			String info = "&7Key&8: &b" + wearable.getWearableKey() + "\n&7Name&8: &b" + wearable.getName() +
			              "\n&7Material&8: &b" + wearable.getMaterial().name() + "\n&7Base Reduction&8: &b" +
			              (int) (wearable.getBaseDamageReduction() * 100) + "%" + "\n&7Traits&8: " + traitsBuilder;

			JsonFormatter jsonFormatter = new JsonFormatter();

			user.sendMessage(jsonFormatter.formatToJson(GanglandChatUtil.color(info), " ".repeat(3)));
		};
	}

}
