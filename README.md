# c4-arena

A place for people to meet and play connect-4 with each other

# Protocol

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
{:type "move_reply", :move 0}
```

State representation is column-major

To client2:
```
{
:type "move_request",
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
{:type "move_reply", :move 2}
```

To client1:
```
{
:type "move_request",
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

And so on. When a winner appears, the server sends:

To client1:
```
{
:type "end",
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
:type "end",
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
