## ZeroMQ

This module uses the native ZeroMQ library, but is API compatible with the pure-Java implementation, JeroMQ. The native API supports Unix domain sockets, and greater performance than that of the Java implementation. Unix domain sockets provide a performant mechinism for multiple host applications to comunicate with the on-host agent, without touching the host NIC (virtual or otherwise). 

### Installing native support

1. Install ZeroMQ. Be sure to install ZeroMQ 4.0.+ (4.1 is NOT compatible)

```
brew update && brew install zeromq
```

2. Download or build the JZMQ binaries
3. Unzip the file and execute the following shell

```
cd jzmq-3.1.0 && sudo mv libjzmq* /Library/Java/Extensions/

```

That's it, you're done.