package me.luckyraven;

import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

public final class Gangland extends JavaPlugin {

	private @Getter Initializer initializer;

	private @Getter
	final Logger logger = super.getLogger();

	@Override
	public void onLoad() {
		initializer = new Initializer(this);
	}

	@Override
	public void onEnable() {
		initializer.postInitialize();
	}

	@Override
	public void onDisable() {
		// Plugin shutdown logic
	}

}
