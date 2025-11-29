#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Assets目录加密打包脚本
将整个assets目录打包成一个加密的压缩包，防止反编译后直接提取

使用方法：
    python3 scripts/encrypt_assets.py

加密说明：
    - 将assets目录打包成zip文件
    - 使用AES-128-ECB加密zip文件
    - 加密后的文件添加4字节魔数头（0x4D4F444C = "MODL"）
    - 密钥与AssetDecryptor.kt中的解密密钥一致
"""

import os
import sys
import zipfile
import shutil
from Crypto.Cipher import AES
from Crypto.Util.Padding import pad

# 密钥（与AssetDecryptor.kt中的getDecryptionKey()一致）
KEY_STRING = "V0!c3@55!5t@nt#2024"
KEY = KEY_STRING.encode('utf-8')[:16]  # AES-128需要16字节密钥

# 魔数头：0x4D4F444C = "MODL" (Model)
MAGIC_HEADER = bytes([0x4D, 0x4F, 0x44, 0x4C])

# 输出文件名（加密后的文件）
ENCRYPTED_ASSETS_FILE = "encrypted_assets.dat"

# 需要排除的备份目录和文件（不打包）
EXCLUDE_DIRS = {
    'assets_encrypted',
    'assets_backup',
    'backup',
    '.backup',
    '.git',
    '__pycache__',
}

# 需要排除的文件（不打包）
EXCLUDE_FILES = {
    'encrypted_assets.dat',  # 加密后的文件本身
    'assets_temp.zip',       # 临时zip文件
    'assets_encrypted.zip',  # 旧的加密zip文件
}


def should_exclude_path(rel_path: str) -> bool:
    """判断路径是否应该被排除"""
    path_parts = rel_path.replace('\\', '/').split('/')
    for part in path_parts:
        # 检查目录名或文件名是否在排除列表中
        if part in EXCLUDE_DIRS:
            return True
        # 检查是否以备份后缀结尾
        if part.endswith('_backup') or part.endswith('_encrypted'):
            return True
    
    # 检查文件名是否在排除列表中
    filename = os.path.basename(rel_path)
    if filename in EXCLUDE_FILES:
        return True
    
    return False


def encrypt_file(input_path: str, output_path: str) -> bool:
    """加密文件"""
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


def create_assets_zip(assets_dir: str, zip_path: str) -> bool:
    """将assets目录打包成zip文件"""
    try:
        if not os.path.exists(assets_dir):
            print(f"❌ Assets目录不存在: {assets_dir}")
            return False
        
        print(f"📦 开始打包Assets目录: {assets_dir}")
        
        # 创建zip文件
        with zipfile.ZipFile(zip_path, 'w', zipfile.ZIP_DEFLATED) as zipf:
            file_count = 0
            skipped_count = 0
            
            # 遍历assets目录
            for root, dirs, files in os.walk(assets_dir):
                # 过滤掉需要排除的目录（原地修改dirs列表）
                dirs[:] = [d for d in dirs if not should_exclude_path(
                    os.path.relpath(os.path.join(root, d), assets_dir)
                )]
                
                for file_name in files:
                    file_path = os.path.join(root, file_name)
                    # 计算相对路径（相对于assets_dir）
                    arcname = os.path.relpath(file_path, assets_dir)
                    
                    # 检查是否应该排除
                    if should_exclude_path(arcname):
                        skipped_count += 1
                        continue
                    
                    zipf.write(file_path, arcname)
                    file_count += 1
                    if file_count % 100 == 0:
                        print(f"   已添加: {file_count} 个文件...")
            
            print(f"   总计: {file_count} 个文件已打包, {skipped_count} 个文件已跳过")
        
        zip_size = os.path.getsize(zip_path)
        print(f"✅ 打包完成: {zip_path} ({zip_size} bytes)")
        return True
    except Exception as e:
        print(f"❌ 打包失败: {e}")
        return False


def main():
    """主函数"""
    # 获取项目根目录
    script_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.dirname(script_dir)
    
    assets_dir = os.path.join(project_root, 'app', 'src', 'main', 'assets')
    temp_zip = os.path.join(project_root, 'app', 'src', 'main', 'assets_temp.zip')
    encrypted_file = os.path.join(project_root, 'app', 'src', 'main', 'assets', ENCRYPTED_ASSETS_FILE)
    
    print("🔒 开始加密打包Assets目录...")
    print(f"   输入目录: {assets_dir}")
    print(f"   输出文件: {encrypted_file}")
    
    # 检查是否有原始文件（排除加密文件和备份文件）
    has_original_files = False
    if os.path.exists(assets_dir):
        for root, dirs, files in os.walk(assets_dir):
            dirs[:] = [d for d in dirs if not should_exclude_path(
                os.path.relpath(os.path.join(root, d), assets_dir)
            )]
            for file in files:
                file_path = os.path.join(root, file)
                rel_path = os.path.relpath(file_path, assets_dir)
                if not should_exclude_path(rel_path):
                    has_original_files = True
                    break
            if has_original_files:
                break
    
    if not has_original_files:
        print("⚠️  警告: Assets目录下没有原始文件（只有加密文件或备份文件）")
        print("   如果这是第一次加密，请确保assets目录下有原始模型文件")
        print("   如果原始文件已被删除，请先恢复原始文件")
        
        # 检查是否已经有加密文件
        if os.path.exists(encrypted_file) and os.path.getsize(encrypted_file) > 100:
            print(f"   ✅ 检测到已存在的加密文件: {encrypted_file} ({os.path.getsize(encrypted_file) / 1024 / 1024:.2f} MB)")
            print("   ℹ️  跳过加密（使用已存在的加密文件）")
            return 0
        else:
            print("   ❌ 加密文件不存在或无效，无法继续")
            return 1
    
    # 步骤1: 打包assets目录
    if not create_assets_zip(assets_dir, temp_zip):
        return 1
    
    # 检查打包后的zip文件大小
    if os.path.exists(temp_zip):
        zip_size = os.path.getsize(temp_zip)
        if zip_size < 100:  # 小于100字节，说明没有打包任何有效文件
            print(f"⚠️  警告: 打包后的zip文件太小 ({zip_size} bytes)，可能没有打包任何有效文件")
            print("   请检查assets目录下是否有原始模型文件")
            os.remove(temp_zip)
            return 1
    
    # 步骤2: 加密zip文件
    if not encrypt_file(temp_zip, encrypted_file):
        # 清理临时文件
        if os.path.exists(temp_zip):
            os.remove(temp_zip)
        return 1
    
    # 清理临时文件
    if os.path.exists(temp_zip):
        os.remove(temp_zip)
    
    # 显示统计信息（只统计未排除的文件）
    original_size = 0
    for root, dirs, files in os.walk(assets_dir):
        # 过滤掉需要排除的目录
        dirs[:] = [d for d in dirs if not should_exclude_path(
            os.path.relpath(os.path.join(root, d), assets_dir)
        )]
        for file in files:
            file_path = os.path.join(root, file)
            rel_path = os.path.relpath(file_path, assets_dir)
            if not should_exclude_path(rel_path):
                original_size += os.path.getsize(file_path)
    encrypted_size = os.path.getsize(encrypted_file)
    
    print(f"\n📊 加密统计:")
    print(f"   原始Assets总大小: {original_size:,} bytes ({original_size / 1024 / 1024:.2f} MB)")
    print(f"   加密后文件大小: {encrypted_size:,} bytes ({encrypted_size / 1024 / 1024:.2f} MB)")
    if original_size > 0:
        print(f"   压缩率: {(1 - encrypted_size / original_size) * 100:.1f}%")
    else:
        print(f"   压缩率: N/A (原始文件为空)")
    
    print(f"\n✅ 加密完成！")
    print(f"   加密文件已保存到: {encrypted_file}")
    print(f"   应用启动时将自动解密并挂载到AssetManager")
    print(f"   原有的 AssetManager.open() 可以直接访问，无需修改代码")
    
    return 0


if __name__ == '__main__':
    sys.exit(main())
