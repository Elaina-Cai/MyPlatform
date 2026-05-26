import re
import os

files = [
    r"C:\Users\Elaina\program\Java\MyPlatform\src\main\java\org\example\myplatform\service\chatgroup\ChatGroupServiceImpl.java",
    r"C:\Users\Elaina\program\Java\MyPlatform\src\main\java\org\example\myplatform\service\friend\impl\FriendServiceImpl.java",
    r"C:\Users\Elaina\program\Java\MyPlatform\src\main\java\org\example\myplatform\service\friend\impl\MessageServiceImpl.java",
]

for path in files:
    with open(path, 'r', encoding='utf-8') as f:
        content = f.read()
    content = re.sub(r'xssUtil\.escapeHtml\(([^)]+)\)', r'\1', content)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)
    print(f"Cleaned: {os.path.basename(path)}")

print("Done")