package org.luckyraven.gangland.gadget.jetpack.packet;

import org.luckyraven.gangland.gadget.jetpack.JetpackSession;
import org.luckyraven.gangland.gadget.jetpack.JetpackTask;
import org.luckyraven.gangland.gadget.packet.PlayerInputInterceptor;

/**
 * Writes the client's WASD/jump/sneak state from every {@code ServerboundPlayerInputPacket} into the active
 * {@link JetpackSession}. Injected on activation and removed on deactivation.
 *
 * <p>Runs on the Netty IO thread; the session's input fields are {@code volatile} so {@link JetpackTask} can read
 * them from the main thread. All NMS reflection lives in {@link PlayerInputInterceptor}.
 */
public final class JetpackInputInterceptor extends PlayerInputInterceptor<JetpackSession> {

	public static final String HANDLER_NAME = "gangland_jetpack_input";

	public JetpackInputInterceptor(JetpackSession session) {
		super(session);
	}

	@Override
	protected void apply(JetpackSession session, PlayerInput input) {
		session.setInputJump(input.jump());
		session.setInputForward(input.forward());
		session.setInputBackward(input.backward());
		session.setInputLeft(input.left());
		session.setInputRight(input.right());
		session.setInputSneak(input.sneak());
	}
}
