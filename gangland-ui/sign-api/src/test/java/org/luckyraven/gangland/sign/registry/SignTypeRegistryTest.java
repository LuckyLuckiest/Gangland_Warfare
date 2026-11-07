package org.luckyraven.gangland.sign.registry;

import org.bukkit.ChatColor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.sign.SignType;

import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proves {@link SignTypeRegistry#normalize}/{@code #findByLine}/{@code #register} (Test Surface,
 * lootchests-signs-waypoints.md: "SignTypeRegistry#normalize/findByLine/register including the
 * overwrite-on-collision behaviour").
 */
@DisplayName("SignTypeRegistry - registration and lookup")
class SignTypeRegistryTest {

	@Test
	@DisplayName("a registered definition is found by both its typed and generated name")
	void findByLine_resolvesTypedAndGeneratedNames() {
		SignTypeRegistry registry = new SignTypeRegistry();
		SignTypeDefinition buy = definition(new SignType("glw-buy", "buy"));
		registry.register(buy);

		assertSame(buy, registry.findByLine("glw-buy").orElseThrow());
		assertSame(buy, registry.findByLine("buy").orElseThrow());
	}

	@Test
	@DisplayName("lookup normalizes color codes, brackets and case")
	void findByLine_normalizesColorsBracketsAndCase() {
		SignTypeRegistry registry = new SignTypeRegistry();
		SignTypeDefinition buy = definition(new SignType("glw-buy", "buy"));
		registry.register(buy);

		assertTrue(registry.findByLine(ChatColor.GREEN + "[BUY]").isPresent(),
		           "a real translated color code (§a) must be stripped");
		assertTrue(registry.findByLine("[BUY]").isPresent(), "square brackets are stripped");
		assertTrue(registry.findByLine("GLW-BUY").isPresent(), "lookup is case-insensitive");
	}

	@Test
	@DisplayName("T-958: lookup is case-insensitive on a Turkish JVM, where an upper-case I must still lower to i")
	void findByLine_upperCaseI_underTurkishDefaultLocale() {
		Locale previous = Locale.getDefault();

		try {
			Locale.setDefault(new Locale("tr", "TR"));

			SignTypeRegistry registry = new SignTypeRegistry();
			SignTypeDefinition view = definition(new SignType("glw-view", "view"));
			registry.register(view);

			assertSame(view, registry.findByLine("[GLW-VIEW]").orElseThrow(), "typed title with an upper-case I");
			assertSame(view, registry.findByLine("VIEW").orElseThrow(), "generated title with an upper-case I");
		} finally {
			Locale.setDefault(previous);
		}
	}

	@Test
	@DisplayName("an unregistered line resolves to empty")
	void findByLine_unknown_returnsEmpty() {
		SignTypeRegistry registry = new SignTypeRegistry();

		assertTrue(registry.findByLine("glw-nonexistent").isEmpty());
	}

	@Test
	@DisplayName("registering a second definition under the same typed/generated key silently overwrites the first")
	void register_overwritesOnKeyCollision() {
		SignTypeRegistry registry = new SignTypeRegistry();
		SignTypeDefinition first = definition(new SignType("glw-buy", "buy"));
		SignTypeDefinition second = definition(new SignType("glw-buy", "buy"));

		registry.register(first);
		registry.register(second);

		assertSame(second, registry.findByLine("glw-buy").orElseThrow(),
		           "register has no collision guard - the second registration replaces the first");
	}

	@Test
	@DisplayName("isRegistered checks the typed key only, not the generated one")
	void isRegistered_checksTypedKeyOnly() {
		SignTypeRegistry registry = new SignTypeRegistry();
		registry.register(definition(new SignType("glw-buy", "buy")));

		assertTrue(registry.isRegistered("glw-buy"));
		assertFalse(registry.isRegistered("buy"), "isRegistered only consults definitionsByTyped");
	}

	@Test
	@DisplayName("getDefinition resolves by the SignType's typed name")
	void getDefinition_resolvesByTypedName() {
		SignTypeRegistry registry = new SignTypeRegistry();
		SignType type = new SignType("glw-sell", "sell");
		SignTypeDefinition sell = definition(type);
		registry.register(sell);

		Optional<SignTypeDefinition> found = registry.getDefinition(type);

		assertTrue(found.isPresent());
		assertSame(sell, found.get());
	}

	@Test
	@DisplayName("getDefinitions returns a defensive copy - mutating it does not affect the registry")
	void getDefinitions_returnsDefensiveCopy() {
		SignTypeRegistry registry = new SignTypeRegistry();
		registry.register(definition(new SignType("glw-buy", "buy")));

		registry.getDefinitions().clear();

		assertTrue(registry.findByLine("glw-buy").isPresent(), "clearing the returned map must not affect the registry");
	}

	private static SignTypeDefinition definition(SignType type) {
		return SignTypeDefinition.builder().signType(type).build();
	}

}
