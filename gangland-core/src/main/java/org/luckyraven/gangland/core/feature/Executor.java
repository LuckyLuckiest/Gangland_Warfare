package org.luckyraven.gangland.core.feature;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.core.timer.Timer;

@Getter(AccessLevel.PROTECTED)
@RequiredArgsConstructor
public abstract class Executor {

	private final JavaPlugin plugin;
	private final String     name;

	public abstract Timer createTimer();

	protected abstract void execute(Timer timer);

}
