package com.ai.assistance.operit.data.model

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import java.util.Date

/**
 * 自学习记忆账本 (Self-Learning Memory Ledger)
 *
 * 每个 [Memory] 节点最多对应一条账本记录，用于追踪该记忆在多次对话中的
 * 强化（reinforcement）、稳定性（stability）与去重（dedup via [sourceHash]）状态。
 *
 * 设计要点：
 *  - 不复刻置信度字段：可信度复用 [Memory.credibility]，账本只记录强化元数据。
 *  - [sourceHash] 用于补充现有的 `mergeMemories` + LLM `alias_for` 去重机制。
 *  - [stability] 与 [status] 以 Int 存储（ObjectBox 不原生支持枚举），
 *    取值见 [MemoryLedger.Stability] 与 [MemoryLedger.Status]。
 *  - 记忆引用使用 [memoryId]（Long + @Index）而非 ToOne：
 *    查询更简单、无 ToOne 生命周期陷阱，且 VPS 无法本地编译 Android，需降低风险。
 */
@Entity
data class MemoryLedger(
    @Id var id: Long = 0,

    // 账本对应的记忆节点 id（1:1，对应 Memory.id）
    @Index
    var memoryId: Long = 0,

    // 原始来源内容的稳定哈希，用于去重（补充 mergeMemories + alias_for）
    @Index
    var sourceHash: String = "",

    // 强化次数：同一事实/陈述被再次确认的次数
    var count: Long = 0,

    var firstSeenAt: Date = Date(),
    var lastReinforcedAt: Date = Date(),

    // 稳定性：VOLATILE=0 / STABLE=1 / IMMUTABLE=2
    var stability: Int = Stability.VOLATILE.value,

    // 状态：ACTIVE=0 / INACTIVE=1（INACTIVE 为衰减后的冷存储）
    var status: Int = Status.ACTIVE.value
) {

    /** 稳定性等级：决定衰减半衰期。 */
    enum class Stability(val value: Int) {
        VOLATILE(0),
        STABLE(1),
        IMMUTABLE(2)
    }

    /** 账本状态：决定记忆是否参与正常检索。 */
    enum class Status(val value: Int) {
        ACTIVE(0),
        INACTIVE(1)
    }
}

/**
 * 自学习记忆的可调参数。集中管理，避免魔法数字散落各处。
 */
object SelfLearningConfig {
    /** 从 VOLATILE 晋升为 STABLE 所需的最小强化次数。 */
    const val PROMOTE_COUNT = 3L

    /** 晋升窗口（周）：超过该窗口未再次强化，则重新计为 VOLATILE 累计。 */
    const val PROMOTE_WINDOW_WEEKS = 6

    /** STABLE 记忆的可信度半衰期（天）。 */
    const val STABLE_HALF_LIFE_DAYS = 90

    /** VOLATILE 记忆的可信度半衰期（天）。 */
    const val VOLATILE_HALF_LIFE_DAYS = 14

    /** 可信度低于该阈值时，记忆进入 INACTIVE 冷存储。 */
    const val INACTIVE_THRESHOLD = 0.30f

    /** 每次强化时可信度向 1.0 收敛的比例。 */
    const val REINFORCE_RATE = 0.5f

    /** 每次强化时重要性向 1.0 收敛的比例。 */
    const val IMPORTANCE_REINFORCE_RATE = 0.5f
}
