package org.luckyraven.gangland.persistence.repository;

import java.lang.annotation.*;

/**
 * Marks a class as a repository that should be auto-registered. Repositories handle data persistence for domain
 * entities.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Repository {
	/**
	 * The entity class this repository manages
	 */
	Class<?> value();

	/**
	 * Optional custom name for the repository
	 */
	String name() default "";

	/**
	 * Condition method to check before registration (e.g., "SettingAddon.isGangEnabled()") Empty string means always
	 * register
	 */
	String condition() default "";

	/**
	 * Whether this repository handles a generic type (e.g., User<? extends OfflinePlayer>) When true, the registry will
	 * handle generic type matching more flexibly
	 */
	boolean isGeneric() default false;
}
