package org.luckyraven.gangland.command.sub.gang;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.command.Command;
import org.luckyraven.gangland.command.data.InformationManager;
import org.luckyraven.gangland.core.support.FakeIdentitySettingsContract;
import org.luckyraven.gangland.core.user.IdentitySettings;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.events.gang.GangLevelUpEvent;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangLevels;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.GangSettings;
import org.luckyraven.gangland.gang.contract.GangAllianceRepositoryContract;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.gangland.gang.file.GangRules.LevelRowValues;
import org.luckyraven.gangland.gang.level.GangLevelTable;
import org.luckyraven.gangland.gang.level.GangLimits;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.permission.GangPermissions;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.gang.support.FakeGangSettingsContract;
import org.luckyraven.gangland.gang.support.W2P2Messages;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.types.ConfirmArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.message.MessageProvider;
import org.luckyraven.keystone.persistence.repository.IRepository;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code /glw gang upgrade}: a level is earned with held-turf XP and claimed by paying its fee (D16 C). Level 1 costs
 * 20000, level 2 costs 40000 and level 3 costs nothing, on a 200-XP curve (level 2 needs 566 more XP).
 */
@DisplayName("GangUpgradeCommand - /glw gang upgrade [confirm]")
class GangUpgradeCommandTest {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private BukkitStatics          statics;
	private MockedStatic<Settings> settings;
	private PluginManager          pluginManager;
	private GangMessages           messages;
	private Gang                   gang;
	private GangManager            gangManager;
	private Player                 player;
	private Argument               upgrade;
	private ConfirmArgument        confirm;

	@BeforeEach
	void setUp() throws IOException {
		GangSettings.bind(new FakeGangSettingsContract());
		IdentitySettings.bind(new FakeIdentitySettingsContract());
		GangLevels.bind(3, 200);
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
		statics = BukkitStatics.install();
		pluginManager = statics.pluginManager();
		settings = mockStatic(Settings.class, org.mockito.Mockito.CALLS_REAL_METHODS);
		settings.when(Settings::getMoneySymbol).thenReturn("$");
		settings.when(Settings::getLanguagePicked).thenReturn("en");
		Command.setInformationManager(mock(InformationManager.class));
		messages = W2P2Messages.shipped(tempDir);

		Map<Integer, LevelRowValues> rows = Map.of(
				0, new LevelRowValues(0, 8, 1, 1, "", List.of()),
				1, new LevelRowValues(20000, 10, 1, 1, "", List.of()),
				2, new LevelRowValues(40000, 12, 2, 2, "", List.of()),
				3, new LevelRowValues(0, 14, 2, 2, "", List.of()));
		GangLevelTable table = new GangLevelTable(() -> rows);

		gang = new Gang(1);
		gangManager = mock(GangManager.class);
		when(gangManager.getGang(1)).thenReturn(gang);
		when(gangManager.findById(1)).thenReturn(gang);
		GangLimits limits = new GangLimits(table, gangManager);

		UserManager<Player> userManager = mock(UserManager.class);
		MemberManager memberManager = mock(MemberManager.class);
		UUID playerId = UUID.randomUUID();
		player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(playerId);
		User<Player> user = mock(User.class);
		when(user.hasGang()).thenReturn(true);
		when(user.getGangId()).thenReturn(1);
		when(userManager.getUser(player)).thenReturn(user);
		Member member = mock(Member.class);
		when(member.getRank()).thenReturn(new Rank("owner", 1));
		when(memberManager.getMember(playerId)).thenReturn(member);

		upgrade = new GangUpgradeCommand(mock(JavaPlugin.class), new Tree<>(), mock(Argument.class), limits, table,
		                                 gangManager, userManager, memberManager, messages);
		confirm = (ConfirmArgument) upgrade.getNode().getChildren().get(0).getData();
	}

	@AfterEach
	void tearDown() {
		settings.close();
		statics.close();
		Command.setInformationManager(null);
		GangLevels.bind(20, 200);
	}

	private String money(long amount) {
		return Settings.formatAmount(BigDecimal.valueOf(amount));
	}

	private void bank(long amount) {
		gang.getEconomy().setAmount(BigDecimal.valueOf(amount));
	}

	private List<String> sent() {
		ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
		verify(player, atLeastOnce()).sendMessage(captor.capture());
		return captor.getAllValues();
	}

	private void preview() {
		upgrade.executeArgument(player, new String[]{"gang", "upgrade"});
	}

	private void confirm() {
		confirm.lock(player);
		confirm.executeArgument(player, new String[]{"gang", "upgrade", "confirm"});
	}

	@Test
	@DisplayName("xpShort_refusedWithBothAmounts: the preview names the XP and the money still missing")
	void xpShort_refusedWithBothAmounts() {
		gang.getLevel().addExperience(17, false, null);
		bank(5000);

		preview();

		String line = sent().get(0);
		assertTrue(line.contains("183"), line);
		assertTrue(line.contains(money(15000)), line);
		assertEquals(0, gang.getLevel().getLevelValue());
	}

	@Test
	@DisplayName("moneyShort: the XP bar is full, the bank is not")
	void moneyShort() {
		gang.getLevel().addExperience(200, false, null);
		bank(5000);

		preview();
		confirm();

		assertTrue(sent().get(0).contains(money(15000)));
		assertEquals(0, gang.getLevel().getLevelValue());
		assertEquals(0, BigDecimal.valueOf(5000).compareTo(gang.getEconomy().getAmount()));
	}

	@Test
	@DisplayName("bothMet_oneLevelOneFee: ready line first, then one level and one withdrawal of the fee")
	void bothMet_oneLevelOneFee() {
		gang.getLevel().addExperience(500, false, null);
		bank(50000);

		preview();
		String ready = sent().get(0);
		assertTrue(ready.contains(money(20000)), ready);
		assertTrue(ready.contains("/glw gang upgrade confirm"), ready);

		confirm();

		assertEquals(1, gang.getLevel().getLevelValue());
		assertEquals(300D, gang.getLevel().getExperience(), 1e-9);   // the bar was paid in XP, the rest is kept
		assertEquals(0, BigDecimal.valueOf(30000).compareTo(gang.getEconomy().getAmount()));
		verify(pluginManager).callEvent(any(GangLevelUpEvent.class));
	}

	@Test
	@DisplayName("addLevelsNeverBelowBar: core addLevels would level below the bar, the command must not")
	void addLevelsNeverBelowBar() {
		gang.getLevel().addExperience(199, false, null);
		bank(50000);

		confirm();

		assertEquals(0, gang.getLevel().getLevelValue());
		assertEquals(199D, gang.getLevel().getExperience(), 1e-9);
		assertEquals(0, BigDecimal.valueOf(50000).compareTo(gang.getEconomy().getAmount()));
		verify(pluginManager, never()).callEvent(any(Event.class));
	}

	@Test
	@DisplayName("moneyNeverAddsXp: a rich bank leaves the XP bar where it is")
	void moneyNeverAddsXp() {
		bank(900000);

		preview();
		confirm();

		assertEquals(0D, gang.getLevel().getExperience(), 1e-9);
		assertEquals(0, gang.getLevel().getLevelValue());
		assertTrue(sent().get(0).contains("200"), sent().get(0));
	}

	@Test
	@DisplayName("cancelledLevelUp_changesNothing: US-43, no level, no XP burnt, no fee")
	void cancelledLevelUp_changesNothing() {
		gang.getLevel().addExperience(500, false, null);
		bank(50000);
		doAnswer(invocation -> {
			((GangLevelUpEvent) invocation.getArgument(0)).setCancelled(true);
			return null;
		}).when(pluginManager).callEvent(any(Event.class));

		confirm();

		assertEquals(0, gang.getLevel().getLevelValue());
		assertEquals(500D, gang.getLevel().getExperience(), 1e-9);
		assertEquals(0, BigDecimal.valueOf(50000).compareTo(gang.getEconomy().getAmount()));
	}

	@Test
	@DisplayName("maxLevel_refused: one 'highest level' line, nothing changes")
	void maxLevel_refused() {
		gang.getLevel().addLevels(3, null);
		bank(900000);

		preview();
		confirm();

		List<String> lines = sent();
		assertEquals(2, lines.size());
		assertEquals(messages.text("Levels.Max", "x"), lines.get(0));
		assertEquals(3, gang.getLevel().getLevelValue());
		assertEquals(0, BigDecimal.valueOf(900000).compareTo(gang.getEconomy().getAmount()));
	}

	@Test
	@DisplayName("a rank without the upgrade node is refused and nothing is charged")
	void noPermission_refused() {
		Rank member = new Rank("member", 2);
		member.getNode().add(new Rank("officer", 3).getNode());
		MemberManager memberManager = mock(MemberManager.class);
		Member row = mock(Member.class);
		when(row.getRank()).thenReturn(member);
		when(memberManager.getMember(player.getUniqueId())).thenReturn(row);
		UserManager<Player> userManager = mock(UserManager.class);
		User<Player> user = mock(User.class);
		when(user.hasGang()).thenReturn(true);
		when(user.getGangId()).thenReturn(1);
		when(userManager.getUser(player)).thenReturn(user);
		GangLevelTable table = new GangLevelTable(() -> Map.of(1, new LevelRowValues(20000, 0, 0, 0, "", List.of())));
		upgrade = new GangUpgradeCommand(mock(JavaPlugin.class), new Tree<>(), mock(Argument.class),
		                                 new GangLimits(table, gangManager), table, gangManager, userManager,
		                                 memberManager, messages);
		confirm = (ConfirmArgument) upgrade.getNode().getChildren().get(0).getData();
		gang.getLevel().addExperience(500, false, null);
		bank(50000);

		confirm();

		assertEquals(messages.text("Levels.No_Permission", "x"), sent().get(0));
		assertEquals(0, gang.getLevel().getLevelValue());
		assertEquals(0, BigDecimal.valueOf(50000).compareTo(gang.getEconomy().getAmount()));
		assertTrue(!GangPermissions.allows(row, player, GangPermissions.UPGRADE));
	}

	@Test
	@DisplayName("costZero_autoLevels: with a free next row the XP levels the gang by itself, with a fee it only banks")
	@SuppressWarnings("unchecked")
	void costZero_autoLevels() {
		Map<Integer, LevelRowValues> free = Map.of(1, new LevelRowValues(0, 0, 0, 0, "", List.of()),
		                                           2, new LevelRowValues(0, 0, 0, 0, "", List.of()));
		GangManager auto = new GangManager(mock(IRepository.class), mock(GangAllianceRepositoryContract.class),
		                                   org.luckyraven.gangland.gang.contract.GangMigrationsContract.NONE,
		                                   new GangLevelTable(() -> free));
		Gang freeGang = new Gang(2);

		auto.addExperience(freeGang, 766);

		assertEquals(2, freeGang.getLevel().getLevelValue());

		Map<Integer, LevelRowValues> paid = Map.of(1, new LevelRowValues(20000, 0, 0, 0, "", List.of()));
		GangManager banking = new GangManager(mock(IRepository.class), mock(GangAllianceRepositoryContract.class),
		                                      org.luckyraven.gangland.gang.contract.GangMigrationsContract.NONE,
		                                      new GangLevelTable(() -> paid));
		Gang paidGang = new Gang(3);

		banking.addExperience(paidGang, 766);

		assertEquals(0, paidGang.getLevel().getLevelValue());
		assertEquals(766D, paidGang.getLevel().getExperience(), 1e-9);
	}
}
