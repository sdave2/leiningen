@echo off

set CLASSPATH="%s"

java -cp "%%CLASSPATH%%" %%JAVA_OPTS%% ^
 clojure.main -e "(use '%s)(apply -main *command-line-args*)" NUL %%*
