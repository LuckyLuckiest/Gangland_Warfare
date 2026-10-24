package org.luckyraven.gangland.gadget.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.gadget.listener.grapple.GrappleFallDamageListener;
import org.luckyraven.gangland.gadget.listener.jetpack.JetpackFallDamageListener;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** cnc-integ T-381: gadget fall-cancel runs at NORMAL so Busted (HIGH) sees the cancelled event and skips it. */
class FallCancelPriorityTest {

	private static EventPriority priorityOf(Class<?> type) {
		for (Method method : type.getDeclaredMethods()) {
			EventHandler handler = method.getAnnotation(EventHandler.class);
			if (handler != null && method.getParameterTypes().length == 1
			    && method.getParameterTypes()[0] == EntityDamageEvent.class) {
				return handler.priority();
			}
		}
		throw new AssertionError("no EntityDamageEvent handler on " + type);
	}

	@Test
	@DisplayName("T-381: Jetpack fall-cancel is NORMAL")
	void jetpackFallCancelIsNormal() {
		assertEquals(EventPriority.NORMAL, priorityOf(JetpackFallDamageListener.class));
	}

	@Test
	@DisplayName("T-381: Grapple fall-cancel is NORMAL")
	void grappleFallCancelIsNormal() {
		assertEquals(EventPriority.NORMAL, priorityOf(GrappleFallDamageListener.class));
	}
}
