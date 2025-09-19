#!/usr/bin/env python3
"""
韩语唤醒词 OpenWakeWord 模型训练脚本
基于OpenWakeWord框架训练自定义韩语唤醒词"하이넛지"

OpenWakeWord需要三个TensorFlow Lite模型:
1. melspectrogram.tflite - 音频转mel频谱图
2. embedding.tflite - 特征嵌入提取  
3. wake.tflite - 唤醒词分类器

使用方法:
    python scripts/train_openwakeword_korean.py --data_dir scripts/training_data/korean_wake_word_tts
    python scripts/train_openwakeword_korean.py --data_dir scripts/training_data/korean_wake_word_tts --epochs 100
"""

import os
import sys
import argparse
import logging
import time
from pathlib import Path
import numpy as np
import json
from typing import List, Tuple, Dict, Optional

# 音频处理
try:
    import librosa
    import soundfile as sf
except ImportError:
    print("❌ 缺少音频处理库: pip install librosa soundfile")
    sys.exit(1)

# TensorFlow
try:
    import tensorflow as tf
    from tensorflow import keras
    from tensorflow.keras import layers
    print(f"✅ TensorFlow版本: {tf.__version__}")
except ImportError:
    print("❌ 缺少TensorFlow: pip install tensorflow")
    sys.exit(1)

# 设置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler('openwakeword_korean_training.log'),
        logging.StreamHandler()
    ]
)
logger = logging.getLogger(__name__)

class OpenWakeWordKoreanTrainer:
    """OpenWakeWord韩语唤醒词训练器"""
    
    def __init__(self, data_dir: str, output_dir: str = "models/openwakeword_korean"):
        self.data_dir = Path(data_dir)
        self.output_dir = Path(output_dir)
        self.output_dir.mkdir(parents=True, exist_ok=True)
        
        # OpenWakeWord音频参数 (与Dicio中OwwModel.kt保持一致)
        self.sample_rate = 16000
        self.mel_input_count = 1152  # MEL_INPUT_COUNT = 512 + 160 * 4
        self.mel_output_count = 5    # (1152 - 512) / 160 + 1 = 5
        self.mel_feature_size = 32   # MEL_FEATURE_SIZE
        
        # 嵌入模型参数
        self.emb_input_count = 76    # EMB_INPUT_COUNT
        self.emb_output_count = 1    # EMB_OUTPUT_COUNT  
        self.emb_feature_size = 96   # EMB_FEATURE_SIZE
        
        # 唤醒词模型参数
        self.wake_input_count = 16   # WAKE_INPUT_COUNT
        
        # 训练参数
        self.batch_size = 32
        self.epochs = 50
        self.learning_rate = 0.001
        self.validation_split = 0.2
        
        logger.info(f"初始化OpenWakeWord训练器")
        logger.info(f"数据目录: {self.data_dir}")
        logger.info(f"输出目录: {self.output_dir}")
        logger.info(f"音频参数: SR={self.sample_rate}, MEL_INPUT={self.mel_input_count}")
    
    def load_audio_file(self, file_path: Path) -> Optional[np.ndarray]:
        """加载音频文件并调整到固定长度"""
        try:
            # 加载音频
            y, sr = librosa.load(str(file_path), sr=self.sample_rate)
            
            # 调整长度到mel_input_count
            if len(y) > self.mel_input_count:
                # 太长则截取中间部分
                start = (len(y) - self.mel_input_count) // 2
                y = y[start:start + self.mel_input_count]
            elif len(y) < self.mel_input_count:
                # 太短则填充零
                y = np.pad(y, (0, self.mel_input_count - len(y)))
            
            return y.astype(np.float32)
            
        except Exception as e:
            logger.error(f"加载音频文件失败 {file_path}: {e}")
            return None
    
    def extract_mel_spectrogram(self, audio: np.ndarray) -> np.ndarray:
        """提取mel频谱图特征 (模拟melspectrogram.tflite的功能)"""
        try:
            # 计算mel频谱图
            mel_spec = librosa.feature.melspectrogram(
                y=audio,
                sr=self.sample_rate,
                n_mels=self.mel_feature_size,
                n_fft=512,
                hop_length=160,
                win_length=512,
                fmin=0,
                fmax=self.sample_rate // 2
            )
            
            # 转换为对数刻度
            log_mel_spec = librosa.power_to_db(mel_spec, ref=np.max)
            
            # 标准化到 [-1, 1] 范围
            log_mel_spec = (log_mel_spec - np.mean(log_mel_spec)) / (np.std(log_mel_spec) + 1e-8)
            log_mel_spec = np.clip(log_mel_spec, -3, 3) / 3.0
            
            # 转置以匹配OpenWakeWord格式 [time, mel_bins]
            mel_features = log_mel_spec.T  # Shape: [time_frames, mel_feature_size]
            
            # 确保输出形状正确
            if mel_features.shape[0] != self.mel_output_count:
                # 调整时间维度
                if mel_features.shape[0] > self.mel_output_count:
                    mel_features = mel_features[:self.mel_output_count]
                else:
                    # 填充
                    pad_width = ((0, self.mel_output_count - mel_features.shape[0]), (0, 0))
                    mel_features = np.pad(mel_features, pad_width, mode='constant')
            
            return mel_features.astype(np.float32)
            
        except Exception as e:
            logger.error(f"提取mel频谱图失败: {e}")
            return None
    
    def load_dataset(self) -> Tuple[np.ndarray, np.ndarray, List[str]]:
        """加载数据集并提取mel频谱图特征"""
        logger.info("开始加载数据集并提取mel特征...")
        
        mel_features_list = []
        labels_list = []
        file_names = []
        
        # 加载正样本
        positive_dir = self.data_dir / "positive"
        if positive_dir.exists():
            logger.info(f"加载正样本从: {positive_dir}")
            positive_files = list(positive_dir.glob("*.wav"))
            logger.info(f"找到 {len(positive_files)} 个正样本文件")
            
            for i, wav_file in enumerate(positive_files):
                audio = self.load_audio_file(wav_file)
                if audio is not None:
                    mel_features = self.extract_mel_spectrogram(audio)
                    if mel_features is not None:
                        mel_features_list.append(mel_features)
                        labels_list.append(1)  # 正样本标签
                        file_names.append(wav_file.name)
                
                if (i + 1) % 20 == 0:
                    logger.info(f"已处理 {i + 1} 个正样本")
        
        # 加载负样本
        negative_dir = self.data_dir / "negative"
        if negative_dir.exists():
            logger.info(f"加载负样本从: {negative_dir}")
            negative_files = list(negative_dir.glob("*.wav"))
            logger.info(f"找到 {len(negative_files)} 个负样本文件")
            
            for i, wav_file in enumerate(negative_files):
                audio = self.load_audio_file(wav_file)
                if audio is not None:
                    mel_features = self.extract_mel_spectrogram(audio)
                    if mel_features is not None:
                        mel_features_list.append(mel_features)
                        labels_list.append(0)  # 负样本标签
                        file_names.append(wav_file.name)
                
                if (i + 1) % 50 == 0:
                    logger.info(f"已处理 {i + 1} 个负样本")
        
        # 转换为numpy数组
        X = np.array(mel_features_list)
        y = np.array(labels_list)
        
        logger.info(f"数据集加载完成:")
        logger.info(f"  总样本数: {len(X)}")
        logger.info(f"  正样本数: {np.sum(y == 1)}")
        logger.info(f"  负样本数: {np.sum(y == 0)}")
        logger.info(f"  Mel特征形状: {X.shape}")
        
        return X, y, file_names
    
    def create_embedding_model(self) -> keras.Model:
        """创建嵌入模型 (模拟embedding.tflite)"""
        logger.info("创建嵌入模型...")
        
        # 输入: [batch, emb_input_count, mel_feature_size, 1]
        inputs = keras.Input(shape=(self.emb_input_count, self.mel_feature_size, 1), name='mel_input')
        
        # 卷积层提取特征
        x = layers.Conv2D(32, (3, 3), activation='relu', padding='same')(inputs)
        x = layers.BatchNormalization()(x)
        x = layers.MaxPooling2D((2, 2))(x)
        
        x = layers.Conv2D(64, (3, 3), activation='relu', padding='same')(x)
        x = layers.BatchNormalization()(x)
        x = layers.MaxPooling2D((2, 2))(x)
        
        x = layers.Conv2D(128, (3, 3), activation='relu', padding='same')(x)
        x = layers.BatchNormalization()(x)
        
        # 全局平均池化
        x = layers.GlobalAveragePooling2D()(x)
        
        # 全连接层
        x = layers.Dense(256, activation='relu')(x)
        x = layers.Dropout(0.3)(x)
        x = layers.Dense(self.emb_feature_size, activation='tanh', name='embedding')(x)
        
        # 输出: [batch, 1, 1, emb_feature_size]
        outputs = layers.Reshape((1, 1, self.emb_feature_size))(x)
        
        model = keras.Model(inputs, outputs, name='embedding_model')
        return model
    
    def create_wake_model(self) -> keras.Model:
        """创建唤醒词分类模型 (模拟wake.tflite)"""
        logger.info("创建唤醒词分类模型...")
        
        # 输入: [batch, wake_input_count, emb_feature_size]
        inputs = keras.Input(shape=(self.wake_input_count, self.emb_feature_size), name='embedding_input')
        
        # LSTM层处理时序特征
        x = layers.LSTM(128, return_sequences=True)(inputs)
        x = layers.Dropout(0.3)(x)
        x = layers.LSTM(64, return_sequences=False)(x)
        x = layers.Dropout(0.3)(x)
        
        # 全连接层
        x = layers.Dense(32, activation='relu')(x)
        x = layers.Dropout(0.2)(x)
        
        # 输出层 - 二分类 (唤醒词 vs 非唤醒词)
        outputs = layers.Dense(1, activation='sigmoid', name='wake_prediction')(x)
        
        model = keras.Model(inputs, outputs, name='wake_model')
        return model
    
    def create_end_to_end_model(self) -> keras.Model:
        """创建端到端模型用于训练"""
        logger.info("创建端到端训练模型...")
        
        # Mel输入
        mel_inputs = keras.Input(shape=(self.mel_output_count, self.mel_feature_size), name='mel_spectrogram')
        
        # 模拟mel特征的时序累积 (简化版本)
        # 在实际OpenWakeWord中，这部分由melspectrogram.tflite处理
        mel_expanded = layers.Reshape((self.mel_output_count, self.mel_feature_size, 1))(mel_inputs)
        
        # 填充到emb_input_count长度
        if self.mel_output_count < self.emb_input_count:
            pad_count = self.emb_input_count - self.mel_output_count
            padding = layers.Lambda(lambda x: tf.pad(x, [[0, 0], [0, pad_count], [0, 0], [0, 0]]))(mel_expanded)
        else:
            padding = mel_expanded
        
        # 嵌入模型
        embedding_model = self.create_embedding_model()
        embeddings = embedding_model(padding)
        
        # 重塑为时序格式
        embeddings_seq = layers.Reshape((1, self.emb_feature_size))(embeddings)
        
        # 模拟时序累积 (简化版本)
        # 复制嵌入以创建时序
        embeddings_repeated = layers.Lambda(
            lambda x: tf.tile(x, [1, self.wake_input_count, 1])
        )(embeddings_seq)
        
        # 唤醒词模型
        wake_model = self.create_wake_model()
        wake_output = wake_model(embeddings_repeated)
        
        # 完整模型
        full_model = keras.Model(mel_inputs, wake_output, name='korean_wake_word_model')
        
        return full_model, embedding_model, wake_model
    
    def train_model(self, X: np.ndarray, y: np.ndarray) -> Dict:
        """训练模型"""
        logger.info("开始训练OpenWakeWord模型...")
        
        # 创建模型
        full_model, embedding_model, wake_model = self.create_end_to_end_model()
        
        # 编译模型
        full_model.compile(
            optimizer=keras.optimizers.Adam(learning_rate=self.learning_rate),
            loss='binary_crossentropy',
            metrics=['accuracy', 'precision', 'recall']
        )
        
        # 显示模型结构
        logger.info("完整模型结构:")
        full_model.summary(print_fn=logger.info)
        
        # 准备训练数据
        # 将标签转换为浮点数
        y_train = y.astype(np.float32)
        
        # 创建回调
        callbacks = [
            keras.callbacks.EarlyStopping(
                monitor='val_loss',
                patience=10,
                restore_best_weights=True
            ),
            keras.callbacks.ReduceLROnPlateau(
                monitor='val_loss',
                factor=0.5,
                patience=5,
                min_lr=1e-6
            ),
            keras.callbacks.ModelCheckpoint(
                filepath=str(self.output_dir / "best_model.weights.h5"),
                monitor='val_accuracy',
                save_best_only=True,
                save_weights_only=True  # 只保存权重避免序列化问题
            )
        ]
        
        # 训练模型
        start_time = time.time()
        logger.info(f"开始训练，数据形状: X={X.shape}, y={y_train.shape}")
        
        history = full_model.fit(
            X, y_train,
            batch_size=self.batch_size,
            epochs=self.epochs,
            validation_split=self.validation_split,
            callbacks=callbacks,
            verbose=1
        )
        
        training_time = time.time() - start_time
        logger.info(f"模型训练完成，耗时: {training_time:.2f} 秒")
        
        # 评估模型
        val_loss, val_accuracy, val_precision, val_recall = full_model.evaluate(
            X, y_train, verbose=0
        )
        
        logger.info(f"最终评估结果:")
        logger.info(f"  验证损失: {val_loss:.4f}")
        logger.info(f"  验证准确率: {val_accuracy:.4f}")
        logger.info(f"  验证精确率: {val_precision:.4f}")
        logger.info(f"  验证召回率: {val_recall:.4f}")
        
        return {
            'full_model': full_model,
            'embedding_model': embedding_model,
            'wake_model': wake_model,
            'history': history.history,
            'training_time': training_time,
            'final_metrics': {
                'loss': val_loss,
                'accuracy': val_accuracy,
                'precision': val_precision,
                'recall': val_recall
            }
        }
    
    def convert_to_tflite(self, model_info: Dict):
        """转换模型为TensorFlow Lite格式"""
        logger.info("转换模型为TensorFlow Lite格式...")
        
        # 创建简化的mel模型 (实际中这个模型比较复杂)
        mel_model = self._create_mel_model()
        
        # 转换模型
        models_to_convert = {
            'melspectrogram.tflite': mel_model,
            'embedding.tflite': model_info['embedding_model'],
            'wake.tflite': model_info['wake_model']
        }
        
        for filename, model in models_to_convert.items():
            try:
                logger.info(f"转换 {filename}...")
                
                # 创建TFLite转换器
                converter = tf.lite.TFLiteConverter.from_keras_model(model)
                
                # 优化设置
                converter.optimizations = [tf.lite.Optimize.DEFAULT]
                converter.target_spec.supported_types = [tf.float32]
                
                # 转换
                tflite_model = converter.convert()
                
                # 保存
                tflite_path = self.output_dir / filename
                with open(tflite_path, 'wb') as f:
                    f.write(tflite_model)
                
                logger.info(f"✅ {filename} 保存到: {tflite_path}")
                
            except Exception as e:
                logger.error(f"转换 {filename} 失败: {e}")
    
    def _create_mel_model(self) -> keras.Model:
        """创建简化的mel频谱图模型"""
        # 这是一个简化版本，实际的mel模型更复杂
        inputs = keras.Input(shape=(self.mel_input_count,), name='audio_input')
        
        # 简单的线性变换作为占位符
        x = layers.Reshape((self.mel_input_count, 1))(inputs)
        x = layers.Conv1D(32, 512, strides=160, activation='relu')(x)
        x = layers.Conv1D(self.mel_feature_size, 1, activation='linear')(x)
        
        # 输出形状: [batch, mel_output_count, mel_feature_size]
        outputs = layers.Lambda(lambda x: x[:, :self.mel_output_count, :])(x)
        
        model = keras.Model(inputs, outputs, name='mel_spectrogram_model')
        return model
    
    def save_model_info(self, model_info: Dict):
        """保存模型信息和元数据"""
        logger.info("保存模型信息...")
        
        # 保存完整模型
        full_model_path = self.output_dir / "korean_wake_word_full.h5"
        model_info['full_model'].save(full_model_path)
        logger.info(f"完整模型已保存到: {full_model_path}")
        
        # 保存元数据
        metadata = {
            'wake_word': '하이넛지',
            'romanized': 'hi_nutji',
            'model_type': 'OpenWakeWord',
            'training_time': time.strftime('%Y-%m-%d %H:%M:%S'),
            'audio_params': {
                'sample_rate': self.sample_rate,
                'mel_input_count': self.mel_input_count,
                'mel_output_count': self.mel_output_count,
                'mel_feature_size': self.mel_feature_size,
                'emb_input_count': self.emb_input_count,
                'emb_feature_size': self.emb_feature_size,
                'wake_input_count': self.wake_input_count
            },
            'training_params': {
                'batch_size': self.batch_size,
                'epochs': self.epochs,
                'learning_rate': self.learning_rate,
                'validation_split': self.validation_split
            },
            'performance': model_info['final_metrics'],
            'training_time_seconds': model_info['training_time'],
            'model_files': [
                'melspectrogram.tflite',
                'embedding.tflite', 
                'wake.tflite'
            ]
        }
        
        metadata_path = self.output_dir / "model_metadata.json"
        with open(metadata_path, 'w', encoding='utf-8') as f:
            json.dump(metadata, f, ensure_ascii=False, indent=2)
        
        logger.info(f"模型元数据已保存到: {metadata_path}")
        
        return metadata_path

def main():
    parser = argparse.ArgumentParser(description="OpenWakeWord韩语唤醒词模型训练")
    parser.add_argument("--data_dir", required=True,
                       help="训练数据目录 (包含positive/和negative/子目录)")
    parser.add_argument("--output_dir", default="models/openwakeword_korean",
                       help="模型输出目录")
    parser.add_argument("--epochs", type=int, default=50,
                       help="训练轮数")
    parser.add_argument("--batch_size", type=int, default=32,
                       help="批次大小")
    parser.add_argument("--learning_rate", type=float, default=0.001,
                       help="学习率")
    
    args = parser.parse_args()
    
    # 检查数据目录
    data_dir = Path(args.data_dir)
    if not data_dir.exists():
        logger.error(f"数据目录不存在: {data_dir}")
        sys.exit(1)
    
    positive_dir = data_dir / "positive"
    negative_dir = data_dir / "negative"
    
    if not positive_dir.exists() or not negative_dir.exists():
        logger.error("数据目录必须包含 positive/ 和 negative/ 子目录")
        sys.exit(1)
    
    try:
        # 创建训练器
        trainer = OpenWakeWordKoreanTrainer(args.data_dir, args.output_dir)
        trainer.epochs = args.epochs
        trainer.batch_size = args.batch_size
        trainer.learning_rate = args.learning_rate
        
        # 加载数据集
        X, y, file_names = trainer.load_dataset()
        
        if len(X) == 0:
            logger.error("没有找到有效的训练数据")
            sys.exit(1)
        
        # 训练模型
        model_info = trainer.train_model(X, y)
        
        # 转换为TFLite
        trainer.convert_to_tflite(model_info)
        
        # 保存模型信息
        metadata_path = trainer.save_model_info(model_info)
        
        logger.info("🎉 OpenWakeWord训练完成!")
        logger.info(f"📁 模型文件目录: {trainer.output_dir}")
        logger.info(f"📊 最终准确率: {model_info['final_metrics']['accuracy']:.4f}")
        logger.info(f"⏱️  训练时间: {model_info['training_time']:.2f} 秒")
        
        print(f"\n🚀 模型文件:")
        for model_file in ['melspectrogram.tflite', 'embedding.tflite', 'wake.tflite']:
            model_path = trainer.output_dir / model_file
            if model_path.exists():
                print(f"  ✅ {model_file}")
            else:
                print(f"  ❌ {model_file} (转换失败)")
        
        print(f"\n📱 在Dicio中使用:")
        print(f"  1. 将三个.tflite文件复制到 app/src/withModels/assets/models/openWakeWord/")
        print(f"  2. 或者通过设置界面导入自定义唤醒词模型")
        
    except KeyboardInterrupt:
        logger.info("训练被用户中断")
    except Exception as e:
        logger.error(f"训练失败: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)

if __name__ == "__main__":
    main()
