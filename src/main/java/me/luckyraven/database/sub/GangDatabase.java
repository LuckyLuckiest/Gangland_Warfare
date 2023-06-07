package me.luckyraven.database.sub;

import me.luckyraven.database.DatabaseHandler;
import me.luckyraven.file.FileManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;

public class GangDatabase extends DatabaseHandler {

	private final FileManager fileManager;

	public GangDatabase(JavaPlugin plugin, FileManager fileManager, int type) throws SQLException {
		super(plugin, fileManager, type);
		this.fileManager = fileManager;
	}

	@Override
	public String fileName() {
		return "gang";
	}

	@Override
	public void tables() throws SQLException {
		getDatabase().initialize(fileManager);
		// For time use this method 'julianday('now')'
		getDatabase().table("data").createTable("id INT PRIMARY KEY NOT NULL", "name VARCHAR(16) NOT NULL",
		                                        "description TEXT NOT NULL", "members LONGTEXT NOT NULL",
		                                        "bounty DOUBLE NOT NULL", "alias LONGTEXT NOT NULL",
		                                        "created DATE NOT NULL");
		getDatabase().table("account").createTable("id INT PRIMARY KEY NOT NULL", "balance DOUBLE NOT NULL");
	}

}
