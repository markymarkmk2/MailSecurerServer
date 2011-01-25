SET JSA_CLASSPATH=JSpamAssassin.jar;.\lib\MoreLog.jar;.\lib\log4j-1.2.9.jar
start /MIN java -classpath %JSA_CLASSPATH% org.apache.spamassassin.JSpamAssassin