# 手动采集 PROJECTION 隔离实验

在项目根目录的 **PowerShell 7 交互终端**运行。先自行进入播放场景，保持头显连接；按项目要求确认 platform-tools 为当前版本。脚本运行 `metavr doctor`，不会安装、启动、重启或停止 APK，不会开启额外 compositor 日志。

```powershell
.\scripts\capture-stage-isolation.ps1 -Run -Device '192.168.0.234:5555'
```

默认设备 `192.168.0.234:5555`，可用 `-Device` 指定；`-MetavrExe` 可指定实际 metavr.exe。默认安全上限 600 秒，可用 `-MaxSeconds 900` 调整（45–1800 秒）。上限从设备第一次写属性之前开始计时，包括等待确认。

1. 看到绿色 **「已生效：PROJECTION 已隐藏」** 后，从平面增加曲率自动进入曲面，调尺寸，再减小曲率回到平面；观察视频和裁切边界。只有当前 generation 显示「已生效」时才操作。
2. 完成后在原终端按 **Enter**。脚本停止隔离、精确恢复原始属性并显示读回值，然后收集日志。输出目录会在启动时打印。
3. 如果提示从未确认、拓扑不稳定、重绑次数耗尽或确认丢失，本轮不能作为有效隔离结果。不要把等待时、gap 期间或结束后的操作算进有效区间。

空参数只显示用法，不访问设备。脚本依据设备当时的 PID 和连续 layer 拓扑动态生成 mask，仅接受唯一 PROJECTION、最多一个 CYLINDER 和其余 QUAD。写入前验证索引、mask 位数及 PROJECTION 对应位、归一化层序列，任一字段为空或不一致即拒绝，并记录原始及验证后的 activation plan。动态插层或移层会立即撤销当前 READY 并记为独立 GAP：先清除旧 skipped mask，再取新鲜快照、验证 PID/属性/所有权/deadline，重算 mask 后重新写入。只有连续 3 次快照确认 topology、runtime mask、属性和唯一 PROJECTION 的 `Not Visible` 一致，才发布新的 generation/segment READY。最多尝试 8 次重绑，deadline 不会因重绑延长；gap 期间不可用于用户操作分析，gap 前后 generation 也不能合并。未知类型、重复/缺失 PROJECTION、PID 或所有权变化、外部属性干预、拓扑持续变化及重绑耗尽都会 fail closed，并保留 `TOPOLOGY_UNSTABLE`、`REBIND_EXHAUSTED`、`CONFIRMATION_LOST` 等实际原因。30 秒内无法首次确认隐藏也会结束。对已有隔离或并发属性变化拒绝覆盖。

设备后台 worker 每秒保存带设备 UTC/epoch 的 layer 快照和状态事件，包括 `ACTIVATION_PLAN`、`VALIDATED_PLAN`、`MUTATION_START`、`SNAPSHOT`、`PROPERTY_READBACK`、`ISOLATION_CONFIRMED`、`GAP_START`、`REBIND_PLAN`、`REBIND_APPLIED`、`GAP_END`、`WORKER_RESULT`、`INTERVAL_END` 和 `RESTORATION`。gap 会显式标记为不可用于用户操作分析。独立 watchdog 在原始 deadline 到达时尝试恢复，正常退出也会恢复。主机通过短命令读取仍在更新的确认状态及属性，避免等待长命令缓冲输出后才开始操作。确认要求 runtime mask 与属性一致，且当前唯一 PROJECTION 为 `Not Visible`；不会仅凭 setprop 成功显示已生效。

关闭终端、断连或异常时，设备 watchdog 仍会尝试恢复，但断电、系统故障或 shell 无法执行时无法保证恢复。恢复连接后，在另一终端执行以下命令（也可随时用它主动结束原实验）：

```powershell
.\scripts\capture-stage-isolation.ps1 -Restore -SessionDirectory 'D:\Workspace\unity\viriviri\temp\stage-isolation-实际时间'
```

保留 `session.json` 与 `worker.sh`；恢复命令只操作对应会话，并读回两项原始属性。只有看到 **「已恢复并读回」** 或 `restoration.json` 中 `restored=true` 才算主机确认完成。中断的会话恢复之前不要开始另一轮。设备端采用全局所有者锁；没有恢复成功会阻止重叠实验。

实验结果与恢复结果分开保存：`result` 保留 `TOPOLOGY_UNSTABLE`、`REBIND_EXHAUSTED`、`CONFIRMATION_LOST`、`NEVER_CONFIRMED` 等真实退出原因，恢复成功不会覆盖失败。`MANUAL_STOP` 是手动结束，`DEADLINE` 是达到安全上限；只有曾经获得 READY 才能称为有观察区间，且每个 READY generation 是单独区间。`restore-status` 和 `restoration.json` 只说明恢复状态，不表示实验成功或渲染问题解决。

输出包含 `session.json`（设备/原属性/PID）、`worker.sh`（实际执行脚本）、`preflight.txt`、`worker.log`、`snapshots.log`、`gaps.log`、`intervals.jsonl`（主机观察到的独立 READY/GAP 段）、`watchdog.log`、`stage-operation.log`（设备独立采集；最多 3 个 4MiB 文件，含 `.1`、`.2`）、`last-status.txt`、`restore-readback.txt` 和 `restoration.json`。日志是有限样本，可能包含实验前历史或系统丢失；以设备确认与结束标记划定观察区间。视觉结果仍需你记录，脚本不会判定渲染缺陷已经修复。
