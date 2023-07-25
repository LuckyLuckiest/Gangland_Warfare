package me.luckyraven;

import lombok.Getter;
import me.luckyraven.account.gang.GangManager;
import me.luckyraven.account.gang.MemberManager;
import me.luckyraven.bukkit.gui.InventoryGUI;
import me.luckyraven.command.CommandManager;
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
import me.luckyraven.util.UnhandledError;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.Objects;

public final class Initializer {

	private final JavaPlugin plugin;

	// on plugin load
	@Getter
	private final InformationManager  informationManager;
	@Getter
	private final UserManager<Player> userManager;

	// on plugin enable
	private @Getter FileManager     fileManager;
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
		// If at any instance these data failed to load then the plugin will not function
		informationManager = new InformationManager();
		informationManager.processCommands();

		userManager = new UserManager<>();
	}

	public void postInitialize() {
		// Files
		fileManager = new FileManager(plugin);
		files();

		// Addons
		if (plugin instanceof Gangland gangland) {
			settingAddon = new SettingAddon(fileManager);
			MessageAddon.setPlugin(gangland);
		}

		// Database
		databaseManager = new DatabaseManager(plugin);
		databases();
		databaseManager.initializeDatabases();

		if (plugin instanceof Gangland gangland) {
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

		this.languageLoader = new LanguageLoader(plugin, fileManager);

//		fileManager.addFile(new FileHandler("scoreboard", ".yml"));
//		fileManager.addFile(new FileHandler("kits", ".yml"));
//		fileManager.addFile(new FileHandler("ammunition", ".yml"));
//		fileManager.addFile(new FileHandler("spawn", "navigation", ".yml"));
//		fileManager.addFile(new FileHandler("warp", "navigation", ".yml"));
	}

	private void databases() {
		int type = -1;
		try {
			fileManager.checkFileLoaded("settings");

			FileConfiguration settings = fileManager.getFile("settings").getFileConfiguration();

			switch (Objects.requireNonNull(settings.getString("Database.Type")).toLowerCase()) {
				case "mysql" -> type = DatabaseHandler.MYSQL;
				case "sqlite" -> type = DatabaseHandler.SQLITE;
			}
		} catch (IOException exception) {
			plugin.getLogger().warning(UnhandledError.FILE_LOADER_ERROR + ": " + exception.getMessage());
		}

		UserDatabase userDatabase = new UserDatabase(plugin, fileManager);
		userDatabase.setType(type);
		databaseManager.addDatabase(userDatabase);

		GangDatabase gangDatabase = new GangDatabase(plugin, fileManager);
		gangDatabase.setType(type);
		databaseManager.addDatabase(gangDatabase);

		RankDatabase rankDatabase = new RankDatabase(plugin, fileManager);
		rankDatabase.setType(type);
		databaseManager.addDatabase(rankDatabase);
	}

	private void events() {
		if (plugin instanceof Gangland gangland) {
			listenerManager.addEvent(new CreateAccount(gangland));
			listenerManager.addEvent(new RemoveAccount(gangland));
			listenerManager.addEvent(new GangMembersDamage(gangland));
			listenerManager.addEvent(new EntityDamage(gangland));
			listenerManager.addEvent(new BountyIncrease());
			listenerManager.addEvent(new PhoneItem());

			// inventory gui test double listener
			listenerManager.addEvent(new InventoryGUI("dummy", 9));
		}
	}

	private void commands(Gangland gangland) {
		// initial command
		Objects.requireNonNull(plugin.getCommand("glw")).setExecutor(commandManager);

		// sub commands
		// default plugin commands
		commandManager.addCommand(new BalanceCommand(gangland));
		commandManager.addCommand(new GangCommand(gangland));
		commandManager.addCommand(new BankCommand(gangland));
		commandManager.addCommand(new EconomyCommand(gangland));
		commandManager.addCommand(new RankCommand(gangland));
		commandManager.addCommand(new BountyCommand(gangland));

		// debug commands
		commandManager.addCommand(new DebugCommand(gangland));
		commandManager.addCommand(new OptionCommand(gangland));
		commandManager.addCommand(new ReadNBTCommand(gangland));

		// Needs to be the final command to add all the help info
		commandManager.addCommand(new HelpCommand(gangland));

		Objects.requireNonNull(plugin.getCommand("glw")).setTabCompleter(
				commandManager.getCommands().values().stream().toList().get(0));
	}

}
