package me.luckyraven.util.command;

/**
 * Marks a command class to be automatically registered with the ListenerManager. Classes annotated with this must
 * extend.Listener.
 */
public @interface CommandHandler {

	String condition() default "";

}
