package org.luckyraven.gangland.copsncrooks.config;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.copsncrooks.npc.police.config.YamlCopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.LookoutRelay;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.core.wanted.Wanted;
import org.luckyraven.gangland.data.gang.GangMembership;
import org.luckyraven.gangland.data.gang.GangMembershipView;
import org.luckyraven.keystone.bean.PostConstruct;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;
import org.luckyraven.keystone.persistence.config.ConfigDocument;
import org.luckyraven.keystone.persistence.config.ConfigParser;
import org.luckyraven.keystone.persistence.config.ConfigReport;
import org.luckyraven.keystone.persistence.config.NodeReader;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Lookout wiring (the bean reads the live config and the wanted level) plus Cops.Lookout.Enable in the parsed and bundled config. */
@DisplayName("LookoutModuleConfig")
class LookoutModuleConfigTest {

	private static Player player(UUID id) {
		Player p = mock(Player.class);
		when(p.getUniqueId()).thenReturn(id);
		return p;
	}

	@SuppressWarnings("unchecked")
	private static UserManager<Player> usersWithLevel(Player p, int level) {
		UserManager<Player> users  = mock(UserManager.class);
		User<Player>        user   = mock(User.class);
		Wanted              wanted = mock(Wanted.class);
		when(wanted.getLevel()).thenReturn(level);
		when(user.getWanted()).thenReturn(wanted);
		when(users.getUser(p)).thenReturn(user);
		return users;
	}

	private static GangMembership sameGang(UUID a, UUID b) {
		GangMembership     gangs = new GangMembership();
		GangMembershipView view  = mock(GangMembershipView.class);
		when(view.gangIdOf(any(UUID.class))).thenReturn(-1);
		when(view.gangIdOf(a)).thenReturn(7);
		when(view.gangIdOf(b)).thenReturn(7);
		gangs.install(view);
		return gangs;
	}

	private static LookoutModuleConfig config() {
		return new LookoutModuleConfig(mock(JavaPlugin.class), new DependencyContainer());
	}

	@Test
	@DisplayName("installLookout hands the relay bean to the radio and is a @PostConstruct")
	void installLookout_wiresRadio() throws NoSuchMethodException {
		LookoutRelay        relay     = mock(LookoutRelay.class);
		CopRadio            radio     = mock(CopRadio.class);
		DependencyContainer container = new DependencyContainer();
		container.registerInstance(LookoutRelay.class, relay);
		container.registerInstance(CopRadio.class, radio);

		new LookoutModuleConfig(mock(JavaPlugin.class), container).installLookout();

		verify(radio).setLookout(relay);
		assertTrue(LookoutModuleConfig.class.getMethod("installLookout").isAnnotationPresent(PostConstruct.class));
	}

	@Test
	@DisplayName("the relay bean reads the wanted level from the user manager: a wanted mate stays silent")
	void relayBean_usesWantedLevel() {
		UUID      hearerId = UUID.randomUUID();
		UUID      huntedId = UUID.randomUUID();
		Player    hearer   = player(hearerId);
		Player    hunted   = player(huntedId);
		CopLoader loader   = mock(CopLoader.class);

		LookoutRelay unwanted = config().lookoutRelay(loader, usersWithLevel(hearer, 0), sameGang(hearerId, huntedId));
		assertTrue(unwanted.test(hearer, hunted));

		LookoutRelay wanted = config().lookoutRelay(loader, usersWithLevel(hearer, 2), sameGang(hearerId, huntedId));
		assertFalse(wanted.test(hearer, hunted));
	}

	@Test
	@DisplayName("the relay bean reads Cops.Lookout.Enable from the loaded provider per call; no provider means enabled")
	void relayBean_readsEnableFromLoadedProviderPerCall() {
		UUID              hearerId = UUID.randomUUID();
		UUID              huntedId = UUID.randomUUID();
		Player            hearer   = player(hearerId);
		Player            hunted   = player(huntedId);
		CopLoader         loader   = mock(CopLoader.class);
		CopConfigProvider provider = mock(CopConfigProvider.class);

		LookoutRelay relay = config().lookoutRelay(loader, usersWithLevel(hearer, 0), sameGang(hearerId, huntedId));

		when(loader.getLoadedProvider()).thenReturn(null);
		assertTrue(relay.test(hearer, hunted), "no provider yet");

		when(loader.getLoadedProvider()).thenReturn(provider);
		when(provider.isLookoutEnabled()).thenReturn(false);
		assertFalse(relay.test(hearer, hunted));

		when(provider.isLookoutEnabled()).thenReturn(true);
		assertTrue(relay.test(hearer, hunted));
	}

	@Test
	@DisplayName("Cops.Lookout.Enable: a missing block is enabled, an explicit false is read")
	void yamlProvider_readsEnable() {
		assertTrue(parse("Cops:\n   Tiers: {}\n", new ConfigReport()).isLookoutEnabled());
		assertFalse(parse("Cops:\n   Lookout:\n      Enable: false\n", new ConfigReport()).isLookoutEnabled());
		assertTrue(parse("Cops:\n   Lookout:\n      Enable: true\n", new ConfigReport()).isLookoutEnabled());
	}

	@Test
	@DisplayName("bundled cops.yml has an appended Cops.Lookout block (Enable true, tagged 0.20.0) and parses without issues")
	void bundledCops_hasLookoutBlock() throws IOException {
		String yaml;
		try (InputStream in = Objects.requireNonNull(
				getClass().getClassLoader().getResourceAsStream("copsncrooks/cops.yml"))) {
			yaml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		int lookout = yaml.indexOf("\n   Lookout:\n");
		assertTrue(lookout > 0, "Cops.Lookout block missing");
		assertTrue(lookout > yaml.indexOf("\n   Disguise:\n"), "appended after the existing 0.20.0 blocks");
		String block = yaml.substring(lookout);
		assertTrue(block.contains("      # 0.20.0") || block.contains("   # 0.20.0"), "block carries a 0.20.0 comment");
		assertTrue(block.contains("      Enable: true"), "Enable key");

		ConfigReport      report   = new ConfigReport();
		CopConfigProvider provider = parse(yaml, report);
		assertTrue(provider.isLookoutEnabled());
		assertTrue(report.issues().isEmpty(), () -> "bundled cops.yml has issues: " + report.issues());

		CopConfigProvider off = parse(yaml.substring(0, lookout) + "\n   Lookout:\n      Enable: false\n",
		                              new ConfigReport());
		assertFalse(off.isLookoutEnabled(), "the bundled block shape is the one the provider reads");
	}

	private static CopConfigProvider parse(String yaml, ConfigReport report) {
		ConfigDocument document = new ConfigParser().parse(Path.of("cops.yml"), new StringReader(yaml), report);
		return new YamlCopConfigProvider(NodeReader.of(document.root(), report), report, null, null);
	}
}
