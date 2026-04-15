package me.luckyraven.copsncrooks.npc.trader.mood;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public final class MoodState {

	private double mood;
	private long   lastUpdateMs;

	public MoodState(long nowMs) {
		this.mood         = 0D;
		this.lastUpdateMs = nowMs;
	}

}
