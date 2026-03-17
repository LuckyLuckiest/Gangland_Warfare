package me.luckyraven.listener;

import me.luckyraven.file.configuration.Settings;
import me.luckyraven.util.listener.ListenerService;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class ListenerManager extends ListenerService {

	public ListenerManager(JavaPlugin plugin) {
		super(plugin);
	}

	@Override
	public boolean invokeMethod(String condition) throws InvocationTargetException, IllegalAccessException {
		Method method = Settings.getSetting(condition);

		if (method != null && (method.getReturnType().getSimpleName().equalsIgnoreCase("boolean") ||
							   method.getReturnType() == Boolean.class)) {
			return (boolean) method.invoke(null);
		}

		return false;
	}

}
