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
