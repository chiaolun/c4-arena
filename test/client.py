#!/usr/bin/env python

import websocket, json

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


start_game()
while 1:
    state = json.loads(ws.recv())
    if state["type"] == "end":
        print "Game has ended"
        print "Press any key to start another round"
        raw_input()
        start_game()
    elif state["type"] == "disconnected":
        print "Other player has disconnected"
        print "Press any key to start another round"
        raw_input()
        start_game()
    elif state["type"] == "ignored":
        print "Invalid input, try again"
    elif state["type"] == "state":
        you = state["you"]
        print "Your side:", you
        print "Board:"
        print_board(state["state"])
        if state.get("winner", False):
            print state["winner"], "has won"
        elif state["turn"] == you:
            print "It's your turn, pick a column"
            col = int(raw_input())
            ws.send(json.dumps({"type" : "move",  "move" : col}))
        else:
            print "Waiting for other player to play"
