package me.luckyraven.data;

import me.luckyraven.command.data.CommandInformation;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

import static me.luckyraven.command.CommandManager.commandDesign;
import static me.luckyraven.util.ChatUtil.color;

public final class HelpInfo {

	private final List<CommandInformation> list;
	private final int                      breaks;

	public HelpInfo() {
		this.list = new ArrayList<>();
		this.breaks = 7;
	}

	public HelpInfo(int breaks) {
		this.list = new ArrayList<>();
		this.breaks = breaks;
	}

	public HelpInfo(List<CommandInformation> list, int breaks) {
		this.list = list;
		this.breaks = breaks;
	}

	public void add(CommandInformation element) {
		list.add(element);
	}

	public void remove(CommandInformation element) {
		list.remove(element);
	}

	public int size() {
		return list.size();
	}

	public CommandInformation getInformation(int index) {
		return list.get(index);
	}

	/**
	 * @return Maximum pages allowed
	 */
	public int getMaxPages() {
		return (list.size() + breaks - 1) / breaks;
	}

	/**
	 * Display help menu to the specified <i>sender</i>.
	 *
	 * @param sender Displaying the help menu to the sender
	 * @param page   The page the sender wants to view
	 * @param title  The title of the help menu
	 */
	public void displayHelp(CommandSender sender, int page, String title) {
		int maxPages = getMaxPages();
		if (page < 1) throw new IllegalArgumentException("Cannot get page less than 1");
		if (page > maxPages) throw new IllegalArgumentException("Cannot exceed maximum allowed pages");
		String header = color("&3Oo&3&m------&r &8&l[&bG&fL&bW&8&l]&7 " + title + " &8[&7" + page + "&5/&7" + maxPages +
				                      "&8] &3&m------&3oO");
		sender.sendMessage("");
		sender.sendMessage(header);
		sender.sendMessage("");
		for (int line = 0; line < breaks; line++)
			try {
				int index = (page - 1) * breaks + line;
				sender.sendMessage(commandDesign(list.get(index).toString()));
			} catch (IndexOutOfBoundsException ignored) {
				break;
			}
		sender.sendMessage("");
	}

}
