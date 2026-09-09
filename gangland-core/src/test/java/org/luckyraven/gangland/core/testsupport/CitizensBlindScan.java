package org.luckyraven.gangland.core.testsupport;

import org.luckyraven.keystone.util.ReflectionUtil;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Simulates a Citizens-less server for a reflection-level regression test (D2/D-fix-1, smoke row D2 — Paper
 * 1.21.11, Citizens NOT installed): Gangland failed to enable with the civilians module deployed —
 * {@code NoClassDefFoundError: net/citizensnpcs/api/npc/NPC at Class.getDeclaredMethods0 … at
 * BeanFactory.runPostConstruct}. Keystone's bean post-construct pass calls {@code getDeclaredMethods()} on every
 * {@code @Configuration}/bean instance, and its listener registration calls {@code getMethods()} on every
 * {@code @ListenerHandler} instance; both eagerly resolve every declared method's parameter and return types, so ONE
 * method whose signature names a Citizens type sinks the whole plugin when Citizens is absent.
 *
 * <p>{@link #citizensBlindClassLoader()} builds a {@link URLClassLoader} whose own search path is the real test
 * classpath with every {@code citizens-main} jar entry filtered out, parented on
 * {@link ClassLoader#getPlatformClassLoader()} (JDK classes only — no app classpath at all). Because the platform
 * parent can never resolve an app-level class, ordinary parent-first delegation falls through to this loader's own
 * {@code findClass} for everything app-level, making it the <b>defining</b> loader for every scanned class — so a
 * later {@code getDeclaredMethods()} call resolves each method's parameter/return types through a loader that
 * genuinely cannot find {@code net.citizensnpcs.*}, reproducing the exact crash without a real Citizens-less server.
 *
 * <p>{@link #findUnsafeClasses(String)} then reproduces the two real scan sites' worth of reflection
 * ({@code getDeclaredMethods()} for {@code BeanFactory.runPostConstruct}/{@code runInitialize}, {@code getMethods()}
 * for {@code ListenerService.registerGuarded}, {@code getDeclaredConstructors()} for
 * {@code DependencyContainer.createInstance}) against every class under a package that carries one of Keystone's
 * four scan annotations.
 */
public final class CitizensBlindScan {

	private static final String CONFIGURATION_ANNOTATION = "org.luckyraven.keystone.bean.Configuration";
	private static final String BEAN_ANNOTATION          = "org.luckyraven.keystone.bean.Bean";

	private static final List<String> SCANNED_ANNOTATIONS = List.of(
			CONFIGURATION_ANNOTATION,
			"org.luckyraven.keystone.bean.listener.ListenerHandler",
			"org.luckyraven.keystone.bean.command.CommandHandler",
			"org.luckyraven.keystone.persistence.repository.Repository"
	);

	private CitizensBlindScan() {
	}

	/**
	 * Loads every class reachable through Keystone's bean/listener/command/repository scan under {@code basePackage}
	 * — {@code @Configuration} classes themselves, every {@code @Bean} method's <b>return type</b> declared on one
	 * (the actual class whose instance lands in {@code BeanFactory.allRegisteredBeans} and gets reflected on — most
	 * of the time this is a plain class with no annotation of its own, e.g. {@code CivilianNpcFactory}, not the
	 * {@code @Configuration} class that produces it), plus directly {@code @ListenerHandler}/{@code @CommandHandler}/
	 * {@code @Repository} classes — through {@link #citizensBlindClassLoader()} and calls
	 * {@code getDeclaredMethods()}, {@code getMethods()} and {@code getDeclaredConstructors()} on each.
	 *
	 * @return the fully-qualified names of classes that threw {@link NoClassDefFoundError} — empty means the
	 * 		package is safe on a Citizens-less server.
	 */
	public static List<String> findUnsafeClasses(String basePackage) {
		ClassLoader   blind   = citizensBlindClassLoader();
		Set<Class<?>> classes = ReflectionUtil.findClasses(basePackage, blind);

		Set<Class<?>> targets = new LinkedHashSet<>();
		for (Class<?> clazz : classes) {
			collectTargets(clazz, targets);
		}

		List<String> unsafe = new ArrayList<>();
		for (Class<?> clazz : targets) {
			if (!isReflectionSafe(clazz)) unsafe.add(clazz.getName());
		}
		return unsafe;
	}

	private static void collectTargets(Class<?> clazz, Set<Class<?>> targets) {
		for (Annotation annotation : clazz.getAnnotations()) {
			String annotationName = annotation.annotationType().getName();
			if (!SCANNED_ANNOTATIONS.contains(annotationName)) continue;

			if (CONFIGURATION_ANNOTATION.equals(annotationName)) {
				// The @Configuration class itself is reflected on directly (BeanFactory.java:238's
				// configClass.getDeclaredMethods(), then configInstances.add(...) feeds runPostConstruct) —
				// but so is every @Bean method's return type, since THAT instance (not the Configuration
				// instance) is what actually lands in allRegisteredBeans. getDeclaredMethods() on the
				// Configuration class itself is safe to call here: it is the same call BeanFactory already
				// makes today, and none of this repo's @Bean factory methods put a Citizens type in their own
				// signature — only the manager classes they return do.
				targets.add(clazz);
				targets.addAll(beanReturnTypes(clazz));
				return;
			}

			// A @ListenerHandler(condition = "...") class is gated in ListenerService.scanAndRegisterListeners
			// BEFORE dependencyContainer.createInstance()/registerGuarded()'s getMethods() call (confirmed by
			// reading ListenerService.java, D2/D-fix-1) — when the condition fails, the class is never
			// instantiated or reflected upon, so a Citizens type in ITS OWN signature is safe by construction.
			if (isGatedListener(annotation, annotationName)) return;

			targets.add(clazz);
			return;
		}
	}

	private static List<Class<?>> beanReturnTypes(Class<?> configClass) {
		List<Class<?>> types = new ArrayList<>();
		for (Method method : configClass.getDeclaredMethods()) {
			for (Annotation annotation : method.getAnnotations()) {
				if (BEAN_ANNOTATION.equals(annotation.annotationType().getName())) {
					types.add(method.getReturnType());
				}
			}
		}
		return types;
	}

	private static boolean isGatedListener(Annotation annotation, String annotationName) {
		if (!"org.luckyraven.keystone.bean.listener.ListenerHandler".equals(annotationName)) return false;
		try {
			Object condition = annotation.annotationType().getMethod("condition").invoke(annotation);
			return condition instanceof String value && !value.isEmpty();
		} catch (ReflectiveOperationException e) {
			return false;
		}
	}

	private static boolean isReflectionSafe(Class<?> clazz) {
		try {
			clazz.getDeclaredMethods();
			clazz.getMethods();
			clazz.getDeclaredConstructors();
			return true;
		} catch (NoClassDefFoundError error) {
			return false;
		}
	}

	private static URLClassLoader citizensBlindClassLoader() {
		String    classpath = System.getProperty("java.class.path");
		List<URL> urls      = new ArrayList<>();

		for (String entry : classpath.split(File.pathSeparator)) {
			if (entry.toLowerCase(Locale.ROOT).contains("citizens")) continue;
			try {
				urls.add(new File(entry).toURI().toURL());
			} catch (MalformedURLException ignored) {
				// Not a valid path segment — skip it, same as the JVM would for a garbage classpath entry.
			}
		}

		return new URLClassLoader(urls.toArray(new URL[0]), ClassLoader.getPlatformClassLoader());
	}
}
