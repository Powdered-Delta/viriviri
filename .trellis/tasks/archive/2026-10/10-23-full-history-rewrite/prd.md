# 重构全仓 Git 历史并同步 Trellis 锚点

## Acceptance target

重整远端 `main` 从根提交起的完整历史，并纳入本地 `main` 上尚未推送的 36 个提交。保留当前活动功能分支的独立提交关系，不把未验收的生产实现合入已验收历史。每个有明确开工与验收通过证据的 Trellis 任务，按 `chore(trellis): start` → 一条聚合的实现提交 → `chore(trellis): archive` 排列。删除示例视频的所有历史版本，更新 Trellis 中所有被改写的提交引用，生成完整旧到新 hash 映射。

## 压缩目标

首轮 236 条提交按触及路径分类：103 条（43.6%）为纯文档/Trellis 变更，99 条混合文档与实现，33 条纯实现，1 条为空提交。

首轮整理后的候选主线为 236 条提交。最终 `main` 控制在 150 条以内，计入本次重构的 Trellis 开工、实施记录和最终锚点同步记录。保留有证据支持的 21 个开工节点及 51 个任务归档节点，并如实保留通过、失败、拒绝、延期或待验收状态。9 条仅归档验证 APK 产物的记录并入对应任务记录。同一任务内的中间实现、研究和格式整理提交合并为一条聚合实现提交；不同任务不跨 Trellis 边界合并。

## Repository facts at planning

- Remote currently advertises only `refs/heads/main`, at `af39a78`; no remote tags were listed.
- Remote main has 247 reachable commits, from root `8e8e527`.
- Local `main` has 283 commits and is 36 commits ahead of the remote tip.
- `codex/production-overlay-b` has two commits beyond local main. This branch is local-only at planning time; preserve it as a separate rewritten line and do not create a remote branch.
- The active working tree contains pre-existing modified and untracked user files. The rewrite must run in an isolated mirror and must not stage, reset, or include those working-tree changes.
- `git-filter-repo` is not installed; Git 2.53 and Python 3.13 meet its documented minimum versions.

## Historical boundaries

- Use Trellis task creation/start records as task-start evidence and explicit user acceptance or completed check/verification records as acceptance evidence.
- A passing build or archived task alone is not headset/user acceptance when the PRD says visual acceptance is pending.
- Preserve failed, rejected, deferred, and in-progress outcomes as such; do not synthesize acceptance commits for them.
- Keep the accepted 2026-10-22 Overlay B POC checkpoint distinct from production port acceptance; its rewritten SHA is `2712858d912fb87e5c467ec8af766e849894c472` (original `3a0a3fa`).
- Resolve the conflicting `10-23-fixed-danmaku-line-spacing` task metadata/PRD by preserving its unresolved status unless a stronger explicit acceptance record is found.

## Media paths to purge from history

- `app-meta/src/main/assets/poc/rick.mp4`
- `spatial-video/app/src/main/res/raw/carousel.mp4`
- `spatial-video/app/src/main/res/raw/doggie.mp4`
- `spatial-video/app/src/main/res/raw/mediagiant.mp4`
- `spatial-video/app/src/main/res/raw/salmon.mp4`
- `app/src/main/res/raw/carousel.mp4`
- `app/src/main/res/raw/doggie.mp4`
- `app/src/main/res/raw/mediagiant.mp4`
- `app/src/main/res/raw/salmon.mp4`
- `app/src/debug/res/raw/geometry_experiment_video.mp4`

## Execution and verification

1. Inventory remote refs and all Trellis task start/acceptance evidence; produce a commit-to-task ledger before rewriting.
2. Create a fresh isolated mirror, preserve a recoverable bundle/ref snapshot, and install `git-filter-repo` only into an isolated tool environment.
3. Filter every listed historical path, then curate the complete `main` history and the active local production branch while preserving required topology.
4. Update Trellis task records, journals, and handoffs using the generated old-to-new commit map. Keep a separate Trellis start commit, implementation commit, and acceptance/archive commit for each proven accepted task.
5. Verify the rewritten ref graph, task-to-commit links, final source trees at each retained milestone, and absence of media paths/blob references in all rewritten refs. Compare `range-diff`/tree snapshots against the original history.
6. Update remote `main` only after the rewritten candidate and mapping have been checked; retain the original bundle and ref map for rollback.

## Out of scope

- Do not include pre-existing uncommitted working-tree edits.
- Do not publish the local-only `codex/production-overlay-b` branch as a new remote branch.
- Do not claim visual/product acceptance for tasks whose Trellis evidence says pending, failed, or deferred.


## Rewrite result

- Compressed the 236-commit first-pass main to 148 history commits; the `feat(history)` record is commit 149 and the final Trellis synchronization is planned as commit 150. The rewritten main has no merge commits.
- Preserved 21 start nodes, 51 task archive nodes, 9 validation-artifact records, 6 standalone status/checkpoint records, and the separate geometry POC / production local branch nodes. `milestones.tsv` contains 89 evidence rows.
- `history-map.tsv` maps all 289 original commit IDs to 154 unique rewritten IDs. `branch-ref-map.json` records rewritten branch tips at the 148-commit history base; the final `main` tip adds the two documented maintenance commits.
- Replaced 88 Trellis commit pointers across 31 files; line-by-line diff verification found 0 mismatches, and the updater reported 0 unresolved or ambiguous references. Existing line-ending counts stayed unchanged.
- Verified the 10 listed sample-video paths are absent from history reachable from all rewritten refs. The rewritten main tree at the 148-commit base matches the first-pass tree.
- Verification was limited to Git graph/tree/ref checks and `git diff --check`; no app tests or builds were run.
