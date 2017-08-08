#!/bin/bash
while read line; do
echo $line
LC_ALL=C find . -name "*.c" -print0 | LC_ALL=C xargs -0 sed -i '' -e "s/$line/kss_$line/g"
LC_ALL=C find . -name "*.h" -print0 | LC_ALL=C xargs -0 sed -i '' -e "s/$line/kss_$line/g"
done < functions_to_fix.txt
