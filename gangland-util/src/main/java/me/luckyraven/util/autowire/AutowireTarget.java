package me.luckyraven.util.autowire;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies which classes should be scanned for autowiring when instantiating this component. This allows you to
 * specify a list of types that the container should search for.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface AutowireTarget {

	/**
	 * Array of classes that should be searched and injected.
	 */
	Class<?>[] value();
}
