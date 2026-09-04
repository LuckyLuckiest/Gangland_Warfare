package org.luckyraven.gangland.sign.registry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.sign.model.SignFormat;
import org.luckyraven.gangland.sign.validation.SignValidationException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proves {@link SignFormatRegistry} registration/lookup by name and by prefix (Test Surface,
 * lootchests-signs-waypoints.md groups this with the sign type registry as a pure-logic
 * candidate).
 */
@DisplayName("SignFormatRegistry - format registration and lookup")
class SignFormatRegistryTest {

	@Test
	@DisplayName("a registered format is found by name, case-insensitively")
	void getFormat_caseInsensitiveLookup() throws SignValidationException {
		SignFormatRegistry registry = new SignFormatRegistry();
		SignFormat format = format("Buy", "glw-buy");
		registry.register(format);

		assertTrue(registry.hasFormat("buy"));
		assertSame(format, registry.getFormat("BUY").orElseThrow());
	}

	@Test
	@DisplayName("registering a null format, or one with a null name, throws")
	void register_nullFormatOrNullName_throws() {
		SignFormatRegistry registry = new SignFormatRegistry();

		assertThrows(SignValidationException.class, () -> registry.register(null));
		assertThrows(SignValidationException.class,
		             () -> registry.register(SignFormat.builder().signTypePrefix("glw-buy").build()));
	}

	@Test
	@DisplayName("getFormatByPrefix matches the sign type prefix case-insensitively")
	void getFormatByPrefix_matchesCaseInsensitively() throws SignValidationException {
		SignFormatRegistry registry = new SignFormatRegistry();
		SignFormat format = format("buy", "glw-buy");
		registry.register(format);

		assertSame(format, registry.getFormatByPrefix("GLW-BUY").orElseThrow());
	}

	@Test
	@DisplayName("getFormatByPrefix and getFormat return empty for unknown input, including null")
	void getFormat_unknownOrNull_returnsEmpty() {
		SignFormatRegistry registry = new SignFormatRegistry();

		assertTrue(registry.getFormat("nonexistent").isEmpty());
		assertTrue(registry.getFormat(null).isEmpty());
		assertTrue(registry.getFormatByPrefix("nonexistent").isEmpty());
		assertTrue(registry.getFormatByPrefix(null).isEmpty());
	}

	@Test
	@DisplayName("unregister removes a format, clear removes all")
	void unregisterAndClear_removeFormats() throws SignValidationException {
		SignFormatRegistry registry = new SignFormatRegistry();
		registry.register(format("buy", "glw-buy"));
		registry.register(format("sell", "glw-sell"));

		registry.unregister("buy");
		assertFalse(registry.hasFormat("buy"));
		assertTrue(registry.hasFormat("sell"));

		registry.clear();
		assertFalse(registry.hasFormat("sell"));
	}

	@Test
	@DisplayName("registering under the same name twice overwrites, matching SignTypeRegistry's collision behaviour")
	void register_overwritesOnNameCollision() throws SignValidationException {
		SignFormatRegistry registry = new SignFormatRegistry();
		SignFormat first = format("buy", "glw-buy");
		SignFormat second = format("buy", "glw-buy-v2");
		registry.register(first);
		registry.register(second);

		assertSame(second, registry.getFormat("buy").orElseThrow());
	}

	private static SignFormat format(String name, String prefix) {
		return SignFormat.builder().formatName(name).signTypePrefix(prefix).build();
	}

}
