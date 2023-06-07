package me.luckyraven.database.sub;

import me.luckyraven.database.DatabaseHandler;
import me.luckyraven.file.FileManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;

public class UserDatabase extends DatabaseHandler {

	private final FileManager fileManager;

	public UserDatabase(JavaPlugin plugin, FileManager fileManager, int type) throws SQLException {
		super(plugin, fileManager, type);
		this.fileManager = fileManager;
	}

	@Override
	public String fileName() {
		return "user";
	}

	@Override
	public void tables() throws SQLException {
		getDatabase().initialize(fileManager);
		getDatabase().table("data").createTable("uuid CHAR(36) PRIMARY KEY NOT NULL", "kills INT NOT NULL",
		                                        "deaths INT NOT NULL", "mob_kills INT NOT NULL",
		                                        "has_bank BOOLEAN NOT NULL", "has_gang BOOLEAN NOT NULL",
		                                        "bounty DOUBLE NOT NULL", "date_joined DATE NOT NULL");
		getDatabase().table("bank").createTable("uuid CHAR(36) PRIMARY KEY NOT NULL", "name TEXT NOT NULL",
		                                        "balance DOUBLE NOT NULL");
		getDatabase().table("account").createTable("uuid CHAR(36) PRIMARY KEY NOT NULL", "balance DOUBLE NOT NULL",
		                                           "gang_id INT NOT NULL");
	}

}
