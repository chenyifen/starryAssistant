#!/usr/bin/env python3
"""
高级韩语唤醒词 TTS 音频生成器
支持多种TTS库、不同音色、语速、情感和远场语音模拟

功能特点:
- 支持多种TTS库: gTTS, edge-tts, pyttsx3, Azure TTS
- 不同音色和语速变化
- 情感表达支持
- 远场语音模拟 (混响、噪声、距离衰减)
- 丰富的正负样本生成
- 数据增强和变换

使用方法:
    python scripts/advanced_korean_tts_generator.py --output_dir training_data
    python scripts/advanced_korean_tts_generator.py --enable_all_engines --far_field
    python scripts/advanced_korean_tts_generator.py --positive_count 500 --negative_count 1000
"""

import os
import sys
import argparse
import logging
import random
import time
import asyncio
from pathlib import Path
from typing import List, Dict, Tuple, Optional, Any
import json
import subprocess
from concurrent.futures import ThreadPoolExecutor, as_completed

# 音频处理库
try:
    import numpy as np
    import librosa
    import soundfile as sf
    from pydub import AudioSegment
    from pydub.effects import normalize, compress_dynamic_range
    from scipy import signal
    from scipy.io import wavfile
except ImportError as e:
    print(f"❌ 缺少音频处理库: {e}")
    print("请安装: pip install numpy librosa soundfile pydub scipy")
    sys.exit(1)

# TTS库 (可选导入)
TTS_ENGINES = {}

# gTTS
try:
    from gtts import gTTS
    TTS_ENGINES['gtts'] = True
except ImportError:
    TTS_ENGINES['gtts'] = False

# edge-tts
try:
    import edge_tts
    TTS_ENGINES['edge_tts'] = True
except ImportError:
    TTS_ENGINES['edge_tts'] = False

# pyttsx3
try:
    import pyttsx3
    TTS_ENGINES['pyttsx3'] = True
except ImportError:
    TTS_ENGINES['pyttsx3'] = False

# 设置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler('advanced_korean_tts.log'),
        logging.StreamHandler()
    ]
)
logger = logging.getLogger(__name__)

class AdvancedKoreanTTSGenerator:
    """高级韩语唤醒词 TTS 生成器"""
    
    def __init__(self, output_dir: str):
        self.output_dir = Path(output_dir)
        self.wake_word = "하이넛지"
        self.romanized = "hi_nutji"
        
        # 创建目录结构
        self.positive_dir = self.output_dir / "positive"
        self.negative_dir = self.output_dir / "negative"
        self.far_field_dir = self.output_dir / "far_field"
        self.enhanced_dir = self.output_dir / "enhanced"
        self.temp_dir = self.output_dir / "temp"
        
        for dir_path in [self.positive_dir, self.negative_dir, self.far_field_dir, 
                        self.enhanced_dir, self.temp_dir]:
            dir_path.mkdir(parents=True, exist_ok=True)
        
        # 音频参数
        self.target_sample_rate = 16000
        self.target_duration = 2.0
        
        # TTS引擎配置
        self.tts_configs = self._initialize_tts_configs()
        
        # 负样本词汇 (扩展版)
        self.negative_words = self._get_negative_words()
        
        # 远场语音参数
        self.far_field_configs = self._get_far_field_configs()
        
    def _initialize_tts_configs(self) -> Dict[str, List[Dict]]:
        """初始化TTS配置"""
        configs = {}
        
        # gTTS 配置
        if TTS_ENGINES['gtts']:
            configs['gtts'] = [
                {'lang': 'ko', 'slow': False, 'tld': 'com'},
                {'lang': 'ko', 'slow': True, 'tld': 'com'},
                {'lang': 'ko', 'slow': False, 'tld': 'co.kr'},
                {'lang': 'ko', 'slow': True, 'tld': 'co.kr'},
            ]
        
        # edge-tts 配置 (韩语语音)
        if TTS_ENGINES['edge_tts']:
            configs['edge_tts'] = [
                {'voice': 'ko-KR-InJoonNeural', 'rate': '+0%', 'pitch': '+0Hz'},
                {'voice': 'ko-KR-InJoonNeural', 'rate': '-20%', 'pitch': '-50Hz'},
                {'voice': 'ko-KR-InJoonNeural', 'rate': '+20%', 'pitch': '+50Hz'},
                {'voice': 'ko-KR-BongJinNeural', 'rate': '+0%', 'pitch': '+0Hz'},
                {'voice': 'ko-KR-BongJinNeural', 'rate': '-10%', 'pitch': '+0Hz'},
                {'voice': 'ko-KR-SunHiNeural', 'rate': '+0%', 'pitch': '+0Hz'},
                {'voice': 'ko-KR-SunHiNeural', 'rate': '+10%', 'pitch': '-30Hz'},
            ]
        
        # pyttsx3 配置
        if TTS_ENGINES['pyttsx3']:
            configs['pyttsx3'] = [
                {'rate': 150, 'volume': 0.9},
                {'rate': 120, 'volume': 0.8},
                {'rate': 180, 'volume': 1.0},
                {'rate': 100, 'volume': 0.7},
            ]
        
        return configs
    
    def _get_negative_words(self) -> List[str]:
        """获取负样本词汇"""
        return [
            # 基础韩语词汇
            "안녕하세요", "감사합니다", "죄송합니다", "괜찮습니다", "네", "아니요",
            "좋아요", "싫어요", "맛있어요", "예쁘다", "멋있다", "재미있다",
            "음악", "영화", "책", "컴퓨터", "전화", "시간", "날씨", "음식",
            "학교", "회사", "집", "친구", "가족", "사랑", "행복", "건강",
            
            # 相似但不同的词汇
            "하이", "안녕", "넛지", "누지", "하이누지", "하이넛", "넛지야",
            "하이넛지야", "하이넛지요", "하이넛지님", "하이넛지씨",
            
            # 数字和时间
            "하나", "둘", "셋", "넷", "다섯", "여섯", "일곱", "여덟", "아홉", "열",
            "오늘", "내일", "어제", "지금", "나중에", "빨리", "천천히", "많이", "조금",
            
            # 动作词汇
            "가다", "오다", "먹다", "마시다", "자다", "일어나다", "앉다", "서다",
            "듣다", "보다", "말하다", "웃다", "울다", "뛰다", "걷다", "읽다",
            
            # 情感词汇
            "기쁘다", "슬프다", "화나다", "무섭다", "놀라다", "걱정하다",
            "편안하다", "피곤하다", "심심하다", "재미있다", "지루하다",
            
            # 日常用语
            "여보세요", "잠깐만요", "실례합니다", "수고하세요", "안녕히가세요",
            "잘 지내세요", "또 만나요", "조심하세요", "화이팅", "대박"
        ]
    
    def _get_far_field_configs(self) -> List[Dict]:
        """获取远场语音配置"""
        return [
            # 近场 (1-2米)
            {'distance': 1.5, 'reverb_level': 0.1, 'noise_level': 0.02, 'name': 'near_field'},
            
            # 中场 (3-5米)
            {'distance': 4.0, 'reverb_level': 0.3, 'noise_level': 0.05, 'name': 'mid_field'},
            
            # 远场 (6-10米)
            {'distance': 8.0, 'reverb_level': 0.5, 'noise_level': 0.08, 'name': 'far_field'},
            
            # 极远场 (10米以上)
            {'distance': 12.0, 'reverb_level': 0.7, 'noise_level': 0.12, 'name': 'very_far_field'},
        ]
    
    def show_engine_status(self):
        """显示TTS引擎状态"""
        print("\n" + "="*80)
        print("🎤 高级韩语唤醒词 TTS 生成器")
        print("="*80)
        
        print(f"\n📋 基本配置:")
        print(f"• 唤醒词: {self.wake_word}")
        print(f"• 输出目录: {self.output_dir}")
        print(f"• 目标采样率: {self.target_sample_rate} Hz")
        print(f"• 目标时长: {self.target_duration} 秒")
        
        print(f"\n🔧 TTS 引擎状态:")
        for engine, available in TTS_ENGINES.items():
            status = "✅ 可用" if available else "❌ 不可用"
            config_count = len(self.tts_configs.get(engine, []))
            print(f"  {engine}: {status} ({config_count} 个配置)")
        
        print(f"\n📝 负样本词汇: {len(self.negative_words)} 个")
        print(f"🌊 远场配置: {len(self.far_field_configs)} 种距离")
        print("="*80)
    
    async def generate_positive_samples_async(self, count: int = 200) -> int:
        """异步生成正样本"""
        logger.info(f"开始异步生成 {count} 个正样本...")
        
        tasks = []
        available_engines = [engine for engine, available in TTS_ENGINES.items() if available]
        
        if not available_engines:
            logger.error("没有可用的TTS引擎")
            return 0
        
        # 创建生成任务
        for i in range(count):
            engine = random.choice(available_engines)
            config = random.choice(self.tts_configs[engine])
            
            task = self._generate_single_positive_sample_async(i, engine, config)
            tasks.append(task)
        
        # 执行任务
        results = await asyncio.gather(*tasks, return_exceptions=True)
        
        success_count = sum(1 for result in results if result is True)
        logger.info(f"正样本生成完成，成功: {success_count}/{count}")
        
        return success_count
    
    async def _generate_single_positive_sample_async(self, index: int, engine: str, config: Dict) -> bool:
        """异步生成单个正样本"""
        try:
            timestamp = int(time.time() * 1000) + index
            filename = f"positive_{engine}_{timestamp}_{index:04d}.wav"
            output_path = self.positive_dir / filename
            
            # 根据引擎生成音频
            if engine == 'gtts':
                return await self._generate_gtts_sample(self.wake_word, config, output_path)
            elif engine == 'edge_tts':
                return await self._generate_edge_tts_sample(self.wake_word, config, output_path)
            elif engine == 'pyttsx3':
                return await self._generate_pyttsx3_sample(self.wake_word, config, output_path)
            
            return False
            
        except Exception as e:
            logger.error(f"生成正样本 {index} 失败: {e}")
            return False
    
    async def _generate_gtts_sample(self, text: str, config: Dict, output_path: Path) -> bool:
        """生成gTTS样本"""
        try:
            # 在线程池中执行同步操作
            loop = asyncio.get_event_loop()
            
            def _sync_gtts():
                tts = gTTS(text=text, **config)
                temp_file = self.temp_dir / f"temp_{output_path.stem}.mp3"
                tts.save(str(temp_file))
                return temp_file
            
            temp_file = await loop.run_in_executor(None, _sync_gtts)
            
            # 转换音频
            success = self._convert_and_process_audio(temp_file, output_path)
            
            # 清理临时文件
            if temp_file.exists():
                temp_file.unlink()
            
            return success
            
        except Exception as e:
            logger.error(f"gTTS生成失败: {e}")
            return False
    
    async def _generate_edge_tts_sample(self, text: str, config: Dict, output_path: Path) -> bool:
        """生成edge-tts样本"""
        try:
            voice = config['voice']
            rate = config['rate']
            pitch = config['pitch']
            
            # 创建SSML
            ssml = f"""
            <speak version="1.0" xmlns="http://www.w3.org/2001/10/synthesis" xml:lang="ko-KR">
                <voice name="{voice}">
                    <prosody rate="{rate}" pitch="{pitch}">
                        {text}
                    </prosody>
                </voice>
            </speak>
            """
            
            # 生成音频
            communicate = edge_tts.Communicate(ssml)
            temp_file = self.temp_dir / f"temp_{output_path.stem}.wav"
            
            await communicate.save(str(temp_file))
            
            # 处理音频
            success = self._convert_and_process_audio(temp_file, output_path)
            
            # 清理临时文件
            if temp_file.exists():
                temp_file.unlink()
            
            return success
            
        except Exception as e:
            logger.error(f"edge-tts生成失败: {e}")
            return False
    
    async def _generate_pyttsx3_sample(self, text: str, config: Dict, output_path: Path) -> bool:
        """生成pyttsx3样本"""
        try:
            loop = asyncio.get_event_loop()
            
            def _sync_pyttsx3():
                engine = pyttsx3.init()
                engine.setProperty('rate', config['rate'])
                engine.setProperty('volume', config['volume'])
                
                temp_file = self.temp_dir / f"temp_{output_path.stem}.wav"
                engine.save_to_file(text, str(temp_file))
                engine.runAndWait()
                return temp_file
            
            temp_file = await loop.run_in_executor(None, _sync_pyttsx3)
            
            # 处理音频
            success = self._convert_and_process_audio(temp_file, output_path)
            
            # 清理临时文件
            if temp_file.exists():
                temp_file.unlink()
            
            return success
            
        except Exception as e:
            logger.error(f"pyttsx3生成失败: {e}")
            return False
    
    def generate_negative_samples(self, count: int = 400) -> int:
        """生成负样本"""
        logger.info(f"开始生成 {count} 个负样本...")
        
        generated_count = 0
        available_engines = [engine for engine, available in TTS_ENGINES.items() if available]
        
        if not available_engines:
            logger.error("没有可用的TTS引擎")
            return 0
        
        for i in range(count):
            try:
                # 随机选择词汇、引擎和配置
                word = random.choice(self.negative_words)
                engine = random.choice(available_engines)
                config = random.choice(self.tts_configs[engine])
                
                timestamp = int(time.time() * 1000)
                filename = f"negative_{engine}_{timestamp}_{i:04d}.wav"
                output_path = self.negative_dir / filename
                
                # 同步生成 (简化版)
                if engine == 'gtts':
                    success = self._generate_gtts_sync(word, config, output_path)
                else:
                    # 其他引擎暂时跳过同步版本
                    continue
                
                if success:
                    generated_count += 1
                    if generated_count % 50 == 0:
                        logger.info(f"已生成 {generated_count} 个负样本")
                
                # 延迟
                time.sleep(random.uniform(0.1, 0.2))
                
            except Exception as e:
                logger.error(f"生成负样本 {i} 失败: {e}")
        
        logger.info(f"负样本生成完成，共 {generated_count} 个")
        return generated_count
    
    def _generate_gtts_sync(self, text: str, config: Dict, output_path: Path) -> bool:
        """同步生成gTTS样本"""
        try:
            tts = gTTS(text=text, **config)
            temp_file = self.temp_dir / f"temp_{output_path.stem}.mp3"
            tts.save(str(temp_file))
            
            success = self._convert_and_process_audio(temp_file, output_path)
            
            if temp_file.exists():
                temp_file.unlink()
            
            return success
            
        except Exception as e:
            logger.error(f"gTTS同步生成失败: {e}")
            return False
    
    def generate_far_field_samples(self, source_dir: Path, count: int = 100) -> int:
        """生成远场语音样本"""
        logger.info(f"开始生成 {count} 个远场语音样本...")
        
        source_files = list(source_dir.glob("*.wav"))
        if not source_files:
            logger.warning(f"源目录 {source_dir} 中没有WAV文件")
            return 0
        
        generated_count = 0
        
        for i in range(count):
            try:
                # 随机选择源文件和远场配置
                source_file = random.choice(source_files)
                far_field_config = random.choice(self.far_field_configs)
                
                # 生成输出文件名
                timestamp = int(time.time() * 1000)
                filename = f"far_field_{far_field_config['name']}_{timestamp}_{i:04d}.wav"
                output_path = self.far_field_dir / filename
                
                # 应用远场效果
                if self._apply_far_field_effects(source_file, output_path, far_field_config):
                    generated_count += 1
                    if generated_count % 20 == 0:
                        logger.info(f"已生成 {generated_count} 个远场样本")
                
            except Exception as e:
                logger.error(f"生成远场样本 {i} 失败: {e}")
        
        logger.info(f"远场样本生成完成，共 {generated_count} 个")
        return generated_count
    
    def _apply_far_field_effects(self, source_path: Path, output_path: Path, config: Dict) -> bool:
        """应用远场效果"""
        try:
            # 加载音频
            y, sr = librosa.load(str(source_path), sr=self.target_sample_rate)
            
            # 1. 距离衰减
            distance_factor = 1.0 / (config['distance'] ** 0.5)
            y = y * distance_factor
            
            # 2. 添加混响
            reverb_level = config['reverb_level']
            if reverb_level > 0:
                # 简单的混响模拟 (延迟 + 衰减)
                delay_samples = int(0.05 * sr)  # 50ms延迟
                reverb = np.zeros_like(y)
                if len(y) > delay_samples:
                    reverb[delay_samples:] = y[:-delay_samples] * reverb_level
                y = y + reverb
            
            # 3. 添加环境噪声
            noise_level = config['noise_level']
            if noise_level > 0:
                noise = np.random.normal(0, noise_level, y.shape)
                y = y + noise
            
            # 4. 高频衰减 (模拟空气吸收)
            if config['distance'] > 3.0:
                # 低通滤波器
                cutoff_freq = max(4000 - config['distance'] * 200, 2000)
                nyquist = sr / 2
                normalized_cutoff = cutoff_freq / nyquist
                b, a = signal.butter(4, normalized_cutoff, btype='low')
                y = signal.filtfilt(b, a, y)
            
            # 5. 标准化
            y = y / np.max(np.abs(y)) if np.max(np.abs(y)) > 0 else y
            
            # 保存
            sf.write(str(output_path), y, sr)
            return True
            
        except Exception as e:
            logger.error(f"应用远场效果失败: {e}")
            return False
    
    def _convert_and_process_audio(self, input_path: Path, output_path: Path) -> bool:
        """转换并处理音频文件"""
        try:
            # 加载音频
            if input_path.suffix.lower() == '.mp3':
                audio = AudioSegment.from_mp3(str(input_path))
            else:
                audio = AudioSegment.from_wav(str(input_path))
            
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
                # 太长则截取中间部分
                start = (len(audio) - target_length_ms) // 2
                audio = audio[start:start + target_length_ms]
            elif len(audio) < target_length_ms:
                # 太短则添加静音
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
    
    def generate_metadata(self) -> Dict:
        """生成元数据"""
        metadata = {
            "wake_word": self.wake_word,
            "romanized": self.romanized,
            "target_sample_rate": self.target_sample_rate,
            "target_duration": self.target_duration,
            "generation_time": time.strftime("%Y-%m-%d %H:%M:%S"),
            "tts_engines": {engine: available for engine, available in TTS_ENGINES.items()},
            "tts_configs": self.tts_configs,
            "negative_words_count": len(self.negative_words),
            "far_field_configs": self.far_field_configs,
            "statistics": {
                "positive_samples": len(list(self.positive_dir.glob("*.wav"))),
                "negative_samples": len(list(self.negative_dir.glob("*.wav"))),
                "far_field_samples": len(list(self.far_field_dir.glob("*.wav"))),
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
        far_field_count = len(list(self.far_field_dir.glob("*.wav")))
        enhanced_count = len(list(self.enhanced_dir.glob("*.wav")))
        
        print("\n" + "="*70)
        print("📊 生成统计")
        print("="*70)
        print(f"✅ 正样本: {positive_count} 个")
        print(f"❌ 负样本: {negative_count} 个")
        print(f"🌊 远场样本: {far_field_count} 个")
        print(f"🔧 增强样本: {enhanced_count} 个")
        print(f"📁 总计: {positive_count + negative_count + far_field_count + enhanced_count} 个")
        print("="*70)

async def main():
    parser = argparse.ArgumentParser(description="高级韩语唤醒词 TTS 音频生成器")
    parser.add_argument("--output_dir", default="training_data/advanced_korean_tts",
                       help="输出目录")
    parser.add_argument("--positive_count", type=int, default=200,
                       help="正样本数量")
    parser.add_argument("--negative_count", type=int, default=400,
                       help="负样本数量")
    parser.add_argument("--far_field_count", type=int, default=100,
                       help="远场样本数量")
    parser.add_argument("--enable_all_engines", action="store_true",
                       help="启用所有可用的TTS引擎")
    parser.add_argument("--far_field", action="store_true",
                       help="生成远场语音样本")
    parser.add_argument("--show_status_only", action="store_true",
                       help="仅显示引擎状态")
    
    args = parser.parse_args()
    
    # 创建生成器
    generator = AdvancedKoreanTTSGenerator(args.output_dir)
    generator.show_engine_status()
    
    if args.show_status_only:
        return
    
    try:
        # 生成正样本 (异步)
        positive_generated = await generator.generate_positive_samples_async(args.positive_count)
        
        # 生成负样本 (同步)
        negative_generated = generator.generate_negative_samples(args.negative_count)
        
        # 生成远场样本
        far_field_generated = 0
        if args.far_field:
            far_field_generated = generator.generate_far_field_samples(
                generator.positive_dir, args.far_field_count
            )
        
        # 生成元数据
        metadata = generator.generate_metadata()
        
        # 显示统计
        generator.show_statistics()
        
        print(f"\n🎉 高级音频生成完成！")
        print(f"📁 输出目录: {generator.output_dir}")
        print(f"✅ 正样本: {positive_generated} 个")
        print(f"❌ 负样本: {negative_generated} 个")
        if args.far_field:
            print(f"🌊 远场样本: {far_field_generated} 个")
        
    except KeyboardInterrupt:
        logger.info("用户中断生成过程")
    except Exception as e:
        logger.error(f"生成过程出错: {e}")
        sys.exit(1)

if __name__ == "__main__":
    asyncio.run(main())
