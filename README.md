zooinspector
============

An improved zookeeper inspector

- Use async operations to speed up read
- Znodes sorted by names in tree viewer
- Timestamp and session id in more readable format in node metadata viewer
- Add a dropdown menu to show the last 10 successfully connected zookeeper addresses
- Support text search in node data viewer
- Support read-only mode for node data viewer
- **Support addAuthInfo in the connection dialog. eg."digest,AuthKey"（zk.addAuthInfo(scheme,auth)**



##改动

- 支持添加验证信息，去访问ACL的节点



Build
- $git clone https://github.com/liaojiacan/zooinspector.git
- $cd zooinspector/
- $mvn clean install

Run
- $chmod +x target/zooinspector-pkg/bin/zooinspector.sh
- $target/zooinspector-pkg/bin/zooinspector.sh


