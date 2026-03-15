package core.automation

import core.game.node.entity.player.Player

enum class TaskType { GATHER, SKILL_LEVEL, CRAFT, COMBAT, CUSTOM }

enum class CompletionCondition { INVENTORY_CONTAINS, SKILL_LEVEL_REACHED }

data class CompletionRule(
    val condition: CompletionCondition,
    val itemId: Int? = null,
    val skill: String? = null,
    val level: Int? = null
)

data class Task(
    val id: String,
    val type: TaskType,
    val target: String,
    val itemId: Int? = null,
    val amount: Int,
    val skill: String? = null,
    val completeWhen: List<CompletionRule>,
    val priority: Int = 1,
    val chainNext: String? = null
) {
    var progress: Int = 0
        private set

    // transient player reference – not serialized
    var player: Player? = null

    fun addProgress(delta: Int) {
        progress += delta
        if (progress > amount) progress = amount
    }

    fun isComplete(): Boolean {
        return completeWhen.any { rule ->
            when (rule.condition) {
                CompletionCondition.INVENTORY_CONTAINS -> player?.let {
                    it.inventory.getAmount(rule.itemId ?: 0) >= amount
                } ?: false
                CompletionCondition.SKILL_LEVEL_REACHED -> player?.let {
                    it.skills.getLevel(rule.skill ?: "") >= (rule.level ?: 0)
                } ?: false
            }
        }
    }
}
