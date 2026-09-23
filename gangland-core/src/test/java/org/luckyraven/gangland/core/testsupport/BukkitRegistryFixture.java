package org.luckyraven.gangland.core.testsupport;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.inventory.ItemFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.logging.Logger;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Installs a minimal {@link Server} so that Bukkit API which resolves through {@code Bukkit.getServer()} works in a
 * plain unit test — chiefly {@code ItemStack.getItemMeta()}/{@code hasItemMeta()}/{@code isSimilar()}, which route
 * through {@link ItemFactory} and NPE without one.
 *
 * <p>The name is historical: on the 1.21 API this fixture also stood up proxy {@code Registry} objects, because
 * {@code Material.isAir()} resolved through {@code Registry.BLOCK} and the {@code Registry} static initialiser
 * required a live server. The compile floor is Spigot 1.16.5 (see the root pom's {@code bukkit.version}), whose
 * {@code Registry} builds every static field from enums and whose {@code Server} has no {@code getRegistry(Class)}
 * at all — so that half is gone. The class name stays so the {@code @BeforeAll} call sites do not churn.
 *
 * <p>This is deliberately different from {@code keystone-testkit}'s {@code BukkitStatics}, which mocks the
 * {@code Bukkit} class statically for the duration of a try-with-resources. Here the <i>real</i> static field is
 * populated once per surefire fork. The two compose fine — a {@code BukkitStatics} block still intercepts everything
 * while it is open.
 */
public final class BukkitRegistryFixture {

	private BukkitRegistryFixture() {
	}

	/**
	 * Install a mock server carrying a metadata-free item factory, unless one is already set. Idempotent and safe
	 * to call from any number of {@code @BeforeAll} hooks — the first caller in the fork wins.
	 */
	public static void install() {
		if (Bukkit.getServer() != null) {
			return;
		}

		Server server = mock(Server.class);

		// Bukkit.setServer logs a banner through these before returning.
		when(server.getLogger()).thenReturn(Logger.getLogger("gangland-test"));
		when(server.getName()).thenReturn("TestServer");
		when(server.getVersion()).thenReturn("test");
		when(server.getBukkitVersion()).thenReturn("1.16.5-R0.1-SNAPSHOT");

		// ItemStack.clone()/equals()/isSimilar() route metadata comparison through the item factory; without one
		// they NPE. A meta-less factory is enough for value comparison of plain stacks.
		when(server.getItemFactory()).thenAnswer(invocation -> itemFactory());

		Bukkit.setServer(server);
	}

	/**
	 * A metadata-free {@link ItemFactory}. {@code getItemMeta} yields {@code null}, so two plain stacks of the
	 * same type and amount compare equal — which is all {@code ItemStack.equals}/{@code isSimilar} need in a unit
	 * test. Stacks carrying real meta are out of scope here; those belong in a server-backed test.
	 */
	private static Object itemFactory() {
		InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
			case "getItemMeta" -> null;
			case "equals" -> args != null && args.length == 2 ? args[0] == args[1] : proxy == (args == null ? null : args[0]);
			case "isApplicable" -> true;
			case "asMetaFor" -> args == null || args.length == 0 ? null : args[0];
			case "updateMaterial" -> args == null || args.length < 2 ? null : args[1];
			case "hashCode" -> System.identityHashCode(proxy);
			case "toString" -> "ProxyItemFactory";
			default -> defaultValue(method.getReturnType());
		};

		return Proxy.newProxyInstance(BukkitRegistryFixture.class.getClassLoader(),
		                              new Class<?>[]{ItemFactory.class}, handler);
	}

	/** Proxies must return a non-null value for primitive return types. */
	private static Object defaultValue(Class<?> returnType) {
		if (!returnType.isPrimitive()) return null;
		if (returnType == boolean.class) return false;
		if (returnType == void.class) return null;
		if (returnType == char.class) return (char) 0;
		if (returnType == long.class) return 0L;
		if (returnType == float.class) return 0f;
		if (returnType == double.class) return 0d;
		return 0;
	}
}
