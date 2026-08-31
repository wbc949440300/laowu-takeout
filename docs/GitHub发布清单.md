# GitHub 发布清单

## 发布前必须完成

- [ ] 吊销并重新生成曾出现在本地文件或日志中的模型 Key、JWT Secret、微信 Secret 和数据库密码。
- [ ] 确认根目录 `.gitignore` 已排除所有 `.env`、日志、数据库、`target/`、`node_modules/` 和构建产物。
- [ ] 在全新目录执行 `git init`、`git add --dry-run .`，确认没有敏感文件被纳入。
- [ ] 在干净环境按 README 重新安装依赖并运行 Agent、Java、前端验证命令。
- [ ] 截取管理后台、用户端和 Agent 确认节点截图，放入 README 或 Release 说明。

## 建议补充

- [ ] 增加一张架构图和一段 2 分钟演示视频。
- [ ] 建立 GitHub Actions：Agent pytest、Maven test、前端 build 和敏感信息扫描。
- [x] 已添加 `.github/workflows/ci.yml`，自动执行 Agent pytest、Maven test、前端 build 和敏感信息扫描。
- [ ] 写一篇技术复盘：为什么高风险工具需要确认、为什么订单归属必须在后端校验。
- [ ] 创建 `v1.0.0` 标签，记录已知限制（Mock 支付、SQLite 单实例 checkpoint、Vue 2 依赖）。

## 暂不作为发布阻塞项

- PostgreSQL/Redis 多实例 checkpoint 迁移。
- 生产级监控告警、灰度发布和复杂容量治理。

这些内容属于真实商业化部署阶段；求职展示阶段优先证明架构理解、边界意识、测试能力和交付完整度。
