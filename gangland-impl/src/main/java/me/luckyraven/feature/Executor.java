package me.luckyraven.feature;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.luckyraven.util.timer.Timer;
import org.bukkit.plugin.java.JavaPlugin;

@Getter(AccessLevel.PROTECTED)
@RequiredArgsConstructor
public abstract class Executor {

	private final JavaPlugin plugin;
	private final String     name;

	public abstract Timer createTimer();

	protected abstract void execute(Timer timer);

}
