# 脚本文件归类整理说明

## 📋 整理概述

已对项目根目录下的脚本文件进行归类整理，只保留编译APK运行的脚本在根目录，其他脚本移动到`scripts/`目录。

---

## ✅ 根目录保留的脚本（编译APK运行）

以下脚本保留在根目录，用于快速编译、安装和运行APK：

1. **`run.sh`** - Debug版本构建、安装和启动脚本
2. **`run_release.sh`** - Release版本构建、安装和启动脚本
3. **`run_main.sh`** - Debug版本构建、安装和启动脚本（主Activity）
4. **`run_before_test.sh`** - 测试前构建和安装Debug版本脚本

### 使用方法

```bash
# Debug版本
./run.sh

# Release版本
./run_release.sh

# 主Activity启动
./run_main.sh

# 测试前构建
./run_before_test.sh
```

---

## 📦 移动到scripts目录的脚本

以下脚本已移动到`scripts/`目录：

### 测试相关脚本
- `test_io_speed.sh` - I/O速度测试
- `test_sherpa_integration.sh` - Sherpa集成测试
- `test_stt_preload.sh` - STT预加载测试
- `test_wake_word_integration.sh` - 唤醒词集成测试
- `run_device_control_test.sh` - 设备控制测试
- `run_single_test.sh` - 单个测试用例运行

### 编译和检查脚本
- `compile_and_test.sh` - 编译和测试
- `quick_compile_test.sh` - 快速编译测试
- `check_model_paths.sh` - 检查模型路径
- `check_models.sh` - 检查模型

### 工具脚本
- `analyze_apk_size.sh` - APK大小分析
- `pull_test_reports.sh` - 拉取测试报告
- `copy_handsfree_models.sh` - 复制免提模型
- `nomodel.sh` - 无模型构建脚本
- `withModels.sh` - 带模型构建脚本
- `fastlane_deploy.sh` - Fastlane部署脚本

### 使用方法

```bash
# 方式1：从根目录运行
./scripts/script_name.sh

# 方式2：进入scripts目录运行
cd scripts
./script_name.sh
```

---

## 📊 整理统计

- **根目录脚本文件**: 4个（编译APK运行）
- **scripts目录脚本文件**: 50个（测试、工具、检查等）
- **已移动脚本**: 11个
- **已删除备份文件**: 5个

---

## 🔧 Git Hook处理

`post-commit-hook.sh`已移动到`.git/hooks/post-commit`，用于Git commit后自动构建。

---

## 📝 注意事项

1. **脚本路径更新**: 如果其他脚本或文档引用了移动后的脚本，需要更新路径
2. **执行权限**: 所有脚本已设置执行权限
3. **中文支持**: 脚本文件名支持中文，在zsh下可正常显示

---

**最后更新**: 2025-11-11  
**整理范围**: 项目根目录所有.sh脚本文件
