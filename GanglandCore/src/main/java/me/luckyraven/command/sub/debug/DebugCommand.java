package me.luckyraven.command.sub.debug;

import com.cryptomorin.xseries.XMaterial;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.luckyraven.Gangland;
import me.luckyraven.bukkit.ItemBuilder;
import me.luckyraven.bukkit.inventory.InventoryHandler;
import me.luckyraven.bukkit.inventory.MultiInventory;
import me.luckyraven.command.CommandHandler;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.compatibility.CompatibilityAPI;
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
import me.luckyraven.ray.RayTrace;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.color.Color;
import me.luckyraven.util.color.ColorUtil;
import me.luckyraven.util.color.MaterialType;
import me.luckyraven.util.timer.CountdownTimer;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
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

				List<Material> swords = Arrays.stream(XMaterial.values())
											  .map(XMaterial::parseMaterial)
											  .filter(Objects::nonNull)
											  .filter(material -> material.name().contains("SWORD"))
											  .toList();

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
			String[] permissions = Bukkit.getPluginManager()
										 .getPermissions()
										 .stream()
										 .map(Permission::getName)
										 .filter(name -> name.startsWith("gangland"))
										 .sorted(String::compareTo)
										 .toArray(String[]::new);
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

				Arrays.stream(placeholders)
					  .forEach(string -> sender.sendMessage(
							  string + " -> " + handler.replacePlaceholder(player, string)));
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

				sender.sendMessage("Normal inventories: ");
				sender.sendMessage(user.getInventories()
									   .stream()
									   .map(InventoryHandler::getTitle)
									   .map(NamespacedKey::getKey)
									   .toArray(String[]::new));

				sender.sendMessage("Special inventories: ");
				sender.sendMessage(user.getSpecialInventories()
									   .stream()
									   .map(InventoryHandler::getTitle)
									   .map(NamespacedKey::getKey)
									   .toArray(String[]::new));
			} else {
				for (User<Player> user : getGangland().getInitializer().getUserManager().getUsers().values()) {
					List<String> values = new ArrayList<>();

					values.addAll(user.getInventories()
									  .stream()
									  .map(InventoryHandler::getTitle)
									  .map(NamespacedKey::getKey)
									  .toList());
					values.addAll(user.getSpecialInventories()
									  .stream()
									  .map(InventoryHandler::getTitle)
									  .map(NamespacedKey::getKey)
									  .toList());

					sender.sendMessage(user.getUser().getName() + ":");
					sender.sendMessage(ChatUtil.createList(values));
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

			sender.sendMessage(permission);
			sender.sendMessage("hasPermission: " + sender.hasPermission(permission));
			sender.sendMessage("isPermissionSet: " + sender.isPermissionSet(permission));
		});

		checkPerm.addSubArgument(checkOptional);

		Argument giveGun = new Argument("weapon", getArgumentTree(), (argument, sender, args) -> {
			if (!(sender instanceof Player)) {
				sender.sendMessage("Only for players!");
				return;
			}

			Inventory   playerInv = ((Player) sender).getInventory();
			ItemBuilder weapon    = new ItemBuilder(Material.STICK);

			weapon.setDisplayName("Test Weapon");
			weapon.addTag("uniqueItem", "weapon");

			for (int i = 0; i < playerInv.getSize(); i++) {
				if (playerInv.getItem(i) != null) continue;

				playerInv.setItem(i, weapon.build());
				break;
			}
		});

		Argument version = new Argument("version", getArgumentTree(), (argument, sender, args) -> {
			sender.sendMessage("Server version: " + Bukkit.getVersion(), "Bukkit version: " + Bukkit.getBukkitVersion(),
							   "Plugin version: " + getGangland().getDescription().getVersion(),
							   "API version: " + getGangland().getDescription().getAPIVersion());

			if (!(sender instanceof Player player && getGangland().getViaAPI() != null)) return;

			int             playerVersion   = getGangland().getViaAPI().getPlayerVersion(player.getUniqueId());
			ProtocolVersion protocolVersion = ProtocolVersion.getProtocol(playerVersion);

			sender.sendMessage("Client version: " + protocolVersion.getName());
		});

		Argument rayCast = new Argument("raycast", getArgumentTree(), (argument, sender, args) -> {
			if (!(sender instanceof Player player)) return;

			double x = 0.15;
			double y = 0.15;
			double z = 0.15;

			Location location = RayTrace.cast(player, x, y, z);
			sender.sendMessage(location != null ? location.toString() : "null");

			if (location != null) {
				for (int i = 0; i < 100; i++) {
					player.getWorld()
						  .spawnParticle(Particle.REDSTONE, location, 50,
										 new Particle.DustOptions(Color.RED.getBukkitColor(), 0.5F));
				}

				for (Entity entity : player.getWorld().getNearbyEntities(location, x, y, z)) {
					// Check if the ray intersects with an entity
					if (entity instanceof LivingEntity && !entity.equals(player)) {
						((LivingEntity) entity).damage(4);
					}
				}

				Block block = location.getBlock();

				CompatibilityAPI.getBlockCompatibility().getCrackPacket(block, 1);

				if (block.getType() != Material.AIR) {
					World    world         = block.getWorld();
					Location blockLocation = block.getLocation();

					CountdownTimer timer = new CountdownTimer(getGangland(), 40,
															  t -> world.playEffect(blockLocation, Effect.STEP_SOUND,
																					block.getType()));

					timer.start(false);
				}
			}
		});

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
		arguments.add(giveGun);
		arguments.add(version);
		arguments.add(rayCast);

		getArgument().addAllSubArguments(arguments);
	}

	@Override
	protected void help(CommandSender sender, int page) { }

	private String convertToJson(String input) {
		return new JsonFormatter().formatToJson(input, " ".repeat(3));
	}

}
