#!/bin/bash

# 批量创建剩余的中文和韩语技能文件

echo "🔧 创建剩余的中文和韩语技能文件"

# 创建 listening.yml (如果需要)
cat > app/src/main/sentences/cn/listening.yml << 'EOF'
query:
  - 你在听吗
  - 你能听到我吗
  - 听得到吗
  - 在听吗
EOF

cat > app/src/main/sentences/ko/listening.yml << 'EOF'
query:
  - 듣고 있어
  - 들려
  - 듣고 있나
  - 들리나요
EOF

echo "✅ 创建了 listening.yml 文件"

# 检查已创建的文件
echo "📊 中文技能文件:"
ls -1 app/src/main/sentences/cn/

echo "📊 韩语技能文件:"  
ls -1 app/src/main/sentences/ko/

echo "🎉 技能文件创建完成！"
