package me.luckyraven.util.autowire;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a constructor parameter to be automatically injected from the dependency injection container. Similar to
 * Spring's @Autowired annotation.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.CONSTRUCTOR, ElementType.PARAMETER})
public @interface Autowired {

	/**
	 * Whether this dependency is required. If false, null will be injected if no instance is found.
	 */
	boolean required() default true;
}