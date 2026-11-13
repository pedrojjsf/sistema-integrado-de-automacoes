@echo off
rem ===========================================================================
rem  Abre o bot com um banco PROPRIO, sem tocar no banco do Receitanet BX.
rem
rem  Use este atalho quando o "Conectar" falhar com uma destas mensagens:
rem
rem    "Nao foi possivel ler uma informacao a partir do banco de dados"
rem    ERROR 42818: Comparisons between 'BIGINT' and 'CHAR ... are not supported
rem
rem  Isso quer dizer que o banco desta maquina foi criado por OUTRA VERSAO do
rem  Receitanet BX (mudou o tipo da coluna "ni"), e o core 1.10.0 que o bot usa
rem  nao consegue ler o usuario. Aqui o bot passa a usar um banco dele, criado
rem  do zero na pasta "home" ao lado do .exe.
rem
rem  Na primeira vez: abra, clique em "Sincronizar catalogo" (usa rede e nao
rem  pede certificado) e depois Conectar. O banco do programa oficial continua
rem  intacto - nada e' apagado nem alterado.
rem
rem  Os arquivos baixados vao para  home\Documents\Arquivos ReceitanetBX
rem  (troque na secao 5 se preferir outra pasta).
rem ===========================================================================
setlocal
set "AQUI=%~dp0"
set "HOME_BOT=%AQUI%home"
if not exist "%HOME_BOT%" mkdir "%HOME_BOT%"
set "JAVA_TOOL_OPTIONS=-Duser.home=%HOME_BOT%"
start "" "%AQUI%ReceitanetBX-Bot.exe" %*
endlocal
