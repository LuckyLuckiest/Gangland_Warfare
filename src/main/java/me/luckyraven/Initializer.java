package me.luckyraven;

import lombok.Getter;
import me.luckyraven.account.gang.GangManager;
import me.luckyraven.account.gang.MemberManager;
import me.luckyraven.bukkit.inventory.InventoryHandler;
import me.luckyraven.command.CommandHandler;
import me.luckyraven.command.CommandManager;
import me.luckyraven.command.CommandTabCompleter;
import me.luckyraven.command.data.InformationManager;
import me.luckyraven.command.sub.*;
import me.luckyraven.command.sub.gang.GangCommand;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.database.DatabaseHandler;
import me.luckyraven.database.DatabaseManager;
import me.luckyraven.database.sub.GangDatabase;
import me.luckyraven.database.sub.RankDatabase;
import me.luckyraven.database.sub.UserDatabase;
import me.luckyraven.file.FileHandler;
import me.luckyraven.file.FileManager;
import me.luckyraven.file.LanguageLoader;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.listener.ListenerManager;
import me.luckyraven.listener.gang.GangMembersDamage;
import me.luckyraven.listener.player.*;
import me.luckyraven.rank.RankManager;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class Initializer {

	private final JavaPlugin plugin;

	// on plugin load
	private final @Getter InformationManager         informationManager;
	private final @Getter UserManager<Player>        userManager;
	private final @Getter UserManager<OfflinePlayer> offlineUserManager;
	private final @Getter FileManager                fileManager;

	// on plugin enable
	private @Getter DatabaseManager databaseManager;
	private @Getter GangManager     gangManager;
	private @Getter MemberManager   memberManager;
	private @Getter ListenerManager listenerManager;
	private @Getter CommandManager  commandManager;
	private @Getter RankManager     rankManager;
	private @Getter LanguageLoader  languageLoader;
	// Addons
	private @Getter SettingAddon    settingAddon;


	public Initializer(JavaPlugin plugin) {
		this.plugin = plugin;
		// If at any instance these data failed to load, then the plugin will not function
		informationManager = new InformationManager();
		informationManager.processCommands();

		// User manager
		userManager = new UserManager<>();
		offlineUserManager = new UserManager<>();

		// File
		fileManager = new FileManager(plugin);
		files();
	}

	public void postInitialize() {
		// Database
		databaseManager = new DatabaseManager(plugin);
		databases();
		databaseManager.initializeDatabases();

		if (plugin instanceof Gangland gangland) {
			// Addons
			MessageAddon.setPlugin(gangland);

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
		}

		// Events
		listenerManager = new ListenerManager(plugin);
		events();
		listenerManager.registerEvents();

		// Commands
		if (plugin instanceof Gangland gangland) {
			commandManager = new CommandManager(gangland);
			commands(gangland);
		}

	}

	@SuppressWarnings("CommentedOutCode")
	private void files() {
		fileManager.addFile(new FileHandler(plugin, "settings", ".yml"), true);

		addonsLoader();

//		fileManager.addFile(new FileHandler("scoreboard", ".yml"));
//		fileManager.addFile(new FileHandler("kits", ".yml"));
//		fileManager.addFile(new FileHandler("ammunition", ".yml"));
	}

	public void addonsLoader() {
		settingAddon = new SettingAddon(fileManager);

		this.languageLoader = new LanguageLoader(plugin, fileManager);
	}

	private void databases() {
		int type;

		if (SettingAddon.getDatabaseType().equalsIgnoreCase("mysql")) type = DatabaseHandler.MYSQL;
		else type = DatabaseHandler.SQLITE;

		UserDatabase userDatabase = new UserDatabase(plugin);
		userDatabase.setType(type);
		databaseManager.addDatabase(userDatabase);

		GangDatabase gangDatabase = new GangDatabase(plugin);
		gangDatabase.setType(type);
		databaseManager.addDatabase(gangDatabase);

		RankDatabase rankDatabase = new RankDatabase(plugin);
		rankDatabase.setType(type);
		databaseManager.addDatabase(rankDatabase);
	}

	private void events() {
		if (plugin instanceof Gangland gangland) {
			// player events
			listenerManager.addEvent(new CreateAccount(gangland));
			listenerManager.addEvent(new RemoveAccount(gangland));
			listenerManager.addEvent(new EntityDamage(gangland));
			listenerManager.addEvent(new BountyIncrease(gangland));
			listenerManager.addEvent(new PlayerDeath(gangland));
			if (SettingAddon.isPhoneEnabled()) listenerManager.addEvent(new PhoneItem(gangland));

			// gang events
			if (SettingAddon.isGangEnable()) {
				listenerManager.addEvent(new GangMembersDamage(gangland));
			}

			// inventory gui test double listener
			listenerManager.addEvent(new InventoryHandler(gangland, "dummy", 9, null));
		}
	}

	private void commands(Gangland gangland) {
		// initial command
		Objects.requireNonNull(plugin.getCommand("glw")).setExecutor(commandManager);

		// sub commands
		// default plugin commands
		commandManager.addCommand(new BalanceCommand(gangland));
		commandManager.addCommand(new BankCommand(gangland));
		commandManager.addCommand(new EconomyCommand(gangland));
		commandManager.addCommand(new RankCommand(gangland));
		commandManager.addCommand(new BountyCommand(gangland));
		commandManager.addCommand(new LevelCommand(gangland));
		// gang commands
		if (SettingAddon.isGangEnable()) {
			commandManager.addCommand(new GangCommand(gangland));
		}

		// debug commands
		commandManager.addCommand(new DebugCommand(gangland));
		commandManager.addCommand(new OptionCommand(gangland));
		commandManager.addCommand(new ReadNBTCommand(gangland));
		commandManager.addCommand(new ReloadCommand(gangland));

		// Needs to be the final command to add all the help info
		commandManager.addCommand(new HelpCommand(gangland));

		Objects.requireNonNull(plugin.getCommand("glw")).setTabCompleter(
				new CommandTabCompleter(CommandHandler.getCommandHandlerMap()));
	}

}
