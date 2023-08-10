package me.luckyraven.command.sub;

import com.cryptomorin.xseries.XMaterial;
import me.clip.placeholderapi.PlaceholderAPIPlugin;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.clip.placeholderapi.expansion.manager.LocalExpansionManager;
import me.luckyraven.Gangland;
import me.luckyraven.account.gang.Gang;
import me.luckyraven.account.gang.GangManager;
import me.luckyraven.account.gang.Member;
import me.luckyraven.account.gang.MemberManager;
import me.luckyraven.bukkit.inventory.MultiInventory;
import me.luckyraven.command.CommandHandler;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.rank.Rank;
import me.luckyraven.util.color.Color;
import me.luckyraven.util.color.ColorUtil;
import me.luckyraven.util.color.MaterialType;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.Permission;

import java.util.*;

public class DebugCommand extends CommandHandler {

	public DebugCommand(Gangland gangland) {
		super(gangland, "debug", false);
	}

	@Override
	protected void onExecute(Argument argument, CommandSender commandSender, String[] arguments) {
		getGangland().getInitializer().getCommandManager().show(commandSender);
	}

	@Override
	protected void initializeArguments(Gangland gangland) {
		// user data
		Argument userData = new Argument("user-data", getArgumentTree(), (argument, sender, args) -> {
			UserManager<Player> userManager = gangland.getInitializer().getUserManager();
			if (sender instanceof Player player) {
				User<Player> user = userManager.getUser(player);

				player.sendMessage(user.toString());
			} else {
				for (User<Player> user : userManager.getUsers().values())
					sender.sendMessage(user.toString());
			}
		});

		// gang data
		Argument gangData = new Argument("gang-data", getArgumentTree(), (argument, sender, args) -> {
			UserManager<Player> userManager = gangland.getInitializer().getUserManager();
			GangManager         gangManager = gangland.getInitializer().getGangManager();
			if (sender instanceof Player player) {
				User<Player> user = userManager.getUser(player);
				if (user.hasGang()) {
					Gang gang = gangManager.getGang(user.getGangId());

					player.sendMessage(gang.toString());
				} else {
					player.sendMessage("Not in a gang...");
				}
			} else {
				for (Gang gang : gangland.getInitializer().getGangManager().getGangs().values())
					sender.sendMessage(gang.toString());
			}
		});

		Argument memberData = new Argument("member-data", getArgumentTree(), (argument, sender, args) -> {
			MemberManager memberManager = gangland.getInitializer().getMemberManager();
			if (sender instanceof Player player) {
				Member member = memberManager.getMember(player.getUniqueId());

				player.sendMessage(member.toString());
			} else {
				for (Member member : memberManager.getMembers().values())
					sender.sendMessage(member.toString());
			}
		});

		// rank data
		Argument rankData = new Argument("rank-data", getArgumentTree(), (argument, sender, args) -> {
			for (Rank rank : gangland.getInitializer().getRankManager().getRanks().values())
				sender.sendMessage(rank.toString());
		});

		// multi inventory
		Argument multiInv = new Argument("multi", getArgumentTree(), (argument, sender, args) -> {
			if (sender instanceof Player player) {

				List<ItemStack> items = new ArrayList<>();

				for (Color color : Color.values()) {
					for (MaterialType type : MaterialType.values()) {
						items.add(new ItemStack(ColorUtil.getMaterialByColor(color.name(), type.name())));
					}
				}

				List<Material> swords = Arrays.stream(XMaterial.values()).map(XMaterial::parseMaterial).filter(
						Objects::nonNull).filter(material -> material.name().contains("SWORD")).toList();

				items.addAll(swords.stream().map(ItemStack::new).toList());

				MultiInventory multi = MultiInventory.dynamicMultiInventory(gangland, items, "&6&lDebug items", false,
				                                                            false, null);

				multi.open(player);
			} else {
				sender.sendMessage("How will you see the inventory?");
			}
		});

		// anvil gui
		Argument anvil = new Argument("anvil", getArgumentTree(), (argument, sender, args) -> {
			if (sender instanceof Player player) {
				User<Player> user = gangland.getInitializer().getUserManager().getUser(player);
				Gang         gang = gangland.getInitializer().getGangManager().getGang(user.getGangId());

				String text = "";
				if (gang != null) text = gang.getDescription();

				new AnvilGUI.Builder().onClick((slot, stateSnapshot) -> {
					if (slot != AnvilGUI.Slot.OUTPUT) {
						return Collections.emptyList();
					}

					stateSnapshot.getPlayer().sendMessage(stateSnapshot.getText());
					return List.of(AnvilGUI.ResponseAction.close());
				}).text(text).title("Enter your answer.").plugin(gangland).open(player);
			} else {
				sender.sendMessage("How will you view the anvil inventory?");
			}
		});

		Argument perm = new Argument("perms", getArgumentTree(), (argument, sender, args) -> {
			String[] permissions = Bukkit.getPluginManager().getPermissions().stream().map(Permission::getName).filter(
					name -> name.startsWith("gangland")).sorted().toArray(String[]::new);
			sender.sendMessage(permissions);
		});

		String[] setOpt = {"settings", "setting"};
		Argument settingOptions = new Argument(setOpt, getArgumentTree(), (argument, sender, args) -> {
			for (Map.Entry<String, Object> entry : SettingAddon.getSettingsMap().entrySet())
				sender.sendMessage(entry.getKey() + ": " + entry.getValue());
		});

		Argument placeholder = new Argument("placeholder", getArgumentTree(), (argument, sender, args) -> {
			LocalExpansionManager expansionManager = PlaceholderAPIPlugin.getInstance().getLocalExpansionManager();

			for (PlaceholderExpansion expansion : expansionManager.getExpansions()) {
				String identifier = expansion.getIdentifier();

				if (!identifier.equalsIgnoreCase("gangland")) continue;

				sender.sendMessage(expansion.getPlaceholders().toArray(String[]::new));
			}
		});

		Argument updateData = new Argument("update-data", getArgumentTree(), (argument, sender, args) -> {
			gangland.getPeriodicalUpdates().forceUpdate();
		});

		// add sub arguments
		List<Argument> arguments = new ArrayList<>();

		arguments.add(userData);
		arguments.add(memberData);
		arguments.add(gangData);
		arguments.add(rankData);
		arguments.add(multiInv);
		arguments.add(anvil);
		arguments.add(perm);
		arguments.add(settingOptions);
		arguments.add(placeholder);
		arguments.add(updateData);

		getArgument().addAllSubArguments(arguments);
	}

	@Override
	public void help(CommandSender sender, int page) {

	}

}
