package me.luckyraven.persistence.support;

import lombok.Getter;

import java.util.Objects;

/**
 * Minimal entity used by all integration tests that exercise the repository / table layer.
 */
@Getter
public final class TestEntity {

	private final String id;
	private final String name;
	private final int    score;

	public TestEntity(String id, String name, int score) {
		this.id    = id;
		this.name  = name;
		this.score = score;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof TestEntity t)) return false;
		return score == t.score && Objects.equals(id, t.id) && Objects.equals(name, t.name);
	}

	@Override
	public int hashCode() { return Objects.hash(id, name, score); }

	@Override
	public String toString() {
		return "TestEntity{id='" + id + "', name='" + name + "', score=" + score + '}';
	}
}
