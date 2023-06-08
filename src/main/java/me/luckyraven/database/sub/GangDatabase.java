package me.luckyraven.database.sub;

import me.luckyraven.database.DatabaseHandler;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.Map;

public class GangDatabase extends DatabaseHandler {
	
	public GangDatabase(JavaPlugin plugin, int type) {
		super(plugin, type);
	}

	@Override
	public Map<String, Object> credentials() {
		return null;
	}

	@Override
	public void tables() throws SQLException {
		// For time use this method 'julianday('now')'
		getDatabase().table("data").createTable("id INT PRIMARY KEY NOT NULL", "name VARCHAR(16) NOT NULL",
		                                        "description TEXT NOT NULL", "members LONGTEXT NOT NULL",
		                                        "bounty DOUBLE NOT NULL", "alias LONGTEXT NOT NULL",
		                                        "created DATE NOT NULL");
		getDatabase().table("account").createTable("id INT PRIMARY KEY NOT NULL", "balance DOUBLE NOT NULL");
	}

}
