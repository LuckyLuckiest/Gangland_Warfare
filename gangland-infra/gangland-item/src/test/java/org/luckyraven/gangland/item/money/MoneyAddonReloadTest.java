package org.luckyraven.gangland.item.money;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * T-949: {@code load} re-reads the three Money text keys with the previous field value as the {@code getString}
 * default, so a key deleted from {@code items/money.yml} kept its stale value across {@code /glw reload files}.
 */
@DisplayName("MoneyAddon - a reload falls back to the built-in text for a deleted key")
class MoneyAddonReloadTest {

	private static YamlConfiguration yaml(String text) {
		return YamlConfiguration.loadConfiguration(new StringReader(text));
	}

	@Test
	@DisplayName("T-949: keys changed and then deleted return to the built-in values on the next load")
	void deletedKeys_fallBackToBuiltIns() {
		MoneyAddon builtIn = new MoneyAddon();
		MoneyAddon addon   = new MoneyAddon();

		addon.load(yaml("""
				Money:
				  Default_Variation: large
				  Pickup_Chat_Message: "custom chat"
				  Pickup_Action_Bar: "custom bar"
				"""));
		assertEquals("large", addon.getDefaultVariationId());
		assertEquals("custom chat", addon.getPickupChatMessage());
		assertEquals("custom bar", addon.getPickupActionBar());

		addon.load(yaml("Money:\n  Drop_Sources: {}\n"));
		assertEquals(builtIn.getDefaultVariationId(), addon.getDefaultVariationId(), "Default_Variation was deleted");
		assertEquals(builtIn.getPickupChatMessage(), addon.getPickupChatMessage(), "Pickup_Chat_Message was deleted");
		assertEquals(builtIn.getPickupActionBar(), addon.getPickupActionBar(), "Pickup_Action_Bar was deleted");
	}

	@Test
	@DisplayName("T-949: a reload whose file has no Money section also returns to the built-in values")
	void missingMoneySection_fallsBackToBuiltIns() {
		MoneyAddon builtIn = new MoneyAddon();
		MoneyAddon addon   = new MoneyAddon();

		addon.load(yaml("Money:\n  Default_Variation: large\n  Pickup_Action_Bar: \"custom bar\"\n"));
		addon.load(yaml("Variations: {}\n"));

		assertEquals(builtIn.getDefaultVariationId(), addon.getDefaultVariationId());
		assertEquals(builtIn.getPickupActionBar(), addon.getPickupActionBar());
	}

}
