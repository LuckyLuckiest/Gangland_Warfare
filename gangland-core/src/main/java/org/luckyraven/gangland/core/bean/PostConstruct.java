package org.luckyraven.gangland.core.bean;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a no-argument method to invoke after every bean has been registered in the container. {@code @PostConstruct}
 * methods may be declared on a {@link Configuration} class itself or on any type produced by a {@link Bean} method. The
 * {@link BeanFactory} runs them in the same topological order as bean instantiation, so post-construct hooks may freely
 * call manager setters or initialisation routines that depend on other beans being fully wired.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface PostConstruct {
}
