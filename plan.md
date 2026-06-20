方案名称：
Termux-Manager

定位：
Termux-Manager 是面向 Termux 开发环境的项目工作区与文件管理器。

它用于统一管理 Termux 中使用的项目目录，提供项目创建、文件浏览、文件移动、公共存储访问、Home 目录联动和安全保护能力。

Claude Code、Codex、Gemini CLI、Git、Node.js、Python、Rust、Go 等 CLI 工具均运行在 Termux 环境中。Termux-Manager 不直接替代这些工具，而是为它们提供统一、清晰、安全的项目目录管理方式。

核心目标：
为 Termux 用户提供一个固定、安全、清晰的项目根目录。

默认项目根目录：
Documents/Termux-Manager

完整路径示例：
/storage/emulated/0/Documents/Termux-Manager

目录结构：
Documents/
└── Termux-Manager/
└── <用户项目目录>/

初始状态：
Termux-Manager 首次创建时，只创建根目录，不默认创建任何项目目录。

初始结构：
Documents/
└── Termux-Manager/

用户新建项目后：

Documents/
└── Termux-Manager/
└── my-app/

项目目录规则：
Termux-Manager 根目录下由用户创建的一级普通目录默认视为项目目录。

例如：
Documents/Termux-Manager/my-app
Documents/Termux-Manager/test-cli
Documents/Termux-Manager/notes-tool

不额外创建 projects 目录，因为 Termux-Manager 本身就是项目工作区根目录。

设计原则：
一个项目 = 一个独立目录
一个项目 = 一个 Git 仓库
一个项目 = 一个 CLI 工作区

Home 映射：
可在 Termux Home 中创建快捷入口：

~/projects -> /storage/emulated/0/Documents/Termux-Manager

这样 CLI 中可以直接使用：

cd ~/projects/my-app
cd ~/projects/test-cli

管理范围：


Termux Home

Documents/Termux-Manager

Download

Documents

Pictures

DCIM

Movies

Music

用户通过 SAF 授权的其他目录



权限策略：


使用 SAF 目录授权

不 Root

默认不申请全盘权限

不访问系统分区

不访问应用私有目录

支持用户按需授权基本公共目录



允许访问的基本目录：


Download

Documents

Pictures

DCIM

Movies

Music



默认隐藏和禁止访问：


Android/

Android/data/

Android/obb/

/system

/vendor

/product

/apex

/data/data

/data/user

其他系统目录和应用私有目录



受保护根目录：
以下目录作为根节点显示，但禁止删除、移动、重命名：


Termux Home

Documents/Termux-Manager

Download

Documents

Pictures

DCIM

Movies

Music

用户授权目录根节点



受保护根目录禁止操作：


删除

移动

重命名



允许操作：
受保护根目录内部的文件和子目录允许自由操作。

支持：


查看

复制

移动

重命名

删除

新建文件夹



允许示例：
Download/demo.zip -> Documents/Termux-Manager/my-app/demo.zip
Documents/Termux-Manager/my-app -> Download/my-app-backup
Pictures/Screenshots -> Documents/Termux-Manager/my-app/assets
Documents/Termux-Manager/api-server -> Home/api-server

禁止示例：
Download -> Home
Documents -> Download
Pictures -> Documents
Termux Home -> Download
Documents/Termux-Manager -> Download

文件流转策略：
支持 Home 与公共存储之间双向移动文件。

支持：


公共存储 -> Home

Home -> 公共存储

公共目录 -> 公共目录

Termux-Manager 项目目录 -> Home

Home -> Termux-Manager 项目目录



公共目录角色：
公共目录不是系统目录，而是用户文件来源和目标位置。

例如：


Download 用于导入压缩包、安装包、素材

Pictures/DCIM 用于导入图片素材

Documents 用于存放文档资料

Termux-Manager 用于统一存放项目



显示策略：
Termux Home：
默认显示隐藏文件。

Documents/Termux-Manager：
默认显示用户项目目录和用户文件。

Documents/Termux-Manager 中的开发项目内部：
允许显示项目自身的隐藏文件。

例如：


.git

.env

.config

.gitignore

README.md

package.json



公共存储普通目录：
默认隐藏隐藏文件，可在设置中开启显示。

本应用内部文件隐藏规则：
Termux-Manager 自身产生的所有内部文件，必须对用户隐藏，不在普通文件列表中公开显示。

包括但不限于：


缓存文件

索引文件

数据库文件

缩略图缓存

扫描记录

临时状态文件

内部配置文件

回收站状态文件

操作日志文件



处理原则：


优先存放在 Android 应用私有目录中。

不应污染 Documents/Termux-Manager 项目根目录。

如因功能需要必须在项目根目录下落盘，只能集中放入隐藏内部目录。

该隐藏内部目录在 Termux-Manager 文件列表中必须完全不可见。

不提供普通用户开关来显示这些内部文件。

不参与普通搜索结果。

不参与项目列表识别。

不允许用户在普通文件管理视图中选中、删除、移动或重命名这些内部文件。



用户视图规则：
Termux-Manager 根目录中，用户只能看到自己创建的项目目录和用户文件。

用户不应看到：


.termux-manager/

.cache/

.index/

.thumbnails/

.state/

.trash/

.termux-manager.json

其他本应用内部文件



因此，用户视图应表现为：

Documents/
└── Termux-Manager/
└── <用户项目目录>/

而不是：

Documents/
└── Termux-Manager/
├── <用户项目目录>/
└── .termux-manager/

删除策略：


删除文件前二次确认

删除目录前二次确认

批量删除前二次确认

优先使用隐藏回收站机制

支持恢复

支持彻底删除



移动策略：


允许移动文件

允许移动子目录

禁止移动受保护根目录

跨区域移动时提示用户确认

从公共目录移动文件时提示源文件将被移除



覆盖策略：
如果目标位置已有同名文件，提供：


覆盖

跳过

重命名

取消



项目创建策略：
新建项目时，默认创建在：

Documents/Termux-Manager/

例如：
Documents/Termux-Manager/my-app


无障碍与残疾人友好设计：
考虑到残疾人用户、手部操作不便用户和单手操作用户，界面应避免将主要操作按钮放在屏幕顶部。

常用操作应尽量放在屏幕底部或拇指容易触达的位置，例如底部工具栏、底部浮层、底部操作面板、长按菜单等。

危险操作仍需二次确认，但确认按钮也应保持易触达，避免强迫用户频繁点击屏幕顶部。

命名建议：
App 名称：
Termux Manager

默认根目录：
Documents/Termux-Manager

显示名称：
Termux-Manager

核心一句话：
Termux-Manager 管理的是 Termux 项目工作区，不替代 CLI 工具；保护根目录，不限制文件流动；隐藏系统目录，只管理用户文件；项目统一放在 Documents/Termux-Manager 下；本应用内部文件必须隐藏，不向用户公开。