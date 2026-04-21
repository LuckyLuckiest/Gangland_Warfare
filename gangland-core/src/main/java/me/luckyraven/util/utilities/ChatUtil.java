package me.luckyraven.util.utilities;

import com.google.common.base.Preconditions;
import me.luckyraven.util.datastructure.SpellChecker;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ChatUtil {

	public static String replaceColorCodes(String message, String replaceWith) {
		return message.replace('§', '&').replaceAll("&[0-9a-fA-Fk-oK-OrR]", replaceWith);
	}

	public static String color(final String message) {
		return color(message, new Replacement("%money_symbol%", "$"));
	}

	public static String color(final String message, final Replacement replacement1,
	                           final Replacement... replacements) {
		Objects.requireNonNull(message);
		String value = message.replace("%n%", "\n");

		value = value.replace(replacement1.placeholder, replacement1.replacement);

		for (int i = 0; i < replacements.length; i += 2) {
			Replacement replacement = replacements[i];
			value = value.replace(replacement.placeholder, replacement.replacement);
		}

		return ChatColor.translateAlternateColorCodes('&', value);
	}

	public static String[] color(String... messages) {
		return Arrays.stream(messages).map(ChatUtil::color).toArray(String[]::new);
	}

	public static String plural(int amount) {
		return amount > 1 ? "s" : "";
	}

	public static String capitalize(@NotNull String text) {
		return text.substring(0, 1).toUpperCase() + text.substring(1);
	}

	public static String center(@NotNull String text, int level) {
		Preconditions.checkNotNull(text, "Text can't be null!");

		if (text.length() >= level) return text;

		int    length = text.length();
		String prefix = " ".repeat((level - length) / 2);

		return prefix + text;
	}

	public static String unicodeCharacters(String position) {
		return String.valueOf(Character.toChars(Integer.parseInt(position, 16)));
	}

	public static String removeSymbol(String message) {
		return message.replaceAll("[^a-zA-Z\\d\\s]*", "");
	}

	public static void sendTitle(Player player, String title, String subtitle) {
		sendTitle(player, title, subtitle, 5, 20, 5);
	}

	public static void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
		player.sendTitle(color(title), color(subtitle), fadeIn, stay, fadeOut);
	}

	/**
	 * Generates a command suggestion based on the words from the dictionary. This method uses a spell checker to find a
	 * common word close to what you have written.
	 *
	 * @param word the word to check
	 * @param dictionary looks for a word from the dictionary
	 * @param command the whole command entered
	 * @param args the arguments of the command
	 *
	 * @return the whole suggested command
	 */
	public static String generateCommandSuggestion(String word, Set<String> dictionary, String command,
	                                               @Nullable String[] args) {
		// generate suggestions
		SpellChecker checker = new SpellChecker(word, dictionary);

		checker.generateSuggestions();

		Map<Integer, List<String>> suggestions = checker.getSuggestions();
		// get the minimum length
		int minimum = suggestions.keySet()
				.stream().mapToInt(Integer::intValue).min().orElse(-1);

		StringBuilder builder = new StringBuilder("&eDid you mean ");

		builder.append("&b\"");

		StringBuilder suggestion = new StringBuilder();
		suggestion.append("/").append(command).append(" ");

		if (args != null) for (String arg : args) suggestion.append(arg).append(" ");
		if (minimum != -1) suggestion.append(suggestions.get(minimum).getFirst());

		String suggestionString = suggestion.toString().trim();

		builder.append(suggestionString);
		builder.append("\"&e?");

		return builder.toString();
	}

	public record Replacement(String placeholder, String replacement) { }

}