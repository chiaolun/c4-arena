#!/usr/bin/env python

import websocket
import json
import sys
import argparse
from engine import Engine

server = "ws://localhost:8001"

parser = argparse.ArgumentParser(description='Play Connect4!')

parser.add_argument('--name', required=True, help='Your id on the server')
parser.add_argument('--engine', default="manual",
                    help='Game engine to use ([manual]/random/neuralq)')
parser.add_argument('--against',
                    help='Who to play against '
                    '(random/aima/<id of other player>)')
parser.add_argument('--nopause', action="store_true",
                    help='Do not pause between games')
parser.add_argument('--verbose', action="store_true",
                    help='More printing for interactive use')

args = parser.parse_args()

ws = websocket.create_connection(server)


def start_game():
    ws.send(json.dumps({
        "type": "start",
        "id": args.name,
        "against": args.against
    }))
    if args.verbose:
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

class Manual(Engine):
    def get_move(self, state, moves, side):
        print "It's your turn, pick a column"
        return int(raw_input())


class Random(Engine):
    def get_move(self, state, moves, side):
        import random
        return random.randint(0, ncols - 1)

# Instantiate engine
engine_name = args.engine
if engine_name == "manual":
    engine = Manual()
elif engine_name == "random":
    engine = Random()
elif engine_name == "neuralq":
    from neuralq import NeuralQ
    engine = NeuralQ(no_learn=False)
else:
    print "Unknown mode:", sys.argv[1]
    sys.exit(1)
print engine_name, "engine chosen"

ngames = 0
nwins = 0
start_game()
while 1:
    msg = json.loads(ws.recv())
    msg_type = msg["type"]
    byebye = ({
        "end": "Game has ended",
        "disconnected": "Other player has disconnected"
    }.get(msg_type, None))

    if byebye:
        if args.verbose:
            print byebye
        if not args.nopause:
            print "Press Enter to start another round"
            raw_input()
        start_game()
        continue

    if msg_type == "ignored":
        print "Invalid input, try again"
        ws.send(json.dumps({"type": "state_request"}))
        continue

    if msg_type == "state":
        you = msg["you"]
        turn = msg["turn"]
        if args.verbose:
            print "Your side:", you
            print "Board:"
            print_board(msg["state"])
        winner = msg.get("winner", None)

        # Do learning bookkeeping for end_game
        if winner is not None:
            engine.end_game(msg["state"], msg["moves"], you, winner)

            ngames += 1
            if winner == you:
                nwins += 1
            if args.verbose:
                print "You have", ("won!" if winner == you else "lost!")
            print "Win ratio: {0}/{1} {2:3d}%".format(
                nwins, ngames, int(round(nwins*100./ngames))
            )
            if ngames >= 100:
                print "Resetting win counter"
                nwins = 0
                ngames = 0
            continue

        if turn == you:
            move = engine.get_move(msg["state"], msg["moves"], you)
            ws.send(json.dumps({"type": "move", "move": move}))
        elif args.verbose:
            print "Waiting for other player to play"
