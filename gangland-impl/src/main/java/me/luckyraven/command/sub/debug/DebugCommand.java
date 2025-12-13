package me.luckyraven.command.sub.debug;

import com.cryptomorin.xseries.XMaterial;
import com.cryptomorin.xseries.particles.XParticle;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.luckyraven.Gangland;
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
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.inventory.InventoryHandler;
import me.luckyraven.inventory.multi.MultiInventory;
import me.luckyraven.inventory.multi.MultiInventoryCreation;
import me.luckyraven.inventory.part.ButtonTags;
import me.luckyraven.inventory.part.Fill;
import me.luckyraven.util.color.Color;
import me.luckyraven.util.color.ColorUtil;
import me.luckyraven.util.color.MaterialType;
import me.luckyraven.util.datastructure.JsonFormatter;
import me.luckyraven.util.ray.RayTrace;
import me.luckyraven.util.timer.CountdownTimer;
import me.luckyraven.weapon.Weapon;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.Permission;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class DebugCommand extends CommandHandler {

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
		Argument userData = getUserData();

		// gang data
		Argument gangData = getGangData();

		Argument memberData = getMemberData();

		// rank data
		Argument rankData = getRankData();

		// waypoint data
		Argument waypointData = getWaypointData();

		// multi inventory
		Argument multiInv = getMultiInv();

		// anvil gui
		Argument anvil = getAnvil();

		// permissions list
		Argument perm = getPerm();

		Argument permOptional = getPermOptional();

		perm.addSubArgument(permOptional);

		// all settings data
		String[] setOpt         = {"settings", "setting"};
		Argument settingOptions = getArgument(setOpt);

		// all settings placeholder
		Argument setPlaceholder = getSetPlaceholder();

		settingOptions.addSubArgument(setPlaceholder);

		// testing placeholder
		Argument placeholder = getPlaceholder();

		// force update data
		Argument updateData = getUpdateData();

		// all-inventory name space key data
		Argument inventoriesData = getInventoriesData();

		Argument specialInventories = getSpecialInventories();

		inventoriesData.addSubArgument(specialInventories);

		Argument checkPerm = getCheckPerm();

		Argument checkOptional = getCheckOptional();

		checkPerm.addSubArgument(checkOptional);

		Argument giveGun = getGiveGun();

		Argument version = getVersion();

		Argument rayCast = getRayCast();

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

	private @NotNull Argument getUserData() {
		return new Argument(getGangland(), "user-data", getArgumentTree(), (argument, sender, args) -> {
			UserManager<Player> userManager = getGangland().getInitializer().getUserManager();
			if (sender instanceof Player player) {
				User<Player> user = userManager.getUser(player);

				user.sendMessage(convertToJson(user.toString()));
			} else {
				for (User<Player> user : userManager.getUsers().values()) {
					user.sendMessage(user.toString());
				}
			}
		});
	}

	private @NotNull Argument getGangData() {
		return new Argument(getGangland(), "gang-data", getArgumentTree(), (argument, sender, args) -> {
			UserManager<Player> userManager = getGangland().getInitializer().getUserManager();
			GangManager         gangManager = getGangland().getInitializer().getGangManager();
			if (sender instanceof Player player) {
				User<Player> user = userManager.getUser(player);
				if (user.hasGang()) {
					Gang gang = gangManager.getGang(user.getGangId());

					user.sendMessage(convertToJson(gang.toString()));
				} else {
					user.sendMessage("Not in a gang...");
				}
			} else {
				Collection<Gang> values = getGangland().getInitializer().getGangManager().getGangs().values();
				for (Gang gang : values) {
					sender.sendMessage(gang.toString());
				}
			}
		});
	}

	private @NotNull Argument getMemberData() {
		return new Argument(getGangland(), "member-data", getArgumentTree(), (argument, sender, args) -> {
			MemberManager memberManager = getGangland().getInitializer().getMemberManager();
			if (sender instanceof Player player) {
				Member member = memberManager.getMember(player.getUniqueId());

				player.sendMessage(convertToJson(member.toString()));
			} else {
				Collection<Member> values = memberManager.getMembers().values();
				for (Member member : values) {
					sender.sendMessage(member.toString());
				}
			}
		});
	}

	private @NotNull Argument getRankData() {
		return new Argument(getGangland(), "rank-data", getArgumentTree(), (argument, sender, args) -> {
			Collection<Rank> values = getGangland().getInitializer().getRankManager().getRanks().values();
			if (sender instanceof Player) {
				for (Rank rank : values) {
					sender.sendMessage(convertToJson(rank.toString()));
				}
			} else {
				for (Rank rank : values) {
					sender.sendMessage(rank.toString());
				}
			}
		});
	}

	private @NotNull Argument getWaypointData() {
		return new Argument(getGangland(), "waypoint-data", getArgumentTree(), (argument, sender, args) -> {
			Collection<Waypoint> values = getGangland().getInitializer().getWaypointManager().getWaypoints().values();
			if (sender instanceof Player) {
				for (Waypoint waypoint : values) {
					sender.sendMessage(convertToJson(waypoint.toString()));
				}
			} else {
				for (Waypoint waypoint : values) {
					sender.sendMessage(waypoint.toString());
				}
			}
		});
	}

	private @NotNull Argument getMultiInv() {
		return new Argument(getGangland(), "multi", getArgumentTree(), (argument, sender, args) -> {
			if (sender instanceof Player player) {
				List<ItemStack> items = new ArrayList<>();

				for (Color color : Color.values()) {
					for (MaterialType type : MaterialType.values()) {
						items.add(new ItemStack(ColorUtil.getMaterialByColor(color.name(), type.name())));
					}
				}

				List<Material> swords = Arrays.stream(XMaterial.values())
						.map(XMaterial::get)
						.filter(Objects::nonNull)
						.filter(material -> material.name().contains("SWORD"))
						.toList();

				items.addAll(swords.stream().map(ItemStack::new).toList());

				String title = "&6&lDebug items";
				Fill   fill  = new Fill(SettingAddon.getInventoryFillName(), SettingAddon.getInventoryFillItem());

				ButtonTags buttonTags = new ButtonTags(SettingAddon.getPreviousPage(), SettingAddon.getHomePage(),
													   SettingAddon.getNextPage());

				MultiInventory multi = MultiInventoryCreation.dynamicMultiInventory(getGangland(), player, items, title,
																					false, false, fill, buttonTags,
																					null);

				if (multi == null) return;

				multi.open(player);
			} else {
				sender.sendMessage("How will you see the inventory?");
			}
		});
	}

	private @NotNull Argument getAnvil() {
		return new Argument(getGangland(), "anvil", getArgumentTree(), (argument, sender, args) -> {
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
	}

	private @NotNull Argument getPerm() {
		return new Argument(getGangland(), "perms", getArgumentTree(), (argument, sender, args) -> {
			String[] array = getGangland().getInitializer()
										  .getPermissionManager()
										  .getPermissions()
										  .toArray(String[]::new);
			sender.sendMessage(array);
		});
	}

	private @NotNull Argument getPermOptional() {
		return new Argument(getGangland(), "bukkit", getArgumentTree(), (argument, sender, args) -> {
			String[] permissions = Bukkit.getPluginManager()
										 .getPermissions()
					.stream()
					.map(Permission::getName)
					.filter(name -> name.startsWith("gangland"))
					.sorted(String::compareTo)
					.toArray(String[]::new);
			sender.sendMessage(permissions);
		});
	}

	private @NotNull Argument getArgument(String[] setOpt) {
		return new Argument(getGangland(), setOpt, getArgumentTree(), (argument, sender, args) -> {
			var jsonFormatter = new JsonFormatter();
			var message       = convertToJson(jsonFormatter.createJson(SettingAddon.getSettingsMap()));

			sender.sendMessage(message);
		});
	}

	private @NotNull Argument getSetPlaceholder() {
		return new Argument(getGangland(), "placeholder", getArgumentTree(), (argument, sender, args) -> {
			var jsonFormatter = new JsonFormatter();
			var message       = convertToJson(jsonFormatter.createJson(SettingAddon.getSettingsPlaceholder()));

			sender.sendMessage(message);
		});
	}

	private @NotNull Argument getPlaceholder() {
		return new Argument(getGangland(), "placeholder-data", getArgumentTree(), (argument, sender, args) -> {
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
	}

	private @NotNull Argument getUpdateData() {
		return new Argument(getGangland(), "update-data", getArgumentTree(), (argument, sender, args) -> {
			getGangland().getPeriodicalUpdates().forceUpdate();
		});
	}

	private @NotNull Argument getInventoriesData() {
		return new Argument(getGangland(), "inv-data", getArgumentTree(), (argument, sender, args) -> {
			if (sender instanceof Player player) {
				User<Player> user = getGangland().getInitializer().getUserManager().getUser(player);

				user.sendMessage("Normal inventories: ");
				user.sendMessage(user.getInventories()
										 .stream()
										 .map(InventoryHandler::getTitle)
										 .map(NamespacedKey::getKey)
										 .toArray(String[]::new));
			} else {
				for (User<Player> user : getGangland().getInitializer().getUserManager().getUsers().values()) {

					List<String> inventories = user.getInventories()
							.stream().map(InventoryHandler::getTitle).map(NamespacedKey::getKey).toList();

					List<String> values = new ArrayList<>(inventories);

					user.sendMessage(user.getUser().getName() + ":");
					user.sendMessage(String.valueOf(values));
				}
			}
		});
	}

	private @NotNull Argument getSpecialInventories() {
		return new Argument(getGangland(), "special", getArgumentTree(), (argument, sender, args) -> {
			String[] array = InventoryHandler.getSpecialInventories().keySet()
					.stream().map(NamespacedKey::getKey).toArray(String[]::new);
			sender.sendMessage(array);
		});
	}

	private @NotNull Argument getCheckPerm() {
		return new Argument(getGangland(), "check-perm", getArgumentTree(), (argument, sender, args) -> {
			sender.sendMessage("Missing argument <permission>");
		});
	}

	private @NotNull Argument getCheckOptional() {
		return new OptionalArgument(getGangland(), getArgumentTree(), (argument, sender, args) -> {
			String permission = args[2];

			sender.sendMessage(permission);
			sender.sendMessage("hasPermission: " + sender.hasPermission(permission));
			sender.sendMessage("isPermissionSet: " + sender.isPermissionSet(permission));
		}, sender -> List.of("<permission>"));
	}

	private @NotNull Argument getGiveGun() {
		return new Argument(getGangland(), "weapon", getArgumentTree(), (argument, sender, args) -> {
			Collection<Weapon> values = getGangland().getInitializer().getWeaponManager().getWeapons().values();
			for (Weapon weapon : values) {
				sender.sendMessage(weapon.getUuid().toString());
			}
		});
	}

	private @NotNull Argument getVersion() {
		return new Argument(getGangland(), "version", getArgumentTree(), (argument, sender, args) -> {
			sender.sendMessage("Server version: " + Bukkit.getVersion(), "Bukkit version: " + Bukkit.getBukkitVersion(),
							   "Plugin version: " + getGangland().getDescription().getVersion(),
							   "API version: " + getGangland().getDescription().getAPIVersion(),
							   "Bukkit version: " + Bukkit.getServer().getClass().getPackage().getName());

			if (!(sender instanceof Player player && getGangland().getViaAPI() != null)) return;

			int             playerVersion   = getGangland().getViaAPI().getPlayerVersion(player.getUniqueId());
			ProtocolVersion protocolVersion = ProtocolVersion.getProtocol(playerVersion);

			sender.sendMessage("Client version: " + protocolVersion.getName());
		});
	}

	private @NotNull Argument getRayCast() {
		return new Argument(getGangland(), "raycast", getArgumentTree(), (argument, sender, args) -> {
			if (!(sender instanceof Player player)) return;

			double x = 0.15;
			double y = 0.15;
			double z = 0.15;

			Location location = RayTrace.cast(player, x, y, z);
			sender.sendMessage(location != null ? location.toString() : "null");

			if (location != null) {
				for (int i = 0; i < 100; i++) {
					player.getWorld()
						  .spawnParticle(Objects.requireNonNull(XParticle.DUST.get()), location, 50,
										 new Particle.DustOptions(Color.RED.getBukkitColor(), 0.5F));
				}

				for (Entity entity : player.getWorld().getNearbyEntities(location, x, y, z)) {
					// Check if the ray intersects with an entity
					if (entity instanceof LivingEntity && !entity.equals(player)) {
						((LivingEntity) entity).damage(4);
					}
				}

				Block block = location.getBlock();

//				CompatibilityAPI.getBlockCompatibility().getCrackPacket(block, 1);

				if (block.getType() != Material.AIR) {
					World    world         = block.getWorld();
					Location blockLocation = block.getLocation();

					CountdownTimer timer = new CountdownTimer(getGangland(), 0L, 0L, 40, null,
															  t -> world.playEffect(blockLocation, Effect.STEP_SOUND,
																					block.getType()), null);

					timer.start(false);
				}
			}
		});
	}

	private String convertToJson(String input) {
		return new JsonFormatter().formatToJson(input, " ".repeat(3));
	}

}
