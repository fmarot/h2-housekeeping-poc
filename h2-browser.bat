cd %~dp0

java.exe -jar "target\lib\h2-2.3.232.jar" ^
	-url "jdbc:h2:.\\target\\DB\\poc;TRACE_LEVEL_FILE=4"  ^
	-user login  ^
	-password pass ^
	-web
	
pause