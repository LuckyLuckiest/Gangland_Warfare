package me.luckyraven.util.listener;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a Listener class to be automatically registered with the ListenerManager. Classes annotated with this must
 * implement {@link org.bukkit.event.Listener}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ListenerHandler {

	/**
	 * Whether this listener should only be registered if certain conditions are met.
	 */
	String condition() default "";

	ListenerPriority priority() default ListenerPriority.NORMAL;

}
