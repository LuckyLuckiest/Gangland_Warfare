package me.luckyraven;

import lombok.Getter;
import me.luckyraven.account.gang.GangManager;
import me.luckyraven.command.CommandManager;
import me.luckyraven.command.data.InformationManager;
import me.luckyraven.command.sub.*;
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
import me.luckyraven.listener.player.CreateAccount;
import me.luckyraven.listener.player.RemoveAccount;
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
	private @Getter ListenerManager listenerManager;
	private @Getter CommandManager  commandManager;
	private @Getter RankManager     rankManager;
	private @Getter LanguageLoader  languageLoader;
	// Addons
	private @Getter SettingAddon    settingAddon;
	private @Getter MessageAddon    messageAddon;


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
			messageAddon = new MessageAddon(gangland);
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
			for (DatabaseHandler handler : databaseManager.getDatabases())
				if (handler instanceof GangDatabase gangDatabase) {
					gangManager.initialize(gangDatabase);
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

	private void events() {
		if (plugin instanceof Gangland) {
			listenerManager.addEvent(new CreateAccount((Gangland) plugin));
			listenerManager.addEvent(new RemoveAccount((Gangland) plugin));
		}
	}

	private void commands(Gangland gangland) {
		// initial command
		Objects.requireNonNull(plugin.getCommand("glw")).setExecutor(commandManager);

		// sub commands
		// default plugin commands
		commandManager.addCommand(new SCBalance(gangland));
		commandManager.addCommand(new SCGang(gangland));
		commandManager.addCommand(new SCBank(gangland));
		commandManager.addCommand(new SCEconomy(gangland));
		commandManager.addCommand(new SCRank(gangland));

		// debug commands
		commandManager.addCommand(new SCDebug(gangland));
		commandManager.addCommand(new SCTesting(gangland));

		// Needs to be the final command to add all the help info
		commandManager.addCommand(new SCHelp(gangland));

		Objects.requireNonNull(plugin.getCommand("glw")).setTabCompleter(
				commandManager.getCommands().values().stream().toList().get(0));
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

}
