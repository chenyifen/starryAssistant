cmake_minimum_required(VERSION 3.10.2)

project("opus_android")

# 设置C++标准
set(CMAKE_CXX_STANDARD 11)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# 查找log库
find_library(log-lib log)

# 创建一个简单的测试库来验证编译系统
add_library(opus_jni SHARED
    opus_jni_stub.cpp
)

# 链接库
target_link_libraries(opus_jni ${log-lib})

# 添加头文件目录（暂时注释掉Opus相关内容）
# include_directories(opus-1.3.1/include)
