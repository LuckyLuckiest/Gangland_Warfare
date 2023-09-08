package me.luckyraven;

import lombok.Getter;
import me.luckyraven.bukkit.inventory.InventoryHandler;
import me.luckyraven.bukkit.scoreboard.ScoreboardManager;
import me.luckyraven.command.CommandHandler;
import me.luckyraven.command.CommandManager;
import me.luckyraven.command.CommandTabCompleter;
import me.luckyraven.command.data.InformationManager;
import me.luckyraven.command.sub.*;
import me.luckyraven.command.sub.debug.DebugCommand;
import me.luckyraven.command.sub.debug.OptionCommand;
import me.luckyraven.command.sub.debug.ReadNBTCommand;
import me.luckyraven.command.sub.debug.TimerCommand;
import me.luckyraven.command.sub.gang.GangCommand;
import me.luckyraven.command.sub.wanted.WantedCommand;
import me.luckyraven.command.sub.waypoint.TeleportCommand;
import me.luckyraven.command.sub.waypoint.WaypointCommand;
import me.luckyraven.data.account.gang.GangManager;
import me.luckyraven.data.account.gang.MemberManager;
import me.luckyraven.data.permission.PermissionManager;
import me.luckyraven.data.permission.PermissionWorker;
import me.luckyraven.data.placeholder.replacer.Replacer;
import me.luckyraven.data.placeholder.worker.GanglandPlaceholder;
import me.luckyraven.data.rank.RankManager;
import me.luckyraven.data.teleportation.Waypoint;
import me.luckyraven.data.teleportation.WaypointManager;
import me.luckyraven.data.teleportation.WaypointTeleport;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.database.DatabaseHandler;
import me.luckyraven.database.DatabaseManager;
import me.luckyraven.database.sub.GangDatabase;
import me.luckyraven.database.sub.RankDatabase;
import me.luckyraven.database.sub.UserDatabase;
import me.luckyraven.database.sub.WaypointDatabase;
import me.luckyraven.file.FileHandler;
import me.luckyraven.file.FileManager;
import me.luckyraven.file.LanguageLoader;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.file.configuration.ScoreboardAddon;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.file.configuration.inventory.InventoryLoader;
import me.luckyraven.listener.ListenerManager;
import me.luckyraven.listener.gang.GangMembersDamage;
import me.luckyraven.listener.inventory.InventoryOpenByCommand;
import me.luckyraven.listener.player.*;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;

import java.util.Objects;
import java.util.stream.Collectors;

public final class Initializer {

	private final Gangland gangland;

	// on plugin load
	private final @Getter InformationManager informationManager;

	// on plugin enable
	private @Getter UserManager<Player>        userManager;
	private @Getter UserManager<OfflinePlayer> offlineUserManager;
	private @Getter PermissionManager          permissionManager;
	private @Getter FileManager                fileManager;
	private @Getter DatabaseManager            databaseManager;
	private @Getter GangManager                gangManager;
	private @Getter MemberManager              memberManager;
	private @Getter ListenerManager            listenerManager;
	private @Getter CommandManager             commandManager;
	private @Getter RankManager                rankManager;
	private @Getter WaypointManager            waypointManager;
	private @Getter ScoreboardManager          scoreboardManager;
	// Addons
	private @Getter SettingAddon               settingAddon;
	private @Getter LanguageLoader             languageLoader;
	private @Getter ScoreboardAddon            scoreboardAddon;
	private @Getter InventoryLoader            inventoryLoader;
	private @Getter GanglandPlaceholder        placeholder;

	public Initializer(Gangland gangland) {
		this.gangland = gangland;
		// If at any instance these data failed to load, then the plugin will not function
		informationManager = new InformationManager();
		informationManager.processCommands();
	}

	public void postInitialize() {
		// File
		fileManager = new FileManager(gangland);
		files();

		// Database
		databaseManager = new DatabaseManager(gangland);
		databases();
		databaseManager.initializeDatabases();

		// Addons
		MessageAddon.setPlugin(gangland);

		// permission manager
		permissionManager = new PermissionManager(this.gangland, new PermissionWorker("gangland"));

		// add all registered plugin permissions
		permissionManager.addAllPermissions(Bukkit.getPluginManager()
		                                          .getPermissions()
		                                          .stream()
		                                          .map(Permission::getName)
		                                          .filter(permission -> permission.startsWith("gangland"))
		                                          .collect(Collectors.toSet()));

		// User manager
		userManager = new UserManager<>(gangland);
		offlineUserManager = new UserManager<>(gangland);

		// Rank manager
		rankManager = new RankManager(gangland);
		for (DatabaseHandler handler : databaseManager.getDatabases())
			if (handler instanceof RankDatabase rankDatabase) {
				rankManager.initialize(rankDatabase);
				break;
			}

		// Gang manager
		gangManager = new GangManager(gangland);
		memberManager = new MemberManager(gangland);
		for (DatabaseHandler handler : databaseManager.getDatabases())
			if (handler instanceof GangDatabase gangDatabase) {
				gangManager.initialize(gangDatabase);
				memberManager.initialize(gangDatabase, gangManager, rankManager);
				break;
			}

		// Waypoint manager
		waypointManager = new WaypointManager(gangland);
		for (DatabaseHandler handler : databaseManager.getDatabases())
			if (handler instanceof WaypointDatabase waypointDatabase) {
				waypointManager.initialize(waypointDatabase);
				break;
			}

		// Events
		listenerManager = new ListenerManager(gangland);
		events();
		listenerManager.registerEvents();

		// Commands
		commandManager = new CommandManager(gangland);
		commands(gangland);

		// Placeholder
		placeholder = new GanglandPlaceholder(gangland, Replacer.Closure.PERCENT);

	}

	@SuppressWarnings("CommentedOutCode")
	private void files() {
		fileManager.addFile(new FileHandler(gangland, "settings", ".yml"), true);
		fileManager.addFile(new FileHandler(gangland, "scoreboard", ".yml"), true);
		scoreboardManager = new ScoreboardManager(gangland);

		addonsLoader();

//		fileManager.addFile(new FileHandler("kits", ".yml"));
//		fileManager.addFile(new FileHandler("ammunition", ".yml"));
	}

	public void addonsLoader() {
		settingAddon = new SettingAddon(fileManager);
		languageLoader = new LanguageLoader(gangland);

		scoreboardLoader();
		inventoryLoader();
	}

	public void scoreboardLoader() {
		scoreboardAddon = new ScoreboardAddon(fileManager);
	}

	public void inventoryLoader() {
		inventoryLoader = new InventoryLoader(gangland);
	}

	private void databases() {
		int type;

		if (SettingAddon.getDatabaseType().equalsIgnoreCase("mysql")) type = DatabaseHandler.MYSQL;
		else type = DatabaseHandler.SQLITE;

		UserDatabase userDatabase = new UserDatabase(gangland);
		userDatabase.setType(type);
		databaseManager.addDatabase(userDatabase);

		GangDatabase gangDatabase = new GangDatabase(gangland);
		gangDatabase.setType(type);
		databaseManager.addDatabase(gangDatabase);

		RankDatabase rankDatabase = new RankDatabase(gangland);
		rankDatabase.setType(type);
		databaseManager.addDatabase(rankDatabase);

		WaypointDatabase waypointDatabase = new WaypointDatabase(gangland);
		waypointDatabase.setType(type);
		databaseManager.addDatabase(waypointDatabase);
	}

	private void events() {
		// player events
		listenerManager.addEvent(new CreateAccount(gangland));
		listenerManager.addEvent(new RemoveAccount(gangland));
		listenerManager.addEvent(new EntityDamage(gangland));
		listenerManager.addEvent(new BountyIncrease(gangland));
		listenerManager.addEvent(new PlayerDeath(gangland));
		listenerManager.addEvent(new LevelUp(gangland));
		listenerManager.addEvent(new WaypointTeleport(new Waypoint("dummy")));

		// switch events
		if (SettingAddon.isPhoneEnabled()) listenerManager.addEvent(new PhoneItem(gangland));
		if (SettingAddon.isScoreboardEnabled()) listenerManager.addEvent(new PlayerScoreboard(gangland));

		// gang events
		if (SettingAddon.isGangEnabled()) {
			listenerManager.addEvent(new GangMembersDamage(gangland));
		}

		// inventory events
		listenerManager.addEvent(new InventoryHandler(gangland, "dummy", 9, "dummy_inventory", false));
		listenerManager.addEvent(new InventoryOpenByCommand(gangland));
	}

	private void commands(Gangland gangland) {
		// initial command
		Objects.requireNonNull(this.gangland.getCommand("glw")).setExecutor(commandManager);

		// sub commands
		// default plugin commands
		commandManager.addCommand(new BalanceCommand(gangland));
		commandManager.addCommand(new BankCommand(gangland));
		commandManager.addCommand(new EconomyCommand(gangland));
		commandManager.addCommand(new RankCommand(gangland));
		commandManager.addCommand(new BountyCommand(gangland));
		commandManager.addCommand(new LevelCommand(gangland));
		commandManager.addCommand(new WaypointCommand(gangland));
		commandManager.addCommand(new TeleportCommand(gangland));
		commandManager.addCommand(new WantedCommand(gangland));

		// gang commands
		if (SettingAddon.isGangEnabled()) {
			commandManager.addCommand(new GangCommand(gangland));
		}

		// debug commands
		commandManager.addCommand(new DebugCommand(gangland));
		commandManager.addCommand(new OptionCommand(gangland));
		commandManager.addCommand(new ReadNBTCommand(gangland));
		commandManager.addCommand(new ReloadCommand(gangland));
		commandManager.addCommand(new TimerCommand(gangland));

		// Needs to be the final command to add all the help info
		commandManager.addCommand(new HelpCommand(gangland));

		PluginCommand command = this.gangland.getCommand("glw");

		if (command == null) return;

		command.setTabCompleter(new CommandTabCompleter(CommandHandler.getCommandHandlerMap()));
	}

}
