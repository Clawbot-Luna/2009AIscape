# 2009AIscape — Game Design Document (v1.2)
*Expanded with Kotlin implementation details and precise file targets.*

## 1. Project Overview
Transform 2009Scape into a management‑style automation game where characters autonomously execute player‑defined tasks.

- **Source**: https://gitlab.com/2009scape/2009scape (server + client, migrating to Kotlin)
- **Our Fork**: https://github.com/Clawbot-Luna/2009AIscape (implementation & experiments)
- **Goal**: Private sandbox for AI‑agent automation research on a live‑feeling MMORPG simulation.

## 2. Core Concept
- Player issues tasks (via chat or UI)
- Character runs tasks automatically until completion
- Task chains enable multi‑step workflows (e.g. Mine → Smelt → Smith → Sell)
- Full transparency: `/tasks`, `/tasklog` explain decisions

## 3. Design Pillars
- **Automation‑First**: minimize manual clicking
- **Strategic Planning**: manage goals, not actions
- **Transparency**: understand why the character did X
- **Extensibility**: plug‑in new task types, AI planners, dashboards

## 4. 2009Scape Architecture (Kotlin‑Focused)
- **Build system**: Gradle (Kotlin DSL likely)
- **Server**: Kotlin/Java mix, moving to pure Kotlin
- **Tick**: ~600ms; world updates processed in `World.tick()` or similar
- **Player entity**: likely `Player.kt` in `server.entity.player`
- **Skills**: `Skills.kt` with enums for skill types and XP handling
- **Actions**: `Action.kt` representing discrete game actions with delay/state
- **Pathfinding**: region‑based, likely `Pathfinder.kt`
- **Configs**: JSON in `Server/data/configs/` (items, NPCs, objects, drops)

### Assumed Package Structure (Kotlin)
```
src/main/kotlin/
└── org/runite/
    ├── server/
    │   ├── Server.kt
    │   ├── World.kt
    │   ├── entity/
    │   │   ├── player/Player.kt
    │   │   ├── npc/Npc.kt
    │   │   └── object/GameObject.kt
    │   ├── skill/Skills.kt
    │   ├── action/Action.kt
    │   ├── action/ActionQueue.kt
    │   ├── pathfinding/Pathfinder.kt
    │   └── automation/   <-- our new package
    │       ├── Task.kt
    │       ├── TaskQueue.kt
    │       ├── TaskLogic.kt
    │       └── commands/TaskCommands.kt
    └── client/...
```

## 5. Automation Layer API (v1)

### 5.1 Task Data Model (Kotlin)
```kotlin
enum class TaskType { GATHER, SKILL_LEVEL, CRAFT, COMBAT, CUSTOM }

enum class CompletionCondition { INVENTORY_CONTAINS, SKILL_LEVEL_REACHED, ITEM_EQUIPPED }

data class Task(
    val id: String,
    val type: TaskType,
    val target: String,        // e.g. "Tree", "Copper rock"
    val itemId: Int? = null,
    val amount: Int,
    val skill: String? = null,
    val completeWhen: List<CompletionRule>,
    val priority: Int = 1,
    val chainNext: String? = null
)

data class CompletionRule(
    val condition: CompletionCondition,
    val itemId: Int? = null,
    val skill: String? = null,
    val level: Int? = null
)
```

### 5.2 TaskQueue (persisted with player)
```kotlin
class TaskQueue(private val player: Player) {
    private val queue: MutableList<Task> = mutableListOf()
    private var activeTask: Task? = null

    fun enqueue(task: Task) { ... }
    fun cancel(taskId: String) { ... }
    fun tick(): Boolean { // returns true if performed an action
        if (activeTask == null) {
            activeTask = queue.firstOrNull { it.priority >= 0 }
            // log selection
        }
        activeTask?.let { task ->
            val action = TaskLogic.decideAction(task)
            if (action != null) {
                player.actionQueue.enqueue(action)
                return true
            }
        }
        return false
    }

    fun checkCompletion(): Boolean { ... } // updates activeTask/queue
    fun serialize(): Map<String, Any?> { ... } // for saving
    fun deserialize(data: Map<String, Any?>) { ... }
}
```

### 5.3 TaskLogic (core decision engine)
```kotlin
object TaskLogic {
    fun decideAction(task: Task, player: Player): Action? {
        return when (task.type) {
            TaskType.GATHER -> findNearestResource(task.target, player)
                ?.let { MoveToAction(it.location) }
            TaskType.SKILL_LEVEL -> ...
            // …
        }
    }

    private fun findNearestResource(targetName: String, player: Player): GameObject? {
        // reuse existing pathfinder; scan nearby objects by name/id
    }
}
```

### 5.4 Integration Points
- `Player.kt`: add `taskQueue` field; call `taskQueue.tick()` inside `Player.pulse()`
- `ActionQueue.kt`: ensure automation actions can be enqueued alongside manual ones
- `CommandExecutor.kt`: register `/task add|list|cancel|log`

### 5.5 Persistence
- During player save (likely `Player.save()` in a specific format), include `taskQueue.serialize()` as JSON
- During load, restore via `TaskQueue.deserialize()`

### 5.6 Transparency
- Log to `logs/tasklog_<player>.txt` or in‑game chat with `/tasklog`
- Each decision includes reason: e.g. “Selected oak tree (id=1) because nearest to (x,y)”

## 6. Implementation Milestones (Kotlin)

| Milestone | Deliverable | Est. |
|-----------|-------------|------|
| M1 – Environment | Clone & build 2009scape server; run local | 1d |
| M2 – Task model | `Task.kt`, `TaskQueue.kt`, chat `/task add` | 2d |
| M3 – Persistence | Save/load task queue with player | 1d |
| M4 – Greedy gatherer | Woodcutting automation (trees) | 3d |
| M5 – Transparency | `/tasks`, `/tasklog` outputs | 1d |
| M6 – Multi‑skill | Mining, Fishing, Combat basics | 3d |
| M7 – Chaining | `chain_next` execution | 2d |
| M8 – Tuning & Docs | Readme, examples, video demo | 2d |

## 7. Risks & Mitigations
- **Kotlin migration state**: If core code still Java, write our layer in Kotlin or match the prevailing language.
- **Tick rate mismatch**: verify server tick (likely 600ms); adjust logic cycle accordingly.
- **Pathfinding reuse**: adapt existing `Pathfinder` API; do not reinvent.
- **Server performance**: keep per‑tick logic O(1) where possible; avoid heavy scans.
- **Interruption handling**: if player moves manually, cancel/suspend active task.

## 8. Success Metrics
- Character gathers 100 logs without manual clicks
- Tasks survive logout/login
- At least 3 skill types automated
- Task chain runs end‑to‑end (mine → bank → smelt)

## 9. Future Extensions
- External dashboard (WebSocket to server)
- AI planner (LLM generates task chains from natural language)
- Multi‑agent coordination (team play)
- Economic bot simulation in Grand Exchange

---
