@echo off
REM ===========================================================================
REM  Checks the mail credentials in .env against Google, without sending mail.
REM  Double-click this after editing MAIL_PASSWORD to confirm it is accepted
REM  before restarting the application.
REM ===========================================================================
cd /d "%~dp0"
python "%~dp0check-mail.py"
pause
