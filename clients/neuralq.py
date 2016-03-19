#!/usr/bin/env python

from c4_neural import (
    moves_to_state,
    flip_state,
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


class NeuralQ():
    def __init__(self, epsilon=0.1):
        network = load_network()
        Q_fn = compile_Q(network)

        def moves_to_move(state0, moves0):
            Qs = Q_fn(np.array([flip_state(moves_to_state(moves0))]))[0]
            return max([(Qs[i], i) for i in valid_columns(state0)])[1]

        self.epsilon = epsilon
        self.network = network
        self.trainer = network_trainer(network)
        self.ms2m = moves_to_move
        self.last_save = time.time()
        self.memory = []
        self.error_num = 0.
        self.error_den = 0
        try:
            self.memory = cPickle.load(file("memory.pickle"))
        except IOError:
            pass

    def get_move(self, state, moves, side):
        if random.random() < self.epsilon:
            action = random.choice(list(valid_columns(state)))
        else:
            action = self.ms2m(state, moves)

        state0 = flip_state(moves_to_state(moves))

        def observer(reward=None, moves=None):
            if moves is not None:
                state1 = flip_state(moves_to_state(moves))
            else:
                state1 = np.zeros_like(state0)

            self.memory.append([
                state0,
                action,
                reward or 0.,
                state1,
            ])

            if len(self.memory) > 100000:
                for _ in range(100):
                    indices = np.random.randint(
                        len(self.memory), size=500
                    )

                    (
                        state0s_batch,
                        actions_batch,
                        rewards_batch,
                        state1s_batch,
                    ) = zip(*[self.memory[i] for i in indices])

                    state0s_batch = np.array(
                        state0s_batch, dtype=theano.config.floatX
                    )
                    actions_batch = np.array(
                        actions_batch, dtype="int8"
                    )
                    rewards_batch = np.array(
                        rewards_batch, dtype=theano.config.floatX
                    )
                    state1s_batch = np.array(
                        state1s_batch, dtype=theano.config.floatX
                    )
                    self.error_num += self.trainer.train(
                        state0s_batch,
                        actions_batch,
                        rewards_batch,
                        state1s_batch,
                        0.99,
                    )
                    self.error_den += 1

                    if time.time() - self.last_save > 300:
                        self.last_save = time.time()
                        print "Saving snapshots"
                        cPickle.dump(self.memory, file("memory.pickle", "w"))
                        save_network(self.network)

                print "nn error:", self.error_num / self.error_den
                self.error_num = 0.
                self.error_den = 0

                self.memory = self.memory[:100000]

        return action, observer
