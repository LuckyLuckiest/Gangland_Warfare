package org.luckyraven.gangland.core.bean;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method on a {@link Configuration}-annotated class as a bean factory method. The method's parameters are
 * resolved from the dependency container (by type, optionally narrowed by {@link Qualifier}), the method is invoked
 * exactly once during {@link BeanFactory#instantiate()}, and the return value is registered as a singleton in the
 * container.
 * <p>
 * <b>Important:</b> bean methods MUST NOT call other {@code @Bean} methods on the same
 * configuration class via {@code this.foo()} — that would construct a second instance, since this framework does not
 * use CGLIB proxies. Instead, declare the dependency as a parameter and let the framework inject the cached singleton.
 *
 * <pre>
 * // WRONG — creates a second UserManager
 * &#64;Bean
 * public GangManager gangManager() {
 *     return new GangManager(this.userManager(), gangland);
 * }
 *
 * // CORRECT — framework injects the cached singleton
 * &#64;Bean
 * public GangManager gangManager(UserManager userManager) {
 *     return new GangManager(userManager, gangland);
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Bean {

	/**
	 * Optional bean name for {@link Qualifier} lookup. Defaults to the method name.
	 */
	String name() default "";

	/**
	 * When {@code true}, the bean is also published via Bukkit's {@code ServicesManager} so that cross-plugin or
	 * cross-module consumers can resolve it without going through the dependency container.
	 */
	boolean publishToServicesManager() default false;

	/**
	 * Marks this bean as a parameterized type whose generic arguments are erased at runtime. Mirrors
	 * {@link org.luckyraven.gangland.persistence.repository.Repository#isGeneric()}. When {@code true}, consumers must
	 * inject the bean via {@link Qualifier} rather than relying on raw-class lookup.
	 */
	boolean isGeneric() default false;
}
