# AGENTS.md — langhua-ofbiz-mfg

OFBiz **硬件组装生产管理**插件（产品/BOM/工艺 → 工单 → 报工 → SN 履历 → 不良返修 → 看板 → 出货/售后）。
供编码助手在本仓库工作时参考：命令、约定、踩过的坑、关键设计决策。

- 仓库根 `F:\git\langhua`，本组件 `langhua-ofbiz-mfg`，同仓库还有兄弟组件 `langhua-ofbiz-learningcurve`
- 运行环境：OFBiz **24.09.07**（开发 `F:\build-ofbiz\apache-ofbiz-24.09.07`，https 8443；
  生产 `F:\build-ofbiz\ofbiz-prod`，https 8453，两套各一个 Derby 库，可同时跑），
  Java 17 corretto，Gradle 8.6；服务端渲染 FTL + Screen widget，无前端框架

## 命令（脚本都在 `C:\Users\shi.jinghai-honor\AppData\Local\Temp\jup`）

| 脚本 | 作用 |
| --- | --- |
| `deploy-mfg.bat` | **开发实例（8443）**：停 OFBiz → robocopy 到 `plugins/mfg` → `gradle jar` → 启动（日志 `F:\build-ofbiz\mfg-deploy.txt`） |
| `deploy-lc.bat` | 同上，学习曲线插件（日志 `lc-deploy.txt`） |
| `stop-ofbiz.bat` | 只停**开发**实例（按命令行里的 `apache-ofbiz-24.09.07` 匹配）；生产有 `stop-prod.bat` |
| `deploy-prod.bat` | **生产实例（8453）**：停生产 → robocopy 两个插件到 `ofbiz-prod\plugins` → `gradle jar` → 启动（日志 `prod-deploy.txt`） |
| `start-prod.bat` / `stop-prod.bat` | 只启/停生产实例（按命令行里的 `ofbiz-prod` 匹配进程） |
| `mfg-tests.bat` | 单元测试（JUnit 4，离线，依赖 `F:\build-ofbiz\lib` 的 jar）；**只编 `MfgServices.java`** |
| `mfg-tests-all.bat` | 同上，但先编**全部源码**（别的类改坏会当场报） |
| `mfg-it.bat` | 集成测试 `integration-test/MfgServiceIT.java`，走 `/webtools/control/scheduleServiceSync` + `_RUN_SYNC_=Y`（日志 `mfg-it.txt`） |
| `deploy-test.bat` | **测试实例（http 8095）**：停 → robocopy 两个插件 → 把部署副本的 `https="true"` 改成 `false` → `gradle jar` → 启动（日志 `test-deploy.txt`） |
| `start-test.bat` / `stop-test.bat` | 只启/停**测试**实例（按命令行里的 `ofbiz-test` 匹配，admin 端口 10723） |
| `make-test-instance.ps1` / `relax-test-https.ps1` | 一次性从开发实例复制出 `F:\build-ofbiz\ofbiz-test` 并改端口；后者把测试实例里 37 个 controller 的 `https="true"` 改 `false`（每次 deploy-test 都会重跑） |
| `verify-m*.ps1` | 页面巡检（真实登录会话），日志 `F:\build-ofbiz\mfg-m*.txt` |

只改 `webapp/` 下的静态文件（css / 图片 / js）时，直接 `Copy-Item` 到 `plugins/mfg/...` 就能生效；
**`.ftl` 不行**（模板缓存命中后不看 mtime），要重新 `deploy-mfg.bat`。

全模块的数据流图在 `README.md` 的「总体流程」（mermaid）；增删实体或服务时顺手更新那张图。
**算式只写在 README 的「计算公式一览」一节**（齐套欠料/FIFO/保修窗口/售后质量/看板/成本/软件报价/学习曲线，
带常量名）；其它小节与页面文案不要另起一份公式，改口径时同步那一节。
**订单截图识别那一套有自己的设计说明**：`docs/识图设计.md`（演进史、时序图、判定流程图、数字口径、失败话术、
已知局限）。改 `MfgOrderScan` / 提示词 / 那几个纯函数前先看它，改完顺手更新里面“改这段代码时的注意事项”那节。

## 约定与坑（都踩过）

- `.ftl` 首行必须 `<#ftl encoding="UTF-8">`；FreeMarker 的默认值比较要加括号 `(x!'v') == 'y'`
- `<screen>` 下**只能有一个** `<section>`；条件分支用嵌套 `<section>` + `<condition><if-empty/></condition>`
  （`if-not-empty` 不是合法元素，取反写 `<not><if-empty/></not>`）
- `<redirect-parameter>` 只接受 **String** 请求属性（Integer 会 `ClassCastException`）
- `ScreenRenderer` 会把 `_ERROR_MESSAGE_` 取走 → 模板里用上下文里的 `errorMessageList`
- 静态目录要在 `webapp/mfg/WEB-INF/web.xml` 的 `allowedPaths` 放行（现有 `:/js`、`:/css`、`:/images`）
- 渲染出来的中文/`*` 可能是 HTML 数字实体（`&#x5f20;`、`&#x2a;`），页面断言要按实体形式匹配；
  OFBiz 的错误页也返回 HTTP 200，所以巡检必须扫 `FTL stack trace` / `FreeMarker template error`
- 改完描述符用 `(New-Object System.Xml.XmlDocument).Load(<path>)` 校验
  （`[xml](Get-Content -Raw)` 在这里不可靠，出错时会把整个文件打出来）
- **布局**：每个屏幕把内容放进 `<decorator-screen name="mfg-decorator" location="component://mfg/widget/MfgCommonScreens.xml">`
  的 `<decorator-section name="body">`；`webapp/mfg/decorator.ftl` 用 `sections.render("body")` 输出，
  并读取屏幕 actions 里设的 `pageTitle` / `activeMenu`；页面模板只管自己的内容（保留自己的 `<h1>`）
- **FreeMarker 宏的位置参数**：`<@m 'A' x.y ('p=' + x.y)/>` 会报
  `Expected a method or function, but this has evaluated to a string+extended_hash` ——
  widget 传下来的字段是 `StringHtmlWrapperForFtl`，后面跟 `(` 被当成方法调用；先 `<#assign back = ...>` 再传
- **文件上传**：multipart 表单的 Java 事件要先 `UtilHttp.getParameterMap(request)`（或读
  `request.getAttribute("multiPartMap")`）；文件字段是 `ByteBuffer`，另有 `_<字段>_fileName` / `_contentType` / `_size`；
  输出二进制用 `<response name="success" type="none"/>`；事件里要跑服务用 `(LocalDispatcher) request.getAttribute("dispatcher")`，
  用户从 `request.getSession().getAttribute("userLogin")` 取（`auth="true"` 的 request-map 一定有）
- **服务的 OUT 属性必须在 services.xml 里声明**，否则 Java 返回的键会被静默丢弃（`mfgDevProjectDetail` 的 `steps` 曾中招）；
  **屏幕里无条件调用的服务，OUT 还要 `optional="true"`**，否则「没有记录」的分支会整页 500（学习曲线 `/recordDetail` 不带 id 曾中招）
- 屏幕里**无条件调用**的服务，所有 IN 参数要 `optional="true"`，否则不带参数打开列表页会抛
  `ServiceValidationException` → 整页 500；实现里对 null 直接返回空结果
- 集成测试的错误断言看**消息文本**（如 `"Unknown development stage"`），不看 `responseMessage=error`
- `deploy-mfg.bat` 的 robocopy **不删除**目标端多余文件：仓库里删掉的 java 类会留在 `plugins/mfg` 里让 `gradle jar` 失败，
  要手工删一次
- PowerShell 5.1：脚本里有中文会被按 ANSI 读成乱码（新写的 `.ps1` 没有 BOM），巡检脚本尽量只写 ASCII；
  没有 `Invoke-WebRequest -Form`，multipart 要手拼；不要用 `&&`，用 `;`
- 提交前先 `git checkout -- langhua-ofbiz-*/.vscode/tasks.json`（跑任务会让它变脏）；提交信息用文件 + `git commit -F`
- **FreeMarker 没有 `ge` / `le`**，比较只有 `gt` / `gte` / `lt` / `lte`；写成 `ge` 是解析期 `ParseException`，
  而且报的是**后一行的列号**，很难看出是哪一行（曾把 products.ftl:229 报成条件里的 `ge`）
- 负数零会渲染成 `-0.00`：`${(-x)?string("0.00")}` 在 x=0 时就是它，用 `${((x)?abs)?string("0.00")}`
- **改 `.ftl` 后直接 `Copy-Item` 不生效**：OFBiz 的模板缓存命中后不会因为 mtime 变化重载（只有上次加载**失败**时才会重试），
  所以模板改动必须重新 `deploy-mfg.bat`（重启）；Java 改动本来就要重启
- `EntityQuery...queryOne()` 在可能多行时抛 `Passed List had more than one value`（一个型号可以有多个开发项目），
  要排序取一条就用 `queryFirst()`
- **每个 webapp 一个会话**：`/mfg` 与 `/learningcurve`（还有 `/webtools`）的 `auth="true"` **不共用登录**，
  同一个 PS 脚本要分别 POST `/mfg/control/login` 与 `/learningcurve/control/login`；集成测试是登录 `/webtools` 跑服务，
  所以**页面/CSV 下载这类 GET 断言只能放巡检脚本**（IT 里 GET `/mfg/control/*` 会 401）
- **新增一个页面要改三处**：`widget/MfgScreens.xml` 的 `<screen>` + `controller.xml` 的 `<request-map>` **和 `<view-map>`**
  （只加 request-map 会报 `No definition found for view with name [...]`）；导航项在 `decorator.ftl`
- **OFBiz 的错误页也是 HTTP 200**：巡检的 `NoTemplateError` 必须同时扫 `ERROR MESSAGE`、`FTL stack trace`、
  `FreeMarker template error`、`Error rendering`，否则错误页会被当成通过
- **FreeMarker 默认值运算符 `!` 的优先级低于比较**：`<#if (x)!0 gt 0>` 会解析成 `x!(0 gt 0)` 而报
  `Expected a boolean, but this has evaluated to a number+extended_hash`；要写 `<#if ((x)!0) gt 0>`
- **`${x?url}` 要求同一模板里有 `<#setting url_escaping_charset="UTF-8">`**，否则渲染抛
  `To do URL encoding, the framework that encloses FreeMarker must specify the "output_encoding" setting ...`；
  `workOrderDetail.ftl` 没有这个 setting，所以在那里用 `?url` 会让整页报错，而且 POST 后的 302 目标页报错时
  巡检看到的是「200 + template error」；批次号这类安全字符直接拼，不要 `?url`

## 测试与巡检

**要点死按钮的地方用测试实例（http 8095）**：`deploy-test.bat` 部署一份把 `https` 关掉的副本，
浏览器（含编辑器的内置浏览器 / Playwright）用 `admin` / `ofbiz` 登录后可以直接点、可以截图，
不碰自签证书。`verify-m*.ps1` 仍旧打开发实例的 8443。

| 脚本 | 覆盖 | 断言数 |
| --- | --- | --- |
| `MfgServicesTest` | 解析/规则/格式化（CSV、SN、FIFO、窗口、编号、附件类型、来料检验规则、成本取整与毛利、CSV 单元格转义、软件报价公式与耗时率中位数、趋势期间标签与良率/单台工时、库存流水符号与不合格批次可用量、预留对可发量的影响、成品批次命名与单位成本分摊、订单行欠货与待排产量、订单行文本解析、库位类型、外协费计价与单台分摊、问题清单排序与校验、账号名规范与姓名拼接、BOM 格式识别与 Fritzing 解析、识图回答拆多张单与长回答不爆栈…） | 108 |
| `MfgServiceIT` | 全链路服务调用（含 BOM 版本、Fritzing BOM 导入、三条开发线、ECO、附件、来料检验、成本、告警、软件报价与台账、成本细化与外协费明细、步骤问题清单、良率/工时趋势、库存流水与盘点、工单预留/发料释放、完工入库与出货扣减、销售订单两级预留与核销、库位与移库、用户账号） | 398 |
| `verify-m11.ps1` | 列表页搜索/过滤/翻页/编码 | 55 |
| `verify-m12.ps1` | 装饰器布局（1 个 html、1 条导航、1 个高亮、样式表、无内联 style、无 FTL 报错） | 102 |
| `verify-m13.ps1` | 产品开发 + BOM 版本 + 三条线路 | 49 |
| `verify-m28b.ps1` | 通用附件（上传/列表/原图/缩略图/错误码/删除/PDF，覆盖产品/开发项目/工单/售后单/出货单） | 59 |
| `verify-m29b.ps1` | 网站图标与导航标志（favicon、brand 图、四个尺寸、全站渲染，两个 webapp 各登一次） | 43 |
| `verify-m31.ps1` | 来料检验：四个状态、建批次 → 判不合格 → 发料被拒 → 让步接收 → 放行 | 21 |
| `verify-m32.ps1` | 成本核算：产品页成本块（材料/工时/合计/目标成本/毛利）、标准工时来源标记、批量重算、BOM 单价与金额列、无单价告警、工单成本块（标准 vs 实际，发料后实际材料跟批次单价走） | 39 |
| `verify-m33.ps1` | 看板告警与 CSV 导出（告警区、三个导出链接、`text/csv` + 下载头、表头列数、行内容） | 30 |
| `verify-m34.ps1` | 软件项目：估算试算、保存与编号、台账计划 vs 实际、实测耗时率、超期进告警 | 30 |
| `verify-m35.ps1` | 报表与成本细化：报表页（卡片/趋势表/日周月切换/未知口径回落/喂进学习曲线的链接）、三份 CSV（良率/工时/采购运费，BOM + 列数 + 合计行）、采购运费块（卡片/表/合计行/周标签）、产品页按工序分摊、工单页工序成本与外协费、开发页问题清单登记→关闭→重开 | 57 |
| `verify-m36.ps1` | 库存：结存页与卡片、流水页与余额、报废入账与超量被拒、盘点单号与差异、安全库存低于阈值标红与建议采购、告警 CSV、工单预留按钮与两次 POST、成品链路（完工入库 → 成品批次 → 出货扣减 → 重复发货被拒，出货单号从成品流水里读，别按「当天最大号」猜）、**变动原因（8 项 OFBiz 原因、按原因筛、方向冲突被拒、只给原因时类型自动跟随、盘点差异也能带原因）** | 84 |
| `verify-m37.ps1` | 销售订单：列表与新建表单、详情（行明细/三个按钮/出货表单）、先锁在库、排产生工单、关单转 CLOSED | 26 |
| `verify-m38.ps1` | 库位与移库：库位页与两个表单、建库位、到货指定库位、按库位看结存、移库两边余额、流水同单号、超量被拒 | 24 |
| `verify-m39.ps1` | 外协费按单分摊：工单页三个表单、加一条按台明细、金额与对账单号、删除后消失、非法计价方式被拒 | 16 |
| `verify-m40.ps1` | 用户账号页：渲染与高亮、建账号（行上的 `data-account` / `data-enabled` / `data-default-password` / `data-groups`）、非法名校验、大小写重名不新增行、重置密码、停用/启用、不能停用自己 | 24 |
| `verify-m41.ps1` | 角色权限：用 `MFG_WAREHOUSE` 账号登进来看菜单/成本/BOM 单价是否隐藏、BOM 导入与销售订单写入被拒、账号页只读；管理员仍看到全部 | 22 |
| `verify-m42.ps1` | Fritzing BOM 导入：格式下拉、粘贴导出后 9 种料合并、`Amount` 给用量、`Label` 给位号、无物料号用名称、本系统格式仍可强制导入、料号超长被拒 | 18 |
| `verify-m43.ps1` | 改密码与退出：新建账号登录、导航有 logout、错当前密码/新旧相同/两次不一致/太短都被拒、改成功落到登录页且会话关闭、旧密码失效新密码可用、logout 结束会话 | 15 |
| `verify-m44.ps1` | BOM 导入选文件（Step 54）：表单是 multipart、有 `file` 字段、上传 Fritzing 文件后合并 9 种料、**文件优先于文本框**、GBK 文件能解码、料号超长被拒、空提交有提示、20 位型号编码也能导入（日志 `mfg-m43b.txt`） | 31 |
| `verify-m45.ps1` | BOM 行就地编辑（Step 56/57）：标题带版本号、每行有自己的表单（`form="bomLine<n>"` 的 qty/designators/isKeyPart/unitPrice）、保存后数量与金额重算、手加一行、删除一行、用量 0 被拒、`#bomBlock` 与 JSON 出口（保存/拒绝/删除三种回答）（日志 `mfg-m45.txt`） | 34 |
| `verify-m46.ps1` | 采购到货单（Step 58）：汇总表与 `data-allocation`、建单与运费、批次下拉、挂批次后的行与金额、合计含运费、同一批次不能挂两张单、负运费被拒、行可以移出、**整单优惠（红包 / 满减）四项断言**、列表翻页、行内展开与就地改行、删行、料号重命名（日志 `mfg-m46.txt`） | 63 |
| `verify-m47.ps1` | 照淘宝订单一步入库（Step 60/65/110）：订单表单、两行明细 → 建单+入库、订单号已存、货款 4.41、便宜料单价 0.0291、重复提交不重复入库、补运费累加、不满四列/数量 0 被拒、**长料号被缩短且全称留在名称里**、识图按钮与 `/mfg/control/orderImage` 降级回答、**淘宝导出的 xlsx 导入块（`#excelForm` / multipart / `#excelNoteTable` / 空提交回 JSON）**（日志 `mfg-m47.txt`；识图那几项要求实例上没配识图，跑完 m48 会清掉） | 42 |
| `verify-m48.ps1` | 系统设置页（Step 61）：识图配置块与状态、密钥不回显、接口必须 http(s)、空密钥保留旧的、保存后状态变 DATABASE、`clear=Y` 恢复未配置、没配/配了两种 scan 回答（日志 `mfg-m48.txt`） | 26 |

## 设计决策

- **三个实例：开发 8443 / 生产 8453 / 测试 8095（只有 http）**：`verify-m*.ps1` 用 `Invoke-WebRequest` +
  `ServerCertificateValidationCallback` 能过自签证书，但**浏览器自动化不行**，所以另起一个不用 TLS 的测试实例。
  端口：开发 http 8080 + https 8443（admin 10523）、生产 http 8090 + https 8453（admin 10623）、
  测试 http **8095** + https 8444（admin 10723），AJP 也错开（测试 8139/8140）。
  **光把 connector 改成 http 不够**：`ConfigXMLReader.RequestMap` 的 `securityHttps` 字段默认 `true`，
  而 `no.http=Y` 时只有 `http.request-map.list` 里的 uri 才重新赋值，所以 `https="true"` 的请求一律被
  `RequestHandler` 302 到 https（Location 里的端口来自 `WebSite.httpsPort`，看着像“莫名其妙回到 8443”）。
  测试实例要**两处一起改**：`framework/webapp/config/url.properties` 的 `no.http=N` + `port.http=8095`，
  再把**所有** controller（含 `framework/common/webcommon/WEB-INF/common-controller.xml`，`logout` 在那里）的
  `https="true"` 改成 `false` —— 仓库里保持 `https="true"` 不变，只有部署副本被改，所以 `deploy-test.bat`
  每次 robocopy 之后都要重跑 `relax-test-https.ps1`
- **浏览器能点了（Step 81）**：`http://localhost:8095/mfg/control/{main,products,delivery,salesOrders}` 用
  `admin`/`ofbiz` 登录后可以真的点开「3 ▸」、改销售订单行的数量；销售订单那条链路的三个风险点
  （`form="soLine_…"` 绑定的输入框会被 `new FormData(form)` 带上、fetch 成功后只换 `#orderListBlock`、
  被拒时提示变红且不动块）都是这样在真浏览器里确认的

- **系统设置放页面，不靠 -D（Step 61）**：`/mfg/control/settings`（导航在「用户账号」后面，`canManageAccounts` 才看得到）
  用 `mfgSettingsView` / `mfgSettingsSave` 读写 `SystemProperty`（`systemResourceId = "mfg"`，propertyId 就是原来的
  `org.langhua.mfg.vision.*`）；`MfgServices.setting()` **先读库、再退回 `System.getProperty`**，所以老启动脚本照旧有效。
  密钥只存不回显（表单 `value=""`，留空=不修改），`clear=Y` 删掉三行；管理员判定复用 `accountAdminProblem`。
  页面靠 `data-configured` / `data-source`（DATABASE / JVM）两个属性给巡检断言；m48 结束时必须 `clear=Y`，
  否则 m47 的“未配置时的回答”会挂。**m48 会清空识图配置（包括密钥，密钥不回显也读不回来），
  所以跑完 suite 要重新填**；正式入口是页面上的「测试连接」（`/mfg/control/visionTest`，拿 1 像素图调一次）。
  地址不写全很常见：`MfgServices.normalizeVisionEndpoint` 在**保存时和调用时**各补一次
  （只写域名 → `/v1/chat/completions`，已到 `/v4` 这类版本号 → `/chat/completions`），
  `describeVisionError` 把 404/401/400/429/5xx 翻成人话（404 = 地址没写全）
- **识图返回空明细的四个原因（Step 63/64 都修了）**：① 地址只写域名（靠上面那个 normalize 补）；
  ② **thinking 模型把 token 全用在思考上**——请求现在带 `max_tokens: 4000`；
  ③ 明细可能在 `reasoning_content` 而不是 `content`——`orderFieldsFrom` 两处都读；
  ④ **模型把换行转义了两次（`\\n`）**——旧的链式 replace 会先把它变成「反斜杠 + 真换行」，
  而 `jsonStringField` 的 `\\.` 匹配不到「反斜杠+换行」→ 整条 `lines` 变 null（**真凶就是这条**，
  真实 DeepSeek 回答里就是 `2.91\\n1/4W10K`）。现在用一遍扫的 `jsonUnescape`（支持 `\uXXXX`）代替链式 replace；
  并且**在原始回复上抓 `content`**（先 unescape 再抓会截断字符串）。
  空明细时的提示会**引用模型的原话**（`MfgServices.visionReplyExcerpt`，160 字），
  否则「这不是订单」「模型看不见图」在页面上长得一模一样
- **别拿本系统页面的截图当订单截图**：用户两次都把带报错的自家页面截下来发进来，模型只能回空
  **`deepseek-flash` 是支持看图的**（官方文档 Vision ✓），`deepseek-v4-pro` 不支持——别把"DeepSeek 没有视觉模型"写进文档；
  设置页的例子列表与 m48 的断言按 `deepseek-flash` / `deepseek-v4-pro` 写；
  **只实测过 DeepSeek**，所以页面上**只给这一条路**（接口地址 + `deepseek-flash` 默认就填好，用户只需粘密钥），
  别的服务商（qwen-vl-max / glm-4v-plus / gpt-4o-mini / ollama）**一律不在页面上列**——列一串没验证的候选，
  用户试错又修不好，比没这个功能更糟（Step 88，用户明确要求）
- **「测试连接」测的是页面上的值，成功就顺手保存（Step 88）**：`MfgOrderScan.test` 先读表单的
  `visionEndpoint` / `visionModel` / `visionKey`，空着才回退到库里存的；成功后 `runSync("mfgSettingsSave")`，
  页面把三张卡片就地翻成“已配置 / 已设置 / 本页保存”（`visionStateCard` / `visionKeyCard` / `visionSourceCard`），
  不用刷新。以前只读库，于是“填完不保存就点测试”会回“还没配置”，用户完全不知道错在哪
- **识图未配置的回答带 `"code":"UNCONFIGURED"`**：`answer(...)` 多了个 code 参数，消息里**不出现系统属性名**
  （以前写 `org.langhua.mfg.vision.endpoint / ...`，给工位上的人看毫无意义）；巡检按 code 断言，不匹配中文
- **单价只能存 2 位小数，所以按“实付金额”记（Step 60）**：`currency-amount` 在 Derby 上是 `NUMERIC(18,2)`，
  淘宝一百个电容实付 2.91 的单价是 0.0291，存进去变 0.03（一百个多算 9 分）。所以 `MfgMaterialLot.receivedAmount`
  存实付总额（2 位、精确），单价由 `lotUnitCost()` = 金额 ÷ 数量 算（无金额时回退 `unitPrice`），
  工单实际材料成本用它；到货单行同样 `unitPriceExact = goodsAmount ÷ qty` 给页面显示 4 位。要改列精度得手工 ALTER，先不动
- **订单日期不是到货日（Step 68）**：`MfgOrderScan` 把截图上那个日期叫 `orderDate`（旧回答里的 `date` 也读），
  **不填到货日**（`dateField` 留空 = 今天），提示里写“截图上的 X 是下单日，到货日请自己选”
- **同一订单再次导入会先问（Step 77）**：`receiptFromOrder` 对**同料号**不再新增行（以前只跳过完全相同的
  qty+金额，改过数字就会加成两行）；新增 IN `overwrite`：`Y` 时走 `mergeOrderLine` ——
  改单行 qty/unitPrice/goodsAmount **以及那个批次的 receivedQty/receivedAmount/unitPrice 和它的 RECEIPT 流水**
  （再 `recalcLotStock`），所以库存与可追溯性不会跟单据脱节；不是 `Y` 就只 skip。
  页面在提交前先打 `/mfg/control/orderPeek?supplierOrderNo=…`（服务 `mfgReceiptLinesOfOrder`）拿“这单已有哪些料”，
  有重叠就 `confirm()`：确定 → 填 `#overwriteField=Y` 再 `form.submit()`；取消 → 什么都不改
- **明细里就地改数量/单价（Step 76）**：子表每行一个空 `<form data-receipt-line id="receiptLine_<单号>_<行号>">`
  （放在 `</table>` 之后，不能放在表里），单元格的 qty/unitPrice 输入框用 `form="…"` 绑定；
  `submit` 拦截后 POST `/mfg/control/receiptLineJson`（`MfgReceiptEvents.saveLineJson` → `mfgPurchaseReceiptLineSave`），
  成功后 **重新 fetch 当前 URL**、只把 `#receiptListBlock` 的 innerHTML 换掉（所以合计/页脚都跟着变），
  再把之前展开的单重新展开；提示放在块外的 `#listHint`（块被替换也不会丢）。
  **脚本必须放在 `#receiptListBlock` 外面**：innerHTML 插入的 `<script>` 不会执行，toggle 会整个失灵（踩过）
- **到货单列表可直接展开明细（Step 75）**：一个到货单通常就一两个商品，所以列表里把「行数」做成可点的 `1 ▸`，
  点开就在下面展开一张子表（行号/批次/料号/名称/数量/单价/货款），再点收起。
  服务 `mfgPurchaseReceiptList` 多返回 `linesByReceipt`（map：receiptId → 行，**只带当前页的行**，OUT 已声明）；
  模板里**要从 `rl.linesByReceipt` 取**（屏幕把服务结果放在 `receiptList` 下，写成 `linesByReceipt[...]` 会永远取不到，踩过）。
  子表的行用 `data-receipt-line-seq`（不能用 `data-line-seq`，否则详情页的“两行明细”断言会被列表的子表干扰）。
  翻页对断言的提醒：列表按 `-receiptDate, -createdStamp` 排，**新建的单不一定在第一页**，巡检要按 `search=<单号>` 找
- **料号可以重命名（Step 74）**：`mfgPartCodeRename`（IN `oldCode`/`newCode`，OUT 各类行数 + `renamed`/`search`）
  一次改全：`MfgMaterialLot` / `MfgMaterialIssue` / `MfgPurchaseReceiptLine` / `MfgBomItem` /
  `MfgBomVersionItem` / `MfgReplacement` / `MfgStockSetting`（后者是主键：新号不存在就复制一行再删旧的，
  已存在就直接删旧的，等于合并）。页面在「物料批次」页的「料号重命名」框（POST `partCodeRename` →
  redirect `materials?search=<新号>&renamed=<行数>`，模板用 `${(parameters.renamed)!}` 显示成功行——
  OFBiz 的 FTL `parameters` 可以直接取值，不必 `?first`）。没有找到旧料号时返回错误而不是默默不改
- **料号里的空格要留着（Step 73）**：`fittingComponentCode` 只压缩连续空白、**不删词间空格**——
  `5.5V1.5F C型 21*7.5` 以前会被挤成 `5.5V1.5FC217.5`（F / C型 / 21*7.5 连在一起看不出），
  现在原样保留；`codeFromName` 也直接拿名称（含中文与空格）当料号，只有 >100 字符才截前 ~93 位 + hash。
  空名称给 `P-000000`
- **订单上没有料号时（Step 72）**：模型常给一个**空的首列**（`,名称,数量,金额`）。`parseOrderArticleLines`
  不再把这种行丢掉，而是用 `codeFromName` 从名称里造一个：取名称里的可打印 ASCII
  （`5.5V1.5F C型 21*7.5` → `5.5V1.5FC217.5`），实在没有 ASCII 就给 `P-` + 6 位 hash（同名同结果）；
  提示词也加了一句“没印料号就填型号文字，不要留空”。**`checkOrderTotals` 在货款为 0 时直接返回**
  （否则会拿实付去当运费，踩过：空料号 → 行被丢 → 运费变成 9.28）
- **运费要跟实付对账（Step 69）**：模型现在还要给 `paidTotal`（实付款），`MfgServices.checkOrderTotals`
  用「货款合计 + 运费 = 实付」校一遍：对不上就把运费改成 `实付 − 货款` 并在 message 里说清楚，
  没给运费也这么补；实付缺失或明细比实付还大就不碰（让人看）。提示词里把“含运费: ￥2.00 = 2.00，
  免运费/包邮才写 0”和“行金额 = 实付 − 运费”写明白了——之前模型把 2.00 报成 4.00 就是这句话含混
- **截图可以 Ctrl+V / 拖进来（Step 67）**：`receipts.ftl` 的 `#orderBlock` 脚本在 `document` 上挂 `paste`，
  从 `clipboardData.items` 里取 `image/*` 的 `getAsFile()`，再用 `new DataTransfer()` 把它塞回
  `input.files`（老浏览器不行就只用内部变量 `chosen`，按钮发 `chosen || file.files[0]`）；
  **带了 `text/plain` 的粘贴不动**（人是在往明细框里贴文字），只有纯图才拦；`drop`/`dragover` 同理，
  拖动时给 `#orderBlock.drop` 加虚线框（样式在 `mfg.css`）。
  页面脚本可以用 node 离线验：`check-page-js.js`（语法 + 引用的 id 都存在）与 `paste-test.js`
  （假 DOM 跑一遍：粘贴 → input.files → 按钮 → fetch）。**自签证书下 Playwright 打不开 8443**，所以只能这样测
- **照订单一步入库（Step 60）**：`mfgReceiptFromOrder`（IN `lines`：`料号,名称,数量,实付金额`，逗号或 Tab）
  一次完成建批次 + RECEIPT 流水 + 到货单行；**订单号做幂等键**（同号复用单据、重复行跳过、后来补的运费累加到单上）；
  页面上是到货单页的 `#orderBlock`，提示“数量按个填、金额填实付”
- **料号列加宽到 100 字符（Step 66）**：`componentCode` 从 `id`（VARCHAR(20)）改成 `name`（VARCHAR(100)），
  涉及 `MfgBomItem` / `MfgBomVersionItem` / `MfgMaterialLot` / `MfgMaterialIssue` / `MfgReplacement` /
  `MfgPurchaseReceiptLine` / `MfgStockSetting`（这张的 `componentCode` 是主键，Derby 也允许 ALTER 加宽）。
  `MAX_COMPONENT_CODE` 改成 100，`fittingComponentCode` 只对 >100 的保底缩短（规则不变，带 hash）。
  **旧库要手工加宽**：`tools/part-code-100.sql`，先停实例，再用 ij
  （`java -cp "derby.jar;derbyshared.jar;derbytools.jar" org.apache.derby.tools.ij 脚本`，jar 在
  `~/.gradle/caches/modules-2/files-2.1/org.apache.derby/*/10.16.1.1/`）；
  **`connect` 必须带 `user=ofbiz`**——表在 `OFBIZ` schema 下，ij 默认连 `APP`，否则报
  “ALTER TABLE cannot be performed on 'MFG_…' because it does not exist”（实际是 schema 不对）。
  物理表名是 `MFG_MATERIAL_LOT` 这种下划线大写、列名是 `COMPONENT_CODE`。
  巡检 m47 改成断言“长料号被接受且**不被缩短**”
- **截图识别（Step 60）**：`MfgOrderScan.scan`（`/mfg/control/orderImage`，multipart `image`）把图发给兼容 OpenAI 的
  视觉接口，配置项是系统属性 `org.langhua.mfg.vision.endpoint` / `.model` / `.key`；没配时返回 `{"ok":false,"message":…}`
  提示手工填（**绝不报错页**），识别结果只**填入可编辑表单**、人不提交不写库。回答解析在 `MfgServices.orderFieldsFrom`
  （纯函数，单测覆盖裸 JSON / ```json 围栏 / chat envelope / `reasoning_content` 四种形状），所以不联网也能测。
  请求体是 OpenAI 的 content 数组（`text` + `image_url` 的 data URL）+ `max_tokens: 4000`；
  端到端可以不用真密钥验证：起一个 `TcpListener` 假接口回一段固定回答（脚本 `mock-vision.ps1`）
- **重复导入不能留下孤儿批次（Step 82）**：`mfgReceiptFromOrder` 原来是 `use-transaction="false"`，
  于是「先建批次 + 流水、再插到货单行」中间一旦失败（比如同一张单被点了两次，两个请求都读到同一个
  `lineSeq`，第二个撞 `MfgPurchaseReceiptLine` 的主键），**批次与到货流水已经提交，行却没写进去**：
  库存多出一份、到货单上看不见，页面上还甩出一大段 Derby SQL。
  现在三件事一起改：① 服务改成 `use-transaction="true"`，失败整单回滚，不留半截数据；
  ② 插行前用 `freeReceiptLineSeq()` 找**真正空着的**行号（并发下也不会撞主键）；
  ③ 失败走 `receiptImportFailure(e)`，把 duplicate / truncation 翻成人话（“这单正在入库（可能重复点了一次）…”），
  其余只留第一句，SQL 用 `Debug.logError` 进日志。单测覆盖这三种形态；
  线上留过的孤儿批次只能按 Step 78 的办法冲掉（`mfgInventoryMove` + `MFG_MOVE_SCRAP`，备注写清原因）
- **运费按月看（Step 59）**：`mfgPurchaseFreightTrend`（IN `fromDate`/`toDate`/`groupBy`）复用 `periodKey` 与 `receiptSummary`，
  一个期间一行：单据数 / 货款 / 运费 / 采购支出 / 运费占比，合计行由同一套 `round2` 算；报表页 `#freightCards` +
  `#freightTable`（行上 `data-period`）+ `#freightTotalRow`，CSV 走事件 `MfgExportEvents.freightCsv`
  （`/mfg/control/freightCsv`，七列 + 合计行 + UTF-8 BOM）。**报表页加了卡片会把巡检里“六个卡片”的计数弄挂**，
  所以 m35 改成只数 `id="freightCards"` 之前的部分；PowerShell 的 `String.Split('…')` 是按**字符**切的，要用 `[regex]::Split`
- **采购运费是整单的期间费用，不摊进料价（Step 58）**：淘宝一个单混着买电阻和芯片，运费按数量摊会把小件单价抬高几倍，
  按金额摊又让贵料背运费，都会失真。所以新增 `MfgPurchaseReceipt`（`PR-yyMMdd-NN`，供应商/店铺订单号/到货日/`freightAmount`/`allocationId`）
  + `MfgPurchaseReceiptLine`（`receiptId` + `lineSeq`，`lotId` 上有 **unique 索引**，一个批次只能挂一张单）：
  批次单价、BOM 单价、工单实际材料成本全部保持**实付价**，运费在单上合计并给出“运费占货款”比例。
  `allocationId` 先只接受 `MFG_ALLOC_NONE`，`BY_AMOUNT` / `BY_QTY` 两个值先占位，以后打开不用改表。
  页面 `/mfg/control/receipts`（导航在「物料批次」后面），服务 `mfgPurchaseReceiptSave` / `LineSave` / `List` / `Detail`，
  列表与详情共用纯函数 `receiptSummary`（`lineCount` / `goodsTotal` / `freightTotal` / `spendTotal` / `freightShare`，货款为 0 不算除法）
- **列表翻页统一用 `pager.ftl` 宏（Step 71）**：`webapp/mfg/pager.ftl` 的 `<@pg.pager action query page pageCount total unit pageSize/>`
  出「第 x / y 页，共 n 条」+ 首页/上一页/页码窗口（当前页 ±2，带 …）/下一页/末页 + 每页 20/50/100，
  当前页高亮、不可用的按钮变灰 span。`query` 是**已经 `?url` 过的筛选串**（不带 page/pageSize，可空），
  调用方用 `<#assign pagerQuery = ''><#if (x.search)?has_content><#assign pagerQuery = 'search=' + x.search?url></#if>` 拼，
  空筛选不会产生 `?search=&page=2`。靠 `data-page` / `data-pages` / `data-page-link` / `data-page-size` 给巡检断言。
  **Step 79 起 14 个列表页全部换成了它**（products / materials / main / claims / dev / eco / vendors / buyers /
  delivery / shortage / stock / stockMoves / salesOrders / receipts），文件里不再有手写的翻页段；
  换法：`<#import "pager.ftl" as pg>` + 把筛选拼成 `'search=' + ((lr.search)!'')?url` 这样的**字符串加法**
  （单引号字面量里不插值，所以不能直接塞 `${…}`），并**必须给默认值加括号** `((x)!'')?url`，
  否则 `!` 的优先级低于 `+`，整句会解析成 `(a + b)!c`；
  `mfgPurchaseReceiptList` 补了 `pageSize` IN，否则换每页条数被静默丢弃
- **一张截图里好几张单（Step 86）**：识图的 prompt 现在要求**永远**回答
  `{\"orders\":[{orderNo,shop,orderDate,paidTotal,freight,refunded,lines}]}`（列表页 `x2` 是数量、`合计` 是实付）；
  `MfgServices.orderListFrom(reply)` 拆成 `List<Map>`（`orderAnswerText` 先看 chat envelope 的 `content` /
  `reasoning_content`，`objectTexts` + `matchingBrace` 手写扫花括号，字符串里的 `{}` 不计数），
  `orderFieldsFrom` 就是「取第一条」，老形状照旧能用。1 张单走原来的填表流程；>1 张返回
  `count` + `orders[]`（带每条 `note`），页面渲染 `#scanOrders` 核对表（`data-scan-order`），
  点「导入这些单」POST `/mfg/control/receiptFromOrdersJson`（`MfgReceiptEvents.ordersJson`，
  参数 `count` + `o<i>.orderNo/shop/freight/lines/refunded`），服务里**每张单一次 `runSync(..., -1, true)`**，
  一张失败不影响别的；结果里 `new` = `importedCount + updatedCount`，所以**重复导同一张截图**时
  `imported=0`、页面显示“之前已入过（这次没新增）”，不会重复建批次。退款单在 `scan` 里就登记进
  `MfgRefundedOrder`，页面上过滤掉不发（服务端还有 `refunded=Y` 与黑名单两道）。
  **真实淘宝「我的订单」截图实测通过（2026-10-24，3 单含 1 单退款）**：订单号/店铺/日期/`x5` 数量/
  `合计` 与 `实付款`、`含运费` 都读对，退款那单 `refunded=Y` 且金额按 实付 − 运费 算；
  料号列优先取标题里的型号（`ATECC608B-SSHDA-T`），没有型号就用规格行/商品名
- **没有“单条识别”这条支路了（Step 90）**：用户要求「单条就是多条的特例」，所以 `scan` 不再有  `orders.size()==1` 的分支——**总是**返回 `orders[]`（加 `count`），页面也**总是**渲染 `#scanOrders` 核对表
  （1 张单就是 1 行，按钮文字自动变「导入这张单」）。原来那条“填进下面的表单 + 红色退款横幅”的支路连同
  `fill()` / `showRefund()` / `#refundBlock` / `wireOrderForm` 里的退款守卫一起删了，`#refundField` 只留隐藏字段（恒为 N）；
  退款信息现在只在核对表里（行上标红「已退款，不入库」），**整张截图都退款时把「导入」按钮藏起来**。
  下单日只显示不落字段：日期格里带 `data-scan-date`（m47 靠它断言“下单日没被当成到货日”）；
  核对表也挪到了识别按钮下面（原来在手填表单下面）
- **到货单行要能删（Step 94）**：用户问“行号 20 那行要删，去哪儿删”—— 服务 `mfgPurchaseReceiptLineSave`
  一直支持 `delete=Y`，只是页面没有按钮。现在明细里每行右边是「保存 + 删除」两个按钮，各绑一个表外的
  隐藏表单（`receiptLineDel_<单号>_<行号>`，`style` 不许内联所以用 `class="secondary"`），
  `onclick="return confirm(...)"` 里写明“只从单据上删掉，批次和库存不动”；
  **删行不动库存**（单据是“买了什么”的凭证，要冲库存走「库存 → 流水」的报废），删完表头的行数/货款/合计自动重算
- **回答被截断要说清楚（Step 94）**：截图一行十几张单/商品名很长时，thinking 模型会把 8000 输出额度用在思考上，
  答案是半截 JSON（`orderCount` 读得到、`orders` 解析出 0 条）。`scanFailure` 现在先看
  `orderCountIn(reply) > 0` 就给“回答被截断，一次只框一两张单”的话术；重试那次**换用 `SHORT_PROMPT`**
  （短提示词 → 想得少、答得快），第一次仍用完整 `PROMPT`
- **红包 / 满减是整单优惠，不是“金额不平”（Step 95）**：用户截图的单子 货款 9.00 + 运费 2.00 = 应收 11.00，  用了淘宝红包 0.27，实付 10.73，旧逻辑报“金额对不上”。现在 `MfgPurchaseReceipt.discountAmount` 记整单优惠，
  口径 **实付 = 货款 + 运费 − 优惠**，`receiptSummary(lines, freight, discount)` 多返回 `discountTotal`，
  `spendTotal` 扣掉优惠；到货单页（建单框 / 汇总 / 列表 / 详情页「优惠」框）、核对表（「优惠」列）、
  报表的采购支出都用这一份。**模型侧**：`PROMPT` 多问 `discount`（优惠 / 红包 / 券），`fieldsOfOneOrder` 带出来；
  读不到时 `checkOrderTotals` 用差额反推（`0 < 货款+运费−实付 < 货款` → 记成优惠），
  `totalsDisagree` 也只在**差额大于货款**时才判“没读全”，否则红包会白白触发一次重试。
  旧库要跑一次 `tools/receipt-discount.sql`（`apply-receipt-discount.ps1` 会停实例 → ij → 起），
  **列是 `NOT NULL DEFAULT 0`：新建单据时必须显式写一次 `discountAmount`**（`purchaseReceiptSave` 曾漏掉，
  Derby 直接 `Error while inserting`，页面只看到一个 200 的建单页跑回自己——m46 就是这样挂了 24 项）
- **库存变动原因借 OFBiz 的 `IID_REASON`（Step 96）**：用户嫌“手工登一笔”只有 报废/退料/补入库/调整 四项、
  问 OFBiz 里那些原因（丢失之类）能不能用。OFBiz 的清单在
  `applications/datamodel/data/seed/ProductSeedData.xml` 的枚举类型 **`IID_REASON`**（8 项：
  `VAR_LOST` / `VAR_STOLEN` / `VAR_FOUND` / `VAR_DAMAGED` / `VAR_SAMPLE` / `VAR_INTEGR` /
  `VAR_MISSHIP_ORDERED` / `VAR_MISSHIP_SHIPPED`），**id 照抄**（导出/对账同一套编号），只在代码里给中文名。
  `MfgInventoryDetail.reasonEnumId`（id 类型，可空）；`movementTypeOfReason()` 给每个原因一个默认类型
  （丢失/被盗/损坏/样品/多发 → 报废，生产领用 → 发料，找到/供方少发 → 补入库），
  `movementReasonProblem(type, reason, qty)` 只校验**方向**（调整看 qty 的正负），方向冲突就报中文错误——
  **不要**改成校验“类型必须完全等于原因暗示的类型”，报废与调整都是出库，硬绑会挡人。
  页面：表单里原因在下拉里、`<option data-type>` 带目标类型，脚本在 change 时把类型拨过去；
  流水表多一列 `data-reason="…"`，过滤器也按原因筛（`mfgInventoryLedger` 的 IN/OUT 都加了 `reasonEnumId`，
  屏幕 `<field-map>` 别漏，否则筛选被静默丢弃）。
  **盘点差异也能登记原因**：`mfgStockCount` 的 IN `reasonEnumId` 写进 `MfgStockCount.varianceReasonId`
  （这一列原来是写死 `MFG_MOVE_COUNT` 的占位，现在放真原因）并带到 `MFG_MOVE_COUNT` 流水上；
  `MOVE_COUNT` 自带正负号，所以 `movementReasonProblem(MOVE_COUNT, reason, variance)` 能直接判方向——
  盘亏配「找到」会被拒；盘点页每行一个原因下拉 + 备注框，历史表多一列 `data-reason`。
  **中文名只在 `webapp/mfg/reason.ftl` 的 `label()` 一份**（`stockMoves.ftl` 与 `stockCount.ftl` 都 `#import "reason.ftl" as rs`），
  以后加原因改这一个文件 + `MOVE_REASONS` + 两处下拉即可。
  旧库跑 `tools/inventory-reason.sql`（`apply-inventory-reason.ps1`：停实例 → ij → 起）——
  **列可空**，所以不像 Step 95 那样必须显式写值。
- **核对表可展开，重新导入是“覆盖”不是“跳过”（Step 93）**：用户发现第一次少读了一件商品，
  改好再导时那件“已经存在”的料号被跳过、数字没更新（到货单上留下 0.0000 单价的行）。
  现在：① `showOrders` 在明细列出「2 ▸」（`data-toggle-lines="scan-<单号>"` + 紧跟一行
  `tr[data-lines="…"]` 里的内表，**复用 `expand.js` 和 `tr[data-lines]` 的折叠 CSS**，
  不要另写一套开关）；② 批量事件 `ordersJson` 传 `overwrite=Y`，所以同料号走 `mergeOrderLine`
  （改行 + 批次 + RECEIPT 流水 + `recalcLotStock`），新料号新增；
  ③ `receiptFromOrder` 在 `overwrite=Y` 时**把运费设成这次的值**（不再是累加，否则上一次写错的运费永远改不掉）；
  ④ 结果消息分开写“新增 N 行 / 覆盖 M 行 / 无变化 K 行”，并且**点名这次没出现的旧行**
  （`leftOverCount` / `leftOverLines`）：不默默删（删会动物流），让人自己判断
- **识图失败就自动重试一次（Step 89）**：真人用下来十次里有一两次识别不出来，重试就好。原因查到两个：① thinking 模型把  `max_tokens` 全用在 `reasoning_content` 上、`content` 是空串（日志里 `carried no lines` 那条 13 KB 的回答就是这么来的）——
  **4000 不够，已改成 8000**；② 模型读漏了列表里的一张单却正常返回。所以 `scan` 现在**最多发 3 次**（`ATTEMPTS`，
  中间 `pause()` 700 ms，第 2、3 次用 `SHORT_PROMPT`），条件是「IOException」或「解析出 0 张单」或「模型说看到 N 张、只读出 M 张」——
  prompt 里因此多了 `"orderCount":"N"`（`MfgServices.orderCountIn`，缺失时按 0 = 不校验）；
  都不行就 `scanFailure(reply, transportProblem)` 分三种情形给话术（连不上 / 输出被截断 / 读不出订单），
  答案带 `"code":"NO_ORDERS"`；页面上失败后按钮文字变成「再试一次」。
  **实测**：同一张 3 单截图连跑 11 次，11 次都读出 3 张单（改之前大约十次里会有一两次失败）
- **淘宝电阻/电容是整包卖的，数量要乘包装量（Step 97）**：用户指着订单详情页说“数量识别有误”——
  9 行里 8 行回 1，其实每行是 `30K（一件100只）` 的 ×1，二极管是 `(50只)` 的 ×4。
  灰色规格行里的包装量有四种写法：`一件100只` / `（50只）` / `100只装` / `1件=100个`，
  **数量 = ×N × 每件只数**，而**金额仍是那一行实付的钱**（1.20，不是 1.20×100），单价于是 0.0120
  （`receivedAmount` 存 1.20、`lotUnitCost` = 金额 ÷ 数量，见 Step 57 的老口径）。
  `PROMPT`/`SHORT_PROMPT` 都写明了这条，并且“包装量不进料号”（`30K（一件100只）;1%精度;0603贴片电阻` 只取 `30K`）——
  不写这条，模型会把整行规格当料号，同一颗料每次买都成新料号。
  **兜底**：`MfgServices.ignoredThePackSize(reply, linesOfOrders(orders))` —— 回答里出现过包装量、
  而每一行数量都是 1，就当作没读好再发一次（钱仍然对得上，人最难发现，比“张数对不上”隐蔽得多）。
  实测同一张 9 行截图 8 次：数量都是 `[100×8, 200]`、金额合计 15.90；导入后到货单 9 行、
  货款/合计 15.90、单价 0.0120 / 0.0300 / 0.0150
- **答案用“一行一个对象”，不是一行一串制表符（Step 98）**：用户发来一张两张单的截图（第二张两件相似电池），
  旧格式 `"lines":"code\tname\tquantity\tamount"` 要模型自己转义 `\t`/`\n`，它经常出错——
  实测出现过一行金额变成 `3.24;402530`（第二件商品被第一件吞掉）、整张单丢掉、某行 code 空着。
  现在 `PROMPT`/`SHORT_PROMPT` 要的是 **`"items":[{"code","name","qty","amount"},…]`**（一件商品一个对象），
  `MfgServices.itemsIn()` 把它读回内部的行字符串；**旧的 `lines` 仍然兼容**（mock 图、老回答照旧能用），
  `matchingBrace` 拆成 `matchingCloser(text, open, opening, closing)` 以便同时支持 `[]`。
  `itemsIn` 遇到 `code` 空着就用 `codeFromName(name)` 补上（和导入那条路同一个函数，核对表里看到的就是会存下去的）。
  另外两处兜底：`anyUnreadable()`（数量 ≤ 0 或金额不是数字 → 当作没读好，重发）、
  `answerScore(orders, seen)` + `scan` 里**保留分数最高的那一份**（第 3 次可能比第 1 次差，不能“最后一份说了算”）：
  分数 = 单数×1000 + 行数×10 + 金额能对上的单数 + （单数等于模型自报张数时 +500）。
  实测同一张两单截图连跑 4 次：4 次都是 2 张单、3 行、金额 77.99 / 3.24+2.94+运费 2.50 = 8.68
- **长图切段识别 + 多平台口径（Step 99）**：用户反馈“一张 5 张单的图往往要识别 2、3 次”。  现在 `MfgOrderScan.slicesOf(image)` 把高于 `SLICE_HEIGHT`（900 px）的图按 150 px 重叠切成几段，
  每段单独问一次（`readPicture(picture, …, partial=true)`，**段里允许金额对不上**，因为一张单可能被切成两半），
  再用 `MfgServices.mergeOrders()` 按订单号合并：同号是一张单，行取并集（重叠区同一行只留一次，键是 `code|qty|amount`），
  字段谁读到用谁的。分数保留逻辑（`answerScore`）从 `scan` 挪进 `readPicture`，每段都一样。
  `ImageIO.read` 解不开的图**原样整张发**，不要因为切图失败让扫描挂掉。
  提示词改成平台中立（淘宝 / 天猫 / 京东 / 拼多多 / 抖音），答案多一个 `platform` 字段，
  核对表里显示成「平台 · 店铺」并带 `data-platform` 属性；**只有淘宝/天猫实测过**，别的平台等真截图再说。
- **模型会把“答案格式”当成答案（Step 91）**：一次真机跑出来 8 个「订单」，7 个的 lines 是模板
  `code\tname\tquantity\tamount`，真正那张单只读了一张。所以：`fieldsOfOneOrder` **要求 orderNo 非空**
  （它本来就是导入去重的键，空号的单子也不该进库）+ `isLineTemplate()` 挡模板行；
  `echoesTemplate()` 命中的回答当作“没读好”再发一次——**只看 `content`（`orderAnswerText`），不看 reasoning**，
  否则 thinking 模型每次都在思考里引用格式，扫描会从 20 秒变成 50 秒（踩过）。
  另外 **prompt 别再往长里写**：之前那条让模型反复权衡（店铺名带不带「天猫」、1.97 与 2.22 哪个是单价），
  最后把输出额度全用在思考上、`content` 空。现在是“当决策写在提示里 + 别权衡、别描述画面”
- **一张单两件商品只读出一件（Step 92）**：真机截图里第二张单有两件商品（3.20×2 与 4.00×2，实付 14.40），
  模型有时只答一件，于是「货款 6.40、运费 0」对不上实付 14.40 —— 而旧的 `checkOrderTotals` 会**把差额
  8.00 改写成运费**，等于把漏掉的商品藏起来。现在：① 只要任一单「货款 + 运费 ≠ 实付」就当作没读全，
  `scan` 自动再发一次（`MfgServices.totalsDisagree`）；② `checkOrderTotals` **只补空缺的运费，不再改写**
  对不上的运费，而是返回一句“实付 X，但明细 Y + 运费 Z 只有 W（差了 D），可能漏了一件商品，请核对”；
  ③ 核对表多了「实付」列，对不上的行标红「实付与明细对不上，请核对」。
  **实测**：这张两单截图连跑 4 次，第二单每次都是 2 行且货款 14.40 对得上；一单两件的行金额 = 行单价 × 数量
  （prompt 里写明了），单件的行金额 = 实付 − 运费
- **`"thinking":{"type":"disabled"}` 是识别质量的分水岭（Step 100）**：用户发来一张 9 张单的淘宝列表图，
  旧逻辑跑出来只有 1~4 张单、耗时 90~300 秒——**日志里看得很清楚：模型把输出额度全用在
  `reasoning_content` 上，`content` 是空串**（`finish_reason:"stop"`，不是 `length`），于是每次都得重发。
  现在 `askVision` 的请求体里带 `"thinking":{"type":"disabled"}`（读页面不是推理任务）：**同一张图 4 秒、9 张单全中**。
  兼容性是「试一次就好」：端点若不认这个字段回 400，`plainAnswerWorks` 置 false 并**去掉它重发一次**，
  之后本进程不再试。**别把这个开关删掉**，它就是 300 秒 → 4 秒的原因。
- **切段后要「收拾」重叠与汇总行（Step 100）**：`MfgServices.tidyOrder(order)` 在金额核对之前做两件事：
  ① 重叠区同一行只留一次——键是 `rowKey(code)|qty|amount`，`rowKey` 会去掉 `[交易快照]` 这类后缀
  （否则同一条会变两行、货款翻倍）；② 明细末尾多出的“汇总行”（金额等于实付或等于其余行合计）会被丢掉——
  **规则是“能对上的那一份赢”**：删掉某一行后 `其余行 + 运费 − 优惠` 能和实付对上（容差 0.06，页面自己会四舍五入）
  就删它。另外 `checkOrderTotals` 里**不许编造优惠**：货款 + 运费已经等于实付时，模型报的 `discount` 一律清零
  （它常把划线价当优惠，实测出现过 4.80）。
  仍然读不准的两处（页面只印 单价、模型把单价当行金额，例如那张单的 0.06/0.11/0.06）会留下红色提示“请核对”，
  **不要为了让提示消失而放宽判据**。
- **`verify-m48` 会清空识图配置**（密钥读不回来），所以它现在**开头先查 `data-configured`，是 Y 就 SKIP 跳过**，
  除非显式 `-Force`；跑这个巡检请指向测试实例，别在配好密钥的实例上跑
- **长得一样的两行可能是两件货（Step 103）**：一块面包板卖六种颜色，页面印六行，料号/数量/金额全一样，
  只有灰色规格行里写 `白色 / 蓝色 / 绿色`。所以**行身份 = 料号 + 数量 + 金额，但不能拿它去重**：
  - 拼段（`joinRows`）只把两段的行接起来，**不去重**；`tidyOrder` 里的 `withoutRepeatedRows` 只在
    “行的金额合计 > 实付”时一次删掉一个“重复最多的行”的最后一份，删到钱对上为止，对不上就原样还回去；
    没有实付金额时**一行都不删**（分不出复制品，宁可让人核对）；
  - 入库（`receiptFromOrder`）里同一个料号可以有好几行：单据的每一行**只能被认领一次**（`claimOne` +
    `Set<String> claimed`，行 id 是 `receiptId#lineSeq`），所以六行建六个批次、重跑同一张单是六次覆盖而不是十二行。
    改这附近时别再写成 `Map<code, line>`（踩过：六行合成一行、后面五次被 skip）
- **切段只在超长页做（Step 104/105/106）**：`SLICE_HEIGHT` 从 700 改成 **2400** —— 普通截图（四五个单）
  **一次读完**，因为淘宝页"块内空行"和"两块之间空行"都是 32~41 像素（实测），切点很容易落在块中间：
  一旦落在块中间，模型就把下段的商品挂到下一张单上——一张 2860×1576 的 4 单截图因此**整体错位一格、第 4 单丢失**。
  真要切时（> 2400）切点滑到空白行（`blankRowNear`，同样空的几行里取离理想切点最近的一行，
  `colourChanges` 每 4 个像素采样一次）。配套五道：
  - `repeatsAnotherOrder`（两张单明细与实付完全相同 → 重发，并在核对表里标“可能抄错了”）；
  - **一段里可能没印订单号**（一张单很高，单号只在第一段）：`orderListFrom(reply, partial=true)` 保留这些行，
    `giveTheRowsAnOrder` 把它们挂到上面最近的单号上（不保留就是“六行面包板只剩 2 行”）；
  - 行身份用 `bareCode`（`黄色（5个）` 与 `黄色` 算同一件）；`withoutRepeatedRows` 删重复行时
    **保留离实付最近的那个状态**，剩下的差距交给 `readingThatAddsUp`（模型凭空多出的行就是这一步丢的）；
  - `roundingSlack`（页面单价四舍五入到分，0.095 印成 0.10，两件 1.20 对 1.19 → 差一两分算对得上，
    `checkOrderTotals` 与 `totalsDisagree` 用同一个额度，别改回 0.005）；模型把划线价当优惠时
    （`0.09` 旁边就是 `0.10 ×2`），`tidyOrder` 会展试“不带优惠”的读法，对上就把优惠清零。
  - **数量口径**：`一件100只` / `（50只）` / 标题里的 `（5个）` / 标题末尾裸写的 `5只` 都算包装量
    （`IC测试板 5只` ×3 → 15）；但**套装 / 组合 / 套餐 算一件**（`实验板（4块组合套装）` ×1 → 1，用户明确要求的）。
  - **金额校验是双向的**：`totalsDisagree` 在“明细 + 运费 − 优惠”与实付**多或少**超过 `roundingSlack` 时都算没读好
    （Step 106 之前只看“少了”，所以“两行各 2.06”那种多读的回答不会重发）。
- **登录后回到原来那一页：用 OFBiz 自带的 `<after-login>` 钩子（Step 107）**：`controller.xml` 顶部的
  `<after-login><event path="…MfgLoginEvents" invoke="afterLogin"/></after-login>` 在登录成功后只跑一次，
  它把“要回到哪一页”定下来（存会话属性 `mfgLoginReturnTo`）；真正跳转是起始页 `main` 的 `backAfterLogin`
  （302）——**不要在 `<after-login>` 里 `sendRedirect`**，那时登录视图还没渲染。
  页面那边：`session.js` 的 `jsonOrLogin` 先请求 `rememberReturn?to=<本页地址>`（它存地址再 302 到登录页），
  因为 401 的是 AJAX 入口，地址栏里那个页面地址只有页面自己知道；`_PREVIOUS_REQUEST_` 只是兜底。
  **两个坑**：① `<after-login>` 的 event 必须是 `(HttpServletRequest, HttpServletResponse)`，
  签名不对会 `NoSuchMethodException`，而框架“忽略 after-login 的错误”，日志里才看得到；
  ② 会话被重启清掉时 `_LAST_VIEW_NAME_` / `_PREVIOUS_REQUEST_` 一并没了，所以页面自己存的那一份才是主路径。
  另外给页面 request-map 开了 `save-last-view="true"`（框架自带的“记住上一个视图”，写入 `_LAST_VIEW_NAME_`）。
- **`jsonStringField` 不能用正则（Step 86 踩坑）**：原来是
  `"name"\s*:\s*"((?:[^"\\]|\\.)*)"`，遇到 thinking 模型那种几千字符的 `content` 会
  **`StackOverflowError` in `Pattern$Loop.match`**（`OrderImage` 请求 23 秒后 200 + **空 body**，
  页面上就是“识别请求失败 / 识别不到”，日志里才有 `Request orderImage caused an error ...
  java.lang.StackOverflowError`）。现在是手写扫描（`indexOf` 找键 → 跳过空白 → `:` → 引号 →
  `closingQuote` 按转义找收尾引号），O(n)、无递归；单测 `readsALongAnswerWithoutBlowingTheStack` 覆盖 4000 段转义
- **`mfg-tests-all.bat`**：`mfg-tests.bat` 只编 `MfgServices.java`，所以别的类改坏（少 `import`、private 可见性）
  要等 `deploy-mfg.bat` 里的 `gradle jar` 才炸，已经两次因此部署失败；新脚本用 `@sources.txt` 编**全部源码**再跑测试。
  **javac 的 @argfile 把 `\` 当转义**，所以文件清单要用正斜杠（PowerShell 里 `-replace '\\','/'`）
- **PowerShell 变量名不区分大小写**：画 mock 订单图的脚本里 `$SHOP`/`$shop`、`$PAID`/`$paid` 是同一个变量，
  于是 `$SHOP + '：' + $shop` 画出来是“ShopA Digital： ShopA Digital”；全局标签用 `$L_SHOP` 这种前缀
- **退款/关闭的单列表（Step 85）**：新实体 `MfgRefundedOrder`（`orderNo` 主键 / `shop` / `note` / `recordedDate`），
  服务 `mfgRefundedOrderSave` / `Delete` / `List`；页面在「从淘宝订单建单并入库」下面一块（`#refundedBlock`，
  行上 `data-refunded-order`），可以手工登记、也能一键撤掉。**识图不再自动登记（Step 108）**：
  模型把「退货宝」「7天无理由退货」看成退款，自动登记就等于把一张好单永久拦住（开发库真出现过两条
  这种脏数据）；现在识别到退款只在**这一轮**不入库（`note` 写“显示已退款，这一次不入库；货确实到了就
  手工粘明细建单，确实退了就把它登记到「退款 / 关闭的单」里”），黑名单一律由人在页面上登记。
  `mfgReceiptFromOrder` 开头查这张单在不在列表里（`refundedOrderProblem`），在就拒绝，所以**手粘单也进不来**。
  **踩坑：在会失败（`return ServiceUtil.returnError`）的服务里写“记录”，必须另开事务**——
  `mfgReceiptFromOrder` 是 `use-transaction="true"`，错误返回会把同一事务里的写入一起回退，
  所以登记走 `dispatcher.runSync("mfgRefundedOrderSave", …, -1, true)`（`rememberRefundedOrderInNewTransaction`）。
  旧库要跑一次 `tools/refunded-order.sql`（停实例 → ij → 起），和 `part-code-100.sql` 一样；
  OFBiz 启动时会自己补上漏写的 `CREATED_TX_STAMP` 列（日志里能看见 `[addColumn]`）
- **退款 / 交易关闭的单都不入库，但两者不是一回事（Step 84/108/109）**：prompt 里有 `refunded` 与
  `closed` 两个字段——`refunded=Y` 只认「退款成功 / 已退款 / 退款中 / 退货退款」，
  `closed=Y` 认「交易关闭 / 已取消 / 交易失败」（**用户确认：交易关闭＝这笔交易没成交，通常是一直没付款**，
  货根本没发）；`退货宝` / `7天无理由退货` / `退换货` 是店铺政策、每行都印，两个字段都填 N。
  `isRefunded` / `isClosed` / `notToBookIn` 判断，`scan` 给两种不同的 note；页面 `leftOut(order)` 把两种行
  都标红（「已退款，不入库」/「交易关闭，不入库」）且**都不提交**，整张截图都是这两种时把「导入」按钮藏起来。
  **两种都不写黑名单**：读错一次只是这一轮不入库；`mfgReceiptFromOrder` 只对 `refunded=Y` 硬拒，
  `closed` 不拦——万一包裹真到了，手工粘明细就能入库。
  **19 位订单号可能少一位**（实测同一张图/不同图读出 `…500570` 与 `…00570`），而订单号是导入的幂等键，
  所以“重复导入是覆盖”只在两次读到的号相同时成立——这是已知局限，别拿它去放宽判据
- **整张到货单可以删（Step 111）**：`mfgPurchaseReceiptDelete`（IN `receiptId`，OUT `deletedReceipt`/`deletedLines`，
  `use-transaction="true"`）删单头 + 明细行，**批次/库存/流水一律不动**（单据只是“买了什么”的纸）；
  页面在详情（`receipts?receiptId=…`）右上，`confirm()` 里写清“只删单据”，删完 302 回列表并显示
  `parameters.deletedReceipt` 的绿色横幅。要冲库存请走「库存 → 流水」报废（那里留原因），
  **不要让删单去改库存**——那就把“单据”和“实物”两件事混在一起了
- **整张到货单是“作废”不是“删”（Step 112）**：`MfgPurchaseReceipt.statusId` = `MFG_RCPT_ACTIVE` / `MFG_RCPT_VOID`
  （老行为 null，一律当 ACTIVE）；作废时写 `voidedDate` / `voidedByUserLoginId` / `voidReason`。
  `mfgPurchaseReceiptVoid` / `mfgPurchaseReceiptRestore`（`use-transaction="true"`），**批次/库存/流水一律不动**
  （与 OFBiz 的 `ShipmentReceipt` 同理：那玩意儿连 statusId 都没有，撤销靠 Return + 反向流水；
  它那个 `deleteShipmentReceipt` 只是 entity-auto 的硬删，界面上没有任何入口）。
  连带四处要跟着看：① 列表默认只列 ACTIVE，`includeVoid=Y` 才把作废的也列出来（模板有开关，行上有 `data-receipt-status`）；
  ② 作废单的明细**算自由批次**（`freeLots` 里排掉只属于 ACTIVE 单的行），才能重挂到建对的单上；
  ③ `purchaseReceiptSave` / `purchaseReceiptLineSave` 对作废单直接拒（要改先恢复）；
  ④ `mfgReceiptFromOrder` 命中作废单时**复活它**（订单号还是幂等键，不能变成两张同号单），
  `mfgPurchaseFreightTrend` 与采购支出汇总都跳过作废单。
  **老的开发/生产库不用手工 ALTER**：OFBiz 启动时会把缺的列补上（日志里的 `[addColumn]`）。
- **淘宝导出的「订单数据」xlsx 可以整批导入（Step 110）**：到货单页上传 → `/mfg/control/orderExcel`
  （`MfgImportEvents.orderExcel`，multipart 字段 `file`，带 `ajax=Y` 回 JSON：`imported/lines/skipped/failed/notes`；
  不带就 302 回 `receipts?excelImported=…`）。解析全在 `MfgOrderExcel`：**xlsx 就是 zip + XML，没有 POI**，
  只读 `xl/sharedStrings.xml` 与第一个 `xl/worksheets/*.xml`，`t="s"` / `t="inlineStr"` 都支持，
  解析器关掉了外部实体（上传的东西只当数据）。
  **导出把订单级单元格只印在该单第一行，后面几行是空的**（合并单元格），所以要先**按列名**定位列、
  再**向下带值**、最后按订单号分组；`商品金额` 是**单价** → 行金额 = 单价 × 数量；
  `实付金额 − 运费 − 明细合计` 若为正数就是**整单优惠**（同 Step 95 口径），只记正数不记负数。
  到货日取 `订单提交时间`（用户确认：补历史最接近）；`交易关闭` / `超时未付款，订单已关闭` / `充值成功`
  的单跳过并在 `notes` 里说明；每张单一次 `runSync("mfgReceiptFromOrder", …, -1, true)`（`overwrite=Y`），
  订单号仍是幂等键，所以同一份文件导两次不会重复买料。**别改成一次性大批事务**：坏一单不该连累别的。
- **xlsx 也能拖进来 / Ctrl+V 粘贴（Step 114）**：`#excelBlock` 的脚本照截图那块（Step 67）的做法，
  把文件塞回 `#excelFile.files`（`new DataTransfer()`，老浏览器失败就用内部变量 `chosen`，提交时
  `data.set('file', chosen, chosen.name)` 兜底），所以**服务端一行没改**。两条边界：
  ① `document` 上的 paste 监听**只拿 workbook**（`.xlsx` / `spreadsheetml` / zip），截图留给上面的识别框——
  否则粘一张图会在这里弹“只认 xlsx”；文字粘贴一律不动（`#linesField` 还要用）；
  ② 拖进来的不是 xlsx 才报红（“截图请拖到上面的识别框”），拖对了一律只填文件、**不自动导入**，
  仍要点「导入 Excel」（导入会写单据，不该由一次拖放触发）。样式是 `#orderBlock.drop, #excelBlock.drop`。
- **滚到哪一块由 `jump.js` 算，别再用 `scrollIntoView({block:'center'})`（Step 115）**：
  贴图 / 贴 xlsx / 点单号跳详情三处都要“把那一行顶到导航条下面”，所以算法只有一份：
  `webapp/mfg/js/jump.js` 的 `window.jumpUnderNav(element, gap)`（导航高 + 14px 的偏移，已经在
  舒适区就**不动**页面，尾部按 `scrollHeight − innerHeight` 夹住）。装饰器里紧跟着 `session.js` 加载。
  **`block:'center'` 的坑**：截图核对表或“为什么没入库”表长了以后，`#orderBlock` / `#excelBlock` 比屏幕还高，
  居中会把**按钮那一行顶到屏幕外**（实测视口 395px、块 1832px 时按钮在 −700px）；
  现在锚的是 `#scanTools` / `#excelTools` 这两个 `<p>`，实测落点 68.7 / 68.8px（目标 nav 54.8 + 14）。
- **列表刷新后要把阅读位置按回去（Step 115）**：导入成功后两个块都会 `fetch(location.href)` 换掉
  **上面**的 `#receiptListBlock`（列表高度会从 3 行涨到 20 行，+1260px），于是下面那块整个往下滑。
  做法：换 innerHTML **之前**记 `anchorBefore = hint.getBoundingClientRect().top`，换完
  `window.scrollBy(0, hint.top - anchorBefore)`，再 `jumpUnderNav(hint)` 兜一次——
  浏览器自己的 scroll anchoring 有时只按到一半（实测停在 26px，被导航条压住），这一步把它拉回 69px。
  **别把这两步顺序颠倒**，也别指望浏览器每次都替你做。
- **服务重启后 AJAX 出口必须跳登录页（Step 101）**：会话过期时 OFBiz 对 `auth="true"` 的请求回
  **401 + 登录页 HTML**（实测 `orderImage` / `orderPeek` 都是 401，响应体里带 `USERNAME`），
  于是 `fetch(...).then(a => a.json())` 抛异常、页面只红一句「识别请求失败，再点一次」——再点也是同一个 401。
  现在 `webapp/mfg/js/session.js`（装饰器里在 `once.js` 之前加载）提供全局 `jsonOrLogin(answer)`：
  401/403、被 302 到 `login`、回来的不是 JSON 而是带 `USERNAME` / `forgotPassword` 的页面，三种都
  `location.replace('/mfg/control/login')` 并 `Promise.reject(new Error('login'))`；
  调用方写成 `.then(jsonOrLogin).then(...)`，`catch` 里先判 `problem.message === 'login'` 就直接 return
  （不要再显示“失败了”）。已接：到货单页的识别/批量导入、BOM 行保存（products.ftl）、
  销售订单行改数量（salesOrders.ftl）；到货单页的 `receiptSaveJson` / `receiptLineJson` / `orderPeek`
  失败会退回 `form.submit()`，普通 POST 本来就会落到登录页，不必改。
  **不要在模板里再写一份**，也不要改成 `answer.json()`；逻辑有 5 个用例的 node 测试
  （`%TEMP%\jup\js-check\session-test.js`，不联网也能跑）
- **`receipts.ftl` 里的 `orderForm` 脚本必须在 DOM 之后接**：那个 script 写在表单**前面**，
  `document.getElementById('orderForm')` 永远是 null，于是 Step 77 的“再次导入先问”和 Step 84 的退款守卫
  在浏览器里**都没生效**（服务端测试看不出来）。现在用 `DOMContentLoaded` 后再 `wireOrderForm()`；
  踩过：m47 的「没有错误框」断言被**常驻的** `class="error"` 横幅弄挂，所以退款块平时不带 `error` 类，
  只在显示时加
- **防连点（Step 83）**：`webapp/mfg/js/once.js`（装饰器 `</body>` 前加载，在 expand.js 之前）用捕获阶段的
  submit 监听给每张表单记 `data-last-action`，**同一个表单 1.2 秒内的第二次提交直接**
  `preventDefault + stopImmediatePropagation`（页面自己的 submit 处理器也就跑一次），同时把表单里的按钮加上
  `.busy`（`opacity .55` + `pointer-events: none`）；`[data-once]` 按钮（截图识别）与
  `data-toggle-lines` / `data-toggle-receipt` 链接走 0.6 秒的窗口——双击「3 ▸」以前会开了又关。
  `form.submit()` 不触发 submit 事件，但那些调用点（BOM 行/到货单/销售订单的 fetch 兜底）都只在
  **第一次提交之后**才跑，所以仍然只发一次；识别按钮在 fetch 期间自己 dim 到底（once.js 只挡双击，
  挡不住 3 秒后的第二下）。在测试实例上用 Playwright `page.dblclick` 验过：双击「建单并入库」只发 1 个 POST、
  只建 1 个批次；双击「4 道工序 ▸」展开后不会被关掉
- **列表行就地展开（Step 80）**：工单 / 产品 / 出货单 / 销售订单四页也学采购到货那样，点行上的「3 ▸」展开明细。
  共用 `webapp/mfg/js/expand.js`（装饰器里 `</body>` 前统一加载）：链接上写 `data-toggle-lines="<key>"`，
  明细行写 `data-lines="<key>"`，折叠靠 **CSS**（`tr[data-lines] { display: none }` + `.open`），
  所以 fetch 换回来的块天然是收起的，脚本不必初始化；点击是**委托在 document 上**的，因为 innerHTML 里的 `<script>` 不会执行。
  列表服务把明细放进**当前页每一行自己的 map**（`items` / `shortages` + `operations` / `snList` / `bomItems`），
  用 `rowsOf(result, key)` 取回 `putPage` 刚塞进去的那一页再补，别在分页前给全表算明细（20 条 × N 次查询）；
- **销售订单行就地改数量（Step 80）**：`salesOrderLineQty`（IN `salesOrderId`/`lineSeq`/`qty`）+ 只给脚本用的
  `/mfg/control/salesOrderLineJson`（`MfgSalesEvents.lineQtyJson`）。规则：数量 > 0、不能小于已出货、CLOSED/CANCELLED 拒改；
  预留不动（加量只是欠货变大，靠「预留」「排产」按钮去补）。页面成功后 `fetch(location.href)` 只换 `#orderListBlock` 的
  innerHTML，提示写在外面的 `#listHint`，并把之前展开的行重新点开；fetch 挂了退回 `form.submit()`。
  行上的输入框用 `form="soLine_<单号>_<行号>"` 绑到表外的隐藏表单（跟到货单行编辑器一个路子）。
  **巡检脚本里给 `salesOrderLineJson` 写数据一定要先读原值再改回**，而且**别拿列表页第一行当试验品**
  （第一页常常全是已关闭的单，改了会被拒，看着像功能没生效）
- **到货单头就地保存（Step 70）**：`receipts.ftl` 的 `#receiptSaveForm` 用委托的 `submit` 拦下，
  POST 到 `/mfg/control/receiptSaveJson`（事件 `MfgReceiptEvents.saveJson`，跑 `mfgPurchaseReceiptSave`，
  手写 `{"ok":…,"message":…}`，`<response type="none"/>`），结果写在 `#saveHint`（绿=成功、红=失败原因）；
  **fetch 挂了就 `form.submit()`** 回普通 POST，所以不丢数据。跟 Step 57 的 BOM 行保存一个路子
- **不刷新整页的保存（Step 57）**：BOM 行编辑器保留了普通表单提交（`/mfg/control/bomItemSave`，302 回本页），
  另外加了一个只给脚本用的 JSON 出口 `/mfg/control/bomItemSaveJson`（`MfgBomEvents.saveLine`，手写
  `{"ok":true,"message":…}`，`<response type="none"/>`）。页面底部一小段脚本用**委托的 `submit` 监听**接住
  这些表单，`new URLSearchParams(new FormData(form))` POST 过去，成功后 `fetch(location.href)` 取回页面、
  用 `DOMParser` 只把 `#bomBlock` 的 `innerHTML` 换掉——所以不用把行的 HTML 在 JS 里重写一遍，也就不会
  和服务端渲染的结果打架；`fetch` 不可用或失败时退回 `form.submit()`。要加新的“就地保存”照这个模式：
  块包一层 `id`、下拉/输入框不变、事件里跑服务再把结果写成 JSON
- **BOM 行就地编辑（Step 56）**：服务 `mfgBomItemSave`（`/mfg/control/bomItemSave`）一条龙：不给 `lineSeq`
  就新增（行号 `nextBomLineSeq`，型号没版本时 `currentOrNewBomVersion` 顺手建一个并设为当前版本）、
  给 `lineSeq` 就改、`delete=Y` 就删；用量必须 > 0，单价留空即清空（**先取 `currentBomVersionId` 再找行**，
  否则不带 `bomVersionId` 的表单会被当成新增，踩过）。模板：表格里每行一个「看不见的」
  `<form id="bomLine<n>">`（只有隐藏字段），单元格里的 input/select 用 HTML5 的 `form="bomLine<n>"` 绑定，
  所以整行可改又不嵌套表单；行上 `data-line-seq`、当前版本行 `data-current="Y"`、金额格 `data-amount`，
  巡检靠这三个属性断言（不碰中文）。**改了这个表以后 m13 / m32 / m42 / m44 的断言都要跟着看**
  （它们原本读的是只读单元格的文本）
- **屏幕里无条件调用的服务，OUT 漏一个就会在页面留下窟窿**：`mfgProductDetail` 没声明 `bomVersionId` 的 OUT，
  结果标题变成「当前 BOM（，9 行）」而且版本表里的「当前使用」永远不出现（`currentVersionId` 一直是空串）；
  模板里 `${x!'默认'}` 对**空串**不生效，要判 `?has_content`
- **BOM 导入也能选文件（Step 54）**：`/mfg/control/bomImport` 现在是 **Java 事件** `MfgImportEvents.bomImport`
  （`<response name="success" type="request-redirect" value="products">`，成功靠事件里 `request.setAttribute(productCode/importedCount/bomFormat)`，
  失败把消息放进 `errorMessageList` 再 `return "error"` 渲染 products 视图）——它只转成 `mfgBomImport` 的 context，
  解析/校验仍全在服务里。表单 `enctype="multipart/form-data"`，**选了文件就以文件为准**（同时粘了文本也忽略），
  字节按 UTF-8 解码、出现 `\uFFFD` 退回 GBK（Excel 另存的 CSV），上限 `MAX_BOM_BYTES` = 2 MB；
  URL 编码的表单（巡检 m42 走的就是这条）仍旧可用，因为 `parametersOf` 找不到 `multiPartMap` 时退回 `UtilHttp.getParameterMap`
- **`bomVersionId` 是 `id`（20 字符），型号编码也是 `id`（20）**：两者一样长，`型号-Vn` 直接拼会超
  （踩过：型号 `orpah-client-demo-bb` 导入 BOM 报 `A truncation error ... shrink VARCHAR
  'orpah-client-demo-bb-V1' to length 20`）。`formatBomVersionId` 现在**截短型号部分**（`orpah-client-demo-V1`），
  `freeBomVersionId` 撞到别的型号同名时再在版本号前加序号；`nextBomVersionNo` 改成用
  `formatBomVersionId` 反查而不是按前缀匹配（截短后前缀会随位数变）。`bomImport` 的数据库错误经
  `bomImportFailure` 把 truncation 翻成人话。巡检 m44 用 20 位型号编码跑一遍
- **BOM 导入的格式层（Step 52）**：`BomFormat` 枚举（`MFG` / `FRITZING`）+ `detectBomFormat()` + `parseBom(csv, format)`
  分发；`splitCsvLine(line, separator)` 支持任意分隔符（逗号仍兼收 Tab）。
  Fritzing 导出是「`Label` 段一件一行 + `Amount` 段给数量」，解析成 `Map<料号, 行>` 后合并：
  **用量优先取 `Amount`（没有就数 `Label` 行数）**，位号用 `Label` 拼成 `J1,J3`，
  料号取 `part number`，为空时退化用名称（大写）。表头行按首列 `Label`/`Amount` 跳过。
  **料号长度在 `bomImport` 里统一校验 ≤ 20**（`componentCode` 是 `id` 类型），否则 Derby 报字段超长。
  加新格式（嘉立创/KiCad）：加枚举 + 识别 + 返回六列的解析方法 + `parseBom` 里分发 + 页面 `<option>`。
- **角色权限（Step 51）**：权限点 `MFG_VIEW` / `MFG_PRODUCTION` / `MFG_STOCK` / `MFG_ENG` / `MFG_SALES` / `MFG_COST` /
  `MFG_ADMIN`，角色组 `MFG_BOSS` / `MFG_FOREMAN` / `MFG_WAREHOUSE` / `MFG_ENGINEER` / `MFG_SALES`（种子文件里建组 + 授权，
  每组都带 `OFBTOOLS_VIEW`；**不要**给框架组如 `FULLADMIN` 授权，新库没有它）。
  **写服务开头一律 `permissionProblem(dctx, context, PERM_X)`**（返回中文错误 → 页面上红框），
  `can()` 里 `MFG_ADMIN` 与 OFBiz 管理员（`PARTYMGR_UPDATE`）兜底，所以演示库/新库的 admin 都通；
  **成本是「软拦截」**：`mfgProductCost` / `mfgWorkOrderCost` 没有 `MFG_COST` 时返回 `denied=Y`（不报错），
  模板用 `<#if costDenied>` 把成本块/单价列/外协费金额换成提示；质量指标整页要 `MFG_COST`；
  导航由装饰器调 `mfgPermissions` 按区域隐藏；**内部直接调用（`workOrderCreate`→`reserveMaterial`、
  `salesOrderPlan`→`workOrderCreate`、`workOrderComplete`→`workOrderCost`）必须把 `userLogin` 传进子 context**，
  否则子服务一律「请先登录」（踩过：完工单价变 0；学习曲线 `learningCurveQuoteToProject` 调 MES 也要带 userLogin）
- **屏幕里调的服务要显式传 `userLogin`**：`auto-field-map="false"` 时只有 `field-map` 里的东西进 context，
  `mfgProductCost` / `mfgWorkOrderCost` / `mfgQualityReport` / `mfgDashboard` 都因此加了一行
  `<field-map field-name="userLogin" from-field="userLogin"/>`（不加 → 成本显示成「无权查看」、质量页 500）
- **用户账号页（Step 50）**：`/mfg/control/users` 是插件自己的账号维护（不用 OFBiz 原生的 Party Manager）。
  服务 `mfgUserList`（清单 + 可选组 + `canManage`）、`mfgUserSave`（建/改）、`mfgUserResetPassword`（回到 `ofbiz`）；
  **连续输错密码会被框架锁号**：`security.properties` 的 `max.failed.logins`（默认 3）次错密码就写
  `disabledDateTime` 把账号停掉，`login.disable.minutes`（默认 30）分钟后自动恢复；`mfgUserSave` 传
  `enabled=Y` 时直接清 `disabledDateTime`/`disabledBy`/`successiveFailedLogins`，所以「启用」就是解锁。
  **巡检脚本别对生产用 `admin`/`ofbiz`**：用户改过密码后每跑一次就多一次失败，跑三遍就把号锁了（踩过）
  服务 `mfgUserList`（清单 + 可选组 + `canManage`）、`mfgUserSave`（建/改）、`mfgUserResetPassword`（回到 `ofbiz`）；
  建账号**调 OFBiz 的 `createPersonAndUserLogin`**（内部以 `system` 身份跑，绕开 `PARTYMGR_CREATE`，密码哈希由框架做），
  再直接写 `UserLoginSecurityGroup`；**一个账号只一个组**，换组就把旧行 `thruDate` 置为当前时间；
  **能管账号的判定跟 OFBiz 一致**：`Security.hasEntityPermission("PARTYMGR", "_UPDATE", userLogin)`（含 `_ADMIN` 兜底），
  所以开发库（admin 在 `FULLADMIN`）和全新库（admin 在 `SUPER`）都成立——**别写死 `SUPER`**（踩过：dev 直接 403/拒绝）；
  防锁死：不能停用自己、不能改自己的安全组；下拉只列有 `OFBTOOLS_VIEW` / `MFG_*` / `LEARNINGCURVE_*` 的组并默认 `SUPER`；
  改密码只走「重置为 `ofbiz`」，真正的改密还是门禁那句；自己改需要 `PARTYMGR_UPDATE`，`updatePassword` 对管理员会跳过旧密码校验
- **屏幕里用 `auth="true"` 的服务必须 `auto-field-map="true"`**：`auto-field-map="false"` 只传显式 `field-map`，
  上下文里的 `userLogin` 不会进服务 → `ServiceAuthException: User authorization is required`（整页 500，
  HTTP 200 但内容是错误页）；`mfgUserList` 就这么踩过，改成 `auto-field-map="true"` 才好
- **BOM 版本化**：`MfgBomVersion`（`型号-Vn`，DRAFT/RELEASED/OBSOLETE，`basisVersionId`、`ecoId`）+ `MfgBomVersionItem`；
  `MfgProduct.currentBomVersionId` 指向在用版本；启用新版本会把上一个 RELEASED 转 OBSOLETE；
  工单创建时把版本冻结到 `MfgWorkOrder.bomVersionId`，齐套/发料按工单的版本算。
  旧单表 `MfgBomItem` 行在首次读取时冻结为 `-V1`（幂等），表保留但不再使用
- **软件开发阶段阶梯**：`MFG_STAGE_CONCEPT → DESIGN → PROTOTYPE → TRIAL → MASS`；`MfgProduct.devStageId` 与
  `MfgDevProject.stageId` 相互独立（项目可以先不关联型号）；关卡 PASS/CONDITIONAL 才推进阶段，到 MASS 时写 `actualLaunchDate` 并置 `MFG_DEV_DONE`
- **步骤问题清单**：`MfgDevStepIssue`（`issueId` = `ISSUE-yyMMdd-NN`，`projectCode` + 可选 `stepId`），
  三态 `MFG_ISSUE_OPEN/DOING/CLOSED`、三档严重度 `LOW/MEDIUM/HIGH`；服务 `mfgDevStepIssueSave/List/Status`，
  页面在开发项目页（`id="issueTable"`，锚点 `#issues`），项目列表与步骤表各显示一个未结数（`openIssueCount`）；
  步骤必须属于项目的线路（`projectOfStep`），关闭时写 `closedDate`、重开清空；列表按「未结 → 严重 → 最新」排序
- **良率/工时趋势报表**：`mfgTrendReport` 按 `MfgReport.reportDate` 分桶（`periodKey`：日/ISO 周/月），
  纯函数 `yieldRate` / `hoursPerUnit`（分母 0 记 0，页面显示「—」）；页面 `/mfg/control/reports`
  带 `id="trendTable"`，两份 CSV 走 `MfgExportEvents.yieldCsv/hoursCsv`（同一服务、同一份数字）；
  单台工时可以拿去与学习曲线的实测点对照
- **库存：流水 + 重算（照 OFBiz 的做法）**：`MfgInventoryDetail`（`lotId`+`seqId` 主键、`quantityDiff` 带符号、
  `movementTypeId` = RECEIPT/ISSUE/RETURN/SCRAP/COUNT/ADJUST/RESERVE/RELEASE）**只增不改**；
  `MfgMaterialLot.onHandQty` / `reservedQty` / `availableQty` 永远由 `recalcLotStock()` 用流水 SUM 覆盖写回，不做加减；
  **预留也是流水**：RESERVE/RELEASE 写 `reservedDiff`（±qty），`quantityDiff` 恒为 0，所以在库不动、可发量变；
  `availableOf(onHand, reserved, inspect)`：`MFG_IQC_FAIL` 的批次为 0，否则 `max(0, onHand − reserved)`；
  `ensureLotLedger()` 给旧批次补到货 + 历史发料（幂等），页面/汇总/告警里第一次访问时顺手补；
  发料（`issueSave`）与通用流水（`inventoryMove`）都先校验「不超过该批次可用量」，超了报 “Only X left in lot”；
  **自己的预留不算抢占**：校验用 `available + 本工单在该批次上的预留`，发完再 `releaseReservation` 掉相应数量（`kittingLines` 用局部 `reservedMap`）；
  盘点写 `MfgStockCount` + 一条 COUNT 流水，**不直接改数**；安全库存 `MfgStockSetting.minimumStock` 低于时进 `addStockAlerts`（料号为 refId）
- **工单自动预留**：`workOrderCreate` 建完单就调 `reserveMaterial`（服务 `mfgWorkOrderReserve`）——按批次先后（FIFO）在可发量里挑，
  一次能占多少占多少（`min(欠料, 该批次可发)`），写一条 `MFG_MOVE_RESERVE`；`mfgWorkOrderRelease` 把该工单的预留整体释放（反向 RESERVE–RELEASE 之和）；
  齐套欠料改为 `max(0, 需求 − 已发 − 已预留)`，所以“开工单不占料”会让两张工单都以为料是自己的；
  IT 里库存/预留那几项要用**每次唯一的料号与型号**（否则结存会在多次运行之间累计）
- **成品库存（Step 42，与元件共用批次表）**：`MfgMaterialLot.lotKindId` = `MFG_LOT_COMPONENT`（空值等同）/ `MFG_LOT_PRODUCT`，
  成品批次 `lotId = FG-<工单号>`、`componentCode` = 型号、`workOrderId` 记来源工单、`unitPrice` = 成品单位成本；
  `mfgWorkOrderComplete` 按「已报工良品数 − 已入库量」补 `MFG_MOVE_RECEIPT`（幂等），成本 = `workOrderCost().actualTotal ÷ 良品数`，
  同时把工单置 `MFG_COMPLETED`、SN 置 `MFG_COMPLETED`（**此前 SN 永远停在 IN_PROGRESS**）；
  `deliverySave` 只收 `MFG_COMPLETED` 的 SN，按工单分组从 `FG-<工单号>` 扣 `MFG_MOVE_ISSUE`（`referenceNo` = 出货单号），不够则拒；
  `mfgProductStock` 按型号汇总成品；**所有元件视角都要排除成品批次**（`isProductLot` / `componentLots`：结存、安全库存告警、BOM 预留、来料检验、发料），
  `mfgLotList` 默认只看元件，盘点用 `kind=ALL`
- **销售订单两级预留（Step 43）**：`MfgSalesOrder`（`SO-yyMMdd-NN`，OPEN/PLANNED/SHIPPED/CLOSED/CANCELLED）+ `MfgSalesOrderItem`（`lineSeq` 主键之一，带 `shippedQty`、`workOrderId`、`lineDueDate`）；
  预留向 `MfgInventoryDetail` 多写两列 `salesOrderId` + `salesOrderItemSeq`，所以“订单预留”和“工单预留”是同一张流水表；
  行欠货 `max(0, qty − 已预留 − 已出货)`，`salesOrderPlan` 只为 `欠货 − 在制余量` 开工单（已有工单就加量），完工入库时自动把新成品锁回同行；
  **出货校验看“可发 = 已预留 + 欠货”**，并且 `deliverySave` 校验成品批次可用量时要加回“本订单在该批次上的预留”，否则自己的预留会把自己拦住；
  出货核销 = 行 `shippedQty += n` + 释放同等预留，所有行出完才转 `MFG_SO_SHIPPED`
- **库位与移库（Step 44）**：`MfgStockLocation`（`locationId` = 货架编码，类型 WAREHOUSE/ZONE/BIN，`parentLocationId` 可空）；
  **库位记在流水上**（`MfgInventoryDetail.locationId`），所以“某库位有多少”= 汇总该批次在该库位的流水，批次总量仍是所有库位之和；
  移库 `mfgStockTransfer` 写一对 `MFG_MOVE_TRANSFER`（源 $-qty$ / 目标 $+qty$，同一 `referenceNo` = `TR-yyMMdd-NN`），先校验来源库位余额；
  `locationId` 为空的旧流水归「未分配库位」，可作为来源移出；预留仍然只到批次层（不按库位拆），文档里要说清楚
- **外协费按单分摊（Step 45）**：`MfgOutsourceFee`（`workOrderId` + `seqId` 主键，`feeTypeId` = SMT/TOOLING/ASSEMBLY/TEST/OTHER，`basisId` = PER_UNIT / PER_POINT / LUMP，`rate` `qty` `amount`）；
  金额纯函数 `outsourceAmount(basis, rate, qty, qtyOk, plannedQty)`（按台数量留空取良品数、再取计划数）、`outsourcePerUnit(amount, qtyOk, plannedQty)`；
  `MfgWorkOrder.outsourceAmount` 降为**明细合计的缓存**（`storeOutsourceTotal` 写回），
  所以 `mfgWorkOrderOutsourceSave`（整单直填）在**已有明细时必须报错**，否则两套数字会打架；成本块 `outsourcePerUnit` 直接拿来展示单台分摊
- **`LOT-yyMMdd-NN` 的序号不一定是两位**：同一天超过 99 个批次后会变成 3 位，巡检脚本里的正则是 `LOT-[0-9]{6}-[0-9]+`（踩过：写成 `{2}` 会截断成别的批次并写到错的批次上）
- **所有 `XX-yyMMdd-NN` 编号都会越过 99**（`DEV-` / `ECO-` / `ISSUE-` / `DO-` / `V-` / `RMA-` / `CNT-`）：服务端用 `%02d`，到 100 自然变 3 位，
  所以**断言里的正则一律写 `[0-9]+`**。踩过：IT 里 `DEV-\d{6}-\d{2}` 把 `DEV-261021-103` 截成 `DEV-261021-10`（一个不存在的项目）→
  `devStepStatus` 走了不存在项目的分支，连带 5 项断言假失败；修法是改正则，不是改服务
- **三条开发线**：项目有 `trackId`（TRACK_PCB / FIRMWARE / ENCLOSURE）+ `partCode`；步骤目录写在代码里
  （`PCB_STEPS` 5、`FIRMWARE_STEPS` 3、`ENCLOSURE_STEPS` 7，最后两步是 外壳入库 → 产品组装），
  状态存 `MfgDevTrackStep`（status/planned/done/owner/vendor/amount/qty/referenceNo/lotId）；
  拿别的线路的步骤来改会被拒绝；`MFG_ENC_STOCK_IN` 完成时按 partCode+qty 生成 `MfgMaterialLot`（用 `step.lotId` 保证幂等），
  产品组装仍是手工开工单
- **ECO**：DRAFT → APPROVED → APPLIED（或 CANCELLED）；只有 APPROVED 能生效，生效时切到 `toBomVersionId`
- **附件（通用）**：`MfgAttachment`（`ownerTypeId` + `ownerId` + `seq` 联合主键，`fileData` 存 BLOB）服务
  PRODUCT / DEV_PROJECT / WORK_ORDER / DELIVERY / CLAIM；服务 `mfgAttachmentSave/List/Delete`，
  事件 `MfgAttachmentEvents.upload/delete/serve`（`/mfg/control/attachmentUpload|attachment|attachmentDelete`，
  旧的 `productImage*` 保留为兼容别名）；页面共用一个宏 `webapp/mfg/attachmentBlock.ftl`，
  产品页/开发项目页/工单详情页/出货页/售后页各调用一次；JPG/PNG/GIF ≤ 4 MB、PDF ≤ 8 MB，
  出错回原页带 `?err=type|size|nofile|owner`，图片 `thumb=1` 现场缩放成 200px JPEG；
  旧表 `MfgProductImage` 首次读产品时复制进来（幂等）
- **来料检验（IQC）**：`MfgMaterialLot.inspectStatusId` 四态 `MFG_IQC_PENDING / PASS / CONCESSION / FAIL`，
  服务 `mfgLotInspect` 记录状态 + `rejectedQty` + `inspector` + `inspectNote` + `inspectDate`；
  **只有 FAIL 挡住发料**（`issueSave` 里 `isLotUsable`），待检和让步接收照常可发——小厂不逐盘全检；
  新建批次默认 PENDING；状态是英文常量，页面断言用 `value="MFG_IQC_*" selected`，别用中文
- **成本核算**：只算材料 + 工时两项，数据全在现有表里——`MfgBomVersionItem.unitPrice`（BOM 导入第 6 列）、
  `MfgProduct.laborRate` + `standardHours`、`MfgMaterialLot.unitPrice`（采购价）+ `MfgIssue`、`MfgReport.workHours`；
  **标准工时优先按工序模板汇总**（`MfgRouting.stdHours`，页面注“按工序分摊”），没模板才用型号 `standardHours`；
  **型号标准工时有来源**（`standardHoursSource` = MANUAL / LEARNING_CURVE + `standardHoursBaseQty` + `standardHoursEstimateRef`，
  学习曲线组件回填时写，型号页 `data-hours-source="..."` 渲染，手改数值时 `productSave` 退回 MANUAL）；
  工单另记 `MfgWorkOrder.outsourceAmount`（整单外协加工费），实际合计 = 材料 + 工时 + 外协，
  差异给“含外协 / 不含外协”两个口径；逐工序表（`operationCosts`）列出标准/实际/差异，
  报工里出现模板外的 `opSeq` 单列 `inRouting=false`，工时不能丢；
  服务 `mfgProductCost`（qty 台的总价 + 目标成本差与毛利）/ `mfgWorkOrderCost`（标准 vs 实际 + 差异 + operations），
  公用纯函数 `costSummary()`（两位数取整、`targetPrice<=0` 时毛利返回 null）；
  **无单价不当 0**：`missingPriceCount` / `issuesWithoutPrice` 单独计数、页面黄条提示；
  目标成本/售价取该型号**最新一个** `MfgDevProject`（`orderBy("-createdStamp").queryFirst()`，同型号可能有多个项目）；
  两张表分别带 `id="productCost"` / `id="workOrderCost"`，工序成本表 `id="operationCost"`，巡检按 id 切片断言，不碰中文
- **BOM 导入的第一个版本就是当前版本**：`bomImport` 在型号还没有 `currentBomVersionId` 时，把新建的版本直接置 RELEASED 并指给型号，
  否则导完 BOM 页面上还是「未建 BOM」（成本、齐套都读不到）
- **看板告警 + CSV 导出**：服务 `mfgAlerts` 收集「欠料工单」（`shortageRows()`，一行一个缺料元件）与「售后窗口」  （未结售后单的 `sevenDayDeadline` / `afterSaleDeadline`，已过期=ERROR、≤24h=WARN；`hoursLeft` 为 0 表示没设截止，
  所以**判断有没有截止要看 Timestamp 是否为 null**）；排序 ERROR 优先、再按剩余小时升序，页面只列前 20 条；
  三个下载 `dashboardCsv` / `alertsCsv` / `shortageCsv` 在 `MfgExportEvents`（事件 + `<response type="none"/>`），
  文件带 UTF-8 BOM（中文 Excel 否则按 ANSI 读），数字用 `.` 小数点；CSV 转义用 `MfgServices.csvCell/csvLine`（有单测）
- **软件项目（功能点口径）**：`MfgSoftwareProject` 一台账两用——自用记实际工时、对外报价算价格。
  纯函数 `softwareCost(ilf, eif, ufp, swf四项, rdf, pdr, dayRate, taxRate, dncRate)`：
  未调整功能点=35×内部逻辑文件+15×外部接口文件、工作量=耗时率×功能点×软件因素×开发因素、
  工期=1.277×(工作量/176)^0.404、金额=人天×日费率+税+直接非人力成本；
  默认值（2019 西部云谷案例，日费率已从 880 加倍到 1760）：耗时率 **40 人时/功能点**（预估功能点口径）、日费率 1760、税 6%、直接非人力成本 5%、人月折算 176；
  软件因素调整因子 = 基础 1.5 − 复用 0.5 + 前期投入 0.1 + 质量 0.05 这类加减项相加，页面四项可填；
  `plannedDays` 在保存时冻结成快照（改参数不改历史），实测耗时率 = actualHours/功能点，列表返回中位数做基准；
  项目超人天/超期进 `mfgAlerts`（`MFG_ALERT_SOFTWARE`）。**40 人时/功能点与明细功能点的 6~7 人时/功能点 差约六倍，不能混用**；
  页面上一律用中文术语（内部逻辑文件 / 外部接口文件 / 未调整功能点 / 功能点耗时率 / 软件因素调整因子 / 开发因素调整因子），不写英文缩写
- **首次登录强制改密码（Step 49）**：出厂密码 `ofbiz` 未改时 `/mfg` 与 `/learningcurve` 只渲染一张改密表单——  门禁在**装饰器**里（`mfg-decorator` 调 `mfgPasswordStatus` → `result-map="pwdStatus"`，`decorator.ftl` 里
  `mustChange` 为真就只输出表单，导航与 `sections.render("body")` 都跳过），不是每个屏幕各写一遍；
  服务 `mfgPasswordStatus` 用 `isDefaultPassword()` 比对 `currentPassword`（按 `security` 的 `password.encrypt`
  决定要不要走 `LoginServices.checkPassword`），`mfgChangePassword` 核对完当前密码后**转调 OFBiz 的 `updatePassword`**，不自己写库；
  **新密码规则自己先查一遍**（`passwordProblem()`：≥5 位、两次一致、不能和旧密码相同）——OFBiz 的
  `updatePassword` 对管理员会跳过「新旧不同」；
  **改成功就退出登录**：`changePasswordSave` 的 success 改成 `request-redirect value="logout"`（两个 webapp 各一份），
  退出后落到登录页（`main` 未登录时是 401 + 登录页，巡检要读异常里的响应体才能断言登录页）；
  导航条最后一项是「退出登录」（`/mfg/control/logout`，`.nav a.logout` 样式，两个装饰器都有，门禁页也有）；
  表单 POST `changePasswordSave`（两个 webapp 各一个 request-map，落到同一个服务）；
  **开关是系统属性 `org.langhua.mfg.requirePasswordChange`，只有 `Y` 才启用**：`start-prod.bat` 设 `Y`，
  开发实例 `ofbiz-server.bat` 显式设 `N`（否则巡检/集成测试用 `admin`/`ofbiz` 登录会整片红），
  `start-dev-gate.bat` 是「开发实例 + 门禁开」的调试入口；重置密码用 `gradle loadAdminUserLogin -PuserLoginId=admin`；
  巡检断言改密码要用 ASCII 标记（`action="/mfg/control/changePasswordSave"` / `action="/mfg/control/workOrderCreate"`），
  别断言中文——上下文里的字符串会被渲染成数字实体
- **保修**：从出货日开始算，月数在产品上（`warrantyMonths`，默认 12）- **淘宝窗口**：7 天无理由 = 签收日 + 8 天 00:00（即签收次日起 168 小时）；15 天售后 = 签收 + 15 天 23:59:59
- **买家名单**：匹配键优先买家昵称（`TB:N:nick`），因为淘宝收件手机号常被隐私号掩码；手机号必须是 11 位真号、
  带 `*` 一律拒绝；名单 12 个月自动失效；**只提示，不拦截任何法定售后**
- **UI 配色**：两个组件统一 VS Code "Dark Modern"（`:root` 变量：底 `#1f1f1f`、条 `#181818`、部件 `#252526`、
  输入 `#313131`、主色 `#0078d4`、链接 `#4daafc`、ok/warn/error `#89d185`/`#cca700`/`#f14c4c`）；
  `--border: #3a3a3a` 比 VS Code 原生 `#2b2b2b` 亮一档；`h2`/`h3` 带 `border-top` 做区块分隔；
  导航条是一行 sticky、放不下横向滚动；**一页只有一个蓝色主按钮**，表格行内按钮用深灰次要样式
- **网站图标**：源图 `F:\build-ofbiz\langhua-logo.png`（400×400 白底）→ `tools/make-icons.ps1` 把白色转透明并生成
  `logo.png`(512) / `logo-180` / `logo-64` / `logo-32` 到两个组件的 `webapp/<组件>/images/`；
  `decorator.ftl` 里 `rel="icon"` ×2 + `apple-touch-icon`，导航条第一项是 `<img class="brand">`（`alt=""`，CSS `height:1.7em`）

## 兄弟组件 langhua-ofbiz-learningcurve

- 部署到 `plugins/learningcurve`；`deploy-lc.bat`（`lc-deploy.txt`）、`run-tests.bat`（40 个单元测试，需要 jama
  `C:\Users\...\.m2\repository\gov\nist\math\jama\1.0.3\jama-1.0.3.jar`）、`lc-it.bat`（52 项）、
  `verify-lc.ps1`（84 项页面巡检）
- **它与本组件有数据联动（两个服务）**：`learningCurveMesBatches` 读本组件的 `MfgWorkOrder` + `MfgReport`，
  把每个工单折成一个批次（数量 = 报工合格数之和，数值 = 报工工时之和）喂给学习曲线；
  `learningCurveMesPeriods` 按 `reportDate` 分日/ISO 周/月，一个期间一个批次——与本组件报表页同一口径；
  因此改 `MfgReport` 的字段或语义时要同步检查那边
- 联动入口：报表页 `/mfg/control/reports` 尾部的「喂进学习曲线」链接带 `mesGroupBy` + `mesFrom` + `mesTo`，
  目标 `/learningcurve/control/main`（两个 webapp 会话不共享）；学习曲线那边只有带了这些参数才用期间取点，
  否则直接打开仍是样例数据
- **学习曲线能把工时写回型号**（Step 47）：那边 `learningCurveApplyStandardHours` 直接改
  `MfgProduct.standardHours` + `standardHoursSource='LEARNING_CURVE'` + `standardHoursBaseQty` + `standardHoursEstimateRef`，
  所以改 `MfgProduct` 工时字段时要同步检查那边；`productSave` 发现数值变了会把来源退回 `MANUAL`
- **`snPrefix` 是 `very-short`（10 字符）且默认取型号编码**：型号编码超过 10 位又不给 `snPrefix` 时，
  过去是 Derby 报错→错误页（HTTP 200，巡检看不出来），现在 `productSave` 直接返回中文错误；
  巡检脚本建型号用 `PSLC<HHmmss>` 这种 10 位码
- **两套实例并存要改的不只是 web 端口**：管理套接字（`AdminServerContainer`，默认 `127.0.0.1:10523`）不在
  `framework/*/ofbiz-component.xml` 里，而是系统属性 `ofbiz.admin.port`；没改的话第二个实例启动到
  `admin-container` 就 `Couldn't create server socket` 退出。`start-prod.bat` 用 `JAVA_TOOL_OPTIONS=-Dofbiz.admin.port=10623`
- **导入用 CSV 同时接受逗号与 Tab**（`splitCsvLine`），并忽略 UTF-8 BOM（`parseBomCsv`/`parseLotCsv` 里的 `\uFEFF`）：
  这样从 Excel 选一块直接粘贴就能用；`#` 开头是注释，所以表头行必须写成 `#料号,名称,...`，否则会被当成料号
- **`<ofbiz-component>` 没有 `title` 属性**：24.09 的 XSD 不认，启动/`loadAll` 会报 `cvc-complex-type.3.2.2`（只是 warning 级别，但污染日志）
- **权限种子只能引用全新库里存在的组**：授给 `FULLADMIN`（新建库里不存在）会违反外键 `SEC_GRP_PERM_GRP`，
  让 `--load-data` 整个回滚、`loadAll` 失败——踩过；只授 `SUPER`
- 两个组件的关系图画在 `langhua-ofbiz-mfg/README.md` 的「与学习曲线组件的关系」（mermaid，
  两组件结构一致时的单一出处；孪生份在学习曲线 README 的「与 MES 联动」）
- 屏幕 `main` / `records` / `recordDetail`，模板 `estimation.ftl` / `records.ftl` / `recordDetail.ftl`
- 同一套装饰器做法：`lc-decorator` + `webapp/learningcurve/decorator.ftl` + `css/lc.css`，
  `pageTitle`（学习曲线估算 / 估算记录 / 估算详情）、`activeMenu`（estimate / records）
- 三个模板保留自己的 `<h1>`；`recordDetail.ftl` 里还有「记录不存在」分支和 CSV/返回链接那一段，别删
- 趋势图是手写 SVG：`<text>` 默认黑色，必须 `svg text { fill: var(--fg-muted) }` 才能在深色底上看见
