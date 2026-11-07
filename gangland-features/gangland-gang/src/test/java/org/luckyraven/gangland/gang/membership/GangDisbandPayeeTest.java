package org.luckyraven.gangland.gang.membership;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.support.FakeIdentitySettingsContract;
import org.luckyraven.gangland.core.user.IdentitySettings;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.GangSettings;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.gang.support.FakeGangSettingsContract;
import org.luckyraven.keystone.economy.EconomyHandler;
import org.luckyraven.keystone.message.MessageProvider;
import org.luckyraven.keystone.persistence.repository.IRepository;
import org.luckyraven.keystone.persistence.repository.RepositoryRegistry;
import org.mockito.MockedStatic;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/**
 * Characterises who the purge's disband (no actor) treats as the payee: the first member whose rank name equals the
 * tail rank ignoring case, skipping a member without a rank, else the first member of the gang. The payee is the
 * only one who gets the creation-fee refund (a quarter of 40 = 10), so the refund shows who was picked.
 */
@DisplayName("GangDisbandService - the payee of a disband without an actor")
class GangDisbandPayeeTest {

	private static final BigDecimal REFUND = new BigDecimal("10");

	private final List<Member>         members    = new ArrayList<>();
	private final List<EconomyHandler> economies  = new ArrayList<>();

	@BeforeEach
	void bindSettings() {
		GangSettings.bind(new FakeGangSettingsContract().withCreateFee(new BigDecimal("40.00")));
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
	}

	@Test
	@DisplayName("a member without a rank is skipped and the tail rank is matched ignoring case")
	void rankLessMemberSkipped_tailMatchedIgnoringCase() {
		disbandWithRanks(null, "Member", "OWNER");

		assertRefundedOnly(2);
	}

	@Test
	@DisplayName("with two tail-rank members the first in gang order wins")
	void twoOwners_firstWins() {
		disbandWithRanks("member", "owner", "Owner");

		assertRefundedOnly(1);
	}

	@Test
	@DisplayName("with no tail-rank member the first member of the gang is the payee")
	void noOwner_firstMemberIsPayee() {
		disbandWithRanks(null, "member", "officer");

		assertRefundedOnly(0);
	}

	@SuppressWarnings("unchecked")
	private void disbandWithRanks(String... rankNames) {
		UserManager<OfflinePlayer> offlineUsers = mock(UserManager.class);
		RepositoryRegistry         registry     = mock(RepositoryRegistry.class);
		when(registry.getRepository(Member.class)).thenReturn(mock(IRepository.class));
		when(registry.getGenericRepository(User.class)).thenReturn(mock(IRepository.class));
		when(registry.getRepository(Gang.class)).thenReturn(mock(IRepository.class));

		PluginManager pluginManager = mock(PluginManager.class);
		Server        server        = mock(Server.class);
		when(server.getPluginManager()).thenReturn(pluginManager);

		List<OfflinePlayer> players = new ArrayList<>();
		for (String rankName : rankNames) {
			Member member = new Member(UUID.randomUUID());
			if (rankName != null) {
				Rank rank = mock(Rank.class);
				when(rank.getName()).thenReturn(rankName);
				member.setRank(rank);
			}
			members.add(member);

			OfflinePlayer player = mock(OfflinePlayer.class);
			players.add(player);

			EconomyHandler economy = new EconomyHandler(null);
			economies.add(economy);

			User<OfflinePlayer> user = mock(User.class);
			when(user.getEconomy()).thenReturn(economy);
			when(offlineUsers.getUser(player)).thenReturn(user);
		}

		Gang gang = new Gang(7, members, "Vipers");
		GangDisbandService service = new GangDisbandService(mock(UserManager.class), offlineUsers,
		                                                    mock(MemberManager.class), mock(GangManager.class),
		                                                    registry, mock(GangMessages.class));

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
		     MockedStatic<Settings> settings = mockStatic(Settings.class, CALLS_REAL_METHODS)) {
			settings.when(Settings::getLanguagePicked).thenReturn("en");
			settings.when(Settings::getMoneySymbol).thenReturn("$");
			bukkit.when(Bukkit::getServer).thenReturn(server);
			bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
			bukkit.when(() -> Bukkit.getOfflinePlayer(any(UUID.class))).thenAnswer(invocation -> {
				UUID asked = invocation.getArgument(0);
				for (int i = 0; i < members.size(); i++) {
					if (members.get(i).getUuid().equals(asked)) return players.get(i);
				}
				return null;
			});

			assertTrue(service.disband(gang, null, false, true).ok());
		}
	}

	private void assertRefundedOnly(int payeeIndex) {
		for (int i = 0; i < economies.size(); i++) {
			BigDecimal expected = i == payeeIndex ? REFUND : BigDecimal.ZERO;
			assertEquals(0, expected.compareTo(economies.get(i).getAmount()),
			             "member " + i + " holds " + economies.get(i).getAmount());
		}
	}
}
