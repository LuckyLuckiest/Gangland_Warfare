package me.luckyraven.command.data;

import me.luckyraven.Gangland;

public record CommandInformation(String usage, String description) {

	public static CommandInformation getInfo(String name) {
		return Gangland.getInstance().getInformationManager().getCommands().get(name);
	}

	@Override
	public String toString() {
		return usage + " - " + description;
	}

}
