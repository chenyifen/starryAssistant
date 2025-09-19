#!/usr/bin/env python3
"""
TTS依赖安装脚本
自动检测和安装韩语TTS生成所需的Python库

使用方法:
    python scripts/install_tts_dependencies.py
    python scripts/install_tts_dependencies.py --install-all
    python scripts/install_tts_dependencies.py --check-only
"""

import subprocess
import sys
import argparse
from pathlib import Path

# 依赖库配置
DEPENDENCIES = {
    # 基础音频处理库
    'basic': [
        'numpy',
        'librosa',
        'soundfile', 
        'pydub',
        'scipy'
    ],
    
    # TTS引擎库
    'tts_engines': [
        'gtts',           # Google TTS
        'edge-tts',       # Microsoft Edge TTS
        'pyttsx3',        # 离线TTS
    ],
    
    # 可选的高级库
    'optional': [
        'azure-cognitiveservices-speech',  # Azure TTS
        'boto3',          # AWS Polly
        'pygame',         # 音频播放
        'matplotlib',     # 可视化
        'tqdm',          # 进度条
    ]
}

def check_dependency(package_name: str) -> bool:
    """检查依赖是否已安装"""
    try:
        __import__(package_name.replace('-', '_'))
        return True
    except ImportError:
        return False

def install_package(package_name: str) -> bool:
    """安装单个包"""
    try:
        print(f"  正在安装 {package_name}...")
        result = subprocess.run([
            sys.executable, '-m', 'pip', 'install', package_name
        ], capture_output=True, text=True, check=True)
        
        print(f"  ✅ {package_name} 安装成功")
        return True
        
    except subprocess.CalledProcessError as e:
        print(f"  ❌ {package_name} 安装失败: {e.stderr}")
        return False

def check_system_dependencies():
    """检查系统依赖"""
    print("\n🔍 检查系统依赖...")
    
    # 检查ffmpeg (pydub需要)
    try:
        subprocess.run(['ffmpeg', '-version'], 
                      capture_output=True, check=True)
        print("  ✅ ffmpeg - 已安装")
    except (subprocess.CalledProcessError, FileNotFoundError):
        print("  ❌ ffmpeg - 未安装")
        print("     macOS: brew install ffmpeg")
        print("     Ubuntu: sudo apt install ffmpeg")
        print("     Windows: 下载并添加到PATH")

def main():
    parser = argparse.ArgumentParser(description="TTS依赖安装脚本")
    parser.add_argument("--install-all", action="store_true",
                       help="安装所有依赖 (包括可选)")
    parser.add_argument("--check-only", action="store_true",
                       help="仅检查依赖状态")
    parser.add_argument("--basic-only", action="store_true",
                       help="仅安装基础依赖")
    
    args = parser.parse_args()
    
    print("🎤 韩语TTS依赖安装工具")
    print("="*50)
    
    # 检查系统依赖
    check_system_dependencies()
    
    # 确定要处理的依赖类别
    categories_to_process = ['basic', 'tts_engines']
    if args.install_all:
        categories_to_process.append('optional')
    elif args.basic_only:
        categories_to_process = ['basic']
    
    # 检查和安装依赖
    for category in categories_to_process:
        packages = DEPENDENCIES[category]
        category_name = {
            'basic': '基础音频处理库',
            'tts_engines': 'TTS引擎库', 
            'optional': '可选高级库'
        }[category]
        
        print(f"\n📦 {category_name}:")
        
        missing_packages = []
        
        for package in packages:
            # 特殊处理包名映射
            import_name = package
            if package == 'edge-tts':
                import_name = 'edge_tts'
            elif package == 'azure-cognitiveservices-speech':
                import_name = 'azure.cognitiveservices.speech'
            
            is_installed = check_dependency(import_name)
            status = "✅ 已安装" if is_installed else "❌ 未安装"
            print(f"  {package}: {status}")
            
            if not is_installed:
                missing_packages.append(package)
        
        # 安装缺失的包
        if missing_packages and not args.check_only:
            print(f"\n🔧 安装缺失的 {category_name}...")
            
            success_count = 0
            for package in missing_packages:
                if install_package(package):
                    success_count += 1
            
            print(f"  安装完成: {success_count}/{len(missing_packages)} 个包")
    
    # 生成使用建议
    print(f"\n💡 使用建议:")
    
    # 检查可用的TTS引擎
    available_engines = []
    
    if check_dependency('gtts'):
        available_engines.append('gTTS (Google)')
    
    if check_dependency('edge_tts'):
        available_engines.append('edge-tts (Microsoft)')
    
    if check_dependency('pyttsx3'):
        available_engines.append('pyttsx3 (离线)')
    
    if available_engines:
        print(f"  🎤 可用TTS引擎: {', '.join(available_engines)}")
        
        print(f"\n🚀 快速开始:")
        print(f"  # 检查TTS功能")
        print(f"  python scripts/test_korean_tts.py")
        print(f"  ")
        print(f"  # 简单生成")
        print(f"  python scripts/simple_korean_tts_generator.py")
        print(f"  ")
        print(f"  # 高级生成")
        print(f"  python scripts/advanced_korean_tts_generator.py")
        
    else:
        print(f"  ❌ 没有可用的TTS引擎，请安装至少一个TTS库")
    
    # 创建requirements.txt
    if not args.check_only:
        requirements_path = Path("scripts/requirements_tts.txt")
        
        all_packages = []
        for category in ['basic', 'tts_engines']:
            all_packages.extend(DEPENDENCIES[category])
        
        with open(requirements_path, 'w') as f:
            for package in all_packages:
                f.write(f"{package}\n")
        
        print(f"\n📝 依赖列表已保存到: {requirements_path}")
        print(f"   可使用: pip install -r {requirements_path}")

if __name__ == "__main__":
    main()
