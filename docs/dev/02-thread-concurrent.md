# 突破OpenAI限制：Thread并发的实现

> 本文基于工程实现梳理 Thread 级别的并发 Run 能力与消息顺序保障方案，聚焦锁设计、消息原子性、工具输出串行化及并发优化策略。

---

## 并发 Run 的核心挑战

- **OpenAI 原生限制**
  - 原生 Assistants API 约束：同一 `thread` 同时仅允许 1 个 active run。
  - 业务诉求：群聊场景下，在同一个thread中，不同用户的连续提问需要“并发触发、独立返回”。
- **目标**
  - 允许同一 `thread` 在服务端并发执行多个 Run（不受原生限制），同时保证线程内消息时序的可预期与一致性。
  - 在允许并发的情况下，保持消息列表user-assistant的交错顺序。  
---

## 分布式锁的精细化设计

- **锁的粒度与“分段”（按 Thread 分段）**
  - 使用 `Redisson` 的 `RReadWriteLock`，按 `threadId` 维度构造锁键：`ThreadLockService`（`service/ThreadLockService.java`）。
  - 这等价于对全局空间做按线程的“分段锁”，避免全局大锁，提升可扩展性。

- **读/写锁职责划分**
  - 读锁（并发可叠加）
    - 单条消息插入使用：`MessageService.createMessage()` 内部通过 `executeWithReadLock()` 包裹实际落库。
  - 写锁（互斥）
    - 需要原子化的批量/复合操作使用：如 `RunService.createRun()`、`ThreadService.copy/merge` 等通过 `executeWithWriteLock()` 包裹整个事务，保障线程级串行化边界。

- **Thread 级锁与 Message 级锁的协同**
  - Thread 级：`ThreadLockService` 控线程范围内“写串行、读并发”。
  - Message 级：对单条消息的更新采用数据库行级加锁（`findByIdForUpdate`），见 `MessageService.addContent()/updateStatus()` 与 `RunStateManager.updateRunStep*()`，防止同一消息/步骤的并发写入冲突。
  - 组合效果：线程内复合写操作由写锁串行化；单消息更新由 DB 行锁保护，二者叠加实现既保证吞吐又保证一致性的分层并发控制。

---

## 消息顺序保证机制

- **user-assistant 消息对的原子性边界**
  - Run 创建为写锁包裹的单事务：`RunService.createRun()` → `doCreateRun()`（受 `@Transactional` 保护）同时落库 `run`、初始 `message_creation` 步骤、空的 `assistant` 消息；若含 `additional_messages` 亦在同一锁/事务内按序插入。
  - 单条消息的完成边界：
    - 仅 LLM 文字：`MessageExecutor` 在接收 `[LLM_DONE]` 后调用 `RunStateManager.finishMessageCreation()` 一次性落库文本与计量（`usage`），然后推进 `message_creation` 步骤至 `completed`。
  - 含工具调用的处理：
    - 先创建 `tool_calls` 步骤（`startToolCalls()`），内部工具并发执行，工具输出可通过`isFinal=true`配置作为 `assistant` 消息的后续 `content` 片段追加（`MessageService.addContent()`），完成后统一收尾。

- **工具输出串行化，执行-输出解耦**
  - 并发执行：`ToolExecutor` 将可服务端执行的工具并行提交至 `caller` 线程池。
  - 串行输出：`ToolOutputChannel`（`core/tools/ToolOutputChannel.java`）按 `ExecutionContext.currentOutputToolCallId` 控制输出资格：
    - 其他 `tool_call_id` 的输出被缓存，当前 ID 优先输出；
    - 工具完成后，`MessageExecutor` 消费 `[TOOL_DONE]` 并 `finishToolCallOutput()` 释放下一个工具的输出资格。
  - Response API 路径：`ResponseMessageExecutor` 使用 `sequenceNumber/outputIndex/itemId` 显式标注事件顺序，彻底与真实完成时序解耦。

- **跨实例运行一致性**
  - `ServiceMesh/RedisMesh` 维护 `runId -> instanceId` 映射，`RunStateManager` 在终止态清理映射；`submitRequiredAction()` 会在检测到其他实例仍在处理时等待/重试，避免“未收敛就穿透”造成的状态撕裂。

---

## 实现要点与代码索引

- **分布式锁**：`service/ThreadLockService.java`
  - `executeWithReadLock()`：单条消息插入的并发化
  - `executeWithWriteLock()`：Run 创建、Thread 复制/合并等需要原子序的写路径
- **Run 执行与状态机**：
  - 执行循环与选择：`core/run/RunExecutor.java`（`executeLoop()`）
  - 状态落库与事件：`core/run/RunStateManager.java`
- **消息执行与流式事件**：
  - Assistants SSE：`core/run/MessageExecutor.java`
  - Responses SSE：`core/run/ResponseMessageExecutor.java`
- **工具并发与串行输出**：
  - 并发调度：`core/tools/ToolExecutor.java`
  - 串行输出通道：`core/tools/ToolOutputChannel.java`
- **消息落库与顺序约束**：
  - `service/MessageService.java`（行级锁与内容追加）
  - `util/MessageUtils.java`（`checkPre()` 约束 user/assistant/tool 的相邻合法性）
- **跨实例一致性**：
  - `mesh/impl/RedisMesh.java` 与 `configuration/ServiceMeshConfiguration.java`

---

## 小结

- 通过“Thread 维度的分布式读写锁 + 消息行级锁”的分层并发控制，结合工具输出的串行化通道与状态机的乐观校验，项目在突破原生单 Run 限制的同时，保证了线程内消息时序的确定性与可预期。
- 该方案在高并发场景下保持了良好的吞吐与一致性平衡，且具备跨实例可扩展性。
