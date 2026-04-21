package me.luckyraven.core.placeholder;

import me.luckyraven.core.Placeholder;
import me.luckyraven.core.placeholder.replacer.CharReplacer;
import me.luckyraven.core.placeholder.replacer.Replacer;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class PlaceholderHandler extends PlaceholderRequest implements Placeholder {

	private static final String PLACEHOLDER_PATTERN         = "%([^%]+)%";
	private static final String BRACKET_PLACEHOLDER_PATTERN = "\\{([^{}]+)}";

	private final String           prefix;
	private final Replacer         replacer;
	private final Replacer.Closure closure;

	public PlaceholderHandler(String prefix, Replacer.Closure closure) {
		this.prefix   = prefix;
		this.replacer = new CharReplacer(closure);
		this.closure  = closure;
	}

	public static String getPlaceholderPattern() {
		return PLACEHOLDER_PATTERN;
	}

	public static String getBracketPlaceholderPattern() {
		return BRACKET_PLACEHOLDER_PATTERN;
	}

	public boolean containsPlaceholder(@NotNull String text) {
		return switch (closure) {
			case PERCENT -> Pattern.compile(PLACEHOLDER_PATTERN).matcher(text).find();
			case BRACKET -> Pattern.compile(BRACKET_PLACEHOLDER_PATTERN).matcher(text).find();
		};
	}

	public String replacePlaceholder(OfflinePlayer player, @NotNull String text) {
		String format = String.format("%%%s_", prefix);
		return replacer.apply(player, text.replace(format, "%"), this);
	}

	@Override
	public String convert(Player player, String text) {
		if (!containsPlaceholder(text)) {
			return text;
		}
		return replacePlaceholder(player, text);
	}

	public List<String> replacePlaceholders(OfflinePlayer player, @NotNull List<String> text) {
		return text.stream().map(line -> replacePlaceholder(player, line)).toList();
	}

	public List<String> replacePlaceholders(OfflinePlayer player, @NotNull String... text) {
		return Arrays.stream(text).map(line -> replacePlaceholder(player, line)).toList();
	}

}
