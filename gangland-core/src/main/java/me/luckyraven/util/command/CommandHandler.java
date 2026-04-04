package me.luckyraven.util.command;

public @interface CommandHandler {

	/**
	 * Optional condition that must be met for this component to be registered.
	 */
	String condition() default "";

	/**
	 * Priority for this component (higher values = higher priority when multiple candidates exist).
	 */
	int priority() default 0;
}
