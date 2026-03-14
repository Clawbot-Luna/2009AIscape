# Implementation Guide (v1.0)
Mapping the automation layer onto the 2009Scape server codebase.

## Assumed Project Layout (from research)
```
2009scape/
├── Server/
│   ├── src/
│   │   ├── com/２００９scape/
│   │   │   ├── server/
│   │   │   │   ├── Server.java
│   │   │   │   ├── World.java
│   │   │   │   ├── entity/player/Player.java
│   │   │   │   ├── entity/npc/Npc.java
│   │   │   │   ├── skill/Skills.java
│   │   │   │   ├── action/Action.java
│   │   │   │   ├── action/ActionQueue.java
│   │   │   │   ├── object/GameObject.java
│   │   │   │   ├── pathfinding/Pathfinder.java
│   │   │   │   └── io/WorldLoader.java
│   │   └── ... 
│   └── data/configs/...
├── RT4-client/...
└── build.gradle
```

## Modification Points

### 1. Player Class Extensions
File: `Server/src/.../entity/player/Player.java`

Add fields:
```java
private TaskQueue taskQueue = new TaskQueue(this);
private Task activeTask = null;
private TaskLogic taskLogic = new TaskLogic(this);
```

Add methods:
```java
public void pulse() {
    // existing tick logic...
    taskLogic.pulse(); // call after base pulse
}
```

### 2. Task & Queue Model
Create new package: `server/automation/`

- `Task.java` – data class (type, target, amount, progress, complete_when, chain_next)
- `TaskQueue.java` – List<Task>, persistence via player save (JSON in player dir)
- `TaskLogic.java` – decides next action each tick based on activeTask

### 3. Action Integration
The `TaskLogic.decideAction()` returns an `Action` subclass (e.g. `SkillAction`) and enqueues it to the player’s `ActionQueue`. The existing `Action` processing loop will execute it. This ensures compatibility with animations, delays, and interruptions.

### 4. Chat Commands
Hook into the existing command system (likely `CommandExecutor`). Add:
- `/task add <json>` or `/task add gather tree 500`
- `/task list`
- `/task cancel <id>`
- `/tasklog`

### 5. Transparency
Log decisions to a `task_log.txt` per player; also show in chat via `/tasklog`.

### 6. Persistence
During player save (likely `Player.save()`), also serialize `taskQueue` and `activeTask`. On load, restore.

## Build & Run
- Use Gradle: `./gradlew runServer` (or whatever tasks exist)
- Set working dir to `Server/`
- Test locally, then optionally add client mod for UI overlay (future)

## Risks
- If the server uses a different tick interval, adjust the `TaskLogic.pulse()` frequency accordingly.
- Pathfinding: reuse existing `Pathfinder` to move to objects.
- Ensure actions don’t conflict with manual player input: if player moves/cancels, set `activeTask = null` or pause.

## Next Steps
1. Clone and build 2009scape server.
2. Locate the actual package/class names.
3. Implement `Task` model and simple `/task add` command.
4. Implement `TaskLogic` for woodcutting (gather trees).
5. Test autonomous loop.

