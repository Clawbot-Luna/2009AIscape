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
            if (task.isComplete()) {
                player.sendMessage("Task ${task.id} complete!")
                queue.remove(task)
                val nextId = task.chainNext
                if (nextId != null) {
                    // Find next task in queue with that id and promote it? Or just remove active and let queue pop next.
                    // For now, just clear active and next queued item will start.
                }
                activeTask = null
                return
            }
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
