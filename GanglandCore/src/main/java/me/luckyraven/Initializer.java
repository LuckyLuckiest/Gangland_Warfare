package me.luckyraven;

import lombok.Getter;
import me.luckyraven.bukkit.inventory.InventoryHandler;
import me.luckyraven.bukkit.scoreboard.ScoreboardManager;
import me.luckyraven.command.CommandManager;
import me.luckyraven.command.CommandTabCompleter;
import me.luckyraven.command.data.InformationManager;
import me.luckyraven.command.sub.*;
import me.luckyraven.command.sub.bank.BankCommand;
import me.luckyraven.command.sub.bounty.BountyCommand;
import me.luckyraven.command.sub.debug.DebugCommand;
import me.luckyraven.command.sub.debug.OptionCommand;
import me.luckyraven.command.sub.debug.ReadNBTCommand;
import me.luckyraven.command.sub.debug.TimerCommand;
import me.luckyraven.command.sub.gang.GangCommand;
import me.luckyraven.command.sub.rank.RankCommand;
import me.luckyraven.command.sub.wanted.WantedCommand;
import me.luckyraven.command.sub.waypoint.TeleportCommand;
import me.luckyraven.command.sub.waypoint.WaypointCommand;
import me.luckyraven.command.sub.weapon.AmmunitionCommand;
import me.luckyraven.command.sub.weapon.WeaponCommand;
import me.luckyraven.compatibility.CompatibilitySetup;
import me.luckyraven.compatibility.CompatibilityWorker;
import me.luckyraven.compatibility.VersionSetup;
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
import me.luckyraven.database.GanglandDatabase;
import me.luckyraven.database.component.Table;
import me.luckyraven.database.tables.*;
import me.luckyraven.exception.PluginException;
import me.luckyraven.feature.weapon.WeaponManager;
import me.luckyraven.file.FileHandler;
import me.luckyraven.file.FileManager;
import me.luckyraven.file.LanguageLoader;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.file.configuration.ScoreboardAddon;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.file.configuration.inventory.InventoryLoader;
import me.luckyraven.file.configuration.weapon.AmmunitionAddon;
import me.luckyraven.file.configuration.weapon.WeaponAddon;
import me.luckyraven.file.configuration.weapon.WeaponLoader;
import me.luckyraven.listener.ListenerManager;
import me.luckyraven.listener.gang.GangMembersDamage;
import me.luckyraven.listener.inventory.InventoryOpenByCommand;
import me.luckyraven.listener.player.*;
import me.luckyraven.listener.player.weapon.WeaponDropped;
import me.luckyraven.listener.player.weapon.WeaponInteract;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class Initializer {

	private final Gangland gangland;

	// on plugin load
	private final @Getter InformationManager informationManager;
	private final @Getter VersionSetup       versionSetup;
	private final @Getter CompatibilitySetup compatibilitySetup;

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
	private @Getter WeaponManager              weaponManager;
	private @Getter GanglandDatabase           ganglandDatabase;
	// Addons
	private @Getter SettingAddon               settingAddon;
	private @Getter LanguageLoader             languageLoader;
	private @Getter ScoreboardAddon            scoreboardAddon;
	private @Getter InventoryLoader            inventoryLoader;
	private @Getter GanglandPlaceholder        placeholder;
	private @Getter AmmunitionAddon            ammunitionAddon;
	private @Getter WeaponLoader               weaponLoader;
	private @Getter WeaponAddon                weaponAddon;
	// Compatibility
	private @Getter CompatibilityWorker        compatibilityWorker;

	public Initializer(Gangland gangland) {
		this.gangland = gangland;

		// If at any instance these data failed to load, then the plugin will not function
		this.informationManager = new InformationManager();
		this.informationManager.processCommands();

		this.versionSetup       = new VersionSetup();
		this.compatibilitySetup = new CompatibilitySetup(gangland);
	}

	public void postInitialize() {
		// permission manager
		permissionManager = new PermissionManager(this.gangland, new PermissionWorker("gangland"));

		// File
		fileManager = new FileManager(gangland);
		files();

		// Database
		databaseManager = new DatabaseManager(gangland);
		databases();
		databaseManager.initializeDatabases();

		// Addons
		MessageAddon.setPlugin(gangland);

		// add all registered plugin permissions
		permissionManager.addAllPermissions(Bukkit.getPluginManager()
												  .getPermissions()
												  .stream()
												  .map(Permission::getName)
												  .filter(permission -> permission.startsWith("gangland"))
												  .collect(Collectors.toSet()));

		// User manager
		userManager        = new UserManager<>(gangland);
		offlineUserManager = new UserManager<>(gangland);

		// initialize the database
		ganglandDatabase = GanglandDatabase.findInstance(databaseManager);

		// manage if the database was null
		if (ganglandDatabase == null) {
			throw new PluginException("Gangland Database instance is not found.");
			// plugin crashes
		}

		List<Table<?>> tables = ganglandDatabase.getTables().stream().toList();

		// Rank manager
		rankManager = new RankManager(gangland);

		// initialize the rank class
		RankTable           rankTable           = getInstanceFromTables(RankTable.class, tables);
		RankParentTable     rankParentTable     = getInstanceFromTables(RankParentTable.class, tables);
		PermissionTable     permissionTable     = getInstanceFromTables(PermissionTable.class, tables);
		RankPermissionTable rankPermissionTable = getInstanceFromTables(RankPermissionTable.class, tables);

		rankManager.initialize(rankTable, rankParentTable, permissionTable, rankPermissionTable);

		// Gang manager
		gangManager   = new GangManager(gangland);
		memberManager = new MemberManager(gangland);

		// initialize the gang and member classes
		GangTable      gangTable      = getInstanceFromTables(GangTable.class, tables);
		GangAllieTable gangAllieTable = getInstanceFromTables(GangAllieTable.class, tables);
		MemberTable    memberTable    = getInstanceFromTables(MemberTable.class, tables);

		gangManager.initialize(gangTable, gangAllieTable);
		memberManager.initialize(memberTable, gangManager, rankManager);

		// Waypoint manager
		waypointManager = new WaypointManager(gangland);

		WaypointTable waypointTable = getInstanceFromTables(WaypointTable.class, tables);

		// initialize the waypoint class
		waypointManager.initialize(waypointTable);

		// Weapon manager
		weaponManager = new WeaponManager(gangland);

		WeaponTable weaponTable = getInstanceFromTables(WeaponTable.class, tables);

		// initialize the weapon class
		weaponManager.initialize(weaponTable);

		// Events
		listenerManager = new ListenerManager(gangland);
		events();
		listenerManager.registerEvents();

		// Commands
		commandManager = new CommandManager(gangland);
		commands(gangland);

		// Placeholder
		placeholder = new GanglandPlaceholder(gangland, Replacer.Closure.PERCENT);

		// Compatibility loader
		compatibilityWorker = new CompatibilityWorker(gangland);
	}

	public void addonsLoader() {
		settingAddon   = new SettingAddon(fileManager);
		languageLoader = new LanguageLoader(gangland);

		scoreboardLoader();
		inventoryLoader();
		weaponLoader();
	}

	public void scoreboardLoader() {
		scoreboardAddon = new ScoreboardAddon(fileManager);
	}

	public void inventoryLoader() {
		inventoryLoader = new InventoryLoader(gangland);

		inventoryLoader.addFile(new FileHandler(gangland, "gang_info", "inventory", ".yml"));
		inventoryLoader.addFile(new FileHandler(gangland, "phone", "inventory", ".yml"));
		inventoryLoader.addFile(new FileHandler(gangland, "phone_gang", "inventory", ".yml"));

		inventoryLoader.initialize();
	}

	public void weaponLoader() {
		ammunitionAddon = new AmmunitionAddon(fileManager);
		weaponAddon     = new WeaponAddon();
		weaponLoader    = new WeaponLoader(gangland);

		weaponLoader.addFile(new FileHandler(gangland, "rifle", "weapon", ".yml"));

		weaponLoader.initialize();
	}

	public <E> E getInstanceFromTables(Class<E> clazz, List<Table<?>> tables) {
		return tables.stream()
					 .filter(clazz::isInstance)
					 .map(clazz::cast)
					 .findFirst()
					 .orElseThrow(() -> new RuntimeException("There was a problem finding class, " + clazz.getName()));
	}

	private void files() {
		fileManager.addFile(new FileHandler(gangland, "settings", ".yml"), true);
		fileManager.addFile(new FileHandler(gangland, "scoreboard", ".yml"), true);
		fileManager.addFile(new FileHandler(gangland, "ammunition", ".yml"), true);
		scoreboardManager = new ScoreboardManager(gangland);

		addonsLoader();
	}

	private void databases() {
		int type;

		if (SettingAddon.getDatabaseType().equalsIgnoreCase("mysql")) type = DatabaseHandler.MYSQL;
		else type = DatabaseHandler.SQLITE;

		// Primary database
		GanglandDatabase ganglandDatabase = new GanglandDatabase(gangland);
		ganglandDatabase.setType(type);
		databaseManager.addDatabase(ganglandDatabase);
	}

	private void events() {
		// player events
		listenerManager.addEvent(new CreateAccount(gangland));
		listenerManager.addEvent(new RemoveAccount(gangland));
		listenerManager.addEvent(new EntityDamage(gangland));
		listenerManager.addEvent(new BountyIncrease(gangland));
		listenerManager.addEvent(new PlayerDeath(gangland));
		listenerManager.addEvent(new LevelUp(gangland));
		listenerManager.addEvent(new LoadResourcePack());
		listenerManager.addEvent(new WaypointTeleport(new Waypoint("dummy")));

		// weapon events
		listenerManager.addEvent(new WeaponInteract(gangland));
		listenerManager.addEvent(new WeaponDropped(gangland));

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
		commandManager.addCommand(new WeaponCommand(gangland));
		commandManager.addCommand(new AmmunitionCommand(gangland));
		commandManager.addCommand(new DownloadResourceCommand(gangland));

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

		// Needs to be the final command to add all the help information
		commandManager.addCommand(new HelpCommand(gangland));

		PluginCommand command = this.gangland.getCommand("glw");

		if (command == null) return;

		command.setTabCompleter(new CommandTabCompleter(CommandManager.getCommands()));
	}

}
