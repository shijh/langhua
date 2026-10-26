# OFBiz 本身的补丁（不是插件的代码）

这里放的是**打在 OFBiz 实例里的框架补丁**，两个组件（mfg / learningcurve）共用同一套实例，所以放在仓库根，
不放在某个组件下。补丁记录在 git 里，`plugins/` 里的组件代码不受影响。

| 文件 | 作用 |
| --- | --- |
| `hashcrypt-pbkdf2.patch` | 统一的 diff，备查（给人看，说明改了哪一行） |
| `apply-hashcrypt-pbkdf2.ps1` | 真正干活的脚本：改实例里的源码 + `gradlew jar`（幂等，可重复跑） |

## 1. HashCrypt 的 PBKDF2 校验（OFBiz 24.09.07）

**问题**：`framework/base/src/main/java/org/apache/ofbiz/base/crypto/HashCrypt.java` 的 `doComparePbkdf2`
把盐多做了一次 `Arrays.toString`：

```java
// 写入时：PBEKeySpec(password, salt.getBytes(), iterations, 256)   —— salt 是原始字节
byte[] salt = Arrays.toString(java.util.Base64.getMimeDecoder().decode(parts[1].getBytes(StandardCharsets.UTF_8)))
        .getBytes(StandardCharsets.UTF_8);
```

于是校验时算的是 `"[103, -67, 24, …]"` 这段文本的字节，跟写入时的盐永远不一样。
**任何 PBKDF2 变体（SHA1 / SHA256 / SHA384 / SHA512）写进库的密码都验不过**（与 HMAC 的选择无关）。

上游 trunk 已经改成直接 `decode(parts[1]…)`（并加了常量时间比较），这里就是**回移（backport）那一行**。

**影响面**：只影响“用 PBKDF2 存的新密码能不能登录”，SHA / SHA-512 / 明文那些路径完全不变。
所以：

- 打了补丁但 `hash.type` 还是 `SHA` / `SHA-512`：行为零变化；
- 想用 PBKDF2（`password.encrypt.hash.type=PBKDF2WithHmacSHA512` + `password.encrypt.pbkdf2.iterations`），
  **必须先打这个补丁**，否则改完密码就登不进去。

## 2. 用法

```powershell
# 打补丁并重建实例的 jar（改完要重启实例才生效）
powershell -NoProfile -ExecutionPolicy Bypass -File apply-hashcrypt-pbkdf2.ps1 -Instance F:\build-ofbiz\ofbiz-prod

# 只改源码、不重建（想自己控制构建时）
powershell -NoProfile -ExecutionPolicy Bypass -File apply-hashcrypt-pbkdf2.ps1 -Instance F:\build-ofbiz\ofbiz-test -SkipBuild

# 找不到 gradle 时自己指定（必须 8.x，OFBiz 24.09 的 build.gradle 在 gradle 9 上会直接报错）
powershell -NoProfile -ExecutionPolicy Bypass -File apply-hashcrypt-pbkdf2.ps1 -Instance F:\build-ofbiz\ofbiz-prod `
    -Gradle C:\Users\shi.jinghai-honor\.gradle\wrapper\dists\gradle-8.6-bin\afr5mpiioh2wthjmwnkmdsd5w\gradle-8.6\bin\gradle.bat
```

脚本挑 gradle 的顺序：`-Gradle` 指定 → 实例自带的 wrapper（`gradlew.bat` + `gradle-wrapper.jar` 都在才算）→
`%USERPROFILE%\.gradle\wrapper\dists` 里**版本号是 `gradle-8.*`** 的最新一份 → PATH 里的 `gradle`。
（这台机器上还装着 gradle 9.8.1，它会先被跳过——24.09 的 `common.gradle` 在 9 上会失败。）

脚本会打印三选一：`patched`（刚改完）/ `already patched`（不用动）/ `unexpected source`（源码和预期不一样，别硬来）。
重建完记得重启：`start-prod.bat` / `stop-prod.bat`（开发 8443 用 `deploy-mfg.bat` 也行，生产别整包 deploy）。

## 3. 升级 OFBiz 会丢

补丁是改实例里的源码，**换新版本的 OFBiz（或重新解包一份实例）就没了**，要重跑一次脚本。
升级前先看一眼 `apply-hashcrypt-pbkdf2.ps1` 里的 `$badPattern` 还匹配得上（匹配不上说明上游已经自己修了，
那时把脚本和这份说明一起删掉即可）。

## 4. 改完怎么验

1. 实例配置：`password.encrypt=true`、`password.encrypt.hash.type=PBKDF2WithHmacSHA512`、
   `password.encrypt.pbkdf2.iterations=210000`，重启；
2. 用 `admin` / 旧密码登录 → 应该正常（旧 `$SHA$…` / `{SHA}…` 不受影响）；
3. 在「用户账号」页随便建个测试账号（默认密码 `ofbiz`）→ 用那个账号登录；
   **能进 = 补丁生效**，此时库里那个账号的值是 `{PBKDF2-SHA512}210000$<base64盐>$<base64摘要>`。
4. 更快的办法（不动界面）：`mfgUserResetPassword` 把测试账号的密码重置成 `ofbiz`，然后直接 POST 登录。
