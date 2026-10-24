package org.luckyraven.gangland.copsncrooks.config;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.ChaseArcs;
import org.luckyraven.gangland.copsncrooks.wanted.getaway.GetawayService;
import org.luckyraven.gangland.core.user.Level;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.data.gang.GangMembership;
import org.luckyraven.gangland.events.user.UserLevelUpEvent;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The getaway wiring: one {@link GetawayService} bean and the civilian-kill style XP grant. */
@DisplayName("GetawayModuleConfig")
class GetawayModuleConfigTest {

	@SuppressWarnings("unchecked")
	private final UserManager<Player> users = mock(UserManager.class);

	@Test
	@DisplayName("it is a @Configuration whose getawayService is a @Bean")
	void shape() throws NoSuchMethodException {
		assertTrue(GetawayModuleConfig.class.isAnnotationPresent(Configuration.class));
		assertTrue(GetawayModuleConfig.class.getMethod("getawayService", ChaseArcs.class, ChaseConfigLoader.class,
		                                               GangMembership.class, DetainmentService.class,
		                                               WantedMessages.class, UserManager.class)
		                                    .isAnnotationPresent(Bean.class));
	}

	@Test
	@DisplayName("the bean builds from its collaborators")
	void bean_builds() {
		GetawayModuleConfig config = new GetawayModuleConfig(mock(JavaPlugin.class), new DependencyContainer());

		assertNotNull(config.getawayService(new ChaseArcs(System::currentTimeMillis), mock(ChaseConfigLoader.class),
		                                    new GangMembership(), mock(DetainmentService.class),
		                                    mock(WantedMessages.class), users));
	}

	@Test
	@DisplayName("grantXp adds the XP to the player's level with a UserLevelUpEvent")
	@SuppressWarnings("unchecked")
	void grant_addsExperience() {
		Player       player = mock(Player.class);
		User<Player> user   = mock(User.class);
		Level        level  = mock(Level.class);
		when(users.getUser(player)).thenReturn(user);
		when(user.getLevel()).thenReturn(level);

		GetawayModuleConfig.grantXp(users, player, 75);

		verify(level).addExperience(eq(75.0), any(UserLevelUpEvent.class));
	}

	@Test
	@DisplayName("grantXp with no loaded user is a no-op")
	void grant_noUser_noop() {
		Player player = mock(Player.class);
		when(users.getUser(player)).thenReturn(null);

		GetawayModuleConfig.grantXp(users, player, 75);

		verify(users).getUser(player);
	}
}
