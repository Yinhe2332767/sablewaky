package com.yinhe233.sablewaky.event;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import com.yinhe233.sablewaky.SableWaky;
import com.yinhe233.sablewaky.SableWakyConfig;
import com.yinhe233.sablewaky.physics.ForceRampManager;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.neoforge.event.ForgeSablePostPhysicsTickEvent;
import dev.ryanhcode.sable.neoforge.event.ForgeSableSubLevelContainerReadyEvent;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Sable 事件的接线处。
 *
 * <p>这几个 {@code ForgeSable*Event} 由 Sable 的 {@code SableEventPublishPlatformImpl}
 * 发到 {@code NeoForge.EVENT_BUS}（游戏总线），所以注册目标是 NeoForge 总线而不是 mod 总线。
 * 另注意 {@code dev.ryanhcode.sable.api.event.SablePrePhysicsTickEvent} 那几个是 SPI 接口，
 * 不是总线事件，别混用。
 */
public final class SableWakyBusEvents {

    /** 已挂过观察者的容器。容器按维度创建，重载时会是新实例。 */
    private static final Set<SubLevelContainer> REGISTERED =
            Collections.newSetFromMap(new IdentityHashMap<SubLevelContainer, Boolean>());

    private SableWakyBusEvents() {
    }

    /**
     * 某个维度的 sub-level 容器就绪 —— 挂上我们的观察者，用来感知物理体的加载与移除。
     */
    @SubscribeEvent
    public static void onContainerReady(final ForgeSableSubLevelContainerReadyEvent event) {
        // 物理只在服务端：ClientSubLevelContainer 没有 physicsSystem，对其调用会抛异常。
        if (!(event.getLevel() instanceof final ServerLevel serverLevel)) {
            return;
        }

        final SubLevelContainer container = event.getContainer();
        if (!REGISTERED.add(container)) {
            return;   // 幂等，避免重复注册导致同一个体被登记两次
        }

        container.addObserver(RampSubLevelObserver.INSTANCE);

        if (SableWakyConfig.debugLog()) {
            SableWaky.LOGGER.info("[Sable: Waky] 已挂载渐入观察者：{}", serverLevel.dimension().location());
        }
    }

    /**
     * 每个物理子步结束时推进渐入。放在 POST 是刻意的，理由见
     * {@link ForceRampManager#tick} 的类文档（只有 POST 之间才构成一个闭合的完整子步）。
     */
    @SubscribeEvent
    public static void onPostPhysicsTick(final ForgeSablePostPhysicsTickEvent event) {
        ForceRampManager.INSTANCE.tick(event.getPhysicsSystem(), event.getTimeStep());
    }

    /**
     * 服务器停止时清空状态，防止换存档/重载世界后残留旧物理体的渐入记录。
     */
    @SubscribeEvent
    public static void onServerStopped(final ServerStoppedEvent event) {
        ForceRampManager.INSTANCE.reset();
        REGISTERED.clear();
    }
}
