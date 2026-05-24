@echo off
:: =============================================================================
::  Juke Demo — Run the Playwright visual demo
::
::  Prerequisites:
::    1. demo-start-server.bat is running in another window
::    2. Server has printed "Started GreetingApplication"
::
::  Double-click this file, or run it from any terminal.
:: =============================================================================
setlocal
python build_nutshell_pdf.py
endlocal
