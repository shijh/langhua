# AGENTS.md — langhua-ofbiz-learningcurve

OFBiz **学习曲线**插件：按历史批次（数量 + 总额/工时）拟合 Wright / Crawford 学习曲线，输出斜率、截距、R²、
待投产批次估算与 CSV 导出。

- 仓库根 `F:\git\langhua`，本组件 `langhua-ofbiz-learningcurve`，兄弟组件 `langhua-ofbiz-mfg`（硬件组装 MES）
- 运行环境：OFBiz **24.09.07**（`F:\build-ofbiz\apache-ofbiz-24.09.07`，Derby，HTTPS 8443，admin/ofbiz），
  Java 17 corretto，Gradle 8.6；服务端渲染 FTL + Screen widget，无前端框架
- 两个组件的通用约定（OFBiz 踩坑、装饰器、配色、图标、PowerShell 脚本坑）写在
  **`../langhua-ofbiz-mfg/AGENTS.md`**，动手前先看那一份，这里只记本组件特有的部分

## 命令（脚本在 `C:\Users\shi.jinghai-honor\AppData\Local\Temp\jup`）

| 脚本 | 作用 |
| --- | --- |
| `deploy-lc.bat` | 停 OFBiz → robocopy 到 `plugins/learningcurve` → `gradle jar` → 启动（日志 `F:\build-ofbiz\lc-deploy.txt`） |
| `run-tests.bat` | 单元测试 30 个（需要 jama：`C:\Users\shi.jinghai-honor\.m2\repository\gov\nist\math\jama\1.0.3\jama-1.0.3.jar`） |
| `lc-it.bat` | 集成测试 18 项（日志 `lc-it.txt`） |
| `verify-lc.ps1` | 页面巡检 56 项（日志 `lc-verify.txt`） |

只改 `webapp/` 下的静态文件时，直接 `Copy-Item` 到 `plugins/learningcurve/...` 就生效，不必整包重发。

## 结构

- 屏幕：`main`（新建估算）/ `records`（记录列表）/ `recordDetail`（详情）
- 模板：`webapp/learningcurve/estimation.ftl` / `records.ftl` / `recordDetail.ftl`，只写内容，保留自己的 `<h1>`
- 装饰器：`widget/LearningCurveCommonScreens.xml` 的 `lc-decorator` + `webapp/learningcurve/decorator.ftl`，
  屏幕 actions 设 `pageTitle`（学习曲线估算 / 估算记录 / 估算详情）与 `activeMenu`（estimate / records）
- 样式：`webapp/learningcurve/css/lc.css`（`/css` 与 `/images` 已在 `web.xml` 的 `allowedPaths` 里）
- `recordDetail.ftl` 里除了结果表还有「记录不存在」分支和 CSV/返回链接那一段，别删

## 注意

- **与 MES 的联动**：`learningCurveMesBatches`（`LearningCurveServices.mesBatches`）直接读 MES 的
  `MfgWorkOrder` / `MfgReport` 实体，把「一个工单 = 一个批次」汇总成 `数量:工时` 串；估算页 `?mesProduct=<型号>` 会预填表单并自动切口径到 HOURS。
  规则：按 `dueDate` → `createdStamp` 排序；没合格数或没报工工时的工单跳过；数字用 `Locale.ROOT` 的 `0.###`（小数点必须是 `.`）；
  MES 没部署时返回空结果 + 提示，**不要抛异常**
- 两组件的关系图（mermaid）在 `langhua-ofbiz-mfg/README.md` 的「与学习曲线组件的关系」，
  本组件 README 的「与 MES 联动」里是孪生份；改联动口径时两处一起改
- **`numeric` 字段是 Long，`floating-point` 才是 Double**：`getDouble()` 对 `numeric` 字段会 `ClassCastException`，一律走 `Number`
- **服务返回的中文会被渲染成数字实体**（`&#x8fd9;`），页面断言要改用结构特征（如「没有哪个表头」）或实体形式
- **趋势图是手写 SVG**：`<text>` 默认黑色，深色主题下必须靠 `svg text { fill: var(--fg-muted) }` 才能看见；
  拟合线 `#4daafc`、实测点 `#f14c4c`、坐标轴 `#4b4b4b`
- 配色与图标和 MES 组件一致（VS Code Dark Modern；`logo-{32,64,180}.png` 由
  `../langhua-ofbiz-mfg/tools/make-icons.ps1` 一起生成）
- 数值格式化沿用服务端 `DecimalFormat`，页面只做展示，不要在模板里再算
