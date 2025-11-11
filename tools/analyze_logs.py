#!/usr/bin/env python3
import os
import re
from collections import defaultdict, Counter
import json

def analyze_log_file(log_file_path):
    """分析单个log文件的韩语语音识别情况"""
    filename = os.path.basename(log_file_path)

    analysis = {
        'filename': filename,
        'total_lines': 0,
        'korean_asr_events': [],
        'skill_matches': [],
        'skill_mismatches': [],
        'fallback_events': [],
        'language_detections': [],
        'error_patterns': [],
        'statistics': {}
    }

    try:
        with open(log_file_path, 'r', encoding='utf-8', errors='ignore') as f:
            lines = f.readlines()
            analysis['total_lines'] = len(lines)

            for i, line in enumerate(lines):
                line = line.strip()

                # ASR最终结果
                asr_match = re.search(r'ASR final result:\s*([^,]+)', line)
                if asr_match:
                    asr_text = asr_match.group(1).strip()
                    analysis['korean_asr_events'].append({
                        'line': i+1,
                        'text': asr_text,
                        'timestamp': extract_timestamp(line)
                    })

                # 技能匹配情况
                if '技能匹配开始' in line or 'MultiLanguageSkill' in line:
                    skill_match = re.search(r'(\w+)_control|(\w+)_launcher|(\w+)_tools', line)
                    if skill_match:
                        skill_type = skill_match.group(1) or skill_match.group(2) or skill_match.group(3)
                        analysis['skill_matches'].append({
                            'line': i+1,
                            'skill_type': skill_type,
                            'timestamp': extract_timestamp(line)
                        })

                # 无匹配技能 - fallback
                if '无匹配技能' in line or 'no skill matched' in line:
                    analysis['skill_mismatches'].append({
                        'line': i+1,
                        'reason': 'no skill matched',
                        'timestamp': extract_timestamp(line)
                    })

                # 识别不出具体命令
                if '识别不出具体命令' in line or 'fallback' in line:
                    analysis['fallback_events'].append({
                        'line': i+1,
                        'type': 'command_not_recognized',
                        'timestamp': extract_timestamp(line)
                    })

                # 语言检测
                lang_match = re.search(r'ASR识别语言:\s*([^,]+)', line)
                if lang_match:
                    analysis['language_detections'].append({
                        'line': i+1,
                        'language': lang_match.group(1).strip(),
                        'timestamp': extract_timestamp(line)
                    })

                # 错误模式
                if 'ERROR' in line or 'Exception' in line:
                    analysis['error_patterns'].append({
                        'line': i+1,
                        'error': line,
                        'timestamp': extract_timestamp(line)
                    })

    except Exception as e:
        analysis['error'] = str(e)

    # 计算统计信息
    analysis['statistics'] = {
        'total_asr_events': len(analysis['korean_asr_events']),
        'total_skill_matches': len(analysis['skill_matches']),
        'total_mismatches': len(analysis['skill_mismatches']),
        'total_fallbacks': len(analysis['fallback_events']),
        'total_errors': len(analysis['error_patterns']),
        'korean_detection_rate': len([d for d in analysis['language_detections'] if '한국어' in d['language'] or 'Korean' in d['language']]),
        'success_rate': len(analysis['skill_matches']) / max(1, len(analysis['korean_asr_events'])) * 100
    }

    # 分析常见的未匹配命令
    asr_texts = [event['text'] for event in analysis['korean_asr_events']]
    mismatch_texts = []
    for mismatch in analysis['skill_mismatches']:
        # 找到对应行的ASR文本
        line_idx = mismatch['line'] - 1
        if line_idx < len(lines):
            context_lines = lines[max(0, line_idx-5):min(len(lines), line_idx+5)]
            for context_line in context_lines:
                asr_match = re.search(r'ASR final result:\s*([^,]+)', context_line)
                if asr_match:
                    mismatch_texts.append(asr_match.group(1).strip())
                    break

    analysis['common_unmatched_commands'] = Counter(mismatch_texts).most_common(10)

    return analysis

def extract_timestamp(line):
    """从日志行中提取时间戳"""
    timestamp_match = re.search(r'(\d{2}-\d{2}\s\d{2}:\d{2}:\d{2}\.\d{3})', line)
    return timestamp_match.group(1) if timestamp_match else None

def main():
    log_dir = "/Users/user/Downloads/log"
    output_dir = "/Users/user/Downloads/log_analysis"

    # 创建输出目录
    os.makedirs(output_dir, exist_ok=True)

    log_files = [f for f in os.listdir(log_dir) if f.endswith('.log')]
    log_files.sort()

    all_analyses = {}

    for log_file in log_files:
        log_path = os.path.join(log_dir, log_file)
        print(f"分析文件: {log_file}")

        analysis = analyze_log_file(log_path)
        all_analyses[log_file] = analysis

        # 保存单个文件的分析结果
        output_file = os.path.join(output_dir, f"{log_file}_analysis.json")
        with open(output_file, 'w', encoding='utf-8') as f:
            json.dump(analysis, f, ensure_ascii=False, indent=2)

        # 保存可读的文本报告
        text_report_file = os.path.join(output_dir, f"{log_file}_report.txt")
        with open(text_report_file, 'w', encoding='utf-8') as f:
            f.write(f"=== {log_file} 分析报告 ===\n\n")
            f.write(f"总行数: {analysis['total_lines']}\n")
            f.write(f"ASR事件数: {analysis['statistics']['total_asr_events']}\n")
            f.write(f"技能匹配数: {analysis['statistics']['total_skill_matches']}\n")
            f.write(f"未匹配数: {analysis['statistics']['total_mismatches']}\n")
            f.write(f"fallback事件数: {analysis['statistics']['total_fallbacks']}\n")
            f.write(f"错误数: {analysis['statistics']['total_errors']}\n")
            f.write(".2f")
            f.write(f"韩语检测次数: {analysis['statistics']['korean_detection_rate']}\n\n")

            if analysis['common_unmatched_commands']:
                f.write("最常见的未匹配命令:\n")
                for cmd, count in analysis['common_unmatched_commands'][:10]:
                    f.write(f"  {cmd}: {count}次\n")
                f.write("\n")

            if analysis['korean_asr_events']:
                f.write("ASR识别的韩语命令样本:\n")
                for event in analysis['korean_asr_events'][:20]:  # 只显示前20个
                    f.write(f"  {event['text']}\n")
                if len(analysis['korean_asr_events']) > 20:
                    f.write(f"  ... 还有{len(analysis['korean_asr_events']) - 20}个\n")
                f.write("\n")

    # 保存汇总报告
    summary_file = os.path.join(output_dir, "summary_report.json")
    with open(summary_file, 'w', encoding='utf-8') as f:
        json.dump(all_analyses, f, ensure_ascii=False, indent=2)

    # 保存汇总文本报告
    summary_text_file = os.path.join(output_dir, "summary_report.txt")
    with open(summary_text_file, 'w', encoding='utf-8') as f:
        f.write("=== 日志文件分析汇总报告 ===\n\n")

        total_stats = {
            'total_lines': 0,
            'total_asr_events': 0,
            'total_matches': 0,
            'total_mismatches': 0,
            'total_fallbacks': 0,
            'total_errors': 0
        }

        for filename, analysis in all_analyses.items():
            f.write(f"文件: {filename}\n")
            f.write(f"  总行数: {analysis['total_lines']}\n")
            f.write(f"  ASR事件: {analysis['statistics']['total_asr_events']}\n")
            f.write(f"  匹配: {analysis['statistics']['total_skill_matches']}\n")
            f.write(f"  未匹配: {analysis['statistics']['total_mismatches']}\n")
            f.write(".1f")
            f.write("\n")

            total_stats['total_lines'] += analysis['total_lines']
            total_stats['total_asr_events'] += analysis['statistics']['total_asr_events']
            total_stats['total_matches'] += analysis['statistics']['total_skill_matches']
            total_stats['total_mismatches'] += analysis['statistics']['total_mismatches']
            total_stats['total_fallbacks'] += analysis['statistics']['total_fallbacks']
            total_stats['total_errors'] += analysis['statistics']['total_errors']

        f.write("\n=== 总体统计 ===\n")
        f.write(f"总文件数: {len(all_analyses)}\n")
        f.write(f"总行数: {total_stats['total_lines']}\n")
        f.write(f"总ASR事件: {total_stats['total_asr_events']}\n")
        f.write(f"总匹配: {total_stats['total_matches']}\n")
        f.write(f"总未匹配: {total_stats['total_mismatches']}\n")
        f.write(f"总fallback: {total_stats['total_fallbacks']}\n")
        f.write(f"总错误: {total_stats['total_errors']}\n")
        if total_stats['total_asr_events'] > 0:
            overall_success_rate = total_stats['total_matches'] / total_stats['total_asr_events'] * 100
            f.write(".2f")

if __name__ == "__main__":
    main()
