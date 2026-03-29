package me.luckyraven.gadget.jetpack;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.lang.reflect.Method;

/**
 * Netty channel handler that intercepts {@code ServerboundPlayerInputPacket} packets to read the jump key and WASD
 * directional input. All state is written into {@link JetpackSession} volatile fields on the Netty IO thread and
 * consumed by {@link JetpackTask} on the main thread.
 *
 * <p>Two NMS APIs are supported via reflection:
 * <ul>
 *   <li><b>1.21.2+</b> — {@code input()} returns an {@code Input} record with boolean accessors
 *       {@code jump()}, {@code forward()}, {@code backward()}, {@code left()}, {@code right()}.</li>
 *   <li><b>pre-1.21.2</b> — packet exposes {@code jumping()}, {@code xxa()}, {@code zza()}.</li>
 * </ul>
 */
public class JetpackInputInterceptor extends ChannelInboundHandlerAdapter {

	public static final String HANDLER_NAME = "gangland_jetpack_input";

	private static volatile boolean  reflectionReady = false;
	private static          Class<?> packetClass;
	private static          boolean  useNewApi;

	// 1.21.2+ Input-record API
	private static Method inputMethod;
	private static Method jumpMethod;
	private static Method forwardMethod;
	private static Method backwardMethod;
	private static Method leftMethod;
	private static Method rightMethod;
	private static Method sneakMethod;

	// pre-1.21.2
	private static Method jumpingMethod;
	private static Method xxaMethod;
	private static Method zzaMethod;
	private static Method shiftKeyDownMethod;

	private final JetpackSession session;

	public JetpackInputInterceptor(JetpackSession session) {
		this.session = session;
		ensureReflectionReady();
	}

	private static void ensureReflectionReady() {
		if (reflectionReady) return;
		synchronized (JetpackInputInterceptor.class) {
			if (reflectionReady) return;
			try {
				packetClass = Class.forName(
						"net.minecraft.network.protocol.game.ServerboundPlayerInputPacket");

				try {
					inputMethod = packetClass.getMethod("input");
					Class<?> inputCls = inputMethod.getReturnType();
					jumpMethod     = inputCls.getMethod("jump");
					forwardMethod  = inputCls.getMethod("forward");
					backwardMethod = inputCls.getMethod("backward");
					leftMethod     = inputCls.getMethod("left");
					rightMethod    = inputCls.getMethod("right");
					sneakMethod    = inputCls.getMethod("shift");
					useNewApi      = true;
				} catch (NoSuchMethodException ignored) {
					jumpingMethod = packetClass.getMethod("jumping");
					xxaMethod     = packetClass.getMethod("xxa");
					zzaMethod     = packetClass.getMethod("zza");
					try {
						shiftKeyDownMethod = packetClass.getMethod("shiftKeyDown");
					} catch (NoSuchMethodException ignored2) {
						// sneak input unavailable for this server version
					}
					useNewApi = false;
				}
			} catch (Exception ignored) {
				packetClass = null;
			} finally {
				reflectionReady = true;
			}
		}
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		if (reflectionReady && packetClass != null && packetClass.isInstance(msg)) {
			updateInput(msg);
		}
		super.channelRead(ctx, msg);
	}

	private void updateInput(Object packet) {
		try {
			boolean jump, fwd, bwd, lft, rgt, sneak;
			if (useNewApi) {
				Object input = inputMethod.invoke(packet);
				jump  = (boolean) jumpMethod.invoke(input);
				fwd   = (boolean) forwardMethod.invoke(input);
				bwd   = (boolean) backwardMethod.invoke(input);
				lft   = (boolean) leftMethod.invoke(input);
				rgt   = (boolean) rightMethod.invoke(input);
				sneak = (boolean) sneakMethod.invoke(input);
			} else {
				jump = (boolean) jumpingMethod.invoke(packet);
				float xxa = (float) xxaMethod.invoke(packet);
				float zza = (float) zzaMethod.invoke(packet);
				// W → zza > 0 | S → zza < 0 | A → xxa > 0 | D → xxa < 0
				fwd   = zza > 0.001f;
				bwd   = zza < -0.001f;
				lft   = xxa > 0.001f;
				rgt   = xxa < -0.001f;
				sneak = shiftKeyDownMethod != null && (boolean) shiftKeyDownMethod.invoke(packet);
			}
			session.setInputJump(jump);
			session.setInputForward(fwd);
			session.setInputBackward(bwd);
			session.setInputLeft(lft);
			session.setInputRight(rgt);
			session.setInputSneak(sneak);
		} catch (Exception ignored) {
		}
	}

}
