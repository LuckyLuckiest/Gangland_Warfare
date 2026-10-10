package org.luckyraven.gangland.listener.inventory;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.data.placeholder.PlaceholderService;
import org.luckyraven.gangland.file.configuration.inventory.InventoryDefinitionStore;
import org.luckyraven.gangland.file.configuration.inventory.InventoryRuntimeContext;
import org.luckyraven.gangland.menu.InventoryBuilder;
import org.luckyraven.gangland.menu.InventoryData;
import org.luckyraven.gangland.menu.OpenInventory;
import org.luckyraven.gangland.menu.State;
import org.luckyraven.gangland.menu.condition.ConditionEvaluator;
import org.luckyraven.keystone.inventory.InventoryService;
import org.luckyraven.keystone.inventory.chest.ChestMenu;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T-226: an {@code Open.Condition} on a menu's {@code Open.Command} decides whether the command opens it. A false
 * condition makes the listener skip the menu without opening it and without cancelling the command, so the command
 * reaches its own executor (the gang onboarding for {@code /glw gang}). With no condition the old behaviour holds.
 */
@DisplayName("InventoryOpenByCommandListener - Open.Condition gates a command-opened menu")
class InventoryOpenConditionTest {

	private static final String CONDITION = "%gangland_user_has-gang%";

	private Player                 player;
	private InventoryBuilder       builder;
	private ChestMenu              menu;
	private ConditionEvaluator     conditions;
	private InventoryDefinitionStore store;
	private InventoryData          data;
	private InventoryOpenByCommandListener listener;

	@BeforeEach
	void setUp() {
		player     = mock(Player.class);
		builder    = mock(InventoryBuilder.class);
		menu       = mock(ChestMenu.class);
		conditions = mock(ConditionEvaluator.class);
		store      = mock(InventoryDefinitionStore.class);
		data       = new InventoryData("gang_info", "Gang", "chest", 27);

		when(builder.inventoryData()).thenReturn(data);
		when(store.getInventoryKeys()).thenReturn(Set.of("gang_info"));
		when(store.getInventory("gang_info")).thenReturn(builder);
		when(builder.createMenu(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
				.thenReturn(menu);

		listener = new InventoryOpenByCommandListener(mock(Gangland.class), mock(PlaceholderService.class),
		                                              conditions, store, mock(InventoryRuntimeContext.class),
		                                              mock(InventoryService.class));
	}

	private void openCommandWith(String condition) {
		data.addOpenInventory(new OpenInventory(State.COMMAND, "/glw gang", null, condition));
	}

	private PlayerCommandPreprocessEvent runCommand() {
		PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player, "/glw gang");
		listener.onInventoryCommand(event);
		return event;
	}

	@Test
	@DisplayName("a false condition neither opens the menu nor cancels the command, so GangCommand runs")
	void falseCondition_skipsMenuAndLetsCommandThrough() {
		openCommandWith(CONDITION);
		when(conditions.evaluate(player, CONDITION)).thenReturn(false);

		PlayerCommandPreprocessEvent event = runCommand();

		assertFalse(event.isCancelled(), "the command must reach GangCommand");
		verify(builder, never()).createMenu(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
		verify(menu, never()).open(any());
	}

	@Test
	@DisplayName("a true condition opens the menu and cancels the command")
	void trueCondition_opensMenuAndCancels() {
		openCommandWith(CONDITION);
		when(conditions.evaluate(eq(player), eq(CONDITION))).thenReturn(true);

		PlayerCommandPreprocessEvent event = runCommand();

		assertTrue(event.isCancelled());
		verify(menu).open(player);
	}

	@Test
	@DisplayName("without an Open.Condition the menu opens and cancels as before")
	void noCondition_opensMenuAndCancels() {
		openCommandWith(null);

		PlayerCommandPreprocessEvent event = runCommand();

		assertTrue(event.isCancelled());
		verify(menu).open(player);
	}
}
