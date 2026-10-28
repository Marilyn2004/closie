# Closie Demo v1.0 — 发布准备验收报告

日期：2026-10-28。结论：**公开演示版本已通过本地发布检查，可以进入部署审批；尚未云端部署。**

## 1. Git 状态与范围

- 分支：`closie-concept-mvp`。
- 本轮基线：`8143fa9`，已重新检查，未直接采用旧报告作为当前验证。
- 实现提交：`03c3af0` — `feat: prepare safe pre-generated Closie public demo`。
- 本报告作为后续文档提交保存，最终 HEAD 可用 `git log -2 --oneline` 查看。
- 未切换/合并主分支、未推送、未公开仓库、未购买服务、未修改生产服务或账单。
- 原有 `docs/VERIFICATION.md` 修改，以及未跟踪的 `docs/EBAY_SETUP.md`、`docs/RAILWAY_DEPLOYMENT.md` 保持原状，没有混入本轮提交。

## 2. 已实现的公开演示体验

**VERIFIED：**

- Discover：七张统一白底原创预生成概念图；可点击详情、类别筛选、五个风格集合、基于显式风格/颜色/收藏的基础排序与重置。
- Find：按衣物类型、颜色和少量风格词检索已有概念。支持选中方向后的细化，例如蓝色开衫改为搜索粉色开衫；不会创建新图或冒用旧图。无法匹配时给出已有素材的搜索建议。
- Style：两套预设搭配、围绕选中概念生成规则式搭配板、主角锁定、替换现有单件、收藏和查看已保存搭配。
- 共享示例只读；修改预设搭配时复制到当前浏览器的私人搭配记录，不更改原作。
- Saved：概念与搭配均可收藏并刷新后重访。
- Style My Item：保留入口与说明，但公开版明确禁用上传、照片识别、品牌型号和产品链接查询；不模拟识别成功或精确商品匹配。
- 所有公开概念都标为预生成 AI inspiration，不展示虚构价格、库存、零售品牌或购买链接。

视觉身份、Discover → Find → Style 导航、React/Spring/PostgreSQL 架构保留。没有替换成新 dashboard，也没有引入新的主要产品功能。

## 3. 安全与 API 费用保护

`PublicDemoPolicy` 根据 `public-demo` profile 或显式公开模式属性生效。即使把公开属性设为 false，只要仍运行公开 profile，保护依旧生效。

- `PublicDemoFilter` 按允许列表放行必要读写，生成、娃娃、上传、私人图片、识别、零售/旧 AI、导入、预算/管理和调试入口均拒绝。
- `StudioJobs` 的入队和 worker，以及 OpenAI、Gemini、Gemini Vision、OpenAI 文字网关均有独立公开模式保护，不能只靠隐藏按钮或缺少 Key。
- 自动刷新目录在公开模式中也不会发起导入。
- 测试故意提供假密钥并开启生成/识别/文字开关，模型仍显示 disabled；直接 HTTP 请求仍返回 403。
- 公开请求体上限 16 KB；公开文档和版本存储有全局容量限制。API 响应不缓存；生产页面具备 CSP、nosniff、禁止嵌入和 referrer 限制。
- 前端进程和依赖安装不会继承 OpenAI/Gemini Key；公开容器没有配置这些 Key。

**本轮新增付费模型请求：0。** 历史一次 OpenAI 生图及估算 $0.00684 的证据仍在 `docs/OPENAI_IMAGES.md`；没有重复验证、生成编辑图或生成娃娃图。公开演示数据库核对：image spend rows=0，OwnedItem rows=0，迁移版本=8。

## 4. 素材与剩余产品边界

素材为既有六张原创内置工具概念图，加上之前成功验证的蓝色开衫。蓝色开衫从本地原始验证输出复制进静态资源；没有新模型请求。来源与 prompt 记录在 `docs/CONCEPT_ASSETS.md`。

已查看七件素材及桌面、手机和生产容器截图：衣物完整可见、白底、无人物/显著商标，风格一致。两套搭配由独立图片组成，不声称是新生成的 AI 拼图。

限制仍明确：只有上衣、下装、鞋、包四类；鞋和包各一件；没有连衣裙、外套或首饰素材。规则式检索/排序不等于完整语义搜索；预生成演示不等于完整实时 AI 产品。人类审美验收仍与自动化检查分开。

## 5. 数据库与图片持久化

- V1–V8 迁移保持原样，没有重写已应用迁移或添加破坏性迁移。
- 公开模式只允许专用 `closie_public_demo` 数据库，拒绝复用 `closie_studio`、`closie_demo` 或 `fashion_search`。现有私人/零售数据库没有被此演示迁移、替换或删除。
- 公开示例以幂等方式补齐，保留原 ID；集合标签属于添加的演示元数据。
- 收藏、偏好、私人搭配副本和版本保存在 PostgreSQL。匿名浏览器的 opaque capability 在 localStorage；没有账号或跨设备自动同步，清除浏览器存储会失去该会话访问能力。
- 公开图片直接打包在 JAR/Docker artifact 中，不依赖临时磁盘，不需要运行时上传卷。
- 本地验证了应用 restart，以及把应用换为新容器、保留原数据库后，概念 ID、收藏、搭配和七张图片均保留。
- 私人模式的 `.local/studio-assets` 与能力令牌不会自动迁移到云端；未来若托管私人模式，必须单独安排持久卷/对象存储、备份与访问策略。

## 6. 测试与构建结果

最终完整 Maven 回归：**152 项，0 failures、0 errors、0 skipped**。

实际浏览器用例：

- 公开演示 4 条通过。
- 私人 studio 5 条通过。
- 旧样例目录 3 条通过。
- 旧零售目录 2 条通过。

合计 14 条浏览器用例通过。另外对实际生产容器进行了浏览器检查：搜索蓝色开衫 → 搭配板 → 收藏 → Saved，页面错误为 0。

其他已验证结果：

- 前端 production build 通过。
- 完整多阶段 Docker 镜像构建通过；最终本地 image ID 前缀 `d1bf23999ee5`。
- 无模型 Key 的公开容器成功启动，数据库成功应用至 V8。
- 静态 JS、favicon、七张图片、CSP、付费/管理路由 403 和超大请求 413 均通过本地成品检查。
- 私人会话隔离、预设搭配复制、收藏、风格集合排序与重置、低帮鞋分类、空状态和细化有自动化覆盖。
- `bash -n dev`、Python/TOML 语法和 `git diff --check` 通过。
- 基线 Git 历史扫描覆盖 10 个提交 / 279 个唯一 blob，未发现已知密钥格式；仅 `.env.example` 被跟踪。当前新增源文件/文档检查也未发现凭据值。这是有限模式扫描，不是完整渗透测试或安全认证。

本机没有 Compose plugin，因此没有运行 Compose 命令；使用现有 Docker CLI 验证了等价的 web/database 网络、持久卷和应用替换。没有为验证安装新工具。远程 CI 和实际云端执行没有被声称为通过。

## 7. 本地查看与验证

本地成品容器：**http://127.0.0.1:8083**。

源代码开发预览：`./dev public-demo` → http://127.0.0.1:5176，后端 8082；`./dev stop-public` 仅停止该预览并保留数据库。

可重复检查成品：

```sh
python3 scripts/check_public_demo.py
# 仅重启/替换本轮本地测试应用，保留数据库后：
python3 scripts/check_public_demo.py --after-restart
node scripts/check_public_demo_browser.cjs
```

脚本只允许显式 localhost 目标，不调用模型。初次检查会创建合成 QA 收藏/搭配，恢复检查复用忽略目录中的会话记录。

本轮本地容器名称为 `closie-demo-release-db` 与 `closie-demo-release-app-v3`；旧的本轮测试应用容器已经停止。若不再查看，可只停止这两个容器；不要删除其 PostgreSQL 卷。它们与既有私人数据库容器及线上服务无关。

## 8. 推荐部署方案

推荐一个 Railway Docker Web 服务同时提供 React 和 Spring API，加专用持久 PostgreSQL/pgvector；公开图片随 artifact 发布，不另设图片服务。这样保留同源 `/api` 路由，省去分离前端带来的 CORS 和运行时图片配置。

Railway 官方当前 Hobby 标价 $5/月，包含 $5 用量，超额另计；Free 的 $1 credit 不应假设足够持续运行 Java 和数据库。实际额度、资源限制和月费需用户审批，不能从本地测试推算保证免费。可考虑 Neon 作为支持 pgvector 的数据库替代方案。详见 [官方 Railway 价格](https://docs.railway.com/pricing/plans) 与 [Neon pgvector](https://neon.com/docs/extensions/pgvector)。

未登录或修改任何托管/账单账户，未购买资源。API 的 $3 项目额度与托管费用是不同预算。

## 9. 部署准备文件

- `Dockerfile`：同源 artifact、favicon、非 root 运行，默认 public-demo。
- `railway.public-demo.toml`：未来新服务明确选择的 Docker builder、healthcheck 和有限重启配置。
- `ops/public-demo.env.example`：仅 profile、DB_URL、DB_USERNAME、DB_PASSWORD；平台提供 PORT，不包含模型/import Key。
- `docker-compose.public-demo.yml`：本地成品演练模板，数据库卷持久、DB 端口不公开。
- `docs/PUBLIC_DEMO_DEPLOYMENT.md`：新服务/专用库、pgvector 权限、备份、HTTPS、直接路由检查、重启持久化和回滚步骤。
- `README.md`、`AGENTS.md`、`PROGRESS.md` 已更新。

## 10. 审批前的剩余事项与是否可部署

**可以批准部署这个有明确限制、零模型调用的公开演示版本。** 本地技术验收已完成，不需要先新增产品功能或继续花模型额度。

正式操作前仍需：

1. 用户确认托管服务、可用额度及月费上限；授权创建独立公开演示资源，不能复用/覆盖现有生产服务。
2. 配置专用数据库连接、pgvector 权限与备份策略。
3. 批准后验证真实 HTTPS、云端资源/日志、重启与重新部署，以及远程 CI。
4. 如要公开源代码仓库，另行决定许可证并批准发布；本轮没有发布仓库。

不可声称：已上线、真实云环境验证完成、完整实时 AI 功能/审美质量通过、商品覆盖全面、真实零售合作或生产用户指标。
