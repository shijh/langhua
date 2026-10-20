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
| SN 履历 | 一块板：报工记录 + 不良记录 + 所用物料批次 |
| 看板 | 在制工单数/数量、今日工时、今日良率、最近报工 |
| **物料批次** | 采购到货台账：内部批次 `LOT-yyMMdd-NN`、供应商批号/DateCode、到货数量；支持 CSV 批量导入 |
| **发料** | 按工单发料（料号 + 批次 + 数量 + 位号），形成“SN → 工单 → 批次”链路 |
| **齐套** | BOM 需求（用量 × 计划数）vs 已发料，自动算出欠料行 |
| **齐套预警** | 按交期列出所有在制工单的欠料明细（欠料行数 / 欠料总量） |
| **外协收发** | 发往 SMT 的套数、回厂板数与不良数、快递单号 |
| **批次追溯** | 反向：批次 → 工单 → SN 数量，用于供应商报批后的召回范围 |
| **FIFO 推定** | 同工单用了多批次时，按发料先后推定各批次覆盖的 SN 区间（仅估算，界面有标注） |
| **返修换件** | 单板级精确记录：SN + 位号/料号 + 原批次 → 新批次 + 原因 + 工时 |

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
`MFG_WORK_ORDER`、`MFG_SN`、`MFG_REPORT`、`MFG_DEFECT`、`MFG_MATERIAL_LOT`、`MFG_MATERIAL_ISSUE`、`MFG_SHIPMENT`、`MFG_REPLACEMENT`）。

## 数据模型

| 实体 | 说明 |
| --- | --- |
| `MfgProduct` | 产品型号：`productCode`（主键）、`snPrefix`、`standardHours`、状态 |
| `MfgBomItem` | BOM 行：`productCode` + `lineSeq`（联合主键）、`componentCode`、`qty`、`designators`（位号）、`isKeyPart` |
| `MfgRouting` | 工序模板：`productCode` + `opSeq`（联合主键）、`opName`、`workCenter`、`stdHours` |
| `MfgWorkOrder` | 工单：`workOrderId`、`productCode`、`plannedQty`、`statusId`、`dueDate`、`customerName`、`smtVendor` |
| `MfgSn` | 单板：`sn`（主键）、`workOrderId`、`productCode`、状态 |
| `MfgReport` | 报工：`reportId`、`workOrderId`、`opSeq`、`sn`（可选）、`operatorName`、`workHours`、`qtyOk`、`qtyNg` || `MfgDefect` | 不良：`defectId`、`workOrderId`、`sn`、`opSeq`、`defectCode`、`qty`、`responsibility`、`repairHours` |
| `MfgMaterialLot` | 物料批次：`lotId`（主键，LOT-yyMMdd-NN）、`componentCode`、`supplierName`、`supplierLot`（DateCode）、`receivedQty`、`receivedDate` |
| `MfgMaterialIssue` | 发料：`issueId`、`workOrderId`、`componentCode`、`lotId`、`qty`、`issuedDate`、可选 `designators` / `snFrom` / `snTo` |
| `MfgShipment` | 外协收发：`shipmentId`、`workOrderId`、`directionId`（`MFG_SHIP_OUT` / `MFG_SHIP_IN`）、`qty`、`defectQty`、`carrier`、`trackingNo` |
| `MfgReplacement` | 返修换件：`replacementId`、`sn`、`componentCode`、`oldLotId` → `newLotId`、`qty`、`reason`、`hours` |

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
| `mfgLotSave` / `mfgLotList` | 物料批次台账（到货、已发、剩余） |
| `mfgIssueSave` | 向工单发料（校验批次属于该料号） |
| `mfgWorkOrderMaterials` | 齐套（BOM 需求 vs 已发）、发料清单、外协收发汇总 |
| `mfgShipmentSave` | 记录发往 SMT（`MFG_SHIP_OUT`）与回厂收货（`MFG_SHIP_IN`） |
| `mfgTraceByLot` | 反向追溯：批次 → 工单 → SN 数（召回范围），并给出 FIFO 推定 SN 区间 |
| `mfgLotImportCsv` | 从 CSV 批量导入到货批次 |
| `mfgShortageList` | 在制工单的欠料清单（按交期排序） |
| `mfgReplacementSave` | 登记单板级换件（原批次 → 新批次） |

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
| `/mfg/control/sn?sn=...` | 单板履历：报工、不良、所用物料批次 |
| `/mfg/control/dashboard` | 车间看板 |
| `/mfg/control/materials` | 物料批次台账、收货登记、CSV 批量导入、按批次反向追溯 |
| `/mfg/control/shortage` | 齐套预警：按交期列出欠料工单与明细 |

访问控制由 `ofbiz-component.xml` 的 `base-permission="OFBTOOLS"` 决定；
`data/MfgSecurityPermissionSeedData.xml` 定义 `MFG_VIEW` / `MFG_ADMIN` 并授予 `SUPER`、`FULLADMIN`。

## 与学习曲线组件的关系

组装报工产生**真实工时**，正是学习曲线需要的输入：

```
工单(WorkOrder) → 工序报工(MfgReport: workHours/qtyOk) → 学习曲线拟合 → 报价/标准工时修正
```

因此 `langhua-ofbiz-learningcurve` 的手工录入 `historyData` 可以逐步被"按批次导出的实际工时"替代。

## 集成测试

`integration-test/MfgServiceIT.java` 通过 OFBiz 服务引擎（Web Tools 的 `scheduleServiceSync` + `_RUN_SYNC_=Y`）
真正调用服务，覆盖 M1 全链路：

```
productSave → bomImport → routingSave → productDetail
           → workOrderCreate → snRegister → reportCreate → defectCreate
           → workOrderDetail → dashboard
           → lotSave → issueSave → shipmentSave → workOrderMaterials → traceByLot
           → shortageList → lotImportCsv → FIFO 推定 → replacementSave
           → 非法报工（workHours = 0）/ 错料发料（批次不属于该料号）
```

共 65 项断言，全部通过时退出码为 0。测试是**可重复执行**的：每次创建/更新产品型号 `ITMFG`、替换其 BOM、
新建一张工单、物料批次，以及相应的报工、不良、发料、收发与换件记录。

前置条件：OFBiz 已启动、已执行 `loadAll`、mfg 组件已部署。

```bash
javac --release 17 -encoding UTF-8 -d build/it-classes integration-test/MfgServiceIT.java
java -cp build/it-classes MfgServiceIT
# 可选参数：-Dofbiz.url=... -Dofbiz.user=... -Dofbiz.password=...
```

## 单元测试

```bash
javac --release 17 -encoding UTF-8 -cp "ofbiz.jar:junit-4.13.2.jar:hamcrest-core-1.3.jar" \
      -d build/test-classes src/test/java/org/langhua/ofbiz/mfg/MfgServicesTest.java \
      src/main/java/org/langhua/ofbiz/mfg/MfgServices.java
java -cp "build/test-classes:ofbiz.jar:junit-4.13.2.jar:hamcrest-core-1.3.jar" \
      org.junit.runner.JUnitCore org.langhua.ofbiz.mfg.MfgServicesTest
```

覆盖 BOM / 批次 CSV 解析（引号、缺列、CRLF、注释行、大小写归一）、FIFO 推定切片，以及 SN / 工单编号格式（共 13 个用例）。

## 追溯精度说明（重要）

SMT 为外协，且对方**不提供上料 / 换料记录**，因此追溯精度分三层：

| 层级 | 精度 | 数据来源 |
| --- | --- | --- |
| 工单级 → 批次集合 | **准确** | 物料全部自采，发料由我方登记（`MfgMaterialIssue`） |
| FIFO 推定 SN 区间 | **估算** | 同工单多批次时，按发料先后与数量比例拆分 SN 序列 |
| 单板换件 | **准确** | 组装 / 返修环节由我方操作，登记 `MfgReplacement` |

所以“召回排查”的推荐做法：先用批次反向追溯圈定工单与推定 SN 区间，再到 SN 页看是否有换件记录确认。

## 开发说明（OFBiz 24.09 踩坑记录）

- **模板编码**：`.ftl` 首行必须是 `<#ftl encoding="UTF-8"/>`，否则 FreeMarker 按 JVM 默认字符集
  （中文 Windows + JDK 17 = GBK）解码，中文变乱码。
- **重定向参数**：`<redirect-parameter>` 只接受 **String** 类型的请求属性，Integer 会抛
  `ClassCastException`，因此 `mfgBomImport.importedCount` 返回 String。
- **字段类型**：24.09 没有 `id-ne`，主键用 `id`；`numeric` 对应 Java `Long`；`indicator` 为 `Y/N`。
- **页面错误提示**：屏幕渲染的 FTL 取不到 `requestAttributes["_ERROR_MESSAGE_"]`，因为
  `ScreenRenderer` 会把它取走并从 request 上移除；模板里要用上下文中的 `errorMessageList`
  （列表时用 `<#list>` 展开）。
- **插件编译**：`plugins/` 下的目录必须先有 `build.gradle`（哪怕没有依赖），否则不会被编译，
  运行期报 `ClassNotFoundException`。
- **`<classpath type="dir" location="config"/>`**：需要在组件里真的存在 `config/` 目录，
  因此放入 `MfgUiLabels.xml`。

## 后续想法

- 出货单与客户 SN 绑定（出厂记录 + 保修起算）。
- 来料检验（IQC）记录：到货批次可直接标记合格/不合格，并影响可发料性。
- 测试数据导入（FCT 结果 CSV → 按 SN 写入参数与判定）。
- 看板自动刷新与钉钉/邮件告警（欠料、良率异常）。
