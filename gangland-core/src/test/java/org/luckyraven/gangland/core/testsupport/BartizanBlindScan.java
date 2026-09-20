package org.luckyraven.gangland.core.testsupport;

import org.luckyraven.keystone.util.ReflectionUtil;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Simulates a Bartizan-less server for a reflection-level regression test (WS7 G5, sibling of
 * {@link CitizensBlindScan} — same reasoning, different soft dependency): {@code gangland-gadget}'s
 * {@code module.yml} drops its hard, fail-fast {@code Plugins: [Bartizan]} dependency, so a class whose method
 * signature still names a Bartizan type would sink the whole module the same way a Citizens type once sank the
 * civilians module — {@code NoClassDefFoundError} out of Keystone's bean/listener reflection scan.
 *
 * <p>Lives in {@code gangland-core}'s test-jar (not gadget-local) because a sibling gate (G5b) needs the identical
 * tool for {@code gangland-civilians}; both modules already declare {@code gangland-core} as a {@code test-jar}
 * dependency for {@link CitizensBlindScan}, so no new test dependency is needed in either module.
 *
 * <p>{@link #bartizanBlindClassLoader()} builds a {@link URLClassLoader} whose own search path is the real test
 * classpath with every class named {@code org.luckyraven.bartizan.*} refused by name (catches {@code
 * bartizan-api} — a {@code provided} compile dependency — since those are exactly the types missing on a
 * Bartizan-less server's runtime classpath), parented on {@link ClassLoader#getPlatformClassLoader()} (JDK classes
 * only). {@link #findUnsafeClasses(String)} then reproduces the same three reflection call sites
 * {@link CitizensBlindScan#findUnsafeClasses(String)} does, against every class under a package that carries one
 * of Keystone's four scan annotations.
 */
public final class BartizanBlindScan {

	private static final String CONFIGURATION_ANNOTATION = "org.luckyraven.keystone.bean.Configuration";
	private static final String BEAN_ANNOTATION          = "org.luckyraven.keystone.bean.Bean";

	private static final List<String> SCANNED_ANNOTATIONS = List.of(
			CONFIGURATION_ANNOTATION,
			"org.luckyraven.keystone.bean.listener.ListenerHandler",
			"org.luckyraven.keystone.bean.command.CommandHandler",
			"org.luckyraven.keystone.persistence.repository.Repository"
	);

	private BartizanBlindScan() {
	}

	/**
	 * Loads every class reachable through Keystone's bean/listener/command/repository scan under {@code basePackage}
	 * — {@code @Configuration} classes themselves, every {@code @Bean} method's <b>return type</b> declared on one,
	 * plus directly {@code @ListenerHandler}/{@code @CommandHandler}/{@code @Repository} classes — through
	 * {@link #bartizanBlindClassLoader()} and calls {@code getDeclaredMethods()}, {@code getMethods()} and
	 * {@code getDeclaredConstructors()} on each.
	 *
	 * @return the fully-qualified names of classes that threw {@link NoClassDefFoundError} — empty means the
	 * 		package is safe on a Bartizan-less server.
	 */
	public static List<String> findUnsafeClasses(String basePackage) {
		URLClassLoader blind = bartizanBlindClassLoader();

		// Self-check #1: the loader must genuinely be unable to resolve Bartizan types, or the scan below passes
		// vacuously.
		assertThrows(ClassNotFoundException.class,
		             () -> Class.forName("org.luckyraven.bartizan.api.BartizanApi", false, blind),
		             "the blind classloader must not be able to resolve Bartizan types");

		Set<Class<?>> classes = ReflectionUtil.findClasses(basePackage, blind);

		Set<Class<?>> targets = new LinkedHashSet<>();
		for (Class<?> clazz : classes) {
			collectTargets(clazz, classes, targets);
		}

		// Self-check #2: an empty target set can never legitimately mean "safe" — it means the scan found nothing
		// under basePackage, which would make every caller's assertTrue(unsafe.isEmpty()) pass vacuously.
		assertFalse(targets.isEmpty(), "the scan found no scanned classes under " + basePackage);

		List<String> unsafe = new ArrayList<>();
		for (Class<?> clazz : targets) {
			if (!isReflectionSafe(clazz)) unsafe.add(clazz.getName());
		}

		try {
			blind.close();
		} catch (IOException ignored) {
			// Best-effort close of the scan's own classloader — a failure to release JAR handles here doesn't
			// invalidate the reflection results already computed above.
		}
		return unsafe;
	}

	private static void collectTargets(Class<?> clazz, Set<Class<?>> classes, Set<Class<?>> targets) {
		for (Annotation annotation : clazz.getAnnotations()) {
			String annotationName = annotation.annotationType().getName();
			if (!SCANNED_ANNOTATIONS.contains(annotationName)) continue;

			if (CONFIGURATION_ANNOTATION.equals(annotationName)) {
				// The @Configuration class itself is reflected on directly (BeanFactory.java:238's
				// configClass.getDeclaredMethods(), then configInstances.add(...) feeds runPostConstruct) —
				// but so is every @Bean method's return type, since THAT instance (not the Configuration
				// instance) is what actually lands in allRegisteredBeans.
				targets.add(clazz);
				targets.addAll(beanReturnTypes(clazz, classes));
				return;
			}

			// A @ListenerHandler(condition = "...") class is gated in ListenerService.scanAndRegisterListeners
			// BEFORE dependencyContainer.createInstance()/registerGuarded()'s getMethods() call — when the
			// condition fails, the class is never instantiated or reflected upon, so a Bartizan type in ITS OWN
			// signature is safe by construction.
			if (isGatedListener(annotation, annotationName)) return;

			targets.add(clazz);
			return;
		}
	}

	/**
	 * A {@code @Bean} method's DECLARED return type is not necessarily what Keystone reflects on at runtime —
	 * {@code BeanFactory} registers and later reflects on the bean's actual runtime CLASS, so when the declared
	 * return type is an interface or abstract class, every scanned class assignable to it is a target too.
	 */
	private static List<Class<?>> beanReturnTypes(Class<?> configClass, Set<Class<?>> classes) {
		List<Class<?>> types = new ArrayList<>();

		Method[] declaredMethods;
		try {
			declaredMethods = configClass.getDeclaredMethods();
		} catch (NoClassDefFoundError error) {
			// The @Configuration class itself is poisoned (its own signature names a type this blind loader can't
			// resolve). isReflectionSafe() below independently re-attempts this same call for every target
			// (configClass is already in targets via collectTargets) and correctly reports it as unsafe there —
			// this guard only stops that failure from surfacing here as an uncaught test error instead of a named
			// finding.
			return types;
		}

		for (Method method : declaredMethods) {
			for (Annotation annotation : method.getAnnotations()) {
				if (!BEAN_ANNOTATION.equals(annotation.annotationType().getName())) continue;

				Class<?> returnType = method.getReturnType();
				types.add(returnType);

				if (returnType.isInterface() || Modifier.isAbstract(returnType.getModifiers())) {
					for (Class<?> candidate : classes) {
						if (candidate != returnType && returnType.isAssignableFrom(candidate)) {
							types.add(candidate);
						}
					}
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

	/**
	 * Public (unlike {@link CitizensBlindScan}'s private equivalent) — a test that wants to force-load one
	 * specific class through the blind loader beyond what the standard annotation scan reaches (e.g. a plain
	 * static helper with no Keystone scan annotation at all) calls this directly.
	 */
	public static URLClassLoader bartizanBlindClassLoader() {
		// Filtering by classpath ENTRY (path/jar name) doesn't work under Surefire's default manifest-only booter
		// jar: java.class.path is then a single jar (whose manifest Class-Path attribute transitively pulls in the
		// real classpath, bartizan-api included), so a path-based "skip anything containing bartizan" filter has
		// nothing to remove. Refuse by class NAME instead — deterministic regardless of how the classpath is shaped.
		List<URL> urls = new ArrayList<>();
		for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
			try {
				urls.add(new File(entry).toURI().toURL());
			} catch (MalformedURLException ignored) {
				// Not a valid path segment — skip it, same as the JVM would for a garbage classpath entry.
			}
		}

		return new URLClassLoader(urls.toArray(new URL[0]), ClassLoader.getPlatformClassLoader()) {
			@Override
			protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
				if (name.startsWith("org.luckyraven.bartizan.")) throw new ClassNotFoundException(name);
				return super.loadClass(name, resolve);
			}
		};
	}
}
