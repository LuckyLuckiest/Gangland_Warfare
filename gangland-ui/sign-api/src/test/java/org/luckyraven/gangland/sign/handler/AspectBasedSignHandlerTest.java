package org.luckyraven.gangland.sign.handler;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.sign.aspect.AspectResult;
import org.luckyraven.gangland.sign.aspect.SignAspect;
import org.luckyraven.gangland.sign.model.ParsedSign;
import org.luckyraven.gangland.sign.registry.SignTypeDefinition;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Proves {@link AspectBasedSignHandler#handle}/{@code #canHandle} (Test Surface,
 * lootchests-signs-waypoints.md: "AspectBasedSignHandler#handle/canHandle with fake aspects -
 * stop-on-failure, continueExecution, and a test asserting the intended priority order (currently
 * failing, see issue 4)").
 *
 * <p>{@code executedOrder} recording fakes avoid a Mockito chain and read directly as the aspect
 * sequence under test.
 */
@DisplayName("AspectBasedSignHandler - aspect chain execution")
class AspectBasedSignHandlerTest {

	private final Player player = mock(Player.class);
	private final ParsedSign sign = mock(ParsedSign.class);

	@Test
	@DisplayName("every aspect that continues runs, in construction order")
	void handle_allAspectsContinue_runsAllInOrder() {
		List<String> executed = new ArrayList<>();
		FakeAspect first = new FakeAspect("first", executed, true, AspectResult.successContinue("ok-1"), 0);
		FakeAspect second = new FakeAspect("second", executed, true, AspectResult.successContinue("ok-2"), 0);

		AspectBasedSignHandler handler = new AspectBasedSignHandler(List.of(first, second));
		List<AspectResult> results = handler.handle(player, sign);

		assertEquals(List.of("first", "second"), executed);
		assertEquals(2, results.size());
		assertTrue(results.get(0).isSuccess());
		assertTrue(results.get(1).isSuccess());
	}

	@Test
	@DisplayName("an aspect result with continueExecution=false stops the chain - later aspects never run")
	void handle_stopsWhenContinueExecutionFalse() {
		List<String> executed = new ArrayList<>();
		FakeAspect first = new FakeAspect("first", executed, true, AspectResult.successStop("stop-here"), 0);
		FakeAspect second = new FakeAspect("second", executed, true, AspectResult.successContinue("never"), 0);

		AspectBasedSignHandler handler = new AspectBasedSignHandler(List.of(first, second));
		List<AspectResult> results = handler.handle(player, sign);

		assertEquals(List.of("first"), executed, "second aspect must not execute after a successStop result");
		assertEquals(1, results.size());
	}

	@Test
	@DisplayName("an aspect whose canExecute fails short-circuits with a failure result and never calls execute")
	void handle_canExecuteFalse_shortCircuitsWithFailure() {
		List<String> executed = new ArrayList<>();
		FakeAspect blocked = new FakeAspect("blocked", executed, false, AspectResult.successContinue("unreachable"), 0);
		FakeAspect after = new FakeAspect("after", executed, true, AspectResult.successContinue("unreachable-too"), 0);

		AspectBasedSignHandler handler = new AspectBasedSignHandler(List.of(blocked, after));
		List<AspectResult> results = handler.handle(player, sign);

		assertTrue(executed.isEmpty(), "execute() must never run for an aspect whose canExecute() is false");
		assertEquals(1, results.size());
		assertFalse(results.get(0).isSuccess());
		assertEquals("blocked: Preconditions not met", results.get(0).getMessage());
	}

	@Test
	@DisplayName("canHandle is true only when every aspect's canExecute is true")
	void canHandle_requiresEveryAspectToPass() {
		FakeAspect ok = new FakeAspect("ok", new ArrayList<>(), true, AspectResult.successContinue(""), 0);
		FakeAspect blocked = new FakeAspect("blocked", new ArrayList<>(), false, AspectResult.successContinue(""), 0);

		AspectBasedSignHandler allPass = new AspectBasedSignHandler(List.of(ok));
		AspectBasedSignHandler onePasses = new AspectBasedSignHandler(List.of(ok, blocked));

		assertTrue(allPass.canHandle(player, sign));
		assertFalse(onePasses.canHandle(player, sign));
	}

	@Test
	@DisplayName("Observation #4/#21 (lootchests-signs-waypoints.md): handle() runs aspects in raw "
	             + "construction order, not by SignAspect#getPriority - the priority mechanism "
	             + "(SignTypeDefinition#getSortedAspects) is never consulted by this handler")
	void handle_ignoresAspectPriority_pinsObservation4() {
		List<String> executed = new ArrayList<>();
		// constructed low-priority-first, high-priority-second - a priority-aware handler would flip this.
		FakeAspect lowPriorityFirst = new FakeAspect("low", executed, true, AspectResult.successContinue(""), 1);
		FakeAspect highPrioritySecond = new FakeAspect("high", executed, true, AspectResult.successContinue(""), 100);

		AspectBasedSignHandler handler = new AspectBasedSignHandler(List.of(lowPriorityFirst, highPrioritySecond));
		handler.handle(player, sign);

		assertEquals(List.of("low", "high"), executed, "construction order is respected, not priority order");

		// SignTypeDefinition#getSortedAspects, if it had been consulted, would have reordered these.
		SignTypeDefinition definition = SignTypeDefinition.builder()
		                                                  .aspects(List.of(lowPriorityFirst, highPrioritySecond))
		                                                  .build();
		assertEquals(List.of(highPrioritySecond, lowPriorityFirst), definition.getSortedAspects(),
		             "getSortedAspects itself is correct - it is simply never called by AspectBasedSignHandler");
	}

	private static final class FakeAspect implements SignAspect {

		private final String name;
		private final List<String> executionLog;
		private final boolean canExecute;
		private final AspectResult result;
		private final int priority;

		private FakeAspect(String name, List<String> executionLog, boolean canExecute, AspectResult result,
		                   int priority) {
			this.name = name;
			this.executionLog = executionLog;
			this.canExecute = canExecute;
			this.result = result;
			this.priority = priority;
		}

		@Override
		public AspectResult execute(Player player, ParsedSign sign) {
			executionLog.add(name);
			return result;
		}

		@Override
		public boolean canExecute(Player player, ParsedSign sign) {
			return canExecute;
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public int getPriority() {
			return priority;
		}

	}

}
