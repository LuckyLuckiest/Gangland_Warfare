package org.luckyraven.gangland.file.configuration;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gangland 0.9.0 gate-C review, finding B1: when the weapon module left, the only death-message template list
 * ({@code Death.Weapon}) left with it and {@code PlayerDeathListener.buildDeathMessage} returned {@code null} for
 * every kill. On the downed path the lethal damage is cancelled, so {@code PlayerDeathEvent} never fires and that
 * broadcast was the only announcement - kills went silent. The core now owns a generic {@code Death.Global} list.
 */
@DisplayName("message_en.yml - Death.Global")
class DeathGlobalMessageTest {

	private static final String RESOURCE = "/message/message_en.yml";

	@Test
	@DisplayName("Death.Global is a non-empty list of %killer%/%victim% templates without %item%")
	void deathGlobalTemplates() throws Exception {
		YamlConfiguration yaml = load();
		List<String>      list = yaml.getStringList("Death.Global");

		assertFalse(list.isEmpty(), "Death.Global must ship at least one template");
		for (String template : list) {
			assertTrue(template.contains("%killer%") && template.contains("%victim%"),
			           "template must name both players: " + template);
			assertFalse(template.contains("%item%"), "only Bartizan can fill %item%: " + template);
		}
	}

	@Test
	@DisplayName("Messages.DEATH_GLOBAL points at that list")
	void messagesMemberResolves() throws Exception {
		assertNotNull(load().get(Messages.DEATH_GLOBAL.getPath()), "Messages.DEATH_GLOBAL path missing in YAML");
	}

	private static YamlConfiguration load() throws Exception {
		try (InputStream in = DeathGlobalMessageTest.class.getResourceAsStream(RESOURCE)) {
			assertNotNull(in, "resource missing: " + RESOURCE);
			return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
		}
	}

}
