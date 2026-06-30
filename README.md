# 思维札记 (Thought Vault)

极简思考记录工具 —— 只管打字保存，AI 自动分类润色，生成日/周/月/季/年 Markdown 总结。

## 项目亮点与创新

###  数据主权 · 你的思考只属于你

所有数据存储在**你自己的群晖 NAS** 上，不经任何第三方云服务。Android APP 通过 WebDAV 直连 NAS 读写，Web 前端通过 Nginx 反代访问。代码完全开源，数据完全私有。

###  AI 全自动知识管道

```
口语化碎片输入 → DeepSeek 自动分类（13 类） + 文字润色 → 每日点评 → 周/月/季/年报逐层聚合
```

这是本项目最核心的创新：**你只管记，AI 帮你理。** 不同于传统笔记工具需要手动整理标签、文件夹、目录，思维札记将杂乱的口语记录全自动转化为结构化的知识体系。每天早上醒来，昨晚的零散想法已经被整理成一篇整洁的日报，附有温和的每日点评。

###  多端统一 · 一个数据源

| 终端 | 方案 | 特色 |
|---|---|---|
| Android APP | Kotlin + Compose，原生体验 | 离线缓存（Room）、硬件加密存储、Material 3 |
| Web 前端 | Preact + TypeScript + Vite，3KB 核心 | 零安装、响应式、PWA 可安装到桌面 |
| 数据层 | 统一 WebDAV | 双端读写同一份数据，无需同步协议 |

###  自建推送通道 · 脱离 GMS 依赖

摒弃 Firebase Cloud Messaging（国内不可用），使用 **自托管 Mosquitto MQTT Broker** 实现：

- Node.js 定时调度器 → MQTT → Android 长连接实时推送，提醒你记得记录
- MQTT 通过 Nginx WSS 加密代理，复用已有 SSL 证书，零额外端口暴露
- 安卓端指数退避重连，不依赖 Google Play Services

###  成本近乎为零

| 资源 | 方案 | 月成本 |
|---|---|---|
| AI 推理 | DeepSeek API | ~$0.07 |
| 服务器 | 自有 NAS + Docker | $0 |
| 数据库 | 文件系统（Markdown） | $0 |
| 推送 | 自建 Mosquitto | $0 |
| **合计** | | **~$0.07/月** |

不用云数据库、不用云函数、不用 SaaS 推送服务——所有基础设施自托管，月费不到一毛钱。

###  安全与隐私设计

- **Android**：EncryptedSharedPreferences（AES256 + Android Keystore 硬件加密）存储密码
- **传输**：全链路 HTTPS + TLS 1.2/1.3，HTTP 严格转发
- **NAS**：密码仅存在用户自己的设备上，服务端不存任何凭据
- **MQTT**：仅 Docker 内网可达，不映射公网端口
- **隔离**：提醒模块（普通 SharedPreferences）与凭据存储（加密 SharedPreferences）物理隔离

###  Markdown 即数据库

放弃 SQLite/MySQL/MongoDB，所有数据以 **Markdown 文件** 存储在 NAS 上。带来的好处：

- 人类可读，任何文本编辑器都能打开
- Git 友好，可以版本控制你的思考历史
- 零依赖，不需要数据库备份策略——NAS 快照即备份
- 格式开放，永远不会被锁定在某个专有格式里

###  AI 的第二大脑 · 为 Agent 提供个人知识库

所有日报、周报、月报、年报均输出为结构化 Markdown 文件，可直接被 AI Agent 索引和检索：

- 你的 Claude Code、Copilot、Cursor 等 AI 编程助手可以读取这些 MD 文件，**深度了解你的思维方式、知识结构和工作习惯**
- 你可以对 Agent 说："根据我上个月的思考记录，帮我列出目前最值得深入的方向" —— Agent 直接搜索你的总结文件，给出基于你真实思考的回答
- 日积月累，这些 MD 文件将成为**你的私有 RAG 知识库**，任何 LLM 都可以用作文本上下文，帮助你回溯想法、发现关联、激发新灵感

###  Todo 自动提取 · 把想法落地为行动

AI 日报脚本不仅分类润色，还会**自动从你的记录中提取待办事项**：

- 识别"想做""计划""准备做"等意图表达，区分**近期待办**（短期可完成）和**长期计划**（需要持续投入）
- 自动写入 `todos/tasks.md`，按完成状态分类，APP 端同步展示
- 帮你形成「记录 → 整理 → 待办 → 执行 → 回顾」的完整闭环
- 每周/月/季/年报中会回顾待办的推进情况，让你对自己的成长轨迹一目了然

---

## 工作原理

```
你打字（口语）→ 移动端/网页保存到 NAS → 凌晨 DeepSeek AI 自动分类+润色 → 生成整洁日报 → 周报 → 月报 → 季报 → 年报
```

## 项目结构

```
sanitized/
├── thought-vault/              # Android APP（Kotlin + Jetpack Compose）
│   └── app/src/main/java/com/example/thoughtvault/
│       ├── data/               # 数据层（WebDAV 客户端、Room 缓存、加密存储）
│       ├── domain/             # 领域层（Entry/Todo 模型、Use Cases）
│       ├── presentation/       # 界面层（Home/History/Detail/Settings/Summary/Todo）
│       ├── notification/       # 本地提醒（WorkManager + AlarmManager）
│       ├── push/               # MQTT 推送（Mosquitto 长连接 + 权限引导）
│       └── di/                 # Hilt 依赖注入
├── thought-vault-web/          # Web 前端 + Node.js 后端（Preact + TypeScript + Vite）
│   ├── src/                    # 前端页面（Home/History/Detail/Settings/Summary/Todo）
│   ├── server/                 # MQTT 定时提醒调度器
│   ├── mosquitto/              # MQTT Broker 配置
│   └── proxy/                  # WebDAV CORS 代理（PHP）
├── nas-scripts/                # NAS 端 Python 脚本
│   ├── common.py               # 共享工具 + DeepSeek API 调用
│   ├── daily_summary.py        # 日报（分类+润色+每日点评+Todo 提取）
│   ├── weekly_summary.py       # 周报
│   ├── monthly_summary.py      # 月报
│   ├── quarterly_summary.py    # 季报
│   ├── yearly_summary.py       # 年报
│   ├── categories.json         # 13 分类定义
│   └── run_all.sh              # 历史数据回填工具
└── README.md
```

---

## 🔧 部署前必读 — 占位符替换

> 本项目已脱敏，所有真实值已替换为 `<your-xxx>` 自描述占位符。部署时需全局搜索替换为你的实际信息。

| 占位符 | 说明 | 出现在哪些文件 |
|---|---|---|
| `<your-nas-domain>` | NAS 域名 | `SettingsDataStore.kt`、`PushConnectionManager.kt`、`network_security_config.xml`、`settings-store.ts` |
| `<your-nas-lan-ip>` | NAS 局域网 IP | `nginx-default.conf` |
| `<your-nas-username>` | NAS 用户名 | `proxy.php`、`common.py` |
| `<your-nas-thoughts-dir>` | NAS 上 thoughts 根目录 | `common.py` |
| `<your-nas-webdav-https-port>` | WebDAV HTTPS 端口 | `SettingsDataStore.kt`、`nginx-default.conf` |
| `<your-nas-webdav-http-port>` | WebDAV HTTP 端口 | `proxy.php`、`types.ts`（注释） |
| `<your-mqtt-wss-port>` | MQTT WebSocket 端口 | `PushConnectionManager.kt` |
| `<your-host-http-port>` | Docker 主机 HTTP 映射端口 | `docker-compose.yml` |
| `<your-host-https-port>` | Docker 主机 HTTPS 映射端口 | `docker-compose.yml` |
| `<your-apk-output-dir>` | APK 构建输出目录 | `app/build.gradle.kts` |

> 提示：在项目根目录执行 `grep -r "<your-" --include="*.kt" --include="*.py" --include="*.ts" --include="*.xml" --include="*.php" --include="*.yml" --include="*.conf" --include="*.kts" .` 可列出所有待替换的占位符。

---

## 快速开始

### 前置条件

- **Android Studio** Hedgehog (2024.1+) 或更新版本
- **Node.js** 22+（Web 前端 + 提醒调度器）
- **Docker** + Docker Compose（Web 前端部署）
- **群晖 NAS** 已安装 WebDAV Server 套件
- **DeepSeek API Key**（[platform.deepseek.com](https://platform.deepseek.com)）

---

### 1. Android APP

```bash
cd thought-vault
./gradlew assembleRelease
# APK 输出至 build/outputs/apk/release/（或你在 build.gradle.kts 中配置的目录）
```

首次启动 → 设置页面 → 填入 NAS 域名、端口、用户名和密码。

---

### 2. Web 前端（Docker 部署）

```bash
cd thought-vault-web

# 1. 放置 SSL 证书
mkdir -p ssl
cp /path/to/fullchain.pem ssl/
cp /path/to/privkey.pem ssl/

# 2. 启动所有服务（Nginx + Mosquitto + 提醒调度器）
docker compose up -d

# 3. 确认运行状态
docker compose ps
```

**包含的服务：**
- **Nginx** — HTTPS 静态文件服务器 + `/api/` 反向代理到 NAS WebDAV + `/mqtt` WebSocket 代理
- **Mosquitto** — MQTT Broker（仅 Docker 内网可达，不暴露公网）
- **reminder-scheduler** — 每天 11:30 / 17:30 / 21:30 推送思考提醒

若需 WebDAV CORS 代理（用于 Web Station），将 `proxy/proxy.php` 放到群晖 Web Station 站点根目录。

---

### 3. NAS Python 脚本

```bash
# SSH 到群晖
ssh admin@<your-nas-ip>

# 创建目录结构
mkdir -p <your-nas-thoughts-dir>/{config,scripts,raw,daily,weekly,monthly,quarterly,yearly,.sync}

# 上传脚本
scp nas-scripts/*.py nas-scripts/*.json nas-scripts/*.sh \
    admin@<your-nas-ip>:<your-nas-thoughts-dir>/scripts/

# 配置 API Key
echo "sk-your-deepseek-api-key" > <your-nas-thoughts-dir>/config/api_key.txt
chmod 600 <your-nas-thoughts-dir>/config/api_key.txt

# 复制分类配置
cp <your-nas-thoughts-dir>/scripts/categories.json <your-nas-thoughts-dir>/config/

# 安装依赖
pip install openai
```

---

### 4. 定时任务（DSM 任务计划）

群晖 DSM → 控制面板 → 任务计划 → 新增 → 用户定义的脚本：

| 任务 | Cron | 命令 |
|---|---|---|
| 日报 | `17 1 * * *` | `python3 <your-nas-thoughts-dir>/scripts/daily_summary.py` |
| 周报 | `23 2 * * 1` | `python3 <your-nas-thoughts-dir>/scripts/weekly_summary.py` |
| 月报 | `37 3 1 * *` | `python3 <your-nas-thoughts-dir>/scripts/monthly_summary.py` |
| 季报 | `41 4 1 1,4,7,10 *` | `python3 <your-nas-thoughts-dir>/scripts/quarterly_summary.py` |
| 年报 | `53 5 1 1 *` | `python3 <your-nas-thoughts-dir>/scripts/yearly_summary.py` |

---

### 5. 测试验证

```bash
# 创建测试数据
mkdir -p <your-nas-thoughts-dir>/raw/$(date +%Y)/$(date +%m)
cat > <your-nas-thoughts-dir>/raw/$(date +%Y)/$(date +%m)/$(date +%Y-%m-%d).md << 'EOF'
# $(date +%Y-%m-%d) 原始记录

---

## 09:15

今天早上想到一个idea就是把平时的想法都记下来然后让AI帮我整理感觉会很方便

## 14:30

下午跟小王开会讨论了那个新功能的设计方案 他觉得我的想法不错 下周开始开发
EOF

# 运行日报
python3 <your-nas-thoughts-dir>/scripts/daily_summary.py $(date +%Y-%m-%d)

# 查看输出
cat <your-nas-thoughts-dir>/daily/$(date +%Y)/$(date +%m)/$(date +%Y-%m-%d).md
```

---

## 技术栈

### Android APP
- Kotlin 2.0 + Jetpack Compose + Material 3
- OkHttp（WebDAV 客户端）
- Room（本地缓存）
- Hilt（依赖注入）
- EncryptedSharedPreferences（AES256 硬件加密存储）
- Eclipse Paho（MQTT 客户端）
- WorkManager（后台提醒调度）

### Web 前端
- Preact + TypeScript + Vite
- Preact Signals（状态管理）
- Docker + Nginx + Mosquitto
- Node.js（定时提醒调度器）

### NAS 脚本
- Python 3
- DeepSeek API（OpenAI 兼容格式）
- DSM Task Scheduler

---

## 费用估算

| 项目 | 估算 |
|---|---|
| DeepSeek API（日报 ~10 条/天） | ~$0.002/天 |
| 周/月/季/年报 AI 综述 | ~$0.01/月 |
| **月费总计** | **~$0.07** |

---

## 许可

MIT
