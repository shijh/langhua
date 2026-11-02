# 第三方组件与许可 — langhua-ofbiz-learningcurve

本组件自身以 **Apache License 2.0** 发布（见同目录 `LICENSE` 与 `NOTICE`，版权归天津龙华 2010-2026）。

## 1. 随本组件分发（进插件 classpath）的第三方代码

| 组件 | 版本 | 许可证 | 用途 | 引入方式 |
| --- | --- | --- | --- | --- |
| JAMA (Java Matrix Package) | 1.0.3 | **Public Domain**（NIST） | 学习曲线的对数拟合 / 最小二乘（`Matrix` / `QRDecomposition`） | `build.gradle` 的 `pluginLibsCompile 'gov.nist.math:jama:1.0.3'` |

JAMA 是 NIST 发布的正式版本，许可证为公共领域（其 POM 的 `<licenses>` 写的就是 `Public Domain`，
指向 Creative Commons Public Domain 声明），**没有署名义务、也没有著佐权约束**，与 Apache-2.0 完全兼容。
它唯一的已知毛病是矩阵奇异时抛 `RuntimeException`（`Matrix is rank deficient`）——
本组件在 `pdrForecast` 里先判“样本全相同”再 catch，见 `AGENTS.md`。

`webapp/learningcurve/` 下的 css / 图片与 `script/` 下的 groovy 脚本都是本项目自己写的，没有前端框架与 CDN 资源。

## 2. 平台在运行时提供（**不**随本组件分发）

| 组件 | 版本 | 许可证 | 用途 |
| --- | --- | --- | --- |
| Apache OFBiz | 24.09.07 | Apache-2.0 | 宿主平台（本组件是它的插件；二维码、库存、型号等能力都由平台提供） |
| Java SE（Amazon Corretto） | 17 | GPLv2 + Classpath Exception | 运行时，由运维安装 |

## 3. 与本组件联动的兄弟组件

`langhua-ofbiz-mfg`（天津龙华自有，Apache-2.0）——通过 `learningCurveMesBatches` / `learningCurveMesPeriods` /
`learningCurveSoftwareQuote` / `learningCurvePdrForecast` 这几个跨组件服务读它的工单、报工与软件项目数据。
同一家公司、同一份许可证，不属于第三方。

## 4. 只在构建 / 测试期使用（不进发行包）

| 组件 | 版本 | 许可证 | 用途 |
| --- | --- | --- | --- |
| JUnit | 4.13.2 | EPL-1.0 | 40 个单元测试（`run-tests.bat`，jar 从 `F:\build-ofbiz\lib` 取） |

## 5. 兼容性小结

| 类别 | 许可证 | 与 Apache-2.0 的兼容性 |
| --- | --- | --- |
| 本组件代码 | Apache-2.0 | — |
| JAMA（随组件分发） | Public Domain | 兼容（无任何限制） |
| 平台 | Apache-2.0 | 兼容 |
| JUnit（测试期） | EPL-1.0 | 不涉及分发 |
| JDK | GPLv2+CE | 由运维提供，本组件不分发 |

## 6. 维护规则

- 新增任何随组件分发的依赖（`pluginLibsCompile` / 复制源码 / 内嵌字体图标）时，必须同时：
  1. 在第 1 节加一行（组件 / 版本 / 许可证 / 用途）；
  2. 确认许可证与 Apache-2.0 发行方式不冲突（GPL / AGPL / SSPL 一律不用）；
  3. 必要时把它的 `LICENSE` 原文放到本目录。
- 升级 JAMA 或换掉它（例如改用平台里已有的矩阵实现）时，同步改第 1 节的版本与 `build.gradle`。
