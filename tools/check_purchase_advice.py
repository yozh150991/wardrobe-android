#!/usr/bin/env python3
"""Run real production purchase comparisons and outfit-generation rules without Android claims."""
from pathlib import Path
import subprocess
import tempfile
root=Path(__file__).resolve().parents[1]
source=root/'app/src/main/java/com/familywardrobe/app'
with tempfile.TemporaryDirectory(prefix='wardrobe-purchase-advice-') as output:
    files=[source/name for name in ['rules/WardrobeRules.java','rules/DailyRules.java','rules/PreferenceRules.java','rules/PreferenceProfile.java', 'rules/StyleRules.java', 'rules/WeatherRules.java','rules/WishRules.java','rules/ShopImportRules.java','rules/PurchaseAdviceRules.java']]
    files.append(root/'app/src/test/java/com/familywardrobe/app/rules/PurchaseAdviceCases.java')
    subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','--release','17','-d',output,*map(str,files)],check=True)
    subprocess.run(['java','-cp',output,'com.familywardrobe.app.rules.PurchaseAdviceCases'],check=True)
