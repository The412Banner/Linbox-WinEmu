#!/usr/bin/env python3
import re
import sys

with open('app/build.gradle.kts', 'r') as f:
    content = f.read()

# Replace terminal-view dependencies
content = re.sub(
    r'implementation\s*\(\s*"com\.github\.termux\.termux-app:terminal-view:[^"]*"\s*\)',
    'implementation(project(":terminal-view"))',
    content
)
content = re.sub(
    r'implementation\s*\(\s*libs\.com\.termux\.terminal\.view\s*\)',
    'implementation(project(":terminal-view"))',
    content
)
content = re.sub(
    r'implementation\s*\(\s*libs\.com\.termux\.termux\.view\s*\)',
    'implementation(project(":terminal-view"))',
    content
)

# Replace termux-shared dependencies
content = re.sub(
    r'implementation\s*\(\s*"com\.github\.termux\.termux-app:termux-shared:[^"]*"\s*\)',
    'implementation(project(":termux-shared"))',
    content
)
content = re.sub(
    r'implementation\s*\(\s*libs\.com\.termux\.termux\.shared\s*\)',
    'implementation(project(":termux-shared"))',
    content
)

with open('app/build.gradle.kts', 'w') as f:
    f.write(content)

print("Dependencies replaced successfully")
