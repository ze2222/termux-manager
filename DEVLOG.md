# Termux-Manager 开发记录 (DEVLOG)

> 本文档记录 Termux-Manager 的完整开发过程:每个阶段做了什么、遇到的问题与解决方式、当前进度。
> 配套文档:`plan.md`(产品方案)、`DEVELOPMENT_PLAN.md`(工程计划)。
> **下次接着开发**:直接看「§2 进度总览」的「当前状态 / 下一步」,再看「§6 下一步:M2」。

最近更新:2026-06-20

---

## 1. 项目速览

| 项 | 值 |
|---|---|
| 仓库 | https://github.com/ze2222/termux-manager (public) |
| 本地路径 | `/storage/emulated/0/app-develop` |
| 语言 / UI | Kotlin / View + XML + Material 3 |
| 构建方式 | GitHub Actions 云构建(Termux 写代码 → `git push` → 云端出 APK) |
| 版本基线 | AGP 8.7.3 · Gradle 8.9 · Kotlin 2.0.21 · KSP 2.0.21-1.0.28 |
| SDK | minSdk 30 (Android 11+) · compileSdk/targetSdk 35 |
| 包名 | `com.termux.manager` |
| APK 产物 | CI artifact `app-debug`,下载到 `/storage/emulated/0/Download/app-debug.apk` |
| Termux Home 访问 | 本机 SFTP(`127.0.0.1:8022`,密码认证)— 计划用于 M4 |

---

## 2. 进度总览

| 里程碑 | 内容 | 状态 |
|---|---|---|
| 环境准备 | 选型、git/gh 配置、CI 链路 | ✅ 完成 |
| **M0** | 项目脚手架 + GitHub Actions CI | ✅ 完成并验证 |
| **M1** | SAF 授权 + 工作区浏览 + `FileBackend` 抽象 | ✅ 完成并验证 |
| M2 | 多存储区域 + 受保护根 + 可见性规则 | ⬜ 未开始(下一步) |
| M3 | 文件操作 + 底部无障碍布局 | ⬜ |
| M4 | Termux Home(SFTP 后端) | ⬜ |
| M5 | 回收站 | ⬜ |
| M6 | 打磨(新建项目 / 设置 / 软链接引导 / 无障碍) | ⬜ |

**当前状态**:M1 已通过真机验证(授权流程顺畅,文件夹与文件正常显示)。
**下一步**:M2 — 见 §6。

---

## 3. 环境与工具配置

### 已确认的环境事实
- Termux(Android 16 / API 36,aarch64)**无安卓构建工具链**(无 JDK/Gradle/SDK),但有 git 2.54、node 22、python 3.13。
- 因构建走 CI,Termux 端**无需**安装构建工具,只负责编辑 + 推送。
- Termux 仓库可装 openjdk-17 / gradle / aapt2 / d8 / apksigner / kotlin(本项目未使用,留作本地构建后备方案)。

### 已配置
- **git 身份**:`ze2222` / `davidblack2854@gmail.com`
- **gh CLI**:已登录 `ze2222`,HTTPS 协议,凭据助手已接管 → `git push` 免密。
  - Token scopes 含 `repo` + **`workflow`**(后者是推送 `.github/workflows/` 所必需)。

### 待配置(留到 M4 测试 Termux Home 时)
Termux 已装 openssh 10.3p1、SFTP 子系统已启用、默认端口 8022。测试时执行:
```bash
passwd     # 设登录密码
sshd       # 启动服务(127.0.0.1:8022)
whoami     # 输出用作 App 登录用户名(本机为 u0_a572)
```

### ⚠️ 环境注意事项(踩坑备忘)
1. **`grep` 在本环境崩溃**:报 `-G: error while loading shared libraries`。脚本里**避免使用 grep**,改用不带管道的命令或 `awk`、`gh --jq`。
2. **共享存储 git「dubious ownership」**:`/storage/emulated/0` 属主是 `media_rw`,Termux 用户是 `u0_a572`,git 安全机制会拦截。已通过 `safe.directory` 例外解决(见 M0)。
3. **共享存储文件权限位不可靠**:仓库已设 `core.filemode false`,避免 git 误报所有文件 mode 变化。

---

## 4. 开发日志

### 规划阶段

**关键决策(与用户确认)**:

| 维度 | 选择 | 理由 |
|---|---|---|
| 构建 | GitHub Actions 云构建 | Termux 无构建链,云端最稳、零本地配置 |
| 语言 | Kotlin | 安卓现代主流 |
| UI | View + XML + Material 3 | 轻量、构建快、底部操作易实现 |
| minSdk | 30 (Android 11+) | 分区存储默认,SAF 代码最精简 |

**Termux Home 访问方案的演进(重要)**:
- 初判:独立 SAF 应用**无法**访问 `/data/data/com.termux/files/home`(其他应用私有目录,非 root 不可达),故 `plan.md` 的「Home 作为受保护根 + Home↔公共双向移动」一度判定不可行。
- 用户提示「本地端口」办法 → 确定方案:**App 内置 SFTP 客户端连本机 `127.0.0.1:8022`**,由 Termux 的 sshd 代表 App 读写 `$HOME`。优雅绕开私有目录限制,且让 Home 联动重新成立。
- 落地设计:文件操作抽象为统一 `FileBackend`,`SafBackend`(公共存储)与 `SftpBackend`(Termux Home)并存,跨后端移动 = 读源流→写目标流。

---

### M0 — 项目脚手架 + GitHub Actions CI ✅

**目标**:验证「写代码 → 云构建 → 出可安装 APK」的完整链路。

**做了什么**:
- 创建 Gradle(Kotlin DSL)项目:`settings.gradle.kts`、根 `build.gradle.kts`、`gradle.properties`、`gradle/libs.versions.toml`(版本目录,集中管理所有依赖)、`app/build.gradle.kts`、`.gitignore`。
- 最小可运行 App:`MainActivity`(显示 "Termux-Manager" 文字)+ Material 3 主题 + `activity_main.xml`。
- CI:`.github/workflows/build.yml` — JDK 17 + `android-actions/setup-android` + `sdkmanager` 装 platform-35/build-tools + `gradle/actions/setup-gradle`(gradle 8.9)+ `gradle assembleDebug` + 上传 `app-debug` artifact。
  - **设计取舍**:不提交 gradle wrapper(jar 是二进制无法文本生成),改由 `setup-gradle` 提供指定版本的 gradle 直接构建。CI-only 项目下最简。

**遇到的问题 #1:git「dubious ownership」**
```
fatal: detected dubious ownership in repository at '/storage/emulated/0/app-develop'
```
- 原因:仓库目录属主(`media_rw`)与运行 git 的 Termux 用户(`u0_a572`)不一致。
- **解决**:
  ```bash
  git config --global --add safe.directory /storage/emulated/0/app-develop
  ```
  之后 `git init` → `add` → `commit` → `git branch -M main` → `gh repo create termux-manager --public --source=. --remote=origin --push` 一次成功。

**结果**:
- 仓库创建:https://github.com/ze2222/termux-manager
- CI 成功(2m55s),APK 11.6 MB。
- 真机安装:显示 "Termux-Manager" 空白界面 ✅(用户确认)。
- 无害警告:`Node.js 20 is deprecated`(GitHub 自动用 Node 24 跑 action,不影响构建;以后可选升级 action 版本)。

---

### M1 — SAF 授权 + 工作区浏览 + FileBackend 抽象 ✅

**目标**:App 能授权内部存储、定位/创建工作区、浏览文件。

**做了什么**:
- 启用依赖:`recyclerview`、`lifecycle-viewmodel-ktx`、`lifecycle-runtime-ktx`、`kotlinx-coroutines-android`。
- 新增数据层 `data/fs/`:
  - `NodeRef`(节点引用,当前包 SAF document uri)、`FileEntry`(列表项模型)、`FileBackend`(统一接口)。
  - `SafBackend`:
    - `list()` 用 **`ContentResolver.query` 直接查 children**(`buildChildDocumentsUriUsingTree`),避免 `DocumentFile.listFiles()` 的逐项开销;目录在前、按名排序。
    - `hasPrimaryRootAccess()` 读 `persistedUriPermissions` 判断是否已授权 primary 根。
    - `ensureWorkspace()` 定位/创建 `Documents/Termux-Manager`。
- UI 层:`BrowserViewModel`(目录栈 + LiveData)、`FileAdapter`(DiffUtil)、重写 `MainActivity`(授权路由 + 浏览)、`activity_main.xml`(授权引导 + 列表两态)、`item_file.xml`、`ic_folder.xml`/`ic_file.xml`(vector 图标)。
- 授权:`ACTION_OPEN_DOCUMENT_TREE` 引导授权 primary 根 → 校验 `getTreeDocumentId == "primary:"` → `takePersistableUriPermission` 持久化(复用系统持久化,不自建存储)。

**遇到的问题 #2:Kotlin 编译失败**
```
e: MainActivity.kt:11:26 Unresolved reference 'viewModels'.
e: MainActivity.kt:22:41 Unresolved reference 'viewModels'.
> Task :app:compileDebugKotlin FAILED
```
- 原因:`by viewModels()` 与 `onBackPressedDispatcher.addCallback{}` 这两个扩展来自 **`androidx.activity:activity-ktx`**,而 `appcompat` 只传递了 activity 核心库(不含 KTX 扩展)。
- **排查方式**:`gh run view <id> --log-failed` 拿失败步骤日志,定位到 `e:` 开头的 Kotlin 错误行。
- **解决**:在版本目录加 `activity = "1.9.3"` + `androidx-activity-ktx`,并在 `app/build.gradle.kts` 加 `implementation(libs.androidx.activity.ktx)`。

**结果**:
- CI 成功(2m17s),APK 11.9 MB。
- 真机验证:授权流程顺畅(Android 16),文件夹与文件正常显示 ✅(用户确认)。

**M1 已知限制**(留待后续里程碑):
- 列表无下拉刷新(仅进入目录时加载一次),外部改动需重进目录才可见。
- 点击文件暂只 Toast 文件名(打开/操作在 M3)。

---

### CI 维护:升级 Actions 到 Node 24(消除 deprecation 警告)

**现象**:每次 CI 都出现 `Node.js 20 is deprecated` 警告(GitHub 已强制用 Node 24 运行,不影响构建,仅提示)。
**原因**:所用 action 版本的 `action.yml` 声明了 `runs.using: node20`。
**解决**:查各仓库最新 release 并升级到 Node 24 版本——
```bash
gh api repos/<owner>/<repo>/releases/latest --jq .tag_name
```
checkout v4→**v7**、setup-java v4→**v5**、setup-android v3→**v4**、setup-gradle v4→**v6**、upload-artifact v4→**v7**。
**结果**:构建成功(1m7s),ANNOTATIONS 警告消失。

---

## 5. 当前代码结构

```
app-develop/
├── settings.gradle.kts            # rootProject + include(:app)
├── build.gradle.kts               # 顶层插件声明(apply false)
├── gradle.properties              # AndroidX / jvmargs / caching
├── gradle/libs.versions.toml      # 版本目录(全部依赖集中管理)
├── .github/workflows/build.yml    # CI:assembleDebug + 上传 APK
├── plan.md                        # 产品方案
├── DEVELOPMENT_PLAN.md            # 工程计划
├── DEVLOG.md                      # 本文档
└── app/
    ├── build.gradle.kts           # 模块配置 + 依赖(按里程碑逐步启用)
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/termux/manager/
        │   ├── MainActivity.kt            # 入口:授权路由 + 浏览
        │   ├── data/fs/                   # FileBackend / SafBackend / NodeRef / FileEntry
        │   └── ui/                        # BrowserViewModel / FileAdapter
        └── res/
            ├── layout/  activity_main.xml, item_file.xml
            ├── drawable/ ic_folder.xml, ic_file.xml
            └── values/  strings.xml, themes.xml
```

提交历史(main):M0 脚手架 → M1 功能(首次编译失败)→ M1 fix(补 activity-ktx)。

---

## 6. 下一步:M2(多存储区域 + 受保护根 + 可见性)

计划实现:
1. 顶层展示**存储区域列表**:工作区 + 各公共目录(Download/Documents/Pictures/DCIM/Movies/Music)+ 用户授权卷。
2. **受保护根**标记:根节点禁止删除/移动/重命名,内部文件可自由操作(`domain/ProtectionRules.kt`)。
3. **系统目录隐藏**:`Android/`、系统分区等不展示。
4. **可见性规则**(`domain/VisibilityRules.kt`):
   - 先剔除内部保留名单(`.trash`/`.termux-manager`/`.cache`/`.index`/`.thumbnails`/`.state` 等),任何情况都不可见。
   - 项目目录内部显示隐藏文件(`.git`/`.env` 等);公共目录默认隐藏点文件、设置可开启。
5. 顺带补「下拉刷新」改善 M1 的一次性加载体验。

实现要点:扩展 `SafBackend` 支持从 primary 根派生各公共目录的 `NodeRef`;新增区域列表 UI(可考虑此时引入 Fragment,或继续单 Activity 内切换)。

---

## 7. 常用命令(日常开发循环)

**核心循环**:编辑代码 → 提交推送 → 等 CI → 下载 APK → 真机安装。

```bash
DIR=/storage/emulated/0/app-develop
REPO=ze2222/termux-manager

# 1) 提交并推送(safe.directory 已配置,filemode 已关闭)
git -C "$DIR" add -A
git -C "$DIR" commit -m "feat: 你的说明"
git -C "$DIR" push

# 2) 查看最近一次 CI 运行 + 监视到结束
gh run list --repo "$REPO" --limit 1
RUN_ID=$(gh run list --repo "$REPO" --limit 1 --json databaseId --jq '.[0].databaseId')
gh run watch "$RUN_ID" --repo "$REPO" --exit-status

# 3) 构建失败时看失败日志(注意:本环境 grep 不可用)
gh run view "$RUN_ID" --repo "$REPO" --log-failed

# 4) 成功后下载 APK 到 Download(覆盖旧的)
rm -f /storage/emulated/0/Download/app-debug.apk
gh run download "$RUN_ID" -n app-debug --repo "$REPO" -D /storage/emulated/0/Download
```

**Termux Home / SFTP(M4 测试时)**:`passwd` 设密码 → `sshd` 启动 → App 连 `127.0.0.1:8022`(用户名 = `whoami` 输出)。
