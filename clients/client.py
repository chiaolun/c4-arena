#!/usr/bin/env python

import websocket, json, sys, argparse

server = "ws://localhost:8001"

parser = argparse.ArgumentParser(description='Play Connect4!')

parser.add_argument('--name', required=True, help='Your id on the server')
parser.add_argument('--engine', required=True, default="random",
                    help='Game engine to use ([manual]/random/neuralq)')
parser.add_argument('--against',
                    help='Who to play against (random/aima/<id of other player>)')
parser.add_argument('--nopause', action="store_true",
                    help='Do not pause between games')

args = parser.parse_args()

ws = websocket.create_connection(server)

def start_game():
    ws.send(json.dumps({"type" : "start",  "id" : args.name, "against" : args.against}))
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
    def get_move(self, state, side):
        print "It's your turn, pick a column"
        return int(raw_input()), None

class Random():
    def get_move(self, state, side):
        import random
        return random.randint(0, ncols - 1), None

# Instantiate engine
engine_name = args.engine
if engine_name == "manual":
    engine = Manual()
elif engine_name == "random":
    engine = Random()
elif engine_name == "neuralq":
    from neuralq import NeuralQ
    engine = NeuralQ(epsilon = 0.1, gamma = 0.5)
else:
    print "Unknown mode:", sys.argv[1]
    sys.exit(1)
print engine_name, "engine chosen"

observer = None
start_game()
while 1:
    msg = json.loads(ws.recv())
    msg_type = msg["type"]
    byebye = ({"end" : "Game has ended",
               "disconnected" : "Other player has disconnected"}
              .get(msg_type, None))

    if byebye:
        print byebye
        if not args.nopause:
            print "Press Enter to start another round"
            raw_input()
        observer = None
        start_game()
        continue

    if msg_type == "ignored":
        print "Invalid input, try again"
        ws.send(json.dumps({"type" : "state_request"}))
        continue

    if msg_type == "state":
        you = msg["you"]
        turn = msg["turn"]
        print "Your side:", you
        print "Board:"
        print_board(msg["state"])
        winner = msg.get("winner", None)

        # Do learning bookkeeping
        reward = 0
        if observer and (turn == you or winner != None):
            if winner == None:
                observer(reward = 0, new_state = msg["state"])
            else:
                if winner == 0:
                    reward = 0
                elif winner == you:
                    reward = 1
                else:
                    reward = -1
                observer(reward = reward, new_state = None)
            observer = None

        if winner != None:
            print winner, "has won"
            continue

        if turn == you:
            move, observer = engine.get_move(msg["state"], you)
            ws.send(json.dumps({"type" : "move",  "move" : move}))
        else:
            print "Waiting for other player to play"
