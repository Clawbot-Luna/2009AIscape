# 2009AIscape — Game Design Document (v1.1)

## 1. Project Overview
Transform the 2009Scape open-source RuneScape server into a management-style automation game where characters execute player‑defined tasks autonomously, enabling strategic planning instead of manual clicking.

- **Source:** https://gitlab.com/2009scape/2009scape (server + client)
- **Target repo:** https://github.com/Clawbot-Luna/2009AIscape
- **Goal:** Private sandbox for AI‑agent automation experiments on a living game world.

## 2. Core Concept Recap
- Player issues tasks (e.g. “Cut 500 logs”, “Reach Woodcutting lvl 30”)
- Character autonomously performs actions to complete the task
- Future: task chains (Mine → Smelt → Smith → Sell)

## 3. Key Design Pillars
- Automation‑First: once a task is set, the game plays itself
- Strategic Planning: manage long‑term goals
- System Transparency: explain decisions to player
- Expandable Automation: support multi‑step chains and AI decision layers

## 4. 2009Scape Architecture (Technical Analysis)
From research:
- **Server**: Java (Gradle), main entry likely `Server/` with tick loop
- **Client**: RT4 client (Java 8/15), communicates with server via protocol
- **World tick**: typical RuneScape servers tick every 600ms; actions are queued/processed per tick
- **Player object**: holds skills, inventory, position, and action queues
- **Skills**: 24 skills, each with own XP, level, and actions (e.g. Woodcutting: Chop tree, use axe)
- **Interactions**: Object/NPC interactions via `ObjectClick`/`NpcClick` handlers
- **Pathfinding**: region‑based (loads of `Region` objects, obstacles)
- **Data**: JSON configs for items, NPCs, objects, drops (in `Server/data/configs/`)

Critical files to modify:
- `Player.java` (or equivalent) — add TaskQueue, TaskEngine
- `Skills.java` — expose skill levels/XP for task conditions
- `Action/` handlers — allow automation triggers
- `World.java` — tick integration

## 5. Proposed Automation Layer API (v1)

### 5.1 Task Definition (player‑facing)
```yaml
task_id: auto_001
type: gather   # gather | skill_level | craft | combat | custom
target: "Tree"
item_id: 1     # if gathering a specific item
amount: 500
skill: "Woodcutting"
complete_when:
  - condition: inventory_contains
    item_id: 1
    amount: 500
  # OR
  - condition: skill_level_reached
    skill: "Woodcutting"
    level: 30
priority: 1
chain_next: task_id_002   # optional
```

### 5.2 In‑game TaskEngine (server‑side)
- Attach to `Player`:
  - `TaskQueue queue`
  - `Task activeTask`
  - `TaskLogic logic` (strategy pattern)

- Each tick (600ms):
  1. Check `activeTask` completion via `complete_when`
  2. If none, pick next queued task → `activeTask`
  3. `logic.decideAction(activeTask)` → returns `Action` (Chop, Mine, Walk, etc.)
  4. Enqueue action to player’s action queue
  5. Log decision for transparency

### 5.3 Transparency UI
- Chat command: `/tasks` → shows queue, progress, current action
- Chat command: `/tasklog` → recent decisions (why did it chop that tree?)
- Optional: small overlay panel (client mod) showing task status

## 6. Implementation Milestones

- **M1**: Codebase navigation & mapping (Week 1)
  - Identify core classes, build & run locally
  - Document entry points for actions & skills

- **M2**: Task system data model (Week 1–2)
  - Add `Task` class, queue persistence (save/load)
  - Chat commands to add/list/remove tasks

- **M3**: Behaviour engine stub (Week 2–3)
  - Simple greedy chooser: for `gather` tasks, find nearest resource node of target type
  - Integrate with action queue

- **M4**: First autonomous loop (Week 3–4)
  - Verify character can chop trees until quantity reached without player input

- **M5**: Multi‑skill support (Week 4–5)
  - Mining, Fishing, Combat (basic)

- **M6**: Chain execution (Week 5–6)
  - `chain_next` linking; seamless transition

- **M7**: Transparency & tuning (Week 6–7)
  - `/tasklog`, decision explanations, fail‑safes (stuck detection)

- **M8**: Documentation & release (Week 7–8)
  - README, usage guide, example task chains

## 7. Risks & Mitigations
- **Server tick constraints**: keep logic lightweight; avoid blocking
- **Pathfinding edge cases**: use existing pathfinder; add timeout & retry
- **State persistence**: store tasks in player file (JSON) to survive logouts
- **Anti‑macro measures**: this is a private experimental fork; we control the server, so no need to bypass external anti‑bot

## 8. Success Metrics
- Character completes “gather X” tasks without manual clicks
- Tasks persist across logout/login
- At least three skill types supported
- Task chains execute end‑to‑end

## 9. Future Extensions
- AI planner (GPT‑4/o1 to generate task chains from goals)
- Economic simulation: bots in Grand Exchange to emulate market
- Multi‑agent collaboration (multiple characters in party)
- External dashboard (web UI) to manage tasks

---
