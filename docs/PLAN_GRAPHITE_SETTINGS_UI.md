# 石墨蓝主题与设置分类改造计划（feat/graphite-settings-ui）

分支：`feat/graphite-settings-ui`，基于 `origin/main`（2b23efc）。
本分支只做实现与验证，**不合并、不推送、不升版本、不改图标**；满意后由用户决定是否合并。

## 目标

在保持现有原生 Kotlin 动态 UI 整体布局不变的前提下：
1. 全应用设置类界面换用石墨蓝（Graphite Blue）配色；
2. 设置页按「悬浮球 / 悬浮工具栏 / 历史画板 / 关于与更新」分类重组；
3. 悬浮画板 overlay 保持透明黑色，透明度与图标一概不动。

## 主题色板（common/FloatInkTheme.kt）

| 角色 | 现值 | 新值 |
|---|---|---|
| background | #0B0F14 | #101214 |
| surface | #121820 | #15181C |
| surfaceRaised | #18212B | #1C2025 |
| surfaceActive | #263A50 | #26384B |
| textPrimary | #F7F9FB | #E8EDF3 |
| textSecondary | #91A0B2 | #A6AFBB |
| textMuted | #718096 | 不变 |
| border | #263241 | #30363E |
| borderStrong | #55FFFFFF | 不变 |
| accent（新增） | — | #8AB4F8 |
| onAccent（新增） | — | 深色（如 #0F1B2D） |
| overlayBar/Panel/Stroke/Selected | 透明黑 | **不变** |

## 交互配色规则

- 主启动按钮：固定 `accent` 背景 + `onAccent` 文字，**不再跟随画笔颜色**；
- 工具选中态：`surfaceActive` 深蓝灰背景 + `accent` 浅蓝描边 + `textPrimary` 高对比文字；
  笔迹预览与颜色圆点仍使用真实工具色，**不全局替换笔迹色**；
- 滑块（SeekBar）、单选（RadioButton）、复选（CheckBox）统一 `accent` 染色；
- 各处硬编码面板/文字色收敛到主题常量（MainActivity、ToolbarLayoutEditorView）。

## 设置页分类结构

1. **悬浮球**：按钮透明度（含实时预览）、自动隐藏、隐藏延迟（关闭自动隐藏时禁用）；
   删除原「显示与自动隐藏 / 自动隐藏」重复标题；
2. **悬浮工具栏**：工具栏大小 + 实时预览 → 工具显隐排序（编辑器）→ 悬浮栏选色范围，按此顺序；
3. **历史画板**：设置页只留入口；点击进入独立内部子页 `history`（列表 / 打开 / 重命名 / 复制 /
   删除 / 导入 / 回收站恢复与清空），子页顶部提供「返回设置」导航；
   修复导入成功与回收站恢复后应返回历史子页（原写法固定 `showPage("settings")`）；
4. **关于与更新**：当前版本信息 + 检查更新按钮。

画笔页（默认绘制 / 当前工具 / 更多工具）功能与顺序不变，颜色语义不变。

## 不做

- overlay 黑色、透明度、图标、悬浮球绘图逻辑；
- 版本号 / 图标资源 / 权限 / README 版本行；
- Paparazzi 等截图设施（仅当资源允许时在**独立 detach worktree** 试渲染，不进入本分支）。

## 步骤（小步 TDD，逐次提交）

1. `docs`：本计划文档；
2. 主题常量石墨蓝 + 新增 accent/onAccent + 主题回归测试；
3. 首页主按钮固定 accent + 工具选中态样式 + 对应测试；
4. 滑块/单选/复选统一 accent + 染色测试；
5. 设置页分类重构 + 历史子页与返回导航 + 导入/恢复导航修复 + 测试；
6. 硬编码颜色收敛到主题常量（MainActivity / ToolbarLayoutEditorView）+ 测试；
7. 文档：README / PROJECT_STATE 以真实源码为准，追加本分支说明（不称已合并）。

## 验证

- 每步运行聚焦测试，最后全量 `./gradlew testDebugUnitTest :app:assembleDebug --rerun-tasks --console=plain`（max-workers=1 已在 gradle.properties）；
- APK 为独立 `.test` 包（applicationIdSuffix），核验 package / versionName / versionCode；
- `git diff --check` 与 `scripts/check-project-gate.sh`；
- 真机 / Paparazzi 渲染属可选验证，缺省如实说明「未真机验证」。