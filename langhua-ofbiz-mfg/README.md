# OFBiz Hardware Assembly (MFG) Component

电子产品硬件组装生产流程管理组件，以 **Apache OFBiz 插件**形式提供。

面向"小批量、多品种、SMT 外协 + 自行组装测试"的模式：**产品/BOM/工艺 → 工单 → 工序报工 → SN 履历 → 不良与返修 → 车间看板**。
支持 OFBiz **24.09 及以上**版本，编译目标 **Java 17**。

## 功能

| 模块 | 内容 |
| --- | --- |
| 产品型号 | 型号、SN 前缀、单台标准工时 |
| BOM | CSV 批量导入元件行：`料号,名称,用量,位号,是否关键件` |
| 工艺路线 | 工序模板（组装 / 老化 / 测试 / 包装…），含工位与标准工时 |
| 工单 | 型号 + 计划数量 + 交期 + 客户 + 外协 SMT 厂，编号 `WO-yyMMdd-NNN` |
| SN 管理 | 按工单批量生成 `前缀-yyMMdd-NNNN`，单板全生命周期状态 |
| 工序报工 | 按工单/工序列报工时与合格/不良数量，支持绑定到具体 SN |
| 不良记录 | 不良代码、责任归属（自制/外协/来料）、返修工时 |
| SN 履历 | 一块板：报工记录 + 不良记录 + 物料追溯 |
| 看板 | 在制工单数/数量、今日工时、今日良率、最近报工 |

## 目录结构

```
langhua-ofbiz-mfg/
├── ofbiz-component.xml                  # 组件描述符
├── build.gradle                         # 无第三方依赖（仅用于纳入 Gradle 构建）
├── config/
│   └── MfgUiLabels.xml                  # 状态多语言标签
├── data/
│   └── MfgSecurityPermissionSeedData.xml # 权限与权限组种子数据
├── entitydef/
│   ├── entitymodel.xml                  # 7 个业务实体
│   └── entitygroup.xml
├── servicedef/
│   └── services.xml                     # 12 个服务
├── widget/
│   ├── MfgCommonScreens.xml             # 登录/错误页 decorator
│   └── MfgScreens.xml                   # 5 个页面
├── webapp/mfg/                          # controller.xml + web.xml + 5 个 FTL
├── src/main/java/org/langhua/ofbiz/mfg/
└── src/test/java/org/langhua/ofbiz/mfg/
```

## 部署

组件源码由 OFBiz 的 Gradle 构建编译，必须放在 **`plugins/`** 目录下：

```bash
cp -r langhua-ofbiz-mfg $OFBIZ_HOME/plugins/mfg
cd $OFBIZ_HOME && ./gradlew jar
cd $OFBIZ_HOME && ./gradlew ofbiz
```

启动后访问（需登录，OFBiz 管理员即可）：

```
https://localhost:8443/mfg/control/main
```

首次启动会根据 `entitydef/entitymodel.xml` 自动建表（Derby 下为 `MFG_PRODUCT`、`MFG_BOM_ITEM`、`MFG_ROUTING`、
`MFG_WORK_ORDER`、`MFG_SN`、`MFG_REPORT`、`MFG_DEFECT`、`MFG_MATERIAL_TRACE`）。

## 数据模型

| 实体 | 说明 |
| --- | --- |
| `MfgProduct` | 产品型号：`productCode`（主键）、`snPrefix`、`standardHours`、状态 |
| `MfgBomItem` | BOM 行：`productCode` + `lineSeq`（联合主键）、`componentCode`、`qty`、`designators`（位号）、`isKeyPart` |
| `MfgRouting` | 工序模板：`productCode` + `opSeq`（联合主键）、`opName`、`workCenter`、`stdHours` |
| `MfgWorkOrder` | 工单：`workOrderId`、`productCode`、`plannedQty`、`statusId`、`dueDate`、`customerName`、`smtVendor` |
| `MfgSn` | 单板：`sn`（主键）、`workOrderId`、`productCode`、状态 |
| `MfgReport` | 报工：`reportId`、`workOrderId`、`opSeq`、`sn`（可选）、`operatorName`、`workHours`、`qtyOk`、`qtyNg` |
| `MfgDefect` | 不良：`defectId`、`workOrderId`、`sn`、`opSeq`、`defectCode`、`qty`、`responsibility`、`repairHours` |
| `MfgMaterialTrace` | 物料追溯：工单/SN 与元件批次、料站、位号的绑定（M2 使用） |

## 服务

| 服务名 | 说明 |
| --- | --- |
| `mfgProductSave` / `mfgProductList` / `mfgProductDetail` | 型号维护与查询（含 BOM、工艺路线） |
| `mfgBomImport` | 从 CSV 导入 BOM，`replace=N` 时追加而不清空 |
| `mfgRoutingSave` | 新增/更新一道工序 |
| `mfgWorkOrderCreate` / `mfgWorkOrderList` / `mfgWorkOrderDetail` | 工单维护；详情含工序进度、SN、不良、累计工时 |
| `mfgSnRegister` / `mfgSnDetail` | 批量生成 SN；按 SN 查完整履历 |
| `mfgReportCreate` | 报工（工时 + 合格/不良数，可绑定 SN） |
| `mfgDefectCreate` | 登记不良与返修工时 |
| `mfgDashboard` | 看板数据 |

示例（Web Tools → Run Service → `mfgBomImport`）：

```
productCode = B5X
csv         = R0402-10K,10K 0402 resistor,4,R1-R4,N
              C0402-100N,100nF 0402 capacitor,6,C1-C6,N
              U1-BT5-SOC,Main SoC,1,U1,Y
              BAT-18650,18650 cell,1,BT1,Y
```

## 页面

| 路径 | 说明 |
| --- | --- |
| `/mfg/control/main` | 工单列表 + 新建工单（含产品型号下拉） |
| `/mfg/control/products?productCode=...` | 产品型号、BOM 导入与工艺路线维护 |
| `/mfg/control/workOrder?workOrderId=...` | 工单详情：工序进度、报工、SN 生成、不良登记 |
| `/mfg/control/sn?sn=...` | 单板履历：报工、不良、物料追溯 |
| `/mfg/control/dashboard` | 车间看板 |

访问控制由 `ofbiz-component.xml` 的 `base-permission="OFBTOOLS"` 决定；
`data/MfgSecurityPermissionSeedData.xml` 定义 `MFG_VIEW` / `MFG_ADMIN` 并授予 `SUPER`、`FULLADMIN`。

## 与学习曲线组件的关系

组装报工产生**真实工时**，正是学习曲线需要的输入：

```
工单(WorkOrder) → 工序报工(MfgReport: workHours/qtyOk) → 学习曲线拟合 → 报价/标准工时修正
```

因此 `langhua-ofbiz-learningcurve` 的手工录入 `historyData` 可以逐步被"按批次导出的实际工时"替代。

## 单元测试

```bash
javac --release 17 -encoding UTF-8 -cp "ofbiz.jar:junit-4.13.2.jar:hamcrest-core-1.3.jar" \
      -d build/test-classes src/test/java/org/langhua/ofbiz/mfg/MfgServicesTest.java \
      src/main/java/org/langhua/ofbiz/mfg/MfgServices.java
java -cp "build/test-classes:ofbiz.jar:junit-4.13.2.jar:hamcrest-core-1.3.jar" \
      org.junit.runner.JUnitCore org.langhua.ofbiz.mfg.MfgServicesTest
```

覆盖 BOM CSV 解析（引号、缺列、CRLF、注释行、大小写归一）与 SN / 工单编号格式。

## 开发说明（OFBiz 24.09 踩坑记录）

- **模板编码**：`.ftl` 首行必须是 `<#ftl encoding="UTF-8"/>`，否则 FreeMarker 按 JVM 默认字符集
  （中文 Windows + JDK 17 = GBK）解码，中文变乱码。
- **重定向参数**：`<redirect-parameter>` 只接受 **String** 类型的请求属性，Integer 会抛
  `ClassCastException`，因此 `mfgBomImport.importedCount` 返回 String。
- **字段类型**：24.09 没有 `id-ne`，主键用 `id`；`numeric` 对应 Java `Long`；`indicator` 为 `Y/N`。
- **插件编译**：`plugins/` 下的目录必须先有 `build.gradle`（哪怕没有依赖），否则不会被编译，
  运行期报 `ClassNotFoundException`。
- **`<classpath type="dir" location="config"/>`**：需要在组件里真的存在 `config/` 目录，
  因此放入 `MfgUiLabels.xml`。

## 后续（M2 / M3）

- **M2**：外协上料记录导入（料盘批次 ↔ 料站 ↔ 位号），测试结果导入，良率与返修分析。
- **M3**：物料齐套/欠料预警、出货单、车间电子看板（自动刷新）。
