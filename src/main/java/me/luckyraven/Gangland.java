package me.luckyraven;

import lombok.Getter;
import me.luckyraven.command.data.InformationManager;
import me.luckyraven.data.user.UserManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

public final class Gangland extends JavaPlugin {

	private static Gangland instance;

	private @Getter InformationManager informationManager;
	private @Getter UserManager        userManager;
	private @Getter Initializer        initializer;

	private @Getter
	final Logger logger = super.getLogger();

	public static Gangland getInstance() {
		if (instance == null) new Gangland();
		return instance;
	}

	@Override
	public void onLoad() {
		informationManager = new InformationManager();
		informationManager.processCommands();

		userManager = new UserManager();
	}

	@Override
	public void onEnable() {
		instance = this;
		// IMPORTANT - everything that uses this instance variable should be placed below this line
		initializer = new Initializer();
	}

	@Override
	public void onDisable() {
		// Plugin shutdown logic
	}

}
