package com.annis.interact.client;

import com.annis.interact.Touch;
import com.annis.interact.net.TouchPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * Modo mimos (quien toca): la camara se pone de costado, el raton queda libre y, con clic
 * izquierdo y arrastrando, se acaricia el pelo o la mejilla, se toma la mano o se le mueve el brazo,
 * o se dan palmaditas en el hombro. Rueda o clic derecho arrastrando: girar la camara.
 * Shift o Esc: terminar.
 */
public class TouchScreen extends Screen {
    private final int receiverId;
    private CameraType before;
    private boolean dragging;
    private boolean orbiting;
    private int zone = Touch.NONE;
    private float[] start = new float[3];
    private float[] point = new float[3];
    private Vec3 planePoint;
    private Vec3 planeNormal;
    private boolean pending;
    private boolean endedBySession;
    private int hover = Touch.NONE;
    private int age;
    private boolean hudBefore;

    /**
     * Primera persona = la camara normal del juego en primera persona (compatible con mods de camara),
     * pero dibujando el propio cuerpo sin cabeza; la mano de primera persona se oculta.
     * De costado = tercera persona con la camara girando alrededor de los dos.
     */
    private void applyView() {
        if (this.minecraft == null) {
            return;
        }
        this.minecraft.options.setCameraType(TouchCam.firstPerson ? CameraType.FIRST_PERSON : CameraType.THIRD_PERSON_BACK);
        this.minecraft.options.hideGui = TouchCam.firstPerson || this.hudBefore;
    }

    public TouchScreen(int receiverId) {
        super(Component.literal("Mimos"));
        this.receiverId = receiverId;
    }

    @Override
    protected void init() {
        if (this.before == null && this.minecraft != null) {
            this.before = this.minecraft.options.getCameraType();
            this.hudBefore = this.minecraft.options.hideGui;
        }
        TouchCam.receiver = this.receiverId;
        TouchCam.active = true;
        applyView();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        // sin fondo oscuro
    }

    private Entity receiver() {
        return this.minecraft == null || this.minecraft.level == null ? null : this.minecraft.level.getEntity(this.receiverId);
    }

    private float pt() {
        return this.minecraft.getTimer().getGameTimeDeltaPartialTick(true);
    }

    /** Rayo de la camara por el punto del raton: {origen, direccion} en el mundo. */
    private Vec3[] ray(double mx, double my) {
        Camera cam = this.minecraft.gameRenderer.getMainCamera();
        Vec3 f = Vec3.directionFromRotation(cam.getXRot(), cam.getYRot());
        Vec3 r = f.cross(new Vec3(0, 1, 0));
        if (r.lengthSqr() < 1.0e-6) {
            r = new Vec3(1, 0, 0);
        }
        r = r.normalize();
        Vec3 u = r.cross(f).normalize();
        double tan = Math.tan(Math.toRadians(this.minecraft.options.fov().get()) / 2.0);
        double aspect = (double) this.width / Math.max(1, this.height);
        double nx = mx / this.width * 2.0 - 1.0;
        double ny = 1.0 - my / this.height * 2.0;
        Vec3 d = f.add(r.scale(nx * tan * aspect)).add(u.scale(ny * tan)).normalize();
        return new Vec3[]{cam.getPosition(), d};
    }

    private float[] toModel(Entity e, Vec3 w) {
        float p = pt();
        return HandLinkImpl.toModel(e.getPosition(p), TouchLinkImpl.yaw(e, p), w);
    }

    private Touch.Hit pick(double mx, double my) {
        Entity e = receiver();
        if (e == null) {
            return null;
        }
        Vec3[] ray = ray(mx, my);
        float[] o = toModel(e, ray[0]);
        float[] o2 = toModel(e, ray[0].add(ray[1]));
        return Touch.pick(o[0], o[1], o[2], o2[0] - o[0], o2[1] - o[1], o2[2] - o[2]);
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        Entity e = receiver();
        String who = e == null ? "" : e.getDisplayName().getString();
        int cx = this.width / 2;
        ctx.drawCenteredString(this.font, Component.literal("Mimos con " + who), cx, 8, 0xFFFFC8E8);
        ctx.drawCenteredString(this.font,
                Component.literal("Clic y arrastra sobre la cabeza, la cara, las manos, los brazos, los hombros o la espalda"),
                cx, 20, 0xFFD8D8E8);
        ctx.drawCenteredString(this.font,
                Component.literal("C o F5: " + (TouchCam.firstPerson ? "ver de costado" : "primera persona")
                        + "   -   Clic derecho o rueda: mover la vista   -   Shift o Esc: terminar"), cx, 31, 0xFF9A9AB0);
        if (!this.dragging && !this.orbiting) {
            Touch.Hit h = pick(mouseX, mouseY);
            this.hover = h == null ? Touch.NONE : h.zone();
        }
        int z = this.dragging ? this.zone : this.hover;
        if (z != Touch.NONE) {
            ctx.drawString(this.font, Component.literal("♥ " + Touch.name(z)), mouseX + 10, mouseY - 4, 0xFFFF9AD0);
        }
    }

    private void sendNow(boolean active) {
        ClientState.TouchState s = ClientState.TOUCHES.computeIfAbsent(this.minecraft.player.getId(), k -> new ClientState.TouchState());
        s.toucher = this.minecraft.player.getId();
        s.receiver = this.receiverId;
        s.zone = this.zone;
        s.active = active && this.zone != Touch.NONE;
        System.arraycopy(this.point, 0, s.target, 0, 3);
        System.arraycopy(this.start, 0, s.start, 0, 3);
        ClientPlayNetworking.send(new TouchPayload(this.zone, this.point[0], this.point[1], this.point[2],
                this.start[0], this.start[1], this.start[2], s.active));
        this.pending = false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1) {
            this.orbiting = true;
            return true;
        }
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        Touch.Hit h = pick(mouseX, mouseY);
        Entity e = receiver();
        if (h == null || e == null) {
            return true;
        }
        this.zone = h.zone();
        this.start = Touch.constrain(this.zone, h.x(), h.y(), h.z());
        this.point = this.start.clone();
        float p = pt();
        this.planePoint = HandLinkImpl.toWorld(e.getPosition(p), TouchLinkImpl.yaw(e, p), h.x(), h.y(), h.z());
        this.planeNormal = ray(mouseX, mouseY)[1];
        this.dragging = true;
        sendNow(true);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (this.orbiting && button == 1) {
            if (TouchCam.firstPerson) {
                TouchCam.lookYaw = Math.max(-50.0f, Math.min(50.0f, TouchCam.lookYaw + (float) dx * 0.4f));
                TouchCam.lookPitch = Math.max(-30.0f, Math.min(45.0f, TouchCam.lookPitch + (float) dy * 0.4f));
            } else {
                TouchCam.orbit = (TouchCam.orbit + (float) dx * 0.6f) % 360.0f;
            }
            return true;
        }
        if (!this.dragging || button != 0 || this.planePoint == null) {
            return true;
        }
        Entity e = receiver();
        if (e == null) {
            return true;
        }
        Vec3[] ray = ray(mouseX, mouseY);
        double den = ray[1].dot(this.planeNormal);
        if (Math.abs(den) < 1.0e-4) {
            return true;
        }
        double t = this.planePoint.subtract(ray[0]).dot(this.planeNormal) / den;
        if (t <= 0) {
            return true;
        }
        float[] m = toModel(e, ray[0].add(ray[1].scale(t)));
        this.point = Touch.constrain(this.zone, m[0], m[1], m[2]);
        // el resto se manda en el tick (como mucho una vez por tick); localmente se ve ya
        ClientState.TouchState s = ClientState.TOUCHES.get(this.minecraft.player.getId());
        if (s != null) {
            System.arraycopy(this.point, 0, s.target, 0, 3);
        }
        this.pending = true;
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 1) {
            this.orbiting = false;
            return true;
        }
        if (button == 0 && this.dragging) {
            this.dragging = false;
            sendNow(false);
            this.zone = Touch.NONE;
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) {
        if (TouchCam.firstPerson) {
            TouchCam.lookPitch = Math.max(-30.0f, Math.min(45.0f, TouchCam.lookPitch - (float) v * 5.0f));
        } else {
            TouchCam.orbit = (TouchCam.orbit + (float) v * 12.0f) % 360.0f;
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || this.minecraft.options.keyShift.matches(keyCode, scanCode)) {
            this.onClose();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_C || this.minecraft.options.keyTogglePerspective.matches(keyCode, scanCode)) {
            TouchCam.firstPerson = !TouchCam.firstPerson;
            applyView();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void tick() {
        super.tick();
        this.age++;
        if (this.pending && this.dragging) {
            sendNow(true);
        }
        Entity e = receiver();
        boolean over = e == null || !e.isAlive()
                || (this.age > 10 && !"mimos".equals(ClientState.session))
                || !ClientState.TOUCHES.containsKey(this.minecraft.player.getId()) && this.age > 10;
        if (over) {
            this.endedBySession = true;
            this.onClose();
        }
    }

    @Override
    public void onClose() {
        if (!this.endedBySession && this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.connection.sendCommand("interaccion parar");
        }
        super.onClose();
    }

    @Override
    public void removed() {
        TouchCam.active = false;
        if (this.minecraft != null) {
            if (this.before != null) {
                this.minecraft.options.setCameraType(this.before);
                this.minecraft.options.hideGui = this.hudBefore;
            }
            if (this.minecraft.player != null) {
                ClientState.TOUCHES.remove(this.minecraft.player.getId());
            }
        }
        super.removed();
    }

    /** Prueba automatica: tocar una zona en un punto (modelo del otro). */
    void testTouch(int zone, float x, float y, float z) {
        this.zone = zone;
        this.start = Touch.constrain(zone, x, y, z);
        this.point = this.start.clone();
        this.dragging = true;
        sendNow(true);
    }

    void testView(boolean first) {
        TouchCam.firstPerson = first;
        applyView();
    }

    void testRelease() {
        this.dragging = false;
        sendNow(false);
        this.zone = Touch.NONE;
    }

    /** Abre la pantalla si este cliente es quien toca. */
    static void openFor(int receiverId) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof TouchScreen)) {
            mc.setScreen(new TouchScreen(receiverId));
        }
    }
}
