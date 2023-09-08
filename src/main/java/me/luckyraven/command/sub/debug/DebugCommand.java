package me.luckyraven.command.sub.debug;

import com.cryptomorin.xseries.XMaterial;
import me.luckyraven.Gangland;
import me.luckyraven.bukkit.inventory.InventoryHandler;
import me.luckyraven.bukkit.inventory.MultiInventory;
import me.luckyraven.command.CommandHandler;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.data.account.gang.Gang;
import me.luckyraven.data.account.gang.GangManager;
import me.luckyraven.data.account.gang.Member;
import me.luckyraven.data.account.gang.MemberManager;
import me.luckyraven.data.placeholder.PlaceholderHandler;
import me.luckyraven.data.rank.Rank;
import me.luckyraven.data.teleportation.Waypoint;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.datastructure.JsonFormatter;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.util.color.Color;
import me.luckyraven.util.color.ColorUtil;
import me.luckyraven.util.color.MaterialType;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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
	protected void initializeArguments() {
		// user data
		Argument userData = new Argument("user-data", getArgumentTree(), (argument, sender, args) -> {
			UserManager<Player> userManager = getGangland().getInitializer().getUserManager();
			if (sender instanceof Player player) {
				User<Player> user = userManager.getUser(player);

				player.sendMessage(convertToJson(user.toString()));
			} else {
				for (User<Player> user : userManager.getUsers().values())
					sender.sendMessage(user.toString());
			}
		});

		// gang data
		Argument gangData = new Argument("gang-data", getArgumentTree(), (argument, sender, args) -> {
			UserManager<Player> userManager = getGangland().getInitializer().getUserManager();
			GangManager         gangManager = getGangland().getInitializer().getGangManager();
			if (sender instanceof Player player) {
				User<Player> user = userManager.getUser(player);
				if (user.hasGang()) {
					Gang gang = gangManager.getGang(user.getGangId());

					player.sendMessage(convertToJson(gang.toString()));
				} else {
					player.sendMessage("Not in a gang...");
				}
			} else {
				for (Gang gang : getGangland().getInitializer().getGangManager().getGangs().values())
					sender.sendMessage(gang.toString());
			}
		});

		Argument memberData = new Argument("member-data", getArgumentTree(), (argument, sender, args) -> {
			MemberManager memberManager = getGangland().getInitializer().getMemberManager();
			if (sender instanceof Player player) {
				Member member = memberManager.getMember(player.getUniqueId());

				player.sendMessage(convertToJson(member.toString()));
			} else {
				for (Member member : memberManager.getMembers().values())
					sender.sendMessage(member.toString());
			}
		});

		// rank data
		Argument rankData = new Argument("rank-data", getArgumentTree(), (argument, sender, args) -> {
			if (sender instanceof Player) {
				for (Rank rank : getGangland().getInitializer().getRankManager().getRanks().values())
					sender.sendMessage(convertToJson(rank.toString()));
			} else {
				for (Rank rank : getGangland().getInitializer().getRankManager().getRanks().values())
					sender.sendMessage(rank.toString());
			}
		});

		// waypoint data
		Argument waypointData = new Argument("waypoint-data", getArgumentTree(), (argument, sender, args) -> {
			if (sender instanceof Player) {
				for (Waypoint waypoint : getGangland().getInitializer().getWaypointManager().getWaypoints().values())
					sender.sendMessage(convertToJson(waypoint.toString()));
			} else {
				for (Waypoint waypoint : getGangland().getInitializer().getWaypointManager().getWaypoints().values())
					sender.sendMessage(waypoint.toString());
			}
		});

		// multi inventory
		Argument multiInv = new Argument("multi", getArgumentTree(), (argument, sender, args) -> {
			if (sender instanceof Player player) {
				User<Player>    user  = getGangland().getInitializer().getUserManager().getUser(player);
				List<ItemStack> items = new ArrayList<>();

				for (Color color : Color.values()) {
					for (MaterialType type : MaterialType.values()) {
						items.add(new ItemStack(ColorUtil.getMaterialByColor(color.name(), type.name())));
					}
				}

				List<Material> swords = Arrays.stream(XMaterial.values()).map(XMaterial::parseMaterial).filter(
						Objects::nonNull).filter(material -> material.name().contains("SWORD")).toList();

				items.addAll(swords.stream().map(ItemStack::new).toList());

				MultiInventory multi = MultiInventory.dynamicMultiInventory(getGangland(), user, items,
				                                                            "&6&lDebug items", false, false, null);

				multi.open(player);
			} else {
				sender.sendMessage("How will you see the inventory?");
			}
		});

		// anvil gui
		Argument anvil = new Argument("anvil", getArgumentTree(), (argument, sender, args) -> {
			if (sender instanceof Player player) {
				User<Player> user = getGangland().getInitializer().getUserManager().getUser(player);
				Gang         gang = getGangland().getInitializer().getGangManager().getGang(user.getGangId());

				String text = "";
				if (gang != null) text = gang.getDescription();

				new AnvilGUI.Builder().onClick((slot, stateSnapshot) -> {
					if (slot != AnvilGUI.Slot.OUTPUT) {
						return Collections.emptyList();
					}

					stateSnapshot.getPlayer().sendMessage(stateSnapshot.getText());
					return List.of(AnvilGUI.ResponseAction.close());
				}).text(text).title("Enter your answer.").plugin(getGangland()).open(player);
			} else {
				sender.sendMessage("How will you view the anvil inventory?");
			}
		});

		// permissions list
		Argument perm = new Argument("perms", getArgumentTree(), (argument, sender, args) -> {
			sender.sendMessage(
					getGangland().getInitializer().getPermissionManager().getPermissions().toArray(String[]::new));
		});

		Argument permOptional = new Argument("bukkit", getArgumentTree(), (argument, sender, args) -> {
			String[] permissions = Bukkit.getPluginManager().getPermissions().stream().map(Permission::getName).filter(
					name -> name.startsWith("gangland")).sorted(String::compareTo).toArray(String[]::new);
			sender.sendMessage(permissions);
		});

		perm.addSubArgument(permOptional);

		// all settings data
		String[] setOpt = {"settings", "setting"};
		Argument settingOptions = new Argument(setOpt, getArgumentTree(), (argument, sender, args) -> {
			JsonFormatter jsonFormatter = new JsonFormatter();
			sender.sendMessage(convertToJson(jsonFormatter.createJson(SettingAddon.getSettingsMap())));
		});

		// all settings placeholder
		Argument setPlaceholder = new Argument("placeholder", getArgumentTree(), (argument, sender, args) -> {
			JsonFormatter jsonFormatter = new JsonFormatter();
			sender.sendMessage(convertToJson(jsonFormatter.createJson(SettingAddon.getSettingsPlaceholder())));
		});

		settingOptions.addSubArgument(setPlaceholder);

		// testing placeholder
		Argument placeholder = new Argument("placeholder-data", getArgumentTree(), (argument, sender, args) -> {
			if (sender instanceof Player player) {
				PlaceholderHandler handler = getGangland().getInitializer().getPlaceholder();

				String[] placeholders = {"%player%", "%info%", "%user_gang-id%"};

				Arrays.stream(placeholders).forEach(
						string -> sender.sendMessage(string + " -> " + handler.replacePlaceholder(player, string)));
			} else {
				sender.sendMessage("Can't process non-player data.");
			}
		});

		// force update data
		Argument updateData = new Argument("update-data", getArgumentTree(), (argument, sender, args) -> {
			getGangland().getPeriodicalUpdates().forceUpdate();
		});

		// all-inventory name space key data
		Argument inventoriesData = new Argument("inv-data", getArgumentTree(), (argument, sender, args) -> {
			if (sender instanceof Player player) {
				User<Player> user = getGangland().getInitializer().getUserManager().getUser(player);
				sender.sendMessage(user.getInventories()
				                       .stream()
				                       .map(InventoryHandler::getTitle)
				                       .map(NamespacedKey::getKey)
				                       .toArray(String[]::new));
			} else {
				for (User<Player> user : getGangland().getInitializer().getUserManager().getUsers().values()) {
					List<String> values = new ArrayList<>();
					values.add(user.getUser().getName() + ":");
					values.addAll(user.getInventories()
					                  .stream()
					                  .map(InventoryHandler::getTitle)
					                  .map(NamespacedKey::getKey)
					                  .toList());
					sender.sendMessage(values.toArray(String[]::new));
				}
			}
		});

		Argument specialInventories = new Argument("special", getArgumentTree(), (argument, sender, args) -> {
			sender.sendMessage(InventoryHandler.getSpecialInventories()
			                                   .keySet()
			                                   .stream()
			                                   .map(NamespacedKey::getKey)
			                                   .toArray(String[]::new));
		});

		inventoriesData.addSubArgument(specialInventories);

		Argument checkPerm = new Argument("check-perm", getArgumentTree(), (argument, sender, args) -> {
			sender.sendMessage("Missing argument <permission>");
		});

		Argument checkOptional = new OptionalArgument(getArgumentTree(), (argument, sender, args) -> {
			String permission = args[2];

			sender.sendMessage(permission + " -> " + sender.hasPermission(permission));
		});

		checkPerm.addSubArgument(checkOptional);

		// add sub arguments
		List<Argument> arguments = new ArrayList<>();

		arguments.add(userData);
		arguments.add(memberData);
		arguments.add(gangData);
		arguments.add(rankData);
		arguments.add(waypointData);
		arguments.add(multiInv);
		arguments.add(anvil);
		arguments.add(perm);
		arguments.add(settingOptions);
		arguments.add(placeholder);
		arguments.add(updateData);
		arguments.add(inventoriesData);
		arguments.add(checkPerm);

		getArgument().addAllSubArguments(arguments);
	}

	@Override
	public void help(CommandSender sender, int page) {

	}

	private String convertToJson(String input) {
		return new JsonFormatter().formatToJson(input, " ".repeat(3));
	}

}
