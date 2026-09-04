package org.luckyraven.gangland.item;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure-logic unit tests for {@link ItemParser} (Test Surface, items-unique.md: "attribute regex — comma truncation,
 * multiple brace groups, {@code type:modifier} splitting, the {@code Material.valueOf} fallback").
 *
 * <p>Pins three High/Medium-confidence observations from items-unique.md:
 * <ul>
 *   <li>#11 — {@code KEY_VALUE_PATTERN}'s value class is {@code [^,}]+}, so a comma inside an attribute value
 *       (e.g. multi-line {@code lore=a,b}) truncates at the first comma.</li>
 *   <li>#12 — {@code matcher.replaceAll("")} strips <em>every</em> {@code {...}} group from the string, but only
 *       the first was ever parsed into attributes.</li>
 *   <li>#13 — {@code getConverter}'s material fallback uses raw {@code Material.valueOf(type)}, not
 *       {@code MaterialConverter}'s {@code XMaterial} resolution, so a legacy/renamed name XMaterial could resolve
 *       is rejected one step earlier.</li>
 * </ul>
 */
@DisplayName("ItemParser — item-reference string -> ItemStack")
class ItemParserTest {

	private RecordingConverter    weaponConverter;
	private RecordingConverter    materialConverter;
	private ItemConverterRegistry registry;
	private ItemParser            parser;

	@BeforeEach
	void setUp() {
		weaponConverter    = new RecordingConverter(new ItemStack(Material.DIAMOND_SWORD));
		materialConverter  = new RecordingConverter(new ItemStack(Material.STONE));
		registry           = new ItemConverterRegistry();
		registry.register("weapon", weaponConverter);
		registry.register("material", materialConverter);
		parser = new ItemParser(registry);
	}

	@Test
	@DisplayName("null and blank input return null without touching the registry")
	void parse_nullOrBlank_returnsNull() {
		assertNull(parser.parse(null));
		assertNull(parser.parse("   "));
	}

	@Test
	@DisplayName("TYPE:modifier is split on the first colon; the type is upper-cased, the modifier is not")
	void parse_splitsTypeAndModifier() {
		parser.parse("weapon:ak47");

		assertEquals("WEAPON", weaponConverter.lastType);
		assertEquals("ak47", weaponConverter.lastModifier);
	}

	@Test
	@DisplayName("a type with no colon is parsed with a null modifier")
	void parse_noColon_nullModifier() {
		registry.register("phone", weaponConverter);

		parser.parse("phone");

		assertEquals("PHONE", weaponConverter.lastType);
		assertNull(weaponConverter.lastModifier);
	}

	@Test
	@DisplayName("an unregistered, non-Material type returns null and never reaches a converter")
	void parse_unknownType_returnsNull() {
		assertNull(parser.parse("not_a_type:foo"));
		assertNull(weaponConverter.lastType);
	}

	@Test
	@DisplayName("a raw Material name with no registered converter falls back to the 'material' converter")
	void parse_bareMaterialName_fallsBackToMaterialConverter() {
		parser.parse("DIAMOND_SWORD");

		assertEquals("DIAMOND_SWORD", materialConverter.lastType);
		assertNull(materialConverter.lastModifier);
	}

	@Test
	@DisplayName("Observation #13: Material.valueOf is the raw enum, not XMaterial — a name only XMaterial resolves"
			+ " never reaches the material converter")
	void parse_legacyMaterialNameXMaterialWouldResolve_isRejectedBeforeConverter() {
		// "STAINED_GLASS" is a legacy 1.8-era name XMaterial can resolve on modern servers, but it is not a member
		// of the current Material enum, so Material.valueOf throws before MaterialConverter ever sees it.
		assertNull(parser.parse("STAINED_GLASS"));
		assertNull(materialConverter.lastType);
	}

	@Test
	@DisplayName("attributes inside a single {k=v,...} group are parsed into the attribute map")
	void parse_singleAttributeGroup_parsedIntoMap() {
		parser.parse("weapon:ak47{name=Gold}");

		assertEquals(Map.of("name", "Gold"), weaponConverter.lastAttrs);
	}

	@Test
	@DisplayName("multiple key=value pairs in one group are all captured")
	void parse_multipleAttributesInOneGroup_allCaptured() {
		parser.parse("weapon:ak47{name=Gold,tier=Epic}");

		assertEquals(Map.of("name", "Gold", "tier", "Epic"), weaponConverter.lastAttrs);
	}

	@Test
	@DisplayName("Observation #11: a comma inside an attribute value truncates it at the first comma")
	void parse_commaInsideAttributeValue_truncatesAtFirstComma() {
		// {lore=a,b} is meant to carry a two-line lore, but the value pattern [^,}]+ stops at the first comma, so
		// "b" is silently reinterpreted as if it were its own bare token (which KEY_VALUE_PATTERN then rejects,
		// since it has no '=').
		parser.parse("weapon:ak47{lore=a,b}");

		assertEquals("a", weaponConverter.lastAttrs.get("lore"), "multi-line lore via {lore=a,b} is unreachable");
		assertFalse(weaponConverter.lastAttrs.containsKey("b"));
	}

	@Test
	@DisplayName("Observation #12: a second {...} group is stripped from the string but its contents are never parsed")
	void parse_secondBraceGroup_strippedButNeverParsed() {
		parser.parse("weapon:ak47{name=Gold}{tier=Epic}");

		assertEquals(Map.of("name", "Gold"), weaponConverter.lastAttrs, "only the first brace group is parsed");
	}

	@Test
	@DisplayName("the type:modifier text is stripped of every brace group before the colon split")
	void parse_typeModifierSplit_excludesBraceGroups() {
		parser.parse("weapon:ak47{name=Gold}");

		assertEquals("ak47", weaponConverter.lastModifier, "the {...} group must not leak into the modifier text");
	}

	// -------------------------------------------------------------- fixture

	private static final class RecordingConverter implements ItemConverter {

		private final ItemStack result;

		String               lastType;
		String               lastModifier;
		Map<String, String>  lastAttrs;

		RecordingConverter(ItemStack result) {
			this.result = result;
		}

		@Override
		public ItemStack convert(String type, String modifier, Map<String, String> attributes) {
			this.lastType     = type;
			this.lastModifier = modifier;
			this.lastAttrs    = new HashMap<>(attributes);
			return result;
		}
	}

}
