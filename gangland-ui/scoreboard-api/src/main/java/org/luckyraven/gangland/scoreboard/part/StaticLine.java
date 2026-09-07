package org.luckyraven.gangland.scoreboard.part;

public class StaticLine extends Line {

	public StaticLine() {
		this(0);
	}

	public StaticLine(int index) {
		super(0, index);
	}

	protected StaticLine(StaticLine source) {
		super(source);
	}

	@Override
	public Line copy() {
		return new StaticLine(this);
	}

}
