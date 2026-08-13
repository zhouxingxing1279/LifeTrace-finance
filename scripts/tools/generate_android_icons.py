#!/usr/bin/env python3
"""
批量为Android图标添加白色背景
"""
import os
import sys

# Check if PIL/Pillow is available, if not provide instructions
try:
    from PIL import Image
except ImportError:
    print("错误: 需要安装 Pillow 库")
    print("请运行: pip3 install Pillow")
    sys.exit(1)

# Android icon sizes
SIZES = {
    'mdpi': 48,
    'hdpi': 72,
    'xhdpi': 96,
    'xxhdpi': 144,
    'xxxhdpi': 192,
}

SOURCE_ICON = 'assets/logo_512.png'
ANDROID_RES = 'android/app/src/main/res'

def add_white_background(input_path, output_path, size):
    """给图标添加白色背景"""
    # 创建白色背景
    background = Image.new('RGB', (size, size), 'white')

    # 打开原始图标并转换为RGBA
    icon = Image.open(input_path).convert('RGBA')

    # 调整图标大小 (留出一些边距，使用80%的空间)
    icon_size = int(size * 0.85)
    icon = icon.resize((icon_size, icon_size), Image.Resampling.LANCZOS)

    # 计算居中位置
    x = (size - icon_size) // 2
    y = (size - icon_size) // 2

    # 将图标合成到白色背景上(保留透明度)
    background.paste(icon, (x, y), icon)

    # 保存
    background.save(output_path, 'PNG', quality=100)

def main():
    print("开始批量生成白底Android图标...")

    if not os.path.exists(SOURCE_ICON):
        print(f"错误: 源图标文件不存在: {SOURCE_ICON}")
        sys.exit(1)

    for density, size in SIZES.items():
        output_dir = f"{ANDROID_RES}/mipmap-{density}"
        output_file = f"{output_dir}/ic_launcher.png"

        print(f"生成 {density} ({size}x{size})...", end=' ')

        try:
            add_white_background(SOURCE_ICON, output_file, size)
            print(f"✓ {output_file}")
        except Exception as e:
            print(f"✗ 失败: {e}")

    print("\n完成！所有Android图标已更新为白底版本")
    print("\n提示:")
    print("1. 图标已生成在 android/app/src/main/res/mipmap-*/ic_launcher.png")
    print("2. 重新构建应用即可看到新图标")
    print("3. 如需调整图标大小占比，修改脚本中的 icon_size = int(size * 0.85) 参数")

if __name__ == '__main__':
    main()
