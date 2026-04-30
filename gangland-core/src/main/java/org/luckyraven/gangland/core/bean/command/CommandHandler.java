package org.luckyraven.gangland.core.bean.command;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface CommandHandler {

	/**
	 * Optional condition that must be met for this component to be registered.
	 */
	String condition() default "";

	/**
	 * Priority controlling registration order. Higher priority commands are registered first.
	 */
	CommandPriority priority() default CommandPriority.NORMAL;
}
