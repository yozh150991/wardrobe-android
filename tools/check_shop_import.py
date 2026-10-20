#!/usr/bin/env python3
"""Execute the production URL/metadata parser and the shared JVM regression cases."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
main = root / 'app/src/main/java/com/familywardrobe/app'
cases = root / 'app/src/test/java/com/familywardrobe/app/ShopMetadataCases.java'
with tempfile.TemporaryDirectory(prefix='wardrobe-shop-check-') as directory:
    subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-d', directory,
                    *(str(main / name) for name in ['WishRules.java', 'ShopImportRules.java', 'ShopMetadata.java', 'TextLimits.java']), str(cases)], check=True)
    subprocess.run(['java', '-cp', directory, 'com.familywardrobe.app.ShopMetadataCases'], check=True)
