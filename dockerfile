FROM tomcat:8.5.69-jdk8

COPY target/OpenKM.war /usr/local/tomcat/webapps/

COPY backup/*.jar /usr/local/tomcat/lib/

COPY backup/web.xml /usr/local/tomcat/conf/
COPY backup/tomcat-users.xml /usr/local/tomcat/conf/
COPY backup/context.xml /usr/local/tomcat/conf/
COPY tomcat_home/PropertyGroups.xml /usr/local/tomcat/
COPY tomcat_home/OpenKM.xml /usr/local/tomcat/

EXPOSE 8080

CMD ["catalina.sh", "run"]