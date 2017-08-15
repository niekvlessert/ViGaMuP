#!/bin/bash
# This will rename functions that exist in vgmplay as well avoiding conflicts
# The functions of vgmplay can't be reused, the implementation started off at the same place, but forking and changes in both cause them to be incompatible...
# Run this when a new version of libkss is included...
while read line; do
echo $line
LC_ALL=C find . -name "*.c" -print0 | LC_ALL=C xargs -0 sed -i '' -e "s/$line/kss_$line/g"
LC_ALL=C find . -name "*.h" -print0 | LC_ALL=C xargs -0 sed -i '' -e "s/$line/kss_$line/g"
done < functions_to_fix.txt
