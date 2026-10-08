package org.luckyraven.gangland.civilians.npc.npc;

import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

/**
 * Red tests for T-189 (0.16.1): {@link CivilianNpc#removeEntityTarget} drops exactly the given entity from the entity
 * target queue (used by the turf cop guard when a cop leaves range) and leaves the rest in order. The queue is read
 * back through reflection because its only public readers consult entity liveness, which mocks do not model.
 */
@DisplayName("CivilianNpc.removeEntityTarget (T-189)")
class CivilianNpcEntityTargetTest {

	@Test
	@DisplayName("removes only the given entity and keeps the rest of the queue in order")
	void removeEntityTarget_removesOnlyThatEntity_keepsOrder() throws Exception {
		CivilianNpc npc = realNpc();
		LivingEntity a = mock(LivingEntity.class);
		LivingEntity b = mock(LivingEntity.class);
		LivingEntity c = mock(LivingEntity.class);
		npc.addEntityTargetToFront(c);
		npc.addEntityTargetToFront(b);
		npc.addEntityTargetToFront(a);

		assertTrue(npc.removeEntityTarget(b));

		assertEquals(List.of(a, c), queue(npc));
	}

	@Test
	@DisplayName("returns false and changes nothing when the entity is not queued")
	void removeEntityTarget_absent_returnsFalse() throws Exception {
		CivilianNpc npc = realNpc();
		LivingEntity a = mock(LivingEntity.class);
		LivingEntity stranger = mock(LivingEntity.class);
		npc.addEntityTargetToFront(a);

		assertFalse(npc.removeEntityTarget(stranger));

		assertEquals(List.of(a), queue(npc));
	}

	/** A CivilianNpc whose real methods run without the Citizens/AbstractNpc constructor; only the queue is wired. */
	private static CivilianNpc realNpc() throws Exception {
		CivilianNpc npc = mock(CivilianNpc.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
		Field field = CivilianNpc.class.getDeclaredField("entityTargetQueue");
		field.setAccessible(true);
		field.set(npc, new java.util.ArrayDeque<LivingEntity>());
		return npc;
	}

	private static List<LivingEntity> queue(CivilianNpc npc) throws Exception {
		Field field = CivilianNpc.class.getDeclaredField("entityTargetQueue");
		field.setAccessible(true);
		@SuppressWarnings("unchecked")
		Deque<LivingEntity> deque = (Deque<LivingEntity>) field.get(npc);
		return new ArrayList<>(deque);
	}
}
