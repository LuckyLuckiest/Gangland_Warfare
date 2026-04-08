package me.luckyraven.util.autowire.bean.conditional;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Conditionally registers a {@link me.luckyraven.util.autowire.bean.Configuration} class or
 * {@link me.luckyraven.util.autowire.bean.Bean} method only when a settings flag resolves to {@code true} via the
 * {@link me.luckyraven.util.autowire.bean.SettingsLookup} contract. The {@link #value()} is a dotted-path key (e.g.
 * {@code "gang.enabled"}).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface ConditionalOnSetting {

	String value();
}
