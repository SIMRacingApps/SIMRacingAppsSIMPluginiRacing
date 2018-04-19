@echo off
cd
@echo on
set CAR=lotus
del %CAR%.zip %CAR%.sra
7z a %CAR%.sra -bb1 -tzip -r %CAR% %CAR%*.json
pause
