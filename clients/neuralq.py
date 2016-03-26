#!/usr/bin/env python

from c4_neural import (
    moves_to_state,
    load_network,
    save_network,
    compile_Q,
    network_trainer,
)
import numpy as np
import theano
import random
import cPickle
import time
from engine import Engine

ncols = 7
nrows = 6
state_dim = ncols * nrows

# http://outlace.com/Reinforcement-Learning-Part-3/


def valid_columns(state):
    for j in range(ncols):
        for i in range(j * nrows, (j + 1) * nrows):
            if state[i] == 0:
                yield j
                break


class NeuralQ(Engine):
    def __init__(self, no_learn=False, foil=False):
        self.load_network()
        self.no_learn = no_learn
        self.foil = False
        if foil:
            self.foil = True
            self.no_learn = True
        self.epochs = 0
        self.last_save = time.time()
        self.memory = []
        self.state0 = None
        self.error_num = 0.
        self.error_den = 0
        try:
            (
                self.epochs,
                self.memory
            ) = cPickle.load(file("neuralq.pickle"))
        except IOError:
            pass
        if self.no_learn:
            self.epochs = 0

    def load_network(self):
        network = load_network()
        Q_fn = compile_Q(network)

        def moves_to_move(moves0):
            state0 = moves_to_state(moves0)
            Qs = Q_fn(np.array([state0]))[0]
            return np.nanargmax(Qs)
        self.ms2m = moves_to_move
        self.network = network
        self.trainer = network_trainer(network)

    def get_move(self, state, moves, side):
        self.epochs += 1
        if self.no_learn:
            self.mepsilon = 1.
        else:
            self.mepsilon = min(0.99, 0.5 + self.epochs / 1e5)
        if self.foil and self.epochs % 1000 == 0:
            print "Reloading network"
            self.load_network()
        if random.random() > self.mepsilon:
            action = random.choice(list(valid_columns(state)))
        else:
            action = self.ms2m(moves)

        state1 = moves_to_state(moves + [action])
        if not self.no_learn:
            self.learner(self.state0, state1=state1)
        self.state0 = state1

        return action

    def end_game(self, state, moves, side, winner):
        reward = {0: 0, 1: 1, 2: -1}[winner]

        if not self.no_learn:
            self.learner(self.state0, reward=reward)

    def learner(self, state0, state1=None, reward=0.):
        if state0 is None:
            return

        if state1 is None:
            state1 = np.zeros_like(self.state0)

        self.memory.append([state0, reward, state1])

        if len(self.memory) > 10000:
            for _ in range(1):
                indices = np.random.randint(
                    len(self.memory), size=500
                )

                (
                    state0s_batch,
                    rewards_batch,
                    state1s_batch,
                ) = zip(*[self.memory[i] for i in indices])

                state0s_batch = np.array(
                    state0s_batch, dtype=theano.config.floatX
                )
                rewards_batch = np.array(
                    rewards_batch, dtype=theano.config.floatX
                )
                state1s_batch = np.array(
                    state1s_batch, dtype=theano.config.floatX
                )
                self.error_num += self.trainer.train(
                    state0s_batch,
                    rewards_batch,
                    state1s_batch,
                    0.9,
                )
                self.error_den += 1

                if time.time() - self.last_save > 300:
                    self.last_save = time.time()
                    print "Saving snapshots"
                    cPickle.dump(
                        (
                            self.epochs,
                            self.memory
                        ),
                        file("neuralq.pickle", "w")
                    )
                    save_network(self.network)

            print "epsilon: {:.5f}".format(1 - self.mepsilon)
            print "nn error:", self.error_num / self.error_den
            self.error_num = 0.
            self.error_den = 0

            self.memory = self.memory[-50000:]
