package org.luckyraven.gangland.command.data;

public record CommandInformation(String usage, String description) {

	@Override
	public String toString() {
		return usage + " - " + description;
	}

}
