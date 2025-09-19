#!/usr/bin/env python3
"""
韩语唤醒词批量数据集生成器
一次性生成大量多样化的训练数据，包括远场语音支持

功能特点:
- 批量生成大规模数据集
- 多种TTS引擎并行处理
- 自动数据平衡和质量控制
- 远场语音模拟
- 数据增强和变换
- 训练/验证/测试集划分

使用方法:
    python scripts/batch_generate_korean_dataset.py --total_samples 5000
    python scripts/batch_generate_korean_dataset.py --large_dataset --far_field
    python scripts/batch_generate_korean_dataset.py --custom --positive 2000 --negative 8000
"""

import os
import sys
import argparse
import logging
import asyncio
import random
import time
from pathlib import Path
from typing import Dict, List, Tuple
import json
from concurrent.futures import ThreadPoolExecutor
import shutil

# 导入我们的生成器
sys.path.append(str(Path(__file__).parent))
from advanced_korean_tts_generator import AdvancedKoreanTTSGenerator

# 设置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler('batch_korean_dataset.log'),
        logging.StreamHandler()
    ]
)
logger = logging.getLogger(__name__)

class BatchKoreanDatasetGenerator:
    """批量韩语数据集生成器"""
    
    def __init__(self, output_dir: str):
        self.output_dir = Path(output_dir)
        self.output_dir.mkdir(parents=True, exist_ok=True)
        
        # 创建子目录
        self.train_dir = self.output_dir / "train"
        self.val_dir = self.output_dir / "val" 
        self.test_dir = self.output_dir / "test"
        self.raw_dir = self.output_dir / "raw"
        
        for dir_path in [self.train_dir, self.val_dir, self.test_dir, self.raw_dir]:
            dir_path.mkdir(parents=True, exist_ok=True)
        
        # 数据集配置
        self.dataset_configs = {
            'small': {'positive': 200, 'negative': 800, 'far_field': 100},
            'medium': {'positive': 1000, 'negative': 4000, 'far_field': 500},
            'large': {'positive': 3000, 'negative': 12000, 'far_field': 1500},
            'xlarge': {'positive': 5000, 'negative': 20000, 'far_field': 2500}
        }
        
        # 数据划分比例 (训练:验证:测试)
        self.split_ratios = {'train': 0.7, 'val': 0.15, 'test': 0.15}
        
    def show_dataset_info(self):
        """显示数据集信息"""
        print("\n" + "="*80)
        print("🎯 韩语唤醒词批量数据集生成器")
        print("="*80)
        
        print(f"\n📊 预设数据集配置:")
        for name, config in self.dataset_configs.items():
            total = config['positive'] + config['negative'] + config['far_field']
            print(f"  {name:8s}: 正样本 {config['positive']:5d} + 负样本 {config['negative']:5d} + 远场 {config['far_field']:4d} = 总计 {total:5d}")
        
        print(f"\n📁 输出目录结构:")
        print(f"  {self.output_dir}/")
        print(f"  ├── raw/           # 原始生成数据")
        print(f"  ├── train/         # 训练集 (70%)")
        print(f"  ├── val/           # 验证集 (15%)")
        print(f"  ├── test/          # 测试集 (15%)")
        print(f"  └── metadata.json  # 数据集元信息")
        
        print("="*80)
    
    async def generate_dataset(self, config_name: str = None, custom_config: Dict = None, 
                             enable_far_field: bool = True) -> Dict:
        """生成完整数据集"""
        
        # 确定生成配置
        if custom_config:
            config = custom_config
            config_name = "custom"
        elif config_name and config_name in self.dataset_configs:
            config = self.dataset_configs[config_name]
        else:
            config = self.dataset_configs['medium']
            config_name = "medium"
        
        logger.info(f"开始生成 {config_name} 数据集...")
        logger.info(f"配置: {config}")
        
        # 创建生成器
        generator = AdvancedKoreanTTSGenerator(str(self.raw_dir))
        
        start_time = time.time()
        
        try:
            # 1. 生成正样本
            logger.info("🔊 生成正样本...")
            positive_count = await generator.generate_positive_samples_async(config['positive'])
            
            # 2. 生成负样本
            logger.info("🚫 生成负样本...")
            negative_count = generator.generate_negative_samples(config['negative'])
            
            # 3. 生成远场样本
            far_field_count = 0
            if enable_far_field and config.get('far_field', 0) > 0:
                logger.info("🌊 生成远场样本...")
                far_field_count = generator.generate_far_field_samples(
                    generator.positive_dir, config['far_field']
                )
            
            # 4. 数据质量检查
            logger.info("🔍 数据质量检查...")
            quality_stats = self._check_data_quality()
            
            # 5. 数据集划分
            logger.info("📊 划分训练/验证/测试集...")
            split_stats = self._split_dataset()
            
            # 6. 生成元数据
            generation_time = time.time() - start_time
            metadata = self._generate_dataset_metadata(
                config_name, config, quality_stats, split_stats, generation_time
            )
            
            logger.info(f"✅ 数据集生成完成！耗时: {generation_time:.1f}秒")
            
            return {
                'config_name': config_name,
                'config': config,
                'generated_counts': {
                    'positive': positive_count,
                    'negative': negative_count,
                    'far_field': far_field_count
                },
                'quality_stats': quality_stats,
                'split_stats': split_stats,
                'generation_time': generation_time,
                'metadata': metadata
            }
            
        except Exception as e:
            logger.error(f"数据集生成失败: {e}")
            raise
    
    def _check_data_quality(self) -> Dict:
        """检查数据质量"""
        logger.info("  检查音频文件质量...")
        
        stats = {
            'total_files': 0,
            'valid_files': 0,
            'invalid_files': 0,
            'duration_stats': {'min': float('inf'), 'max': 0, 'avg': 0},
            'size_stats': {'min': float('inf'), 'max': 0, 'avg': 0}
        }
        
        durations = []
        sizes = []
        
        # 检查所有WAV文件
        for wav_file in self.raw_dir.rglob("*.wav"):
            stats['total_files'] += 1
            
            try:
                # 检查文件大小
                file_size = wav_file.stat().st_size
                sizes.append(file_size)
                
                # 检查音频时长 (使用librosa)
                import librosa
                duration = librosa.get_duration(filename=str(wav_file))
                durations.append(duration)
                
                # 基本质量检查
                if 0.5 <= duration <= 3.0 and file_size > 1000:  # 合理的时长和大小
                    stats['valid_files'] += 1
                else:
                    stats['invalid_files'] += 1
                    logger.warning(f"质量问题: {wav_file.name} (时长: {duration:.2f}s, 大小: {file_size})")
                
            except Exception as e:
                stats['invalid_files'] += 1
                logger.warning(f"无法检查文件: {wav_file.name} - {e}")
        
        # 计算统计信息
        if durations:
            stats['duration_stats'] = {
                'min': min(durations),
                'max': max(durations),
                'avg': sum(durations) / len(durations)
            }
        
        if sizes:
            stats['size_stats'] = {
                'min': min(sizes),
                'max': max(sizes),
                'avg': sum(sizes) / len(sizes)
            }
        
        logger.info(f"  质量检查完成: {stats['valid_files']}/{stats['total_files']} 文件有效")
        
        return stats
    
    def _split_dataset(self) -> Dict:
        """划分数据集"""
        logger.info("  收集所有音频文件...")
        
        # 收集所有文件和标签
        all_files = []
        
        # 正样本
        for wav_file in (self.raw_dir / "positive").glob("*.wav"):
            all_files.append((wav_file, 1, 'positive'))
        
        # 负样本
        for wav_file in (self.raw_dir / "negative").glob("*.wav"):
            all_files.append((wav_file, 0, 'negative'))
        
        # 远场样本 (标记为正样本)
        for wav_file in (self.raw_dir / "far_field").glob("*.wav"):
            all_files.append((wav_file, 1, 'far_field'))
        
        # 打乱数据
        random.shuffle(all_files)
        
        # 计算划分点
        total_count = len(all_files)
        train_count = int(total_count * self.split_ratios['train'])
        val_count = int(total_count * self.split_ratios['val'])
        
        # 划分数据
        train_files = all_files[:train_count]
        val_files = all_files[train_count:train_count + val_count]
        test_files = all_files[train_count + val_count:]
        
        # 复制文件到对应目录
        splits = {
            'train': (train_files, self.train_dir),
            'val': (val_files, self.val_dir),
            'test': (test_files, self.test_dir)
        }
        
        split_stats = {}
        
        for split_name, (files, target_dir) in splits.items():
            logger.info(f"  复制 {len(files)} 个文件到 {split_name} 集...")
            
            # 创建子目录
            pos_dir = target_dir / "positive"
            neg_dir = target_dir / "negative"
            pos_dir.mkdir(exist_ok=True)
            neg_dir.mkdir(exist_ok=True)
            
            pos_count = neg_count = 0
            
            for i, (src_file, label, category) in enumerate(files):
                # 生成新文件名
                new_name = f"{split_name}_{category}_{i:05d}.wav"
                
                if label == 1:  # 正样本
                    target_file = pos_dir / new_name
                    pos_count += 1
                else:  # 负样本
                    target_file = neg_dir / new_name
                    neg_count += 1
                
                # 复制文件
                shutil.copy2(src_file, target_file)
            
            # 生成文件列表
            self._create_file_list(target_dir, split_name)
            
            split_stats[split_name] = {
                'total': len(files),
                'positive': pos_count,
                'negative': neg_count
            }
        
        logger.info("  数据集划分完成")
        return split_stats
    
    def _create_file_list(self, split_dir: Path, split_name: str):
        """创建文件列表"""
        file_list_path = split_dir / f"{split_name}_files.txt"
        
        with open(file_list_path, 'w', encoding='utf-8') as f:
            # 正样本
            for wav_file in sorted((split_dir / "positive").glob("*.wav")):
                rel_path = wav_file.relative_to(split_dir)
                f.write(f"{rel_path} 1\n")
            
            # 负样本
            for wav_file in sorted((split_dir / "negative").glob("*.wav")):
                rel_path = wav_file.relative_to(split_dir)
                f.write(f"{rel_path} 0\n")
    
    def _generate_dataset_metadata(self, config_name: str, config: Dict, 
                                 quality_stats: Dict, split_stats: Dict, 
                                 generation_time: float) -> Dict:
        """生成数据集元数据"""
        metadata = {
            "dataset_info": {
                "name": f"korean_wake_word_{config_name}",
                "wake_word": "하이넛지",
                "language": "ko-KR",
                "generation_time": time.strftime("%Y-%m-%d %H:%M:%S"),
                "generation_duration_seconds": generation_time,
                "config_name": config_name,
                "config": config
            },
            "audio_specs": {
                "sample_rate": 16000,
                "channels": 1,
                "duration_seconds": 2.0,
                "format": "wav"
            },
            "dataset_stats": {
                "total_samples": quality_stats['total_files'],
                "valid_samples": quality_stats['valid_files'],
                "quality_stats": quality_stats,
                "split_stats": split_stats,
                "split_ratios": self.split_ratios
            },
            "usage": {
                "training": "Use train/ directory for model training",
                "validation": "Use val/ directory for hyperparameter tuning", 
                "testing": "Use test/ directory for final evaluation",
                "file_format": "Each split contains positive/ and negative/ subdirectories",
                "labels": "1 = wake word (positive), 0 = non-wake word (negative)"
            }
        }
        
        # 保存元数据
        metadata_path = self.output_dir / "dataset_metadata.json"
        with open(metadata_path, 'w', encoding='utf-8') as f:
            json.dump(metadata, f, ensure_ascii=False, indent=2)
        
        logger.info(f"数据集元数据已保存: {metadata_path}")
        return metadata
    
    def show_final_statistics(self, result: Dict):
        """显示最终统计信息"""
        print("\n" + "="*80)
        print("🎉 数据集生成完成！")
        print("="*80)
        
        config = result['config']
        generated = result['generated_counts']
        splits = result['split_stats']
        
        print(f"\n📊 生成统计:")
        print(f"  配置: {result['config_name']}")
        print(f"  ✅ 正样本: {generated['positive']} 个")
        print(f"  ❌ 负样本: {generated['negative']} 个")
        print(f"  🌊 远场样本: {generated['far_field']} 个")
        print(f"  ⏱️  生成时间: {result['generation_time']:.1f} 秒")
        
        print(f"\n📁 数据集划分:")
        for split_name, stats in splits.items():
            print(f"  {split_name:5s}: {stats['total']:5d} 个 (正: {stats['positive']:4d}, 负: {stats['negative']:4d})")
        
        print(f"\n🎯 使用方法:")
        print(f"  训练: {self.train_dir}")
        print(f"  验证: {self.val_dir}")
        print(f"  测试: {self.test_dir}")
        print(f"  元数据: {self.output_dir}/dataset_metadata.json")
        
        print("="*80)

async def main():
    parser = argparse.ArgumentParser(description="韩语唤醒词批量数据集生成器")
    parser.add_argument("--output_dir", default="datasets/korean_wake_word",
                       help="输出目录")
    
    # 预设配置
    parser.add_argument("--small", action="store_true", help="生成小型数据集 (1K样本)")
    parser.add_argument("--medium", action="store_true", help="生成中型数据集 (5K样本)")
    parser.add_argument("--large", action="store_true", help="生成大型数据集 (16K样本)")
    parser.add_argument("--xlarge", action="store_true", help="生成超大型数据集 (27K样本)")
    
    # 自定义配置
    parser.add_argument("--custom", action="store_true", help="使用自定义配置")
    parser.add_argument("--positive", type=int, help="正样本数量")
    parser.add_argument("--negative", type=int, help="负样本数量")
    parser.add_argument("--far_field_samples", type=int, help="远场样本数量")
    
    # 选项
    parser.add_argument("--no_far_field", action="store_true", help="不生成远场样本")
    parser.add_argument("--show_info_only", action="store_true", help="仅显示信息")
    
    args = parser.parse_args()
    
    # 创建生成器
    generator = BatchKoreanDatasetGenerator(args.output_dir)
    generator.show_dataset_info()
    
    if args.show_info_only:
        return
    
    # 确定配置
    config_name = None
    custom_config = None
    
    if args.custom:
        if not (args.positive and args.negative):
            print("❌ 自定义模式需要指定 --positive 和 --negative")
            return
        
        custom_config = {
            'positive': args.positive,
            'negative': args.negative,
            'far_field': args.far_field_samples or 0
        }
    else:
        # 选择预设配置
        if args.small:
            config_name = 'small'
        elif args.large:
            config_name = 'large'
        elif args.xlarge:
            config_name = 'xlarge'
        else:
            config_name = 'medium'  # 默认
    
    enable_far_field = not args.no_far_field
    
    try:
        # 生成数据集
        result = await generator.generate_dataset(
            config_name=config_name,
            custom_config=custom_config,
            enable_far_field=enable_far_field
        )
        
        # 显示结果
        generator.show_final_statistics(result)
        
    except KeyboardInterrupt:
        logger.info("用户中断生成过程")
    except Exception as e:
        logger.error(f"批量生成失败: {e}")
        sys.exit(1)

if __name__ == "__main__":
    asyncio.run(main())
