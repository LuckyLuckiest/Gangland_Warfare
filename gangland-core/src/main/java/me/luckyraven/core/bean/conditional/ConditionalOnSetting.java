package me.luckyraven.core.bean.conditional;

import me.luckyraven.core.bean.Bean;
import me.luckyraven.core.bean.Configuration;
import me.luckyraven.core.bean.SettingsLookup;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Conditionally registers a {@link Configuration} class or {@link Bean} method only when a settings flag resolves to
 * {@code true} via the {@link SettingsLookup} contract. The {@link #value()} is a dotted-path key (e.g.
 * {@code "gang.enabled"}).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface ConditionalOnSetting {

	String value();
}
