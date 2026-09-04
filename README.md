# MPAssistant

公众号后台的桌面版WebView壳子 + Markdown转公众号排版工具。

## 用 GitHub Actions 编译 APK(不需要装 Android Studio)

1. 在 GitHub 上新建一个仓库(建议设为 Private,私人小工具没必要公开)
2. 把这个文件夹里的全部内容(包括隐藏的 `.github` 文件夹)上传到仓库根目录
   - 网页端操作:仓库页面 → Add file → Upload files,把整个文件夹拖进去
   - 或者用命令行:
     ```
     git init
     git add .
     git commit -m "init"
     git branch -M main
     git remote add origin 你的仓库地址
     git push -u origin main
     ```
3. 推送后打开仓库的 **Actions** 标签页,会看到 "Build APK" 工作流自动开始运行(第一次跑大概3-5分钟)
4. 跑完之后点进这次运行记录,页面最下面 **Artifacts** 里有个 `MPAssistant-debug-apk`,点击下载,是个zip,解压后就是APK文件
5. 手机上直接用浏览器打开仓库的 Actions 页面(或用GitHub手机APP)也能下载这个zip,下载后解压安装即可
6. 首次安装需要在手机设置里允许"安装未知来源应用"

## 之后想改代码/重新编译

改完代码后正常 `git push`,Actions 会自动重新跑一遍,重新下载最新的 APK 覆盖安装即可,不需要卸载。

## 添加新的排版模板

不需要改任何 Kotlin/Java 代码,只要两步:

1. 在 `app/src/main/assets/templates/` 下新建一个 JSON 文件,比如 `my-theme.json`,格式跟 `default.json` 一样——是一个"HTML标签名 → 内联CSS字符串"的映射,支持的标签有 H1/H2/H3/P/LI/A/STRONG/EM/BLOCKQUOTE/CODE/PRE/IMG/HR/TABLE/TH/TD
2. 在 `app/src/main/assets/templates/manifest.json` 里加一行,比如:
   ```json
   { "id": "my-theme", "name": "我的主题" }
   ```
   注意 `id` 要和文件名(不带 `.json`)一致

提交、push,Actions 编译完的新APK里,Markdown排版页面顶部的下拉框就会多出这个选项,选中后立即生效,选择记忆在本地(下次打开自动带出上次选的模板)。

## 关于签名

这个工作流打的是 **debug APK**,没有用正式签名,只能用来自己安装测试,不能上架应用商店。个人日常使用完全没问题。
