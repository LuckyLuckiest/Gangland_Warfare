package me.luckyraven.command.sub.gang;

import com.cryptomorin.xseries.XMaterial;
import me.luckyraven.Gangland;
import me.luckyraven.command.Command;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.ArgumentUtil;
import me.luckyraven.core.ItemBuilder;
import me.luckyraven.core.autowire.bean.Qualifier;
import me.luckyraven.core.color.ColorUtil;
import me.luckyraven.core.color.MaterialType;
import me.luckyraven.core.command.CommandHandler;
import me.luckyraven.data.account.gang.Gang;
import me.luckyraven.data.account.gang.GangAlliance;
import me.luckyraven.data.account.gang.GangManager;
import me.luckyraven.data.account.gang.member.Member;
import me.luckyraven.data.account.gang.member.MemberManager;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.data.rank.Rank;
import me.luckyraven.data.rank.RankManager;
import me.luckyraven.database.GanglandDatabase;
import me.luckyraven.economy.bank.Currency;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.inventory.InventoryHandler;
import me.luckyraven.inventory.multi.MultiInventory;
import me.luckyraven.inventory.multi.MultiInventoryCreation;
import me.luckyraven.inventory.part.ButtonTags;
import me.luckyraven.inventory.part.Fill;
import me.luckyraven.inventory.util.InventoryUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@CommandHandler(condition = "isGangEnabled")
public final class GangCommand extends Command {

	private final UserManager<Player>        userManager;
	private final UserManager<OfflinePlayer> offlineUserManager;
	private final GangManager                gangManager;
	private final MemberManager              memberManager;
	private final RankManager                rankManager;
	private final GanglandDatabase           ganglandDatabase;

	public GangCommand(Gangland gangland,
	                   @Qualifier("online") UserManager<Player> userManager,
	                   @Qualifier("offline") UserManager<OfflinePlayer> offlineUserManager,
	                   GangManager gangManager,
	                   MemberManager memberManager,
	                   RankManager rankManager,
	                   GanglandDatabase ganglandDatabase) {
		super(gangland, "gang", true);

		this.userManager        = userManager;
		this.offlineUserManager = offlineUserManager;
		this.gangManager        = gangManager;
		this.memberManager      = memberManager;
		this.rankManager        = rankManager;
		this.ganglandDatabase   = ganglandDatabase;

		var list = getCommands().entrySet()
				.stream()
				.filter(entry -> entry.getKey().startsWith("gang"))
				.sorted(Map.Entry.comparingByKey())
				.map(Map.Entry::getValue)
				.toList();
		getHelpInfo().addAll(list);
	}

	@Override
	protected void onExecute(Argument argument, CommandSender commandSender, String[] arguments) {
		Player       player = (Player) commandSender;
		User<Player> user   = userManager.getUser(player);

		if (user == null) return;

		if (!user.hasGang()) help(commandSender, 1);
	}

	@Override
	protected void initializeArguments() {
		Argument create = new GangCreateCommand(getGangland(), getArgumentTree(), getArgument(), userManager,
		                                        memberManager, gangManager, rankManager, ganglandDatabase);
		Argument delete = new GangDeleteCommand(getGangland(), getArgumentTree(), getArgument(), userManager,
		                                        memberManager, gangManager, rankManager, ganglandDatabase);
		GangInviteCommand addUser = new GangInviteCommand(getGangland(), getArgumentTree(), getArgument(), userManager,
		                                                  memberManager, gangManager, rankManager);
		Argument acceptInvite = addUser.gangAccept();
		Argument removeUser = new GangKickCommand(getGangland(), getArgumentTree(), getArgument(), userManager,
		                                          offlineUserManager, memberManager, gangManager, rankManager,
		                                          ganglandDatabase);
		Argument leave = new GangLeaveCommand(getGangland(), getArgumentTree(), getArgument(), userManager,
		                                      memberManager, gangManager, rankManager);
		Argument promoteUser = new GangPromoteCommand(getGangland(), getArgumentTree(), getArgument(), userManager,
		                                              memberManager, gangManager, rankManager);
		Argument demoteUser = new GangDemoteCommand(getGangland(), getArgumentTree(), getArgument(), userManager,
		                                            memberManager, gangManager, rankManager);

		getArgument().addPermission(getPermission() + ".force_rank");

		Argument deposit = new GangDepositCommand(getGangland(), getArgumentTree(), getArgument(), userManager,
		                                          memberManager, gangManager);
		Argument withdraw = new GangWithdrawCommand(getGangland(), getArgumentTree(), getArgument(), userManager,
		                                            memberManager, gangManager);
		Argument balance = new GangBalanceCommand(getGangland(), getArgumentTree(), getArgument(), userManager,
		                                          gangManager);
		Argument name = new GangRenameCommand(getGangland(), getArgumentTree(), getArgument(), userManager,
		                                      gangManager);
		Argument description = new GangDescriptionCommand(getGangland(), getArgumentTree(), getArgument(), userManager,
		                                                  gangManager);
		Argument ally = new GangAllyCommand(getGangland(), getArgumentTree(), getArgument(), userManager, memberManager,
		                                    gangManager);
		Argument display = new GangDisplayCommand(getGangland(), getArgumentTree(), getArgument(), userManager,
		                                          gangManager);
		Argument color = new GangColorCommand(getGangland(), getArgumentTree(), getArgument(), userManager,
		                                      gangManager);

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
		BigDecimal balance    = gang.getEconomy().getAmount();
		BigDecimal maxBalance = Settings.getGangMaxBalance();

		BigDecimal threeQuarters = Currency.multiply(maxBalance, 0.75);
		BigDecimal half          = Currency.multiply(maxBalance, 0.5);

		if (balance.compareTo(maxBalance) >= 0) return XMaterial.EMERALD_BLOCK.get();
		else if (balance.compareTo(threeQuarters) >= 0) return XMaterial.DIAMOND_BLOCK.get();
		else if (balance.compareTo(half) >= 0) return XMaterial.GOLD_BLOCK.get();

		return XMaterial.IRON_BLOCK.get();
	}

	private void gangStat(User<Player> user, UserManager<Player> userManager, GangManager gangManager) {
		Gang   gang  = gangManager.getGang(user.getGangId());
		String title = "&6&l" + gang.getDisplayNameString() + "&r gang";
		int    size  = 5 * 9;

		InventoryHandler gui = new InventoryHandler(getGangland(), title, size, user.getUser());

		// balance
		Material material = itemToBalance(gang);

		gui.setItem(11, material, "&bBalance", new ArrayList<>(
				List.of(String.format("&e%s%s", Settings.getMoneySymbol(),
				                      Settings.formatAmount(gang.getEconomy().getAmount())))), true, false);

		// id
		gui.setItem(13, XMaterial.CRAFTING_TABLE.get(), "&bID", new ArrayList<>(List.of("&e" + gang.getId())), false,
		            false);

		// description
		gui.setItem(15, XMaterial.PAPER.get(), "&bDescription", new ArrayList<>(List.of("&e" + gang.getDescription())),
		            false, false, (player, inventory, items) -> {
					var desc = Objects.requireNonNull(
							getArgumentTree().find(new Argument(getGangland(), "desc", getArgumentTree())));
					var argumentSequence = ArgumentUtil.getArgumentSequence(desc, Gangland.SHORT_PREFIX);

					player.performCommand(argumentSequence);
				});

		Fill fill = new Fill(Settings.getInventoryFillName(), Settings.getInventoryFillItem());

		ButtonTags buttonTags = new ButtonTags(Settings.getPreviousPage(), Settings.getHomePage(),
		                                       Settings.getNextPage());

		// members
		gui.setItem(19, XMaterial.PLAYER_HEAD.get(), "&bMembers", new ArrayList<>(
							List.of("&a" + gang.getOnlineMembers(userManager).size() + "&7/&e" + gang.getMembers().size())), false,
		            false, (player, inventory, item) -> {
					User<Player> user1 = userManager.getUser(player);

					if (user1 == null) return;

					Gang gang1 = gangManager.getGang(user1.getGangId());

					List<ItemStack> items = new ArrayList<>();

					for (Member member : gang1.getMembers()) {
						OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(member.getUuid());
						Rank          userRank      = member.getRank();
						String        rank          = "null";

						if (userRank != null) rank = userRank.getName();

						List<String> data = new ArrayList<>();
						data.add("&7rank:&e " + rank);
						data.add("&7Contribution:&e " + member.getContribution());
						data.add("&7Joined:&e " + member.getGangJoinDateString());

						ItemBuilder itemBuilder = new ItemBuilder(XMaterial.PLAYER_HEAD.get()).setDisplayName(
								"&b" + offlinePlayer.getName()).setLore(data);

						itemBuilder.modifyNBT(nbt -> nbt.setString("SkullOwner", offlinePlayer.getName()));

						items.add(itemBuilder.build());
					}

					String title1 = "&6&lGang Members";
					MultiInventory multi = MultiInventoryCreation.dynamicMultiInventory(getGangland(), player, items,
					                                                                    title1, false, false, fill,
					                                                                    buttonTags, null);

					if (multi == null) return;

					multi.open(player);
				});

		// bounty
		gui.setItem(22, XMaterial.BLAZE_ROD.get(), "&bBounty", new ArrayList<>(
				List.of(String.format("&e%s%s", Settings.getMoneySymbol(),
				                      Settings.formatAmount(gang.getBounty().getAmount())))), true, false);

		// ally
		gui.setItem(25, XMaterial.REDSTONE.get(), "&bAlly", List.of("&e" + gang.getAllies().size()), false, false,
		            (player, inventory, item) -> {
						User<Player> user1 = userManager.getUser(player);

						if (user1 == null) return;

						Gang gang1 = gangManager.getGang(user1.getGangId());

						List<ItemStack> items = new ArrayList<>();

						for (Gang ally : gang1.getAllies()
								.stream().map(GangAlliance::ally).toList()) {
							List<String> data = new ArrayList<>();
							data.add("&7ID:&e " + ally.getId());
							data.add(String.format("&7Members:&a %d&7/&e%d", ally.getOnlineMembers(userManager).size(),
				                                   ally.getMembers().size()));
							data.add("&7Created:&e " + ally.getDateCreatedString());

							ItemBuilder itemBuilder = new ItemBuilder(XMaterial.REDSTONE.get()).setDisplayName(
									"&b" + ally.getDisplayNameString()).setLore(data);

							items.add(itemBuilder.build());
						}

						String title1 = "&6&lGang Allies";
						MultiInventory multi = MultiInventoryCreation.dynamicMultiInventory(getGangland(), player,
			                                                                                items, title1, false, false,
			                                                                                fill, buttonTags, null);

						if (multi == null) return;

						multi.open(player);
					});

		// date created
		gui.setItem(29, XMaterial.WRITABLE_BOOK.get(), "&bCreated",
		            new ArrayList<>(List.of("&e" + gang.getDateCreatedString())), true, false);

		// color
		gui.setItem(31, ColorUtil.getMaterialByColor(gang.getColor(), MaterialType.WOOL.name()), "&bColor",
		            new ArrayList<>(List.of("&e" + gang.getColor().toLowerCase().replace("_", " "))), false, false,
		            (player, inventory, item) -> {
						var color = Objects.requireNonNull(
								getArgumentTree().find(new Argument(getGangland(), "color", getArgumentTree())));
						var argumentSequence = ArgumentUtil.getArgumentSequence(color, Gangland.SHORT_PREFIX);

						player.performCommand(argumentSequence);
					});

		gui.setItem(33, ColorUtil.getMaterialByColor(gang.getColor(), MaterialType.BANNER.name()), "&bStatistics",
		            new ArrayList<>(List.of("&eGang stats")), false, false);

		InventoryUtil.fillInventory(gui, fill);

		gui.open(user.getUser());
	}

}
