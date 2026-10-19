package org.luckyraven.gangland.copsncrooks.config;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.ChaseArcs;
import org.luckyraven.gangland.copsncrooks.wanted.mostwanted.MostWanted;
import org.luckyraven.gangland.data.placeholder.extension.PlaceholderContribution;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The Most Wanted wiring: the radio call and the placeholder bean. */
@DisplayName("MostWantedModuleConfig")
class MostWantedModuleConfigTest {

	private final MostWantedModuleConfig config =
			new MostWantedModuleConfig(mock(JavaPlugin.class), new DependencyContainer());

	@Test
	@DisplayName("the radio call is skipped without a cop group, and dispatches Most_Wanted with the stars when there is one")
	void radioOnlyWithGroup() {
		CopManager manager = mock(CopManager.class);
		CopRadio   radio   = mock(CopRadio.class);
		UUID       id      = UUID.randomUUID();
		Player     player  = mock(Player.class);
		when(player.getUniqueId()).thenReturn(id);
		BiConsumer<Player, Integer> call = config.radio(manager, radio);

		when(manager.groupOf(id)).thenReturn(null);
		call.accept(player, 4);
		verify(radio, never()).dispatch(any(), any(), anyString(), anyInt(), anyString());

		CopGroup group = mock(CopGroup.class);
		when(manager.groupOf(id)).thenReturn(group);
		call.accept(player, 4);
		verify(radio).dispatch(group, player, "Most_Wanted", 4, "");
	}

	@Test
	@DisplayName("mostWantedPlaceholders is a @Bean typed PlaceholderContribution, answering the title token")
	void placeholderBeanType() throws NoSuchMethodException {
		MostWanted mw = mock(MostWanted.class);
		when(mw.holderName()).thenReturn("Steve");

		Method method = MostWantedModuleConfig.class.getMethod("mostWantedPlaceholders", MostWanted.class);
		assertTrue(method.isAnnotationPresent(Bean.class));
		assertEquals(PlaceholderContribution.class, method.getReturnType());
		assertEquals("Steve", config.mostWantedPlaceholders(mw).resolveGlobal("cnc_most_wanted"));
	}

	@Test
	@DisplayName("mostWanted is a @Bean built from the arcs, the config, the messages, the cop manager and the radio")
	void mostWantedIsABean() throws NoSuchMethodException {
		Method method = MostWantedModuleConfig.class.getMethod("mostWanted", ChaseArcs.class, ChaseConfigLoader.class,
		                                                      WantedMessages.class, CopManager.class, CopRadio.class);
		assertTrue(method.isAnnotationPresent(Bean.class));

		MostWanted built = config.mostWanted(new ChaseArcs(System::currentTimeMillis), mock(ChaseConfigLoader.class),
		                                     mock(WantedMessages.class), mock(CopManager.class), mock(CopRadio.class));
		assertEquals(0, built.holderStars());
	}
}
