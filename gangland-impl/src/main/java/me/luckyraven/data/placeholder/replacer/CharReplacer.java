package me.luckyraven.data.placeholder.replacer;

import me.luckyraven.data.placeholder.PlaceholderRequest;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public class CharReplacer implements Replacer {

	private final Closure closure;

	public CharReplacer(Closure closure) {
		this.closure = closure;
	}

	@Override
	public String apply(OfflinePlayer player, @NotNull String text, PlaceholderRequest request) {
		char[] chars = text.toCharArray();
		// holds all the text
		StringBuilder builder = new StringBuilder(text.length());
		// holds the text which is inside the closure
		StringBuilder parameter = new StringBuilder();

		// loop through all the chars
		for (int i = 0; i < chars.length; i++) {
			char current = chars[i];

			// check if it was not a head or the last value
			if (current != closure.getHead() || i + 1 >= chars.length) {
				builder.append(current);
				continue;
			}

			boolean invalid  = true;
			boolean hasSpace = false;

			while (++i < chars.length) {
				char param = chars[i];

				if (param == ' ') {
					hasSpace = true;
					break;
				}

				if (param == closure.getTail()) {
					invalid = false;
					break;
				}

				parameter.append(param);
			}

			// process the found value
			String paramStr = parameter.toString();

			// clear the StringBuilder to accept other parameters
			parameter.setLength(0);

			if (invalid) {
				builder.append(closure.getHead()).append(paramStr);

				if (hasSpace) builder.append(' ');

				continue;
			}

			// search for the value inside the request method
			String replacement = request.onRequest(player, paramStr);

			if (replacement == null) {
				builder.append(closure.getHead()).append(paramStr).append(closure.getTail());
				continue;
			}

			builder.append(replacement);
		}

		return builder.toString();
	}

}
