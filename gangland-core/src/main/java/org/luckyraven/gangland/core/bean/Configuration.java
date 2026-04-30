package org.luckyraven.gangland.core.bean;

import org.luckyraven.gangland.core.bean.autowire.DependencyContainer;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a bean factory. Classes annotated with {@code @Configuration} are discovered by
 * {@link BeanFactory#scan(String)}, instantiated via constructor injection from the shared {@link DependencyContainer},
 * and then every {@link Bean}-annotated method on the instance is invoked to produce a bean registered into the
 * container.
 * <p>
 * Similar to Spring's {@code @Configuration} but without CGLIB proxying — see {@link Bean} for the implications on
 * intra-config method calls.
 * <p>
 * Each configuration class belongs to exactly one {@link Phase}. Every {@code @Bean} method defined in the class
 * inherits that phase — there is no per-bean phase override. To produce beans for a different phase, write a separate
 * configuration class. This forces a clean "one phase per file" layout (e.g. {@code FileConfig} only contains FILE
 * beans, {@code DatabaseConfig} only contains DATABASE beans).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Configuration {

	/**
	 * Optional human-readable name for diagnostics. Defaults to the simple class name.
	 */
	String name() default "";

	/**
	 * Bootstrap phase that every {@code @Bean} method on this configuration class belongs to. Defaults to
	 * {@link Phase#CONFIG} so existing single-phase configurations need no annotation change.
	 */
	Phase phase() default Phase.CONFIG;
}
