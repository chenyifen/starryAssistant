#!/usr/bin/env python3
"""
韩语 TTS 功能测试脚本
用于快速验证 Google TTS 是否正常工作

使用方法:
    python scripts/test_korean_tts.py
"""

import sys
from pathlib import Path

def test_dependencies():
    """测试依赖库"""
    print("🔍 检查依赖库...")
    
    missing_deps = []
    
    try:
        import gtts
        print("  ✅ gtts - OK")
    except ImportError:
        missing_deps.append("gtts")
        print("  ❌ gtts - 缺失")
    
    try:
        from pydub import AudioSegment
        print("  ✅ pydub - OK")
    except ImportError:
        missing_deps.append("pydub")
        print("  ❌ pydub - 缺失")
    
    if missing_deps:
        print(f"\n❌ 缺少依赖: {', '.join(missing_deps)}")
        print("请安装: pip install " + " ".join(missing_deps))
        return False
    
    print("✅ 所有依赖库检查通过")
    return True

def test_tts_generation():
    """测试TTS生成"""
    print("\n🎤 测试 Google TTS 生成...")
    
    try:
        from gtts import gTTS
        from pydub import AudioSegment
        
        # 测试目录
        test_dir = Path("test_tts_output")
        test_dir.mkdir(exist_ok=True)
        
        # 测试词汇
        test_words = ["하이넛지", "안녕하세요", "감사합니다"]
        
        for i, word in enumerate(test_words, 1):
            print(f"  测试 {i}/{len(test_words)}: '{word}'")
            
            # 生成TTS
            tts = gTTS(text=word, lang='ko', slow=False)
            
            # 保存文件
            mp3_file = test_dir / f"test_{i}.mp3"
            wav_file = test_dir / f"test_{i}.wav"
            
            tts.save(str(mp3_file))
            print(f"    ✅ MP3 生成成功: {mp3_file}")
            
            # 转换为WAV
            audio = AudioSegment.from_mp3(str(mp3_file))
            audio = audio.set_channels(1).set_frame_rate(16000)
            audio.export(str(wav_file), format="wav")
            print(f"    ✅ WAV 转换成功: {wav_file}")
            
            # 清理MP3
            mp3_file.unlink()
        
        print(f"\n✅ TTS 测试完成，输出目录: {test_dir}")
        return True
        
    except Exception as e:
        print(f"❌ TTS 测试失败: {e}")
        return False

def test_korean_specific():
    """测试韩语特定功能"""
    print("\n🇰🇷 测试韩语特定功能...")
    
    try:
        from gtts import gTTS
        
        # 测试不同的韩语TTS配置
        configs = [
            {'slow': False, 'tld': 'com'},
            {'slow': True, 'tld': 'com'},
            {'slow': False, 'tld': 'co.kr'},
        ]
        
        test_dir = Path("test_korean_specific")
        test_dir.mkdir(exist_ok=True)
        
        for i, config in enumerate(configs, 1):
            print(f"  配置 {i}: slow={config['slow']}, tld={config['tld']}")
            
            tts = gTTS(text="하이넛지", lang='ko', **config)
            output_file = test_dir / f"korean_config_{i}.mp3"
            tts.save(str(output_file))
            
            print(f"    ✅ 生成成功: {output_file}")
        
        print(f"✅ 韩语配置测试完成，输出目录: {test_dir}")
        return True
        
    except Exception as e:
        print(f"❌ 韩语配置测试失败: {e}")
        return False

def main():
    print("🧪 韩语 TTS 功能测试")
    print("=" * 40)
    
    # 测试依赖
    if not test_dependencies():
        sys.exit(1)
    
    # 测试TTS生成
    if not test_tts_generation():
        sys.exit(1)
    
    # 测试韩语特定功能
    if not test_korean_specific():
        sys.exit(1)
    
    print("\n🎉 所有测试通过！")
    print("📝 现在可以使用 simple_korean_tts_generator.py 生成训练数据")

if __name__ == "__main__":
    main()
