package org.luckyraven.gangland.copsncrooks.config;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.wanted.heat.HeatLedger;
import org.luckyraven.keystone.bean.PostConstruct;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;
import org.mockito.ArgumentCaptor;

import java.util.UUID;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins the heat wiring: the module registers a cop-attacked hook that reports the assault to the ledger.
 */
@DisplayName("HeatModuleConfig")
class HeatModuleConfigTest {

	private CopManager       manager;
	private HeatLedger       ledger;
	private HeatModuleConfig config;

	@BeforeEach
	void setUp() {
		manager = mock(CopManager.class);
		ledger  = mock(HeatLedger.class);

		DependencyContainer container = new DependencyContainer();
		container.registerInstance(CopManager.class, manager);
		container.registerInstance(HeatLedger.class, ledger);

		config = new HeatModuleConfig(mock(JavaPlugin.class), container);
	}

	@SuppressWarnings("unchecked")
	private BiConsumer<CopNpc, Player> registeredHook() {
		config.registerAttackedHook();

		ArgumentCaptor<BiConsumer<CopNpc, Player>> captor = ArgumentCaptor.forClass(BiConsumer.class);
		verify(manager).addCopAttackedHook(captor.capture());
		return captor.getValue();
	}

	@Test
	@DisplayName("postConstruct registers an attacked hook that reports the assault")
	void postConstruct_registersAnAttackedHookThatReportsTheAssault() {
		UUID     copId    = UUID.randomUUID();
		LivingEntity entity = mock(LivingEntity.class);
		CopNpc   cop      = mock(CopNpc.class);
		Player   player   = mock(Player.class);
		Location location = mock(Location.class);
		when(entity.getUniqueId()).thenReturn(copId);
		when(cop.getEntity()).thenReturn(entity);
		when(player.getLocation()).thenReturn(location);

		registeredHook().accept(cop, player);

		verify(ledger).reportAssault(player, copId, location);
	}

	@Test
	@DisplayName("the hook is registered by the bean container: registerAttackedHook is a @PostConstruct")
	void registerAttackedHook_isAPostConstruct() throws NoSuchMethodException {
		assertTrue(HeatModuleConfig.class.getMethod("registerAttackedHook").isAnnotationPresent(PostConstruct.class),
		           "without it, hitting a cop never reaches the heat ledger");
	}

	@Test
	@DisplayName("a cop without an entity reports nothing")
	void attackedHook_copWithoutEntity_reportsNothing() {
		CopNpc cop = mock(CopNpc.class);
		when(cop.getEntity()).thenReturn(null);

		registeredHook().accept(cop, mock(Player.class));

		verifyNoInteractions(ledger);
	}
}
