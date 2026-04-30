package org.luckyraven.gangland.gadget.packet;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Netty channel handler base that intercepts {@code ServerboundPlayerInputPacket} packets before the server processes
 * them, parses the client's WASD/jump/sneak state, and delegates the parsed value to a feature-specific
 * {@link #apply(Object, PlayerInput)} hook.
 *
 * <p>Concrete subclasses (e.g. the car and jetpack interceptors) only declare their own {@code HANDLER_NAME}
 * constant and implement {@link #apply(Object, PlayerInput)} to write the relevant fields into their session. All NMS
 * reflection, version detection, and pipeline guards are owned here.
 *
 * <p>Two NMS APIs are supported automatically:
 * <ul>
 *   <li><b>1.21.2+</b> — {@code input()} returns an {@code Input} record with boolean accessors
 *       {@code forward()}, {@code backward()}, {@code left()}, {@code right()}, {@code jump()}, {@code shift()}.</li>
 *   <li><b>pre-1.21.2</b> — packet exposes {@code jumping()}, float {@code xxa()}/{@code zza()}, and optionally
 *       {@code shiftKeyDown()}.</li>
 * </ul>
 *
 * <p>All input fields in the subclass's session should be {@code volatile} because {@link #channelRead} runs on the
 * Netty IO thread while the consumer (movement task) typically runs on the main server thread.
 *
 * @param <S> session type owned by the concrete feature
 */
public abstract class PlayerInputInterceptor<S> extends ChannelInboundHandlerAdapter {

	// --- shared reflection state, resolved once on first construction ---
	private static volatile boolean  reflectionReady = false;
	private static          Class<?> packetClass;
	private static          boolean  useNewApi;

	// 1.21.2+ Input-record API
	private static Method inputMethod;
	private static Method forwardMethod;
	private static Method backwardMethod;
	private static Method leftMethod;
	private static Method rightMethod;
	private static Method jumpMethod;
	private static Method sneakMethod;

	// pre-1.21.2 float API
	private static Method xxaMethod;
	private static Method zzaMethod;
	private static Method jumpingMethod;
	private static Method shiftKeyDownMethod;
	// ---------------------------------------------------------------------

	private final S session;

	protected PlayerInputInterceptor(S session) {
		this.session = session;
		ensureReflectionReady();
	}

	/**
	 * Invoked on the Netty IO thread for every {@code ServerboundPlayerInputPacket}. Implementations write the fields
	 * they care about into {@code session}; unused fields can simply be ignored.
	 */
	protected abstract void apply(S session, PlayerInput input);

	/**
	 * Returns the Netty {@link Channel} backing the player's server connection, or {@code null} if it cannot be
	 * retrieved via reflection.
	 *
	 * <p>Field path (Mojang-mapped 1.17+):
	 * {@code ServerPlayer.connection} (SGPLI) → {@code ServerCommonPacketListenerImpl.connection} (Connection) →
	 * {@code Connection.channel}.
	 */
	@Nullable
	public static Channel getChannel(Player player) {
		try {
			Object serverPlayer   = player.getClass().getMethod("getHandle").invoke(player);
			Object packetListener = getField(serverPlayer, "connection");   // ServerGamePacketListenerImpl
			Object connection     = getField(packetListener, "connection"); // Connection
			return (Channel) getField(connection, "channel");
		} catch (Exception ignored) {
			return null;
		}
	}

	private static void ensureReflectionReady() {
		if (reflectionReady) return;
		synchronized (PlayerInputInterceptor.class) {
			if (reflectionReady) return;
			try {
				packetClass = Class.forName(
						"net.minecraft.network.protocol.game.ServerboundPlayerInputPacket");

				// Try 1.21.2+ API first: packet exposes input() → Input record
				try {
					inputMethod = packetClass.getMethod("input");
					Class<?> inputCls = inputMethod.getReturnType();
					forwardMethod  = inputCls.getMethod("forward");
					backwardMethod = inputCls.getMethod("backward");
					leftMethod     = inputCls.getMethod("left");
					rightMethod    = inputCls.getMethod("right");
					jumpMethod     = inputCls.getMethod("jump");
					sneakMethod    = inputCls.getMethod("shift");
					useNewApi      = true;
				} catch (NoSuchMethodException ignored) {
					// Pre-1.21.2: float record accessors, with optional sneak
					xxaMethod     = packetClass.getMethod("xxa");
					zzaMethod     = packetClass.getMethod("zza");
					jumpingMethod = packetClass.getMethod("jumping");
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

	private static Object getField(Object obj, String name) throws Exception {
		Field field = findField(obj.getClass(), name);
		if (field == null) throw new NoSuchFieldException(name + " on " + obj.getClass());
		field.setAccessible(true);
		return field.get(obj);
	}

	private static Field findField(Class<?> cls, String name) {
		while (cls != null) {
			try {
				return cls.getDeclaredField(name);
			} catch (NoSuchFieldException ignored) {
				cls = cls.getSuperclass();
			}
		}
		return null;
	}

	@Override
	public final void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		if (reflectionReady && packetClass != null && packetClass.isInstance(msg)) {
			try {
				apply(session, parsePacket(msg));
			} catch (Exception ignored) {
			}
		}
		super.channelRead(ctx, msg);
	}

	private PlayerInput parsePacket(Object packet) throws ReflectiveOperationException {
		if (useNewApi) {
			Object  in    = inputMethod.invoke(packet);
			boolean fwd   = (boolean) forwardMethod.invoke(in);
			boolean bwd   = (boolean) backwardMethod.invoke(in);
			boolean lft   = (boolean) leftMethod.invoke(in);
			boolean rgt   = (boolean) rightMethod.invoke(in);
			boolean jump  = (boolean) jumpMethod.invoke(in);
			boolean sneak = (boolean) sneakMethod.invoke(in);
			return new PlayerInput(fwd, bwd, lft, rgt, jump, sneak);
		}

		float   xxa   = (float) xxaMethod.invoke(packet);
		float   zza   = (float) zzaMethod.invoke(packet);
		boolean jump  = (boolean) jumpingMethod.invoke(packet);
		boolean sneak = shiftKeyDownMethod != null && (boolean) shiftKeyDownMethod.invoke(packet);
		// W key → zza > 0  |  S key → zza < 0  |  A key → xxa > 0  |  D key → xxa < 0
		return new PlayerInput(zza > 0.001f, zza < -0.001f, xxa > 0.001f, xxa < -0.001f, jump, sneak);
	}

	/**
	 * Parsed WASD/jump/sneak state for a single input packet. All fields are boolean regardless of the underlying NMS
	 * API version. {@code jump} and {@code sneak} are {@code false} on server versions that don't expose them.
	 */
	public record PlayerInput(boolean forward, boolean backward, boolean left, boolean right, boolean jump,
	                          boolean sneak) {
	}
}
