# Reinminder

个人用的事件提醒 App（Android）。记录重要事件，按时提醒。

- 一句话快速记录，中英文都能识别：`11/5 下午5点 小王生日 #纪念日`、`Fri 3pm checkup #health`
- 重复事件（每天 / 每周 / 每月 / 每年）、一个事件多个提醒、推迟、闹钟式全屏提醒
- 今天 / 日程（列表、月历）/ 设置；折叠屏展开时今天页左右双栏
- 中文 / English 切换，默认中文

## 隐私

- 不申请网络权限，数据只存在手机上
- 不进 Google 云备份，也不参与换机传输
- 换机用 App 自己的备份文件，默认用密码加密（AES-256-GCM，PBKDF2 派生密钥）
- 不含任何统计或崩溃上报

## 安装和更新

每次推送到 `main`，GitHub Actions 会编译并发布 Release `v<VERSION>.<编译号>`。用 Obtainium 添加本仓库即可安装和自动更新。

首次使用前，在仓库 Settings › Secrets and variables › Actions 里添加 `SIGNING_PASSWORD`（任意强密码，自己保存好）。第一次编译会生成签名密钥并提交到 `signing/release.p12`，以后每个版本都用同一个密钥签名，才能覆盖安装。

## 自定义图标

见 [branding/README.md](branding/README.md)。默认图标是浅蓝铃铛加琥珀色圆点。
