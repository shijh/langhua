# Legacy build (OFBiz 09.04)

本目录保留组件在 **OFBiz 09.04** 时代的构建方式，仅作历史参考，**不再使用**。

- `pom.xml` / `assembly.xml`：Maven 构建 + assembly 打包，产出 `learningcurve/lib/*.jar` 并部署到 OFBiz 09.04 的 `hot-deploy`。
- 该构建依赖的两个制品已无法获取：
  - `ofbiz:ofbiz-base:09.04`（发布仓库 `http://maven2.langhua.org/public/` 已失效）
  - `jama:matrix:1.0.2`（不在 Maven Central）

当前构建请使用 OFBiz 24.09 的 Gradle 插件机制，见仓库根目录 `README.md`。
