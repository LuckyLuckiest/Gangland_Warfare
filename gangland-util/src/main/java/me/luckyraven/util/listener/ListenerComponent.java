package me.luckyraven.util.listener;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class to be automatically registered as a component in the dependency injection container.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ListenerComponent {

	/**
	 * Optional condition that must be met for this component to be registered.
	 */
	String condition() default "";

	/**
	 * Priority for this component (higher values = higher priority when multiple candidates exist).
	 */
	int priority() default 0;
}