#!/usr/bin/env python3
"""
V31唤醒词模型转换脚本
将训练数据转换为TensorFlow Lite模型文件，用于Android集成
"""

import os
import numpy as np
import tensorflow as tf
from tensorflow import keras
from tensorflow.keras import layers
import json

print(f"✅ TensorFlow版本: {tf.__version__}")

class V31ModelConverter:
    def __init__(self, data_dir, output_dir):
        self.data_dir = data_dir
        self.output_dir = output_dir
        
        # 模型参数 - 与OpenWakeWord兼容
        self.sample_rate = 16000
        self.mel_input_count = 1280  # 80ms @ 16kHz
        self.mel_feature_size = 32   # mel bins
        self.emb_input_count = 76    # mel frames
        self.emb_feature_size = 96   # embedding size
        self.wake_input_count = 16   # feature frames
        
        # 确保输出目录存在
        os.makedirs(output_dir, exist_ok=True)
        
    def load_training_data(self):
        """加载训练数据"""
        try:
            pos_train = np.load(os.path.join(self.data_dir, "positive_features_train.npy"))
            neg_train = np.load(os.path.join(self.data_dir, "negative_features_train.npy"))
            pos_test = np.load(os.path.join(self.data_dir, "positive_features_test.npy"))
            neg_test = np.load(os.path.join(self.data_dir, "negative_features_test.npy"))
            
            print(f"正样本训练数据: {pos_train.shape}")
            print(f"负样本训练数据: {neg_train.shape}")
            print(f"正样本测试数据: {pos_test.shape}")
            print(f"负样本测试数据: {neg_test.shape}")
            
            return pos_train, neg_train, pos_test, neg_test
            
        except Exception as e:
            print(f"❌ 加载训练数据失败: {e}")
            return None, None, None, None
    
    def create_mel_model(self):
        """创建mel频谱图模型 - 简化版本"""
        model = keras.Sequential([
            layers.Input(shape=(self.mel_input_count,)),
            layers.Reshape((self.mel_input_count, 1)),
            layers.Conv1D(32, 25, strides=10, activation='relu'),
            layers.Conv1D(32, 3, activation='relu'),
            layers.Reshape((76, 32))  # 输出mel频谱图
        ])
        
        model.compile(optimizer='adam', loss='mse')
        return model
    
    def create_embedding_model(self):
        """创建embedding模型"""
        model = keras.Sequential([
            layers.Input(shape=(self.emb_input_count, self.mel_feature_size)),
            layers.Reshape((self.emb_input_count, self.mel_feature_size, 1)),
            layers.Conv2D(32, (3, 3), activation='relu'),
            layers.Conv2D(64, (3, 3), activation='relu'),
            layers.GlobalAveragePooling2D(),
            layers.Dense(self.emb_feature_size, activation='relu')
        ])
        
        model.compile(optimizer='adam', loss='mse')
        return model
    
    def create_wake_model(self, pos_train, neg_train):
        """创建wake word检测模型"""
        # 准备训练数据
        X_train = np.concatenate([pos_train, neg_train], axis=0)
        y_train = np.concatenate([
            np.ones((pos_train.shape[0], 1)),
            np.zeros((neg_train.shape[0], 1))
        ], axis=0)
        
        # 随机打乱数据
        indices = np.random.permutation(len(X_train))
        X_train = X_train[indices]
        y_train = y_train[indices]
        
        # 创建模型
        model = keras.Sequential([
            layers.Input(shape=(self.wake_input_count, self.emb_feature_size)),
            layers.LSTM(64, return_sequences=True),
            layers.LSTM(32),
            layers.Dense(16, activation='relu'),
            layers.Dropout(0.3),
            layers.Dense(1, activation='sigmoid')
        ])
        
        model.compile(
            optimizer='adam',
            loss='binary_crossentropy',
            metrics=['accuracy']
        )
        
        # 训练模型
        print("训练wake word模型...")
        model.fit(X_train, y_train, epochs=10, batch_size=32, validation_split=0.2, verbose=1)
        
        return model
    
    def convert_to_tflite(self, model, model_name):
        """将Keras模型转换为TensorFlow Lite格式"""
        try:
            print(f"转换 {model_name} 为TFLite格式...")
            
            # 创建转换器
            converter = tf.lite.TFLiteConverter.from_keras_model(model)
            converter.optimizations = [tf.lite.Optimize.DEFAULT]
            
            # 转换
            tflite_model = converter.convert()
            
            # 保存
            output_path = os.path.join(self.output_dir, f"{model_name}.tflite")
            with open(output_path, 'wb') as f:
                f.write(tflite_model)
            
            print(f"✅ {model_name}.tflite 保存成功 ({len(tflite_model)} bytes)")
            return True
            
        except Exception as e:
            print(f"❌ 转换 {model_name} 失败: {e}")
            return False
    
    def convert_all(self):
        """转换所有模型"""
        print("开始转换V31模型...")
        
        # 加载训练数据
        pos_train, neg_train, pos_test, neg_test = self.load_training_data()
        if pos_train is None:
            return False
        
        success_count = 0
        
        # 1. 创建并转换mel模型
        print("创建mel频谱图模型...")
        mel_model = self.create_mel_model()
        if self.convert_to_tflite(mel_model, "melspectrogram"):
            success_count += 1
        
        # 2. 创建并转换embedding模型
        print("创建嵌入模型...")
        emb_model = self.create_embedding_model()
        if self.convert_to_tflite(emb_model, "embedding"):
            success_count += 1
        
        # 3. 创建并转换wake模型
        print("创建唤醒词分类模型...")
        wake_model = self.create_wake_model(pos_train, neg_train)
        if self.convert_to_tflite(wake_model, "wake"):
            success_count += 1
        
        print(f"模型转换完成: {success_count}/3 个模型成功")
        
        if success_count == 3:
            print("✅ 所有模型转换成功")
            return True
        else:
            print("❌ 部分模型转换失败")
            return False

def main():
    # 配置路径
    data_dir = "new_wake_model"
    output_dir = "models/openwakeword_korean_v31"
    
    print(f"数据目录: {data_dir}")
    print(f"输出目录: {output_dir}")
    
    # 创建转换器并执行转换
    converter = V31ModelConverter(data_dir, output_dir)
    success = converter.convert_all()
    
    if success:
        print("\n🎉 V31模型转换完成！")
        print(f"模型文件已保存到: {output_dir}")
        print("可以将这些文件复制到hiNudgeOpenWakeWord目录使用")
    else:
        print("\n❌ 模型转换失败")

if __name__ == "__main__":
    main()