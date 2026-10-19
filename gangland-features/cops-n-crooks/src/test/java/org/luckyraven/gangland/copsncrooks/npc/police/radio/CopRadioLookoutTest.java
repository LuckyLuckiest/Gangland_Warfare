package org.luckyraven.gangland.copsncrooks.npc.police.radio;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.keystone.npc.NpcSquad;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.luckyraven.keystone.testkit.PluginMocks;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** P-LOOK-1/2 on the cop side: the voice delegates relaysTo to the installed predicate; Lookout_Format is a root key. */
@DisplayName("CopRadio - lookout")
class CopRadioLookoutTest {

	@TempDir
	Path tempDir;

	private BukkitStatics bukkit;
	private CopRadio      radio;
	private Player        suspect;
	private Player        hearer;
	private CopGroup      group;
	private List<String>  tested;

	@BeforeEach
	void setUp() {
		bukkit = BukkitStatics.install();
		World             world    = mock(World.class);
		CopConfigProvider provider = mock(CopConfigProvider.class);
		radio   = new CopRadio(() -> provider, key -> List.of(key), () -> 10_000L, (task, ticks) -> { });
		suspect = player(world);
		hearer  = player(world);
		group   = new CopGroup(suspect.getUniqueId());
		tested  = new ArrayList<>();
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	private Player player(World world) {
		UUID   id = UUID.randomUUID();
		Player p  = mock(Player.class);
		when(p.getUniqueId()).thenReturn(id);
		when(p.getWorld()).thenReturn(world);
		when(p.getLocation()).thenReturn(new Location(world, 0, 64, 0));
		bukkit.statics().when(() -> Bukkit.getPlayer(id)).thenReturn(p);
		return p;
	}

	private BiPredicate<Player, Player> recording(boolean answer) {
		return (h, t) -> {
			tested.add(h.getUniqueId() + ">" + t.getUniqueId());
			return answer;
		};
	}

	@Test
	@DisplayName("an installed predicate decides, asked with (hearer, the hunted player); none installed relays to nobody")
	void installedPredicate_decides() {
		assertFalse(radio.voice(group).relaysTo(hearer, group.getSquad()), "no predicate yet");

		radio.setLookout(recording(true));
		assertTrue(radio.voice(group).relaysTo(hearer, group.getSquad()));
		assertEquals(List.of(hearer.getUniqueId() + ">" + suspect.getUniqueId()), tested);

		radio.setLookout(recording(false));
		assertFalse(radio.voice(group).relaysTo(hearer, group.getSquad()));
	}

	@Test
	@DisplayName("setLookout(null) goes back to relaying to nobody")
	void nullPredicate_resets() {
		radio.setLookout(recording(true));
		assertTrue(radio.voice(group).relaysTo(hearer, group.getSquad()));

		radio.setLookout(null);

		assertFalse(radio.voice(group).relaysTo(hearer, group.getSquad()));
	}

	@Test
	@DisplayName("a squad with no hunted player, or a hunted entity that is not a player, never calls the predicate")
	void noHuntedPlayer_predicateNotCalled() {
		radio.setLookout(recording(true));
		assertTrue(radio.voice(group).relaysTo(hearer, group.getSquad()), "control: a player target relays");
		tested.clear();

		assertFalse(radio.voice(group).relaysTo(hearer, new NpcSquad()), "unknown squad hunts nobody");

		UUID         mobId = UUID.randomUUID();
		LivingEntity mob   = mock(LivingEntity.class);
		bukkit.statics().when(() -> Bukkit.getPlayer(mobId)).thenReturn(null);
		bukkit.statics().when(() -> Bukkit.getEntity(mobId)).thenReturn(mob);
		CopGroup mobGroup = new CopGroup(mobId);
		assertFalse(radio.voice(mobGroup).relaysTo(hearer, mobGroup.getSquad()), "a mob is not a player");

		assertTrue(tested.isEmpty());
	}

	@Test
	@DisplayName("Lookout_Format is read from the document root and falls back to the code default when the file lacks it")
	void lookoutFormat_rootKeyWithFallback() throws IOException {
		CopRadioMessages withKey = build("Lookout_Format: \"[L %name%] %line%\"\n");
		assertEquals(List.of("[L %name%] %line%"), withKey.lines("Lookout_Format"));

		CopRadioMessages old = build("Lines: {}\n");
		assertEquals(List.of("&8[Lookout %name%] &r%line%"), old.lines("Lookout_Format"));
	}

	@Test
	@DisplayName("the bundled English and Spanish radio files carry the Lookout_Format root key")
	void bundledFiles_haveRootKey() throws IOException {
		assertEquals("&8[Lookout %name%] &r%line%",
		             shipped("copsncrooks/cop_radio_messages.yml").getString("Lookout_Format"));
		assertEquals("&8[Vigía %name%] &r%line%",
		             shipped("copsncrooks/cop_radio_messages_es.yml").getString("Lookout_Format"));
	}

	private YamlConfiguration shipped(String resource) throws IOException {
		try (InputStream in = Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(resource))) {
			return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
		}
	}

	private CopRadioMessages build(String yaml) throws IOException {
		JavaPlugin  plugin      = PluginMocks.plugin(tempDir);
		FileManager fileManager = new FileManager(plugin);
		Files.writeString(tempDir.resolve("cop_radio_messages.yml"), yaml, StandardCharsets.UTF_8);
		fileManager.addFile(new FileHandler(plugin, tempDir.resolve("cop_radio_messages.yml").toFile()), false);
		return new CopRadioMessages(fileManager);
	}
}
