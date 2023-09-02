package me.luckyraven.data.placeholder;

import me.luckyraven.data.placeholder.replacer.CharReplacer;
import me.luckyraven.data.placeholder.replacer.Replacer;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class PlaceholderHandler extends PlaceholderRequest {

	private static final String PLACEHOLDER_PATTERN         = "%([^%]+)%";
	private static final String BRACKET_PLACEHOLDER_PATTERN = "\\{([^{}]+)}";

	private final Replacer         replacer;
	private final Replacer.Closure closure;

	public PlaceholderHandler(Replacer.Closure closure) {
		this.replacer = new CharReplacer(closure);
		this.closure = closure;
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
		return replacer.apply(player, text.replace("%gangland_", "%"), this);
	}

	public List<String> replacePlaceholders(OfflinePlayer player, @NotNull List<String> text) {
		return text.stream().map(line -> replacePlaceholder(player, line)).toList();
	}

	public List<String> replacePlaceholders(OfflinePlayer player, @NotNull String... text) {
		return Arrays.stream(text).map(line -> replacePlaceholder(player, line)).toList();
	}

}
