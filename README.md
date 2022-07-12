zooinspector
============

An improved zookeeper inspector

- Use async operations to speed up read
- Znodes sorted by names in tree viewer
- Timestamp and session id in more readable format in node metadata viewer
- Add a dropdown menu to show the last 10 successfully connected zookeeper addresses
- Support text search in node data viewer
- Support read-only mode for node data viewer

Build
- $git clone https://github.com/zzhang5/zooinspector.git
- $cd zooinspector/
- $mvn clean package

Run
- $chmod +x target/zooinspector-pkg/bin/zooinspector.sh
- $target/zooinspector-pkg/bin/zooinspector.sh

Support SASL DIGEST-MD5
- Edit $target/zooinspector-pkg/bin/zooinspector.sh, Add "java.security.auth.login.config" jvm options.

    **zooinspector.sh example:**
    ```shell script
    exec "$JAVACMD" $JAVA_OPTS \
      $EXTRA_JVM_ARGUMENTS \
      -classpath "$CLASSPATH" \
      -Dapp.name="zooinspector" \
      -Dapp.pid="$$" \
      -Dapp.repo="$REPO" \
      -Dbasedir="$BASEDIR" \
      -Djava.security.auth.login.config={your path}/zk_client.conf \
      org.apache.zookeeper.inspector.ZooInspector \
      "$@"
    ```
    **zk_client.conf example:**
    ```shell script
    Client {
        org.apache.zookeeper.server.auth.DigestLoginModule required
        username=""
        password="";
    };
    ```
 