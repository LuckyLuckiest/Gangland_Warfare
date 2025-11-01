package me.luckyraven.compatibility.version.recoil;

import me.luckyraven.feature.weapon.projectile.recoil.RecoilCompatibility;
import net.minecraft.network.protocol.game.PacketPlayOutPosition;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3D;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_21_R4.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class Recoil_1_21_R4 extends RecoilCompatibility {

	private final Set<Relative> ABSOLUTE_FLAGS = new HashSet<>(Arrays.asList(Relative.a, Relative.b, Relative.c));
	private final Set<Relative> RELATIVE_FLAGS = new HashSet<>(
			Arrays.asList(Relative.a, Relative.b, Relative.c, Relative.e, Relative.d));

	@Override
	public void modifyCameraRotation(@NotNull Player player, float yaw, float pitch, boolean position) {
		float newYaw   = -yaw + 1;
		float newPitch = pitch - 1;

		Location playerLocation = player.getLocation();
		Vec3D    movePosition   = new Vec3D(playerLocation.getX(), playerLocation.getY(), playerLocation.getZ());

		PositionMoveRotation moveRotation = new PositionMoveRotation(movePosition, movePosition, newYaw, newPitch);
		PacketPlayOutPosition packet = new PacketPlayOutPosition(0, moveRotation,
																 position ? RELATIVE_FLAGS : ABSOLUTE_FLAGS);

		(((CraftPlayer) player).getHandle()).f.b(packet);
	}

}
