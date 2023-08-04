package me.luckyraven.command.sub;

import me.luckyraven.Gangland;
import me.luckyraven.account.Account;
import me.luckyraven.account.gang.Gang;
import me.luckyraven.bukkit.inventory.MultiInventory;
import me.luckyraven.command.CommandHandler;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.rank.Rank;
import me.luckyraven.util.color.Color;
import me.luckyraven.util.color.ColorUtil;
import me.luckyraven.util.color.MaterialType;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class DebugCommand extends CommandHandler {

	public DebugCommand(Gangland gangland) {
		super(gangland, "debug", false);
	}

	@Override
	protected void onExecute(Argument argument, CommandSender commandSender, String[] arguments) {
		commandSender.sendMessage("Test");
	}

	@Override
	protected void initializeArguments(Gangland gangland) {
		// user data
		Argument userData = new Argument("user-data", getArgumentTree(), (argument, sender, args) -> {
			if (sender instanceof Player player) {
				UserManager<Player> userManager = gangland.getInitializer().getUserManager();

				User<Player> user = userManager.getUser(player);

				for (Account<?, ?> account : user.getLinkedAccounts())
					sender.sendMessage(account.toString());

				player.sendMessage(user.toString());
			} else {
				sender.sendMessage("Does nothing yet!");
			}
		});

		// gang data
		Argument gangData = new Argument("gang-data", getArgumentTree(), (argument, sender, args) -> {
			for (Gang gang : gangland.getInitializer().getGangManager().getGangs().values())
				sender.sendMessage(gang.toString());
		});

		// rank data
		Argument rankData = new Argument("rank-data", getArgumentTree(), (argument, sender, args) -> {
			for (Rank rank : gangland.getInitializer().getRankManager().getRanks().values())
				sender.sendMessage(rank.toString());
		});

		// multi inventory
		Argument multiInv = new Argument("multi-inv", getArgumentTree(), (argument, sender, args) -> {
			if (sender instanceof Player player) {

				List<ItemStack> items = new ArrayList<>();

				for (Color color : Color.values()) {
					for (MaterialType type : MaterialType.values()) {
						items.add(new ItemStack(ColorUtil.getMaterialByColor(color.name(), type.name())));
					}
				}

				List<Material> swords = Arrays.stream(Material.values()).filter(
						material -> material.name().contains("SWORD")).toList();

				items.addAll(swords.stream().map(ItemStack::new).toList());

				MultiInventory multi = MultiInventory.dynamicMultiInventory(gangland, items, "&6Debug items", player);

				multi.open(player);
			} else {
				sender.sendMessage("How will you see the inventory?");
			}
		});

		// anvil gui
		Argument anvil = new Argument("anvil", getArgumentTree(), (argument, sender, args) -> {
			if (sender instanceof Player player) {
				// this api doesn't work for 1.20_R1
				new AnvilGUI.Builder().onClose(
						stateSnapshot -> stateSnapshot.getPlayer().sendMessage("You closed the inventory.")).onClick(
						(slot, stateSnapshot) -> {
							if (slot != AnvilGUI.Slot.OUTPUT) {
								return Collections.emptyList();
							}

							if (stateSnapshot.getText().equalsIgnoreCase("you")) {
								stateSnapshot.getPlayer().sendMessage("You have magical powers!");
								return List.of(AnvilGUI.ResponseAction.close());
							} else {
								return List.of(AnvilGUI.ResponseAction.replaceInputText("Try again"));
							}
						}).preventClose().text("What is the meaning of life?").title("Enter your answer.").plugin(
						gangland).open(player);
			}
		});

		// add sub arguments
		List<Argument> arguments = new ArrayList<>();

		arguments.add(userData);
		arguments.add(gangData);
		arguments.add(rankData);
		arguments.add(multiInv);
//		arguments.add(anvil);

		getArgument().addAllSubArguments(arguments);
	}

	@Override
	public void help(CommandSender sender, int page) {

	}

}
