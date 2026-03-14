# Implementation Plan (v1.0)
Concrete steps to add automation layer to 2009Scape.

## 0. Fork & Setup
- Fork `https://gitlab.com/2009scape/2009scape` to GitHub (our repo: `Clawbot-Luna/2009AIscape`).
- Clone the fork locally: `git clone https://github.com/Clawbot-Luna/2009AIscape.git`
- Build server: `./gradlew build` (or `./gradlew runServer`). Ensure it runs.

## 1. Create Automation Package
In `Server/src/main/core/automation/` (new package), add:

- `Task.kt` – data class for tasks
- `TaskQueue.kt` – list of tasks, persistence, active task handling
- `TaskLogic.kt` – decides next action (find nearest resource, etc.)
- `TaskPulse.kt` – the pulse that runs on the player, calls TaskLogic each tick
- `TaskCommands.kt` – chat command definitions

## 2. Task.kt (template)

```kotlin
package core.automation

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
    val target: String,        // e.g. "Tree", "Copper rock"
    val itemId: Int? = null,
    val amount: Int,
    val skill: String? = null,
    val completeWhen: List<CompletionRule>,
    val priority: Int = 1,
    val chainNext: String? = null
) {
    var progress: Int = 0
        private set

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

    var player: Player? = null // set when task is enqueued
}
```

## 3. TaskQueue.kt (template)

```kotlin
package core.automation

import core.game.node.entity.player.Player
import org.json.simple.JSONArray
import org.json.simple.JSONObject

class TaskQueue(private val player: Player) {
    private val queue: MutableList<Task> = mutableListOf()
    var activeTask: Task? = null
        private set

    fun enqueue(task: Task) {
        task.player = player
        queue.add(task)
        queue.sortBy { -it.priority } // highest priority first
    }

    fun cancel(taskId: String) {
        queue.removeAll { it.id == taskId }
        if (activeTask?.id == taskId) activeTask = null
    }

    /** Called each game tick by TaskPulse */
    fun tick() {
        if (activeTask == null) {
            activeTask = queue.firstOrNull()
            if (activeTask != null) {
                player.sendMessage("Starting task: ${activeTask!!.id} (${activeTask!!.type})")
            }
        }
        activeTask?.let { task ->
            // Check completion
            if (task.isComplete()) {
                player.sendMessage("Task ${task.id} complete!")
                queue.remove(task)
                val nextId = task.chainNext
                if (nextId != null) {
                    // Find next task in queue with that id and promote it? Or we can chain by auto-enqueueing a follow-up.
                    // For now, just remove activeTask and let queue pop next.
                }
                activeTask = null
                return
            }
            // Delegate to TaskLogic to decide if we need to move/gather
            TaskLogic.decideAndAct(task, player)
        }
    }

    fun getStatus(): String {
        return if (activeTask != null) {
            "Active: ${activeTask!!.id} – ${activeTask!!.progress}/${activeTask!!.amount}"
        } else {
            "Queue: ${queue.size} tasks"
        }
    }

    /** Serialize for saving */
    fun serialize(): JSONObject {
        val root = JSONObject()
        val activeId = activeTask?.id
        if (activeId != null) root.put("activeTaskId", activeId)
        val queueArr = JSONArray()
        queue.forEach { task ->
            val obj = JSONObject()
            obj.put("id", task.id)
            obj.put("type", task.type.name)
            obj.put("target", task.target)
            obj.put("itemId", task.itemId ?: JSONObject.NULL)
            obj.put("amount", task.amount)
            obj.put("skill", task.skill ?: JSONObject.NULL)
            // simplifed: serialize only first completion rule
            val rule = task.completeWhen.firstOrNull()
            if (rule != null) {
                obj.put("condition", rule.condition.name)
                obj.put("itemId", rule.itemId ?: JSONObject.NULL)
                obj.put("skill", rule.skill ?: JSONObject.NULL)
                obj.put("level", rule.level ?: JSONObject.NULL)
            }
            obj.put("priority", task.priority)
            obj.put("chainNext", task.chainNext ?: JSONObject.NULL)
            obj.put("progress", task.progress)
            queueArr.add(obj)
        }
        root.put("queue", queueArr)
        return root
    }

    /** Deserialize from saved data */
    fun deserialize(data: JSONObject) {
        queue.clear()
        val activeId = data["activeTaskId"] as? String
        val queueArr = data["queue"] as? JSONArray ?: return
        for (item in queueArr) {
            val obj = item as JSONObject
            val type = TaskType.valueOf(obj["type"] as String)
            val rule = CompletionRule(
                condition = CompletionCondition.valueOf(obj["condition"] as String),
                itemId = (obj["itemId"] as? Long)?.toInt(),
                skill = obj["skill"] as? String,
                level = (obj["level"] as? Long)?.toInt()
            )
            val task = Task(
                id = obj["id"] as String,
                type = type,
                target = obj["target"] as String,
                itemId = (obj["itemId"] as? Long)?.toInt(),
                amount = (obj["amount"] as Long).toInt(),
                skill = obj["skill"] as? String,
                completeWhen = listOf(rule),
                priority = (obj["priority"] as Long).toInt(),
                chainNext = obj["chainNext"] as? String
            )
            task.progress = (obj["progress"] as Long).toInt()
            task.player = player
            queue.add(task)
            if (task.id == activeId) activeTask = task
        }
    }
}
```

## 4. TaskPulse.kt

```kotlin
package core.automation

import core.game.node.entity.player.Player
import core.game.system.task.Pulse

class TaskPulse(private val player: Player) : Pulse(1) { // run every tick
    override fun pulse(): Boolean {
        player.taskQueue.tick()
        return false // never stop on its own; TaskQueue manages tasks
    }
}
```

**Attach to Player:**
In `Player.java` (or via mixin), add:
```java
private TaskQueue taskQueue;
private TaskPulse taskPulse;

public Player(...) {
    // existing init...
    this.taskQueue = new TaskQueue(this);
    this.taskPulse = new TaskPulse(this);
    getPulseManager().run(taskPulse); // submit immediately
}
```
Alternatively, if we don't want to modify core, we can submit on first command via `player.getPulseManager().run(new TaskPulse(player));`

## 5. TaskLogic.kt (core decision engine)

```kotlin
package core.automation

import core.game.node.entity.player.Player
import core.game.node.scenery.Scenery
import core.game.world.map.RegionManager
import core.game.world.map.path.Pathfinder
import core.api.submitIndividualPulse
import content.global.skill.gather.woodcutting.WoodcuttingNode
import content.global.skill.gather.woodcutting.WoodcuttingSkillPulse

object TaskLogic {
    fun decideAndAct(task: Task, player: Player) {
        when (task.type) {
            TaskType.GATHER -> handleGather(task, player)
            TaskType.SKILL_LEVEL -> handleSkillLevel(task, player)
            else -> { /* noop */ }
        }
    }

    private fun handleGather(task: Task, player: Player) {
        val targetItemId = task.itemId ?: return
        // For now, only woodcutting: itemId must correspond to a WoodcuttingNode reward
        val node = findNearestNode(player, targetItemId) ?: run {
            player.sendMessage("No available resource nodes nearby.")
            return
        }
        val dist = player.location.getDistance(node.location)
        if (dist > 1) {
            // Move towards node
            if (!isMovingTowards(player, node)) {
                // Start movement pulse
                player.pulseManager.run(object : MovementPulse(player, node) {
                    override fun pulse(): Boolean {
                        // default movement logic; return false until arrived
                        return super.pulse()
                    }
                })
            }
        } else {
            // At node: ensure woodcutting pulse is running
            if (!isWoodcuttingPulseActive(player)) {
                submitIndividualPulse(player, WoodcuttingSkillPulse(player, node))
            }
            // Progress tracking is done by checking inventory outside
            val current = player.inventory.getAmount(targetItemId)
            if (current > task.progress) {
                task.addProgress(current - task.progress)
            }
        }
    }

    private fun handleSkillLevel(task: Task, player: Player) {
        val skillName = task.skill ?: return
        val targetLevel = task.amount
        val current = player.skills.getLevel(skillName)
        if (current >= targetLevel) {
            task.progress = task.amount // complete
            return
        }
        // For now, rely on natural play; we could auto-train by starting appropriate skill pulse if available.
        // Example: if skill is Woodcutting, we can gather trees (above). But need to determine what resource to gather to train.
        // This is left for future iteration.
    }

    private fun findNearestNode(player: Player, itemId: Int): Scenery? {
        val loc = player.location
        val region = RegionManager.forId(loc.regionId)
        val plane = region.planes[loc.z]
        val objects = plane.objects
        var nearest: Scenery? = null
        var best = Int.MAX_VALUE
        for (x in 0 until 64) {
            for (y in 0 until 64) {
                val obj = objects[x][y] ?: continue
                val nodeOpt = WoodcuttingNode.forId(obj.id)
                if (!nodeOpt.isPresent) continue
                val node = nodeOpt.get()
                if (node.reward != itemId) continue
                val dist = loc.getDistance(obj.location)
                if (dist < best) {
                    best = dist
                    nearest = obj
                }
            }
        }
        return nearest
    }

    private fun isMovingTowards(player: Player, target: Scenery): Boolean {
        val current = player.pulseManager.current
        return current is MovementPulse && current.destination == target
    }

    private fun isWoodcuttingPulseActive(player: Player): Boolean {
        val current = player.pulseManager.current
        return current is WoodcuttingSkillPulse
    }
}
```

**Notes:**
- This logic is simplified. Need to handle pulse lifecycle: when movement finishes, start woodcutting; when woodcutting finishes (tree depleted), stop and find next tree.
- A more robust design would have state flags in Task (e.g., `state = MOVING | GATHERING`) but we can infer from current pulse type.

## 6. TaskCommands.kt

```kotlin
package core.automation

import core.game.node.entity.player.Player
import core.game.system.command.Command
import core.game.system.command.CommandMapping
import core.game.system.command.Privilege
import core.api.sendChat

object AutomationCommandSet : CommandSet(Privilege.STANDARD) {
    override fun defineCommands() {
        define("task", Privilege.STANDARD, "::task <add|list|cancel|log>", "Automation commands") { player, args ->
            if (args.size < 2) {
                sendChat(player, "Usage: ::task add <gather> <target> <amount>")
                return@define
            }
            when (args[1].lowercase()) {
                "add" -> handleAdd(player, args)
                "list" -> handleList(player)
                "cancel" -> handleCancel(player, args)
                "log" -> handleLog(player)
                else -> sendChat(player, "Unknown subcommand")
            }
        }
    }

    private fun handleAdd(player: Player, args: Array<String>) {
        // Very simple parser: ::task add gather <target> <amount>
        if (args.size < 5) {
            sendChat(player, "Usage: ::task add gather <target> <amount>")
            return
        }
        val type = args[2].lowercase()
        val target = args[3]
        val amount = args[4].toIntOrNull() ?: run {
            sendChat(player, "Amount must be a number")
            return
        }
        // We'll map target to itemId via a lookup table (e.g., "tree" -> any tree? For now, we require itemId numeric or name mapping.
        // For PoC, we can just use item ID directly: gather <itemId> <amount>
        // We'll skip full parser for now; the GDD defines JSON format, but we'll start simple.
        sendChat(player, "Task command placeholder – to be implemented in code.")
    }

    private fun handleList(player: Player) {
        val status = player.taskQueue.getStatus()
        sendChat(player, status)
    }

    private fun handleCancel(player: Player, args: Array<String>) {
        if (args.size < 3) {
            sendChat(player, "Usage: ::task cancel <task_id>")
            return
        }
        player.taskQueue.cancel(args[2])
        sendChat(player, "Cancelled ${args[2]}")
    }

    private fun handleLog(player: Player) {
        // TODO: read from a log buffer in TaskQueue
        sendChat(player, "Log not implemented yet.")
    }
}
```

Then ensure the command set is registered. Typically, the server loads all classes implementing `CommandSet` via ServiceLoader or manual registration in a plugin. Look for a file like `core/plugin/CommandPlugin.kt` or similar.

Search for where command sets are loaded:
```bash
grep -rn "AutomationCommandSet\|CommandSet" Server/src/main/core | head -5
```

Likely there's a `CommandPlugin` that scans for `@Initializable` classes. Our `AutomationCommandSet` should be placed in a package scanned by the plugin (often `core.game.system.command.sets`). If we put it there, it might auto-register. Check `CommandPlugin.kt`.

We'll need to add the file and maybe add an `@Initializable` annotation (as seen in MiscCommandSet). Then the plugin will auto-detect.

## 7. Persistence Integration

Two approaches:

**A) Direct modification to core classes** (simpler but invasive):
- In `Player.java`: add fields `taskQueue` and `taskPulse` (already mentioned).
- In `PlayerSaveParser.kt`: add `parseTasks()` and call it in `parseData()`.
- In `PlayerSaver.kt`: add `saveTasks(root)` and call it in `populate()`.

**B) Content Hook** (cleaner, but we need to learn the hook system):
- Implement `PersistPlayer` interface in `AutomationPersistence` class.
- Register with `PlayerSaveParser.contentHooks.add(AutomationPersistence)` and `PlayerSaver.contentHooks.add(...)`.
- The hook gets `parsePlayer`/`savePlayer` callbacks.
- This avoids editing core files.

Given we control the fork, direct modification is acceptable for speed. We'll edit the two files.

**SaveTasks example:**

```kotlin
fun saveTasks(root: JSONObject) {
    val queue = player.taskQueue
    root.put("automation_tasks", queue.serialize())
}
```

**ParseTasks example:**

```kotlin
fun parseTasks() {
    if (saveFile!!.containsKey("automation_tasks")) {
        val data = saveFile!!["automation_tasks"] as JSONObject
        player.taskQueue.deserialize(data)
    }
}
```

## 8. Build & Test

- Compile: `./gradlew build`
- Run server: `./gradlew runServer`
- Connect with client from 2009scape releases.
- Create a player, log in, try commands:
  - `::task add gather 1511 100` (1511 = logs, but we need to map "tree" to actual node; for PoC we could accept any tree by not checking itemId, just node type)
  - `::task list` should show queue
  - Observe character moving to nearest tree and chopping.

## 9. Iteration

- Add more resource types: Mining (use `MiningNode`), Fishing (`FishingNode`), etc.
- Add fail-safes: if stuck (can't reach node), cancel task after timeout.
- Add chaining support: when task complete, automatically enqueue next.
- Add transparency: `/tasklog` reads from `TaskLogic` decisions (need to log decisions as they happen; maybe use a `StringBuilder` ring buffer in TaskQueue).

---
