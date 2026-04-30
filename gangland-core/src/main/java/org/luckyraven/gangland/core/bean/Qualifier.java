package org.luckyraven.gangland.core.bean;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Disambiguates a bean dependency by name when multiple candidates of the same raw type exist in the container. Apply
 * to a {@link Bean} method parameter to request a specific named bean.
 *
 * <pre>
 * &#64;Bean
 * public GangManager gangManager(&#64;Qualifier("player") UserManager&lt;Player&gt; users) {
 *     return new GangManager(users, gangland);
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER, ElementType.METHOD})
public @interface Qualifier {

	String value();
}
