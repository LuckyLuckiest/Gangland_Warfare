package org.luckyraven.gangland.gadget.sign;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.testsupport.BukkitRegistryFixture;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.gadget.car.Car;
import org.luckyraven.gangland.gadget.car.CarKey;
import org.luckyraven.gangland.gadget.car.CarManager;
import org.luckyraven.gangland.sign.SignType;
import org.luckyraven.gangland.sign.aspect.AspectResult;
import org.luckyraven.gangland.sign.aspect.ItemTransferAspect;
import org.luckyraven.gangland.sign.aspect.MoneyAspect;
import org.luckyraven.gangland.sign.aspect.SignAspect;
import org.luckyraven.gangland.sign.bulk.BulkActionPreview;
import org.luckyraven.gangland.sign.handler.AspectBasedSignHandler;
import org.luckyraven.gangland.sign.model.ParsedSign;
import org.luckyraven.gangland.sign.model.SignFormat;
import org.luckyraven.gangland.sign.model.SignLineFormat;
import org.luckyraven.gangland.sign.parser.TradeSignParser;
import org.luckyraven.gangland.sign.registry.SignTypeDefinition;
import org.luckyraven.gangland.sign.type.trade.BaseTradeSign;
import org.luckyraven.keystone.color.Color;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.keystone.item.nbt.NbtBridge;
import org.luckyraven.keystone.item.nbt.ItemNbtAccessor;
import org.luckyraven.keystone.item.nbt.NbtType;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Characterises {@code CarBuySign} and {@code CarSellSign} before their identical ~95 lines move into a shared base:
 * the definition they build (type, validator, parser, bulk handler, aspect list and order, aspect kinds), the item
 * provider and similarity lambdas, the bulk preview and the not-initialised guard, and the four-line sign format.
 */
@DisplayName("Car buy/sell signs — definition, aspects, bulk handler and format")
class CarTradeSignCharacterisationTest {

	@BeforeAll
	static void bootstrap() {
		BukkitRegistryFixture.install();
	}

	/**
	 * Per-stack NBT store. {@code RecordingNbtAccessor} keeps one tag map for every stack, which cannot tell two cars
	 * apart; this one is keyed by stack identity.
	 */
	private static final class PerStackNbt implements ItemNbtAccessor {

		private final Map<ItemStack, Map<String, Object>> tags = new IdentityHashMap<>();

		@Override
		public boolean isAvailable() {
			return true;
		}

		@Override
		public boolean has(ItemStack item, String key) {
			return tags.getOrDefault(item, Map.of()).containsKey(key);
		}

		@Override
		public Object get(ItemStack item, String key) {
			return tags.getOrDefault(item, Map.of()).get(key);
		}

		@Override
		public String getString(ItemStack item, String key) {
			return (String) get(item, key);
		}

		@Override
		public int getInt(ItemStack item, String key) {
			Object value = get(item, key);
			return value == null ? 0 : (Integer) value;
		}

		@Override
		public void set(ItemStack item, String key, NbtType type, Object value) {
			tags.computeIfAbsent(item, k -> new HashMap<>()).put(key, value);
		}

		@Override
		public void remove(ItemStack item, String key) {
			tags.getOrDefault(item, new HashMap<>()).remove(key);
		}

		@Override
		public String describe(ItemStack item) {
			return String.valueOf(tags.get(item));
		}
	}

	@BeforeEach
	void setUp() {
		NbtBridge.install(new PerStackNbt());
	}

	@AfterEach
	void tearDown() {
		NbtBridge.reset();
	}

	private static final SignType BUY  = new SignType("[car-buy]", "CAR-BUY");
	private static final SignType SELL = new SignType("[car-sell]", "CAR-SELL");

	@SuppressWarnings("unchecked")
	private final UserManager<Player> userManager = mock(UserManager.class);
	private final CarManager          carManager  = mock(CarManager.class);

	private static Object field(Object target, String name) throws Exception {
		Field field = target.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return field.get(target);
	}

	private static ParsedSign parsed(String content, double price, int amount) {
		ParsedSign sign = mock(ParsedSign.class);
		when(sign.getContent()).thenReturn(content);
		when(sign.getPrice()).thenReturn(price);
		when(sign.getAmount()).thenReturn(amount);
		return sign;
	}

	private static ItemStack stamped(String carId, int amount) {
		ItemStack stack = new ItemStack(Material.MINECART, amount);
		new ItemBuilder(stack).addTag(CarKey.CAR_ID.getKey(), carId);
		return stack;
	}

	private void assertDefinition(BaseTradeSign sign, SignType type, String moneyName, String itemName,
	                              int moneyPriority) throws Exception {
		SignTypeDefinition definition = ((org.luckyraven.gangland.sign.type.Sign) sign).createDefinition();

		assertSame(type, definition.getSignType());
		assertInstanceOf(CarSignValidator.class, definition.getSignValidator());
		assertInstanceOf(TradeSignParser.class, definition.getSignParser());
		assertInstanceOf(AspectBasedSignHandler.class, definition.getHandler());
		assertSame(sign, definition.getBulkHandler());

		List<SignAspect> aspects = definition.getAspects();
		assertEquals(2, aspects.size());
		assertInstanceOf(MoneyAspect.class, aspects.get(0));
		assertInstanceOf(ItemTransferAspect.class, aspects.get(1));
		assertEquals(moneyName, aspects.get(0).getName());
		assertEquals(itemName, aspects.get(1).getName());
		assertEquals(moneyPriority, aspects.get(0).getPriority());
		assertSame(userManager, field(aspects.get(0), "userManager"));
	}

	@Test
	@DisplayName("buy: withdraws money, then gives the car item; definition is wired to the sign itself")
	void buy_definition() throws Exception {
		assertDefinition(new CarBuySign(userManager, carManager, BUY), BUY, "MoneyAspect-WITHDRAW",
		                 "ItemTransferAspect-GIVE", 100);
	}

	@Test
	@DisplayName("sell: takes the car item, money aspect deposits; definition is wired to the sign itself")
	void sell_definition() throws Exception {
		assertDefinition(new CarSellSign(userManager, carManager, SELL), SELL, "MoneyAspect-DEPOSIT",
		                 "ItemTransferAspect-TAKE", -100);
	}

	private void assertProviderAndSimilarity(CarBuySignOrSell signs) throws Exception {
		SignTypeDefinition definition = signs.sign().createDefinition();
		Object             transfer   = definition.getAspects().get(1);

		ItemTransferAspect.ItemProvider provider = (ItemTransferAspect.ItemProvider) field(transfer, "itemProvider");

		Car car = mock(Car.class);
		ItemStack built = new ItemStack(Material.MINECART);
		when(car.buildItem()).thenReturn(built);
		when(carManager.getCar("sports")).thenReturn(car);

		assertSame(built, provider.getItem(parsed("sports", 1, 1)));
		assertNull(provider.getItem(parsed("ghost", 1, 1)));

		ItemTransferAspect.ItemSimilarityChecker checker =
				(ItemTransferAspect.ItemSimilarityChecker) field(transfer, "similarityChecker");
		Player player = mock(Player.class);

		assertTrue(checker.isSimilar(player, stamped("a", 1), stamped("a", 2)));
		assertFalse(checker.isSimilar(player, stamped("a", 3), stamped("b", 4)));
		assertFalse(checker.isSimilar(player, new ItemStack(Material.MINECART, 5), new ItemStack(Material.MINECART, 6)));
		assertFalse(checker.isSimilar(player, stamped("a", 7), new ItemStack(Material.MINECART, 8)));
	}

	private record CarBuySignOrSell(org.luckyraven.gangland.sign.type.Sign sign) {}

	@Test
	@DisplayName("buy: the item provider builds the named car (null when unknown); similarity compares car ids")
	void buy_providerAndSimilarity() throws Exception {
		assertProviderAndSimilarity(new CarBuySignOrSell(new CarBuySign(userManager, carManager, BUY)));
	}

	@Test
	@DisplayName("sell: the item provider builds the named car (null when unknown); similarity compares car ids")
	void sell_providerAndSimilarity() throws Exception {
		assertProviderAndSimilarity(new CarBuySignOrSell(new CarSellSign(userManager, carManager, SELL)));
	}

	@Test
	@DisplayName("buy: previewBulk mirrors the sign; executeBulkAction fails before createDefinition, delegates after")
	void buy_bulk() {
		CarBuySign sign = new CarBuySign(userManager, carManager, BUY);
		assertBulk(sign, sign::previewBulk, sign::createDefinition, sign::executeBulkAction);
	}

	@Test
	@DisplayName("sell: previewBulk mirrors the sign; executeBulkAction fails before createDefinition, delegates after")
	void sell_bulk() {
		CarSellSign sign = new CarSellSign(userManager, carManager, SELL);
		assertBulk(sign, sign::previewBulk, sign::createDefinition, sign::executeBulkAction);
	}

	private void assertBulk(Object sign, Function<ParsedSign, BulkActionPreview> preview, Runnable init,
	                        java.util.function.BiFunction<Player, ParsedSign, List<AspectResult>> execute) {
		ParsedSign parsed = parsed("sports", 12.5, 3);

		BulkActionPreview bulk = preview.apply(parsed);
		assertEquals(3, bulk.getQuantity());
		assertEquals(12.5, bulk.getTotalPrice());
		assertEquals("sports", bulk.getContentName());

		Player              player = mock(Player.class);
		List<AspectResult>  before = execute.apply(player, parsed);
		assertEquals(1, before.size());
		assertFalse(before.get(0).isSuccess());
		assertFalse(before.get(0).isContinueExecution());
		assertEquals("Sign not fully initialized - please contact an admin.", before.get(0).getMessage());

		init.run();

		// userManager.getUser(player) is null on the mock, so MoneyAspect.canExecute refuses and the handler stops.
		List<AspectResult> after = execute.apply(player, parsed);
		assertEquals(1, after.size());
		assertFalse(after.get(0).isSuccess());
		assertTrue(after.get(0).getMessage().startsWith("MoneyAspect-"));
		assertTrue(after.get(0).getMessage().endsWith(": Preconditions not met"));
	}

	private void assertFormat(org.luckyraven.gangland.sign.type.Sign sign, SignType type) throws Exception {
		SignFormat format = sign.createFormat();

		assertEquals(type.generated().toLowerCase(), format.getFormatName());
		assertEquals(type.typed(), format.getSignTypePrefix());
		assertEquals(4, format.getLineFormats().size());

		SignLineFormat line1 = format.getLineFormat(0);
		SignLineFormat line2 = format.getLineFormat(1);
		SignLineFormat line3 = format.getLineFormat(2);
		SignLineFormat line4 = format.getLineFormat(3);

		assertEquals(SignLineFormat.LineContentType.TITLE, line1.getContentType());
		assertEquals(SignLineFormat.LineContentType.CUSTOM_TEXT, line2.getContentType());
		assertEquals(SignLineFormat.LineContentType.PRICE, line3.getContentType());
		assertEquals(SignLineFormat.LineContentType.QUANTITY, line4.getContentType());

		for (int i = 0; i < 4; i++) {
			SignLineFormat line = format.getLineFormat(i);
			assertEquals(i, line.getLineNumber());
			assertTrue(line.isRequired());
		}

		assertNull(line1.getDefaultColor());
		assertEquals(Color.GRAY, line2.getDefaultColor());
		assertEquals(Color.LIME, line3.getDefaultColor());
		assertEquals(Color.CYAN, line4.getDefaultColor());

		assertEquals("&8[&3" + type.generated() + "&8]", line1.getFormatter().apply("ignored"));
		assertEquals("&7sports", line2.getFormatter().apply("sports"));
		assertEquals("&a12", line3.getFormatter().apply("12"));
		assertEquals("&b3", line4.getFormatter().apply("3"));
	}

	@Test
	@DisplayName("buy: createFormat is the four-line title / name / price / quantity layout")
	void buy_format() throws Exception {
		assertFormat(new CarBuySign(userManager, carManager, BUY), BUY);
	}

	@Test
	@DisplayName("sell: createFormat is the four-line title / name / price / quantity layout")
	void sell_format() throws Exception {
		assertFormat(new CarSellSign(userManager, carManager, SELL), SELL);
	}
}
