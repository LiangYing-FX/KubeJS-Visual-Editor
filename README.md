# KubeJS Visual Editor（KubeJS 可视化编辑器）

作者：**良影**
Minecraft **1.21.1 / NeoForge** 游戏内可视化 KubeJS 配方编辑器，界面全中文。

按 `K` 打开：左列模组，中列配方类型，右列参数（每行左边是字段名、右边是输入框），
下方实时预览，`加入队列` 攒多条，`保存全部` 导出到 `kubejs/server_scripts/visual/editor_export.js`。

**状态：编译通过，产物 `build/libs/kubejs-editor-0.1.0.jar`。**

## 界面

| 元素 | 中文 |
|---|---|
| 标题 | KubeJS 可视化编辑器 |
| 列头 | 模组 / 配方 |
| 顶部按钮 | 导入 |
| 底部按钮 | 加入队列、保存全部、删除配方、清空队列 |
| 物品选择 | 选择物品 / 搜索物品 ID |
| 嵌套步骤 | 步骤 (n)、清空、添加步骤、取消 |

## 配方类型（56 种）

| 模组 | 数量 | 覆盖 |
|---|---|---|
| Vanilla | 9 | 有序合成、无序合成、熔炉、高炉、烟熏炉、营火、切石机、锻造台、锻造纹饰 |
| Mekanism | 28 | 原生 13 种 + custom 15 种 |
| Create | 15 | 含序列组装（嵌套） |
| AE2 | 2 | 充能器、压印器 |
| MI | 2 | machine、forge_hammer |

### 有序合成（工作台）

**原版工作台布局**：左边 3×3 网格，箭头，右边输出槽（点击/拖拽填物品，下方是数量输入框）。
图案行由编辑器自动推导——相同物品自动共用字母，空格自动留空。

生成格式**逐字符复刻手写脚本**：

```js
event.shaped(
    Item.of('fu917:infinitycell_reprocessed_fissile_fragment'),
    [
        'ABA',
        'CDC',
        'EFE'
    ],
    {
        A: 'allthecompressed:fluorite_block_9x',
        B: 'mekanism_extras:absolute_induction_cell'
    }
);
```

- 数量为 1 时**不写第二个参数**（`Item.of('id')`），大于 1 才写 `Item.of('id', 16)`
- 键对象**只列图案里实际出现的字母**，按 A→I 排序
- 图案行原样保留空格（`' A '`）
- 多条之间用分号，不依赖 ASI

### 无序合成

同样是 3×3 材料格 + 输出槽，材料存在哪里都不影响顺序；生成的脚本每行一个材料：

```js
event.shapeless(
    Item.of('minecraft:crafting_table'),
    [
        'minecraft:oak_planks',
        'minecraft:oak_planks'
    ]
);
```

字段：输出、数量、图案行 1/2/3、A~I 九格，共 14 个。

**图案行里的空格会被原样保留**（`' A '` 就是中间一格），这一点直接影响配方形状。
早版本会把 TEXT 字段 trim 掉，导致 `' A '` 变 `'A'`、配方整体错位，已修。

字段类型：ITEM / ITEM_LIST / FLUID / NUMBER / TEXT / ENUM / BOOL / NESTED / LIST

## 三块能力

1. **可视化表单**：物品字段点 `...` 弹选择器，列表同时显示**物品名称和 ID**（搜索两者都能匹配），带图标和滚轮翻页
2. **JEI**：`compat/JeiCompat` 带 `@JeiPlugin`，不装 JEI 不加载。注册了 `addGuiScreenHandler` +
   `IGuiProperties`，所以**打开编辑器时 JEI 物品面板会显示出来**（普通自定义 Screen 上 JEI 默认隐藏），
   可以直接把物品拖到输入框或九宫格上
3. **导入脚本**：`导入` 扫描 `kubejs/server_scripts/**/*.js`，反向填表

导入器区分了"图案行"和"物品"：数组参数（`['ABA','CDC']`）只会填 TEXT 字段，
对象/标量里的 id 才会填物品格——否则图案字母会被当成物品。

## 保存不会覆盖旧内容

以前每条配方都写进 `visual/editor_export.js`，第二次保存就把上一次的冲掉。

现在**按模组分文件夹、按配方类型分文件**，一个文件坏了只影响它自己：

```
kubejs/server_scripts/visual/mekanism/crushing.js
kubejs/server_scripts/visual/create/mixing.js
kubejs/server_scripts/visual/minecraft/shaped.js
```

旧版本生成的 `visual/mekanism_crushing.js` 也会被读入合并，不会丢。

**删除配方统一放在 `visual/removals.js`**：选好配方类型、填上输入物品，点 `删除配方` 再保存，
生成 `event.remove({ type: 'mekanism:crushing', input: 'minecraft:coal' })`，全部集中在一个文件里方便整体回滚。

## 已更改面板

左下角 `已更改` 按钮，打开一个总览：

```
已注册物品 (3)
  kubejs:ruby
已删除配方 (2)
  mekanism:crushing  input=minecraft:coal
已更改配方 (按文件)
  mekanism/crushing.js  3 条
  minecraft/shaped.js   5 条
```

只读，扫的是磁盘上的实际文件，不是编辑器内存状态。

保存时先读已有文件、解析回配方、和新条目合并，**内容完全相同的跳过**（状态栏显示"新增 N 条，跳过重复 M 条"）。

## 注册物品

顶部 `注册物品` 按钮：命名空间 / 物品名 / 显示名 + `选择 PNG`。

**注意**：客户端是 headless 的（`java.awt.headless=true`），系统文件对话框打不开。
所以材质改成**目录选择**：把自己的 png 丢进 `kubejs_editor/imports/`，点 `选择 PNG` 就会列出来；
已经存在于 `kubejs/assets` 里的材质也会一起列出。

保存后：

- 图片复制到 `kubejs/assets/<ns>/textures/item/<name>.png`
- 脚本追加到 `kubejs/startup_scripts/editor_items.js`

```js
StartupEvents.registry('item', event => {
    event.create('kubejs:custom_item')
        .displayName('自定义物品')
        .texture('kubejs:item/custom_item')
})
```

**startup_scripts 只在启动时加载，注册物品必须重启游戏**，`/reload` 不生效。

## 扩展接口

四种来源，同 modId 自动合并：

1. 内置 `resources/kubejs_editor/recipes.json`
2. **其他 mod 的 jar**：放一份 `resources/kubejs_editor/recipes.json`，装了自动出现
3. **配置目录**：`config/kubejs_editor/*.json`
4. **Java API**（KubeJS 脚本可调）：

```js
const Api = Java.loadClass('dev.dsh.kjseditor.api.KubeJSEditorApi')
Api.registerMod('thermal', 'Thermal', 0xFF8800, [
  Api.kind('smelter', '感应炉', "event.recipes.thermal.smelter('{out}', '{in}')", [
    Api.field('out', '输出', 'ITEM', 'minecraft:iron_ingot'),
    Api.field('in', '输入', 'ITEM', 'minecraft:iron_ore')
  ])
])
```

JSON 格式：

```json
{ "mods": [{ "id": "thermal", "name": "Thermal", "color": 16746496,
  "kinds": [{ "id": "smelter", "name": "感应炉",
    "template": "event.recipes.thermal.smelter('{out}', '{in}')",
    "fields": [
      { "key": "out", "label": "输出", "type": "ITEM", "def": "minecraft:iron_ingot" },
      { "key": "in", "label": "输入", "type": "ITEM", "def": "minecraft:iron_ore" }
    ]}]}]}
```

## 构建

```bash
./gradlew build
```

1. **JDK 21 HotSpot**（Temurin 可用）
2. 能访问 `maven.neoforged.net`
3. `neoFormRuntime { useEclipseCompiler = true }` 必需：JDK javac 编
   `net/minecraft/world/ticks/package-info.java` 会报 `Line: 1 ...: null`，ECJ 正常

## 结构

```
KubeJSEditorMod.java            主类 + K 键
client/EditorScreen.java        主界面、队列、拖放目标、导入入口
client/ItemPickerScreen.java    物品选择器
client/StepEditorScreen.java    嵌套步骤编辑器
compat/JeiCompat.java           @JeiPlugin
compat/EditorGhostHandler.java  JEI 拖拽 -> 字段
api/KubeJSEditorApi.java        公开扩展接口
data/RecipeLoader.java          四级加载 + 合并
gen/ScriptGen.java              模板替换、NESTED/LIST 展开、remove
gen/ScriptImporter.java         脚本反向解析
```
