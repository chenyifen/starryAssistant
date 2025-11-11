#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Assets模型文件加密脚本
用于在构建时加密assets目录下的模型文件，防止反编译后直接提取

使用方法：
    python3 scripts/encrypt_assets.py

加密说明：
    - 使用AES-128-ECB加密
    - 加密后的文件添加4字节魔数头（0x4D4F444C = "MODL"）
    - 密钥与AssetDecryptor.kt中的解密密钥一致
"""

import os
import sys
from Crypto.Cipher import AES
from Crypto.Util.Padding import pad

# 密钥（与AssetDecryptor.kt中的getDecryptionKey()一致）
KEY_STRING = "V0!c3@55!5t@nt#2024"
KEY = KEY_STRING.encode('utf-8')[:16]  # AES-128需要16字节密钥

# 魔数头：0x4D4F444C = "MODL" (Model)
MAGIC_HEADER = bytes([0x4D, 0x4F, 0x44, 0x4C])

# 需要加密的文件扩展名
ENCRYPT_EXTENSIONS = {'.onnx', '.mdl', '.tflite', '.bin'}

# 需要加密的目录（相对assets根目录）
ENCRYPT_DIRS = [
    'korean_hinudge_onnx',
    'sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17',
    'tts-en',
    'tts-ko',
    'vosk-model-small-ko-0.22',
]

# 根目录下需要加密的文件
ENCRYPT_ROOT_FILES = [
    'embedding_model.onnx',
    'melspectrogram.onnx',
    'silero_vad.onnx',
]


def encrypt_file(input_path: str, output_path: str) -> bool:
    """加密单个文件"""
    try:
        # 读取原始文件
        with open(input_path, 'rb') as f:
            plaintext = f.read()
        
        # 创建AES加密器
        cipher = AES.new(KEY, AES.MODE_ECB)
        
        # 加密数据（PKCS5填充）
        encrypted_data = cipher.encrypt(pad(plaintext, AES.block_size))
        
        # 写入加密文件：魔数头 + 加密数据
        with open(output_path, 'wb') as f:
            f.write(MAGIC_HEADER)
            f.write(encrypted_data)
        
        print(f"✅ 加密成功: {input_path} -> {output_path}")
        print(f"   原始大小: {len(plaintext)} bytes, 加密后: {len(encrypted_data) + 4} bytes")
        return True
    except Exception as e:
        print(f"❌ 加密失败: {input_path}, 错误: {e}")
        return False


def should_encrypt_file(file_path: str, file_name: str) -> bool:
    """判断文件是否需要加密"""
    # 检查文件扩展名
    _, ext = os.path.splitext(file_name)
    if ext.lower() not in ENCRYPT_EXTENSIONS:
        return False
    
    # 检查是否在需要加密的目录中
    path_parts = file_path.replace('\\', '/').split('/')
    for encrypt_dir in ENCRYPT_DIRS:
        if encrypt_dir in path_parts:
            return True
    
    # 检查是否是根目录下的需要加密的文件
    if file_name in ENCRYPT_ROOT_FILES:
        return True
    
    return False


def process_assets_directory(assets_dir: str, output_dir: str):
    """处理assets目录，加密模型文件"""
    if not os.path.exists(assets_dir):
        print(f"❌ Assets目录不存在: {assets_dir}")
        return False
    
    encrypted_count = 0
    skipped_count = 0
    
    # 遍历assets目录
    for root, dirs, files in os.walk(assets_dir):
        for file_name in files:
            file_path = os.path.join(root, file_name)
            rel_path = os.path.relpath(file_path, assets_dir)
            
            if should_encrypt_file(rel_path, file_name):
                # 需要加密
                output_path = os.path.join(output_dir, rel_path)
                os.makedirs(os.path.dirname(output_path), exist_ok=True)
                
                if encrypt_file(file_path, output_path):
                    encrypted_count += 1
                else:
                    # 加密失败，复制原文件
                    import shutil
                    shutil.copy2(file_path, output_path)
                    skipped_count += 1
            else:
                # 不需要加密，直接复制
                output_path = os.path.join(output_dir, rel_path)
                os.makedirs(os.path.dirname(output_path), exist_ok=True)
                import shutil
                shutil.copy2(file_path, output_path)
    
    print(f"\n📊 加密统计:")
    print(f"   已加密: {encrypted_count} 个文件")
    print(f"   跳过: {skipped_count} 个文件")
    return encrypted_count > 0


def main():
    """主函数"""
    # 获取项目根目录
    script_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.dirname(script_dir)
    
    assets_dir = os.path.join(project_root, 'app', 'src', 'main', 'assets')
    output_dir = os.path.join(project_root, 'app', 'src', 'main', 'assets_encrypted')
    
    print("🔒 开始加密Assets模型文件...")
    print(f"   输入目录: {assets_dir}")
    print(f"   输出目录: {output_dir}")
    
    # 清理输出目录
    if os.path.exists(output_dir):
        import shutil
        shutil.rmtree(output_dir)
    
    # 处理assets目录
    success = process_assets_directory(assets_dir, output_dir)
    
    if success:
        print("\n✅ 加密完成！")
        print(f"   加密后的文件已保存到: {output_dir}")
        print("   请将assets_encrypted目录重命名为assets，或修改构建配置使用assets_encrypted")
    else:
        print("\n⚠️  未找到需要加密的文件")
    
    return 0 if success else 1


if __name__ == '__main__':
    sys.exit(main())

