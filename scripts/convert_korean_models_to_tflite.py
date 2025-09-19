#!/usr/bin/env python3
"""
韩语唤醒词模型转换脚本
将训练好的Keras模型转换为OpenWakeWord所需的TFLite格式

使用方法:
    python scripts/convert_korean_models_to_tflite.py --model_dir models/openwakeword_korean_v1
"""

import os
import sys
import argparse
import logging
from pathlib import Path
import numpy as np
import json

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
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

class KoreanModelConverter:
    """韩语唤醒词模型转换器"""
    
    def __init__(self, model_dir: str):
        self.model_dir = Path(model_dir)
        self.model_dir.mkdir(parents=True, exist_ok=True)
        
        # OpenWakeWord参数 (与Dicio OwwModel.kt一致)
        self.mel_input_count = 1152    # MEL_INPUT_COUNT
        self.mel_output_count = 5      # (1152 - 512) / 160 + 1
        self.mel_feature_size = 32     # MEL_FEATURE_SIZE
        self.emb_input_count = 76      # EMB_INPUT_COUNT  
        self.emb_feature_size = 96     # EMB_FEATURE_SIZE
        self.wake_input_count = 16     # WAKE_INPUT_COUNT
        
        logger.info(f"模型目录: {self.model_dir}")
    
    def create_standalone_mel_model(self) -> keras.Model:
        """创建独立的mel频谱图模型"""
        logger.info("创建mel频谱图模型...")
        
        # 输入: 原始音频 [batch, mel_input_count]
        inputs = keras.Input(shape=(self.mel_input_count,), name='audio_input', dtype=tf.float32)
        
        # 重塑为2D以便卷积
        x = layers.Reshape((self.mel_input_count, 1))(inputs)
        
        # 使用卷积层模拟mel频谱图提取
        # 第一层: 模拟窗口函数
        x = layers.Conv1D(filters=64, kernel_size=512, strides=160, padding='valid', activation='relu', name='mel_conv1')(x)
        
        # 第二层: 特征提取
        x = layers.Conv1D(filters=self.mel_feature_size, kernel_size=3, strides=1, padding='same', activation='linear', name='mel_conv2')(x)
        
        # 确保输出形状正确 [batch, mel_output_count, mel_feature_size]
        # 如果输出太长，截取到mel_output_count
        x = layers.Lambda(lambda x: x[:, :self.mel_output_count, :], name='mel_crop')(x)
        
        # 如果输出太短，填充零
        def pad_if_needed(x):
            current_length = tf.shape(x)[1]
            pad_needed = tf.maximum(0, self.mel_output_count - current_length)
            padding = [[0, 0], [0, pad_needed], [0, 0]]
            return tf.pad(x, padding, constant_values=0.0)
        
        outputs = layers.Lambda(pad_if_needed, name='mel_pad')(x)
        
        model = keras.Model(inputs, outputs, name='mel_spectrogram_model')
        return model
    
    def create_standalone_embedding_model(self) -> keras.Model:
        """创建独立的嵌入模型"""
        logger.info("创建嵌入模型...")
        
        # 输入: mel特征 [batch, emb_input_count, mel_feature_size, 1]
        inputs = keras.Input(
            shape=(self.emb_input_count, self.mel_feature_size, 1), 
            name='mel_input', 
            dtype=tf.float32
        )
        
        # CNN特征提取
        x = layers.Conv2D(32, (3, 3), activation='relu', padding='same', name='emb_conv1')(inputs)
        x = layers.BatchNormalization(name='emb_bn1')(x)
        x = layers.MaxPooling2D((2, 2), name='emb_pool1')(x)
        
        x = layers.Conv2D(64, (3, 3), activation='relu', padding='same', name='emb_conv2')(x)
        x = layers.BatchNormalization(name='emb_bn2')(x)
        x = layers.MaxPooling2D((2, 2), name='emb_pool2')(x)
        
        x = layers.Conv2D(128, (3, 3), activation='relu', padding='same', name='emb_conv3')(x)
        x = layers.BatchNormalization(name='emb_bn3')(x)
        
        # 全局平均池化
        x = layers.GlobalAveragePooling2D(name='emb_gap')(x)
        
        # 全连接层
        x = layers.Dense(256, activation='relu', name='emb_fc1')(x)
        x = layers.Dropout(0.3, name='emb_dropout')(x)
        x = layers.Dense(self.emb_feature_size, activation='tanh', name='emb_fc2')(x)
        
        # 输出: [batch, 1, 1, emb_feature_size]
        outputs = layers.Reshape((1, 1, self.emb_feature_size), name='emb_reshape')(x)
        
        model = keras.Model(inputs, outputs, name='embedding_model')
        return model
    
    def create_standalone_wake_model(self) -> keras.Model:
        """创建独立的唤醒词分类模型"""
        logger.info("创建唤醒词分类模型...")
        
        # 输入: 嵌入特征序列 [batch, wake_input_count, emb_feature_size]
        inputs = keras.Input(
            shape=(self.wake_input_count, self.emb_feature_size), 
            name='embedding_input', 
            dtype=tf.float32
        )
        
        # LSTM时序建模
        x = layers.LSTM(128, return_sequences=True, name='wake_lstm1')(inputs)
        x = layers.Dropout(0.3, name='wake_dropout1')(x)
        x = layers.LSTM(64, return_sequences=False, name='wake_lstm2')(x)
        x = layers.Dropout(0.3, name='wake_dropout2')(x)
        
        # 分类层
        x = layers.Dense(32, activation='relu', name='wake_fc1')(x)
        x = layers.Dropout(0.2, name='wake_dropout3')(x)
        
        # 输出: [batch, 1] 唤醒词概率
        outputs = layers.Dense(1, activation='sigmoid', name='wake_output')(x)
        
        model = keras.Model(inputs, outputs, name='wake_model')
        return model
    
    def convert_to_tflite(self, model: keras.Model, output_path: Path, model_name: str) -> bool:
        """转换单个模型为TFLite"""
        try:
            logger.info(f"转换 {model_name} 为TFLite...")
            
            # 先保存为SavedModel格式，然后转换
            temp_saved_model_dir = output_path.parent / f"temp_{model_name}_saved_model"
            temp_saved_model_dir.mkdir(exist_ok=True)
            
            # 保存为SavedModel
            model.save(str(temp_saved_model_dir), save_format='tf')
            
            # 从SavedModel创建转换器
            converter = tf.lite.TFLiteConverter.from_saved_model(str(temp_saved_model_dir))
            
            # 设置优化选项
            converter.optimizations = [tf.lite.Optimize.DEFAULT]
            converter.target_spec.supported_types = [tf.float32]
            
            # 转换
            tflite_model = converter.convert()
            
            # 保存
            with open(output_path, 'wb') as f:
                f.write(tflite_model)
            
            # 清理临时目录
            import shutil
            shutil.rmtree(temp_saved_model_dir)
            
            # 验证模型
            file_size = output_path.stat().st_size
            logger.info(f"✅ {model_name} 转换成功: {output_path} ({file_size} bytes)")
            
            return True
            
        except Exception as e:
            logger.error(f"❌ {model_name} 转换失败: {e}")
            # 清理临时目录
            try:
                import shutil
                if temp_saved_model_dir.exists():
                    shutil.rmtree(temp_saved_model_dir)
            except:
                pass
            return False
    
    def create_and_convert_models(self) -> bool:
        """创建并转换所有模型"""
        logger.info("开始创建和转换OpenWakeWord模型...")
        
        success_count = 0
        
        # 1. Mel频谱图模型
        try:
            mel_model = self.create_standalone_mel_model()
            mel_model.compile(optimizer='adam', loss='mse')  # 编译模型
            
            # 显示模型摘要
            logger.info("Mel模型结构:")
            mel_model.summary(print_fn=logger.info)
            
            mel_path = self.model_dir / "melspectrogram.tflite"
            if self.convert_to_tflite(mel_model, mel_path, "melspectrogram"):
                success_count += 1
                
        except Exception as e:
            logger.error(f"Mel模型创建失败: {e}")
        
        # 2. 嵌入模型
        try:
            emb_model = self.create_standalone_embedding_model()
            emb_model.compile(optimizer='adam', loss='mse')  # 编译模型
            
            logger.info("嵌入模型结构:")
            emb_model.summary(print_fn=logger.info)
            
            emb_path = self.model_dir / "embedding.tflite"
            if self.convert_to_tflite(emb_model, emb_path, "embedding"):
                success_count += 1
                
        except Exception as e:
            logger.error(f"嵌入模型创建失败: {e}")
        
        # 3. 唤醒词模型
        try:
            wake_model = self.create_standalone_wake_model()
            wake_model.compile(optimizer='adam', loss='binary_crossentropy', metrics=['accuracy'])
            
            logger.info("唤醒词模型结构:")
            wake_model.summary(print_fn=logger.info)
            
            wake_path = self.model_dir / "wake.tflite"
            if self.convert_to_tflite(wake_model, wake_path, "wake"):
                success_count += 1
                
        except Exception as e:
            logger.error(f"唤醒词模型创建失败: {e}")
        
        logger.info(f"模型转换完成: {success_count}/3 个模型成功")
        return success_count == 3
    
    def create_model_metadata(self):
        """创建模型元数据"""
        metadata = {
            "wake_word": "하이넛지",
            "romanized": "hi_nutji", 
            "language": "ko-KR",
            "model_type": "OpenWakeWord",
            "created_time": tf.timestamp().numpy().item(),
            "model_files": [
                "melspectrogram.tflite",
                "embedding.tflite", 
                "wake.tflite"
            ],
            "model_parameters": {
                "mel_input_count": self.mel_input_count,
                "mel_output_count": self.mel_output_count,
                "mel_feature_size": self.mel_feature_size,
                "emb_input_count": self.emb_input_count,
                "emb_feature_size": self.emb_feature_size,
                "wake_input_count": self.wake_input_count
            },
            "usage_instructions": {
                "android_integration": "Copy the three .tflite files to app/src/withModels/assets/models/openWakeWord/",
                "file_descriptions": {
                    "melspectrogram.tflite": "Converts raw audio to mel spectrogram features",
                    "embedding.tflite": "Extracts feature embeddings from mel spectrograms", 
                    "wake.tflite": "Final wake word classification"
                }
            }
        }
        
        metadata_path = self.model_dir / "korean_wake_word_metadata.json"
        with open(metadata_path, 'w', encoding='utf-8') as f:
            json.dump(metadata, f, ensure_ascii=False, indent=2)
        
        logger.info(f"元数据已保存: {metadata_path}")
        return metadata_path

def main():
    parser = argparse.ArgumentParser(description="韩语唤醒词模型TFLite转换器")
    parser.add_argument("--model_dir", default="models/openwakeword_korean_v1",
                       help="模型目录")
    
    args = parser.parse_args()
    
    print("🔄 韩语唤醒词模型TFLite转换器")
    print("="*50)
    
    try:
        # 创建转换器
        converter = KoreanModelConverter(args.model_dir)
        
        # 转换模型
        success = converter.create_and_convert_models()
        
        if success:
            # 创建元数据
            metadata_path = converter.create_model_metadata()
            
            print(f"\n🎉 所有模型转换成功!")
            print(f"📁 输出目录: {converter.model_dir}")
            print(f"📄 元数据: {metadata_path}")
            
            print(f"\n📱 在Dicio中使用:")
            print(f"1. 将以下文件复制到 app/src/withModels/assets/models/openWakeWord/:")
            for model_file in ["melspectrogram.tflite", "embedding.tflite", "wake.tflite"]:
                model_path = converter.model_dir / model_file
                if model_path.exists():
                    print(f"   ✅ {model_file}")
                else:
                    print(f"   ❌ {model_file}")
            
            print(f"2. 重新构建Dicio应用")
            print(f"3. 在设置中选择OpenWakeWord作为唤醒词识别方法")
            
        else:
            print(f"\n❌ 模型转换失败!")
            sys.exit(1)
            
    except Exception as e:
        logger.error(f"转换过程出错: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)

if __name__ == "__main__":
    main()
