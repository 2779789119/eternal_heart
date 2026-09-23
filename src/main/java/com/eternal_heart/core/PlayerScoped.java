package com.eternal_heart.core;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * 玩家作用域状态容器 —— 全项目「按玩家存放运行期临时状态」的唯一实现。
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>底层使用 fastutil 开放寻址哈希表（{@code Object2ObjectOpenHashMap}）：
 *       相对 {@code ConcurrentHashMap} 省去同步与 Node 链开销，内存占用约为其 1/3；</li>
 *   <li>所有访问都发生在逻辑主线程（服务端 tick、Forge 事件回调、网络包主线程消费者）。
 *       单人存档下客户端与服务端逻辑本就在同一线程；独立服务器上客户端是另一个 JVM，
 *       两端实例天然隔离。因此这里<strong>无需</strong>并发控制；</li>
 *   <li>{@link #forEach} 使用键快照遍历，允许回调内部移除当前条目而不产生异常。</li>
 * </ul>
 *
 * @param <V> 每个玩家持有的状态类型
 */
public final class PlayerScoped<V> {

    private final Map<UUID, V> states = new Object2ObjectOpenHashMap<>(32);

    /** 读取状态；不存在返回 null。 */
    public V get(UUID id) {
        return states.get(id);
    }

    /** 读取或创建状态（惰性初始化，避免每次访问都做 null 判断）。 */
    public V compute(UUID id, Supplier<V> factory) {
        V value = states.get(id);
        if (value == null) {
            value = factory.get();
            states.put(id, value);
        }
        return value;
    }

    public void put(UUID id, V value) {
        states.put(id, value);
    }

    /** 移除并返回状态（玩家退出 / 清理时调用）。 */
    public V remove(UUID id) {
        return states.remove(id);
    }

    public void clear() {
        states.clear();
    }

    public int size() {
        return states.size();
    }

    /**
     * 遍历全部状态。回调内可以安全地调用 {@link #remove}；
     * 已被移除的条目不会再被访问。
     */
    public void forEach(BiConsumer<UUID, V> action) {
        if (states.isEmpty()) return;
        UUID[] keys = states.keySet().toArray(new UUID[0]);
        for (UUID key : keys) {
            V value = states.get(key);
            if (value != null) action.accept(key, value);
        }
    }
}
