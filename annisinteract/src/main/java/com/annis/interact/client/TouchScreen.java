package com.annis.interact.client;

import com.annis.interact.Touch;
import com.annis.interact.net.TouchPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

/**
 * Modo mimos (quien toca): la camara se pone de costado, el raton queda libre y, con clic
 * izquierdo y arrastrando, se acaricia el pelo o la mejilla, se toma la mano o se le mueve el brazo,
 * o se dan palmaditas en el hombro. Rueda o clic derecho arrastrando: girar la camara.
 * Shift o Esc: terminar.
 */
public class TouchScreen extends Screen {
    private final int receiverId;
    private Perspective before;
    private boolean dragging;
    private boolean orbiting;
    private int zone = Touch.NONE;
    private float[] start = new float[3];
    private float[] point = new float[3];
    private Vec3d planePoint;
    private Vec3d planeNormal;
    private boolean pending;
    private boolean endedBySession;
    private int hover = Touch.NONE;
    private int age;

    public TouchScreen(int receiverId) {
        super(Text.literal("Mimos"));
        this.receiverId = receiverId;
    }

    @Override
    protected void init() {
        if (this.before == null && this.client != null) {
            this.before = this.client.options.getPerspective();
            this.client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        }
        TouchCam.receiver = this.receiverId;
        TouchCam.active = true;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // sin fondo oscuro
    }

    private Entity receiver() {
        return this.client == null || this.client.world == null ? null : this.client.world.getEntityById(this.receiverId);
    }

    private float pt() {
        return this.client.getRenderTickCounter().getTickDelta(true);
    }

    /** Rayo de la camara por el punto del raton: {origen, direccion} en el mundo. */
    private Vec3d[] ray(double mx, double my) {
        Camera cam = this.client.gameRenderer.getCamera();
        Vec3d f = Vec3d.fromPolar(cam.getPitch(), cam.getYaw());
        Vec3d r = f.crossProduct(new Vec3d(0, 1, 0));
        if (r.lengthSquared() < 1.0e-6) {
            r = new Vec3d(1, 0, 0);
        }
        r = r.normalize();
        Vec3d u = r.crossProduct(f).normalize();
        double tan = Math.tan(Math.toRadians(this.client.options.getFov().getValue()) / 2.0);
        double aspect = (double) this.width / Math.max(1, this.height);
        double nx = mx / this.width * 2.0 - 1.0;
        double ny = 1.0 - my / this.height * 2.0;
        Vec3d d = f.add(r.multiply(nx * tan * aspect)).add(u.multiply(ny * tan)).normalize();
        return new Vec3d[]{cam.getPos(), d};
    }

    private float[] toModel(Entity e, Vec3d w) {
        float p = pt();
        return HandLinkImpl.toModel(e.getLerpedPos(p), TouchLinkImpl.yaw(e, p), w);
    }

    private Touch.Hit pick(double mx, double my) {
        Entity e = receiver();
        if (e == null) {
            return null;
        }
        Vec3d[] ray = ray(mx, my);
        float[] o = toModel(e, ray[0]);
        float[] o2 = toModel(e, ray[0].add(ray[1]));
        return Touch.pick(o[0], o[1], o[2], o2[0] - o[0], o2[1] - o[1], o2[2] - o[2]);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        Entity e = receiver();
        String who = e == null ? "" : e.getDisplayName().getString();
        int cx = this.width / 2;
        ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Mimos con " + who), cx, 8, 0xFFFFC8E8);
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Clic y arrastra: acariciar el pelo o la mejilla, tomar la mano, mover el brazo, palmaditas"),
                cx, 20, 0xFFD8D8E8);
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Rueda o clic derecho: girar la camara   -   Shift o Esc: terminar"), cx, 31, 0xFF9A9AB0);
        if (!this.dragging && !this.orbiting) {
            Touch.Hit h = pick(mouseX, mouseY);
            this.hover = h == null ? Touch.NONE : h.zone();
        }
        int z = this.dragging ? this.zone : this.hover;
        if (z != Touch.NONE) {
            ctx.drawTextWithShadow(this.textRenderer, Text.literal("♥ " + Touch.name(z)), mouseX + 10, mouseY - 4, 0xFFFF9AD0);
        }
    }

    private void sendNow(boolean active) {
        ClientState.TouchState s = ClientState.TOUCHES.computeIfAbsent(this.client.player.getId(), k -> new ClientState.TouchState());
        s.toucher = this.client.player.getId();
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
        this.planePoint = HandLinkImpl.toWorld(e.getLerpedPos(p), TouchLinkImpl.yaw(e, p), h.x(), h.y(), h.z());
        this.planeNormal = ray(mouseX, mouseY)[1];
        this.dragging = true;
        sendNow(true);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (this.orbiting && button == 1) {
            TouchCam.orbit = (TouchCam.orbit + (float) dx * 0.6f) % 360.0f;
            return true;
        }
        if (!this.dragging || button != 0 || this.planePoint == null) {
            return true;
        }
        Entity e = receiver();
        if (e == null) {
            return true;
        }
        Vec3d[] ray = ray(mouseX, mouseY);
        double den = ray[1].dotProduct(this.planeNormal);
        if (Math.abs(den) < 1.0e-4) {
            return true;
        }
        double t = this.planePoint.subtract(ray[0]).dotProduct(this.planeNormal) / den;
        if (t <= 0) {
            return true;
        }
        float[] m = toModel(e, ray[0].add(ray[1].multiply(t)));
        this.point = Touch.constrain(this.zone, m[0], m[1], m[2]);
        // el resto se manda en el tick (como mucho una vez por tick); localmente se ve ya
        ClientState.TouchState s = ClientState.TOUCHES.get(this.client.player.getId());
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
        TouchCam.orbit = (TouchCam.orbit + (float) v * 12.0f) % 360.0f;
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || this.client.options.sneakKey.matchesKey(keyCode, scanCode)) {
            this.close();
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
                || !ClientState.TOUCHES.containsKey(this.client.player.getId()) && this.age > 10;
        if (over) {
            this.endedBySession = true;
            this.close();
        }
    }

    @Override
    public void close() {
        if (!this.endedBySession && this.client != null && this.client.player != null) {
            this.client.player.networkHandler.sendChatCommand("interaccion parar");
        }
        super.close();
    }

    @Override
    public void removed() {
        TouchCam.active = false;
        if (this.client != null) {
            if (this.before != null) {
                this.client.options.setPerspective(this.before);
            }
            if (this.client.player != null) {
                ClientState.TOUCHES.remove(this.client.player.getId());
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

    void testRelease() {
        this.dragging = false;
        sendNow(false);
        this.zone = Touch.NONE;
    }

    /** Abre la pantalla si este cliente es quien toca. */
    static void openFor(int receiverId) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!(mc.currentScreen instanceof TouchScreen)) {
            mc.setScreen(new TouchScreen(receiverId));
        }
    }
}
