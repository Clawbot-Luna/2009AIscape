# Task Commands (Proposed)

Players issue tasks via in‑game chat. Commands are case‑insensitive.

## Add Task
```
/task add gather tree 500
```
Params:
- `gather` – task type
- `tree` – target resource (maps to object name)
- `500` – amount

JSON form (advanced):
```
/task add {"type":"skill_level","skill":"Woodcutting","amount":30}
```

## List Tasks
```
/task list
```
Shows queue, active task, progress.

## Cancel Task
```
/task cancel auto_001
```

## Task Log
```
/tasklog
```
Shows recent decision reasons.

## Example Chains
```
/task add gather oak 200
/task add gather willow 150
```
Gauntlet: use `chain_next` to link; future UI will allow chaining UI.

