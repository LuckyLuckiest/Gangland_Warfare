package me.luckyraven.database.component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface AttributeLink {

	boolean primaryKey() default false;

	boolean unique() default false;

	boolean nullable() default true;

	String defaultValue() default "";

}
