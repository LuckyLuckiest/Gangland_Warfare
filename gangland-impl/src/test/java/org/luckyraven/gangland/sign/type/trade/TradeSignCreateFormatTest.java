package org.luckyraven.gangland.sign.type.trade;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.item.configuration.UniqueItemAddon;
import org.luckyraven.gangland.sign.SignType;
import org.luckyraven.gangland.sign.model.SignFormat;
import org.luckyraven.gangland.sign.model.SignLineFormat;
import org.luckyraven.gangland.sign.model.SignLineFormat.LineContentType;
import org.luckyraven.keystone.color.Color;
import org.luckyraven.keystone.item.ItemParser;
import org.luckyraven.keystone.item.ItemSerializerRegistry;

import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Characterises the {@code SignFormat} that {@code BuySign} and {@code SellSign} build: the lower-cased generated name
 * as format name, the typed text as prefix, and four required lines (title, custom text, price, quantity) with their
 * default colours and formatters.
 */
@DisplayName("BuySign/SellSign.createFormat")
class TradeSignCreateFormatTest {

	private static final SignType TYPE = new SignType("[buy]", "BuyIt");

	@SuppressWarnings("unchecked")
	private static BuySign buy() {
		return new BuySign(mock(UserManager.class), mock(UniqueItemAddon.class), mock(ItemSerializerRegistry.class),
		                   mock(ItemParser.class), TYPE);
	}

	@SuppressWarnings("unchecked")
	private static SellSign sell() {
		return new SellSign(mock(UserManager.class), mock(UniqueItemAddon.class), mock(ItemSerializerRegistry.class),
		                    mock(ItemParser.class), TYPE);
	}

	private static void assertFormat(SignFormat format) {
		assertEquals("buyit", format.getFormatName());
		assertEquals("[buy]", format.getSignTypePrefix());
		assertTrue(format.getConditionalLines().isEmpty());

		List<SignLineFormat> lines = format.getLineFormats();
		assertEquals(4, lines.size());

		assertLine(lines.get(0), 0, null, LineContentType.TITLE, "&8[&3BuyIt&8]", "anything");
		assertLine(lines.get(1), 1, Color.GRAY, LineContentType.CUSTOM_TEXT, "&7stone", "stone");
		assertLine(lines.get(2), 2, Color.LIME, LineContentType.PRICE, "&a12", "12");
		assertLine(lines.get(3), 3, Color.CYAN, LineContentType.QUANTITY, "&b64", "64");
	}

	private static void assertLine(SignLineFormat line, int number, Color color, LineContentType type, String formatted,
	                               String input) {
		assertEquals(number, line.getLineNumber());
		assertTrue(line.isRequired());
		assertEquals(color, line.getDefaultColor());
		assertEquals(type, line.getContentType());
		assertNull(line.getPrefix());
		assertNull(line.getSuffix());
		assertEquals(formatted, line.getFormatter().apply(input));
	}

	@Test
	@DisplayName("BuySign builds the four-line trade format")
	void buySign() {
		assertFormat(buy().createFormat());
	}

	@Test
	@DisplayName("SellSign builds the same four-line trade format")
	void sellSign() {
		assertFormat(sell().createFormat());
	}

	@Test
	@DisplayName("each call builds a fresh format")
	void freshEachCall() {
		Supplier<SignFormat> supplier = buy()::createFormat;

		assertTrue(supplier.get() != supplier.get());
	}
}
