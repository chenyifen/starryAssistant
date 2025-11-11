#!/usr/bin/env python3
import re
import os
import sys

def fix_spaces_in_file(file_path):
    """修复yml文件中的空格问题"""
    if not os.path.exists(file_path):
        print(f"❌ 文件不存在: {file_path}")
        return False, 0
    
    with open(file_path, 'r', encoding='utf-8') as f:
        lines = f.readlines()
    
    modified = False
    spaces_count = 0
    new_lines = []
    
    for line in lines:
        match = re.match(r'^(\s*-\s+)(.*?)(\s*)$', line)
        if match:
            prefix = match.group(1)
            sentence = match.group(2)
            suffix = match.group(3)
            
            # 检查是否有空格（排除注释行）
            if ' ' in sentence and not sentence.strip().startswith('#'):
                modified = True
                spaces_count += sentence.count(' ')
                sentence_no_spaces = sentence.replace(' ', '')
                new_lines.append(prefix + sentence_no_spaces + suffix)
            else:
                new_lines.append(line)
        else:
            new_lines.append(line)
    
    if modified:
        with open(file_path, 'w', encoding='utf-8') as f:
            f.writelines(new_lines)
        return True, spaces_count
    else:
        return False, 0

if __name__ == '__main__':
    ko_yml_files = [
        "app/src/main/sentences/ko/app_launcher.yml",
        "app/src/main/sentences/ko/power_control.yml",
        "app/src/main/sentences/ko/whiteboard_tools.yml",
        "app/src/main/sentences/ko/system_navigation.yml",
        "app/src/main/sentences/ko/input_source_control.yml",
    ]
    
    print("批量修复所有韩语yml文件中的空格问题...")
    print("=" * 70)
    
    total_fixed = 0
    total_spaces = 0
    for file_path in ko_yml_files:
        fixed, spaces = fix_spaces_in_file(file_path)
        if fixed:
            print(f"✅ {file_path}: 去掉了 {spaces} 个空格")
            total_fixed += 1
            total_spaces += spaces
        else:
            print(f"✓  {file_path}: 无空格问题")
    
    print("=" * 70)
    print(f"\n📊 总计修复了 {total_fixed} 个文件，去掉了 {total_spaces} 个空格")

