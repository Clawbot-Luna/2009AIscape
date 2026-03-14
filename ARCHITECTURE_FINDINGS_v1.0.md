# 2009Scape Architecture Findings (v1.0)

## Source Repository
- GitLab: https://gitlab.com/2009scape/2009scape
- Cloned locally for research on 2026-03-15

## Key Classes & Packages

| Concern | Class (path) | Notes |
|---------|--------------|-------|
| Player entity | `core.game.node.entity.player.Player` (Player.java) | Holds skills, inventory, equipment, pulseManager, etc. |
| Skills | `core.game.node.entity.skill.Skills` (Skills.java) | Methods: `getLevel(skill)`, `addExperience(skill, xp)` |
| Scenery (objects) | `core.game.node.scenery.Scenery` | Represents game objects (trees, rocks, etc.) |
| Location | `core.game.world.map.Location` | X/Y/Z coordinates |
| Region/RegionPlane | `core.game.world.map.Region` / `RegionPlane` | 64x64 tile planes; objects stored in `RegionPlane.objects[x][y]` |
| RegionManager | `core.game.world.map.RegionManager` (Kotlin object) | `forId(regionId)`, `getObject(location)`, `getLocalPlayers(...)` |
| Pulse (base) | `core.game.system.task.Pulse` | Abstract; `pulse()` called every tick after delay; `running` flag |
| PulseManager | `core.game.node.entity.impl.PulseManager` | `entity.getPulseManager().run(pulse)`; manages current pulses |
| MovementPulse | `core.game.interaction.MovementPulse` | Abstract; use `new MovementPulse(entity, destination) { }` |
| Pathfinder | `core.game.world.map.path.Pathfinder` | `Pathfinder.find(entity, destination)` returns `Path` |
| Woodcutting pulse | `content.global.skill.gather.woodcutting.WoodcuttingSkillPulse` | Constructor: `WoodcuttingSkillPulse(Player player, Scenery node)` |
| Woodcutting nodes | `content.global.skill.gather.woodcutting.WoodcuttingNode` (enum) | Maps object IDs to tree types, required level, reward item, XP |
| Axe interaction | `content.global.handlers.item.withobject.AxeOnTree` (Kotlin) | Shows how to trigger woodcutting: `submitIndividualPulse(player, WoodcuttingSkillPulse(player, tree))` |
| API utilities | `core.api.ContentAPI` (Kotlin) | `submitIndividualPulse(entity, pulse)` wrapper |

## Game Tick & Pulses

- World ticks every 600ms.
- `Pulse` subclasses are submitted to `PulseManager`.
- `Pulse.update()` is called after `delay` ticks; if `pulse()` returns `true`, the pulse stops.
- MovementPulse: handles pathfinding and movement; stops when destination reached.
- WoodcuttingSkillPulse: loops internally, producing logs periodically until tree depleted or inventory full.

## Proposed Automation Integration Points

### 1. Task Model
Add classes in `server/automation/` package in our fork:
- `Task.kt` – data class for tasks (type, target, amount, progress, completion rules)
- `TaskQueue.kt` – manages task list, persistence, active task selection
- `TaskLogic.kt` – core decision engine (what action to take next)
- `TaskPulse.kt` – the main pulse attached to the player; calls `TaskLogic` each tick and dispatches actions

### 2. Attaching to Player
In `Player.java` (or via mixin/plugin), add:
```java
private TaskQueue taskQueue = new TaskQueue(this);
private TaskPulse taskPulse;
```
During player initialization (likely after `Player` construction), create `taskPulse` and submit it:
```java
getPulseManager().run(player.taskPulse = new TaskPulse(this));
```
The `TaskPulse` will live alongside other pulses; it should be a low-priority or always-running pulse (maybe use `PulseType.STANDARD`). It will inspect `taskQueue` and take actions.

### 3. Finding Resources
Use `RegionManager.forId(player.getLocation().getRegionId())` → `Region` → `RegionPlane plane = region.getPlanes()[z]` → `Scenery[][] objects = plane.getObjects()`. Iterate over the 64x64 grid, collect non‑null objects whose IDs match any `WoodcuttingNode` (or MiningNode, etc.). Compute distance to player; pick nearest.

### 4. Movement
If nearest target > interaction range (e.g., > 1 tile), create a `MovementPulse` and submit:
```java
getPulseManager().run(new MovementPulse(player, targetScenery) { ... });
```
Optionally wait until movement completes (check `player.getLocation().equals(targetScenery.getLocation())` or distance < 2).

### 5. Gathering
Once at the node, start the appropriate skill pulse:
```java
ContentAPI.submitIndividualPulse(player, new WoodcuttingSkillPulse(player, targetScenery));
```
Monitor inventory: `player.getInventory().getAmount(itemId)` to track progress. When amount reached, mark task complete; optionally clear any ongoing pulse if still running.

### 6. Chat Commands
Hook into existing command system (likely via `core.game.system.command.Command`). Create `TaskCommands.kt`/`.java` with handlers for:
- `/task add <type> <target> <amount>`
- `/task list`
- `/task cancel <id>`
- `/tasklog`

Regenerate or document integration point. Look for `CommandManager` or similar.

### 7. Persistence
Add fields to `TaskQueue` and `Task` that are serializable to JSON. Extend player save (likely `Player.save()` / `Player.parse`) to include task data in the player's save file (probably stored under `data/players/` as `.txt` or `.json`).

## Concrete Code Snippets

### Finding a nearby tree (pseudo‑Kotlin)
```kotlin
fun findNearestTree(player: Player): Scenery? {
    val loc = player.getLocation()
    val region = RegionManager.forId(loc.getRegionId())
    val plane = region.getPlanes()[loc.getZ()]
    val objects = plane.getObjects()
    var nearest: Scenery? = null
    var bestDist = Int.MAX_VALUE
    for (x in 0..63) {
        for (y in 0..63) {
            val obj = objects[x][y] ?: continue
            if (!WoodcuttingNode.forId(obj.getId()).isPresent) continue
            val dist = loc.getXDistance(obj.getLocation()) + loc.getYDistance(obj.getLocation())
            if (dist < bestDist) {
                bestDist = dist
                nearest = obj
            }
        }
    }
    return nearest
}
```

### Submitting a movement pulse (Kotlin)
```kotlin
player.getPulseManager().run(object : MovementPulse(player, target) {
    override fun pulse(): Boolean {
        // MovementPulse default implementation moves; return false until arrived
        return super.pulse()
    }
})
```

### Starting woodcutting
```kotlin
ContentAPI.submitIndividualPulse(player, WoodcuttingSkillPulse(player, target))
```

## Unknowns & Next Steps

- Confirm exact command registration mechanism (search for `Command` class or `CommandExecutor`).
- Determine player save format and hook points for task persistence.
- Decide pulse priority: ensure TaskPulse runs even if other pulses present (maybe use a separate pulse type or always‑re‑submit).
- Design graceful degradation: if pathfinding fails, mark task as blocked and wait.
- Build the 2009scape server locally to test and refine these assumptions.

---
