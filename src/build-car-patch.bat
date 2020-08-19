@echo on
%~d0
cd %~dp0
set CAR=dallarair18
del %TMP%\%CAR%.zip %TMP%\%CAR%.sra
7z a %TMP%\%CAR%.sra -bb1 -tzip -r %CAR% %CAR%*.json
pause
