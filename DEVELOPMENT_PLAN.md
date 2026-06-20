# Termux-Manager 安卓应用开发计划

> 本文档是对同目录 `plan.md`(产品方案)的工程落地计划。

## 背景 (Context)

`plan.md` 定义了一款名为 **Termux-Manager** 的原生安卓应用:面向 Termux 开发者的「项目工作区 + 文件管理器」。它把 `Documents/Termux-Manager` 作为固定项目根,统一管理项目目录,并在多个存储区域之间安全地流转文件;强调**非 root、不申请全盘权限、用 SAF 授权、隐藏系统/私有目录、保护根目录、隐藏回收站、应用内部文件对用户不可见、主操作集中在屏幕底部(无障碍)**。

本计划把该方案落地为可执行的工程计划。已确认的关键前提:

| 维度 | 决策 |
|---|---|
| 构建方式 | **GitHub Actions 云构建**(Termux 内写代码 + `git push`,APK 在云端产出) |
| 开发语言 | **Kotlin** |
| UI | **View + XML + Material 3**(传统 View 体系,底部操作栏/底部弹层) |
| 最低版本 | **minSdk 30 (Android 11+)**,`targetSdk = 36` / `compileSdk = 36`(贴合用户设备 Android 16) |
| Termux Home 访问 | **本机 SFTP**(App 连 `127.0.0.1:8022`,**密码认证**,由 Termux 的 `sshd` 代读写 `$HOME`) |

**环境实情**:工作目录 `/storage/emulated/0/app-develop` 为空(仅 `plan.md`);构建走 CI,Termux 端**无需**装 JDK/Gradle/SDK,只编辑代码 + 推送。Termux 已装 **openssh 10.3p1**,`sshd_config` 的 **SFTP 子系统已启用**(`Subsystem sftp`),默认端口 **8022**,`PasswordAuthentication` 默认为 `yes` —— 即 Home 访问所需的服务端**基本就绪**,用户仅需 `passwd` 设密码并启动 `sshd`。Termux 用户名为 `u0_a572`。

---

## 关键设计:统一存储后端(SAF + 本机 SFTP)

文件操作抽象为统一接口 `FileBackend`,两种实现并存,使「公共存储」与「Termux Home」在 UI 与操作层完全一致:

```kotlin
interface FileBackend {
  suspend fun list(dir: NodeRef): List<FileEntry>
  suspend fun mkdir(parent, name); suspend fun rename(node, newName)
  suspend fun delete(node); suspend fun moveWithin(src, dstParent)   // 同后端内优化
  suspend fun openInput(node): InputStream
  suspend fun openOutput(parent, name, size?): OutputStream
}
```
- **`SafBackend`** — 公共存储 + 工作区,基于 `ContentResolver`/`DocumentsContract`。
- **`SftpBackend`** — Termux Home,基于 **sshj** 连 `127.0.0.1:8022`(密码认证)。
- **跨后端移动/复制**(Home ↔ 公共存储)由上层 `TransferService` 用「读源流 `openInput` → 写目标流 `openOutput`,move 再删源」完成 —— 这正是 `plan.md` 要求的「Home ↔ 公共存储双向移动」,现在自然成立。

**三个落地要点:**

1. **Termux Home(via 本机 SFTP)** — App 提供「启用 Termux Home」引导页,展示可复制命令:
   ```
   passwd      # 首次设置登录密码
   sshd        # 启动服务,监听 127.0.0.1:8022
   whoami      # 复制输出 -> 填入 App 的「用户名」
   ```
   App 侧填 host=`127.0.0.1`、port=`8022`、user=`<whoami 输出>`、password,点「测试连接」即把 Home 挂为一个受保护根区域。密码用 `EncryptedSharedPreferences` 加密存储。**补充**:同时引导用户在 Termux 建软链接 `ln -sfn /storage/emulated/0/Documents/Termux-Manager ~/projects`,方便 CLI 侧 `cd ~/projects/<项目>`。

2. **SAF 授权:一次授权 primary 卷根** — Android 11+ 允许授权主共享存储根 `content://com.android.externalstorage.documents/tree/primary%3A`,一次覆盖 `Download/Documents/Pictures/DCIM/Movies/Music` 等全部公共目录(系统自动拒绝 `Android/data`、`Android/obb`,契合方案的「禁止访问 Android/」)。App 在 UI 层把这些子目录组织成各自「受保护根节点」,无需逐目录授权;SD 卡/U 盘等卷再单独追加。

3. **回收站落点:各区域内隐藏 `.trash` + 元数据集中私有目录** — SAF/SFTP 均无系统回收站。删除=移入该区域下隐藏 `.trash/`(同区域内 move 为快速 rename,避免跨文件系统复制);原始路径/删除时间/大小等元数据集中存应用私有目录(Room)。`.trash` 等内部目录在列表中始终过滤,用户不可见。

---

## 技术架构

**整体**:单 `Activity` + 多 `Fragment`(手动 fragment 事务,不引入 Navigation 以保持 KISS);MVVM(`ViewModel` + Repository);文件操作走协程 + 进度对话框/通知。

**关键技术决策**:
- 目录列举用 **`ContentResolver.query` 直接查 children**(`buildChildDocumentsUriUsingTree`),而非慢的 `DocumentFile.listFiles()`。
- 同后端移动优先 `DocumentsContract.moveDocument()` / SFTP `rename`;跨后端或不支持时**流式 copy(+delete)**。
- SAF 授权持久化复用系统 `contentResolver.persistedUriPermissions`;SFTP 密码用 `EncryptedSharedPreferences`。
- 回收站记录用 Room;显示设置用 DataStore。
- **sshj 在 Android 上需处理 BouncyCastle 依赖**(通常显式引入 `bcprov-jdk18on` 并排除冲突)—— CI 构建会验证。

**模块/关键文件**(代表性):
```
app/src/main/
  AndroidManifest.xml                  // 无存储权限声明(纯 SAF);仅 INTERNET(连 localhost);单 Activity
  java/com/termux/manager/
    App.kt  MainActivity.kt
    data/fs/
      FileBackend.kt  FileEntry.kt  NodeRef.kt
      SafBackend.kt                    // ContentResolver/DocumentsContract
      SftpBackend.kt                   // sshj -> 127.0.0.1:8022(密码)
      StorageArea.kt                   // 受保护根:工作区/Download/Documents/Pictures/DCIM/Movies/Music/授权卷/Termux Home(SFTP)
      TransferService.kt               // 同后端 move/copy + 跨后端流式传输 + 进度
    data/trash/   TrashDatabase.kt  TrashItem.kt  TrashDao.kt  TrashRepository.kt
    data/settings/  SettingsStore.kt(DataStore)  SftpCredentialStore.kt(加密)
    domain/
      ProtectionRules.kt               // 受保护根判定;系统目录黑名单;内部文件保留名单(.trash/.termux-manager/.cache/.index/.thumbnails/.state 等)
      VisibilityRules.kt               // 按区域+设置决定隐藏文件可见性
    ui/
      onboarding/  GrantAccessFragment(SAF)  EnableTermuxHomeFragment(SFTP 引导+测试连接)
      browser/  BrowserFragment  BrowserViewModel  FileAdapter
      ops/      BottomAppBar/BottomSheet 操作面板、ConflictDialog(覆盖/跳过/重命名/取消)、ConfirmDeleteDialog
      trash/    TrashFragment
      settings/ SettingsFragment
  res/layout, res/values(Material3 主题、strings)
```

**可见性规则**(`VisibilityRules`):①先剔除内部保留名单(任何区域/设置下都不可见、不可选、不入搜索/项目识别);②工作区内**项目目录内部**显示其余隐藏文件(`.git`/`.env` 等用户文件);③公共目录默认隐藏点文件、设置可开启;④Termux Home 默认显示隐藏文件(符合方案)。

---

## 里程碑(增量交付,每个里程碑 CI 都能产出可安装 APK)

- **M0 — 脚手架 + CI**:Gradle(Kotlin DSL),`minSdk 30 / target 36`;依赖 `core-ktx/appcompat/material/recyclerview/documentfile/lifecycle-viewmodel-ktx/room(+ksp)/datastore-preferences/coroutines/security-crypto/sshj(+bcprov)`;在 `app-develop` 初始化 git(`core.filemode=false`)并推送 GitHub;`.github/workflows/build.yml`:`setup-java@v4`(temurin 17)+ `setup-android` + `./gradlew assembleDebug` + `upload-artifact`。**验证**:push 后 Actions 产出可安装 APK。
- **M1 — SAF 授权 + 工作区浏览 + 后端抽象**:确立 `FileBackend` 抽象(先实现 `SafBackend`);首启引导授权 primary 根并持久化;无则创建 `Documents/Termux-Manager`;`ContentResolver` 高效列举 + RecyclerView + 进入/返回。**验证**:能浏览工作区。
- **M2 — 多区域 + 受保护根 + 可见性**:顶层展示工作区 + 各公共目录 + 授权卷;受保护根标记(禁删/移/重命名);系统目录隐藏;`VisibilityRules` 与内部文件过滤生效。**验证**:各区域可浏览,根受保护,隐藏规则正确。
- **M3 — 文件操作 + 底部无障碍布局**:`BottomAppBar`/`BottomSheet` 承载 复制/移动/重命名/删除/新建文件夹/新建项目;多选;`TransferService`(同后端 move、流式 copy,协程+进度);覆盖冲突对话框;跨区域移动确认、公共目录移动提示源删除;受保护根操作拦截。**验证**:完整文件流转 + 冲突处理。
- **M4 — Termux Home(SFTP 后端)**:实现 `SftpBackend`(sshj+密码,凭据加密存储);`EnableTermuxHomeFragment` 引导(passwd/sshd/whoami + 测试连接);Home 作为受保护根区域接入;**跨后端** Home↔公共存储互传(复用 `TransferService` 流式路径)。**验证**:启用并浏览 Home;Home↔Download 双向移动/复制成功。
- **M5 — 回收站**:删除=移入区域内 `.trash`+入库(二次确认/批量确认);回收站视图 恢复 / 彻底删除;覆盖 SAF 与 SFTP 两类区域。**验证**:删除可恢复、可彻底清除,`.trash` 不可见。
- **M6 — 打磨**:新建项目(建目录;`git init`/CLI 由用户在 Termux 侧做)、Termux 软链接引导、设置页(隐藏文件开关/回收站/SAF 授权管理/SFTP 连接管理)、无障碍细节(底部触达、确认按钮位置、TalkBack 标签)、空状态/错误/空间不足提示。

---

## 验证方式(端到端)

1. **构建**:Termux 内 `git push` → GitHub Actions 下载 `app-debug` artifact → 设备安装(debug 签名可直装)。
2. **授权与浏览**:首启完成 SAF 授权;确认 `Documents/Termux-Manager` 自动创建;浏览各公共目录;确认 `Android/`、系统目录、`.trash`/`.termux-manager` 等不可见。
3. **保护规则**:对各受保护根尝试删/移/重命名 → 被拦截;其内部文件可自由操作。
4. **公共存储流转**:`Download↔工作区`、公共↔公共 移动/复制;制造同名冲突验证四种处理;验证跨区域确认与源删除提示。
5. **Termux Home(SFTP)**:在 Termux `passwd && sshd`,App 端填 `127.0.0.1:8022`/用户名/密码 → 测试连接通过;浏览 `$HOME`;`Home→Download`、`Download→Home` 双向移动/复制成功;断开 sshd 时 App 给出友好的连接失败引导。
6. **回收站**:删除文件/目录/批量(含 Home 内文件)→ 二次确认 → 进回收站 → 恢复 / 彻底删除成功。
7. **无障碍**:确认主操作均在底部可单手触达;TalkBack 抽查标签。

---

## 注意事项 / 待定项

- **sshd 生命周期**:`sshd` 不开机自启,Termux 重启后需重新 `sshd`(或用 Termux:Boot 自启)。App 在连接失败时引导启动。
- **本机端口安全**:`sshd` 默认监听所有接口(局域网内同密码可达)。引导中**建议**于 `sshd_config` 设 `ListenAddress 127.0.0.1` 仅限本机(默认仅建议、不强制)。
- **GitHub 仓库**:需 GitHub 账号并新建仓库用于 CI(私有仓库 Actions 免费额度够用)。
- **签名**:MVP 用 debug 签名直接安装;日后要 release 签名再把 keystore 放进 GitHub Secrets。
- **数据持久化**:回收站元数据用 Room(记录有列表/查询需求);如更偏好零依赖 JSON 文件方案,可在 M0 调整。
