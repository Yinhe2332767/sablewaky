package com.yinhe233.sablewaky;

import com.yinhe233.sablewaky.physics.RampEasing;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 力渐变的 SERVER 配置。
 *
 * <p>注意：时长用 tick 表达（对模组作者更直观），内部按物理子步时间累加，
 * 因此不受 {@code substepsPerTick} 配置或服务器卡顿影响，窗口长度恒定。
 */
public final class SableWakyConfig {

    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.IntValue RAMP_DURATION_TICKS;
    private static final ModConfigSpec.EnumValue<RampEasing> EASING;
    private static final ModConfigSpec.BooleanValue HOLD_ANGULAR;
    private static final ModConfigSpec.BooleanValue DEBUG_LOG;

    static {
        final ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment(
                "物理体加载后的受力渐入（load guard）。",
                "每个 sub-level 进入物理世界后的第一段时间内，其净加速度会按 alpha(t) 从 0 倍平滑回升到 1 倍，",
                "用来消除「部分方块/方块实体先就绪」造成的瞬时失衡，也就是加载坠机。"
        ).push("load_guard");

        RAMP_DURATION_TICKS = builder
                .comment("渐入窗口长度（tick）。20 tick = 1 秒。",
                        "填 0 表示完全关闭该功能（受力始终按 1 倍施加）。")
                .defineInRange("ramp_duration_ticks", 60, 0, 600);

        EASING = builder
                .comment("alpha(t) 缓动曲线：",
                        "LINEAR     线性；",
                        "SMOOTHSTEP 3t^2-2t^3，起点与终点导数为 0，较柔和；",
                        "SINE_IN    正弦上升，介于两者之间；",
                        "LOGISTICS  1/(1+exp(-8(t-0.5))，起点终点最缓和但中间变化更快。")
                .defineEnum("easing", RampEasing.LINEAR);

        HOLD_ANGULAR = builder
                .comment("是否同时渐入角速度，即一并抑制加载瞬间的翻滚。",
                        "建议保持 true，否则结构可能在窗口内原地转起来。")
                .define("hold_angular_velocity", true);

        DEBUG_LOG = builder
                .comment("调试日志：输出每个物理体开始/结束渐入的记录（INFO），",
                        "以及每个物理子步的 alpha 与修正量（TRACE）。仅调参和排查时打开。")
                .define("debug_log", false);

        builder.pop();

        SPEC = builder.build();
    }

    private SableWakyConfig() {
    }

    /** 渐入窗口长度，单位秒。 */
    public static double rampSeconds() {
        return RAMP_DURATION_TICKS.get() / 20.0D;
    }

    /** 渐入窗口长度（tick），仅用于日志展示。 */
    public static int rampDurationTicks() {
        return RAMP_DURATION_TICKS.get();
    }

    /** 缓动曲线。 */
    public static RampEasing easing() {
        return EASING.get();
    }

    /** 是否同时渐入角速度。 */
    public static boolean holdAngularVelocity() {
        return HOLD_ANGULAR.get();
    }

    /** 调试日志开关。 */
    public static boolean debugLog() {
        return DEBUG_LOG.get();
    }
}
