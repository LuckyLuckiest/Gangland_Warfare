package me.luckyraven.util.autowire.bean;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a bean factory. Classes annotated with {@code @Configuration} are discovered by
 * {@link BeanFactory#scan(String)}, instantiated via constructor injection from the shared
 * {@link me.luckyraven.util.autowire.DependencyContainer}, and then every {@link Bean}-annotated method on the instance
 * is invoked to produce a bean registered into the container.
 * <p>
 * Similar to Spring's {@code @Configuration} but without CGLIB proxying — see {@link Bean} for the implications on
 * intra-config method calls.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Configuration {

	/**
	 * Optional human-readable name for diagnostics. Defaults to the simple class name.
	 */
	String name() default "";
}
