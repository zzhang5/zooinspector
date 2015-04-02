zooinspector
============

An improved zookeeper inspector

- Make use of async zookeeper operations to speed up
- Sorted znodes
- More readable representation of node metadata
- Add a dropdown menu to record the history of successfully connected zookeeper addresses

Build
- $git clone https://github.com/zzhang5/zooinspector.git
- $cd zooinspector/
- $mvn clean package

Run
- $chmod +x target/zooinspector-pkg/bin/zooinspector.sh
- $target/zooinspector-pkg/bin/zooinspector.sh

