package creamy.oldfag_addons.modules;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.player.ChestSwap;
import meteordevelopment.orbit.EventHandler;
import creamy.oldfag_addons.Addon;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ElytraItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;


public class ElytraFlyPlus extends Module {
    public ElytraFlyPlus() {
        super(Addon.CATEGORY, "Elytra Fly+", "Better efly.");
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgExtras = settings.createGroup("Extras");

    //--------------------General--------------------//

    public final Setting<Double> horizontalSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("horizontal-speed")
        .description("How fast you go forward and backward.")
        .defaultValue(1)
        .min(0)
        .build()
    );

    private final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
        .name("Up Speed")
        .description("How many blocks to move each tick.")
        .defaultValue(1)
        .min(0)
        .sliderRange(0, 5)
        .build()
    );
    private final Setting<Double> upMultiplier = sgGeneral.add(new DoubleSetting.Builder()
        .name("Up Multiplier")
        .description("How many times faster should we fly up.")
        .defaultValue(1)
        .min(0)
        .sliderRange(0, 5)
        .build()
    );
    private final Setting<Double> down = sgGeneral.add(new DoubleSetting.Builder()
        .name("Down Speed")
        .description("How many blocks to move down each tick.")
        .defaultValue(1)
        .min(0)
        .sliderRange(0, 5)
        .build()
    );
    private final Setting<Double> fallSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("Fall Speed")
        .description("How many blocks to fall down each tick.")
        .defaultValue(0.01)
        .min(0)
        .sliderRange(0, 1)
        .build()
    );

    public final Setting<Boolean> elytraFlyacceleration = sgGeneral.add(new BoolSetting.Builder()
        .name("acceleration")
        .defaultValue(false)
        .build()
    );

    public final Setting<Double> elytraFlyaccelerationStep = sgGeneral.add(new DoubleSetting.Builder()
        .name("acceleration-step")
        .min(0.1)
        .max(5)
        .defaultValue(1)
        .visible(() -> elytraFlyacceleration.get())
        .build()
    );

    public final Setting<Double> elytraFlyaccelerationMin = sgGeneral.add(new DoubleSetting.Builder()
        .name("acceleration-start")
        .min(0.1)
        .defaultValue(0)
        .visible(() -> elytraFlyacceleration.get())
        .build()
    );

    public final Setting<Boolean> stopInWater = sgExtras.add(new BoolSetting.Builder()
        .name("stop-in-water")
        .description("Stops flying in water.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> dontGoIntoUnloadedChunks = sgExtras.add(new BoolSetting.Builder()
        .name("no-unloaded-chunks")
        .description("Stops you from going into unloaded chunks.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> noCrash = sgExtras.add(new BoolSetting.Builder()
        .name("no-crash")
        .description("Stops you from going into walls.")
        .defaultValue(false)
        .build()
    );

    public final Setting<Integer> crashLookAhead = sgExtras.add(new IntSetting.Builder()
        .name("crash-look-ahead")
        .description("Distance to look ahead when flying.")
        .defaultValue(5)
        .range(1, 15)
        .sliderMin(1)
        .visible(noCrash::get)
        .build()
    );

    public final Setting<Boolean> replace = sgExtras.add(new BoolSetting.Builder()
        .name("elytra-replace")
        .description("Replaces broken elytra with a new elytra.")
        .defaultValue(false)
        .build()
    );

    public final Setting<Integer> replaceDurability = sgExtras.add(new IntSetting.Builder()
        .name("replace-durability")
        .description("The durability threshold your elytra will be replaced at.")
        .defaultValue(2)
        .range(1, Items.ELYTRA.getMaxDamage() - 1)
        .sliderRange(1, Items.ELYTRA.getMaxDamage() - 1)
        .visible(replace::get)
        .build()
    );

    public final Setting<ChestSwapMode> chestSwap = sgExtras.add(new EnumSetting.Builder<ChestSwapMode>()
        .name("chest-swap")
        .description("Enables ChestSwap when toggling this module.")
        .defaultValue(ChestSwapMode.WaitForGround)
        .build()
    );

    private boolean moving;
    private float yaw;
    private float pitch;
    private float p;
    private double velocity;
    private final Vec3d vec3d = new Vec3d(0,0,0);
    protected boolean lastForwardPressed;
    protected double velX, velY, velZ;
    protected Vec3d forward, right;
    protected double acceleration;

    private class StaticGroundListener {
        @EventHandler
        private void chestSwapGroundListener(PlayerMoveEvent event) {
            if (mc.player != null && mc.player.isOnGround()) {
                if (mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA) {
                    Modules.get().get(ChestSwap.class).swap();
                    disableGroundListener();
                }
            }
        }
    }
    private final StaticGroundListener staticGroundListener = new StaticGroundListener();

    public void handleFallMultiplier() {
        if (velY < 0) velY *= 0;
        else if (velY > 0) velY = 0;
    }

    protected void enableGroundListener() {
        MeteorClient.EVENT_BUS.subscribe(staticGroundListener);
    }

    protected void disableGroundListener() {
        MeteorClient.EVENT_BUS.unsubscribe(staticGroundListener);
    }

    public void handleAcceleration() {
        if (elytraFlyacceleration.get()) {
            if (!PlayerUtils.isMoving()) acceleration = 0;
            acceleration = Math.min(
                acceleration + elytraFlyaccelerationMin.get() + elytraFlyaccelerationStep.get() * .1,
                horizontalSpeed.get()
            );
        } else {
            acceleration = 0;
        }
    }

    protected double getSpeed() {
        return elytraFlyacceleration.get() ? acceleration : horizontalSpeed.get();
    }

    public void handleHorizontalSpeed(PlayerMoveEvent event) {
        boolean a = false;
        boolean b = false;

        if (mc.options.forwardKey.isPressed()) {
            velX += forward.x * getSpeed() * 10;
            velZ += forward.z * getSpeed() * 10;
            a = true;
        } else if (mc.options.backKey.isPressed()) {
            velX -= forward.x * getSpeed() * 10;
            velZ -= forward.z * getSpeed() * 10;
            a = true;
        }

        if (mc.options.rightKey.isPressed()) {
            velX += right.x * getSpeed() * 10;
            velZ += right.z * getSpeed() * 10;
            b = true;
        } else if (mc.options.leftKey.isPressed()) {
            velX -= right.x * getSpeed() * 10;
            velZ -= right.z * getSpeed() * 10;
            b = true;
        }

        if (a && b) {
            double diagonal = 1 / Math.sqrt(2);
            velX *= diagonal;
            velZ *= diagonal;
        }
    }

    @Override
    public void onActivate() {
        acceleration = 0;
        if ((chestSwap.get() == ChestSwapMode.Always || chestSwap.get() == ChestSwapMode.WaitForGround)
                && mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() != Items.ELYTRA) {
            Modules.get().get(ChestSwap.class).swap();
        }
    }

    public void onDeactivate() {
        if (chestSwap.get() == ChestSwapMode.Always && mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA) {
            Modules.get().get(ChestSwap.class).swap();
        } else if (chestSwap.get() == ChestSwapMode.WaitForGround) {
            enableGroundListener();
        }
        mc.player.getAbilities().flying = false;
        mc.player.getAbilities().allowFlying = false;
    }

    @EventHandler
    public void onTick(TickEvent.Post event) {
        if (replace.get()) {
            ItemStack chestStack = mc.player.getInventory().getArmorStack(2);
            if (chestStack.getItem() == Items.ELYTRA) {
                if (chestStack.getMaxDamage() - chestStack.getDamage() <= replaceDurability.get()) {
                    FindItemResult elytra = InvUtils.find(stack -> stack.getMaxDamage() - stack.getDamage() > replaceDurability.get() && stack.getItem() == Items.ELYTRA);
                    InvUtils.move().from(elytra.slot()).toArmor(2);
                }
            }
        }

        if (mc.player.getInventory().getArmorStack(2).getItem() != Items.ELYTRA || mc.player.fallDistance <= 0.2 || mc.options.sneakKey.isPressed()) return;

        if (mc.options.forwardKey.isPressed()) {
            vec3d.add(0, 0, horizontalSpeed.get());
            vec3d.rotateY(-(float) Math.toRadians(mc.player.getYaw()));
        } else if (mc.options.backKey.isPressed()) {
            vec3d.add(0, 0, horizontalSpeed.get());
            vec3d.rotateY((float) Math.toRadians(mc.player.getYaw()));
        }

        mc.player.setVelocity(vec3d);
        mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true));

    }

    public void onPacketSend(PacketEvent.Send event) {
        if (event.packet instanceof PlayerMoveC2SPacket) {
            mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!(mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() instanceof ElytraItem)) return;
        if (mc.player.isFallFlying()) {
            velX = 0;
            velY = event.movement.y;
            velZ = 0;
            forward = Vec3d.fromPolar(0, mc.player.getYaw()).multiply(0.1);
            right = Vec3d.fromPolar(0, mc.player.getYaw() + 90).multiply(0.1);

            // Handle stopInWater
            if (mc.player.isTouchingWater() && stopInWater.get()) {
                mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                return;
            }

            handleFallMultiplier();
            handleAcceleration();
            handleHorizontalSpeed(event);

            int chunkX = (int) ((mc.player.getX() + velX) / 16);
            int chunkZ = (int) ((mc.player.getZ() + velZ) / 16);
            if (dontGoIntoUnloadedChunks.get()) {
                if (mc.world.getChunkManager().isChunkLoaded(chunkX, chunkZ)) {
                    ((IVec3d) event.movement).set(velX, velY, velZ);
                } else {
                    ((IVec3d) event.movement).set(0, velY, 0);
                }
            } else ((IVec3d) event.movement).set(velX, velY, velZ);

            mc.player.getAbilities().flying = true;
            mc.player.getAbilities().setFlySpeed(horizontalSpeed.get().floatValue() / 20);
        } else {
            if (lastForwardPressed) {
                mc.options.forwardKey.setPressed(false);
                lastForwardPressed = false;
            }
        }
        if (noCrash.get() && mc.player.isFallFlying()) {
            Vec3d lookAheadPos = mc.player.getPos().add(mc.player.getVelocity().normalize().multiply(crashLookAhead.get()));
            RaycastContext raycastContext = new RaycastContext(mc.player.getPos(), new Vec3d(lookAheadPos.getX(), mc.player.getY(), lookAheadPos.getZ()), RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player);
            BlockHitResult hitResult = mc.world.raycast(raycastContext);
            if (hitResult != null && hitResult.getType() == HitResult.Type.BLOCK) {
                ((IVec3d) event.movement).set(0, velY, 0);
            }
        }
    }

    public boolean canPacketEfly() {
        return isActive() && mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() instanceof ElytraItem && !mc.player.isOnGround();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onMove(PlayerMoveEvent event) {
        controlTick(event);
    }
    
    private void controlTick(PlayerMoveEvent event) {
        if (!mc.player.isFallFlying()) {return;}

        updateControlMovement();
        pitch = 0;

        boolean movingUp = false;

        if (!mc.options.sneakKey.isPressed() && mc.options.jumpKey.isPressed() && velocity > speed.get() * 0.4) {
            p = (float) Math.min(p + 0.1 * (1 - p) * (1 - p) * (1 - p), 1f);

            pitch = Math.max(Math.max(p, 0) * -90, -90);

            movingUp = true;
            moving = false;
        } else {
            velocity = speed.get();
            p = -0.2f;
        }

        velocity = moving ? speed.get() : Math.min(velocity + Math.sin(Math.toRadians(pitch)) * 0.08, speed.get());

        double cos = Math.cos(Math.toRadians(yaw + 90));
        double sin = Math.sin(Math.toRadians(yaw + 90));

        double x = moving && !movingUp ? cos * speed.get() : movingUp ? velocity * Math.cos(Math.toRadians(pitch)) * cos : 0;
        double y = pitch < 0 ? velocity * upMultiplier.get() * -Math.sin(Math.toRadians(pitch)) * velocity : -fallSpeed.get();
        double z = moving && !movingUp ? sin * speed.get() : movingUp ? velocity * Math.cos(Math.toRadians(pitch)) * sin : 0;

        y *= Math.abs(Math.sin(Math.toRadians(movingUp ? pitch : mc.player.getPitch())));

        if (mc.options.sneakKey.isPressed() && !mc.options.jumpKey.isPressed()) {
            y = -down.get();
        }

        ((IVec3d) event.movement).set(x, y, z);
        mc.player.setVelocity(0, 0, 0);
    }

    private void updateControlMovement() {
        float yaw = mc.player.getYaw();

        float f = mc.player.input.movementForward;
        float s = mc.player.input.movementSideways;

        if (f > 0) {
            moving = true;
            yaw += s > 0 ? -45 : s < 0 ? 45 : 0;
        } else if (f < 0) {
            moving = true;
            yaw += s > 0 ? -135 : s < 0 ? 135 : 180;
        } else {
            moving = s != 0;
            yaw += s > 0 ? -90 : s < 0 ? 90 : 0;
        }
        this.yaw = yaw;
    }

    public enum ChestSwapMode {
        Always,
        Never,
        WaitForGround
    }

}
