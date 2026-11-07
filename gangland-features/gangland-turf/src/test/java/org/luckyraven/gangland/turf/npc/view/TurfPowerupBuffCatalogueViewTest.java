package org.luckyraven.gangland.turf.npc.view;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.gangland.turf.powerups.ActiveTurfBuff;
import org.luckyraven.gangland.turf.support.TurfTextFixture;
import org.luckyraven.gangland.turf.support.CaptureFixtures;
import org.luckyraven.gangland.turf.support.YamlFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.powerups.ActiveBuffManager;
import org.luckyraven.gangland.turf.powerups.EffectType;
import org.luckyraven.gangland.turf.powerups.PowerupDefinition;
import org.luckyraven.gangland.turf.powerups.PowerupRegistry;
import org.luckyraven.keystone.economy.EconomyHandler;
import org.luckyraven.keystone.inventory.flow.MenuFlow;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the boost catalogue paging (TF-18: nine per page, arrows only where a page exists) and the purchase
 * re-check it shares with the garrison panel (TF-19).
 */
@DisplayName("TurfPowerupBuffCatalogueView - paging and purchase re-check")
class TurfPowerupBuffCatalogueViewTest {

	private static List<PowerupDefinition> boosts(int count) {
		List<PowerupDefinition> defs = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			defs.add(new PowerupDefinition("b%02d".formatted(i), "&aBoost", BigDecimal.TEN,
			                               EffectType.INCOME_MULTIPLIER, 1.25, 60));
		}
		return defs;
	}

	private static List<String> ids(List<PowerupDefinition> defs) {
		return defs.stream().map(PowerupDefinition::id).toList();
	}

	@Test
	@DisplayName("25 boosts make two pages: 21 on the first, four on the second (TF-18)")
	void pages_twentyOneAPage() {
		List<PowerupDefinition> all = boosts(25);

		assertEquals(2, TurfPowerupBuffCatalogueView.pages(all.size()));
		assertEquals(21, TurfPowerupBuffCatalogueView.page(all, 0).size());
		assertEquals(List.of("b21", "b22", "b23", "b24"), ids(TurfPowerupBuffCatalogueView.page(all, 1)));
		assertEquals(1, TurfPowerupBuffCatalogueView.pages(0));
	}

	@Test
	@DisplayName("the next arrow shows only while a later page exists, the previous arrow only after page one")
	void arrows_onlyWhereAPageExists() {
		assertTrue(TurfPowerupBuffCatalogueView.hasNext(0, 2));
		assertFalse(TurfPowerupBuffCatalogueView.hasNext(1, 2));
		assertFalse(TurfPowerupBuffCatalogueView.hasPrevious(0));
		assertTrue(TurfPowerupBuffCatalogueView.hasPrevious(1));
	}

	@Test
	@DisplayName("an empty catalogue still has one (empty) page")
	void pages_emptyIsOnePage() {
		assertEquals(1, TurfPowerupBuffCatalogueView.pages(0));
	}

	@Test
	@DisplayName("a boost purchase after the turf changed hands is refused, the bank is untouched, the panel closes")
	void purchase_turfChangedHands_refused() {
		PowerupRegistry registry = new PowerupRegistry();
		ActiveBuffManager buffs  = mock(ActiveBuffManager.class);
		QuartermasterAccess access = mock(QuartermasterAccess.class);
		EconomyHandler bank = new EconomyHandler(null);
		bank.setAmount(BigDecimal.valueOf(50_000));
		Gang gang = mock(Gang.class);
		when(gang.getEconomy()).thenReturn(bank);
		Turf turf = mock(Turf.class);
		TurfPowerupFlowSession session = new TurfPowerupFlowSession(turf, gang, gang, "Quartermaster");
		Player viewer = mock(Player.class);
		@SuppressWarnings("unchecked")
		MenuFlow<TurfPowerupFlowSession> flow = mock(MenuFlow.class);
		when(access.denyKey(viewer, session)).thenReturn("Quartermaster.Owner_Changed");

		TurfPowerupBuffCatalogueView view = new TurfPowerupBuffCatalogueView(registry, buffs, access,
		                                                                     mock(TurfMessages.class), null,
		                                                                     () -> MenuStyle.DEFAULT);
		view.attemptBuy(flow, viewer, session, boosts(25).get(0));

		verify(flow).end();
		assertEquals(0, new BigDecimal("50000").compareTo(bank.getAmount()));
		verify(buffs, never()).activate(org.mockito.ArgumentMatchers.anyInt(), any());
	}

	@Test
	@DisplayName("a boost purchase refused for a reason that keeps the panel open shows the line, then re-renders")
	void purchase_noRank_refusedThenRerendered() {
		ActiveBuffManager buffs  = mock(ActiveBuffManager.class);
		QuartermasterAccess access = mock(QuartermasterAccess.class);
		EconomyHandler bank = new EconomyHandler(null);
		bank.setAmount(BigDecimal.valueOf(50_000));
		Gang gang = mock(Gang.class);
		when(gang.getEconomy()).thenReturn(bank);
		TurfPowerupFlowSession session = new TurfPowerupFlowSession(mock(Turf.class), gang, gang, "Quartermaster");
		Player viewer = mock(Player.class);
		@SuppressWarnings("unchecked")
		MenuFlow<TurfPowerupFlowSession> flow = mock(MenuFlow.class);
		TurfMessages messages = mock(TurfMessages.class);
		when(messages.text(eq(QuartermasterAccess.NO_RANK), anyString())).thenReturn("no rank line");
		when(access.denyKey(viewer, session)).thenReturn(QuartermasterAccess.NO_RANK);

		TurfPowerupBuffCatalogueView view = new TurfPowerupBuffCatalogueView(new PowerupRegistry(), buffs, access,
		                                                                     messages, null, () -> MenuStyle.DEFAULT);
		view.attemptBuy(flow, viewer, session, boosts(25).get(0));

		org.mockito.InOrder order = org.mockito.Mockito.inOrder(viewer, flow);
		order.verify(viewer).sendMessage("no rank line");
		order.verify(flow).rerender();
		verify(flow, never()).end();
		assertEquals(0, new BigDecimal("50000").compareTo(bank.getAmount()));
		verify(buffs, never()).activate(org.mockito.ArgumentMatchers.anyInt(), any());
	}

	// ---- menu pass (task M) -------------------------------------------------------------------------------

	private static final int TURF_ID = 3;

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private static PowerupRegistry registryOf(PowerupDefinition... defs) {
		PowerupRegistry                  registry = new PowerupRegistry();
		Map<String, PowerupDefinition> map      = new LinkedHashMap<>();
		for (PowerupDefinition def : defs) {
			map.put(def.id(), def);
		}
		registry.replaceAll(map);
		return registry;
	}

	private static PowerupRegistry registryOf(List<PowerupDefinition> defs) {
		return registryOf(defs.toArray(new PowerupDefinition[0]));
	}

	private static TurfPowerupFlowSession sessionWithBank(long money) {
		EconomyHandler bank = new EconomyHandler(null);
		bank.setAmount(BigDecimal.valueOf(money));
		Gang gang = mock(Gang.class);
		when(gang.getEconomy()).thenReturn(bank);
		Turf turf = mock(Turf.class);
		when(turf.getId()).thenReturn(TURF_ID);
		when(turf.getDisplayName()).thenReturn("Docks");
		return new TurfPowerupFlowSession(turf, gang, gang, "Quartermaster");
	}

	private static TurfMessageContract durations() {
		TurfMessageContract durations = mock(TurfMessageContract.class);
		when(durations.formatDuration(anyLong())).thenAnswer(invocation -> "D" + invocation.getArgument(0));
		return durations;
	}

	private static TurfPowerupBuffCatalogueView realView(PowerupRegistry registry, ActiveBuffManager buffs,
	                                                     TurfMessages messages) {
		return new TurfPowerupBuffCatalogueView(registry, buffs, mock(QuartermasterAccess.class), messages,
		                                        durations(), () -> MenuStyle.DEFAULT);
	}

	private static TurfPowerupBuffCatalogueView plainView(PowerupRegistry registry) {
		ActiveBuffManager buffs = mock(ActiveBuffManager.class);
		when(buffs.active(TURF_ID)).thenReturn(List.of());
		TurfMessages messages = mock(TurfMessages.class);
		when(messages.text(anyString(), anyString())).thenAnswer(invocation -> invocation.getArgument(0));
		when(messages.text(anyString(), anyString(), any(Object[].class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
		return realView(registry, buffs, messages);
	}

	private static PowerupDefinition boost(String id, EffectType type, double magnitude) {
		return new PowerupDefinition(id, "&6" + id, BigDecimal.valueOf(500), type, magnitude, 600);
	}

	@Test
	@DisplayName("render_setsBorderAndFill: 5 rows, six boosts on 10-15, Back 36, Close 44, no page cells")
	void render_setsBorderAndFill() {
		PowerupRegistry registry = registryOf(boosts(6));

		try (PanelProbe probe = new PanelProbe(plainView(registry), sessionWithBank(0))) {
			assertEquals(5, probe.rows);
			assertTrue(probe.border, "border");
			assertTrue(probe.fill, "fill");
			assertEquals(Set.of(10, 11, 12, 13, 14, 15, 36, 44), probe.items.keySet());
			assertEquals(Material.ARROW, probe.type(36));
			assertEquals(Material.BARRIER, probe.type(44));
		}
	}

	@Test
	@DisplayName("render_pageOneOf25: 21 boosts on 10-16, 19-25 and 28-34, page info 40, Next 42, no Previous")
	void render_pageOneOf25() {
		PowerupRegistry registry = registryOf(boosts(25));

		try (PanelProbe probe = new PanelProbe(plainView(registry), sessionWithBank(0))) {
			Set<Integer> expected = new TreeSet<>(Set.of(36, 40, 42, 44));
			for (int row : new int[]{9, 18, 27}) {
				for (int column = 1; column <= 7; column++) {
					expected.add(row + column);
				}
			}
			assertEquals(expected, probe.items.keySet());
			assertEquals(Material.PAPER, probe.type(40));
			assertEquals(Material.ARROW, probe.type(42));
		}
	}

	@Test
	@DisplayName("render_pageTwoOf25: four boosts, Previous on 38, no Next")
	void render_pageTwoOf25() {
		PowerupRegistry registry = registryOf(boosts(25));
		TurfPowerupFlowSession session = sessionWithBank(0);
		session.setBuffPage(1);

		try (PanelProbe probe = new PanelProbe(plainView(registry), session)) {
			assertEquals(Set.of(10, 11, 12, 13, 36, 38, 40, 44), probe.items.keySet());
			assertEquals(Material.ARROW, probe.type(38));
		}
	}

	@Test
	@DisplayName("oldFile_titleAndDuration: an old %npc% title and %seconds% line still render without literals")
	void oldFile_titleAndDuration() throws Exception {
		String old = "Quartermaster:" + System.lineSeparator()
		             + "   Title_Buffs: \"&8&l[&b&lBuffs &8- &6%npc%&8&l]\"" + System.lineSeparator()
		             + "   Buffs_Duration: \"&7Duration: &b%seconds%s\"" + System.lineSeparator();
		try (var settings = TurfTextFixture.stubMoneySymbol()) {
			Path dir = Files.createDirectories(tempDir.resolve("old" + System.nanoTime()));
			TurfMessages messages = new TurfMessages(YamlFixture.fileManager(dir, "turf_messages", old),
			                                         mock(TurfMessageContract.class),
			                                         CaptureFixtures.settings(180, 15, 15, 10, new int[]{25, 50, 75}, 90, 90));
			ActiveBuffManager buffs = mock(ActiveBuffManager.class);
			when(buffs.active(TURF_ID)).thenReturn(List.of());
			TurfPowerupBuffCatalogueView view = realView(registryOf(boost("x", EffectType.INCOME_MULTIPLIER, 1.5)),
			                                             buffs, messages);
			TurfPowerupFlowSession session = sessionWithBank(0);

			assertTrue(!view.title(session).contains("%npc%"), view.title(session));
			try (PanelProbe probe = new PanelProbe(view, session)) {
				List<String> lore = probe.lore(probe.items.keySet().iterator().next());
				assertTrue(lore.stream().anyMatch(line -> line.contains("600s")), lore.toString());
			}
		}
	}

	@Test
	@DisplayName("title_passesNpcPair: the buff title carries the Quartermaster name for old files")
	void title_passesNpcPair() {
		TurfMessages messages = mock(TurfMessages.class);
		TurfPowerupBuffCatalogueView view = realView(registryOf(boosts(1)), mock(ActiveBuffManager.class), messages);
		view.title(sessionWithBank(0));
		verify(messages).text("Quartermaster.Title_Buffs", "&5Timed Buffs", "npc", "Quartermaster");
	}

	@Test
	@DisplayName("render_noAttributeIcons: no sword, helmet or potion on any slot")
	void render_noAttributeIcons() {
		PowerupRegistry registry = registryOf(boost("a", EffectType.INCOME_MULTIPLIER, 1.5),
		                                      boost("b", EffectType.GUARD_TOUGHNESS, 1.5));

		try (PanelProbe probe = new PanelProbe(plainView(registry), sessionWithBank(0))) {
			assertTrue(probe.noAttributeIcons(), probe.items.toString());
		}
	}

	@Test
	@DisplayName("icon_fromDefinition: the slot shows the definition's icon, else the effect's default")
	void icon_fromDefinition() {
		PowerupDefinition custom = new PowerupDefinition("c", "&6c", BigDecimal.TEN, EffectType.INCOME_MULTIPLIER,
		                                                 1.5, 60, "DIAMOND", List.of());
		PowerupRegistry registry = registryOf(custom, boost("d", EffectType.FASTER_REINFORCEMENTS, 0.5));

		try (PanelProbe probe = new PanelProbe(plainView(registry), sessionWithBank(0))) {
			assertEquals(custom.iconMaterial(), probe.type(10));
			assertEquals(Material.DIAMOND, probe.type(10));
			assertEquals(Material.CLOCK, probe.type(11));
		}
	}

	/** The lore of the only boost in a catalogue, built from the in-code fallbacks. */
	private List<String> loreOf(PowerupDefinition def, ActiveBuffManager buffs) throws Exception {
		try (var settings = TurfTextFixture.stubMoneySymbol()) {
			TurfMessages messages = TurfTextFixture.emptyMessages(
					Files.createDirectories(tempDir.resolve("m" + System.nanoTime())),
					mock(TurfMessageContract.class));
			try (PanelProbe probe = new PanelProbe(realView(registryOf(def), buffs, messages), sessionWithBank(0))) {
				return probe.lore(probe.items.keySet().iterator().next());
			}
		}
	}

	private List<String> idleLoreOf(PowerupDefinition def) throws Exception {
		ActiveBuffManager buffs = mock(ActiveBuffManager.class);
		when(buffs.active(TURF_ID)).thenReturn(List.of());
		return loreOf(def, buffs);
	}

	private void assertFriendly(EffectType type, double magnitude, String expected) throws Exception {
		List<String> lore = idleLoreOf(boost("x", type, magnitude));

		assertTrue(lore.stream().anyMatch(line -> line.replaceAll("[§&][0-9a-fk-or]", "").equals(expected)), expected + " in " + lore);
		assertTrue(lore.stream().noneMatch(line -> line.contains("_") || line.contains(type.name())), lore.toString());
	}

	@Test
	@DisplayName("effectLine_friendlyWording_noEnumName: income 1.5 reads +50%")
	void effectLine_income() throws Exception {
		assertFriendly(EffectType.INCOME_MULTIPLIER, 1.5, "Effect: +50% turf income");
	}

	@Test
	@DisplayName("effectLine_friendlyWording_noEnumName: discount 0.5 reads 50% off")
	void effectLine_discount() throws Exception {
		assertFriendly(EffectType.GARRISON_DISCOUNT, 0.5, "Effect: 50% off new guards");
	}

	@Test
	@DisplayName("effectLine_friendlyWording_noEnumName: toughness 1.5 reads +50%")
	void effectLine_toughness() throws Exception {
		assertFriendly(EffectType.GUARD_TOUGHNESS, 1.5, "Effect: +50% health on your guards while it runs");
	}

	@Test
	@DisplayName("effectLine_friendlyWording_noEnumName: waves 1.5 reads +50%")
	void effectLine_waves() throws Exception {
		assertFriendly(EffectType.BIGGER_GUARD_WAVES, 1.5, "Effect: +50% guards fighting at once in a contest");
	}

	@Test
	@DisplayName("effectLine_friendlyWording_noEnumName: reinforcements 0.5 reads 50% sooner")
	void effectLine_reinforcements() throws Exception {
		assertFriendly(EffectType.FASTER_REINFORCEMENTS, 0.5, "Effect: Replacements arrive 50% sooner");
	}

	@Test
	@DisplayName("the lore follows DESIGN 6.4: description, blank, effect, duration in time words, cost")
	void lore_order() throws Exception {
		PowerupDefinition def = new PowerupDefinition("x", "&6x", BigDecimal.valueOf(500),
		                                              EffectType.INCOME_MULTIPLIER, 1.5, 600, null,
		                                              List.of("&7Pays more."));

		List<String> lore = idleLoreOf(def);

		assertTrue(lore.get(0).contains("Pays more."), lore.toString());
		assertEquals(" ", lore.get(1));
		assertTrue(lore.get(2).contains("Effect"), lore.toString());
		assertTrue(lore.get(3).contains("Duration") && lore.get(3).contains("D600"), lore.toString());
		assertTrue(lore.get(4).contains("Cost"), lore.toString());
	}

	@Test
	@DisplayName("activeLine_showsTimeLeft: two buffs of one powerup show the longest remaining, plus the stack line")
	void activeLine_showsTimeLeft() throws Exception {
		long now = System.currentTimeMillis();
		ActiveBuffManager buffs = mock(ActiveBuffManager.class);
		when(buffs.active(TURF_ID)).thenReturn(List.of(
				new ActiveTurfBuff(1, TURF_ID, "x", EffectType.INCOME_MULTIPLIER, 1.5, now + 60_000L),
				new ActiveTurfBuff(2, TURF_ID, "x", EffectType.INCOME_MULTIPLIER, 1.5, now + 300_000L),
				new ActiveTurfBuff(3, TURF_ID, "other", EffectType.INCOME_MULTIPLIER, 1.5, now + 900_000L)));

		List<String> lore = loreOf(boost("x", EffectType.INCOME_MULTIPLIER, 1.5), buffs);

		assertTrue(lore.stream().anyMatch(line -> line.matches(".*Active: .*D(299|300).*left.*")), lore.toString());
		assertTrue(lore.stream().anyMatch(line -> line.contains("Buying again stacks")), lore.toString());
	}

	@Test
	@DisplayName("no active line and no stack line while the powerup is not running")
	void activeLine_absentWhenIdle() throws Exception {
		List<String> lore = idleLoreOf(boost("x", EffectType.INCOME_MULTIPLIER, 1.5));

		assertTrue(lore.stream().noneMatch(line -> line.contains("Active") || line.contains("stacks")),
		           lore.toString());
	}

	@Test
	@DisplayName("bought_namesDisplayName: Bought_Boost receives the display name, not the id")
	void bought_namesDisplayName() {
		PowerupDefinition def = boost("small_income", EffectType.INCOME_MULTIPLIER, 1.5);
		ActiveBuffManager buffs = mock(ActiveBuffManager.class);
		TurfMessages messages = mock(TurfMessages.class);
		TurfPowerupFlowSession session = sessionWithBank(10_000);
		Player viewer = mock(Player.class);
		@SuppressWarnings("unchecked")
		MenuFlow<TurfPowerupFlowSession> flow = mock(MenuFlow.class);

		realView(registryOf(def), buffs, messages).attemptBuy(flow, viewer, session, def);

		verify(messages).text(eq("Quartermaster.Bought_Boost"), anyString(), eq("boost"), eq("&6small_income"),
		                      eq("turf"), eq("Docks"));
	}
}
