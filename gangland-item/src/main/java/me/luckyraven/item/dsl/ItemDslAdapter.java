package me.luckyraven.item.dsl;

import lombok.RequiredArgsConstructor;
import me.luckyraven.item.ItemConverter;
import me.luckyraven.item.ItemConverterRegistry;
import me.luckyraven.persistence.config.ConfigReport;
import me.luckyraven.persistence.config.Severity;
import me.luckyraven.persistence.config.dsl.BracketedAttrsParser;
import me.luckyraven.persistence.config.dsl.DslValue;
import me.luckyraven.persistence.config.dsl.StringDslParser;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bridges the positional {@link BracketedAttrsParser} to the existing {@link ItemConverter} registry. Loaders migrated
 * to the {@link me.luckyraven.persistence.config.NodeReader} API invoke {@link #asDslParser()} so scalar values like
 * {@code DIAMOND_SWORD{custom_model_data=1021}} parse into an {@link ItemStack} while emitting located
 * {@link me.luckyraven.persistence.config.ConfigIssue}s for unknown item types or conversion failures.
 *
 * <p>Syntax accepted (inherited from {@link BracketedAttrsParser}):
 * <ul>
 *   <li>{@code DIAMOND_SWORD} — bare Material name, falls back to the registry's {@code material} converter.</li>
 *   <li>{@code weapon:ak47} — registered {@code type:modifier}.</li>
 *   <li>{@code weapon:ak47[name=Gold]} or {@code weapon:ak47{name=Gold}} — attributes.</li>
 * </ul>
 *
 * <p>{@link ItemParser} is not deleted — it stays wired for callers that have not
 * migrated. Migrated loaders should prefer this adapter because the errors it
 * produces carry {@code file.yml:L:C} locations instead of silent {@code null}.
 */
@RequiredArgsConstructor
public final class ItemDslAdapter {

	private final ItemConverterRegistry registry;
	private final BracketedAttrsParser  parser;

	private static Map<String, String> flattenAttributes(DslValue value) {
		Map<String, String> out = new LinkedHashMap<>();

		for (Map.Entry<String, DslValue> entry : value.attrs().entrySet()) {
			out.put(entry.getKey(), entry.getValue().raw());
		}

		return out;
	}

	/**
	 * Resolve a parsed DSL value into an {@link ItemStack}.
	 *
	 * @param value the parsed DSL tree; typically produced by {@code reader.get("Item").asDsl(...)}.
	 * @param report issue collector; {@code item.unknown_type} and {@code item.conversion_failed} issues are appended
	 * 		here.
	 *
	 * @return the converted item, or {@code null} when no converter can handle the head type.
	 */
	public ItemStack apply(DslValue value, ConfigReport report) {
		if (value == null) return null;

		String head = value.name();

		if (head == null || head.isEmpty()) {
			report.add(Severity.ERROR, value.at(), "",
			           "item DSL must begin with a type name (e.g. DIAMOND_SWORD, weapon:ak47)",
			           "item.missing_type");
			return null;
		}

		String[] split    = head.split(":", 2);
		String   type     = split[0];
		String   modifier = split.length > 1 ? split[1] : null;

		ItemConverter converter = resolveConverter(type);

		if (converter == null) {
			report.add(Severity.ERROR, value.at(), "",
			           "unknown item type '" + type + "'", "item.unknown_type");
			return null;
		}

		Map<String, String> attrs = flattenAttributes(value);

		ItemStack stack = converter.convert(type, modifier, attrs);

		if (stack == null) {
			report.add(Severity.ERROR, value.at(), "",
			           "converter for '" + type + "' rejected the DSL value", "item.conversion_failed");
		}

		return stack;
	}

	/**
	 * @return a {@link StringDslParser} that parses the raw scalar and feeds it to
	 *        {@link #apply(DslValue, ConfigReport)}. Use with {@code reader.get("Item").asDsl(adapter.asDslParser())}.
	 */
	public StringDslParser<ItemStack> asDslParser() {
		return (raw, scalarLoc, report) -> {
			DslValue value = parser.parse(raw, scalarLoc, report);

			if (value == null) return null;

			return apply(value, report);
		};
	}

	private ItemConverter resolveConverter(String type) {
		if (registry.hasConverter(type)) return registry.getConverter(type);

		try {
			Material.valueOf(type.toUpperCase());
			return registry.getConverter("material");
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}

}
