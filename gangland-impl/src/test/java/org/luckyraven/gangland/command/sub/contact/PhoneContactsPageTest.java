package org.luckyraven.gangland.command.sub.contact;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The bundled phone opens a Crooked Contact page whose two buttons run {@code /glw contact 1|2}. */
@DisplayName("Phone - the Crooked Contact page")
class PhoneContactsPageTest {

	private static YamlConfiguration bundled(String name) {
		var stream = PhoneContactsPageTest.class.getResourceAsStream("/inventory/" + name + ".yml");
		assertNotNull(stream, name + ".yml is bundled");
		return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
	}

	@Test
	@DisplayName("phone.yml slot 31 opens phone_contacts")
	void phoneSlot31_opensTheContactsPage() {
		YamlConfiguration phone = bundled("phone");

		assertEquals("phone_contacts", phone.getString("Slots.31.OnClick.Inventory"));
		assertEquals("&6&lCrooked Contact", phone.getString("Slots.31.Name"));
	}

	@Test
	@DisplayName("phone_contacts has one button per star count and shows the price and cooldown")
	void contactsPage_buttonsRunTheCommand() {
		YamlConfiguration page = bundled("phone_contacts");

		assertEquals("/glw contact 1", page.getString("Slots.21.OnClick.Command"));
		assertEquals("/glw contact 2", page.getString("Slots.23.OnClick.Command"));

		for (String slot : List.of("21", "23")) {
			String lore = String.join("\n", page.getStringList("Slots." + slot + ".Lore"));
			assertTrue(lore.contains("%gangland_contact_price%"));
			assertTrue(lore.contains("%gangland_contact_cooldown%"));
		}
	}

}
