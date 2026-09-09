package org.luckyraven.gangland.core.testsupport;

import org.luckyraven.keystone.util.ReflectionUtil;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Pins B-1 (0.9.0, phase-D review blocker): Keystone instantiates every {@code @Configuration} class
 * ({@code BeanFactory.java:219-233}) before any bean phase runs — at that moment the container holds only
 * {@code Gangland}, {@code GanglandContext}, {@code DependencyContainer} and {@code ModuleLoader}. A
 * {@code @Configuration} constructor asking for anything else (e.g. {@code GadgetModuleConfig}'s old
 * {@code FuelService} parameter) throws {@code IllegalStateException: Failed to instantiate @Configuration class …}
 * out of {@code BeanFactory.instantiate}, sinking the whole plugin.
 *
 * <p>Implemented as a static parameter-type whitelist check rather than a real construction attempt: every
 * {@code @Configuration} class in this codebase only field-assigns its constructor parameters (no other
 * constructor-time logic), so a signature check catches the same bug deterministically without needing working
 * mock instances of {@code Gangland}/{@code GanglandContext} (both non-trivial {@code JavaPlugin} subtypes).
 */
public final class ConfigurationConstructorScan {

	private static final Set<String> AVAILABLE_AT_CONFIGURATION_TIME = Set.of(
			"org.luckyraven.gangland.Gangland",
			"org.luckyraven.gangland.bootstrap.GanglandContext",
			"org.luckyraven.keystone.bean.autowire.DependencyContainer",
			"org.luckyraven.keystone.module.ModuleLoader"
	);

	private static final String CONFIGURATION_ANNOTATION = "org.luckyraven.keystone.bean.Configuration";

	private ConfigurationConstructorScan() {
	}

	/**
	 * Every {@code @Configuration} class under {@code basePackage} (seen through {@code classLoader}) whose
	 * declared constructor asks for a parameter type outside the four available at {@code @Configuration}
	 * instantiation time. Empty means every configuration in the package is safe to bootstrap.
	 *
	 * @return {@code "<ClassName>(<paramSimpleName>)"} entries, one per offending parameter.
	 */
	public static List<String> findUnavailableDependencyConfigs(String basePackage, ClassLoader classLoader) {
		Set<Class<?>> classes    = ReflectionUtil.findClasses(basePackage, classLoader);
		List<String>  violations = new ArrayList<>();

		for (Class<?> clazz : classes) {
			if (!isConfiguration(clazz)) continue;

			for (Constructor<?> constructor : clazz.getDeclaredConstructors()) {
				for (Class<?> paramType : constructor.getParameterTypes()) {
					if (!AVAILABLE_AT_CONFIGURATION_TIME.contains(paramType.getName())) {
						violations.add(clazz.getName() + "(" + paramType.getSimpleName() + ")");
					}
				}
			}
		}

		return violations;
	}

	private static boolean isConfiguration(Class<?> clazz) {
		for (Annotation annotation : clazz.getAnnotations()) {
			if (CONFIGURATION_ANNOTATION.equals(annotation.annotationType().getName())) return true;
		}
		return false;
	}
}
