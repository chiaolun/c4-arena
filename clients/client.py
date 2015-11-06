#!/usr/bin/env python

import websocket, json, sys

server = "ws://localhost:8001"

print "What is your name?"
name = raw_input()

ws = websocket.create_connection(server)

def start_game():
    ws.send(json.dumps({"type" : "start",  "id" : name}))
    print "Waiting for game to start"

ncols = 7
nrows = 6

def print_board(board):
    for i in range(nrows):
        row = str(nrows - i - 1) + "|"
        for j in range(ncols):
            k = j * nrows + (nrows - i - 1)
            row += ".12"[board[k]]
        print row
    print "-" * (ncols + 2)
    print "".join([" |"] + [str(i) for i in range(ncols)])

# Basic Engines
class Manual():
    def get_move(self, state):
        print "It's your turn, pick a column"
        return int(raw_input())

class Random():
    def get_move(self, state):
        import random
        return random.randint(0, ncols - 1)

# Read cli options
nopause = sys.argv[2:] and sys.argv[2] == "nopause"

# Instantiate engine
engine_name = sys.argv[1:] and sys.argv[1]
if not engine_name or engine_name == "manual":
    engine = Manual()
elif engine_name == "random":
    engine = Random()
elif engine_name == "neuralq":
    from neuralq import NeuralQ
    engine = NeuralQ()
else:
    print "Unknown mode:", sys.argv[1]
    sys.exit(1)
print engine_name, "engine chosen"

start_game()
while 1:
    state = json.loads(ws.recv())
    if state["type"] == "end":
        print "Game has ended"
        if not nopause:
            print "Press Enter to start another round"
            raw_input()
        start_game()
    elif state["type"] == "disconnected":
        print "Other player has disconnected"
        if not nopause:
            print "Press Enter to start another round"
            raw_input()
        start_game()
    elif state["type"] == "ignored":
        print "Invalid input, try again"
        ws.send(json.dumps({"type" : "state_request"}))
    elif state["type"] == "state":
        you = state["you"]
        print "Your side:", you
        print "Board:"
        print_board(state["state"])
        if state.get("winner", False):
            print state["winner"], "has won"
        elif state["turn"] == you:
            col = engine.get_move(state)
            ws.send(json.dumps({"type" : "move",  "move" : col}))
        else:
            print "Waiting for other player to play"
