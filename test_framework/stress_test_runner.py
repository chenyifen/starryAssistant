#!/usr/bin/env python3
"""
压力测试运行器
支持多设备、多机器架构的压力测试
"""

import asyncio
import subprocess
import json
import time
import re
from datetime import datetime
from typing import List, Dict, Optional
from dataclasses import dataclass, asdict
from pathlib import Path
import argparse

@dataclass
class TestDevice:
    """测试设备配置"""
    device_id: str
    adb_serial: str
    device_model: str
    android_version: str

@dataclass
class AudioSource:
    """音频源配置"""
    wake_word_file: str
    command_files: List[str]
    playback_device: str  # PC, Android, etc.

@dataclass
class TestResult:
    """测试结果"""
    test_name: str
    start_time: str
    end_time: str
    duration_seconds: int
    success: bool
    metrics: Dict
    errors: List[str]

class StressTestRunner:
    """压力测试运行器"""
    
    def __init__(self, devices: List[TestDevice], audio_source: AudioSource):
        self.devices = devices
        self.audio_source = audio_source
        self.results: List[TestResult] = []
        self.log_collectors = {}
        self.performance_monitors = {}
    
    async def run_test_suite(self, test_config: Dict):
        """运行测试套件"""
        print(f"🚀 开始运行测试套件: {test_config.get('name', 'Unknown')}")
        
        # 启动日志收集
        for device in self.devices:
            await self._start_log_collection(device)
        
        # 运行各项测试
        if test_config.get('wake_word_stability', {}).get('enabled', False):
            await self.test_wake_word_stability(
                duration_minutes=test_config['wake_word_stability'].get('duration_minutes', 60)
            )
        
        if test_config.get('asr_accuracy', {}).get('enabled', False):
            await self.test_asr_accuracy(
                test_cases=test_config['asr_accuracy'].get('test_cases', [])
            )
        
        if test_config.get('long_running', {}).get('enabled', False):
            await self.test_long_running(
                duration_hours=test_config['long_running'].get('duration_hours', 24)
            )
        
        if test_config.get('high_frequency', {}).get('enabled', False):
            await self.test_high_frequency_operations(
                operations_per_second=test_config['high_frequency'].get('ops_per_sec', 10),
                duration_seconds=test_config['high_frequency'].get('duration_seconds', 60)
            )
        
        # 停止日志收集
        for device in self.devices:
            await self._stop_log_collection(device)
        
        # 生成报告
        self.generate_report()
    
    async def test_wake_word_stability(
        self,
        duration_minutes: int = 60,
        interval_seconds: int = 5
    ):
        """测试唤醒词稳定性"""
        test_name = f"wake_word_stability_{duration_minutes}min"
        start_time = datetime.now()
        
        print(f"🧪 开始唤醒词稳定性测试: {duration_minutes}分钟")
        
        total_attempts = (duration_minutes * 60) // interval_seconds
        successful_detections = 0
        failed_detections = 0
        
        for i in range(total_attempts):
            # 播放唤醒词
            await self._play_wake_word()
            
            # 等待检测（最多3秒）
            await asyncio.sleep(3)
            
            # 检查是否检测到
            detected = await self._check_wake_word_detected(self.devices[0])
            if detected:
                successful_detections += 1
                print(f"✅ 检测成功 ({i+1}/{total_attempts})")
            else:
                failed_detections += 1
                print(f"❌ 检测失败 ({i+1}/{total_attempts})")
            
            # 等待间隔
            await asyncio.sleep(interval_seconds)
        
        end_time = datetime.now()
        duration = (end_time - start_time).total_seconds()
        success_rate = successful_detections / total_attempts if total_attempts > 0 else 0
        
        result = TestResult(
            test_name=test_name,
            start_time=start_time.isoformat(),
            end_time=end_time.isoformat(),
            duration_seconds=int(duration),
            success=success_rate >= 0.95,  # 95%以上认为成功
            metrics={
                'total_attempts': total_attempts,
                'successful_detections': successful_detections,
                'failed_detections': failed_detections,
                'success_rate': success_rate
            },
            errors=[]
        )
        
        self.results.append(result)
        print(f"✅ 唤醒词稳定性测试完成: 成功率 {success_rate*100:.2f}%")
    
    async def test_asr_accuracy(self, test_cases: List[Dict[str, str]]):
        """测试ASR识别准确性"""
        test_name = "asr_accuracy"
        start_time = datetime.now()
        
        print(f"🧪 开始ASR准确性测试: {len(test_cases)}个测试用例")
        
        results = []
        for i, test_case in enumerate(test_cases):
            print(f"  测试用例 {i+1}/{len(test_cases)}: {test_case.get('name', 'Unknown')}")
            
            # 触发唤醒
            await self._play_wake_word()
            await asyncio.sleep(1)
            
            # 播放命令音频
            await self._play_command(test_case.get('audio_file', ''))
            
            # 等待识别完成
            await asyncio.sleep(5)
            
            # 获取识别结果
            asr_result = await self._get_asr_result(self.devices[0])
            
            # 比较结果
            expected = test_case.get('expected_text', '').lower()
            actual = asr_result.lower() if asr_result else ''
            is_correct = actual == expected
            
            results.append({
                'test_case': test_case.get('name', 'Unknown'),
                'expected': expected,
                'actual': actual,
                'correct': is_correct
            })
            
            if is_correct:
                print(f"    ✅ 正确: {actual}")
            else:
                print(f"    ❌ 错误: 期望 '{expected}', 实际 '{actual}'")
        
        end_time = datetime.now()
        duration = (end_time - start_time).total_seconds()
        accuracy = sum(1 for r in results if r['correct']) / len(results) if results else 0
        
        result = TestResult(
            test_name=test_name,
            start_time=start_time.isoformat(),
            end_time=end_time.isoformat(),
            duration_seconds=int(duration),
            success=accuracy >= 0.90,  # 90%以上认为成功
            metrics={
                'total_cases': len(results),
                'correct_cases': sum(1 for r in results if r['correct']),
                'accuracy': accuracy,
                'details': results
            },
            errors=[]
        )
        
        self.results.append(result)
        print(f"✅ ASR准确性测试完成: 准确率 {accuracy*100:.2f}%")
    
    async def test_long_running(
        self,
        duration_hours: int = 24,
        check_interval_minutes: int = 30
    ):
        """长时间运行测试"""
        test_name = f"long_running_{duration_hours}h"
        start_time = datetime.now()
        end_time = start_time.replace(hour=start_time.hour + duration_hours)
        
        print(f"🧪 开始长时间运行测试: {duration_hours}小时")
        
        check_count = (duration_hours * 60) // check_interval_minutes
        metrics_history = []
        
        while datetime.now() < end_time:
            # 收集性能指标
            for device in self.devices:
                metrics = await self._collect_performance_metrics(device)
                metrics_history.append(metrics)
            
            # 执行一次唤醒测试
            await self._play_wake_word()
            await asyncio.sleep(3)
            
            # 等待检查间隔
            await asyncio.sleep(check_interval_minutes * 60)
        
        # 分析结果
        analysis = self._analyze_long_running_results(metrics_history)
        
        result = TestResult(
            test_name=test_name,
            start_time=start_time.isoformat(),
            end_time=datetime.now().isoformat(),
            duration_seconds=int((datetime.now() - start_time).total_seconds()),
            success=analysis['stable'],
            metrics=analysis,
            errors=[]
        )
        
        self.results.append(result)
        print(f"✅ 长时间运行测试完成")
    
    async def test_high_frequency_operations(
        self,
        operations_per_second: int = 10,
        duration_seconds: int = 60
    ):
        """高频操作测试"""
        test_name = f"high_frequency_{operations_per_second}ops_{duration_seconds}s"
        start_time = datetime.now()
        
        print(f"🧪 开始高频操作测试: {operations_per_second}次/秒, {duration_seconds}秒")
        
        total_operations = operations_per_second * duration_seconds
        interval_ms = 1000 // operations_per_second
        
        successful_operations = 0
        failed_operations = 0
        
        for i in range(total_operations):
            try:
                # 快速连续唤醒
                await self._play_wake_word()
                
                await asyncio.sleep(interval_ms / 1000.0)
                
                if await self._check_wake_word_detected(self.devices[0]):
                    successful_operations += 1
                else:
                    failed_operations += 1
            except Exception as e:
                failed_operations += 1
                print(f"操作 {i} 失败: {e}")
        
        end_time = datetime.now()
        duration = (end_time - start_time).total_seconds()
        success_rate = successful_operations / total_operations if total_operations > 0 else 0
        
        result = TestResult(
            test_name=test_name,
            start_time=start_time.isoformat(),
            end_time=end_time.isoformat(),
            duration_seconds=int(duration),
            success=success_rate >= 0.80,  # 80%以上认为成功
            metrics={
                'total_operations': total_operations,
                'successful': successful_operations,
                'failed': failed_operations,
                'success_rate': success_rate
            },
            errors=[]
        )
        
        self.results.append(result)
        print(f"✅ 高频操作测试完成: 成功率 {success_rate*100:.2f}%")
    
    async def _play_wake_word(self):
        """播放唤醒词"""
        # 根据播放设备类型选择不同的实现
        if self.audio_source.playback_device == "PC":
            # 使用PC音频播放
            subprocess.Popen([
                'python3', 'test_framework/audio_player.py',
                'play', self.audio_source.wake_word_file
            ])
        else:
            # 使用Android设备播放
            subprocess.run([
                'adb', '-s', self.devices[0].adb_serial,
                'shell', 'am', 'start',
                '-a', 'android.intent.action.VIEW',
                '-d', f'file://{self.audio_source.wake_word_file}',
                '-t', 'audio/wav'
            ])
    
    async def _play_command(self, audio_file: str):
        """播放命令音频"""
        if self.audio_source.playback_device == "PC":
            subprocess.Popen([
                'python3', 'test_framework/audio_player.py',
                'play', audio_file
            ])
        else:
            subprocess.run([
                'adb', '-s', self.devices[0].adb_serial,
                'shell', 'am', 'start',
                '-a', 'android.intent.action.VIEW',
                '-d', f'file://{audio_file}',
                '-t', 'audio/wav'
            ])
    
    async def _check_wake_word_detected(self, device: TestDevice) -> bool:
        """检查是否检测到唤醒词"""
        # 通过logcat检查
        result = subprocess.run([
            'adb', '-s', device.adb_serial,
            'logcat', '-d', '-s', 'AutoTest:I', '*:S'
        ], capture_output=True, text=True, timeout=1)
        
        # 检查最近的日志中是否有唤醒词检测
        lines = result.stdout.split('\n')
        recent_lines = lines[-10:]  # 最近10行
        return any('唤醒词检测成功' in line or 'WAKE WORD DETECTED' in line for line in recent_lines)
    
    async def _get_asr_result(self, device: TestDevice) -> Optional[str]:
        """获取ASR识别结果"""
        result = subprocess.run([
            'adb', '-s', device.adb_serial,
            'logcat', '-d', '-s', 'AutoTest:I', '*:S'
        ], capture_output=True, text=True, timeout=1)
        
        # 从日志中提取ASR结果
        lines = result.stdout.split('\n')
        for line in reversed(lines):
            if 'ASR结果:' in line:
                match = re.search(r'ASR结果: (.+)', line)
                if match:
                    return match.group(1)
        return None
    
    async def _start_log_collection(self, device: TestDevice):
        """启动日志收集"""
        # 清空logcat
        subprocess.run([
            'adb', '-s', device.adb_serial,
            'logcat', '-c'
        ])
    
    async def _stop_log_collection(self, device: TestDevice):
        """停止日志收集"""
        pass
    
    async def _collect_performance_metrics(self, device: TestDevice) -> Dict:
        """收集性能指标"""
        # 获取内存使用
        mem_result = subprocess.run([
            'adb', '-s', device.adb_serial,
            'shell', 'dumpsys', 'meminfo', 'com.ai.voice'
        ], capture_output=True, text=True)
        
        # 解析内存信息
        memory_mb = 0.0
        if 'TOTAL' in mem_result.stdout:
            match = re.search(r'TOTAL\s+(\d+)', mem_result.stdout)
            if match:
                memory_mb = int(match.group(1)) / 1024.0
        
        return {
            'timestamp': datetime.now().isoformat(),
            'memory_mb': memory_mb
        }
    
    def _analyze_long_running_results(self, metrics_history: List[Dict]) -> Dict:
        """分析长时间运行结果"""
        if not metrics_history:
            return {'stable': False, 'reason': 'No metrics collected'}
        
        # 检查内存泄漏
        memory_values = [m['memory_mb'] for m in metrics_history]
        memory_trend = (memory_values[-1] - memory_values[0]) / len(memory_values) if len(memory_values) > 1 else 0
        
        return {
            'stable': memory_trend < 1.0,  # 内存增长 < 1MB/检查点
            'memory_trend_mb_per_check': memory_trend,
            'max_memory_mb': max(memory_values),
            'min_memory_mb': min(memory_values),
            'avg_memory_mb': sum(memory_values) / len(memory_values)
        }
    
    def generate_report(self):
        """生成测试报告"""
        report_dir = Path('test_reports')
        report_dir.mkdir(exist_ok=True)
        
        timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
        report_file = report_dir / f'stress_test_report_{timestamp}.json'
        
        report_data = {
            'test_time': datetime.now().isoformat(),
            'devices': [asdict(d) for d in self.devices],
            'results': [asdict(r) for r in self.results]
        }
        
        with open(report_file, 'w', encoding='utf-8') as f:
            json.dump(report_data, f, indent=2, ensure_ascii=False)
        
        print(f"📊 测试报告已生成: {report_file}")

async def main():
    parser = argparse.ArgumentParser(description='压力测试运行器')
    parser.add_argument('--config', type=str, required=True, help='测试配置文件')
    parser.add_argument('--device', type=str, help='设备序列号')
    
    args = parser.parse_args()
    
    # 加载配置
    with open(args.config, 'r', encoding='utf-8') as f:
        config = json.load(f)
    
    # 创建设备列表
    devices = []
    if args.device:
        # 从ADB获取设备信息
        result = subprocess.run(['adb', '-s', args.device, 'shell', 'getprop'], capture_output=True, text=True)
        devices.append(TestDevice(
            device_id='device1',
            adb_serial=args.device,
            device_model='Unknown',
            android_version='Unknown'
        ))
    else:
        # 使用配置文件中的设备
        for dev_config in config.get('devices', []):
            devices.append(TestDevice(**dev_config))
    
    # 创建音频源
    audio_config = config.get('audio_source', {})
    audio_source = AudioSource(**audio_config)
    
    # 创建测试运行器
    runner = StressTestRunner(devices, audio_source)
    
    # 运行测试
    await runner.run_test_suite(config.get('test_suite', {}))

if __name__ == '__main__':
    asyncio.run(main())


