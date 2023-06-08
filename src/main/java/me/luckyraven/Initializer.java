package me.luckyraven;

import lombok.Getter;
import me.luckyraven.command.CommandManager;
import me.luckyraven.command.data.InformationManager;
import me.luckyraven.command.sub.SCBalance;
import me.luckyraven.command.sub.SCHelp;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.database.DatabaseHandler;
import me.luckyraven.database.DatabaseManager;
import me.luckyraven.database.sub.GangDatabase;
import me.luckyraven.database.sub.UserDatabase;
import me.luckyraven.file.FileHandler;
import me.luckyraven.file.FileManager;
import me.luckyraven.file.LanguageLoader;
import me.luckyraven.listener.ListenerManager;
import me.luckyraven.listener.player.CreateAccount;
import me.luckyraven.listener.player.RemoveAccount;
import me.luckyraven.rank.RankManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class Initializer {

	private final JavaPlugin plugin;

	// on plugin load
	private @Getter
	final InformationManager  informationManager;
	private @Getter
	final UserManager<Player> userManager;

	// on plugin enable
	private @Getter FileManager     fileManager;
	private @Getter DatabaseManager databaseManager;
	private @Getter ListenerManager listenerManager;
	private @Getter CommandManager  commandManager;
	private @Getter RankManager     rankManager;
	private @Getter LanguageLoader  languageLoader;


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

		// Database
		databaseManager = new DatabaseManager();
		databases();
		databaseManager.initializeDatabases();

		// Ranks
		rankManager = new RankManager();
		rankManager.processRanks();

		// Events
		listenerManager = new ListenerManager(plugin);
		events();
		listenerManager.registerEvents();

		// Commands
		commandManager = new CommandManager(plugin);
		commands();
	}

	private void events() {
		if (plugin instanceof Gangland) {
			listenerManager.addEvent(new CreateAccount((Gangland) plugin));
			listenerManager.addEvent(new RemoveAccount((Gangland) plugin));
		}
	}

	private void commands() {
		// initial command
		Objects.requireNonNull(plugin.getCommand("glw")).setExecutor(commandManager);

		if (plugin instanceof Gangland) {
			// sub commands
			commandManager.addCommand(new SCBalance((Gangland) plugin));
			commandManager.addCommand(new SCHelp((Gangland) plugin));
		}
	}

	private void files() {
		fileManager.addFile(new FileHandler(plugin, "settings", ".yml"), true);

		this.languageLoader = new LanguageLoader(plugin, fileManager);

//		fileManager.addFile(new FileHandler("scoreboard", ".yml"));
//		fileManager.addFile(new FileHandler("kits", ".yml"));
//		fileManager.addFile(new FileHandler("ammunition", ".yml"));
//		fileManager.addFile(new FileHandler("spawn", "navigation", ".yml"));
//		fileManager.addFile(new FileHandler("warp", "navigation", ".yml"));
//		fileManager.addFile(new FileHandler("player_data", "data", ".db"));
//		fileManager.addFile(new FileHandler("gang_data", "data", ".db"));
	}

	private void databases() {
		databaseManager.addDatabase(new UserDatabase(plugin, DatabaseHandler.MYSQL, fileManager));
		databaseManager.addDatabase(new GangDatabase(plugin, DatabaseHandler.MYSQL));
	}

}
