package org.luckyraven.gangland.copsncrooks.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.exceptions.base.MockitoException;
import org.luckyraven.gangland.copsncrooks.integration.config.GanglandCopSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.config.YamlCopConfigProvider;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;

import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

/**
 * Plain accessors of the three config classes: every public no-argument {@code getX()} / {@code isX()} that has a
 * same-named instance field of the same type returns that field's value, whatever the field holds. The instance is
 * built without its constructor (a mock that calls real methods), the field is set to a distinct sentinel and the
 * accessor is read back. Accessors that transform their field are named in {@code skip}.
 */
@DisplayName("config classes: plain accessors return their field")
class PlainAccessorsTest {

	/** Reads every plain accessor of {@code type} back from a sentinel written to its field; returns how many were checked. */
	private static int checkPlainAccessors(Class<?> type, Set<String> skip) throws ReflectiveOperationException {
		Object       instance = mock(type, CALLS_REAL_METHODS);
		List<String> failures = new ArrayList<>();
		int          checked  = 0;
		int          index    = 0;

		for (Field field : type.getDeclaredFields()) {
			if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;

			String name  = field.getName();
			String cap   = Character.toUpperCase(name.charAt(0)) + name.substring(1);
			String getter = (field.getType() == boolean.class ? "is" : "get") + cap;
			if (skip.contains(getter)) continue;

			Method method;
			try {
				method = type.getMethod(getter);
			} catch (NoSuchMethodException e) {
				continue;
			}
			if (method.getReturnType() != field.getType()) continue;

			Object sentinel = sentinel(field.getType(), ++index);
			if (sentinel == null) continue; // a JDK collection: its accessor wraps it, so it is not a plain accessor

			field.setAccessible(true);
			field.set(instance, sentinel);
			Object read = method.invoke(instance);
			if (sentinel != read && !sentinel.equals(read)) {
				failures.add(getter + ": wrote " + sentinel + " read " + read);
			}
			checked++;
		}

		assertEquals(List.of(), failures);
		return checked;
	}

	private static Object sentinel(Class<?> type, int index) {
		if (type == int.class) return 1000 + index;
		if (type == long.class) return 100_000L + index;
		if (type == double.class) return 0.5 + index;
		if (type == boolean.class) return Boolean.TRUE;
		if (type == String.class) return "sentinel-" + index;
		if (type.isPrimitive() || type.getName().startsWith("java.")) return null;
		try {
			return mock(type);
		} catch (MockitoException e) {
			return allocate(type); // a record or final class: an instance that never ran its constructor
		}
	}

	private static Object allocate(Class<?> type) {
		try {
			Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
			theUnsafe.setAccessible(true);
			return ((Unsafe) theUnsafe.get(null)).allocateInstance(type);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	@Test
	@DisplayName("YamlCopConfigProvider")
	void yamlCopConfigProvider() throws ReflectiveOperationException {
		int checked = checkPlainAccessors(YamlCopConfigProvider.class, Set.of());

		assertEquals(64, checked); // 62 one-line getters + the two 0.21.0 ones that carry a trailing comment
	}

	@Test
	@DisplayName("GanglandCopSettings")
	void ganglandCopSettings() throws ReflectiveOperationException {
		int checked = checkPlainAccessors(GanglandCopSettings.class, Set.of());

		assertEquals(37, checked);
	}

	@Test
	@DisplayName("ChaseConfigLoader")
	void chaseConfigLoader() throws ReflectiveOperationException {
		int checked = checkPlainAccessors(ChaseConfigLoader.class, Set.of());

		assertEquals(9, checked);
		assertTrue(checked > 0);
	}
}
