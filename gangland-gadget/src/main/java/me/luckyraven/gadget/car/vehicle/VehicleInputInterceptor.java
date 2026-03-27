package me.luckyraven.gadget.car.vehicle;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Netty channel handler that intercepts {@code ServerboundPlayerInputPacket} packets before the server processes them.
 * The WASD state is written into the active {@link VehicleSession} on every incoming steering tick.
 *
 * <p>Injected into the player's pipeline at {@code "packet_handler"} on car spawn and removed on
 * despawn. All session input fields are {@code volatile} because this runs on the Netty IO thread while
 * {@link VehicleMovementTask} runs on the main server thread.
 *
 * <p>Two NMS APIs are supported automatically via reflection:
 * <ul>
 *   <li><b>1.21.2+</b> — {@code input()} returns an {@code Input} record with boolean accessors
 *       {@code forward()}, {@code backward()}, {@code left()}, {@code right()}.</li>
 *   <li><b>pre-1.21.2</b> — float record accessors {@code xxa()} and {@code zza()}.</li>
 * </ul>
 */
public class VehicleInputInterceptor extends ChannelInboundHandlerAdapter {

	public static final String HANDLER_NAME = "gangland_vehicle_input";

	// --- shared reflection state, resolved once at first constructor call ---
	private static volatile boolean  reflectionReady = false;
	private static          Class<?> packetClass;
	private static          boolean  useNewApi;

	// 1.21.2+ Input-record API
	private static Method inputMethod;
	private static Method forwardMethod;
	private static Method backwardMethod;
	private static Method leftMethod;
	private static Method rightMethod;

	// pre-1.21.2 float API
	private static Method xxaMethod;
	private static Method zzaMethod;
	// -----------------------------------------------------------------------

	private final VehicleSession session;

	public VehicleInputInterceptor(VehicleSession session) {
		this.session = session;
		ensureReflectionReady();
	}

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
		synchronized (VehicleInputInterceptor.class) {
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
					useNewApi      = true;
				} catch (NoSuchMethodException ignored) {
					// Pre-1.21.2: float record accessors
					xxaMethod = packetClass.getMethod("xxa");
					zzaMethod = packetClass.getMethod("zza");
					useNewApi = false;
				}
			} catch (Exception ignored) {
				packetClass = null;
			} finally {
				reflectionReady = true;
			}
		}
	}

	// -----------------------------------------------------------------------

	private static Object getField(Object obj, String name) throws Exception {
		Field field = findField(obj.getClass(), name);
		if (field == null) throw new NoSuchFieldException(name + " on " + obj.getClass());
		field.setAccessible(true);
		return field.get(obj);
	}

	// -----------------------------------------------------------------------

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
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		if (reflectionReady && packetClass != null && packetClass.isInstance(msg)) {
			updateInput(msg);
		}
		super.channelRead(ctx, msg);
	}

	private void updateInput(Object packet) {
		try {
			if (useNewApi) {
				Object  input = inputMethod.invoke(packet);
				boolean fwd   = (boolean) forwardMethod.invoke(input);
				boolean bwd   = (boolean) backwardMethod.invoke(input);
				boolean lft   = (boolean) leftMethod.invoke(input);
				boolean rgt   = (boolean) rightMethod.invoke(input);
				session.setInput(fwd, bwd, lft, rgt);
			} else {
				float xxa = (float) xxaMethod.invoke(packet);
				float zza = (float) zzaMethod.invoke(packet);
				// W key → zza > 0  |  S key → zza < 0
				// A key → xxa > 0  |  D key → xxa < 0  (client convention for pre-1.21.2)
				session.setInput(zza > 0.001f, zza < -0.001f, xxa > 0.001f, xxa < -0.001f);
			}
		} catch (Exception ignored) { }
	}
}
