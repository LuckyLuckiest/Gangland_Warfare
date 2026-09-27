package org.luckyraven.gangland.copsncrooks.listener.detainment;

import org.junit.jupiter.api.DisplayName;

/**
 * Review fix round 1 (spec 4.9) added a {@code cop.leaveSquad()} call to {@link CopListener#onCopDeath} ahead of
 * {@code destroy()}, and a test asserting that order. Reverted: {@code isCopNpc}/{@code findCopByEntity} require
 * {@code cop.isValid()}, which is false during {@code EntityDeathEvent} (the entity is already dead), so the branch
 * never ran outside the test's mock - the AI sweep's {@code CopGroup.release} already drops a dead cop from the squad
 * within one AI tick. The pre-existing unreachable handler itself is tracked separately (docket T-111).
 */
@DisplayName("CopListener - cop death")
class CopListenerTest {
}
