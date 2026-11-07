package org.luckyraven.gangland.npcshops.command.trader;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.npcshops.trader.TraderData;
import org.luckyraven.gangland.npcshops.trader.TraderManager;
import org.luckyraven.gangland.npcshops.trader.trait.TraderTraitRegistry;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.message.MessageProvider;
import org.luckyraven.keystone.shop.ShopRegistry;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** T-957: {@code /glw trader create} must not claim a spawn when the NPC was not spawned (Citizens absent). */
@DisplayName("TraderCreateCommand - spawn feedback")
class TraderCreateCommandTest {

	private final Player              player        = mock(Player.class);
	private final TraderManager       traderManager = mock(TraderManager.class);
	private final ShopRegistry        shopRegistry  = mock(ShopRegistry.class);
	private final TraderTraitRegistry traitRegistry = mock(TraderTraitRegistry.class);

	private BukkitStatics bukkit;

	@BeforeEach
	void installBukkit() {
		bukkit = BukkitStatics.install();
	}

	@AfterEach
	void removeBukkit() {
		bukkit.close();
	}

	@BeforeAll
	static void primeMessages() throws ReflectiveOperationException {
		Field field = Settings.class.getDeclaredField("moneySymbol");
		field.setAccessible(true);
		field.set(null, "$");
		Messages.init(new MessageProvider() {
			@Override
			public String getString(String path) {
				return path;
			}

			@Override
			public List<String> getStringList(String path) {
				return List.of();
			}
		});
	}

	@SuppressWarnings("unchecked")
	private void create() throws ReflectiveOperationException {
		when(shopRegistry.exists("armory")).thenReturn(true);
		when(traitRegistry.exists("greedy")).thenReturn(true);
		when(player.getLocation()).thenReturn(new Location(mock(World.class), 1, 65, 2));

		TraderCreateCommand command = new TraderCreateCommand(mock(JavaPlugin.class), mock(Tree.class),
		                                                      mock(Argument.class), traderManager, shopRegistry,
		                                                      traitRegistry);
		Method spawnTrader = TraderCreateCommand.class.getDeclaredMethod("spawnTrader", CommandSender.class,
		                                                                 String[].class, String.class);
		spawnTrader.setAccessible(true);
		spawnTrader.invoke(command, player, new String[]{"trader", "create", "armory", "greedy"}, null);
	}

	private String sent() {
		ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
		verify(player).sendMessage(message.capture());
		return message.getValue();
	}

	@Test
	@DisplayName("a create whose NPC did not spawn does not report Spawned")
	void notSpawnedIsNotReportedAsSpawned() throws ReflectiveOperationException {
		// a mocked manager's create(...) spawns nothing, exactly like TraderManager.spawn returning null sans Citizens
		create();

		assertFalse(sent().contains("Spawned trader"), "reported a spawn that never happened");
	}

	@Test
	@DisplayName("a create whose NPC did not spawn says it was saved and will appear with Citizens")
	void notSpawnedSaysSaved() throws ReflectiveOperationException {
		create();

		assertTrue(sent().contains("Trader saved"));
	}

	@Test
	@DisplayName("a create whose NPC spawned reports Spawned")
	void spawnedIsReportedAsSpawned() throws ReflectiveOperationException {
		when(traderManager.create(any(TraderData.class))).thenReturn(true);

		create();

		assertTrue(sent().contains("Spawned trader"));
	}
}
