package com.yinhe233.sablewaky.physics;

import org.joml.Vector3d;

/**
 * 单个物理体的渐入状态。
 *
 * <p>"参考速度"({@link #linearRef} / {@link #angularRef}) 是<b>我们已经修正过的</b>速度，
 * 也就是该体在上一物理子步结束时的应有速度。用它和本子步结束时的原始速度作差，
 * 就得到本子步的净速度增量 Δ（包含重力、所有施加的冲量、以及接触求解）。
 */
public final class RampState {

    /** 渐入总时长 [s]。 */
    private final double duration;

    /** 缓动曲线。 */
    private final RampEasing easing;

    /** 已累积的物理时间 [s]。 */
    private double elapsed;

    /** 是否已经用第一个子步的速度做过基准；第一个子步不修正。 */
    private boolean primed;

    /** 上一子步结束时的应有线速度。 */
    public final Vector3d linearRef = new Vector3d();

    /** 上一子步结束时的应有角速度。 */
    public final Vector3d angularRef = new Vector3d();

    public RampState(final double duration, final RampEasing easing) {
        this.duration = duration;
        this.easing = easing;
    }

    public boolean isPrimed() {
        return this.primed;
    }

    public void markPrimed() {
        this.primed = true;
    }

    /**
     * 推进一个物理子步。
     *
     * @param timeStep 本子步时长 [s]
     * @return 推进后的倍率 α ∈ [0,1]
     */
    public float advance(final double timeStep) {
        if (this.duration <= 0.0D) {
            return 1.0F;
        }

        this.elapsed += timeStep;

        final double t = this.elapsed / this.duration;
        if (t >= 1.0D) {
            return 1.0F;
        }
        if (t <= 0.0D) {
            return 0.0F;
        }

        return (float) Math.max(0.0D, Math.min(1.0D, this.easing.apply(t)));
    }
}
