package org.luckyraven.gangland.listener;

import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.core.bean.autowire.DependencyContainer;
import org.luckyraven.gangland.core.bean.listener.ListenerService;
import org.luckyraven.gangland.file.configuration.Settings;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class ListenerManager extends ListenerService {

	public ListenerManager(JavaPlugin plugin, DependencyContainer dependencyContainer) {
		super(plugin, dependencyContainer);
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
