package me.luckyraven.core.bean.conditional;

import me.luckyraven.core.bean.Bean;
import me.luckyraven.core.bean.Configuration;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Conditionally registers a {@link Configuration} class or {@link Bean} method only when every type listed in
 * {@link #value()} is already present in the dependency container at the time the condition is evaluated. Class-level
 * conditions are evaluated before any bean methods on the class run; method-level conditions are evaluated after all
 * class-level conditions have passed.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface ConditionalOnBean {

	Class<?>[] value();
}
