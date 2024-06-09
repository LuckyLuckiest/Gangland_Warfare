package me.luckyraven.util;

public record Pair<P1, P2>(P1 first, P2 second) {

	@Override
	public int hashCode() {
		return first.hashCode() + second.hashCode();
	}
}
