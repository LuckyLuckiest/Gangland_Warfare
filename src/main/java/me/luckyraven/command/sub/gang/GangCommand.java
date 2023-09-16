package me.luckyraven.command.sub.gang;

import com.cryptomorin.xseries.XMaterial;
import me.luckyraven.Gangland;
import me.luckyraven.bukkit.ItemBuilder;
import me.luckyraven.bukkit.inventory.InventoryHandler;
import me.luckyraven.bukkit.inventory.MultiInventory;
import me.luckyraven.command.CommandHandler;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.data.CommandInformation;
import me.luckyraven.data.account.gang.Gang;
import me.luckyraven.data.account.gang.GangManager;
import me.luckyraven.data.account.gang.Member;
import me.luckyraven.data.rank.Rank;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.util.InventoryUtil;
import me.luckyraven.util.color.ColorUtil;
import me.luckyraven.util.color.MaterialType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class GangCommand extends CommandHandler {

	public GangCommand(Gangland gangland) {
		super(gangland, "gang", true);

		List<CommandInformation> list = getCommands().entrySet()
		                                             .parallelStream()
		                                             .filter(entry -> entry.getKey().startsWith("gang"))
		                                             .sorted(Map.Entry.comparingByKey())
		                                             .map(Map.Entry::getValue)
		                                             .toList();
		getHelpInfo().addAll(list);
	}

	@Override
	protected void onExecute(Argument argument, CommandSender commandSender, String[] arguments) {
		UserManager<Player> userManager = getGangland().getInitializer().getUserManager();
//		GangManager         gangManager = getGangland().getInitializer().getGangManager();

		Player       player = (Player) commandSender;
		User<Player> user   = userManager.getUser(player);

		if (!user.hasGang())
//			gangStat(getGangland(), user, userManager, gangManager);
//		else
			help(commandSender, 1);
	}

	@Override
	protected void initializeArguments() {
		// create gang
		// glw gang create <name>
		Argument create = new GangCreateCommand(getGangland(), getArgumentTree(), getArgument());

		// delete gang
		// glw gang delete
		Argument delete = new GangDeleteCommand(getGangland(), getArgumentTree(), getArgument());

		// add user to gang
		// glw gang invite <name>
		GangInviteCommand addUser = new GangInviteCommand(getGangland(), getArgumentTree(), getArgument());

		// glw gang accept
		Argument acceptInvite = addUser.gangAccept();

		// remove user from gang
		// glw gang kick <name>
		Argument removeUser = new GangKickCommand(getGangland(), getArgumentTree(), getArgument());

		// leave the gang
		// glw gang leave
		Argument leave = new GangLeaveCommand(getGangland(), getArgumentTree(), getArgument());

		// promote user in gang
		// glw gang promote <name>
		Argument promoteUser = new GangPromoteCommand(getGangland(), getArgumentTree(), getArgument());

		// demote user in gang
		// glw gang demote <name>
		Argument demoteUser = new GangDemoteCommand(getGangland(), getArgumentTree(), getArgument());

		getArgument().addPermission(getPermission() + ".force_rank");

		// deposit money to gang
		// glw gang deposit <amount>
		Argument deposit = new GangDepositCommand(getGangland(), getArgumentTree(), getArgument());

		// withdraw money from gang
		// glw gang withdraw <amount>
		Argument withdraw = new GangWithdrawCommand(getGangland(), getArgumentTree(), getArgument());

		// balance of gang
		// glw gang balance
		Argument balance = new GangBalanceCommand(getGangland(), getArgumentTree(), getArgument());

		// change gang name
		// glw gang name <name>
		Argument name = new GangRenameCommand(getGangland(), getArgumentTree(), getArgument());

		// change gang description
		// opens an anvil with a paper that can change the title
		// glw gang desc
		Argument description = new GangDescriptionCommand(getGangland(), getArgumentTree(), getArgument());

		// gang ally
		// glw gang ally <request/abandon> <id>
		Argument ally = new GangAllyCommand(getGangland(), getArgumentTree(), getArgument());

		// change gang display name
		// glw gang display <name>
		Argument display = new GangDisplayCommand(getGangland(), getArgumentTree(), getArgument());

		// change gang color using gui
		// glw gang color
		Argument color = new GangColorCommand(getGangland(), getArgumentTree(), getArgument());

		// add sub arguments
		List<Argument> arguments = new ArrayList<>();

		arguments.add(create);
		arguments.add(delete);

		arguments.add(addUser);
		arguments.add(acceptInvite);

		arguments.add(removeUser);
		arguments.add(leave);

		arguments.add(promoteUser);
		arguments.add(demoteUser);

		arguments.add(deposit);
		arguments.add(withdraw);
		arguments.add(balance);

		arguments.add(name);
		arguments.add(description);

		arguments.add(ally);

		arguments.add(display);
		arguments.add(color);

		getArgument().addAllSubArguments(arguments);
	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Gang");
	}

	private Material itemToBalance(Gang gang) {
		double balance    = gang.getEconomy().getBalance();
		double maxBalance = SettingAddon.getGangMaxBalance();

		// 1 max balance
		if (balance >= maxBalance) return XMaterial.EMERALD_BLOCK.parseMaterial();
			// 3/4 max balance
		else if (balance >= (double) 3 / 4 * maxBalance) return XMaterial.DIAMOND_BLOCK.parseMaterial();
			// 1/2 max balance
		else if (balance >= (double) 1 / 2 * maxBalance) return XMaterial.GOLD_BLOCK.parseMaterial();

		// 1/4 max balance
		return XMaterial.IRON_BLOCK.parseMaterial();
	}

	private void gangStat(Gangland gangland, User<Player> user, UserManager<Player> userManager,
	                      GangManager gangManager) {
		Gang gang = gangManager.getGang(user.getGangId());
		InventoryHandler gui = new InventoryHandler(gangland, "&6&l" + gang.getDisplayNameString() + "&r gang", 5 * 9,
		                                            user, false);

		// balance
		Material material = itemToBalance(gang);

		gui.setItem(11, material, "&bBalance", new ArrayList<>(
				List.of(String.format("&e%s%s", SettingAddon.getMoneySymbol(),
				                      SettingAddon.formatDouble(gang.getEconomy().getBalance())))), true, false);

		// id
		gui.setItem(13, XMaterial.CRAFTING_TABLE.parseMaterial(), "&bID", new ArrayList<>(List.of("&e" + gang.getId())),
		            false, false);

		// description
		gui.setItem(15, XMaterial.PAPER.parseMaterial(), "&bDescription",
		            new ArrayList<>(List.of("&e" + gang.getDescription())), false, false,
		            (player, inventory, items) -> {
			            player.performCommand(Argument.getArgumentSequence(Objects.requireNonNull(
					            getArgumentTree().find(new Argument("desc", getArgumentTree())))));
		            });

		// members
		gui.setItem(19, XMaterial.PLAYER_HEAD.parseMaterial(), "&bMembers", new ArrayList<>(
				            List.of("&a" + gang.getOnlineMembers(userManager).size() + "&7/&e" + gang.getGroup().size())), false,
		            false, (player, inventory, item) -> {
					User<Player>    user1 = userManager.getUser(player);
					Gang            gang1 = gangManager.getGang(userManager.getUser(player).getGangId());
					List<ItemStack> items = new ArrayList<>();

					for (Member member : gang1.getGroup()) {
						OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(member.getUuid());
						Rank          userRank      = member.getRank();
						String        rank          = "null";

						if (userRank != null) rank = userRank.getName();

						List<String> data = new ArrayList<>();
						data.add("&7rank:&e " + rank);
						data.add("&7Contribution:&e " + member.getContribution());
						data.add("&7Joined:&e " + member.getGangJoinDateString());

						ItemBuilder itemBuilder = new ItemBuilder(XMaterial.PLAYER_HEAD.parseMaterial()).setDisplayName(
								"&b" + offlinePlayer.getName()).setLore(data);

						itemBuilder.modifyNBT(nbt -> nbt.setString("SkullOwner", offlinePlayer.getName()));

						items.add(itemBuilder.build());
					}

					MultiInventory multi = MultiInventory.dynamicMultiInventory(gangland, user1, items,
					                                                            "&6&lGang Members", false, false, null);

					multi.open(player);
				});

		// bounty
		gui.setItem(22, XMaterial.BLAZE_ROD.parseMaterial(), "&bBounty", new ArrayList<>(
				List.of(String.format("&e%s%s", SettingAddon.getMoneySymbol(),
				                      SettingAddon.formatDouble(gang.getBounty().getAmount())))), true, false);

		// ally
		gui.setItem(25, XMaterial.REDSTONE.parseMaterial(), "&bAlly", List.of("&e" + gang.getAlly().size()), false,
		            false, (player, inventory, item) -> {
					User<Player>    user1 = userManager.getUser(player);
					Gang            gang1 = gangManager.getGang(userManager.getUser(player).getGangId());
					List<ItemStack> items = new ArrayList<>();

					for (Gang ally : gang1.getAlly()) {
						List<String> data = new ArrayList<>();
						data.add("&7ID:&e " + ally.getId());
						data.add(String.format("&7Members:&a %d&7/&e%d", ally.getOnlineMembers(userManager).size(),
						                       ally.getGroup().size()));
						data.add("&7Created:&e " + ally.getDateCreatedString());

						ItemBuilder itemBuilder = new ItemBuilder(XMaterial.REDSTONE.parseMaterial()).setDisplayName(
								"&b" + ally.getDisplayNameString()).setLore(data);

						items.add(itemBuilder.build());
					}

					MultiInventory multi = MultiInventory.dynamicMultiInventory(gangland, user1, items,
					                                                            "&6&lGang Allies", false, false, null);

					multi.open(player);
				});

		// date created
		gui.setItem(29, XMaterial.WRITABLE_BOOK.parseMaterial(), "&bCreated",
		            new ArrayList<>(List.of("&e" + gang.getDateCreatedString())), true, false);

		// color
		gui.setItem(31, ColorUtil.getMaterialByColor(gang.getColor(), MaterialType.WOOL.name()), "&bColor",
		            new ArrayList<>(List.of("&e" + gang.getColor().toLowerCase().replace("_", " "))), false, false,
		            (player, inventory, item) -> {
			            player.performCommand(Argument.getArgumentSequence(Objects.requireNonNull(
					            getArgumentTree().find(new Argument("color", getArgumentTree())))));
		            });

		gui.setItem(33, ColorUtil.getMaterialByColor(gang.getColor(), MaterialType.BANNER.name()), "&bStatistics",
		            new ArrayList<>(List.of("&eGang stats")), false, false);

		InventoryUtil.fillInventory(gui);

		gui.open(user.getUser());
	}

}
