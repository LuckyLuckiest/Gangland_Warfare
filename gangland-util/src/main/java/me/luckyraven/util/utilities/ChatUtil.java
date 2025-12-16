package me.luckyraven.util.utilities;

import com.cryptomorin.xseries.messages.ActionBar;
import com.google.common.base.Preconditions;
import me.luckyraven.util.datastructure.SpellChecker;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
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

	public static void consoleColor(String message) {
		Bukkit.getConsoleSender().sendMessage(color(message));
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

	public static void startUpMessage(JavaPlugin plugin) {
		PluginDescriptionFile pdf = plugin.getDescription();
		consoleColor(getPrefix(pdf));
		consoleColor("\t&8Author&7: " + pdf.getAuthors());
		consoleColor("\t&8Version&7: &5(&6" + pdf.getVersion() + "&5)");
		consoleColor(getPrefix(pdf));
	}

	public static String getPrefix(PluginDescriptionFile pdf) {
		return color("&8-[\t&6" + pdf.getName() + "\t&8]-");
	}

	public static String getFilePrefix(PluginDescriptionFile pdf) {
		return color("&8-[\t&6" + pdf.getName() + " &cFiles\t&8]-");
	}

	public static String removeSymbol(String message) {
		return message.replaceAll("[^a-zA-Z\\d\\s]*", "");
	}

	public static void sendActionBar(Player player, String message) {
		ActionBar.clearActionBar(player);
		ActionBar.sendActionBar(player, color(message));
	}

	public static void sendActionBar(JavaPlugin plugin, Player player, String message, long duration) {
		ActionBar.clearActionBar(player);

		BaseComponent baseComponent = TextComponent.fromLegacy(color(message));

		ActionBar.sendActionBar(plugin, player, baseComponent, duration);
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

		builder.append("&b\"").append("/").append(command).append(" ");

		if (args != null) for (String arg : args) builder.append(arg).append(" ");

		if (minimum != -1) builder.append(suggestions.get(minimum).getFirst());

		builder.trimToSize();
		builder.append("\"&e?");

		return builder.toString();
	}

	public record Replacement(String placeholder, String replacement) { }

}