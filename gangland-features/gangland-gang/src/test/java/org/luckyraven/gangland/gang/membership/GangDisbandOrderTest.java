package org.luckyraven.gangland.gang.membership;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.support.FakeIdentitySettingsContract;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.core.user.IdentitySettings;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangAlliance;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.database.repositories.gang.GangAllianceRepository;
import org.luckyraven.gangland.gang.GangSettings;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.gangland.gang.support.FakeGangSettingsContract;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.keystone.message.MessageProvider;
import org.luckyraven.keystone.persistence.repository.IRepository;
import org.luckyraven.keystone.persistence.repository.RepositoryRegistry;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.CALLS_REAL_METHODS;

/** T-703: alliance rows must be gone before the gang row, because gang_ally has FKs to gang(id) with no ON DELETE. */
@DisplayName("GangDisbandService - alliance rows deleted before the gang row")
class GangDisbandOrderTest {

	@Test
	@DisplayName("deleteAllForGang_runsBeforeGangDelete")
	@SuppressWarnings("unchecked")
	void deleteAllForGang_runsBeforeGangDelete() {
		GangSettings.bind(new FakeGangSettingsContract());
		IdentitySettings.bind(new FakeIdentitySettingsContract());
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
		PluginManager pluginManager = mock(PluginManager.class);
		Server server = mock(Server.class);
		when(server.getPluginManager()).thenReturn(pluginManager);

		RepositoryRegistry registry = mock(RepositoryRegistry.class);
		IRepository<Gang> gangRepository = mock(IRepository.class);
		GangAllianceRepository allianceRepository = mock(GangAllianceRepository.class);
		when(registry.getRepository(Gang.class)).thenReturn(gangRepository);
		when(registry.getRepository(GangAlliance.class)).thenReturn(allianceRepository);

		Gang gang = new Gang(7);
		gang.setName("Vipers");
		GangDisbandService service = new GangDisbandService(mock(UserManager.class), mock(UserManager.class),
		                                                    mock(MemberManager.class), mock(GangManager.class),
		                                                    registry, mock(GangMessages.class));

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
		     MockedStatic<Settings> settings = mockStatic(Settings.class, CALLS_REAL_METHODS)) {
			settings.when(Settings::getLanguagePicked).thenReturn("en");
			settings.when(Settings::getMoneySymbol).thenReturn("$");
			bukkit.when(Bukkit::getServer).thenReturn(server);
			bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);

			assertTrue(service.disband(gang, null, false, false).ok());
		}

		InOrder order = inOrder(allianceRepository, gangRepository);
		order.verify(allianceRepository).deleteAllForGang(gang);
		order.verify(gangRepository).delete(gang);
	}
}
