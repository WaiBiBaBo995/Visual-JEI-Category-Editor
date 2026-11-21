# Visual JEI Category Editor — Fabric 1.20.1

游戏内可视化 JEI 分类与展示配方编辑器。此分支独立维护 Minecraft 1.20.1，使用 Java 17、Fabric 和 LDLib1，编辑器与材质资源工作流参考 [MBD2 1.20.1](https://github.com/Low-Drag-MC/Multiblocked2/tree/1.20.1)。1.21.1 NeoForge 源码独立维护在 [main 分支](https://github.com/WaiBiBaBo995/Visual-JEI-Category-Editor/tree/main)。

![LDLib1 配置器与纹理资源](docs/screenshots/forge-1.20.1/editor-layout.png)

所有修改仅影响客户端 JEI 展示；实际合成和机器配方仍由原模组或配方脚本决定。服务器无需安装 VJCE。

## 安装

| 组件 | 开发依赖 |
| --- | --- |
| Minecraft | 1.20.1 |
| Java | 17 |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.92.12+1.20.1 |
| LDLib | Fabric 1.20.1 版 1.0.48 或 1.0.52.a |
| JEI | 15.20.0.105，Fabric 1.20.1 |

将模组 JAR、Fabric API、LDLib 和 JEI 放入客户端 `mods/`。

可选联动在两个实例中通过验证。一个实例使用 Create Fabric 6.0.8.1 和 AE2 15.4.12，另一个实例使用 Mekanism Fabric 10.4.16.fabric.1 和 AE2 15.4.12。以上 Create Fabric 与 Mekanism Fabric 版本同装时出现 Porting Lib 的物品接口冲突。

## 使用

1. 进入世界，执行 `/vjce editor`。
2. 从分类库新建分类，或选择现有 JEI 分类。
3. 在“分类数据”中编辑分类、配方、候选原料、数量、概率、催化物和字段。
4. 在“页面布局”中编辑槽位、文字、进度、LDLib1 材质及三维场景。
5. 用 `File → Save` 保存 `.vjce1201` 工程；“保存并应用”写入运行配置，重新进入世界生效。
6. “导出 ZIP”或 `/vjce export` 输出 `exports/vjce-1.20.1-modpack.zip`，解压到目标实例根目录。

分类库、分类数据、场景编辑与弹窗统一使用深色表单；侧栏支持与资源管理器一致的收起动画，视图缩放保留编辑状态，页面布局工作区透明。右侧配置器沿用 MBD2 1.20.1 的 LDLib1 原生样式：深色底、可折叠分组、紧凑同行输入、圆形开关、颜色条和方形纹理预览。底部材质库提供 LDLib1 材质类型、九宫格、组合材质和资源属性面板，可拖入画布。方块资源提供 ID 搜索、预览和合法状态下拉；实体资源提供类型搜索、预览和姿态设置，两者支持高级 NBT。场景支持方块、方块实体、实体、独立动画和世界快照。

| 操作 | 行为 |
| --- | --- |
| 左键 | 仅选择 |
| Shift＋左键拖动 | 移动组件并吸附 |
| Ctrl＋单击 | 进入绿色变换框 |
| 绿色框边缘／八个控制点拖动 | 调整布局宽高；Ctrl＋拖角保持比例 |
| Alt＋绿色框内部拖动 | 旋转；Shift 以 15° 吸附 |
| 变换过程中按 Alt | 暂停位置吸附 |
| 层级树 Ctrl／Shift＋单击 | 切换多选／同级范围选择 |
| Alt＋单击 | 循环选择重叠组件 |
| Ctrl＋G／Ctrl＋Shift＋G | 分组／取消分组 |
| Ctrl＋D／Delete | 复制／删除 |
| Ctrl＋Z／Ctrl＋Shift＋Z | 撤销／重做 |
| 空白左拖／中键拖动／滚轮 | 平移／平移／以鼠标为锚点缩放 |
| Esc | 取消当前手势；再按退出绿色框 |

场景编辑：左键选择、右键环绕、中键平移、滚轮缩放，G／R／S 进行移动／旋转／缩放，X／Y／Z 限定轴，Enter 提交手势，Esc 取消。世界框选保留全屏对话框，使用 MBD2 式自由相机：按住右键转向，配合 W/A/S/D/Q/E 移动、Shift 加速，按住右键滚轮调速。Shift＋左键交替选择两个角点，也可编辑两行 XYZ 坐标。场景半径控制预览范围，右侧参数栏可以隐藏。

原料选择器沿用 JEI 的搜索规则和配置，支持 `@模组`、组合、排除、或条件和短语，输入独立于 JEI 主搜索框。页面布局默认显示可切换的 JEI 单配方参照外框，标出准确原点和宽高；随画布平移缩放，独立于组件变换，不参与导出。

原料类型从当前 JEI 注册列表选择；切换后选定对应类型的原料才会替换数据，关闭选择器保留原值。
未加载模组的旧类型标记为“不可用”并保留数据。槽位角色、绑定属性及其他枚举使用列表选择。

配方数据的三个添加按钮同一行等宽排列，当前配方在两个列表中同步变绿；槽位配置器使用单条外层滚动条。资源栏、页面侧栏和全局配置器收起时，内容随动画补满空间，保留编辑内容和视角；右侧配置器左上角提供常驻箭头。

## 工程与兼容

- 使用与上游 `forge-1.20.1` 同样的 `.vjce1201` 工程格式。配置位于 `config/vjce-1.20.1/categories/`。
- 此版本不导入 1.21.1 的 LDLib2 工程。`main` 分支的 1.21.1 工程保持独立。
- 原生分类保留 JEI 原料检索与焦点关联。仅改布局继续使用原生配方填充；覆盖展示原料后禁用填充。
- 特殊控件无法安全拆解时保留只读原页；第三方配方新增能力取决于对应适配。
- 世界快照最多 4096 个方块、32 个实体、每轴 32 格，仅包含客户端已同步的数据。
- 示例：将 [examples/config/](examples/config/) 复制到实例根目录。

## 开发

检出 `codex/fabric-1.20.1` 分支，安装 JDK 17 作为编译和运行工具链，并用 JDK 21 或更新版本启动 Gradle。在该分支的项目根目录执行下列命令。

```powershell
.\gradlew.bat build
.\gradlew.bat runClient
.\gradlew.bat runClient -PuiTest=all
.\gradlew.bat runClient -PuiTest=picker
.\gradlew.bat runClient -PuiTest=panels
.\gradlew.bat runClient -PuiTest=choices
.\gradlew.bat runPackagedClient --rerun -PuiTest=all
```

产物在 `build/libs/`。客户端验证输出在 `build/ui-test/`，验收失败会让相应的客户端任务失败，详细结果见 `report.json` 的 `passed` 和 `exit-code.txt`。分类审计输出在对应测试实例的 `vjce-category-audit.json`。

[进阶指南](docs/GUIDE.zh-CN.md) · [许可证](LICENSE)

## English

This branch ports VJCE to Minecraft 1.20.1 with Fabric Loader 0.19.5, Fabric API 0.92.12, Java 17, LDLib 1.0.48 or 1.0.52.a, and JEI 15.20.0.105. Its editor and texture resources follow the LDLib1 workflow used by MBD2 1.20.1. The [main branch](https://github.com/WaiBiBaBo995/Visual-JEI-Category-Editor/tree/main) contains the separate 1.21.1 NeoForge project.

Run `/vjce editor` in a world. Create or import a category, edit recipe data and canvas layouts, then save a `.vjce1201` project. Publish the configuration and re-enter the world to apply it. `/vjce export` creates an archive to extract at the destination instance root.

Texture resources use LDLib1 texture types and property editors. Canvas elements support transforms, groups, bindings and opacity. Scene tabs support blocks, block entities, entities and read-only world snapshots. The editor uses the `.vjce1201` project format from the upstream Forge 1.20.1 branch. Changes apply to JEI display only.

Ingredient types come from the current JEI registry. Changing a type opens a picker limited to that type; selecting a result commits the complete replacement, while cancelling preserves the original. Unavailable types retain their original data and display an unavailable label. Slot roles, binding properties and enum parameters use dropdowns.

Check out `codex/fabric-1.20.1` and use the commands above. Run Gradle with JDK 21 or newer and install a JDK 17 toolchain. Run `runClient -PuiTest=choices` for the form selection checks. See the client reports for the exact validation outcome.
