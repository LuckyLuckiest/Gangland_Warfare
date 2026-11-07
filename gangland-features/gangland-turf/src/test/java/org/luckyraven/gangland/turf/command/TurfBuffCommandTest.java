package org.luckyraven.gangland.turf.command;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.powerups.ActiveBuffManager;
import org.luckyraven.gangland.turf.powerups.EffectType;
import org.luckyraven.gangland.turf.powerups.PowerupDefinition;
import org.luckyraven.gangland.turf.powerups.PowerupRegistry;
import org.luckyraven.gangland.turf.selection.WandSelectionManager;
import org.luckyraven.gangland.turf.support.TurfTextFixture;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.luckyraven.keystone.testkit.PluginMocks;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T-942: the powerup id typed after {@code /glw turf buff} is lower-cased with {@link Locale#ROOT}, so a Turkish
 * default locale does not turn the capital {@code I} of {@code SMALL_INCOME_BOOST} into a dotless {@code ı}.
 */
@DisplayName("TurfBuffCommand - id lookup is locale independent (T-942)")
class TurfBuffCommandTest {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private MockedStatic<Settings> settings;
	private PowerupRegistry        registry;
	private ActiveBuffManager      buffs;
	private WandSelectionManager   selections;
	private OptionalArgument       buffId;
	private Locale                 defaultLocale;
	private Turf                   turf;

	@BeforeEach
	void setUp() throws IOException {
		defaultLocale = Locale.getDefault();
		settings      = TurfTextFixture.stubMoneySymbol();
		TurfMessageContract messages = mock(TurfMessageContract.class);
		TurfManager         turfs    = mock(TurfManager.class);
		registry   = mock(PowerupRegistry.class);
		buffs      = mock(ActiveBuffManager.class);
		selections = new WandSelectionManager();
		turf = new Turf(7, "Docks", new CuboidRegion("world", 0, 0, 10, 10), null, BigDecimal.ONE, 0L, 0L);
		when(turfs.get(7)).thenReturn(turf);

		JavaPlugin plugin = PluginMocks.plugin(tempDir);
		Argument   parent = mock(Argument.class);
		when(parent.getPermission()).thenReturn("gangland.command.turf");
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			TurfBuffCommand command = new TurfBuffCommand(plugin, new Tree<>(), parent, turfs, selections, messages,
			                                              TurfTextFixture.messages(tempDir, messages), registry, buffs);
			buffId = (OptionalArgument) command.getNode().getChildren().get(0).getData();
		}
	}

	@AfterEach
	void tearDown() {
		Locale.setDefault(defaultLocale);
		settings.close();
	}

	@Test
	@DisplayName("a capitalised id is looked up lower-cased under a Turkish default locale")
	void capitalisedId_turkishLocale_stillFindsThePowerup() {
		PowerupDefinition def = new PowerupDefinition("small_income_boost", "Boost", BigDecimal.TEN,
		                                              EffectType.INCOME_MULTIPLIER, 1.5, 60);
		List<String> looked = new ArrayList<>();
		when(registry.get(anyString())).thenAnswer(invocation -> {
			looked.add(invocation.getArgument(0));
			return def;
		});
		Player admin = mock(Player.class);
		when(admin.getUniqueId()).thenReturn(UUID.randomUUID());
		when(admin.hasPermission(TurfPermissions.ADMIN)).thenReturn(true);
		selections.get(admin).setActiveTurfId(7);
		Locale.setDefault(Locale.forLanguageTag("tr-TR"));

		buffId.executeArgument(admin, new String[]{"turf", "buff", "SMALL_INCOME_BOOST"});

		assertEquals(List.of("small_income_boost"), looked, "the id must be lower-cased with Locale.ROOT");
		verify(buffs).activate(7, def);
	}
}
