import sys
from pathlib import Path

# 保证从 sky-agent 根目录可导入 app 包
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
