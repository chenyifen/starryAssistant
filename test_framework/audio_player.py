#!/usr/bin/env python3
"""
音频播放器
支持PC端和Android端音频播放
"""

import sys
import subprocess
import argparse
from pathlib import Path

def play_audio_pc(audio_file: str, volume: float = 1.0):
    """PC端播放音频（使用系统命令）"""
    audio_path = Path(audio_file)
    if not audio_path.exists():
        print(f"❌ 音频文件不存在: {audio_file}")
        return False
    
    # macOS
    if sys.platform == 'darwin':
        subprocess.run(['afplay', str(audio_path)])
    # Linux
    elif sys.platform.startswith('linux'):
        subprocess.run(['aplay', str(audio_path)])
    # Windows
    elif sys.platform == 'win32':
        subprocess.run(['powershell', '-c', f'(New-Object Media.SoundPlayer "{audio_path}").PlaySync()'])
    else:
        print(f"❌ 不支持的操作系统: {sys.platform}")
        return False
    
    return True

def play_audio_android(device_serial: str, audio_file: str):
    """Android端播放音频（通过ADB）"""
    # 推送音频文件到设备
    subprocess.run([
        'adb', '-s', device_serial,
        'push', audio_file, '/sdcard/test_audio.wav'
    ])
    
    # 使用am start播放音频
    subprocess.run([
        'adb', '-s', device_serial,
        'shell', 'am', 'start',
        '-a', 'android.intent.action.VIEW',
        '-d', 'file:///sdcard/test_audio.wav',
        '-t', 'audio/wav'
    ])

def main():
    parser = argparse.ArgumentParser(description='音频播放器')
    parser.add_argument('play', action='store_true', help='播放音频')
    parser.add_argument('file', type=str, help='音频文件路径')
    parser.add_argument('--device', type=str, help='Android设备序列号')
    parser.add_argument('--volume', type=float, default=1.0, help='音量 (0.0-1.0)')
    
    args = parser.parse_args()
    
    if args.device:
        play_audio_android(args.device, args.file)
    else:
        play_audio_pc(args.file, args.volume)

if __name__ == '__main__':
    main()


