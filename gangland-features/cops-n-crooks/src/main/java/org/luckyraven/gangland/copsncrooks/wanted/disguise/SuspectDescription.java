package org.luckyraven.gangland.copsncrooks.wanted.disguise;

/**
 * What a cop can say about a suspect (0.20.0, cops-only per D-A2). Empty text means none; {@code head} is the mask name
 * when {@code masked}.
 */
public record SuspectDescription(String head, String chest, String weapon, boolean inCar, boolean masked) {

	public SuspectDescription {
		head   = head == null ? "" : head;
		chest  = chest == null ? "" : chest;
		weapon = weapon == null ? "" : weapon;
	}
}
