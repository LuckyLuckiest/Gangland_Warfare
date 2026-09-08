package org.luckyraven.gangland.item.listener;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Module boundary guard (T-14, 2026-09-08). {@code gangland-item} is compiled straight into the core jar, so any
 * listener under {@code org.luckyraven.gangland.item.listener} must be constructible with zero runtime modules
 * present. {@code WearableEquipListener} used to live here with a constructor parameter of type
 * {@code WearableEquipService}, a bean only {@code WeaponModuleConfig} (in the weapon runtime module) registers -
 * on a weapon-less server this failed with "Failed to instantiate listener ... Cannot resolve required parameter
 * of type WearableEquipService". Fixed by relocating {@code WearableEquipListener} to
 * {@code org.luckyraven.gangland.weapon.listener.wearable} (see {@code WeaponModuleTest}); this test walks every
 * class actually compiled under {@code item.listener} and fails if any constructor still asks for
 * {@code WearableEquipService}.
 */
@DisplayName("item.listener module boundary")
class ItemListenerModuleBoundaryTest {

	private static final String PACKAGE          = "org.luckyraven.gangland.item.listener";
	private static final String WEARABLE_SERVICE = "org.luckyraven.gangland.item.contract.WearableEquipService";

	@Test
	@DisplayName("no class under item.listener declares a constructor parameter of type WearableEquipService")
	void noListenerDependsOnWearableEquipService() throws Exception {
		List<Class<?>> classes = classesUnder(PACKAGE);
		assertFalse(classes.isEmpty(), "expected to find compiled classes under " + PACKAGE);

		for (Class<?> clazz : classes) {
			for (Constructor<?> constructor : clazz.getDeclaredConstructors()) {
				for (Class<?> paramType : constructor.getParameterTypes()) {
					assertTrue(!paramType.getName().equals(WEARABLE_SERVICE),
							clazz.getName() + " declares a constructor parameter of type " + WEARABLE_SERVICE +
							" - that bean is only provided by the weapon runtime module, so this listener cannot " +
							"live in the core-compiled item module (T-14). Move it under " +
							"org.luckyraven.gangland.weapon.listener instead.");
				}
			}
		}
	}

	private static List<Class<?>> classesUnder(String packageName) throws Exception {
		List<Class<?>> found = new ArrayList<>();
		String path = packageName.replace('.', '/');
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		for (URL url : Collections.list(loader.getResources(path))) {
			File root = new File(url.toURI());
			if (root.isDirectory()) {
				collect(root, packageName, found);
			}
		}
		return found;
	}

	private static void collect(File dir, String packageName, List<Class<?>> out) throws Exception {
		File[] files = dir.listFiles();
		if (files == null) return;

		for (File file : files) {
			if (file.isDirectory()) {
				collect(file, packageName + "." + file.getName(), out);
			} else if (file.getName().endsWith(".class") && !file.getName().contains("$")) {
				String simpleName = file.getName().substring(0, file.getName().length() - ".class".length());
				out.add(Class.forName(packageName + "." + simpleName));
			}
		}
	}
}
