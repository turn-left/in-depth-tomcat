#  Tomcat - 源码分析准备和分析入口

转载于 链接：https://pdai.tech/md/framework/tomcat/tomcat-x-sourcecode.html

## 源代码下载和编译

首先是去官网下载Tomcat的源代码和二进制安装包，我这里分析最新的[Tomcat9.0.39稳定版本  (opens new window)](https://tomcat.apache.org/download-90.cgi)https://tomcat.apache.org/download-90.cgi

### [¶](#下载二进制包和源码) 下载二进制包和源码

> 下载二进制包的主要目的在于，让我们回顾一下包中的内容；其次，在我们后面通过源码包编译后，以方便和二进制包进行对比。

- 下载两个包

![img](/ethen/imgs/tomcat/tomcat-x-sourcecode-2.png)

- 查看二进制包中主要模块

![img](/ethen/imgs/tomcat/tomcat-x-sourcecode-3.png)

### [¶](#编译源码) 编译源码

- 导入IDEA

![img](/ethen/imgs/tomcat/tomcat-x-sourcecode-4.png)

- 使用ant编译

![img](/ethen/imgs/tomcat/tomcat-x-sourcecode-1.png)

### [¶](#理解编译后模块) 理解编译后模块

> 这里有两点要注意下：第一：在编译完之后，编译输出到哪里了呢？第二：编译后的结果是不是和我们下载的二进制文件对的上呢？

- 编译的输出在哪里

![img](/ethen/imgs/tomcat/tomcat-x-sourcecode-5.png)

- 编译的输出结果是否对的上，很显然和上面的二进制包一致

![img](/ethen/imgs/tomcat/tomcat-x-sourcecode-6.png)

## [¶](#从启动脚本定位tomcat源码入口) 从启动脚本定位Tomcat源码入口

> 好了，到这里我们基本上已经有准备好代码了，接下来便是寻找代码入口了。@pdai

### [¶](#startupbat) startup.bat

> 当我们初学tomcat的时候, 肯定先要学习怎样启动tomcat. 在tomcat的bin目录下有两个启动tomcat的文件, 一个是startup.bat, 它用于windows环境下启动tomcat; 另一个是startup.sh, 它用于linux环境下tomcat的启动. 两个文件中的逻辑是一样的, 我们只分析其中的startup.bat.

- startup.bat的源码: **startup.bat文件实际上就做了一件事情: 启动catalina.bat.**

```bash
@echo off
rem Licensed to the Apache Software Foundation (ASF) under one or more
rem contributor license agreements.  See the NOTICE file distributed with
rem this work for additional information regarding copyright ownership.
rem The ASF licenses this file to You under the Apache License, Version 2.0
rem (the "License"); you may not use this file except in compliance with
rem the License.  You may obtain a copy of the License at
rem
rem     http://www.apache.org/licenses/LICENSE-2.0
rem
rem Unless required by applicable law or agreed to in writing, software
rem distributed under the License is distributed on an "AS IS" BASIS,
rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
rem See the License for the specific language governing permissions and
rem limitations under the License.

rem ---------------------------------------------------------------------------
rem Start script for the CATALINA Server
rem ---------------------------------------------------------------------------

setlocal

rem Guess CATALINA_HOME if not defined
set "CURRENT_DIR=%cd%"
if not "%CATALINA_HOME%" == "" goto gotHome
set "CATALINA_HOME=%CURRENT_DIR%"
if exist "%CATALINA_HOME%\bin\catalina.bat" goto okHome
cd ..
set "CATALINA_HOME=%cd%"
cd "%CURRENT_DIR%"
:gotHome
if exist "%CATALINA_HOME%\bin\catalina.bat" goto okHome
echo The CATALINA_HOME environment variable is not defined correctly
echo This environment variable is needed to run this program
goto end
:okHome

set "EXECUTABLE=%CATALINA_HOME%\bin\catalina.bat"

rem Check that target executable exists
if exist "%EXECUTABLE%" goto okExec
echo Cannot find "%EXECUTABLE%"
echo This file is needed to run this program
goto end
:okExec

rem Get remaining unshifted command line arguments and save them in the
set CMD_LINE_ARGS=
:setArgs
if ""%1""=="""" goto doneSetArgs
set CMD_LINE_ARGS=%CMD_LINE_ARGS% %1
shift
goto setArgs
:doneSetArgs

call "%EXECUTABLE%" start %CMD_LINE_ARGS%
```

- 当然如果你感兴趣，不妨也可以看下上面脚本的含义

  - .bat文件中@echo是打印指令, 用于控制台输出信息, rem是注释符.
  - 跳过开头的注释, 我们来到配置CATALINA_HOME的代码段, 执行startup.bat文件首先会设置CATALINA_HOME.

  ```bash
  set "CURRENT_DIR=%cd%"
  if not "%CATALINA_HOME%" == "" goto gotHome
  set "CATALINA_HOME=%CURRENT_DIR%"
  if exist "%CATALINA_HOME%\bin\catalina.bat" goto okHome
  cd ..
  set "CATALINA_HOME=%cd%"
  cd "%CURRENT_DIR%"
  :gotHome
  if exist "%CATALINA_HOME%\bin\catalina.bat" goto okHome
  echo The CATALINA_HOME environment variable is not defined correctly
  echo This environment variable is needed to run this program
  goto end
  :okHome    
  ```

  - 先通过set指令把当前目录设置到一个名为CURRENT_DIR的变量中,
  - 如果系统中配置过CATALINA_HOME则跳到gotHome代码段. 正常情况下我们的电脑都没有配置CATALINA_HOME, 所以往下执行, 把当前目录设置为CATALINA_HOME.
  - 然后判断CATALINA_HOME目录下是否存在catalina.bat文件, 如果存在就跳到okHome代码块.
  - 在okHome中, 会把catalina.bat文件的的路径赋给一个叫EXECUTABLE的变量, 然后会进一步判断这个路径是否存在, 存在则跳转到okExec代码块, 不存在的话会在控制台输出一些错误信息.
  - 在okExec中, 会把setArgs代码块的返回结果赋值给CMD_LINE_ARGS变量, 这个变量用于存储启动参数.
  - setArgs中首先会判断是否有参数, (if ""%1""==""""判断第一个参数是否为空), 如果没有参数则相当于参数项为空. 如果有参数则循环遍历所有的参数(每次拼接第一个参数).
  - 最后执行call "%EXECUTABLE%" start %CMD_LINE_ARGS%, 也就是说执行catalina.bat文件, 如果有参数则带上参数.

> 这样看来, 在windows下启动tomcat未必一定要通过startup.bat, 用catalina.bat start也是可以的.

### [¶](#catalinabat) catalina.bat

> catalina的脚本有点多，我们分开看：

- 跳过开头的注释, 我们来到下面的代码段:

```bash
setlocal

rem Suppress Terminate batch job on CTRL+C
if not ""%1"" == ""run"" goto mainEntry
if "%TEMP%" == "" goto mainEntry
if exist "%TEMP%\%~nx0.run" goto mainEntry
echo Y>"%TEMP%\%~nx0.run"
if not exist "%TEMP%\%~nx0.run" goto mainEntry
echo Y>"%TEMP%\%~nx0.Y"
call "%~f0" %* <"%TEMP%\%~nx0.Y"
rem Use provided errorlevel
set RETVAL=%ERRORLEVEL%
del /Q "%TEMP%\%~nx0.Y" >NUL 2>&1
exit /B %RETVAL%
:mainEntry
del /Q "%TEMP%\%~nx0.run" >NUL 2>&1    
```

- 大多情况下我们启动tomcat都没有设置参数, 所以直接跳到mainEntry代码段, 删除了一个临时文件后, 继续往下执行.

```bash
rem Guess CATALINA_HOME if not defined
set "CURRENT_DIR=%cd%"
if not "%CATALINA_HOME%" == "" goto gotHome
set "CATALINA_HOME=%CURRENT_DIR%"
if exist "%CATALINA_HOME%\bin\catalina.bat" goto okHome
cd ..
set "CATALINA_HOME=%cd%"
cd "%CURRENT_DIR%"
:gotHome

if exist "%CATALINA_HOME%\bin\catalina.bat" goto okHome
echo The CATALINA_HOME environment variable is not defined correctly
echo This environment variable is needed to run this program
goto end
:okHome

rem Copy CATALINA_BASE from CATALINA_HOME if not defined
if not "%CATALINA_BASE%" == "" goto gotBase
set "CATALINA_BASE=%CATALINA_HOME%" 
```

- 可以看到这段代码与startup.bat中开头的代码相似, 在确定CATALINA_HOME下有catalina.bat后把CATALINA_HOME赋给变量CATALINA_BASE.

```bash
rem Get standard environment variables
if not exist "%CATALINA_BASE%\bin\setenv.bat" goto checkSetenvHome
call "%CATALINA_BASE%\bin\setenv.bat"
goto setenvDone
:checkSetenvHome
if exist "%CATALINA_HOME%\bin\setenv.bat" call "%CATALINA_HOME%\bin\setenv.bat"
:setenvDone

rem Get standard Java environment variables
if exist "%CATALINA_HOME%\bin\setclasspath.bat" goto okSetclasspath
echo Cannot find "%CATALINA_HOME%\bin\setclasspath.bat"
echo This file is needed to run this program
goto end
:okSetclasspath
call "%CATALINA_HOME%\bin\setclasspath.bat" %1
if errorlevel 1 goto end

rem Add on extra jar file to CLASSPATH
rem Note that there are no quotes as we do not want to introduce random
rem quotes into the CLASSPATH
if "%CLASSPATH%" == "" goto emptyClasspath
set "CLASSPATH=%CLASSPATH%;"
:emptyClasspath
set "CLASSPATH=%CLASSPATH%%CATALINA_HOME%\bin\bootstrap.jar"   
```

> 上面这段代码依次执行了setenv.bat和setclasspath.bat文件, 目的是获得CLASSPATH, 相信会Java的同学应该都会在配置环境变量时都配置过classpath, 系统拿到classpath路径后把它和CATALINA_HOME拼接在一起, 最终定位到一个叫bootstrap.jar的文件. 虽然后面还有很多代码, 但是这里必须暂停提示一下: bootstrap.jar将是我们启动tomcat的环境.

- 接下来从gotTmpdir代码块到noEndorsedVar代码块进行了一些配置, 由于不是主要内容暂且跳过.

```bash
echo Using CATALINA_BASE:   "%CATALINA_BASE%"
echo Using CATALINA_HOME:   "%CATALINA_HOME%"
echo Using CATALINA_TMPDIR: "%CATALINA_TMPDIR%"
if ""%1"" == ""debug"" goto use_jdk
echo Using JRE_HOME:        "%JRE_HOME%"
goto java_dir_displayed
:use_jdk
echo Using JAVA_HOME:       "%JAVA_HOME%"
:java_dir_displayed
echo Using CLASSPATH:       "%CLASSPATH%"

set _EXECJAVA=%_RUNJAVA%
set MAINCLASS=org.apache.catalina.startup.Bootstrap
set ACTION=start
set SECURITY_POLICY_FILE=
set DEBUG_OPTS=
set JPDA=

if not ""%1"" == ""jpda"" goto noJpda
set JPDA=jpda
if not "%JPDA_TRANSPORT%" == "" goto gotJpdaTransport
set JPDA_TRANSPORT=dt_socket
:gotJpdaTransport
if not "%JPDA_ADDRESS%" == "" goto gotJpdaAddress
set JPDA_ADDRESS=8000
:gotJpdaAddress
if not "%JPDA_SUSPEND%" == "" goto gotJpdaSuspend
set JPDA_SUSPEND=n
:gotJpdaSuspend
if not "%JPDA_OPTS%" == "" goto gotJpdaOpts
set JPDA_OPTS=-agentlib:jdwp=transport=%JPDA_TRANSPORT%,address=%JPDA_ADDRESS%,server=y,suspend=%JPDA_SUSPEND%
:gotJpdaOpts
shift
:noJpda

if ""%1"" == ""debug"" goto doDebug
if ""%1"" == ""run"" goto doRun
if ""%1"" == ""start"" goto doStart
if ""%1"" == ""stop"" goto doStop
if ""%1"" == ""configtest"" goto doConfigTest
if ""%1"" == ""version"" goto doVersion   
```

- 接下来, 我们能看到一些重要的信息, 其中的重点是:

```bash
set _EXECJAVA=%_RUNJAVA%, 设置了jdk中bin目录下的java.exe文件路径.
set MAINCLASS=org.apache.catalina.startup.Bootstrap, 设置了tomcat的启动类为Bootstrap这个类. (后面会分析这个类)
set ACTION=start设置tomcat启动    
```



> 大家可以留意这些参数, 最后执行tomcat的启动时会用到.

```bash
if not ""%1"" == ""jpda"" goto noJpda
set JPDA=jpda
if not "%JPDA_TRANSPORT%" == "" goto gotJpdaTransport
set JPDA_TRANSPORT=dt_socket
:gotJpdaTransport
if not "%JPDA_ADDRESS%" == "" goto gotJpdaAddress
set JPDA_ADDRESS=8000
:gotJpdaAddress
if not "%JPDA_SUSPEND%" == "" goto gotJpdaSuspend
set JPDA_SUSPEND=n
:gotJpdaSuspend
if not "%JPDA_OPTS%" == "" goto gotJpdaOpts
set JPDA_OPTS=-agentlib:jdwp=transport=%JPDA_TRANSPORT%,address=%JPDA_ADDRESS%,server=y,suspend=%JPDA_SUSPEND%
:gotJpdaOpts
shift
:noJpda

if ""%1"" == ""debug"" goto doDebug
if ""%1"" == ""run"" goto doRun
if ""%1"" == ""start"" goto doStart
if ""%1"" == ""stop"" goto doStop
if ""%1"" == ""configtest"" goto doConfigTest
if ""%1"" == ""version"" goto doVersion
```



- 接着判断第一个参数是否是jpda, 是则进行一些设定. 而正常情况下第一个参数是start, 所以跳过这段代码. 接着会判断第一个参数的内容, 根据判断, 我们会跳到doStart代码段. (有余力的同学不妨看下debug, run等启动方式)

```bash
:doStart
shift
if "%TITLE%" == "" set TITLE=Tomcat
set _EXECJAVA=start "%TITLE%" %_RUNJAVA%
if not ""%1"" == ""-security"" goto execCmd
shift
echo Using Security Manager
set "SECURITY_POLICY_FILE=%CATALINA_BASE%\conf\catalina.policy"
goto execCmd   
```



- 可以看到doStart中无非也是设定一些参数, 最终会跳转到execCmd代码段

```bash
:execCmd
rem Get remaining unshifted command line arguments and save them in the
set CMD_LINE_ARGS=
:setArgs
if ""%1""=="""" goto doneSetArgs
set CMD_LINE_ARGS=%CMD_LINE_ARGS% %1
shift
goto setArgs
:doneSetArgs    
```



> 可以看到这段代码也是在拼接参数, 把参数拼接到一个叫CMD_LINE_ARGS的变量中, 接下来就是catalina最后的一段代码了.

```bash
rem Execute Java with the applicable properties
if not "%JPDA%" == "" goto doJpda
if not "%SECURITY_POLICY_FILE%" == "" goto doSecurity
%_EXECJAVA% %LOGGING_CONFIG% %LOGGING_MANAGER% %JAVA_OPTS% %CATALINA_OPTS% %DEBUG_OPTS% -D%ENDORSED_PROP%="%JAVA_ENDORSED_DIRS%" -classpath "%CLASSPATH%" -Dcatalina.base="%CATALINA_BASE%" -Dcatalina.home="%CATALINA_HOME%" -Djava.io.tmpdir="%CATALINA_TMPDIR%" %MAINCLASS% %CMD_LINE_ARGS% %ACTION%
goto end
:doSecurity
%_EXECJAVA% %LOGGING_CONFIG% %LOGGING_MANAGER% %JAVA_OPTS% %CATALINA_OPTS% %DEBUG_OPTS% -D%ENDORSED_PROP%="%JAVA_ENDORSED_DIRS%" -classpath "%CLASSPATH%" -Djava.security.manager -Djava.security.policy=="%SECURITY_POLICY_FILE%" -Dcatalina.base="%CATALINA_BASE%" -Dcatalina.home="%CATALINA_HOME%" -Djava.io.tmpdir="%CATALINA_TMPDIR%" %MAINCLASS% %CMD_LINE_ARGS% %ACTION%
goto end
:doJpda
if not "%SECURITY_POLICY_FILE%" == "" goto doSecurityJpda
%_EXECJAVA% %LOGGING_CONFIG% %LOGGING_MANAGER% %JAVA_OPTS% %JPDA_OPTS% %CATALINA_OPTS% %DEBUG_OPTS% -D%ENDORSED_PROP%="%JAVA_ENDORSED_DIRS%" -classpath "%CLASSPATH%" -Dcatalina.base="%CATALINA_BASE%" -Dcatalina.home="%CATALINA_HOME%" -Djava.io.tmpdir="%CATALINA_TMPDIR%" %MAINCLASS% %CMD_LINE_ARGS% %ACTION%
goto end
:doSecurityJpda
%_EXECJAVA% %LOGGING_CONFIG% %LOGGING_MANAGER% %JAVA_OPTS% %JPDA_OPTS% %CATALINA_OPTS% %DEBUG_OPTS% -D%ENDORSED_PROP%="%JAVA_ENDORSED_DIRS%" -classpath "%CLASSPATH%" -Djava.security.manager -Djava.security.policy=="%SECURITY_POLICY_FILE%" -Dcatalina.base="%CATALINA_BASE%" -Dcatalina.home="%CATALINA_HOME%" -Djava.io.tmpdir="%CATALINA_TMPDIR%" %MAINCLASS% %CMD_LINE_ARGS% %ACTION%
goto end

:end
```

- 跳过前面两行判断后, 来到了关键语句:

```bash
%_EXECJAVA% %LOGGING_CONFIG% %LOGGING_MANAGER% %JAVA_OPTS% %CATALINA_OPTS% %DEBUG_OPTS% -D%ENDORSED_PROP%="%JAVA_ENDORSED_DIRS%" -classpath "%CLASSPATH%" -Dcatalina.base="%CATALINA_BASE%" -Dcatalina.home="%CATALINA_HOME%" -Djava.io.tmpdir="%CATALINA_TMPDIR%" %MAINCLASS% %CMD_LINE_ARGS% %ACTION%    
```

> _EXECJAVA也就是_RUNJAVA, 也就是平时说的java指令, 但在之前的doStart代码块中把_EXECJAVA改为了start "%TITLE%" %_RUNJAVA%, 所以系统会另启一个命令行窗口, 名字叫Tomcat. 在拼接一系列参数后, 我们会看见%MAINCLASS%, 也就是org.apache.catalina.startup.Bootstrap启动类, 拼接完启动参数后, 最后拼接的是%ACTION%, 也就是start.

**总结**:

- **catalina.bat最终执行了Bootstrap类中的main方法**.
- 我们可以通过设定不同的参数让tomcat以不同的方式运行. 在ide中我们是可以选择debug等模式启动tomcat的, 也可以为其配置参数, 在catalina.bat中我们看到了启动tomcat背后的运作流程.

## [¶](#参考文章) 参考文章

- https://www.cnblogs.com/tanshaoshenghao/p/10932306.html