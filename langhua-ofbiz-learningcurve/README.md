# OFBiz Learning Curve Component

学习曲线（Wright / Crawford）成本估算组件，以 **Apache OFBiz 插件**形式提供。

支持 OFBiz **24.09 及以上**版本，编译目标 **Java 17**（OFBiz 24.09 的最低要求）。

## 功能

- `LearningCurve`：学习曲线值对象（斜率、截距、拟合优度、合格/剔除数据）
- `OFBizLearningCurveHandler`：数据转换（Wright / Crawford）、最小二乘拟合、剔除异常点、估算
- `I_OFBizLearningCurve`：接口定义（`WRIGHT = 1`、`CRAWFORD = 2`）
- `LearningCurveServices`：OFBiz 服务门面（可通过服务引擎调用）

## 目录结构

```
langhua-ofbiz-learningcurve/
├── ofbiz-component.xml                  # 组件描述符（OFBiz 入口）
├── build.gradle                         # 第三方依赖（gov.nist.math:jama）
├── config/
│   └── LearningCurveErrorUiLabels.xml   # 多语言错误提示
├── servicedef/
│   └── services.xml                     # 服务定义
├── src/main/java/org/langhua/ofbiz/learningcurve/
└── src/test/java/org/langhua/ofbiz/learningcurve/
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
| `learningCurveEstimate` | 估算后续 N 个单位的总成本，返回 `estimate` 及拟合参数 |
| `learningCurveParameters` | 仅返回拟合参数（斜率、截距、R²、合格/剔除行数） |

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

