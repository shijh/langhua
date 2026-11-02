# 第三方组件与许可 — langhua-ofbiz-mfg

本组件自身以 **Apache License 2.0** 发布（见同目录 `LICENSE` 与 `NOTICE`，版权归天津龙华 2010-2026）。

这份文件把“本组件用到的外部东西”按 **是否随本组件一起分发** 分开发布，原因是：把平台带的东西列成“我们的第三方库”会
误导读者，而漏掉真正打进包里的又会出合规问题。**结论先写在这里：本组件不分发任何第三方代码。**

## 1. 随本组件分发（进插件 jar / webapp）的第三方代码

| 组件 | 版本 | 许可证 | 用途 |
| --- | --- | --- | --- |
| 无 | — | — | `build.gradle` 的 `dependencies {}` 是空的 |

`webapp/mfg/js/` 下 9 个脚本（`niimbot.js` / `jump.js` / `enter.js` / `once.js` / `expand.js` / `session.js` /
`shots.js` / `copyLabel.js` / `dashboardRefresh.js`）全部是本项目自己写的，没有 jQuery / moment / 任何 CDN 资源；
`css/mfg.css`、`images/` 下的图标与本组件自己生成的素材也没有第三方来源（见第 6 节）。

## 2. 平台在运行时提供（**不**随本组件分发）

| 组件 | 版本 | 许可证 | 用途 | 与本组件的关系 |
| --- | --- | --- | --- | --- |
| Apache OFBiz | 24.09.07 | Apache-2.0 | 宿主平台 | 本组件是它的插件 |
| ZXing core | 3.5.4 | Apache-2.0 | 二维码编码 | 走平台自带的 `generateQRCodeImage` 服务（`framework/common/src/main/java/org/apache/ofbiz/common/qrcode/QRCodeServices.java`），本组件只 `runSync` 调它 |
| Java SE（Amazon Corretto） | 17 | GPLv2 + Classpath Exception | 运行时 | 由运维安装，本组件不分发 |

ZXing 是 OFBiz 自己的依赖（平台仓库 `dependencies.gradle` 第 22 行 `implementation 'com.google.zxing:core:3.5.4'`），
由 OFBiz 的发行包与 NOTICE 覆盖；本组件的 jar 里没有它的任何类，升级 ZXing 属于升级平台的事。
两处 Apache-2.0 之间的关系：本组件与 ZXing 同为 Apache-2.0，仅在运行时链接，无冲突。

## 3. 浏览器标准 API（不是库）

- **Web Bluetooth**（`navigator.bluetooth`，`webapp/mfg/js/niimbot.js`）：W3C 规范、浏览器内建能力，没有可附加的许可证义务，也不随任何包分发。
- **Canvas 2D**（`outboundLabel.ftl` 里把标签画成 1 位位图）：同上。

## 4. 参考过、但**没有复制代码**的协议文档

| 来源 | 许可证 | 用到的内容 |
| --- | --- | --- |
| [`kjy00302/niimprint`](https://github.com/kjy00302/niimprint) | MIT | 精臣 D11 的命令序列、行包（`0x85`）布局、连接包外面套 `0x03` |
| [`iscarelli/niimbot-web-bluetooth`](https://github.com/iscarelli/niimbot-web-bluetooth) | MIT（`docs/protocol-v4.md`） | GATT service / characteristic UUID、帧结构与校验、各命令的应答码 |

`webapp/mfg/js/niimbot.js` 是按这两份**文档里描述的字节格式**重新实现的（纯 ES5 + Promise、无依赖），
一行源码都没有拷贝；文件头与 `AGENTS.md` 的 Step 261 都写了出处——这属于致谢，不是 MIT 的署名义务
（MIT 只在“分发源码或其派生作品”时才要求保留版权声明，我们两者都没有）。
将来若真要照抄它们的代码，则需要在本文第 1 节补一行 MIT 与本仓库的那份 `LICENSE` 副本。

## 5. 只在构建 / 测试期使用（不进发行包）

| 组件 | 版本 | 许可证 | 用途 |
| --- | --- | --- | --- |
| JUnit | 4.13.2 | EPL-1.0 | 单元测试（`mfg-tests-all.bat` 用 `F:\build-ofbiz\lib` 里的 jar，不打进插件） |

## 6. 图片与图形素材

- `images/logo*.png`、`images/logo-qr.png`：由 `tools/make-icons.ps1` / `tools/make-qr-logo.ps1` 从本公司自己的
  `F:\build-ofbiz\langhua-logo.png` 生成（脚本在仓库里），源图自有。
- `images/cloud-*.svg`（登录页的云纹边框）：**自有**，路径由 `tools/make-cloud-tiles.js`（Node，无依赖）按几何参数
  算出——一条平缓正弦 + 浪峰/浪谷上各一个云头，再用同一个渐变遮罩拼成四张可平铺的图。脚本在仓库里，结果可重现。
- 页面里的系统字体（`KaiTi` 等）只在客户端渲染时引用，不随本组件分发字体文件。

> **云纹的来源变更（Step 264，留档）**
>
> 这一道边框原来用的是 **iconfont.cn 下载后在 Inkscape 里改的**两张图（`Pictures\长云纹.svg` / `圆角.svg`，
> 文件里留着 iconfont 的 `p-id` 属性与 `#d45500` / `#e0bc9c` 原色）。iconfont **没有统一许可证**，
> 版权归上传者、按图标库页面上标明的授权条件用；而我们的用法是**切碎、镜像、加渐变遮罩后随产品分发**
> （改编 + 再分发），比“在页面里显示原图”更依赖授权范围，因此改成了自绘。
> 那两张源文件与 `%TEMP%\jup\make-long-tiles.ps1` / `make-corner-tiles.ps1` **不再参与构建，也不再随产品分发**。
> 角花（`corner-*.svg`）同时撤掉了：1.8mm 见方时读成一块糊点，现在四条带子直接铺到角上、由卡片自己的圆角收边。


## 7. 兼容性小结

| 类别 | 许可证 | 与 Apache-2.0 的兼容性 |
| --- | --- | --- |
| 本组件代码 | Apache-2.0 | — |
| 平台与 ZXing | Apache-2.0 | 兼容（同类许可，运行期链接） |
| 协议参考文档 | MIT | 兼容（未复制代码，仅致谢） |
| 云纹图形 | 自有（`tools/make-cloud-tiles.js` 生成） | 兼容 |
| JUnit（测试期） | EPL-1.0 | 不涉及分发；即便分发也与 Apache-2.0 兼容（EPL 允许与其“同一作品”外的东西共存） |
| JDK | GPLv2+CE | 由运维提供，本组件不分发 |

**没有 GPL / AGPL / SSPL 之类的强著佐权代码进入本组件的任何发行物**（JDK 的 GPLv2 只约束 JDK 自身，
Classpath Exception 明确允许链接它的应用按自己的许可证发布）。

## 8. 维护规则

- 只在**真正分发**的东西变化时改本文第 1 节；平台升级（含 ZXing 换版本）改第 2 节的版本号即可。
- 新增任何随组件分发的第三方代码（复制源码、`pluginLibsCompile`、内嵌字体/图标集）时，必须同时：
  1. 在第 1 节加一行（组件 / 版本 / 许可证 / 用途）；
  2. 在 `webapp` 或 `src` 里保留其版权声明，必要时把它的 `LICENSE` 原文放到本目录；
  3. 确认不是 GPL / AGPL / SSPL 等与 Apache-2.0 发行方式冲突的许可。
- `build.gradle` 的 `dependencies {}` 一旦不空，第 1 节的“无”必须改成实际依赖。
