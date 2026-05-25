@echo off
:: =============================================================================
::  Juke Manual — Build PDF from JUKE_MANUAL.md
::
::  Prerequisites:
::    Python 3.x on PATH
::
::  Double-click this file, or run it from any terminal.
::  Dependencies are installed automatically from requirements.txt.
:: =============================================================================
setlocal
pip install -r requirements.txt
python build_manual_pdf.py
endlocal
