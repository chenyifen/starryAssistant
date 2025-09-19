#!/usr/bin/env python3
"""
韩语唤醒词 Google TTS 音频生成器
专门用于生成 "하이넛지" 唤醒词的高质量训练数据

功能特点:
- 使用 Google Text-to-Speech (gTTS) 生成音频
- 支持多种语音参数变化
- 自动生成正样本和负样本
- 音频后处理和格式转换
- 数据增强和变换

使用方法:
    python scripts/generate_korean_wake_word_tts.py --output_dir training_data/korean_wake_word
    python scripts/generate_korean_wake_word_tts.py --positive_count 200 --negative_count 500
    python scripts/generate_korean_wake_word_tts.py --enhance --noise_level 0.1
"""

import os
import sys
import argparse
import logging
import random
import time
from pathlib import Path
from typing import List, Dict, Tuple, Optional
import json

# 音频处理库
try:
    from gtts import gTTS
    import pygame
    from pydub import AudioSegment
    from pydub.effects import normalize, compress_dynamic_range
    import numpy as np
    import librosa
    import soundfile as sf
except ImportError as e:
    print(f"❌ 缺少必要的依赖库: {e}")
    print("请安装: pip install gtts pygame pydub numpy librosa soundfile")
    sys.exit(1)

# 设置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler('korean_tts_generation.log'),
        logging.StreamHandler()
    ]
)
logger = logging.getLogger(__name__)

class KoreanWakeWordTTSGenerator:
    """韩语唤醒词 TTS 生成器"""
    
    def __init__(self, output_dir: str):
        self.output_dir = Path(output_dir)
        self.wake_word = "하이넛지"
        self.romanized = "hi_nutji"
        
        # 创建目录结构
        self.positive_dir = self.output_dir / "positive"
        self.negative_dir = self.output_dir / "negative"
        self.temp_dir = self.output_dir / "temp"
        self.enhanced_dir = self.output_dir / "enhanced"
        
        for dir_path in [self.positive_dir, self.negative_dir, self.temp_dir, self.enhanced_dir]:
            dir_path.mkdir(parents=True, exist_ok=True)
        
        # TTS 参数配置
        self.tts_configs = [
            {'lang': 'ko', 'slow': False, 'tld': 'com'},
            {'lang': 'ko', 'slow': True, 'tld': 'com'},
            {'lang': 'ko', 'slow': False, 'tld': 'co.kr'},
            {'lang': 'ko', 'slow': True, 'tld': 'co.kr'},
        ]
        
        # 负样本词汇 (韩语常用词)
        self.negative_words = [
            "안녕하세요", "감사합니다", "죄송합니다", "괜찮습니다", "네", "아니요",
            "좋아요", "싫어요", "맛있어요", "예쁘다", "멋있다", "재미있다",
            "음악", "영화", "책", "컴퓨터", "전화", "시간", "날씨", "음식",
            "학교", "회사", "집", "친구", "가족", "사랑", "행복", "건강",
            "돈", "일", "공부", "운동", "여행", "휴식", "잠", "꿈",
            "하이", "안녕", "넛지", "누지", "하이누지", "하이넛", "넛지야",
            "하이넛지야", "하이넛지요", "하이넛지님", "하이넛지씨"
        ]
        
        # 音频参数
        self.target_sample_rate = 16000
        self.target_duration = 2.0  # 秒
        
    def show_generation_info(self):
        """显示生成信息"""
        print("\n" + "="*80)
        print("🎤 韩语唤醒词 '하이넛지' Google TTS 音频生成器")
        print("="*80)
        
        print(f"\n📋 生成配置:")
        print(f"• 唤醒词: {self.wake_word}")
        print(f"• 罗马音: {self.romanized}")
        print(f"• 输出目录: {self.output_dir}")
        print(f"• 目标采样率: {self.target_sample_rate} Hz")
        print(f"• 目标时长: {self.target_duration} 秒")
        
        print(f"\n🔧 TTS 配置:")
        for i, config in enumerate(self.tts_configs, 1):
            print(f"  {i}. 语言: {config['lang']}, 慢速: {config['slow']}, TLD: {config['tld']}")
        
        print(f"\n📝 负样本词汇数量: {len(self.negative_words)}")
        print("\n" + "="*80)
    
    def generate_positive_samples(self, count: int = 100) -> int:
        """生成正样本 (唤醒词)"""
        logger.info(f"开始生成 {count} 个正样本...")
        
        generated_count = 0
        
        for i in range(count):
            try:
                # 随机选择TTS配置
                config = random.choice(self.tts_configs)
                
                # 生成文件名
                timestamp = int(time.time() * 1000)
                filename = f"positive_{timestamp}_{i:04d}.wav"
                temp_mp3 = self.temp_dir / f"temp_{timestamp}.mp3"
                output_path = self.positive_dir / filename
                
                # 生成TTS
                logger.debug(f"生成正样本 {i+1}/{count}: {config}")
                tts = gTTS(
                    text=self.wake_word,
                    lang=config['lang'],
                    slow=config['slow'],
                    tld=config.get('tld', 'com')
                )
                
                # 保存为临时MP3
                tts.save(str(temp_mp3))
                
                # 转换为WAV并处理
                if self._convert_and_process_audio(temp_mp3, output_path):
                    generated_count += 1
                    if generated_count % 10 == 0:
                        logger.info(f"已生成 {generated_count} 个正样本")
                
                # 清理临时文件
                if temp_mp3.exists():
                    temp_mp3.unlink()
                
                # 添加随机延迟避免请求过快
                time.sleep(random.uniform(0.1, 0.3))
                
            except Exception as e:
                logger.error(f"生成正样本 {i+1} 失败: {e}")
                continue
        
        logger.info(f"正样本生成完成，共 {generated_count} 个文件")
        return generated_count
    
    def generate_negative_samples(self, count: int = 200) -> int:
        """生成负样本 (非唤醒词)"""
        logger.info(f"开始生成 {count} 个负样本...")
        
        generated_count = 0
        
        for i in range(count):
            try:
                # 随机选择负样本词汇和TTS配置
                word = random.choice(self.negative_words)
                config = random.choice(self.tts_configs)
                
                # 生成文件名
                timestamp = int(time.time() * 1000)
                filename = f"negative_{timestamp}_{i:04d}.wav"
                temp_mp3 = self.temp_dir / f"temp_neg_{timestamp}.mp3"
                output_path = self.negative_dir / filename
                
                # 生成TTS
                logger.debug(f"生成负样本 {i+1}/{count}: '{word}' with {config}")
                tts = gTTS(
                    text=word,
                    lang=config['lang'],
                    slow=config['slow'],
                    tld=config.get('tld', 'com')
                )
                
                # 保存为临时MP3
                tts.save(str(temp_mp3))
                
                # 转换为WAV并处理
                if self._convert_and_process_audio(temp_mp3, output_path):
                    generated_count += 1
                    if generated_count % 20 == 0:
                        logger.info(f"已生成 {generated_count} 个负样本")
                
                # 清理临时文件
                if temp_mp3.exists():
                    temp_mp3.unlink()
                
                # 添加随机延迟
                time.sleep(random.uniform(0.1, 0.3))
                
            except Exception as e:
                logger.error(f"生成负样本 {i+1} 失败: {e}")
                continue
        
        logger.info(f"负样本生成完成，共 {generated_count} 个文件")
        return generated_count
    
    def _convert_and_process_audio(self, input_path: Path, output_path: Path) -> bool:
        """转换并处理音频文件"""
        try:
            # 加载MP3文件
            audio = AudioSegment.from_mp3(str(input_path))
            
            # 转换为单声道
            if audio.channels > 1:
                audio = audio.set_channels(1)
            
            # 设置采样率
            audio = audio.set_frame_rate(self.target_sample_rate)
            
            # 标准化音量
            audio = normalize(audio)
            
            # 动态范围压缩
            audio = compress_dynamic_range(audio)
            
            # 调整时长
            target_length_ms = int(self.target_duration * 1000)
            if len(audio) > target_length_ms:
                # 如果太长，从中间截取
                start = (len(audio) - target_length_ms) // 2
                audio = audio[start:start + target_length_ms]
            elif len(audio) < target_length_ms:
                # 如果太短，添加静音
                silence_needed = target_length_ms - len(audio)
                silence_before = silence_needed // 2
                silence_after = silence_needed - silence_before
                
                silence_seg = AudioSegment.silent(duration=silence_before)
                audio = silence_seg + audio + AudioSegment.silent(duration=silence_after)
            
            # 保存为WAV
            audio.export(str(output_path), format="wav")
            
            return True
            
        except Exception as e:
            logger.error(f"音频处理失败 {input_path} -> {output_path}: {e}")
            return False
    
    def enhance_audio_data(self, noise_level: float = 0.05, pitch_shift_range: float = 2.0):
        """音频数据增强"""
        logger.info("开始音频数据增强...")
        
        # 处理正样本
        positive_files = list(self.positive_dir.glob("*.wav"))
        self._enhance_audio_files(positive_files, "positive", noise_level, pitch_shift_range)
        
        # 处理负样本 (较少增强)
        negative_files = list(self.negative_dir.glob("*.wav"))[:50]  # 只增强部分负样本
        self._enhance_audio_files(negative_files, "negative", noise_level * 0.5, pitch_shift_range * 0.5)
        
        logger.info("音频数据增强完成")
    
    def _enhance_audio_files(self, files: List[Path], sample_type: str, noise_level: float, pitch_shift_range: float):
        """增强音频文件"""
        for i, file_path in enumerate(files):
            try:
                # 加载音频
                y, sr = librosa.load(str(file_path), sr=self.target_sample_rate)
                
                # 1. 添加噪声
                if noise_level > 0:
                    noise = np.random.normal(0, noise_level, y.shape)
                    y_noise = y + noise
                    
                    # 保存噪声版本
                    noise_path = self.enhanced_dir / f"{sample_type}_noise_{file_path.stem}.wav"
                    sf.write(str(noise_path), y_noise, sr)
                
                # 2. 音调变换
                if pitch_shift_range > 0:
                    # 向上音调变换
                    pitch_shift_up = random.uniform(0.5, pitch_shift_range)
                    y_pitch_up = librosa.effects.pitch_shift(y, sr=sr, n_steps=pitch_shift_up)
                    
                    pitch_up_path = self.enhanced_dir / f"{sample_type}_pitch_up_{file_path.stem}.wav"
                    sf.write(str(pitch_up_path), y_pitch_up, sr)
                    
                    # 向下音调变换
                    pitch_shift_down = random.uniform(-pitch_shift_range, -0.5)
                    y_pitch_down = librosa.effects.pitch_shift(y, sr=sr, n_steps=pitch_shift_down)
                    
                    pitch_down_path = self.enhanced_dir / f"{sample_type}_pitch_down_{file_path.stem}.wav"
                    sf.write(str(pitch_down_path), y_pitch_down, sr)
                
                # 3. 时间拉伸
                stretch_factor = random.uniform(0.9, 1.1)
                y_stretch = librosa.effects.time_stretch(y, rate=stretch_factor)
                
                # 调整长度
                target_length = int(self.target_duration * sr)
                if len(y_stretch) > target_length:
                    y_stretch = y_stretch[:target_length]
                elif len(y_stretch) < target_length:
                    y_stretch = np.pad(y_stretch, (0, target_length - len(y_stretch)))
                
                stretch_path = self.enhanced_dir / f"{sample_type}_stretch_{file_path.stem}.wav"
                sf.write(str(stretch_path), y_stretch, sr)
                
                if (i + 1) % 10 == 0:
                    logger.info(f"已增强 {i + 1} 个 {sample_type} 文件")
                    
            except Exception as e:
                logger.error(f"增强音频文件失败 {file_path}: {e}")
    
    def generate_metadata(self):
        """生成元数据文件"""
        metadata = {
            "wake_word": self.wake_word,
            "romanized": self.romanized,
            "target_sample_rate": self.target_sample_rate,
            "target_duration": self.target_duration,
            "generation_time": time.strftime("%Y-%m-%d %H:%M:%S"),
            "tts_configs": self.tts_configs,
            "negative_words_count": len(self.negative_words),
            "statistics": {
                "positive_samples": len(list(self.positive_dir.glob("*.wav"))),
                "negative_samples": len(list(self.negative_dir.glob("*.wav"))),
                "enhanced_samples": len(list(self.enhanced_dir.glob("*.wav")))
            }
        }
        
        metadata_path = self.output_dir / "metadata.json"
        with open(metadata_path, 'w', encoding='utf-8') as f:
            json.dump(metadata, f, ensure_ascii=False, indent=2)
        
        logger.info(f"元数据已保存到: {metadata_path}")
        return metadata
    
    def show_statistics(self):
        """显示统计信息"""
        positive_count = len(list(self.positive_dir.glob("*.wav")))
        negative_count = len(list(self.negative_dir.glob("*.wav")))
        enhanced_count = len(list(self.enhanced_dir.glob("*.wav")))
        
        print("\n" + "="*60)
        print("📊 生成统计")
        print("="*60)
        print(f"✅ 正样本: {positive_count} 个")
        print(f"❌ 负样本: {negative_count} 个")
        print(f"🔧 增强样本: {enhanced_count} 个")
        print(f"📁 总计: {positive_count + negative_count + enhanced_count} 个")
        print("="*60)

def main():
    parser = argparse.ArgumentParser(description="韩语唤醒词 Google TTS 音频生成器")
    parser.add_argument("--output_dir", default="training_data/korean_wake_word_tts",
                       help="输出目录 (默认: training_data/korean_wake_word_tts)")
    parser.add_argument("--positive_count", type=int, default=100,
                       help="正样本数量 (默认: 100)")
    parser.add_argument("--negative_count", type=int, default=200,
                       help="负样本数量 (默认: 200)")
    parser.add_argument("--enhance", action="store_true",
                       help="启用音频数据增强")
    parser.add_argument("--noise_level", type=float, default=0.05,
                       help="噪声级别 (默认: 0.05)")
    parser.add_argument("--pitch_shift_range", type=float, default=2.0,
                       help="音调变换范围 (默认: 2.0)")
    parser.add_argument("--show_info_only", action="store_true",
                       help="仅显示信息，不生成音频")
    
    args = parser.parse_args()
    
    # 创建生成器
    generator = KoreanWakeWordTTSGenerator(args.output_dir)
    generator.show_generation_info()
    
    if args.show_info_only:
        return
    
    try:
        # 生成正样本
        positive_generated = generator.generate_positive_samples(args.positive_count)
        
        # 生成负样本
        negative_generated = generator.generate_negative_samples(args.negative_count)
        
        # 音频增强
        if args.enhance:
            generator.enhance_audio_data(args.noise_level, args.pitch_shift_range)
        
        # 生成元数据
        metadata = generator.generate_metadata()
        
        # 显示统计
        generator.show_statistics()
        
        print(f"\n🎉 音频生成完成！")
        print(f"📁 输出目录: {generator.output_dir}")
        print(f"✅ 正样本: {positive_generated} 个")
        print(f"❌ 负样本: {negative_generated} 个")
        
        if args.enhance:
            enhanced_count = len(list(generator.enhanced_dir.glob("*.wav")))
            print(f"🔧 增强样本: {enhanced_count} 个")
        
    except KeyboardInterrupt:
        logger.info("用户中断生成过程")
    except Exception as e:
        logger.error(f"生成过程出错: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()
