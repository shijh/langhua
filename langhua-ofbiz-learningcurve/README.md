# OFBiz Learning Curve Component

学习曲线（Wright / Crawford）成本估算组件，以 **Apache OFBiz 插件**形式提供。

支持 OFBiz **24.09 及以上**版本，编译目标 **Java 17**（OFBiz 24.09 的最低要求）。

## 功能

- `LearningCurve`：学习曲线值对象（斜率、截距、拟合优度、合格/剔除数据）
- `OFBizLearningCurveHandler`：数据转换（Wright / Crawford）、最小二乘拟合、剔除异常点、估算
- `I_OFBizLearningCurve`：接口定义（`WRIGHT = 1`、`CRAWFORD = 2`）
- `LearningCurveServices`：OFBiz 服务门面（可通过服务引擎调用）
- Web 页面：录入历史数据并**保存估算记录**、记录列表、单条详情（含趋势图）与 CSV 导出
- 口径可切换：`COST`（成本，元）与 `HOURS`（工时，小时），同一套算法
- 估算记录按 **产品型号 / 项目 + 批次** 两个维度存储（`LearningCurveEstimate` / `LearningCurveBatch`）

## 目录结构

```
langhua-ofbiz-learningcurve/
├── ofbiz-component.xml                  # 组件描述符（OFBiz 入口）
├── build.gradle                         # 第三方依赖（gov.nist.math:jama）
├── config/
│   └── LearningCurveErrorUiLabels.xml   # 多语言错误提示
├── data/
│   └── LearningCurveSecurityPermissionSeedData.xml   # 权限与权限组种子数据
├── entitydef/
│   ├── entitymodel.xml                  # 估算记录 / 批次实体
│   └── entitygroup.xml                  # 实体分组（org.apache.ofbiz）
├── servicedef/
│   └── services.xml                     # 服务定义
├── widget/
│   ├── LearningCurveCommonScreens.xml   # 登录/错误页 decorator + 页面布局装饰器 lc-decorator
│   └── LearningCurveScreens.xml         # 估算、记录列表、记录详情页面（内容包在 lc-decorator 里）
├── webapp/learningcurve/                # controller.xml + web.xml + 3 个 FTL + decorator.ftl + css/
├── src/main/java/org/langhua/ofbiz/learningcurve/
├── src/test/java/org/langhua/ofbiz/learningcurve/
└── integration-test/LearningCurveServiceIT.java
```

## 部署

组件源码由 OFBiz 的 Gradle 构建编译，因此必须放在 **`plugins/`** 目录下（`hot-deploy/` 只认已编译的 jar）。

```bash
# 1. 复制到 OFBiz 的 plugins 目录（目录名即组件名）
cp -r langhua-ofbiz-learningcurve $OFBIZ_HOME/plugins/learningcurve

# 2. 编译（会自动从 Maven Central 下载 jama）
cd $OFBIZ_HOME && ./gradlew jar

# 3. 启动
cd $OFBIZ_HOME && ./gradlew ofbiz
```

Windows 下用 `gradlew.bat`，`JAVA_HOME` 指向 JDK 17。

启动后可在 **Web Tools → View Components** 中看到 `learningcurve`（Enabled = Y）。

## 服务

| 服务名 | 说明 |
| --- | --- |
| `learningCurveEstimate` | 估算后续 N 个单位的总成本与单位成本，返回 `estimate`、`unitCost` 及拟合参数 |
| `learningCurveParameters` | 仅返回拟合参数（斜率、截距、R²、合格/剔除行数） |
| `learningCurveSaveEstimate` | 计算并**保存**一条估算记录（含历史批次），返回 `estimateId` |
| `learningCurveListEstimates` | 按产品型号（可选）列出已保存记录，按创建时间倒序 |
| `learningCurveChartData` | 返回详情页所需数据：记录头、批次列表、趋势图坐标点、坐标轴最大值 |

参数说明：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `historyData` | String | 是 | 历史数据，`单位数:成本` 用 `;` 或 `,` 分隔，如 `2:200;3:285;5:450` |
| `newUnitNumber` | Double | 是（estimate） | 需估算的新单位数 |
| `leastSquare` | Double | 否 | 期望拟合优度，默认 `0.95` |
| `methodType` | Integer | 否 | `1` = Wright，`2` = Crawford，默认 `1` |

示例（Web Tools → Run Service → `learningCurveEstimate`）：

```
historyData   = 2:200;3:285;5:450
newUnitNumber = 5
leastSquare   = 0.95
methodType    = 1
```

返回：`estimate ≈ 440.188`（Wright）；`methodType = 2` 时 `estimate ≈ 445.511`（Crawford）。

## Web 页面

部署并启动后访问（需登录，OFBiz 管理员即可）：

```
https://localhost:8443/learningcurve/control/main
```

页面录入历史数据后提交，直接调用 `learningCurveEstimate` 服务并展示估算结果与拟合参数。
访问控制由 `ofbiz-component.xml` 中的 `base-permission="OFBTOOLS"` 决定。

| 路径 | 说明 |
| --- | --- |
| `/learningcurve/control/main` | 估算表单：产品型号、口径（成本/工时）、历史批次、待估算单位数、期望 R²、方法 |
| `POST /learningcurve/control/estimate` | 调用 `learningCurveSaveEstimate` 保存记录，跳转到详情页 |
| `/learningcurve/control/records` | 记录列表，可按产品型号筛选 |
| `/learningcurve/control/recordDetail?estimateId=...` | 详情：估算结果、历史批次、趋势图（SVG）、CSV 下载 |
| `/learningcurve/control/exportCsv?estimateId=...` | 导出该记录为 CSV（`text/csv`） |

### 页面布局：单一装饰器

页面外壳（`<html>` / `<head>` / 样式表 / 顶部导航）只定义一次，和组装 MES 组件（`langhua-ofbiz-mfg`）用同一套做法：

- `widget/LearningCurveCommonScreens.xml` 的 `lc-decorator` 渲染 `webapp/learningcurve/decorator.ftl`；
- `widget/LearningCurveScreens.xml` 的每个屏幕把自己的内容放进 `decorator-section name="body"`；
- 每个屏幕的 `actions` 设 `pageTitle`（页面标题）与 `activeMenu`（当前菜单键：`estimate` / `records`），
  装饰器据此生成 `<title>`、高亮当前菜单项；
- 样式集中在 `webapp/learningcurve/css/lc.css`（`web.xml` 的 `allowedPaths` 加了 `:/css`），
  模板里不再有 `<style>` 块，3 个模板合计减少了约 45 行重复的外壳代码。

加一个菜单项只改 `decorator.ftl`（一行 `navItem`），改配色只改 `lc.css`。

## 数据模型

| 实体 | 说明 |
| --- | --- |
| `LearningCurveEstimate` | 估算记录头：`estimateId`（主键）、`productCode`、`measureType`、`methodType`、`leastSquareTarget`、`newUnitNumber`、`estimateTotal`、`estimateUnit`、`slope`、`intercept`、`leastSquareActual`、`qualifiedRows`、`filteredRows`、`historyData`、四个审计时间戳 |
| `LearningCurveBatch` | 历史批次明细：`estimateId` + `batchSeq`（联合主键）、`lotSize`、`lotValue`；通过 `LC_BATCH_EST` 关联头记录 |

首次启动时 OFBiz 会根据 `entitydef/entitymodel.xml` 自动建表（Derby 下为 `LEARNING_CURVE_ESTIMATE`、`LEARNING_CURVE_BATCH`）。
字段类型使用 OFBiz 24.09 的定义（注意：`id-ne` 等旧类型已不存在，主键用 `id`，整数用 `numeric` → Java `Long`）。

## 中文编码注意

- 每个 `.ftl` 的**第一行**必须是 `<#ftl encoding="UTF-8"/>`。OFBiz 的 FreeMarker 配置没有设置默认编码，
  会退化为 JVM 默认字符集（中文 Windows + JDK 17 下是 GBK），UTF-8 模板会被按 GBK 解码，输出二次编码的乱码。
- 页面响应头为 `text/html;charset=UTF-8`，源文件请统一保存为 UTF-8（无 BOM）。
- CSV 导出带 UTF-8 BOM，便于 Excel 正确识别中文产品型号。

## 权限

`data/LearningCurveSecurityPermissionSeedData.xml` 定义两个权限：

| 权限 | 说明 |
| --- | --- |
| `LEARNINGCURVE_VIEW` | 查看 / 执行估算 |
| `LEARNINGCURVE_ADMIN` | 组件全部操作 |

`loadAll` 时两者授予 `SUPER` 组；如需授权给其它安全组，追加 `SecurityGroupPermission` 记录即可。

## 集成测试

`integration-test/LearningCurveServiceIT.java` 通过 OFBiz 服务引擎（Web Tools 的
`scheduleServiceSync` + `_RUN_SYNC_=Y`）真正调用服务，覆盖 `learningCurveEstimate` 的 Wright、Crawford
两种算法与非法输入，并通过 `learningCurveSaveEstimate` / `learningCurveListEstimates` /
`learningCurveChartData` 验证「保存 → 列表 → 详情图表」整条链路（含数据库写入），全部通过时退出码为 0。

前置条件：OFBiz 已启动、已执行 `loadAll`、组件已部署。

```bash
javac --release 17 -encoding UTF-8 -d build/it-classes integration-test/LearningCurveServiceIT.java
java -cp build/it-classes LearningCurveServiceIT
# 可选参数：-Dofbiz.url=... -Dofbiz.user=... -Dofbiz.password=...
```

## 单元测试

测试使用 JUnit 4（与 OFBiz 自带测试栈一致），位于 `src/test/java`。
OFBiz 的 `test` 任务会自动包含组件测试源码；也可单独运行：

```bash
javac -encoding UTF-8 -cp "ofbiz.jar:jama-1.0.3.jar:junit-4.13.2.jar:hamcrest-core-1.3.jar" \
      -d build/test-classes $(find src/test/java -name '*.java')
java -cp "build/test-classes:src/main/java:ofbiz.jar:jama-1.0.3.jar:junit-4.13.2.jar:hamcrest-core-1.3.jar:config" \
      org.junit.runner.JUnitCore \
      org.langhua.ofbiz.learningcurve.LearningCurveTest \
      org.langhua.ofbiz.learningcurve.OFBizLearningCurveHandlerTest \
      org.langhua.ofbiz.learningcurve.LearningCurveServicesTest
```

## 从 OFBiz 09.04 迁移

本组件最初面向 OFBiz 09.04。迁移到 24.09 时的主要变化：

- `org.ofbiz.base.util.*` → `org.apache.ofbiz.base.util.*`
- 编译目标 Java 1.5 → 17
- `ofbiz-component.xml`：schema 改为 `https://ofbiz.apache.org/dtds/ofbiz-component.xsd`；`<classpath type="jar" location="lib/*"/>` 中的通配符不再被支持
- 依赖 `jama:matrix:1.0.2`（原 langhua 仓库已失效）替换为 Maven Central 上的 `gov.nist.math:jama:1.0.3`（同一个 `Jama` 包/API）
- 旧版 Maven + assembly 打包流程已移至 `legacy/ofbiz-09.04/`

## 开发说明

本次针对 OFBiz 24.09 / Java 17 的代码升级（包名迁移、服务化封装、插件化重构、单元测试补充与编译告警清理），使用 **DeepSeek V4.1 Flash** 辅助开发完成。

界面布局踩坑（与 `langhua-ofbiz-mfg` 共用结论）：

- **装饰器**：`<decorator-screen>` 放在 `widgets` 里，内容写在 `<decorator-section name="body">`，
  装饰器模板用 `sections.render("body")` 输出；屏幕 `actions` 里设的上下文变量（`pageTitle` / `activeMenu`）
  在装饰器里可直接读取。
- **静态资源放行**：`webapp/learningcurve/WEB-INF/web.xml` 的 `ControlFilter` 只放行 `allowedPaths`
  中的前缀，新增 `css/` 就必须加 `:/css`，否则样式表 404。
- **`<screen>` 只能有一个 `<section>`**；模板报错时 OFBiz 仍返回 HTTP 200，所以页面断言要扫
  `FTL stack trace` / `FreeMarker template error`，不能只看状态码。

