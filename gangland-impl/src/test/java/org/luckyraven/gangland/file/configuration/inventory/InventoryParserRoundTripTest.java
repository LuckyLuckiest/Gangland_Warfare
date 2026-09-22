package org.luckyraven.gangland.file.configuration.inventory;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.core.testsupport.BukkitRegistryFixture;
import org.luckyraven.gangland.data.placeholder.PlaceholderService;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.menu.InventoryBuilder;
import org.luckyraven.gangland.menu.condition.ConditionEvaluator;
import org.luckyraven.gangland.menu.multi.ItemSourceEntry;
import org.luckyraven.gangland.menu.multi.ItemSourceProvider;
import org.luckyraven.keystone.cooldown.CooldownService;
import org.luckyraven.keystone.inventory.InventoryService;
import org.luckyraven.keystone.inventory.chest.ChestMenu;
import org.luckyraven.keystone.inventory.registry.MenuOpener;
import org.luckyraven.keystone.item.ItemParser;
import org.luckyraven.keystone.item.nbt.ItemNbtAccessor;
import org.luckyraven.keystone.item.nbt.NbtBridge;
import org.luckyraven.keystone.item.nbt.NbtType;
import org.luckyraven.keystone.permission.PermissionManager;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.luckyraven.keystone.testkit.PluginMocks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Round-trips each of the 9 core YAML menus under {@code gangland-impl/src/main/resources/inventory/} through
 * {@link InventoryRuntimeContext#registerInventory}/{@link InventoryBuilder#createMenu}/{@code createPagedMenu} —
 * the WS2 G3 retarget onto Keystone's {@code ChestMenuBuilder}. Pins: slot count (rows), a sample of item
 * materials/names per menu, and at least one handler binding (a click dispatched through
 * {@link ChestMenu#dispatchClick} produces the YAML-declared effect — a command run or another menu opened via the
 * {@link MenuOpener} captured at parse time).
 *
 * <p>None of the 9 core menus declare {@code Draggable: true} on any slot (verified by inspection of every YAML file
 * under {@code inventory/} — grep for {@code Draggable} returns zero hits), so none of them have an interactive
 * slot and the keystone-inventory item-return contract's "interactive non-ItemHoldingComponent slots discard player
 * items on close" caveat does not apply to any core menu.
 */
class InventoryParserRoundTripTest {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private static final Path RESOURCES = Path.of("src/main/resources/inventory");

	private JavaPlugin              plugin;
	private InventoryDefinitionStore definitionStore;
	private InventoryRuntimeContext context;
	private InventoryService        inventoryService;
	private MenuOpener              opener;
	private Player                  player;

	@BeforeAll
	static void bootstrapBukkitRegistry() {
		BukkitRegistryFixture.install();
		// XMaterial$Data's clinit parses a real NMS version out of Bukkit.getServer().getVersion() — the
		// fixture's own "test" placeholder doesn't parse and permanently poisons the class for the whole fork
		// (a failed static initializer stays failed). Re-stub the shared mock server with a real version string
		// before any XMaterial.valueOf/get call in this test class forces the class to load.
		org.bukkit.Server server = Bukkit.getServer();
		if (server != null) {
			when(server.getVersion()).thenReturn("git-Paper-1 (MC: 1.21.1)");
		}
	}

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		// The real NBT-API accessor reflects into com.mojang.authlib.GameProfile, which a plain unit-test JVM
		// doesn't have on its classpath (it's a server-runtime class). SlotItemFactory.create/ItemBuilder.hasNBTTag
		// touch it for every item (color/head tag checks), so swap in an in-memory fake for this test — same
		// fixture shape as ItemDefinitionSimilarityTest's PerStackNbtAccessor.
		NbtBridge.install(new InMemoryNbtAccessor());

		// InventoryRuntimeContext.openInventoryForPlayer reads Settings.getInventoryFillName/Item directly (a
		// real navigation triggered by registerNavigationTarget's click tests goes through it) — Settings' ~200
		// fields are process-wide statics that must be initialized once (documentation/TESTING.md §4).
		org.luckyraven.gangland.support.SettingsFixture.initializeMinimal(tempDir);

		plugin           = PluginMocks.plugin(tempDir);
		definitionStore  = new InventoryDefinitionStore(plugin);
		inventoryService = new InventoryService(mock(CooldownService.class));
		opener           = mock(MenuOpener.class);

		Gangland gangland = mock(Gangland.class);

		UUID uuid = UUID.randomUUID();
		player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(uuid);
		when(player.hasPermission(anyString())).thenReturn(true);

		User<Player> user = mock(User.class);
		when(user.getUuid()).thenReturn(uuid);
		when(user.getUser()).thenReturn(player);

		UserManager<Player> userManager = mock(UserManager.class);
		when(userManager.getUser(player)).thenReturn(user);

		// Pass-through placeholder resolver — the round-trip is about the parse->build pipeline, not the
		// placeholder catalogue (that's PlaceholderService's own test coverage).
		PlaceholderService placeholderService = mock(PlaceholderService.class);
		when(placeholderService.convert(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));

		// The evaluator always takes the True branch — every core menu's Condition slot is exercised, matching
		// how the shipped YAML is expected to render for a normal player (e.g. %gangland_user_has-bank%).
		ConditionEvaluator conditionEvaluator = mock(ConditionEvaluator.class);
		when(conditionEvaluator.evaluate(any(), any())).thenReturn(true);

		ItemParser itemParser = mock(ItemParser.class); // no prefixed item refs in the 9 core menus

		// Empty for every source: the shipped Item_Template for every paginated core menu is Type: PLAYER_HEAD,
		// which needs com.mojang.authlib (XSkull) — absent from a plain unit-test JVM. registerNavigationTarget
		// drives real navigation through this context (e.g. phone -> phone_gang -> ... -> user_stat), so this
		// provider backs every such path, not just the paginated menus' own direct tests (which build their own
		// separate provider where non-empty coverage is safe, e.g. allianceStat's REDSTONE template).
		ItemSourceProvider itemSourceProvider = (p, source) -> java.util.List.of();

		context = new InventoryRuntimeContext(gangland, definitionStore, itemSourceProvider, conditionEvaluator,
		                                      userManager, mock(PermissionManager.class), placeholderService,
		                                      itemParser, inventoryService);
	}

	@AfterEach
	void tearDown() {
		NbtBridge.reset();
	}

	/** In-memory {@link ItemNbtAccessor} — same shape as {@code ItemDefinitionSimilarityTest}'s own fixture. */
	private static final class InMemoryNbtAccessor implements ItemNbtAccessor {

		private final Map<ItemStack, Map<String, Object>> storage = new IdentityHashMap<>();

		private Map<String, Object> tagsOf(ItemStack stack) {
			return storage.computeIfAbsent(stack, s -> new HashMap<>());
		}

		@Override
		public boolean isAvailable() {
			return true;
		}

		@Override
		public boolean has(ItemStack stack, String tag) {
			return tagsOf(stack).containsKey(tag);
		}

		@Override
		@Nullable
		public Object get(ItemStack stack, String tag) {
			return tagsOf(stack).get(tag);
		}

		@Override
		@Nullable
		public String getString(ItemStack stack, String tag) {
			Object value = tagsOf(stack).get(tag);
			return value == null ? null : String.valueOf(value);
		}

		@Override
		public int getInt(ItemStack stack, String tag) {
			Object value = tagsOf(stack).get(tag);
			return value instanceof Number number ? number.intValue() : 0;
		}

		@Override
		public void set(ItemStack stack, String tag, NbtType type, @Nullable Object value) {
			tagsOf(stack).put(tag, value);
		}

		@Override
		public void remove(ItemStack stack, String tag) {
			tagsOf(stack).remove(tag);
		}

		@Override
		@Nullable
		public String describe(ItemStack stack) {
			return tagsOf(stack).toString();
		}
	}

	private static ItemSourceEntry entry(String key, String value) {
		return new ItemSourceEntry(Map.of(key, value));
	}

	// ---- shared round-trip machinery ----

	private FileHandler load(String name) throws IOException {
		Path source = RESOURCES.resolve(name + ".yml");
		Path copy   = tempDir.resolve(name + ".yml");
		Files.copy(source, copy);
		return new FileHandler(plugin, copy.toFile());
	}

	private static Inventory fakeInventory(InventoryHolder holder, int size) {
		ItemStack[] contents = new ItemStack[size];
		Inventory   inv      = mock(Inventory.class);
		when(inv.getHolder()).thenReturn(holder);
		when(inv.getSize()).thenReturn(size);
		when(inv.getItem(anyInt())).thenAnswer(invocation -> {
			int idx = invocation.getArgument(0);
			return idx >= 0 && idx < contents.length ? contents[idx] : null;
		});
		doAnswer(invocation -> {
			int       idx  = invocation.getArgument(0);
			ItemStack item = invocation.getArgument(1);
			if (idx >= 0 && idx < contents.length) contents[idx] = item;
			return null;
		}).when(inv).setItem(anyInt(), any());
		return inv;
	}

	private static BukkitStatics installBukkit() {
		BukkitStatics bukkit = BukkitStatics.install();
		// BukkitStatics.install() fully mocks the static Bukkit class for its scope, so any method it doesn't
		// stub itself (getItemFactory among them) answers null instead of falling through to the real server
		// BukkitRegistryFixture installed outside this block — stub it explicitly, same as Keystone's own
		// PagedRegionTest.
		bukkit.statics().when(Bukkit::getItemFactory).thenReturn(mock(ItemFactory.class));
		bukkit.statics().when(() -> Bukkit.createInventory(any(InventoryHolder.class), anyInt(), anyString()))
		      .thenAnswer(invocation -> fakeInventory(invocation.getArgument(0), invocation.getArgument(1)));
		return bukkit;
	}

	/** Opens {@code player} into {@code menu} inside a faked Bukkit — needed because {@code build()} alone does not
	 *  render (rendering happens at {@code open(Player)}). */
	private static void open(ChestMenu menu, Player player) {
		InventoryView view = mock(InventoryView.class);
		when(player.getOpenInventory()).thenReturn(view);
		when(view.getTopInventory()).thenReturn(mock(Inventory.class));
		menu.open(player);
	}

	private static InventoryClickEvent clickEvent(ChestMenu menu, int slot) {
		// Resolve the current item BEFORE opening any when(...) chain — evaluating another mock's method (the
		// fake Inventory's getItem) in between when(event.getCurrentItem()) and .thenReturn(...) corrupts
		// Mockito's ongoing-stubbing state (its thread-local tracks the *last* mock invocation).
		ItemStack currentItem = menu.bukkitInventory().getItem(slot);

		InventoryClickEvent event = mock(InventoryClickEvent.class);
		when(event.getRawSlot()).thenReturn(slot);
		when(event.getClick()).thenReturn(ClickType.LEFT);
		when(event.getCurrentItem()).thenReturn(currentItem);
		return event;
	}

	private ChestMenu registerAndBuild(String name) throws IOException {
		return registerAndBuild(name, menu -> { });
	}

	/**
	 * {@code withinBukkitScope} runs INSIDE the same {@code BukkitStatics} try-with-resources this method opens to
	 * build and open the menu — required for a test that also dispatches a click causing a real internal
	 * navigation (via {@code context::openInventoryForPlayer}, baked into regular/static slots' handlers at parse
	 * time): that navigation calls {@code Bukkit.createInventory} again to build the destination menu, and the
	 * scope closes the instant this method would otherwise return, taking the faked answer with it.
	 */
	private ChestMenu registerAndBuild(String name, java.util.function.Consumer<ChestMenu> withinBukkitScope)
			throws IOException {
		context.registerInventory(load(name));
		InventoryBuilder builder = definitionStore.getInventory(name);
		assertNotNull(builder, name + " must be registered in the definition store");

		try (BukkitStatics ignored = installBukkit()) {
			ChestMenu menu = builder.createMenu(inventoryService, plugin, placeholderOf(), player,
			                                    InventoryBuilder.DEFAULT_FILL_ITEM, InventoryBuilder.DEFAULT_FILL_NAME,
			                                    InventoryBuilder.DEFAULT_LINE_ITEM, InventoryBuilder.DEFAULT_LINE_NAME,
			                                    conditionEvaluatorOf(), opener);
			open(menu, player);
			withinBukkitScope.accept(menu);
			return menu;
		}
	}

	// Pass-through, except %gangland_gang_color% (gang_info.yml slots 31/33 feed it into a Color: YAML key, which
	// needs a real color name to resolve a material — everything else passes through unresolved).
	private org.luckyraven.keystone.util.Placeholder placeholderOf() {
		return (p, raw) -> raw == null ? null : raw.replace("%gangland_gang_color%", "GREEN");
	}

	private ConditionEvaluator conditionEvaluatorOf() {
		ConditionEvaluator evaluator = mock(ConditionEvaluator.class);
		when(evaluator.evaluate(any(), any())).thenReturn(true);
		return evaluator;
	}

	/**
	 * Registers {@code targetMenu} as a real navigation destination. A regular/static slot's click handler is
	 * built at PARSE time (menu.handler.*) with {@code context::openInventoryForPlayer} baked in as its
	 * {@code MenuOpener} — not the separate {@code opener} mock passed to {@code createMenu}/{@code createPagedMenu}
	 * (that one only backs a CONDITIONAL slot's raw action, resolved lazily at build time). So the real proof a
	 * click navigated is a second live {@code player.openInventory(...)} call, once {@code targetMenu} is actually
	 * registered for {@code context} to find.
	 */
	private void registerNavigationTarget(String targetMenu) throws IOException {
		context.registerInventory(load(targetMenu));
	}

	// ---- the 9 core menus ----

	@Test
	@DisplayName("phone.yml — 6 rows, wallet/bank/bounty/gang slots, OnClick.Inventory navigates via MenuOpener")
	void phone() throws IOException {
		registerNavigationTarget("phone_gang");

		registerAndBuild("phone", menu -> {
			assertEquals(54, menu.bukkitInventory().getSize());
			// Type: LEATHER_CHESTPLATE + Color: GREEN goes through the same color-swap path as the old
			// InventoryBuilder.createInventory: "LEATHER_CHESTPLATE" doesn't contain any MaterialType name, so the
			// resolver falls back to its WOOL default and yields GREEN_WOOL — pinned here exactly as ported, not as
			// a new design choice.
			assertEquals(Material.GREEN_WOOL, menu.bukkitInventory().getItem(20).getType());
			assertEquals(Material.FURNACE, menu.bukkitInventory().getItem(22).getType());
			assertEquals(Material.PAPER, menu.bukkitInventory().getItem(24).getType());
			assertEquals(Material.EMERALD, menu.bukkitInventory().getItem(40).getType());

			// slot 20 -> OnClick.Inventory: phone_gang
			menu.dispatchClick(clickEvent(menu, 20), player);
			verify(player, times(2)).openInventory(any(Inventory.class));
		});
	}

	@Test
	@DisplayName("gang_info.yml — 5 rows, balance/id/members slots, OnClick.Inventory navigates to user_stat")
	void gangInfo() throws IOException {
		registerNavigationTarget("user_stat");

		registerAndBuild("gang_info", menu -> {
			assertEquals(45, menu.bukkitInventory().getSize());
			assertEquals(Material.GOLD_BLOCK, menu.bukkitInventory().getItem(11).getType());
			assertEquals(Material.CRAFTING_TABLE, menu.bukkitInventory().getItem(13).getType());
			assertTrue(menu.bukkitInventory().getItem(19).getType().name().contains("HEAD")
			           || menu.bukkitInventory().getItem(19).getType() == Material.PLAYER_HEAD);

			menu.dispatchClick(clickEvent(menu, 19), player);
			verify(player, times(2)).openInventory(any(Inventory.class));
		});
	}

	@Test
	@DisplayName("gang_stat.yml — 6 rows, no slots, fill-only")
	void gangStat() throws IOException {
		ChestMenu menu = registerAndBuild("gang_stat");

		assertEquals(54, menu.bukkitInventory().getSize());
	}

	@Test
	@DisplayName("phone_banking.yml — 3 rows, Condition-driven bank slot renders the True branch")
	void phoneBanking() throws IOException {
		registerNavigationTarget("phone");

		registerAndBuild("phone_banking", menu -> {
			assertEquals(27, menu.bukkitInventory().getSize());
			// slot 4 has a True/False Condition on %gangland_user_has-bank%; the evaluator stub always returns
			// true, so the BOOK item (both branches use BOOK) renders with the True-branch name.
			assertEquals(Material.BOOK, menu.bukkitInventory().getItem(4).getType());

			// slot 22 -> OnClick.Inventory: phone (fixed action, not conditional)
			menu.dispatchClick(clickEvent(menu, 22), player);
			verify(player, times(2)).openInventory(any(Inventory.class));
		});
	}

	@Test
	@DisplayName("phone_bounty.yml — 6 rows, static bounty slots, Back button navigates to phone")
	void phoneBounty() throws IOException {
		registerNavigationTarget("phone");

		registerAndBuild("phone_bounty", menu -> {
			assertEquals(54, menu.bukkitInventory().getSize());
			assertEquals(Material.ENCHANTED_BOOK, menu.bukkitInventory().getItem(21).getType());
			assertEquals(Material.EMERALD, menu.bukkitInventory().getItem(40).getType());

			menu.dispatchClick(clickEvent(menu, 49), player);
			verify(player, times(2)).openInventory(any(Inventory.class));
		});
	}

	@Test
	@DisplayName("phone_gang.yml — 5 rows, Condition-driven gang slot, Search Gang navigates")
	void phoneGang() throws IOException {
		registerNavigationTarget("phone_gang_search");

		registerAndBuild("phone_gang", menu -> {
			assertEquals(45, menu.bukkitInventory().getSize());
			assertEquals(Material.SLIME_BALL, menu.bukkitInventory().getItem(21).getType());
			assertEquals(Material.BOOKSHELF, menu.bukkitInventory().getItem(23).getType());

			menu.dispatchClick(clickEvent(menu, 23), player);
			verify(player, times(2)).openInventory(any(Inventory.class));
		});
	}

	@Test
	@DisplayName("phone_gang_search.yml — paginated, static filter buttons render, page suffix on the title")
	void phoneGangSearch() throws IOException {
		context.registerInventory(load("phone_gang_search"));
		InventoryBuilder builder = definitionStore.getInventory("phone_gang_search");
		assertNotNull(builder);

		var buttonTags = new org.luckyraven.gangland.menu.part.ButtonTags("prev", "home", "next");

		try (BukkitStatics ignored = installBukkit()) {
			// Empty item source: this YAML's Item_Template is Type: PLAYER_HEAD + a placeholder Data tag, which
			// needs com.mojang.authlib (XSkull) — absent from a plain unit-test JVM (only a real server jar
			// carries it), and the module pom may never grow a test dependency to add it (documentation/TESTING.md
			// §1). An empty source list means renderTemplateEntries' map never actually runs the per-entry
			// resolver, so this still exercises real coverage: registration, PageConfig with 0 entries, the static
			// filter buttons (none of them are player heads) and their click bindings, and decoration.
			ItemSourceProvider provider = (p, source) -> java.util.List.of();

			ChestMenu menu = builder.createPagedMenu(inventoryService, plugin, placeholderOf(), player,
			                                         conditionEvaluatorOf(), InventoryBuilder.DEFAULT_FILL_ITEM,
			                                         InventoryBuilder.DEFAULT_FILL_NAME, buttonTags, provider, opener, 0);
			open(menu, player);

			// 54-slot region -> rows clamp to 6; static filter buttons at slots 9/18/19/27/28/36 per the YAML.
			assertEquals(54, menu.bukkitInventory().getSize());
			assertEquals(Material.ARROW, menu.bukkitInventory().getItem(9).getType());
			assertEquals(Material.NAME_TAG, menu.bukkitInventory().getItem(18).getType());
			assertEquals(Material.BARRIER, menu.bukkitInventory().getItem(27).getType());

			// slot 9 -> OnClick.Inventory: phone_gang
			registerNavigationTarget("phone_gang");
			menu.dispatchClick(clickEvent(menu, 9), player);
			verify(player, times(2)).openInventory(any(Inventory.class));
		}
	}

	@Test
	@DisplayName("user_stat.yml — paginated gang-members list, static filter buttons render")
	void userStat() throws IOException {
		context.registerInventory(load("user_stat"));
		InventoryBuilder builder = definitionStore.getInventory("user_stat");
		assertNotNull(builder);

		var buttonTags = new org.luckyraven.gangland.menu.part.ButtonTags("prev", "home", "next");

		try (BukkitStatics ignored = installBukkit()) {
			// Empty item source — see phoneGangSearch's comment; user_stat.yml's Item_Template is also
			// Type: PLAYER_HEAD.
			ItemSourceProvider provider = (p, source) -> java.util.List.of();

			ChestMenu menu = builder.createPagedMenu(inventoryService, plugin, placeholderOf(), player,
			                                         conditionEvaluatorOf(), InventoryBuilder.DEFAULT_FILL_ITEM,
			                                         InventoryBuilder.DEFAULT_FILL_NAME, buttonTags, provider, opener, 0);
			open(menu, player);

			assertEquals(54, menu.bukkitInventory().getSize());
			assertEquals(Material.NAME_TAG, menu.bukkitInventory().getItem(18).getType());
			assertEquals(Material.BARRIER, menu.bukkitInventory().getItem(27).getType());
			assertEquals(Material.COMPASS, menu.bukkitInventory().getItem(36).getType());
		}
	}

	@Test
	@DisplayName("alliance_stat.yml — paginated ally list, no static items, 3-row minimum clamp")
	void allianceStat() throws IOException {
		context.registerInventory(load("alliance_stat"));
		InventoryBuilder builder = definitionStore.getInventory("alliance_stat");
		assertNotNull(builder);

		var buttonTags = new org.luckyraven.gangland.menu.part.ButtonTags("prev", "home", "next");

		try (BukkitStatics ignored = installBukkit()) {
			ItemSourceProvider provider = (p, source) -> java.util.List.of(
					entry("ally_id", "2"), entry("ally_name", "Ally Gang"), entry("ally_online", "1"),
					entry("ally_total", "3"), entry("ally_created", "today"));

			ChestMenu menu = builder.createPagedMenu(inventoryService, plugin, placeholderOf(), player,
			                                         conditionEvaluatorOf(), InventoryBuilder.DEFAULT_FILL_ITEM,
			                                         InventoryBuilder.DEFAULT_FILL_NAME, buttonTags, provider, opener, 0);
			open(menu, player);

			assertTrue(menu.bukkitInventory().getSize() >= 27); // rows clamped to [3,6]
		}
	}

	@Test
	@DisplayName("alliance_stat.yml — perPage+1 entries: page count, last-page remainder, nav buttons per page (F4)")
	void allianceStatPaginationBoundary() throws IOException {
		context.registerInventory(load("alliance_stat"));
		InventoryBuilder builder = definitionStore.getInventory("alliance_stat");
		assertNotNull(builder);

		// Empty tags: InventoryBuilder.headItem's real texture threading (fix round 1, F1) calls
		// ItemBuilder.customHead(tag), which needs com.mojang.authlib (XSkull) — the same real-server-only,
		// classpath-absent dependency the paginated Item_Template tests already work around (empty entries).
		// ItemBuilder.customHead(String) no-ops on a null/empty profile string, so an empty tag lets this test
		// exercise the nav buttons' real structure (slot position, presence, lore) without needing skin
		// resolution — the actual texture rendering is "Cannot verify" outside a live client either way (review).
		var buttonTags = new org.luckyraven.gangland.menu.part.ButtonTags("", "", "");

		try (BukkitStatics ignored = installBukkit()) {
			// alliance_stat.yml declares Size: 54 -> 6 rows (explicit-size branch of computeRows, item-count-
			// independent) -> the default interior region is rows 1..4, cols 1..7 = 28 slots/page (perPage).
			// perPage + 1 = 29 entries forces exactly 2 pages with a 1-entry remainder on the last page.
			int perPage = 28;
			List<ItemSourceEntry> thirtyEntries = java.util.stream.IntStream.range(0, perPage + 1)
					.mapToObj(i -> entry("ally_id", String.valueOf(i)))
					.map(m -> new ItemSourceEntry(m.placeholders()))
					.toList();
			ItemSourceProvider provider = (p, source) -> thirtyEntries;

			ChestMenu page0 = builder.createPagedMenu(inventoryService, plugin, placeholderOf(), player,
			                                          conditionEvaluatorOf(), InventoryBuilder.DEFAULT_FILL_ITEM,
			                                          InventoryBuilder.DEFAULT_FILL_NAME, buttonTags, provider, opener, 0);
			open(page0, player);

			assertEquals(54, page0.bukkitInventory().getSize());
			// Region first cell (row 1, col 1 = slot 10) and last cell (row 4, col 7 = slot 43) both filled ->
			// page 0 is a full 28-entry page.
			assertNotNull(page0.bukkitInventory().getItem(10), "page 0 first region slot must be filled");
			assertNotNull(page0.bukkitInventory().getItem(43), "page 0 last region slot must be filled (28 entries)");
			// Nav buttons on page 0 (first of 2 pages): next only. Slots 45/49/53 are all in the bottom border row
			// (F2: the paged path always draws one), so "no button here" means "still the border material", not
			// null — an explicit .slot(...) override is what a real nav button looks like (PLAYER_HEAD).
			assertEquals(Material.PLAYER_HEAD, page0.bukkitInventory().getItem(53).getType(),
			            "next button (size-1) must render on page 0");
			assertEquals(Material.BLACK_STAINED_GLASS_PANE, page0.bukkitInventory().getItem(49).getType(),
			            "no home button on page 0 - still border fill");
			assertEquals(Material.BLACK_STAINED_GLASS_PANE, page0.bukkitInventory().getItem(45).getType(),
			            "no prev button on page 0 - still border fill");

			ChestMenu page1 = builder.createPagedMenu(inventoryService, plugin, placeholderOf(), player,
			                                          conditionEvaluatorOf(), InventoryBuilder.DEFAULT_FILL_ITEM,
			                                          InventoryBuilder.DEFAULT_FILL_NAME, buttonTags, provider, opener, 1);
			open(page1, player);

			// Last-page remainder: only the 29th entry (index 28) renders, in the region's first cell; the last
			// cell (slot 43, filled on page 0) is empty here.
			assertNotNull(page1.bukkitInventory().getItem(10), "page 1's one remainder entry must render");
			assertEquals(null, page1.bukkitInventory().getItem(43), "page 1 must not fill the region's last cell");
			// Nav buttons on page 1 (last of 2 pages): no next (border fill); home + prev present.
			assertEquals(Material.BLACK_STAINED_GLASS_PANE, page1.bukkitInventory().getItem(53).getType(),
			            "no next button on the last page - still border fill");
			assertEquals(Material.PLAYER_HEAD, page1.bukkitInventory().getItem(49).getType(),
			            "home button (size-5) must render on page > 0");
			assertEquals(Material.PLAYER_HEAD, page1.bukkitInventory().getItem(45).getType(),
			            "prev button (size-9) must render on page > 0");
		}
	}

}
