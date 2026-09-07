package org.luckyraven.gangland.core.testsupport;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Server;
import org.bukkit.block.BlockType;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Installs a minimal {@link Server} so that Bukkit API which resolves through {@link Registry} works in a plain
 * unit test — most importantly {@link org.bukkit.Material#isAir()} and XSeries lookups such as
 * {@code XAttribute.ARMOR.get()}.
 *
 * <p><b>Why this is needed.</b> On the 1.21 API {@code Material.isAir()} is no longer a plain switch — it resolves
 * {@code asBlockType()}, which reads the static {@code Registry.BLOCK}. {@code Registry}'s static initialiser calls
 * {@code Bukkit.getRegistry(...)} thirty times and wraps every result in {@code Objects.requireNonNull}, so with a
 * null {@code Bukkit.server} the class fails to initialise. Worse, once it has failed, every later touch in the
 * same surefire fork throws {@code NoClassDefFoundError}, so one unguarded call turns into a cascade of failures
 * in unrelated test classes.
 *
 * <p><b>Two ordering traps, both deliberate here.</b>
 * <ol>
 *   <li>Mockito's inline mock maker calls {@code Class.forName(name, true, loader)} on the type it mocks, and
 *       {@link Proxy#newProxyInstance} likewise initialises the interfaces it implements. Either one applied to
 *       {@code Registry} <i>before</i> the server exists re-enters the failing initialiser. So every registry
 *       object here is built <b>lazily, inside the {@code getRegistry} answer</b> — by which point
 *       {@code Registry.<clinit>} is already running on this thread, and the JVM permits recursive initialisation
 *       from the same thread.</li>
 *   <li>{@code Registry} latches its thirty static fields permanently on first touch, so this must run before
 *       anything else in the fork reaches them. Call it from {@code @BeforeAll}.</li>
 * </ol>
 *
 * <p>This is deliberately different from {@code keystone-testkit}'s {@code BukkitStatics}, which mocks the
 * {@code Bukkit} class statically for the duration of a try-with-resources. Here the <i>real</i> static field must
 * be populated, precisely because {@code Registry} latches. The two compose fine — a {@code BukkitStatics} block
 * still intercepts everything while it is open.
 *
 * <p><b>Upstream candidate:</b> this belongs in {@code keystone-testkit} beside {@code BukkitStatics}; it lives
 * here only to avoid a Keystone version bump mid-initiative.
 */
public final class BukkitRegistryFixture {

	/** The vanilla block keys that report {@code isAir()}. */
	private static final Set<String> AIR_KEYS = Set.of("air", "cave_air", "void_air");

	private BukkitRegistryFixture() {
	}

	/**
	 * Install a mock server backed by proxy registries, unless one is already set. Idempotent and safe to call
	 * from any number of {@code @BeforeAll} hooks — the first caller in the fork wins.
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
		when(server.getBukkitVersion()).thenReturn("1.21.11-R0.1-SNAPSHOT");

		// Lazy on purpose — see trap (1) in the class javadoc.
		when(server.getRegistry(any())).thenAnswer(invocation -> registryProxy(invocation.getArgument(0)));

		// ItemStack.clone()/equals()/isSimilar() route metadata comparison through the item factory; without one
		// they NPE. A meta-less factory is enough for value comparison of plain stacks.
		when(server.getItemFactory()).thenAnswer(invocation -> itemFactory());

		Bukkit.setServer(server);
	}

	/**
	 * A registry whose {@code get} yields a proxy of the registry's own element type, so callers that merely need
	 * a non-null handle (XSeries attribute/enchantment lookups, for instance) get one. {@link BlockType} entries
	 * additionally answer {@code isAir()} correctly by namespaced key.
	 */
	private static Object registryProxy(Class<?> elementType) {
		InvocationHandler handler = (proxy, method, args) -> {
			switch (method.getName()) {
				case "get":
				case "getOrThrow":
					if (args == null || args.length == 0 || !(args[0] instanceof NamespacedKey key)) {
						return null;
					}
					return element(elementType, AIR_KEYS.contains(key.getKey()));
				case "stream":
					return Stream.empty();
				case "iterator":
					return Collections.emptyIterator();
				case "spliterator":
					return Collections.emptyList().spliterator();
				case "hashCode":
					return System.identityHashCode(proxy);
				case "equals":
					return proxy == args[0];
				case "toString":
					return "ProxyRegistry<" + elementType.getSimpleName() + ">";
				default:
					return defaultValue(method.getReturnType());
			}
		};

		return Proxy.newProxyInstance(BukkitRegistryFixture.class.getClassLoader(),
		                              new Class<?>[]{Registry.class}, handler);
	}

	/** A proxy standing in for one registry entry. Only {@code isAir()} carries real meaning. */
	private static Object element(Class<?> elementType, boolean air) {
		if (!elementType.isInterface()) {
			return null;
		}

		InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
			case "isAir" -> air;
			case "hashCode" -> System.identityHashCode(proxy);
			case "equals" -> proxy == args[0];
			case "toString" -> "Proxy" + elementType.getSimpleName();
			default -> defaultValue(method.getReturnType());
		};

		return Proxy.newProxyInstance(BukkitRegistryFixture.class.getClassLoader(),
		                              new Class<?>[]{elementType}, handler);
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
