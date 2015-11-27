# c4-arena

A place for people to meet and play connect-4 with each other

# How to run server

Install leiningen - http://leiningen.org/

Go to the base folder, and type ```lein run``` - it will use port
8001 for websocket connections and port 4222 for the nrepl connection

# How to run client

Go to ```clients/``` and run ```./client.py --name Alice```

There are two reference players that you can play against, and also
two game engines that you can use, like this:
```
./client.py --name Bob --engine random --against random
./client.py --name Carl --engine neuralq --against aima
```

The AIMA reference client is from [here](https://github.com/aima-java/aima-java/blob/5856227325ef64b9e9493d663943c6080490848f/aima-core/src/main/java/aima/core/environment/connectfour/ConnectFourAIPlayer.java). It's an IterativeDeepeningAlphaBetaSearch that's been restricted to 0.5 seconds of computation time.

You will need websocket-client, which can be installed by ```pip install websocket-client```

# Client protocol

Clients connect via websocket.

From client1:
```
{:type "start", :id "Alice"}
```

From client2:
```
{:type "start", :id "Bob"}
```

To client1:
```
{
:type "state",
:turn 1 :you 1
:state [0,0,0,0,0,0,
        0,0,0,0,0,0,
        0,0,0,0,0,0,
        0,0,0,0,0,0,
        0,0,0,0,0,0,
        0,0,0,0,0,0,
        0,0,0,0,0,0]
}
```

From client1:
```
{:type "move", :move 0}
```

State representation is column-major

To client2:
```
{
:type "state",
:turn 2 :you 2
:state [1,0,0,0,0,0,
        0,0,0,0,0,0,
        0,0,0,0,0,0,
        0,0,0,0,0,0,
        0,0,0,0,0,0,
        0,0,0,0,0,0,
        0,0,0,0,0,0]
}
```

The move you make is a column number

From client2:
```
{:type "move", :move 2}
```

To client1:
```
{
:type "state",
:turn 1 :you 1
:state [1,0,0,0,0,0,
        0,0,0,0,0,0,
        2,0,0,0,0,0,
        0,0,0,0,0,0,
        0,0,0,0,0,0,
        0,0,0,0,0,0,
        0,0,0,0,0,0]
}
```

And so on. When a winner appears, the server sends a state update:

To client1:
```
{
:type "state",
:winner 1
:turn 0 :you 1
:state [1,0,0,0,0,0,
        2,1,0,0,0,0,
        2,2,1,0,0,0,
        2,1,2,1,0,0,
        1,0,0,0,0,0,
        0,0,0,0,0,0,
        0,0,0,0,0,0]
}
```

To client2:
```
{
:type "state",
:winner 1
:turn 0 :you 2
:state [1,0,0,0,0,0,
        2,1,0,0,0,0,
        2,2,1,0,0,0,
        2,1,2,1,0,0,
        1,0,0,0,0,0,
        0,0,0,0,0,0,
        0,0,0,0,0,0]
}
```

and then sends

{:type "end"}

* If the other player terminates their connection, server will send
```
{:type "disconnected"}
```

* Any invalid messages result in a reply
```
{:type "ignored", :msg <copy of msg>}
```

* You can request the current state like this:

From client1:
```
{
:type "state_request"
}
```

To client1:
```
{
:type "state"
:turn 1
:you 1
:state [1,0,0,0,0,0,
        0,0,0,0,0,0,
        2,0,0,0,0,0,
        0,0,0,0,0,0,
        0,0,0,0,0,0,
        0,0,0,0,0,0,
        0,0,0,0,0,0]
}
```

## License

Copyright © 2015 Chiao-Lun Cheng

Distributed under the Eclipse Public License version 1.0
