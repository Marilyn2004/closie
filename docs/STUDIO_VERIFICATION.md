# Closie V1.1 — 本轮交付与验证报告

验证日期：2026-10-27。仓库：`/Users/marilyn/Documents/ChatGPT/时尚谷/closie-v2`。

## 1. 分支与提交

- 开发分支：`closie-concept-mvp`，基于 `2c5c5c1`。
- 功能提交：`053d309` — `feat: add local Closie concept studio and unified item styling`。
- 本报告随后作为独立文档提交保存；完整提交号可运行 `git log -2 --oneline` 查看。
- 未合并主分支、未推送、未部署、未修改 Railway 或线上数据库。
- 开始前已存在的 `docs/VERIFICATION.md` 修改，以及未跟踪的 `docs/EBAY_SETUP.md`、`docs/RAILWAY_DEPLOYMENT.md` 保持原状，没有混入本轮提交。旧 README 保存在 `docs/RETAIL_README_HISTORICAL.md`，开始前的文档快照还在忽略目录 `.local/pre-v11-docs/`。

## 2. 用户流程：已验证与边界

**VERIFIED — 本地真实应用流程：**

- Discover 展示六张统一白底原创概念图，分类、显式风格/颜色偏好排序、收藏与重置可用；刷新首页不触发模型生成。
- Find 把英文请求变成多个经过校验的设计方向，精确设计可复用概念；新设计明确显示 Design brief，不冒用旧图。限定词表支持颜色、版型、领口、细节、否定偏好和部分场合。
- 概念编辑有持久 ID 与版本历史。首次编辑共享原作时，会保留原图版本，再追加私人设计版本；能查看、还原、删除。后续编辑传递选中的版本和原图引用。
- Style This 使用独立单品资产组成固定布局搭配板，主角不可被替换操作误改；单件替换保留其他单品；搭配板与历史持久保存。
- Style My Item 的照片、品牌型号、产品链接三种入口共用识别 → 用户确认 → OwnedItem → 同一搭配引擎。没有精确匹配也可确认后继续。
- 私人照片校验、去元数据、访问隔离和删除可用；未关联到衣服的照片也有清理入口。用户原图保留为主角图，不拿生成近似图冒充原衣服。
- Saved / My Style 按当前匿名浏览器能力令牌保存到数据库；不是跨设备账号系统。

**PARTIALLY IMPLEMENTED — 接口与控制流程存在，但真实模型未验证：**

- 异步服装出图、参考图编辑、重新生成、任务轮询、取消、持久失败状态和显式重试；数据库状态转换与失败保留旧版本已在测试替身下验证。
- Doll Preview 只有明确点击才创建任务，标为近似图；没有展示假冒实时结果的固定娃娃图。
- 可选 OpenAI 结构化设计描述增强与 Gemini 照片理解；默认关闭，照片还需单次勾选同意。模型建议与用户确认、外部商品证据分开保存。
- 品牌/型号与 URL 查找局限于当前合规目录的一页候选；不代表完整互联网商品搜索。

**NOT IMPLEMENTED / NOT VERIFIED：**

- 实际账号下的实时图片、照片识别、设计描述模型质量，以及二十条真实请求的人类审美评估。
- 广泛多商户检索、语义向量检索、完整自然语言否定/复杂约束推理、跨设备登录及云端持久图片存储。
- 概念图没有保证能买到的对应商品、真实价格或库存，因此不能展示虚构购物预算计算或购买链接。

## 3. 前端与后端

- 保留 React / Vite 与 Java 21 / Spring Boot / PostgreSQL 架构。
- `frontend/src/studio/StudioApp.jsx`、`api.js`、`studio.css`：新主流程、原图/设计版本、搭配板、统一个人单品确认、隐私图片与状态提示。
- `frontend/src/RetailApp.jsx`：保留此前零售页面；`frontend/src/main.jsx` 按 studio API 是否启用加载相应应用，`?retail=1` 可显式打开旧体验。
- `src/main/java/com/fashionsearch/studio/`：StyleLibrary、DesignBriefEngine、StudioService、StudioStore、StudioAssets、StudioJobs、GeminiImages、GeminiVision 和控制器。
- `StudioSafetyConfig.java` 与 `application-studio.yml`：独立本地库和 localhost 保护；`dev` 默认 studio，仍保留 demo / live。
- 未改动既有 eBay 适配器、商品事实、导入/删除通知处理。隔离概念库不会删除原有零售库数据。

## 4. 数据迁移

- V1–V5 保持不变。
- `V6__concept_studio.sql`：概念/个人单品/搭配/偏好文档、父子版本、私人资产、持久生成任务及模型使用记录。
- `V7__stable_studio_revision_order.sql`：版本追加顺序编号；保留旧 ID 和父引用，修复同一事务内时间戳相同导致的历史排序问题。
- Testcontainers 中从空库迁移通过；本地 `closie_studio` 从 V6 升级 V7 启动成功。没有针对生产库运行迁移。
- 私人文件在忽略目录 `.local/studio-assets`，数据库保存引用；备份恢复需要同时保存数据库、文件和匿名浏览器访问能力。

## 5. 图片与模型提供者

- 六张原创素材来自本轮内置 imagegen 工具预生成，具体 prompt 与来源在 `docs/CONCEPT_ASSETS.md`。已进行文件与视觉检查，但还没有用户审美批准。
- Gemini 采用 ImageGenerationProvider 抽象、服务端 key、固定官方源、模型可用性查询、参考图输入、超时、大小上限和输出文件验证。
- 本地 HTTP 测试验证了模型检查先于生成、图片请求格式、原图传递和权限失败处理。**这不是实际 Gemini API 成功的证明。**
- 实际本地状态：studio enabled；imageConfigured=false；visionConfigured=false；image model 为空；当前解释为 CURATED_RULES。
- 无应用内付费图片、OpenAI 描述或 Gemini 照片调用。需明确配置可用模型与调用预算后才能进行真实验证。
- 默认每日图片任务上限 12、每浏览器 6；文字调用预算默认为 0。失败/取消仍计入预约额度；中断任务不自动重放，避免无意重复付费。

## 6. OwnedItem 与匹配

- USER_PHOTO / BRAND_MODEL / PRODUCT_URL 保留来源。
- 精确已有商品 URL 记录可作为 EXACT_VERIFIED 证据；名称重合只能是 POSSIBLE_MATCH；其余为 NOT_FOUND。测试覆盖三种状态，使用的是合成且明确允许的测试目录记录，不是真实商户合作证明。
- 照片未启用识别时使用手动确认；启用时也保留确认步骤，AI 识别不等于真实商品验证。
- HTTPS URL 仅与已授权目录记录比较；没有任意抓取第三方网页。目录不包含目标商品时继续使用用户描述/照片进行搭配。
- 当前独立概念库不带现成零售库存，无法承诺找到 Aritzia 等品牌的具体 SKU。

## 7. 自动化验证

最终完整运行：**127 个 JUnit 测试项，0 failures、0 errors、0 skipped**，其中含三条浏览器测试启动项。

浏览器实际结果：

- 新版 studio：5 passed，覆盖收藏/编辑/还原/搭配、自然语言设计方向、三种个人单品入口、手机偏好排序、桌面截图。
- 原版零售目录：2 passed；该轮默认配置里额外发现的五条 studio 用例按条件跳过，由单独 studio 套件运行。
- 原版样例目录：3 passed。

合计 **10 条实际浏览器用例通过**。正向流程使用真实 Spring API 与临时 PostgreSQL，图片生成/照片模型成功分支使用明确的测试替身。额外覆盖分类回归、原图与版本保留、跨浏览器访问隔离、上传检查、锚点保护、任务去重/取消/失败、配额，以及 URL/名称匹配的可信度边界。

前端 Vite production build 通过；`git diff --check` 通过。新源代码与配置样例的凭据模式扫描未发现 key 字符串。没有安装新依赖或改动依赖清单。

截图：`docs/screenshots/studio-discover.png`、`studio-mobile.png`，已查看桌面与手机实际渲染，没有发现横向溢出。

## 8. 真实模型评估

**未进行，不能宣称通过。** 二十条实际请求与评价标准已准备在 `docs/MODEL_EVALUATION.md`。需要记录模型、版本 ID、耗时/失败、服装一致性，以及用户对审美、结构、颜色和搭配的评价。六张内置工具概念素材不代替这一评估。

## 9. 本地启动

```sh
cd /Users/marilyn/Documents/ChatGPT/时尚谷/closie-v2
./dev studio
```

访问 http://127.0.0.1:5173；后端 8080；独立 PostgreSQL 55432 / closie_studio。当前预览已启动。用 `./dev stop` 停止这个工作区应用，保留数据。

配置与测试命令在根 README；模型密钥仅放服务器环境/忽略文件，不发到聊天、前端或 Git。

## 10. 当前完成状态与最高优先级阻塞

**本地概念与个人单品的核心流程已经可用；完整“经过真实模型验证的 AI MVP”尚未完成。**

按严重程度排列：

1. **真实模型账号、可用模型及明确预算未验证。** 当前新设计大多停留在可保存的设计描述，不能承诺实时出图或照片识别。
2. **尚无人类审美验收与二十条真实模型评估。** 无法证明生成、局部编辑和娃娃预览的实际效果。
3. **素材与搭配覆盖有限。** 六件 capsule，只有一双鞋/一个包；许多分类和替换缺少候选。
4. **理解与搭配逻辑仍以有限规则为主。** 可选模型增强没有真实质量数据，尚无全面季节/复杂约束或语义检索。
5. **仅适合本地/受控展示。** 匿名浏览器访问、文件存储和限额不是完整公共多用户安全/运维方案；广泛授权商品检索也未接入。

## 11. 下一项任务与 recruiter demo

下一项应是：配置一个有明确预算的图片模型账号，先执行少量真实生成/原图编辑/个人单品识别，再按二十条协议完成对照评价，并由用户选择接受的视觉方向。通过后再扩充经过批准的 capsule，而不是重新依赖未经筛选的 eBay 页面。

**可以展示：** 真实的本地全栈结构、持久版本、异步任务设计、隐私边界、统一单品流程和自动化测试，以及明确标注的原创素材。

**还不适合宣称：** 已验证的实时 AI 个人穿搭产品、全面商品发现服务或公开生产就绪版本。完整 recruiter-facing AI demo 需要先解决上述第 1、2 项。
