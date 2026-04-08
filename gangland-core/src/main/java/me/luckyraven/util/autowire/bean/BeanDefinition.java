package me.luckyraven.util.autowire.bean;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Internal description of a bean factory method discovered by {@link BeanFactory}. Built once during scan, sorted
 * topologically by {@link BeanGraph}, and finally invoked to produce the actual bean instance.
 *
 * @param factoryMethod the {@link Bean}-annotated method on the configuration class
 * @param configInstance the configuration instance the method will be invoked on
 * @param name the bean name (defaults to the method name when {@link Bean#name()} is empty)
 * @param returnType the raw return type of the factory method
 * @param parameterRequirements one entry per method parameter describing how to resolve it
 * @param conditionalOnBean types that must be present in the container for the bean to register
 * @param conditionalOnSetting setting keys that must resolve to {@code true} via {@link SettingsLookup}
 * @param publishToServicesManager whether to also publish the resulting bean via Bukkit's {@code ServicesManager}
 * @param isGeneric whether the return type is parameterized; consumers must disambiguate via {@link Qualifier}
 */
public record BeanDefinition(
		Method factoryMethod,
		Object configInstance,
		String name,
		Class<?> returnType,
		List<ParameterRequirement> parameterRequirements,
		List<Class<?>> conditionalOnBean,
		List<String> conditionalOnSetting,
		boolean publishToServicesManager,
		boolean isGeneric
) {

	/**
	 * Describes how a single bean-method parameter should be resolved.
	 *
	 * @param type the raw parameter type
	 * @param qualifier the {@link Qualifier#value()} if present, else {@code null}
	 */
	public record ParameterRequirement(Class<?> type, String qualifier) {
	}
}
