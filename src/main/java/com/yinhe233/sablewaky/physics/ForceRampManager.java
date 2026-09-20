package com.yinhe233.sablewaky.physics;

import java.util.Iterator;
import java.util.UUID;

import org.joml.Vector3d;

import com.yinhe233.sablewaky.SableWaky;
import com.yinhe233.sablewaky.SableWakyConfig;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.server.level.ServerLevel;

/**
 * 力渐入的核心：登记正在渐入的物理体，并在每个物理子步把它们的净速度增量按 α 缩放。
 *
 * <h2>为什么缩放"速度增量"等价于缩放"所有受力"</h2>
 * 一个物理子步里，物理体的速度变化 Δ 来自：所有被施加的冲量（推进器、升力、磁铁、弹簧……）、
 * 重力、以及接触求解。设上一子步我们修正后的速度为 {@code v_ref}，本子步结束时的原始速度为
 * {@code v_raw}，则 {@code Δ = v_raw - v_ref}。
 * 我们补一个速度修正 {@code -(1-α)·Δ}，于是实际速度变成
 * {@code v_ref + α·Δ} —— 相当于这一子步的<b>全部</b>加速度都被乘了 α。
 *
 * <p>这样做的好处：不需要 mixin 任何 Sable 内部实现，也不需要逐个枚举方块类型，
 * 重力天然被一起缩放（否则前 1 秒结构会直接下坠，失去意义）。
 *
 * <h2>时序为什么必须用 POST 事件</h2>
 * {@code ForgeSablePostPhysicsTickEvent} 位于子步循环的最末尾（pose 更新之后）：
 * <pre>
 *   subLevel.prePhysicsTick(...)      ← 方块实体的力在这里施加
 *   ForgeSablePrePhysicsTickEvent
 *   subLevel.applyQueuedForces(...)   ← 排队力（气球浮力等）在这里施加
 *   pipeline.physicsTick(dt)          ← 重力与接触求解在这里
 *   updateAllPoses(...)
 *   ForgeSablePostPhysicsTickEvent    ← 我们在这里
 * </pre>
 * 因此"上一次 POST 到这一次 POST"之间恰好包含了整整一个子步的全部速度变化，
 * Δ 才是一个完整、闭合的增量。若改用 PRE 事件，会漏掉 PRE 之前与之后施加的力。
 */
public final class ForceRampManager {

    public static final ForceRampManager INSTANCE = new ForceRampManager();

    /** 只在渐入窗口内的物理体。identity 语义，与 Sable 自身的 ReferenceOpenHashSet 风格一致。 */
    private final Reference2ObjectOpenHashMap<ServerSubLevel, RampState> ramps = new Reference2ObjectOpenHashMap<>();

    // 复用的临时向量，避免每子步分配
    private final Vector3d tempLinear = new Vector3d();
    private final Vector3d tempAngular = new Vector3d();
    private final Vector3d deltaLinear = new Vector3d();
    private final Vector3d deltaAngular = new Vector3d();

    private ForceRampManager() {
    }

    /** 稳态快速路径：没有任何体在渐入时，调用方可以直接跳过。 */
    public boolean isEmpty() {
        return this.ramps.isEmpty();
    }

    /** 世界卸载时清空，防止跨存档残留。 */
    public void reset() {
        this.ramps.clear();
    }

    /**
     * 物理体被加入容器时登记。
     *
     * <p>注意这里只登记、不做任何物理操作：此时物理体可能还没有进入物理管道，
     * 计时与速度读取都要等到 {@link #tick} 里确认质量追踪器已建好之后才开始。
     */
    public void onBodyAdded(final SubLevel subLevel) {
        if (!(subLevel instanceof final ServerSubLevel serverSubLevel)) {
            return;
        }

        final double seconds = SableWakyConfig.rampSeconds();
        if (seconds <= 0.0D) {
            return;   // 功能已关闭
        }

        this.ramps.put(serverSubLevel, new RampState(seconds, SableWakyConfig.easing()));

        if (SableWakyConfig.debugLog()) {
            SableWaky.LOGGER.info("[Sable: Waky] 开始渐入 {}（{} tick）",
                    describe(serverSubLevel), SableWakyConfig.rampDurationTicks());
        }
    }

    /** 物理体被移除时清理。 */
    public void onBodyRemoved(final SubLevel subLevel) {
        if (this.ramps.isEmpty()) {
            return;
        }

        if (this.ramps.remove(subLevel) != null && SableWakyConfig.debugLog()) {
            SableWaky.LOGGER.info("[Sable: Waky] 物理体被移除，取消渐入 {}", subLevel.getUniqueId());
        }
    }

    /**
     * 每个物理子步调用一次（POST 事件）。
     *
     * @param system   本子步所属的物理系统
     * @param timeStep 本子步时长 [s]
     */
    public void tick(final SubLevelPhysicsSystem system, final double timeStep) {
        if (this.ramps.isEmpty()) {
            return;   // 绝大多数时间走这里，零开销
        }

        final ServerLevel level = system.getLevel();
        final boolean holdAngular = SableWakyConfig.holdAngularVelocity();
        final boolean debug = SableWakyConfig.debugLog();

        // 刻意用标准迭代器而不是 fastIterator()：后者不保证支持 remove()，
        // 而这里需要在渐入结束时把条目摘掉。窗口期条目极少，分配开销可忽略。
        final Iterator<Reference2ObjectMap.Entry<ServerSubLevel, RampState>> iterator =
                this.ramps.reference2ObjectEntrySet().iterator();

        while (iterator.hasNext()) {
            final Reference2ObjectMap.Entry<ServerSubLevel, RampState> entry = iterator.next();
            final ServerSubLevel body = entry.getKey();
            final RampState state = entry.getValue();

            // 同一个表里可能同时存在多个维度的物理体，只处理当前这个系统的。
            // 用引用比较是刻意的：ServerLevel 实例唯一。
            if (body.isRemoved() || body.getLevel() != level) {
                continue;
            }

            // 质量追踪器由 SubLevelPhysicsSystem#onSubLevelAdded 在 pipeline.add 之前建立，
            // 所以它非空就说明该体已经真正进入物理管道，可以安全读取速度了。
            // （没有这个闸门，对尚未进管道的 runtimeId 读速度会让 Rapier 侧 unwrap 失败并抛异常。）
            if (body.getMassTracker() == null) {
                continue;
            }

            // 推进计时；尚未进管道的时间不计入窗口
            final float alpha = state.advance(timeStep);
            if (alpha >= 1.0F) {
                iterator.remove();   // 渐入结束，永久退出，回归零开销
                if (debug) {
                    SableWaky.LOGGER.info("[Sable: Waky] 渐入完成 {}，受力恢复 1 倍", describe(body));
                }
                continue;
            }

            final RigidBodyHandle handle = system.getPhysicsHandle(body);
            if (!handle.isValid()) {
                continue;
            }

            handle.getLinearVelocity(this.tempLinear);
            if (holdAngular) {
                handle.getAngularVelocity(this.tempAngular);
            }

            if (!state.isPrimed()) {
                // 第一个子步只做基准，不修正（否则会把进入管道前的历史速度也算进去）
                state.linearRef.set(this.tempLinear);
                if (holdAngular) {
                    state.angularRef.set(this.tempAngular);
                }
                state.markPrimed();
                continue;
            }

            // k = 需要抹掉的净增量比例；α = 1 时 k = 0（但上面已经 return 了）
            final double k = 1.0D - alpha;

            this.deltaLinear.set(this.tempLinear).sub(state.linearRef);
            // 先更新参考速度（v_ref_new = v_raw - k·Δ），再把 Δ 缩放成修正量，顺序不能反
            state.linearRef.set(this.tempLinear).fma(-k, this.deltaLinear);
            this.deltaLinear.mul(-k);

            if (holdAngular) {
                this.deltaAngular.set(this.tempAngular).sub(state.angularRef);
                state.angularRef.set(this.tempAngular).fma(-k, this.deltaAngular);
                this.deltaAngular.mul(-k);
            } else {
                this.deltaAngular.zero();
            }

            handle.addLinearAndAngularVelocity(this.deltaLinear, this.deltaAngular);

            if (debug) {
                SableWaky.LOGGER.trace("[Sable: Waky] {} α={} |修正|={} k={}",
                        describe(body), String.format("%.3f", alpha),
                        String.format("%.4f", this.deltaLinear.length()),
                        String.format("%.4f", k));
            }
        }
    }

    private static String describe(final ServerSubLevel body) {
        final UUID id = body.getUniqueId();
        return id == null ? "<unnamed>" : id.toString().substring(0, 8);
    }
}
