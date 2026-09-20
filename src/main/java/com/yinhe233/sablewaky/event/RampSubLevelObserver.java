package com.yinhe233.sablewaky.event;

import com.yinhe233.sablewaky.physics.ForceRampManager;

import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;

/**
 * 把 Sable 的 sub-level 生命周期转成力渐入的登记/注销。
 *
 * <p>Sable 没有提供 sub-level 被加载的 NeoForge 事件，但 {@code SubLevelContainer} 允许挂
 * {@link SubLevelObserver}，而 {@code onSubLevelAdded} 会在组装、切分、从存档载入时都被触发，
 * 正好覆盖"物理体被加载"这件事。
 *
 * <p>注册时机：收到 {@code ForgeSableSubLevelContainerReadyEvent} 之后（见 {@link SableWakyBusEvents}）。
 * 那时 Sable 自己的 {@code SubLevelPhysicsSystem} 已经先注册为观察者，所以我们排在它后面，
 * 回调发生时质量追踪器已经建好。
 */
public final class RampSubLevelObserver implements SubLevelObserver {

    public static final RampSubLevelObserver INSTANCE = new RampSubLevelObserver();

    private RampSubLevelObserver() {
    }

    @Override
    public void onSubLevelAdded(final SubLevel subLevel) {
        ForceRampManager.INSTANCE.onBodyAdded(subLevel);
    }

    @Override
    public void onSubLevelRemoved(final SubLevel subLevel, final SubLevelRemovalReason reason) {
        ForceRampManager.INSTANCE.onBodyRemoved(subLevel);
    }
}
