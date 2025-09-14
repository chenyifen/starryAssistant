#!/usr/bin/env python3
"""
韩语唤醒词训练数据收集工具
支持多种数据源：TTS合成、在线服务、录音工具

使用方法:
    python scripts/collect_korean_training_data.py --method tts --output_dir training_data
    python scripts/collect_korean_training_data.py --method record --output_dir training_data
    python scripts/collect_korean_training_data.py --method download --output_dir training_data
"""

import os
import sys
import argparse
import logging
from pathlib import Path
import requests
import json
import time
import random
from typing import List, Dict, Tuple
import subprocess

# 设置日志
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

class KoreanDataCollector:
    """韩语训练数据收集器"""
    
    def __init__(self, output_dir: str):
        self.output_dir = Path(output_dir)
        self.wake_word = "하이넛지"
        self.romanized = "hi_nutji"
        
        # 创建目录结构
        self.positive_dir = self.output_dir / "positive"
        self.negative_dir = self.output_dir / "negative"
        self.temp_dir = self.output_dir / "temp"
        
        for dir_path in [self.positive_dir, self.negative_dir, self.temp_dir]:
            dir_path.mkdir(parents=True, exist_ok=True)
    
    def show_collection_guide(self):
        """显示数据收集完整指南"""
        print("\n" + "="*80)
        print("🎤 韩语唤醒词'하이넛지'训练数据收集完整指南")
        print("="*80)
        
        print("\n📋 收集方法概览:")
        print("1. 🤖 TTS合成 - 使用在线TTS服务生成基础数据")
        print("2. 🎙️ 真人录音 - 收集真实语音数据")
        print("3. 📥 在线下载 - 从公开数据集下载")
        print("4. 🔄 数据增强 - 对现有数据进行变换")
        
        print("\n" + "="*50)
        print("方法1: 🤖 在线TTS服务生成")
        print("="*50)
        
        print("\n🌐 推荐的免费TTS服务:")
        print("• Google Text-to-Speech (gTTS)")
        print("• Microsoft Azure Cognitive Services (免费额度)")
        print("• Amazon Polly (免费额度)")
        print("• Naver Clova Voice (韩语专用)")
        print("• Kakao i Voice (韩语专用)")
        
        print("\n📝 TTS生成步骤:")
        print("1. 注册TTS服务账号")
        print("2. 获取API密钥")
        print("3. 使用不同声音生成'하이넛지'")
        print("4. 调整语速、音调、音量")
        print("5. 生成50-100个变体")
        
        print("\n💻 代码示例 (Google TTS):")
        print("""
# 安装依赖
pip install gtts pydub

# Python代码
from gtts import gTTS
from pydub import AudioSegment
import io

def generate_wake_word_variants():
    text = "하이넛지"
    
    # 不同语言代码尝试
    lang_variants = ['ko', 'ko-KR']
    
    for i, lang in enumerate(lang_variants):
        for speed in [0.8, 1.0, 1.2]:  # 不同语速
            tts = gTTS(text=text, lang=lang, slow=(speed < 1.0))
            
            # 保存到内存
            mp3_fp = io.BytesIO()
            tts.write_to_fp(mp3_fp)
            mp3_fp.seek(0)
            
            # 转换为WAV
            audio = AudioSegment.from_mp3(mp3_fp)
            audio = audio.set_frame_rate(16000).set_channels(1)
            
            # 调整音量
            audio = audio + random.randint(-5, 5)  # 随机音量调整
            
            filename = f"tts_{lang}_{speed}_{i:03d}.wav"
            audio.export(f"positive/{filename}", format="wav")
        """)
        
        print("\n" + "="*50)
        print("方法2: 🎙️ 真人录音收集")
        print("="*50)
        
        print("\n🎯 录音要求:")
        print("• 格式: 16kHz, 16-bit, mono WAV")
        print("• 长度: 1-3秒")
        print("• 环境: 安静，无回声")
        print("• 设备: 质量好的麦克风")
        
        print("\n👥 录音策略:")
        print("• 男性声音: 30-40个样本")
        print("• 女性声音: 30-40个样本")
        print("• 儿童声音: 20-30个样本")
        print("• 老年人声音: 10-20个样本")
        
        print("\n🎵 语音变化:")
        print("• 正常语调")
        print("• 疑问语调")
        print("• 命令语调")
        print("• 轻声细语")
        print("• 大声呼叫")
        
        print("\n🛠️ 推荐录音工具:")
        print("• Audacity (免费，跨平台)")
        print("• GarageBand (Mac)")
        print("• Voice Recorder (手机)")
        print("• OBS Studio (高级)")
        
        print("\n📱 手机录音脚本:")
        print("""
# 使用手机录音的批量处理
# 1. 录制多个音频文件
# 2. 传输到电脑
# 3. 批量转换格式

# FFmpeg批量转换
for file in *.m4a; do
    ffmpeg -i "$file" -ar 16000 -ac 1 -sample_fmt s16 "${file%.*}.wav"
done
        """)
        
        print("\n" + "="*50)
        print("方法3: 📥 公开数据集下载")
        print("="*50)
        
        print("\n🗃️ 韩语语音数据集:")
        print("• KSS Dataset (Korean Single Speaker)")
        print("• Zeroth-Korean (Mozilla Common Voice)")
        print("• AIHub 한국어 음성 데이터")
        print("• Google Speech Commands (部分韩语)")
        
        print("\n🔍 数据集搜索关键词:")
        print("• Korean speech dataset")
        print("• 한국어 음성 데이터셋")
        print("• Korean wake word dataset")
        print("• Korean voice commands")
        
        print("\n" + "="*50)
        print("方法4: 🔄 数据增强技术")
        print("="*50)
        
        print("\n🎛️ 音频增强方法:")
        print("• 添加背景噪音")
        print("• 调整音量和音调")
        print("• 时间拉伸/压缩")
        print("• 添加回声和混响")
        print("• 频谱遮蔽")
        
        print("\n💻 数据增强代码:")
        print("""
# 使用librosa进行数据增强
import librosa
import numpy as np
from scipy.signal import butter, lfilter

def augment_audio(audio, sr=16000):
    augmented = []
    
    # 1. 添加噪音
    noise = np.random.normal(0, 0.005, audio.shape)
    augmented.append(audio + noise)
    
    # 2. 调整音调
    for pitch_shift in [-2, -1, 1, 2]:
        pitched = librosa.effects.pitch_shift(audio, sr, pitch_shift)
        augmented.append(pitched)
    
    # 3. 时间拉伸
    for rate in [0.9, 1.1]:
        stretched = librosa.effects.time_stretch(audio, rate)
        augmented.append(stretched)
    
    # 4. 添加混响
    reverb = np.convolve(audio, np.random.exponential(0.1, 1000))
    augmented.append(reverb[:len(audio)])
    
    return augmented
        """)
        
        print("\n" + "="*50)
        print("负样本收集策略")
        print("="*50)
        
        print("\n❌ 负样本类型:")
        print("• 其他韩语词汇: 안녕하세요, 감사합니다, 죄송합니다")
        print("• 类似发音: 하이, 넛지, 하이브리드")
        print("• 环境声音: 음악, TV, 대화, 교통소음")
        print("• 其他语言: Hello, Hi there, Hey Google")
        
        print("\n🎯 负样本收集比例:")
        print("• 正样本: 100-200个")
        print("• 负样本: 300-600个 (2-3倍)")
        
        print("\n" + "="*50)
        print("质量控制检查清单")
        print("="*50)
        
        print("\n✅ 音频质量检查:")
        print("□ 采样率: 16kHz")
        print("□ 位深度: 16-bit")
        print("□ 声道: 单声道")
        print("□ 长度: 1-3秒")
        print("□ 音量: 适中，无削波")
        print("□ 噪音: 最小背景噪音")
        print("□ 清晰度: 语音清晰可辨")
        
        print("\n📊 数据集平衡:")
        print("□ 性别平衡: 男女比例适当")
        print("□ 年龄分布: 涵盖不同年龄段")
        print("□ 口音变化: 包含不同地区口音")
        print("□ 环境多样: 不同录音环境")
        
        print("\n🔧 技术验证:")
        print("□ 文件格式正确")
        print("□ 文件完整无损坏")
        print("□ 标注准确")
        print("□ 目录结构正确")
        
        print("\n" + "="*80)
        print("🚀 开始收集数据!")
        print("="*80)
        print("选择一种或多种方法开始收集训练数据。")
        print("建议从TTS合成开始，然后补充真人录音。")
        print("记住：数据质量比数量更重要！")
        print("="*80)
    
    def generate_tts_samples_gtts(self, count: int = 50):
        """使用Google TTS生成样本"""
        try:
            from gtts import gTTS
            from pydub import AudioSegment
            import io
        except ImportError:
            logger.error("请安装依赖: pip install gtts pydub")
            return False
        
        logger.info(f"使用Google TTS生成 {count} 个样本...")
        
        # 不同的TTS参数
        lang_variants = ['ko']
        slow_variants = [False, True]
        
        sample_count = 0
        for i in range(count):
            try:
                # 随机选择参数
                lang = random.choice(lang_variants)
                slow = random.choice(slow_variants)
                
                # 生成TTS
                tts = gTTS(text=self.wake_word, lang=lang, slow=slow)
                
                # 保存到内存
                mp3_fp = io.BytesIO()
                tts.write_to_fp(mp3_fp)
                mp3_fp.seek(0)
                
                # 转换为WAV
                audio = AudioSegment.from_mp3(mp3_fp)
                audio = audio.set_frame_rate(16000).set_channels(1)
                
                # 随机音频增强
                if random.random() > 0.5:
                    # 随机音量调整 (-3dB to +3dB)
                    volume_change = random.uniform(-3, 3)
                    audio = audio + volume_change
                
                # 保存文件
                filename = f"gtts_{lang}_{slow}_{sample_count:03d}.wav"
                filepath = self.positive_dir / filename
                audio.export(str(filepath), format="wav")
                
                sample_count += 1
                if sample_count % 10 == 0:
                    logger.info(f"已生成 {sample_count} 个TTS样本")
                
                # 避免请求过快
                time.sleep(0.5)
                
            except Exception as e:
                logger.error(f"生成TTS样本失败: {e}")
                continue
        
        logger.info(f"TTS样本生成完成，共 {sample_count} 个文件")
        return True
    
    def generate_negative_samples_tts(self, count: int = 100):
        """生成负样本"""
        try:
            from gtts import gTTS
            from pydub import AudioSegment
            import io
        except ImportError:
            logger.error("请安装依赖: pip install gtts pydub")
            return False
        
        # 韩语负样本词汇
        negative_words = [
            "안녕하세요", "감사합니다", "죄송합니다", "괜찮습니다",
            "하이", "넛지", "하이브리드", "안녕", "여보세요",
            "구글", "시리", "알렉사", "빅스비"
        ]
        
        logger.info(f"生成 {count} 个负样本...")
        
        sample_count = 0
        for i in range(count):
            try:
                # 随机选择负样本词汇
                word = random.choice(negative_words)
                
                # 生成TTS
                tts = gTTS(text=word, lang='ko', slow=random.choice([True, False]))
                
                # 保存到内存
                mp3_fp = io.BytesIO()
                tts.write_to_fp(mp3_fp)
                mp3_fp.seek(0)
                
                # 转换为WAV
                audio = AudioSegment.from_mp3(mp3_fp)
                audio = audio.set_frame_rate(16000).set_channels(1)
                
                # 保存文件
                filename = f"negative_tts_{word}_{sample_count:03d}.wav"
                # 替换文件名中的特殊字符
                filename = filename.replace(" ", "_").replace(".", "")
                filepath = self.negative_dir / filename
                audio.export(str(filepath), format="wav")
                
                sample_count += 1
                if sample_count % 20 == 0:
                    logger.info(f"已生成 {sample_count} 个负样本")
                
                time.sleep(0.3)
                
            except Exception as e:
                logger.error(f"生成负样本失败: {e}")
                continue
        
        logger.info(f"负样本生成完成，共 {sample_count} 个文件")
        return True
    
    def create_recording_script(self):
        """创建录音脚本"""
        script_content = f"""#!/bin/bash
# 韩语唤醒词录音脚本

echo "🎙️ 韩语唤醒词录音助手"
echo "目标词汇: {self.wake_word}"
echo "请准备好麦克风，在安静环境中录音"
echo ""

# 创建录音目录
mkdir -p "{self.positive_dir}"
mkdir -p "{self.negative_dir}"

# 录音函数
record_positive() {{
    echo "📢 请说: {self.wake_word}"
    echo "按回车开始录音，录音3秒后自动停止..."
    read
    
    filename="manual_$(date +%Y%m%d_%H%M%S).wav"
    filepath="{self.positive_dir}/$filename"
    
    echo "🔴 录音中... (3秒)"
    sox -d -r 16000 -c 1 -b 16 "$filepath" trim 0 3
    echo "✅ 录音完成: $filename"
    
    # 播放录音确认
    echo "🔊 播放录音确认:"
    play "$filepath"
}}

# 批量录音
echo "开始批量录音正样本..."
for i in {{1..20}}; do
    echo ""
    echo "=== 第 $i 个样本 ==="
    record_positive
    
    echo "继续下一个? (y/n)"
    read -n 1 continue_recording
    echo ""
    
    if [[ $continue_recording != "y" ]]; then
        break
    fi
done

echo ""
echo "🎉 录音完成!"
echo "正样本保存在: {self.positive_dir}"
echo "请检查录音质量，删除不合格的文件"
"""
        
        script_path = self.output_dir / "record_samples.sh"
        with open(script_path, 'w', encoding='utf-8') as f:
            f.write(script_content)
        
        # 设置执行权限
        os.chmod(script_path, 0o755)
        
        logger.info(f"录音脚本已创建: {script_path}")
        return script_path
    
    def validate_samples(self):
        """验证收集的样本"""
        try:
            import librosa
        except ImportError:
            logger.error("请安装librosa: pip install librosa")
            return False
        
        logger.info("验证训练样本...")
        
        # 检查正样本
        positive_files = list(self.positive_dir.glob("*.wav"))
        negative_files = list(self.negative_dir.glob("*.wav"))
        
        logger.info(f"正样本: {len(positive_files)} 个文件")
        logger.info(f"负样本: {len(negative_files)} 个文件")
        
        # 验证音频格式
        valid_positive = 0
        valid_negative = 0
        
        for file_path in positive_files:
            try:
                y, sr = librosa.load(file_path, sr=16000)
                if len(y) > 0 and sr == 16000:
                    valid_positive += 1
            except Exception as e:
                logger.warning(f"无效的正样本文件: {file_path}")
        
        for file_path in negative_files:
            try:
                y, sr = librosa.load(file_path, sr=16000)
                if len(y) > 0 and sr == 16000:
                    valid_negative += 1
            except Exception as e:
                logger.warning(f"无效的负样本文件: {file_path}")
        
        logger.info(f"有效正样本: {valid_positive}/{len(positive_files)}")
        logger.info(f"有效负样本: {valid_negative}/{len(negative_files)}")
        
        # 给出建议
        if valid_positive < 50:
            logger.warning("正样本数量不足，建议至少50个")
        if valid_negative < 100:
            logger.warning("负样本数量不足，建议至少100个")
        if valid_negative < valid_positive * 2:
            logger.warning("负样本数量应该是正样本的2-3倍")
        
        return valid_positive >= 10 and valid_negative >= 20

def main():
    parser = argparse.ArgumentParser(description="收集韩语唤醒词训练数据")
    parser.add_argument("--output_dir", default="training_data/korean_wake_word",
                       help="输出目录")
    parser.add_argument("--method", choices=["guide", "tts", "record", "validate"],
                       default="guide", help="收集方法")
    parser.add_argument("--count", type=int, default=50,
                       help="生成样本数量")
    
    args = parser.parse_args()
    
    collector = KoreanDataCollector(args.output_dir)
    
    if args.method == "guide":
        collector.show_collection_guide()
    
    elif args.method == "tts":
        print("🤖 使用TTS生成训练数据...")
        success1 = collector.generate_tts_samples_gtts(args.count)
        success2 = collector.generate_negative_samples_tts(args.count * 2)
        
        if success1 and success2:
            print("✅ TTS样本生成完成!")
            collector.validate_samples()
        else:
            print("❌ TTS样本生成失败")
    
    elif args.method == "record":
        print("🎙️ 创建录音脚本...")
        script_path = collector.create_recording_script()
        print(f"📝 录音脚本已创建: {script_path}")
        print("运行脚本开始录音:")
        print(f"bash {script_path}")
    
    elif args.method == "validate":
        print("🔍 验证训练数据...")
        if collector.validate_samples():
            print("✅ 数据验证通过，可以开始训练!")
        else:
            print("❌ 数据不足或有问题，请检查")

if __name__ == "__main__":
    main()
