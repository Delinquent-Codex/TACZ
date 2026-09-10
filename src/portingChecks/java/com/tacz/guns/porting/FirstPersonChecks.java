package com.tacz.guns.porting;

import com.maydaymemory.mae.basic.*;
import com.maydaymemory.mae.blend.SimpleAdditiveBlender;
import com.tacz.guns.api.client.animation.IFPAnimationInstance;
import com.tacz.guns.client.animation.FirstPersonItemState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;

public final class FirstPersonChecks {
    private static int assertions;

    private static void check(boolean condition, String description) {
        assertions++;
        if (!condition) throw new AssertionError(description);
    }

    private static final class Instance implements IFPAnimationInstance {
        private ItemStack item;
        private int draws, putaways, ticks;
        private boolean drawn;
        private Quaternionf camera = new Quaternionf();
        private Instance(ItemStack item) { this.item = item; }
        public ItemStack currentItem() { return item; }
        public Pose getPose() { return DummyPose.INSTANCE; }
        public Pose getCachedPose() { return getPose(); }
        public void tick(float partialTick) { ticks++; }
        public Quaternionf getCameraRotation() { return camera; }
        public void setCameraRotation(Quaternionf value) { camera = value; }
        public void updateItem(ItemStack value) { item = value; }
        public void triggerDraw() { if (!drawn) { draws++; drawn = true; } }
        public void triggerPutAway() { putaways++; }
    }

    public static void main(String[] args) {
        RegistryFixture.bootstrap();
        long[] now = {1000}, duration = {200};
        ArrayList<Instance> instances = new ArrayList<>();
        var state = new FirstPersonItemState(item -> {
            if (item.isEmpty() || item.is(Items.DIRT)) return null;
            Instance instance = new Instance(item); instances.add(instance); return instance;
        }, item -> !item.isEmpty() && !item.is(Items.DIRT), item -> duration[0], ItemStack::isSameItem, () -> now[0]);
        ItemStack first = new ItemStack(Items.STICK), second = new ItemStack(Items.BLAZE_ROD), vanilla = new ItemStack(Items.DIRT);
        state.tick(0, vanilla);
        check(state.activeInstance() == null && !state.shouldLockVanilla() && state.targetHeight() == 0, "vanilla has no custom instance");
        state.tick(1, first);
        Instance initial = (Instance) state.activeInstance();
        check(initial != null && !state.shouldLockVanilla() && state.targetHeight() == 1, "vanilla to custom starts immediately");
        state.frame(.25F); state.frame(.5F);
        check(initial.draws == 1 && initial.ticks == 2, "draw callback is offered each frame and instance prevents duplicate draw");
        var updated = first.copyWithCount(5);
        state.tick(1, updated);
        check(state.activeInstance() == initial && initial.item == updated && initial.putaways == 0, "same item state refresh does not restart animation");
        state.tick(2, second);
        check(state.shouldLockVanilla() && state.activeInstance() == initial && initial.putaways == 1, "custom switch keeps outgoing model through put-away");
        check(initial.item == updated, "outgoing instance retains original item while target updates");
        now[0] = 1199; state.tick(2, second);
        check(state.activeInstance() == initial, "sheathe remains active before deadline");
        state.frame(.75F);
        check(initial.ticks == 3, "outgoing animation keeps receiving frames");
        state.tick(3, first);
        check(initial.putaways == 1 && state.shouldLockVanilla(), "rapid switching changes pending item without repeating put-away");
        now[0] = 1200; state.tick(3, first);
        check(!state.shouldLockVanilla() && state.activeInstance() != initial && state.activeInstance().currentItem() == first,
                "deadline selects most recent target and releases vanilla lock");
        Instance selected = (Instance) state.activeInstance(); state.frame(0);
        check(selected.draws == 1, "latest target draws after outgoing completes");
        state.tick(3, second);
        check(state.shouldLockVanilla() && selected.putaways == 1, "replacement in same hotbar slot triggers put-away");
        now[0] += 200; state.tick(3, second);
        Instance swapped = (Instance) state.activeInstance(); state.forceHandSwap(); state.tick(3, second);
        check(swapped.putaways == 1 && state.shouldLockVanilla(), "server offhand notification forces same-item transition");
        state.tick(4, vanilla);
        check(state.targetHeight() == 0 && state.activeInstance() == swapped, "custom to vanilla keeps outgoing until deadline");
        now[0] += 200; state.tick(4, vanilla);
        check(state.activeInstance() == null && !state.shouldLockVanilla(), "vanilla resumes after final put-away");
        state.tick(0, first); duration[0] = 0; state.tick(1, second);
        check(!state.shouldLockVanilla() && state.activeInstance().currentItem() == second, "zero duration completes during same tick");
        state.forceHandSwap(); state.reset();
        check(state.activeInstance() == null && state.targetHeight() == 0 && !state.shouldLockVanilla(), "logout/reload reset releases all active state");
        int count = instances.size(); state.tick(1, second);
        check(instances.size() == count + 1 && !state.shouldLockVanilla(), "reset starts a fresh draw without stale forced transition");

        // Exercise the actual unmodified MAE dependency with target JOML and fastutil, not a substitute pose library.
        var channel = new ArrayInterpolatableChannel<Vector3fc>();
        Vector3f start = new Vector3f(), end = new Vector3f(8, 4, -2);
        channel.add(new Vector3fKeyframe(2, end, end, Vector3fLinearInterpolator.INSTANCE));
        channel.add(new Vector3fKeyframe(0, start, start, Vector3fLinearInterpolator.INSTANCE));
        channel.refresh();
        var animation = new BasicAnimation("fixture", new ZYXBoneTransformFactory(), LinkedListPoseBuilder::new);
        animation.setTranslationChannel(7, channel);
        Pose pose = animation.evaluate(1);
        BoneTransform transform = pose.getBoneTransforms().iterator().next();
        check(transform.boneIndex() == 7 && transform.translation().equals(new Vector3f(4, 2, -1)), "MAE sorts and interpolates real bone channels");
        check(animation.getEndTimeS() == 2 && transform.scale().equals(new Vector3f(1)), "MAE end time and identity scale retained");
        check(animation.evaluate(-1).getBoneTransforms().iterator().next().translation().equals(start)
                && animation.evaluate(3).getBoneTransforms().iterator().next().translation().equals(end), "MAE clamps endpoint samples");
        Pose blend = new SimpleAdditiveBlender(new ZYXBoneTransformFactory(), LinkedListPoseBuilder::new).blend(pose, pose);
        check(blend.getBoneTransforms().iterator().next().translation().equals(end), "MAE additive pose blending runs on target dependency closure");
        check(transform.translation().equals(new Vector3f(4, 2, -1)), "MAE blend leaves original pose intact");
        System.out.println("First-person checks passed: " + assertions + " assertions (state controller and MAE; no game rendering or live inventory).");
    }
}
