package me.luckyraven;

import lombok.Getter;
import me.luckyraven.command.CommandManager;
import me.luckyraven.command.sub.SCBalance;
import me.luckyraven.command.sub.SCHelp;
import me.luckyraven.data.rank.Rank;
import me.luckyraven.data.rank.types.Member;
import me.luckyraven.data.rank.types.Owner;
import me.luckyraven.file.FileHandler;
import me.luckyraven.file.FileManager;
import me.luckyraven.file.LanguageLoader;
import me.luckyraven.listener.ListenerManager;
import me.luckyraven.listener.player.CreateAccount;

import java.util.Objects;

public final class Initializer {

	private static final Gangland gangland = Gangland.getInstance();

	private @Getter
	final           FileManager     fileManager;
	private @Getter
	final           ListenerManager listenerManager;
	private @Getter
	final           CommandManager  commandManager;
	private @Getter
	final           Rank            rank;
	private @Getter LanguageLoader  languageLoader;


	public Initializer() {
		// Files
		fileManager = new FileManager();
		files();

		// Events
		listenerManager = new ListenerManager();
		events();
		listenerManager.registerEvents();

		// Commands
		commandManager = new CommandManager();
		commands();

		// Ranks
		rank = new Rank();
		ranks();
	}

	private void events() {
		listenerManager.addEvent(new CreateAccount());
	}

	private void commands() {
		// initial command
		Objects.requireNonNull(gangland.getCommand("glw")).setExecutor(commandManager);

		// sub commands
		CommandManager.addCommand(new SCBalance());
		CommandManager.addCommand(new SCHelp());
	}

	private void files() {
		fileManager.addFile(new FileHandler("settings", ".yml"));

		this.languageLoader = new LanguageLoader(fileManager);

//		fileManager.addFile(new FileHandler("scoreboard", ".yml"));
//		fileManager.addFile(new FileHandler("kits", ".yml"));
//		fileManager.addFile(new FileHandler("ammunition", ".yml"));
//		fileManager.addFile(new FileHandler("spawn", "navigation", ".yml"));
//		fileManager.addFile(new FileHandler("warp", "navigation", ".yml"));
//		fileManager.addFile(new FileHandler("player_data", "data", ".db"));
//		fileManager.addFile(new FileHandler("gang_data", "data", ".db"));
	}

	private void ranks() {
		rank.add(new Owner());
		rank.add(new Member());
	}

}
