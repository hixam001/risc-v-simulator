@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements. See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership. The ASF licenses this file
@REM to you under the Apache License, Version 2.0 (the
@REM "License"); you may not use this file except in compliance
@REM with the License. You may obtain a copy of the License at
@REM
@REM    http://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing,
@REM software distributed under the License is distributed on an
@REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@REM KIND, either express or implied. See the License for the
@REM specific language governing permissions and limitations
@REM under the License.
@REM ----------------------------------------------------------------------------

@REM Apache Maven Wrapper startup script, version 3.2.0

@IF "%__MVNW_ARG0_NAME__%"=="" (SET __MVNW_ARG0_NAME__=%~nx0)
@SET __ MVNW_CMD__=
@SET __MVNW_ERROR__=
@SET __MVNW_MAVEN_ERROR__=
@SETLOCAL

@IF NOT "%MAVEN_SKIP_RC%"=="" GOTO skipRcFile
@IF EXIST "%ALLUSERSPROFILE%\mavenrc_pre.bat" CALL "%ALLUSERSPROFILE%\mavenrc_pre.bat" %*
@IF EXIST "%USERPROFILE%\.mavenrc_pre.bat" CALL "%USERPROFILE%\.mavenrc_pre.bat" %*
:skipRcFile

@REM Find the project base dir, i.e. the directory that contains the folder ".mvn".
@REM Fallback to current directory if not found.

SET MAVEN_PROJECTBASEDIR=%MAVEN_BASEDIR%
IF NOT "%MAVEN_PROJECTBASEDIR%"=="" GOTO endDetectBaseDir

SET EXEC_DIR=%CD%
SET WDIR=%EXEC_DIR%
:findBaseDir
IF EXIST "%WDIR%\.mvn" GOTO baseDirFound
cd ..
IF "%WDIR%"=="%CD%" GOTO baseDirNotFound
SET WDIR=%CD%
GOTO findBaseDir

:baseDirFound
SET MAVEN_PROJECTBASEDIR=%WDIR%
cd "%EXEC_DIR%"
GOTO endDetectBaseDir

:baseDirNotFound
SET MAVEN_PROJECTBASEDIR=%EXEC_DIR%
cd "%EXEC_DIR%"

:endDetectBaseDir

IF NOT EXIST "%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar" (
    IF NOT "%MVNW_REPOURL%"=="" (
        SET MVNW_REPO_PATTERN=/org/apache/maven/wrapper/maven-wrapper/
    )
    SET DOWNLOAD_URL="https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar"
    WHERE curl >nul 2>nul
    IF %ERRORLEVEL% EQU 0 (
        curl -fsSL -o "%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar" %DOWNLOAD_URL%
    ) ELSE (
        powershell -Command "&{"^
            "$webclient = new-object System.Net.WebClient;"^
            "if (-not ([string]::IsNullOrEmpty('%MVNW_USERNAME%') -and [string]::IsNullOrEmpty('%MVNW_PASSWORD%'))) {"^
            "$webclient.Credentials = new-object System.Net.NetworkCredential('%MVNW_USERNAME%', '%MVNW_PASSWORD%');"^
            "}"^
            "$webclient.DownloadFile('%DOWNLOAD_URL%', '%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar')"^
        "}"
    )
)
@REM End of extension

@SET WRAPPER_JAR="%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar"
@SET WRAPPER_LAUNCHER=org.apache.maven.wrapper.MavenWrapperMain

@SET WRAPPER_OPTS="-Dmaven.multiModuleProjectDirectory=%MAVEN_PROJECTBASEDIR%"

%JAVA_HOME%\bin\java.exe ^
    %MAVEN_OPTS% ^
    %MAVEN_DEBUG_OPTS% ^
    -classpath %WRAPPER_JAR% ^
    %WRAPPER_OPTS% ^
    %WRAPPER_LAUNCHER% %MAVEN_CONFIG% %*

IF ERRORLEVEL 1 GOTO error
GOTO end

:error
SET ERROR_CODE=%ERRORLEVEL%

:end
@ENDLOCAL & SET ERROR_CODE=%ERROR_CODE%

IF "%MAVEN_SKIP_RC%"=="" (
    IF EXIST "%ALLUSERSPROFILE%\mavenrc_post.bat" CALL "%ALLUSERSPROFILE%\mavenrc_post.bat"
    IF EXIST "%USERPROFILE%\.mavenrc_post.bat" CALL "%USERPROFILE%\.mavenrc_post.bat"
)

EXIT /B %ERROR_CODE%
