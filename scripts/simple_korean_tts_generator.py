#!/usr/bin/env python3
"""
简化版韩语唤醒词 Google TTS 生成器
专门用于生成 "하이넛지" 唤醒词的训练数据

依赖最少，易于使用：
- 只需要 gtts 和 pydub
- 自动处理音频格式转换
- 生成正样本和负样本

安装依赖:
    pip install gtts pydub

使用方法:
    python scripts/simple_korean_tts_generator.py
    python scripts/simple_korean_tts_generator.py --count 50
    python scripts/simple_korean_tts_generator.py --output_dir my_training_data
"""

import os
import sys
import argparse
import time
import random
from pathlib import Path

try:
    from gtts import gTTS
    from pydub import AudioSegment
except ImportError as e:
    print(f"❌ 缺少必要的依赖库: {e}")
    print("请安装: pip install gtts pydub")
    print("如果是 macOS，可能还需要: brew install ffmpeg")
    sys.exit(1)

class SimpleKoreanTTSGenerator:
    """简化版韩语TTS生成器"""
    
    def __init__(self, output_dir: str = "training_data/korean_tts"):
        self.output_dir = Path(output_dir)
        self.wake_word = "하이넛지"
        
        # 创建目录
        self.positive_dir = self.output_dir / "positive"
        self.negative_dir = self.output_dir / "negative"
        self.temp_dir = self.output_dir / "temp"
        
        for dir_path in [self.positive_dir, self.negative_dir, self.temp_dir]:
            dir_path.mkdir(parents=True, exist_ok=True)
        
        # 负样本词汇
        self.negative_words = [
            # 常用韩语词汇
            "안녕하세요", "감사합니다", "죄송합니다", "괜찮습니다", "네", "아니요",
            "좋아요", "싫어요", "맛있어요", "예쁘다", "멋있다", "재미있다",
            "음악", "영화", "책", "컴퓨터", "전화", "시간", "날씨", "음식",
            "학교", "회사", "집", "친구", "가족", "사랑", "행복", "건강",
            
            # 相似但不同的词汇
            "하이", "안녕", "넛지", "누지", "하이누지", "하이넛", "넛지야",
            "하이넛지야", "하이넛지요", "하이넛지님", "하이넛지씨",
            
            # 数字和常用表达
            "하나", "둘", "셋", "넷", "다섯", "여섯", "일곱", "여덟", "아홉", "열",
            "오늘", "내일", "어제", "지금", "나중에", "빨리", "천천히", "많이", "조금"
        ]
        
        print(f"🎤 韩语唤醒词 TTS 生成器")
        print(f"📝 唤醒词: {self.wake_word}")
        print(f"📁 输出目录: {self.output_dir}")
        print(f"📊 负样本词汇: {len(self.negative_words)} 个")
    
    def generate_positive_samples(self, count: int = 30):
        """生成正样本"""
        print(f"\n🔊 生成 {count} 个正样本...")
        
        success_count = 0
        
        # TTS 配置变化
        configs = [
            {'slow': False, 'tld': 'com'},
            {'slow': True, 'tld': 'com'},
            {'slow': False, 'tld': 'co.kr'},
            {'slow': True, 'tld': 'co.kr'},
        ]
        
        for i in range(count):
            try:
                config = random.choice(configs)
                
                print(f"  生成正样本 {i+1}/{count} (慢速: {config['slow']}, TLD: {config['tld']})")
                
                # 生成TTS
                tts = gTTS(
                    text=self.wake_word,
                    lang='ko',
                    slow=config['slow'],
                    tld=config['tld']
                )
                
                # 文件路径
                timestamp = int(time.time() * 1000)
                temp_file = self.temp_dir / f"temp_pos_{timestamp}.mp3"
                output_file = self.positive_dir / f"positive_{timestamp}_{i:03d}.wav"
                
                # 保存并转换
                tts.save(str(temp_file))
                
                if self._convert_to_wav(temp_file, output_file):
                    success_count += 1
                
                # 清理临时文件
                if temp_file.exists():
                    temp_file.unlink()
                
                # 延迟避免请求过快
                time.sleep(random.uniform(0.2, 0.5))
                
            except Exception as e:
                print(f"    ❌ 生成失败: {e}")
        
        print(f"✅ 正样本生成完成: {success_count}/{count}")
        return success_count
    
    def generate_negative_samples(self, count: int = 60):
        """生成负样本"""
        print(f"\n🚫 生成 {count} 个负样本...")
        
        success_count = 0
        
        for i in range(count):
            try:
                # 随机选择词汇和配置
                word = random.choice(self.negative_words)
                slow = random.choice([True, False])
                tld = random.choice(['com', 'co.kr'])
                
                print(f"  生成负样本 {i+1}/{count}: '{word}' (慢速: {slow})")
                
                # 生成TTS
                tts = gTTS(text=word, lang='ko', slow=slow, tld=tld)
                
                # 文件路径
                timestamp = int(time.time() * 1000)
                temp_file = self.temp_dir / f"temp_neg_{timestamp}.mp3"
                output_file = self.negative_dir / f"negative_{timestamp}_{i:03d}.wav"
                
                # 保存并转换
                tts.save(str(temp_file))
                
                if self._convert_to_wav(temp_file, output_file):
                    success_count += 1
                
                # 清理临时文件
                if temp_file.exists():
                    temp_file.unlink()
                
                # 延迟
                time.sleep(random.uniform(0.1, 0.3))
                
            except Exception as e:
                print(f"    ❌ 生成失败: {e}")
        
        print(f"✅ 负样本生成完成: {success_count}/{count}")
        return success_count
    
    def _convert_to_wav(self, mp3_path: Path, wav_path: Path) -> bool:
        """将MP3转换为WAV"""
        try:
            # 加载MP3
            audio = AudioSegment.from_mp3(str(mp3_path))
            
            # 转换为单声道，16kHz
            audio = audio.set_channels(1)
            audio = audio.set_frame_rate(16000)
            
            # 标准化音量
            audio = audio.normalize()
            
            # 调整长度到2秒
            target_length = 2000  # 毫秒
            if len(audio) > target_length:
                # 太长则截取中间部分
                start = (len(audio) - target_length) // 2
                audio = audio[start:start + target_length]
            elif len(audio) < target_length:
                # 太短则添加静音
                silence_needed = target_length - len(audio)
                silence_before = silence_needed // 2
                silence_after = silence_needed - silence_before
                
                silence = AudioSegment.silent(duration=silence_before)
                audio = silence + audio + AudioSegment.silent(duration=silence_after)
            
            # 保存为WAV
            audio.export(str(wav_path), format="wav")
            return True
            
        except Exception as e:
            print(f"    ❌ 音频转换失败: {e}")
            return False
    
    def show_statistics(self):
        """显示统计信息"""
        positive_count = len(list(self.positive_dir.glob("*.wav")))
        negative_count = len(list(self.negative_dir.glob("*.wav")))
        
        print(f"\n📊 生成统计:")
        print(f"  ✅ 正样本: {positive_count} 个")
        print(f"  ❌ 负样本: {negative_count} 个")
        print(f"  📁 总计: {positive_count + negative_count} 个")
        print(f"  📂 输出目录: {self.output_dir}")
    
    def create_file_list(self):
        """创建文件列表用于训练"""
        file_list_path = self.output_dir / "file_list.txt"
        
        with open(file_list_path, 'w', encoding='utf-8') as f:
            # 正样本
            for wav_file in sorted(self.positive_dir.glob("*.wav")):
                f.write(f"{wav_file.relative_to(self.output_dir)} 1\n")
            
            # 负样本
            for wav_file in sorted(self.negative_dir.glob("*.wav")):
                f.write(f"{wav_file.relative_to(self.output_dir)} 0\n")
        
        print(f"📝 文件列表已保存: {file_list_path}")

def main():
    parser = argparse.ArgumentParser(description="简化版韩语唤醒词 TTS 生成器")
    parser.add_argument("--output_dir", default="training_data/korean_tts",
                       help="输出目录")
    parser.add_argument("--positive_count", type=int, default=30,
                       help="正样本数量")
    parser.add_argument("--negative_count", type=int, default=60,
                       help="负样本数量")
    parser.add_argument("--count", type=int,
                       help="总样本数量 (会自动分配正负样本比例)")
    
    args = parser.parse_args()
    
    # 如果指定了总数量，自动分配
    if args.count:
        args.positive_count = args.count // 3
        args.negative_count = args.count - args.positive_count
    
    print("🎤 简化版韩语唤醒词 TTS 生成器")
    print("="*50)
    
    try:
        generator = SimpleKoreanTTSGenerator(args.output_dir)
        
        # 生成样本
        pos_count = generator.generate_positive_samples(args.positive_count)
        neg_count = generator.generate_negative_samples(args.negative_count)
        
        # 创建文件列表
        generator.create_file_list()
        
        # 显示统计
        generator.show_statistics()
        
        print(f"\n🎉 生成完成!")
        print(f"📁 请查看输出目录: {generator.output_dir}")
        print(f"📝 训练文件列表: {generator.output_dir}/file_list.txt")
        
    except KeyboardInterrupt:
        print("\n⏹️  用户中断")
    except Exception as e:
        print(f"\n❌ 错误: {e}")

if __name__ == "__main__":
    main()
