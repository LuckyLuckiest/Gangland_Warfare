package org.luckyraven.gangland.command.sub.debug;

import com.cryptomorin.xseries.XMaterial;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.Permission;
import org.jetbrains.annotations.NotNull;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.bootstrap.PeriodicalUpdates;
import org.luckyraven.gangland.command.Command;
import org.luckyraven.gangland.command.CommandManager;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.bean.command.CommandHandler;
import org.luckyraven.keystone.color.Color;
import org.luckyraven.keystone.color.ColorUtil;
import org.luckyraven.keystone.color.MaterialType;
import org.luckyraven.keystone.datastructure.JsonFormatter;
import org.luckyraven.keystone.util.ChatUtil;
import org.luckyraven.keystone.permission.PermissionManager;
import org.luckyraven.gangland.data.placeholder.worker.GanglandPlaceholder;
import org.luckyraven.gangland.data.teleportation.Waypoint;
import org.luckyraven.gangland.data.teleportation.WaypointManager;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.gang.rank.RankManager;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.gangland.inventory.InventoryHandler;
import org.luckyraven.gangland.inventory.flow.MultiPanelInventory;
import org.luckyraven.gangland.inventory.multi.ListEntry;
import org.luckyraven.gangland.inventory.multi.MultiInventory;
import org.luckyraven.gangland.inventory.multi.MultiInventoryCreation;
import org.luckyraven.gangland.inventory.part.ButtonTags;
import org.luckyraven.gangland.inventory.part.Fill;
import org.luckyraven.gangland.inventory.villager.VillagerInventory;
import org.luckyraven.gangland.inventory.villager.VillagerInventoryRegistry;
import org.luckyraven.gangland.inventory.villager.VillagerTrade;
import org.luckyraven.gangland.weapon.Weapon;
import org.luckyraven.gangland.weapon.WeaponManager;

import java.util.*;

@CommandHandler
public final class DebugCommand extends Command {

	private final UserManager<Player>       userManager;
	private final GangManager               gangManager;
	private final MemberManager             memberManager;
	private final RankManager               rankManager;
	private final WaypointManager           waypointManager;
	private final PermissionManager         permissionManager;
	private final WeaponManager             weaponManager;
	private final GanglandPlaceholder       placeholder;
	private final CommandManager            commandManager;
	private final VillagerInventoryRegistry villagerRegistry;

	public DebugCommand(Gangland gangland,
	                    @Qualifier("online") UserManager<Player> userManager,
	                    GangManager gangManager,
	                    MemberManager memberManager,
	                    RankManager rankManager,
	                    WaypointManager waypointManager,
	                    PermissionManager permissionManager,
	                    WeaponManager weaponManager,
	                    GanglandPlaceholder placeholder,
	                    CommandManager commandManager,
	                    VillagerInventoryRegistry villagerRegistry) {
		super(gangland, "debug", false);

		this.userManager       = userManager;
		this.gangManager       = gangManager;
		this.memberManager     = memberManager;
		this.rankManager       = rankManager;
		this.waypointManager   = waypointManager;
		this.permissionManager = permissionManager;
		this.weaponManager     = weaponManager;
		this.placeholder       = placeholder;
		this.commandManager    = commandManager;
		this.villagerRegistry  = villagerRegistry;
	}

	@Override
	protected void onExecute(Argument argument, CommandSender commandSender, String[] arguments) {
		commandManager.show(commandSender);
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

		// villager merchant gui
		Argument villager = getVillagerTest();

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

		// add sub arguments
		List<Argument> arguments = new ArrayList<>();

		arguments.add(userData);
		arguments.add(memberData);
		arguments.add(gangData);
		arguments.add(rankData);
		arguments.add(waypointData);
		arguments.add(multiInv);
		arguments.add(anvil);
		arguments.add(villager);
		arguments.add(perm);
		arguments.add(settingOptions);
		arguments.add(placeholder);
		arguments.add(updateData);
		arguments.add(inventoriesData);
		arguments.add(checkPerm);
		arguments.add(giveGun);
		arguments.add(version);

		getArgument().addAllSubArguments(arguments);
	}

	@Override
	protected void help(CommandSender sender, int page) { }

	private @NotNull Argument getUserData() {
		return new Argument(getGangland(), "user-data", getArgumentTree(), (argument, sender, args) -> {
			if (sender instanceof Player player) {
				User<Player> user = userManager.getUser(player);

				if (user == null) return;

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
			if (sender instanceof Player player) {
				User<Player> user = userManager.getUser(player);

				if (user == null) return;

				if (user.hasGang()) {
					Gang gang = gangManager.getGang(user.getGangId());

					user.sendMessage(convertToJson(gang.toString()));
				} else {
					user.sendMessage("Not in a gang...");
				}
			} else {
				Collection<Gang> values = gangManager.getGangs().values();
				for (Gang gang : values) {
					sender.sendMessage(gang.toString());
				}
			}
		});
	}

	private @NotNull Argument getMemberData() {
		return new Argument(getGangland(), "member-data", getArgumentTree(), (argument, sender, args) -> {
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
			Collection<Rank> values = rankManager.getRanks().values();
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
			Collection<Waypoint> values = waypointManager.getWaypoints().values();
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
				Fill   fill  = new Fill(Settings.getInventoryFillName(), Settings.getInventoryFillItem());

				ButtonTags buttonTags = new ButtonTags(Settings.getPreviousPage(), Settings.getHomePage(),
				                                       Settings.getNextPage());

				List<ListEntry> entries = items.stream().map(ListEntry::of).toList();

				MultiInventory multi = MultiInventoryCreation.dynamicMultiInventory(getGangland(), player, entries,
				                                                                    title,
				                                                                    false, 0, fill, buttonTags,
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
				User<Player> user = userManager.getUser(player);

				if (user == null) return;

				Gang gang = gangManager.getGang(user.getGangId());

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

	private @NotNull Argument getVillagerTest() {
		return new Argument(getGangland(), "villager", getArgumentTree(), (argument, sender, args) -> {
			if (!(sender instanceof Player player)) {
				sender.sendMessage("How will you see the inventory?");
				return;
			}

			VillagerDebugPanel         panel   = new VillagerDebugPanel(this::openDebugVillager);
			VillagerDebugPanel.Session session = new VillagerDebugPanel.Session();

			MultiPanelInventory<VillagerDebugPanel.Session> host =
					new MultiPanelInventory<>(getGangland(), player, session);
			host.register("main", panel);
			host.openAt("main");
		});
	}

	private void openDebugVillager(Player player) {
		VillagerInventory vi = new VillagerInventory(villagerRegistry, "&6&lDebug Trader");

		vi.addTrade(VillagerTrade.of(
				new ItemStack(Material.DIAMOND),
				new ItemStack(Material.EMERALD, 1),
				9999,
				p -> p.sendMessage(ChatUtil.color("&aBought a diamond for 1 emerald."))));

		vi.addTrade(VillagerTrade.of(
				new ItemStack(Material.BREAD),
				new ItemStack(Material.WHEAT, 2),
				9999,
				p -> p.sendMessage(ChatUtil.color("&aBought bread for 2 wheat."))));

		vi.addTrade(VillagerTrade.of(
				new ItemStack(Material.GOLDEN_APPLE),
				new ItemStack(Material.GOLD_INGOT, 4),
				new ItemStack(Material.APPLE, 1),
				9999,
				p -> p.sendMessage(ChatUtil.color("&aBought a golden apple."))));

		vi.open(player);
	}

	private @NotNull Argument getPerm() {
		return new Argument(getGangland(), "perms", getArgumentTree(), (argument, sender, args) -> {
			String[] array = permissionManager.getPermissions().toArray(String[]::new);
			sender.sendMessage(array);
		});
	}

	private @NotNull Argument getPermOptional() {
		return new Argument(getGangland(), "bukkit", getArgumentTree(), (argument, sender, args) -> {
			String[] permissions = Bukkit.getPluginManager()
			                             .getPermissions()
					.stream()
					.map(Permission::getName)
					.filter(name -> name.startsWith(Gangland.FULL_PREFIX))
					.sorted(String::compareTo)
					.toArray(String[]::new);
			sender.sendMessage(permissions);
		});
	}

	private @NotNull Argument getArgument(String[] setOpt) {
		return new Argument(getGangland(), setOpt, getArgumentTree(), (argument, sender, args) -> {
			var jsonFormatter = new JsonFormatter();
			var message       = convertToJson(jsonFormatter.createJson(Settings.getSettingsMap()));

			sender.sendMessage(message);
		});
	}

	private @NotNull Argument getSetPlaceholder() {
		return new Argument(getGangland(), "placeholder", getArgumentTree(), (argument, sender, args) -> {
			var jsonFormatter = new JsonFormatter();
			var message       = convertToJson(jsonFormatter.createJson(Settings.getSettingsPlaceholder()));

			sender.sendMessage(message);
		});
	}

	private @NotNull Argument getPlaceholder() {
		return new Argument(getGangland(), "placeholder-data", getArgumentTree(), (argument, sender, args) -> {
			if (sender instanceof Player player) {
				String[] placeholders = {"%player%", "%info%", "%user_gang-id%"};

				Arrays.stream(placeholders)
						.forEach(string -> sender.sendMessage(
								string + " -> " + placeholder.replacePlaceholder(player, string)));
			} else {
				sender.sendMessage("Can't process non-player data.");
			}
		});
	}

	private @NotNull Argument getUpdateData() {
		return new Argument(getGangland(), "update-data", getArgumentTree(), (argument, sender, args) -> {
			getGangland().getContext().get(PeriodicalUpdates.class).forceUpdate();
		});
	}

	private @NotNull Argument getInventoriesData() {
		return new Argument(getGangland(), "inv-data", getArgumentTree(), (argument, sender, args) -> {
			if (sender instanceof Player player) {
				User<Player> user = userManager.getUser(player);

				if (user == null) return;

				user.sendMessage("Normal inventories: ");
				user.sendMessage(user.getInventories()
										 .stream()
										 .map(InventoryHandler::getTitle)
						                 .map(NamespacedKey::getKey)
						                 .toArray(String[]::new));
			} else {
				for (User<Player> user : userManager.getUsers().values()) {

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
			Collection<Weapon> values = weaponManager.getWeapons().values();
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

	private String convertToJson(String input) {
		return new JsonFormatter().formatToJson(input, " ".repeat(3));
	}

}
