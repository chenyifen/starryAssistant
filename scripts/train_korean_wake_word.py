#!/usr/bin/env python3
"""
韩语唤醒词"하이넛지"训练脚本
使用OpenWakeWord框架训练自定义韩语唤醒词模型

使用方法:
    python scripts/train_korean_wake_word.py --data_dir training_data --output_dir models

依赖安装:
    pip install openwakeword tensorflow librosa soundfile numpy
"""

import os
import sys
import argparse
import logging
from pathlib import Path
import numpy as np
import soundfile as sf
import librosa
from typing import List, Tuple

# 设置日志
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

class KoreanWakeWordTrainer:
    """韩语唤醒词训练器"""
    
    def __init__(self, data_dir: str, output_dir: str):
        self.data_dir = Path(data_dir)
        self.output_dir = Path(output_dir)
        self.wake_word = "하이넛지"
        self.romanized = "hi_nutji"
        
        # 音频参数
        self.sample_rate = 16000
        self.duration = 1.0  # 1秒音频片段
        self.n_mels = 32
        self.n_fft = 512
        self.hop_length = 160
        
        # 训练参数
        self.batch_size = 32
        self.epochs = 100
        self.learning_rate = 0.001
        
    def setup_directories(self):
        """设置训练目录结构"""
        directories = [
            self.data_dir / "positive",
            self.data_dir / "negative", 
            self.output_dir,
            self.output_dir / "logs",
            self.output_dir / "checkpoints"
        ]
        
        for dir_path in directories:
            dir_path.mkdir(parents=True, exist_ok=True)
            logger.info(f"Created directory: {dir_path}")
    
    def collect_sample_instructions(self):
        """打印样本收集指导"""
        print("\n" + "="*60)
        print("🎤 韩语唤醒词训练数据收集指南")
        print("="*60)
        print(f"目标唤醒词: {self.wake_word} ({self.romanized})")
        print("\n📁 目录结构:")
        print(f"  {self.data_dir}/")
        print(f"  ├── positive/  # 正样本: 说'{self.wake_word}'的录音")
        print(f"  └── negative/  # 负样本: 其他声音和词汇")
        
        print("\n✅ 正样本收集建议:")
        print("  • 录制100-200个不同的'하이넛지'发音")
        print("  • 包含不同性别、年龄的说话者")
        print("  • 不同语调、语速和音量")
        print("  • 不同环境噪音条件")
        print("  • 格式: 16kHz, 16-bit, mono WAV")
        print("  • 长度: 1-3秒")
        
        print("\n❌ 负样本收集建议:")
        print("  • 其他韩语词汇和句子")
        print("  • 环境噪音、音乐、电视声音")
        print("  • 类似发音的词汇")
        print("  • 英语和其他语言")
        print("  • 数量应为正样本的2-3倍")
        
        print("\n🎯 录音质量要求:")
        print("  • 清晰的语音信号")
        print("  • 最小背景噪音")
        print("  • 一致的音量水平")
        print("  • 避免回声和失真")
        
        print("\n📝 文件命名建议:")
        print("  positive/speaker1_01.wav")
        print("  positive/speaker1_02.wav") 
        print("  positive/speaker2_01.wav")
        print("  negative/noise_01.wav")
        print("  negative/korean_word_01.wav")
        print("="*60)
    
    def validate_audio_files(self) -> Tuple[List[str], List[str]]:
        """验证音频文件"""
        positive_files = []
        negative_files = []
        
        # 检查正样本
        positive_dir = self.data_dir / "positive"
        if positive_dir.exists():
            for file_path in positive_dir.glob("*.wav"):
                try:
                    data, sr = sf.read(file_path)
                    if sr != self.sample_rate:
                        logger.warning(f"Sample rate mismatch in {file_path}: {sr} != {self.sample_rate}")
                    positive_files.append(str(file_path))
                except Exception as e:
                    logger.error(f"Error reading {file_path}: {e}")
        
        # 检查负样本
        negative_dir = self.data_dir / "negative"
        if negative_dir.exists():
            for file_path in negative_dir.glob("*.wav"):
                try:
                    data, sr = sf.read(file_path)
                    if sr != self.sample_rate:
                        logger.warning(f"Sample rate mismatch in {file_path}: {sr} != {self.sample_rate}")
                    negative_files.append(str(file_path))
                except Exception as e:
                    logger.error(f"Error reading {file_path}: {e}")
        
        logger.info(f"Found {len(positive_files)} positive samples")
        logger.info(f"Found {len(negative_files)} negative samples")
        
        return positive_files, negative_files
    
    def extract_features(self, audio_files: List[str]) -> np.ndarray:
        """提取音频特征"""
        features = []
        
        for file_path in audio_files:
            try:
                # 加载音频
                data, sr = librosa.load(file_path, sr=self.sample_rate)
                
                # 确保音频长度
                target_length = int(self.sample_rate * self.duration)
                if len(data) < target_length:
                    # 填充零
                    data = np.pad(data, (0, target_length - len(data)))
                else:
                    # 截取
                    data = data[:target_length]
                
                # 提取梅尔频谱图
                mel_spec = librosa.feature.melspectrogram(
                    y=data,
                    sr=sr,
                    n_mels=self.n_mels,
                    n_fft=self.n_fft,
                    hop_length=self.hop_length
                )
                
                # 转换为对数刻度
                log_mel_spec = librosa.power_to_db(mel_spec, ref=np.max)
                
                features.append(log_mel_spec.T)  # 转置以匹配时间x特征格式
                
            except Exception as e:
                logger.error(f"Error processing {file_path}: {e}")
        
        return np.array(features)
    
    def create_tensorflow_model(self, input_shape: Tuple[int, int]):
        """创建TensorFlow模型"""
        try:
            import tensorflow as tf
            from tensorflow.keras import layers, models
        except ImportError:
            logger.error("TensorFlow not installed. Please install: pip install tensorflow")
            return None
        
        model = models.Sequential([
            layers.Input(shape=input_shape),
            
            # 卷积层
            layers.Conv1D(64, 3, activation='relu'),
            layers.BatchNormalization(),
            layers.MaxPooling1D(2),
            layers.Dropout(0.3),
            
            layers.Conv1D(128, 3, activation='relu'),
            layers.BatchNormalization(),
            layers.MaxPooling1D(2),
            layers.Dropout(0.3),
            
            layers.Conv1D(256, 3, activation='relu'),
            layers.BatchNormalization(),
            layers.GlobalAveragePooling1D(),
            layers.Dropout(0.5),
            
            # 全连接层
            layers.Dense(128, activation='relu'),
            layers.BatchNormalization(),
            layers.Dropout(0.5),
            
            layers.Dense(64, activation='relu'),
            layers.Dropout(0.3),
            
            # 输出层
            layers.Dense(1, activation='sigmoid')
        ])
        
        model.compile(
            optimizer=tf.keras.optimizers.Adam(learning_rate=self.learning_rate),
            loss='binary_crossentropy',
            metrics=['accuracy', 'precision', 'recall']
        )
        
        return model
    
    def train_model(self):
        """训练模型"""
        try:
            import tensorflow as tf
        except ImportError:
            logger.error("TensorFlow not installed. Please install: pip install tensorflow")
            return False
        
        # 验证音频文件
        positive_files, negative_files = self.validate_audio_files()
        
        if len(positive_files) < 10:
            logger.error(f"Not enough positive samples: {len(positive_files)}. Need at least 10.")
            return False
        
        if len(negative_files) < 20:
            logger.error(f"Not enough negative samples: {len(negative_files)}. Need at least 20.")
            return False
        
        # 提取特征
        logger.info("Extracting features from positive samples...")
        positive_features = self.extract_features(positive_files)
        
        logger.info("Extracting features from negative samples...")
        negative_features = self.extract_features(negative_files)
        
        # 准备训练数据
        X = np.concatenate([positive_features, negative_features])
        y = np.concatenate([
            np.ones(len(positive_features)),
            np.zeros(len(negative_features))
        ])
        
        # 打乱数据
        indices = np.random.permutation(len(X))
        X = X[indices]
        y = y[indices]
        
        logger.info(f"Training data shape: {X.shape}")
        logger.info(f"Labels shape: {y.shape}")
        
        # 创建模型
        model = self.create_tensorflow_model(X.shape[1:])
        if model is None:
            return False
        
        # 设置回调
        callbacks = [
            tf.keras.callbacks.EarlyStopping(
                monitor='val_loss',
                patience=10,
                restore_best_weights=True
            ),
            tf.keras.callbacks.ReduceLROnPlateau(
                monitor='val_loss',
                factor=0.5,
                patience=5,
                min_lr=1e-7
            ),
            tf.keras.callbacks.ModelCheckpoint(
                filepath=str(self.output_dir / "checkpoints" / "best_model.h5"),
                monitor='val_accuracy',
                save_best_only=True
            )
        ]
        
        # 训练模型
        logger.info("Starting model training...")
        history = model.fit(
            X, y,
            batch_size=self.batch_size,
            epochs=self.epochs,
            validation_split=0.2,
            callbacks=callbacks,
            verbose=1
        )
        
        # 保存模型
        model_path = self.output_dir / f"{self.romanized}_korean.h5"
        model.save(str(model_path))
        logger.info(f"Model saved to: {model_path}")
        
        # 转换为TensorFlow Lite
        tflite_path = self.convert_to_tflite(model)
        if tflite_path:
            logger.info(f"TensorFlow Lite model saved to: {tflite_path}")
        
        return True
    
    def convert_to_tflite(self, model):
        """转换为TensorFlow Lite格式"""
        try:
            import tensorflow as tf
            
            # 转换为TensorFlow Lite
            converter = tf.lite.TFLiteConverter.from_keras_model(model)
            converter.optimizations = [tf.lite.Optimize.DEFAULT]
            
            # 量化以减少模型大小
            converter.representative_dataset = self.representative_dataset_gen
            converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS_INT8]
            converter.inference_input_type = tf.int8
            converter.inference_output_type = tf.int8
            
            tflite_model = converter.convert()
            
            # 保存TensorFlow Lite模型
            tflite_path = self.output_dir / f"{self.romanized}_korean.tflite"
            with open(tflite_path, 'wb') as f:
                f.write(tflite_model)
            
            return tflite_path
            
        except Exception as e:
            logger.error(f"Error converting to TensorFlow Lite: {e}")
            return None
    
    def representative_dataset_gen(self):
        """代表性数据集生成器用于量化"""
        positive_files, _ = self.validate_audio_files()
        sample_files = positive_files[:10]  # 使用前10个样本
        
        for file_path in sample_files:
            features = self.extract_features([file_path])
            yield [features.astype(np.float32)]
    
    def test_model(self, model_path: str):
        """测试模型性能"""
        try:
            import tensorflow as tf
            
            # 加载模型
            if model_path.endswith('.tflite'):
                interpreter = tf.lite.Interpreter(model_path=model_path)
                interpreter.allocate_tensors()
                
                input_details = interpreter.get_input_details()
                output_details = interpreter.get_output_details()
                
                logger.info("TensorFlow Lite model loaded successfully")
                logger.info(f"Input shape: {input_details[0]['shape']}")
                logger.info(f"Output shape: {output_details[0]['shape']}")
                
            else:
                model = tf.keras.models.load_model(model_path)
                logger.info("Keras model loaded successfully")
                logger.info(f"Model summary:\n{model.summary()}")
            
            return True
            
        except Exception as e:
            logger.error(f"Error testing model: {e}")
            return False

def main():
    parser = argparse.ArgumentParser(description="Train Korean wake word model")
    parser.add_argument("--data_dir", default="training_data/korean_wake_word", 
                       help="Directory containing training data")
    parser.add_argument("--output_dir", default="models/korean_wake_word",
                       help="Output directory for trained model")
    parser.add_argument("--collect_guide", action="store_true",
                       help="Show data collection guide")
    parser.add_argument("--train", action="store_true",
                       help="Start training")
    parser.add_argument("--test", type=str,
                       help="Test model file")
    
    args = parser.parse_args()
    
    trainer = KoreanWakeWordTrainer(args.data_dir, args.output_dir)
    trainer.setup_directories()
    
    if args.collect_guide:
        trainer.collect_sample_instructions()
    
    if args.train:
        success = trainer.train_model()
        if success:
            print("\n🎉 训练完成!")
            print(f"📁 模型文件保存在: {args.output_dir}")
            print("📱 将 .tflite 文件复制到 app/src/main/assets/models/openWakeWord/")
        else:
            print("\n❌ 训练失败，请检查数据和依赖")
    
    if args.test:
        trainer.test_model(args.test)
    
    if not any([args.collect_guide, args.train, args.test]):
        print("使用 --help 查看可用选项")
        trainer.collect_sample_instructions()

if __name__ == "__main__":
    main()
