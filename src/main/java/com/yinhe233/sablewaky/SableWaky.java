package com.yinhe233.sablewaky;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.yinhe233.sablewaky.event.SableWakyBusEvents;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Sable: Waky —— 给 Sable 的物理体（sub-level）加"加载后受力渐入"。
 *
 * <p>动机：结构被加载/组装时，各方块与方块实体并不是同一刻就绪的。先就绪的推进器、
 * 气球升力面会立刻全额施力，而配平用的另一部分还没参与计算，于是产生瞬时失衡 → 坠机。
 *
 * <p>做法：每个物理体进入物理管道后的第一秒内，把它每个物理子步的<b>净速度增量</b>
 * 按 α(t) 从 0 缩放到 1。因为速度增量等价于合力产生的加速度，所以效果就是
 * "所有受力从 0 倍平滑回升到 1 倍"，且不需要 mixin 任何 Sable 内部实现。
 *
 * <p>实现全部基于公开 API：{@code ForgeSablePostPhysicsTickEvent} +
 * {@code SubLevelObserver} + {@code RigidBodyHandle}。
 */
@Mod(SableWaky.MODID)
public final class SableWaky {

    /** 必须与 gradle.properties 的 mod_id、mods.toml 的 modId 完全一致（小写）。 */
    public static final String MODID = "sablewaky";

    public static final Logger LOGGER = LogUtils.getLogger();

    public SableWaky(final IEventBus modEventBus, final ModContainer modContainer) {
        // 物理渐变属于玩法规则，走 SERVER 配置（单人存档 / 服务器各自独立）。
        modContainer.registerConfig(ModConfig.Type.SERVER, SableWakyConfig.SPEC);

        // ForgeSable*Event 是发在 NeoForge 游戏总线上的，不是 mod 总线。
        NeoForge.EVENT_BUS.register(SableWakyBusEvents.class);

        // 注意：SERVER 配置要等服务器启动才加载，构造期不能读 .get()，否则抛异常。
        // 也正因如此，这里是全模组唯一一条无条件的日志。
        LOGGER.info("[Sable: Waky] 已加载：sub-level 加载后受力渐入");
    }
}
